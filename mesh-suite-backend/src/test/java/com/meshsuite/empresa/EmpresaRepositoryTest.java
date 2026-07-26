package com.meshsuite.empresa;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmpresaRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    EmpresaRepository empresaRepository;
    @Autowired
    EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        // Tenant has no RLS (it's the tenant-defining table), so this insert needs
        // no app.tenant_id session var.
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    // The empresa_tenant_isolation policy has no explicit WITH CHECK, so Postgres
    // reuses its USING expression for INSERT too: writing a row now requires
    // app.tenant_id to already equal that row's tenant_id, not just reading one.
    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    @Test
    @Transactional
    void savesEmpresaForTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Confecção Aurora Ltda");
        empresa.setCnpj("11222333000144");

        Empresa saved = empresaRepository.save(empresa);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isAtivo()).isTrue();
    }

    @Test
    @Transactional
    void rejectsDuplicateCnpjAcrossTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        Empresa a = new Empresa();
        a.setTenantId(tenantA.getId());
        a.setRazaoSocial("Confecção Aurora Ltda");
        a.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(a);

        setTenantContext(tenantB.getId());
        Empresa b = new Empresa();
        b.setTenantId(tenantB.getId());
        b.setRazaoSocial("Confecção Boreal Ltda");
        b.setCnpj("11222333000144");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> empresaRepository.saveAndFlush(b));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Confecção Aurora Ltda");
        empresa.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(empresa);
        entityManager.clear();

        // RESET reverts the SET LOCAL above (back to no value, since it was never set
        // at session level either), simulating a query with no tenant context — RLS
        // denies every row.
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM empresa")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
