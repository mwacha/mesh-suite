package com.meshsuite;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    private static final String APP_ROLE = "meshsuite_app";
    private static final String APP_ROLE_PASSWORD = "meshsuite_app";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    private static boolean roleBootstrapped = false;

    // Deliberately NOT @ServiceConnection: that would wire Spring's datasource to the
    // container's own POSTGRES_USER, which the postgres image always makes a cluster
    // superuser. Superusers bypass Row-Level Security unconditionally, even with FORCE
    // ROW LEVEL SECURITY ("row security is always disabled for superusers" -- Postgres
    // docs) -- every RLS policy in this project would silently do nothing. Instead this
    // bootstraps a separate, non-superuser role and points the datasource at that.
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        bootstrapAppRoleOnce();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_ROLE_PASSWORD);
        // application.yml requires JWT_SECRET with no default; tests supply one directly.
        registry.add("app.jwt.secret", () -> "test-secret-test-secret-test-secret-32b");
    }

    private static synchronized void bootstrapAppRoleOnce() {
        if (roleBootstrapped) {
            return;
        }
        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = admin.createStatement()) {
            stmt.execute("CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_ROLE_PASSWORD + "'");
            stmt.execute("ALTER DATABASE " + POSTGRES.getDatabaseName() + " OWNER TO " + APP_ROLE);
            stmt.execute("GRANT ALL ON SCHEMA public TO " + APP_ROLE);
            roleBootstrapped = true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to bootstrap non-superuser app role", e);
        }
    }
}
