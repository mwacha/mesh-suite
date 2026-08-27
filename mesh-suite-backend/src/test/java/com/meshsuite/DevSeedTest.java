package com.meshsuite;

import com.meshsuite.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class DevSeedTest extends AbstractIntegrationTest {

    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void seedCreatesTwoTenantsWithLoginableAdmins() {
        var marina = authService.findAllByEmailForLogin("marina@aurora.com.br").stream().findFirst().orElse(null);
        var carlos = authService.findAllByEmailForLogin("carlos@boreal.com.br").stream().findFirst().orElse(null);

        assertThat(marina).isNotNull();
        assertThat(carlos).isNotNull();
        assertThat(passwordEncoder.matches("MeshSuite@123", marina.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("MeshSuite@123", carlos.getPasswordHash())).isTrue();
    }
}
