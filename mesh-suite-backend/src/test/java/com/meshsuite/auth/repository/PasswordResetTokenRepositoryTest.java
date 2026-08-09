package com.meshsuite.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.PasswordResetToken;
import com.meshsuite.auth.repository.PasswordResetTokenRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
