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
        var marina = authService.findByEmailForLogin("marina@aurora.com.br");
        var carlos = authService.findByEmailForLogin("carlos@boreal.com.br");

        assertThat(marina).isNotNull();
        assertThat(carlos).isNotNull();
        assertThat(passwordEncoder.matches("MeshSuite@123", marina.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("MeshSuite@123", carlos.getPasswordHash())).isTrue();
    }
}
