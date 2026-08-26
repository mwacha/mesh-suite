package com.meshsuite.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class ProductRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProductRepository productRepository;
    @Autowired EntityManager entityManager;

    private Tenant createTenant(String code) {
        Tenant t = new Tenant();
        t.setCodigo(code);
        t.setNome(code);
        return tenantRepository.saveAndFlush(t);
    }

    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    private Product newProduct(UUID tenantId, String sku) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Camiseta Polo");
        p.setSku(sku);
        p.setSalePrice(new BigDecimal("59.90"));
        return p;
    }

    @Test
    @Transactional
    void savesProductWithDefaults() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Product saved = productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001"));
        entityManager.clear();

        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(reloaded.getMeasurementUnit()).isEqualTo(MeasurementUnit.UN);
        assertThat(reloaded.getStockQuantity()).isEqualByComparingTo("0");
        assertThat(reloaded.getType()).isEqualTo(ProductType.PRODUCT);
    }

    @Test
    @Transactional
    void skuUniquenessSpansEveryProductType() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001"));

        Product kit = newProduct(tenant.getId(), "P0001");
        kit.setType(ProductType.PRODUCT_KIT);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> productRepository.saveAndFlush(kit));
    }

    @Test
    @Transactional
    void findByIdAndTypeOnlyMatchesTheGivenType() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Product saved = productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001"));

        assertThat(productRepository.findByIdAndType(saved.getId(), ProductType.PRODUCT)).isPresent();
        assertThat(productRepository.findByIdAndType(saved.getId(), ProductType.PRODUCT_KIT)).isEmpty();
    }

    @Test
    @Transactional
    void skuMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001")));
    }

    @Test
    @Transactional
    void sameSkuAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        productRepository.saveAndFlush(newProduct(tenantA.getId(), "P0001"));

        setTenantContext(tenantB.getId());
        Product saved = productRepository.saveAndFlush(newProduct(tenantB.getId(), "P0001"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM product")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
