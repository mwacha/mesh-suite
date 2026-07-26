package com.meshsuite.empresa;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class EmpresaRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    EmpresaRepository empresaRepository;
    @Autowired
    EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    @Test
    void savesEmpresaForTenant() {
        Tenant tenant = createTenant("aurora");
        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Confecção Aurora Ltda");
        empresa.setCnpj("11222333000144");

        Empresa saved = empresaRepository.save(empresa);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isAtivo()).isTrue();
    }

    @Test
    void rejectsDuplicateCnpjAcrossTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        Empresa a = new Empresa();
        a.setTenantId(tenantA.getId());
        a.setRazaoSocial("Confecção Aurora Ltda");
        a.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(a);

        Empresa b = new Empresa();
        b.setTenantId(tenantB.getId());
        b.setRazaoSocial("Confecção Boreal Ltda");
        b.setCnpj("11222333000144");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> empresaRepository.saveAndFlush(b));
    }

    @Test
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Confecção Aurora Ltda");
        empresa.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(empresa);
        entityManager.clear();

        // No app.tenant_id session var set: RLS policy denies every row.
        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM empresa")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
