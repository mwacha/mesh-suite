package com.meshsuite.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.ProductAllListItemResponse;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductListingServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductListingService productListingService;
    @Autowired EntityManager entityManager;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private UUID setUpTenant(String code) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(code);
        tenant.setNome(code);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User caller = new User();
        caller.setTenantId(tenant.getId());
        caller.setName("Test Caller");
        caller.setEmail("caller-" + UUID.randomUUID() + "@" + code + ".com.br");
        caller.setPasswordHash("hash");
        caller.setRole(Role.ADMINISTRATIVE);
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.VIEW));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        return tenant.getId();
    }

    private Product simple(UUID tenantId, String sku, String price) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Camiseta " + sku);
        p.setSku(sku);
        p.setSalePrice(new BigDecimal(price));
        return productRepository.saveAndFlush(p);
    }

    private Product kit(UUID tenantId, String sku, String price) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setType(ProductType.PRODUCT_KIT);
        p.setName("Kit " + sku);
        p.setSku(sku);
        p.setSalePrice(new BigDecimal(price));
        return productRepository.saveAndFlush(p);
    }

    private Product variationParent(UUID tenantId, String sku, String price) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setType(ProductType.VARIATION_PARENT);
        p.setName("Camiseta com Variação " + sku);
        p.setSku(sku);
        p.setSalePrice(new BigDecimal(price));
        return productRepository.saveAndFlush(p);
    }

    private Product variationChild(UUID tenantId, Product parent, String sku, String price) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setType(ProductType.VARIATION_CHILD);
        p.setParentProduct(parent);
        p.setName(parent.getName());
        p.setSku(sku);
        p.setSalePrice(new BigDecimal(price));
        return productRepository.saveAndFlush(p);
    }

    @Test
    void listsAllTypesTogetherWithATypeDiscriminator() {
        UUID tenantId = setUpTenant("aurora");
        simple(tenantId, "P0001", "89.90");
        kit(tenantId, "KIT001", "150.00");
        variationParent(tenantId, "V0001", "99.90");

        Page<ProductAllListItemResponse> page =
                productListingService.listAll(null, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent()).extracting(ProductAllListItemResponse::type)
                .containsExactlyInAnyOrder(ProductType.PRODUCT, ProductType.PRODUCT_KIT, ProductType.VARIATION_PARENT);
    }

    @Test
    void excludesVariationChildRowsAsStandaloneEntries() {
        UUID tenantId = setUpTenant("aurora");
        Product parent = variationParent(tenantId, "V0001", "99.90");
        variationChild(tenantId, parent, "V0001-P", "89.90");

        Page<ProductAllListItemResponse> page =
                productListingService.listAll(null, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).type()).isEqualTo(ProductType.VARIATION_PARENT);
    }

    @Test
    void nestsChildrenOnlyUnderTheirVariationParentRow() {
        UUID tenantId = setUpTenant("aurora");
        simple(tenantId, "P0001", "89.90");
        Product parent = variationParent(tenantId, "V0001", "99.90");
        variationChild(tenantId, parent, "V0001-P", "89.90");
        variationChild(tenantId, parent, "V0001-M", "94.90");

        Page<ProductAllListItemResponse> page =
                productListingService.listAll(null, null, null, PageRequest.of(0, 10));

        ProductAllListItemResponse simpleRow = page.getContent().stream()
                .filter(r -> r.type() == ProductType.PRODUCT).findFirst().orElseThrow();
        ProductAllListItemResponse parentRow = page.getContent().stream()
                .filter(r -> r.type() == ProductType.VARIATION_PARENT).findFirst().orElseThrow();

        assertThat(simpleRow.children()).isEmpty();
        assertThat(parentRow.children()).extracting("sku")
                .containsExactlyInAnyOrder("V0001-P", "V0001-M");
    }

    @Test
    void filtersByType() {
        UUID tenantId = setUpTenant("aurora");
        simple(tenantId, "P0001", "89.90");
        kit(tenantId, "KIT001", "150.00");

        Page<ProductAllListItemResponse> page =
                productListingService.listAll(null, null, ProductType.PRODUCT_KIT, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).sku()).isEqualTo("KIT001");
    }

    @Test
    void filtersByStatusAndSearchAcrossAllTypes() {
        UUID tenantId = setUpTenant("aurora");
        Product inactiveKit = kit(tenantId, "KIT001", "150.00");
        inactiveKit.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveKit);
        simple(tenantId, "P0001", "89.90");

        Page<ProductAllListItemResponse> page =
                productListingService.listAll("kit", ProductStatus.INACTIVE, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).sku()).isEqualTo("KIT001");
    }

    @Test
    void summarizesActiveAndInactiveCountsAcrossAllTypesExcludingChildren() {
        UUID tenantId = setUpTenant("aurora");
        simple(tenantId, "P0001", "89.90");
        kit(tenantId, "KIT001", "150.00");
        Product parent = variationParent(tenantId, "V0001", "99.90");
        variationChild(tenantId, parent, "V0001-P", "89.90");

        Product inactiveKit = kit(tenantId, "KIT002", "50.00");
        inactiveKit.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveKit);

        var summary = productListingService.summary();

        // 3 active (simple + KIT001 + variation parent) + 1 inactive (KIT002) -- the
        // variation child is never counted as a standalone row.
        assertThat(summary.active()).isEqualTo(3);
        assertThat(summary.inactive()).isEqualTo(1);
        assertThat(summary.total()).isEqualTo(4);
    }
}
