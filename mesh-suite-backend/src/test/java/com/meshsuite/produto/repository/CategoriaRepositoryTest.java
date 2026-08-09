package com.meshsuite.produto.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.produto.domain.Categoria;
import com.meshsuite.produto.repository.CategoriaRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class CategoriaRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaRepository categoriaRepository;
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

    private Categoria novaCategoria(UUID tenantId, String nome) {
        Categoria c = new Categoria();
        c.setTenantId(tenantId);
        c.setNome(nome);
        return c;
    }

    @Test
    @Transactional
    void savesCategoriaWithDefaults() {
        Tenant tenant = createTenant("aurora-cat");
        setTenantContext(tenant.getId());

        Categoria saved = categoriaRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas"));
        entityManager.clear();

        Categoria reloaded = categoriaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAtivo()).isTrue();
    }

    @Test
    @Transactional
    void nomeMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-cat");
        setTenantContext(tenant.getId());

        categoriaRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> categoriaRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas")));
    }

    @Test
    @Transactional
    void sameNomeAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-cat");
        Tenant tenantB = createTenant("boreal-cat");

        setTenantContext(tenantA.getId());
        categoriaRepository.saveAndFlush(novaCategoria(tenantA.getId(), "Camisas"));

        setTenantContext(tenantB.getId());
        Categoria saved = categoriaRepository.saveAndFlush(novaCategoria(tenantB.getId(), "Camisas"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-cat");
        setTenantContext(tenant.getId());
        categoriaRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM categoria")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
