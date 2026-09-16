package com.meshsuite.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.TenantSignupToken;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class TenantSignupTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    TenantSignupTokenRepository tokenRepository;

    @Test
    @Transactional
    void savesAndFindsByTokenHash() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora-signup");
        tenant.setNome("Aurora Signup");
        tenant.setAtivo(false);
        tenantRepository.saveAndFlush(tenant);

        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(tenant.getId());
        token.setTokenHash("abc123hash");
        token.setExpiraEm(Instant.now().plus(24, ChronoUnit.HOURS));

        tokenRepository.save(token);

        assertThat(tokenRepository.findByTokenHash("abc123hash")).isPresent();
        assertThat(tokenRepository.findByTokenHash("no-such-hash")).isEmpty();
    }
}
