package com.meshsuite.company.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class CompanyRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    CompanyRepository companyRepository;
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

    // The company_tenant_isolation policy has no explicit WITH CHECK, so Postgres
    // reuses its USING expression for INSERT too: writing a row now requires
    // app.tenant_id to already equal that row's tenant_id, not just reading one.
    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    @Test
    @Transactional
    void savesCompanyForTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Confecção Aurora Ltda");
        company.setCnpj("11222333000144");

        Company saved = companyRepository.save(company);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @Transactional
    void rejectsDuplicateCnpjAcrossTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        Company a = new Company();
        a.setTenantId(tenantA.getId());
        a.setLegalName("Confecção Aurora Ltda");
        a.setCnpj("11222333000144");
        companyRepository.saveAndFlush(a);

        setTenantContext(tenantB.getId());
        Company b = new Company();
        b.setTenantId(tenantB.getId());
        b.setLegalName("Confecção Boreal Ltda");
        b.setCnpj("11222333000144");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> companyRepository.saveAndFlush(b));
    }

    @Test
    @Transactional
    void savesAndReloadsDetailFields() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Confecção Aurora Ltda");
        company.setCnpj("11222333000144");
        company.setTradeName("Confecção Aurora");
        company.setStateRegistration("123456789");
        company.setMunicipalRegistration("987654");
        company.setPhone("(11) 3000-0000");
        company.setEmail("contato@aurora.com.br");
        company.setWebsite("www.aurora.com.br");
        company.setZipCode("01310100");
        company.setStreet("Av. Paulista");
        company.setNumber("1000");
        company.setComplement("Sala 10");
        company.setNeighborhood("Bela Vista");
        company.setCity("São Paulo");
        company.setState("SP");

        Company saved = companyRepository.saveAndFlush(company);
        entityManager.clear();

        Company reloaded = companyRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTradeName()).isEqualTo("Confecção Aurora");
        assertThat(reloaded.getZipCode()).isEqualTo("01310100");
        assertThat(reloaded.getCity()).isEqualTo("São Paulo");
        assertThat(reloaded.getState()).isEqualTo("SP");
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Confecção Aurora Ltda");
        company.setCnpj("11222333000144");
        companyRepository.saveAndFlush(company);
        entityManager.clear();

        // RESET reverts the SET LOCAL above (back to no value, since it was never set
        // at session level either), simulating a query with no tenant context — RLS
        // denies every row.
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM company")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
