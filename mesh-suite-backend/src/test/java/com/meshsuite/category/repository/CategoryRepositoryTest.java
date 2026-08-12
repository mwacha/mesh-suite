package com.meshsuite.category.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.category.domain.Category;
import com.meshsuite.category.repository.CategoryRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class CategoryRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired CategoryRepository categoryRepository;
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

    private Category novaCategoria(UUID tenantId, String nome) {
        Category c = new Category();
        c.setTenantId(tenantId);
        c.setName(nome);
        return c;
    }

    @Test
    @Transactional
    void savesCategoryWithDefaults() {
        Tenant tenant = createTenant("aurora-cat");
        setTenantContext(tenant.getId());

        Category saved = categoryRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas"));
        entityManager.clear();

        Category reloaded = categoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActive()).isTrue();
    }

    @Test
    @Transactional
    void nameMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-cat");
        setTenantContext(tenant.getId());

        categoryRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> categoryRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas")));
    }

    @Test
    @Transactional
    void sameNameAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-cat");
        Tenant tenantB = createTenant("boreal-cat");

        setTenantContext(tenantA.getId());
        categoryRepository.saveAndFlush(novaCategoria(tenantA.getId(), "Camisas"));

        setTenantContext(tenantB.getId());
        Category saved = categoryRepository.saveAndFlush(novaCategoria(tenantB.getId(), "Camisas"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-cat");
        setTenantContext(tenant.getId());
        categoryRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM category")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
