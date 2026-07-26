package com.meshsuite.tenant;

import com.meshsuite.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TenantRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;

    @Test
    void savesAndFindsTenant() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("Confecção Aurora");
        Tenant saved = tenantRepository.save(tenant);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isAtivo()).isTrue();
        assertThat(tenantRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void rejectsDuplicateCodigo() {
        Tenant a = new Tenant();
        a.setCodigo("aurora");
        a.setNome("Confecção Aurora");
        tenantRepository.saveAndFlush(a);

        Tenant b = new Tenant();
        b.setCodigo("aurora");
        b.setNome("Outra Empresa");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> tenantRepository.saveAndFlush(b));
    }
}
