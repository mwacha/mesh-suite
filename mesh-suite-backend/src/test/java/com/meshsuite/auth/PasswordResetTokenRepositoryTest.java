package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordResetTokenRepository tokenRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void savesAndFindsByTokenHash() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("aurora");
        tenantRepository.saveAndFlush(tenant);

        // usuario_tenant_isolation's USING expression also gates INSERT (no explicit
        // WITH CHECK), so app.tenant_id must be set before writing this row.
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@confeccaoaurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash("abc123hash");
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));

        tokenRepository.save(token);

        assertThat(tokenRepository.findByTokenHash("abc123hash")).isPresent();
    }
}
