package com.meshsuite.brand.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.brand.domain.Brand;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class BrandRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired BrandRepository brandRepository;
    @Autowired EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    private Brand novaBrand(UUID tenantId, String nome) {
        Brand b = new Brand();
        b.setTenantId(tenantId);
        b.setName(nome);
        return b;
    }

    @Test
    @Transactional
    void savesBrandWithDefaults() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());

        Brand saved = brandRepository.saveAndFlush(novaBrand(tenant.getId(), "Marca Alpha"));
        entityManager.clear();

        Brand reloaded = brandRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActive()).isTrue();
        assertThat(reloaded.getName()).isEqualTo("Marca Alpha");
    }

    @Test
    @Transactional
    void nameMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());

        brandRepository.saveAndFlush(novaBrand(tenant.getId(), "Marca Alpha"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> brandRepository.saveAndFlush(novaBrand(tenant.getId(), "Marca Alpha")));
    }

    @Test
    @Transactional
    void sameNameAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-corest");
        Tenant tenantB = createTenant("boreal-corest");

        setTenantContext(tenantA.getId());
        brandRepository.saveAndFlush(novaBrand(tenantA.getId(), "Marca Alpha"));

        setTenantContext(tenantB.getId());
        Brand saved = brandRepository.saveAndFlush(novaBrand(tenantB.getId(), "Marca Alpha"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());
        brandRepository.saveAndFlush(novaBrand(tenant.getId(), "Marca Alpha"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM brand")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
