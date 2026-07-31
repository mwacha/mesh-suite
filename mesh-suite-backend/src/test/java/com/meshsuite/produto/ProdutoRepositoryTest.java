package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProdutoRepository produtoRepository;
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

    private Produto novoProduto(UUID tenantId, String sku) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("59.90"));
        return p;
    }

    @Test
    @Transactional
    void savesProdutoWithDefaults() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Produto saved = produtoRepository.saveAndFlush(novoProduto(tenant.getId(), "P0001"));
        entityManager.clear();

        Produto reloaded = produtoRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusProduto.ATIVO);
        assertThat(reloaded.getUnidadeMedida()).isEqualTo(UnidadeMedida.UN);
        assertThat(reloaded.getQuantidadeEstoque()).isEqualByComparingTo("0");
    }

    @Test
    @Transactional
    void skuMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        produtoRepository.saveAndFlush(novoProduto(tenant.getId(), "P0001"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> produtoRepository.saveAndFlush(novoProduto(tenant.getId(), "P0001")));
    }

    @Test
    @Transactional
    void sameSkuAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        produtoRepository.saveAndFlush(novoProduto(tenantA.getId(), "P0001"));

        setTenantContext(tenantB.getId());
        Produto saved = produtoRepository.saveAndFlush(novoProduto(tenantB.getId(), "P0001"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        produtoRepository.saveAndFlush(novoProduto(tenant.getId(), "P0001"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM produto")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
