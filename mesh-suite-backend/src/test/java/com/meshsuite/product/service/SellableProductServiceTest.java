package com.meshsuite.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.SellableProductResponse;
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
class SellableProductServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired SellableProductService sellableProductService;
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
    void listsSimpleKitAndVariationChildRowsTogether() {
        UUID tenantId = setUpTenant("aurora");
        simple(tenantId, "P0001", "89.90");
        kit(tenantId, "KIT001", "150.00");
        Product parent = variationParent(tenantId, "V0001", "99.90");
        variationChild(tenantId, parent, "V0001-P", "89.90");

        Page<SellableProductResponse> page = sellableProductService.list(null, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(SellableProductResponse::sku)
                .containsExactlyInAnyOrder("P0001", "KIT001", "V0001-P");
    }

    @Test
    void excludesTheVariationParentItself() {
        UUID tenantId = setUpTenant("aurora");
        Product parent = variationParent(tenantId, "V0001", "99.90");
        variationChild(tenantId, parent, "V0001-P", "89.90");

        Page<SellableProductResponse> page = sellableProductService.list(null, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(SellableProductResponse::sku)
                .containsExactly("V0001-P");
    }

    @Test
    void exposesTypeSizeAndColorwayForThePickerBadgeAndFilters() {
        UUID tenantId = setUpTenant("aurora");
        kit(tenantId, "KIT001", "150.00");
        Product parent = variationParent(tenantId, "V0001", "99.90");
        Product child = variationChild(tenantId, parent, "V0001-P-AZ", "89.90");
        child.setSize("P");
        productRepository.saveAndFlush(child);

        Page<SellableProductResponse> page = sellableProductService.list(null, null, null, PageRequest.of(0, 10));

        SellableProductResponse childRow = page.getContent().stream()
                .filter(r -> r.sku().equals("V0001-P-AZ")).findFirst().orElseThrow();
        SellableProductResponse kitRow = page.getContent().stream()
                .filter(r -> r.sku().equals("KIT001")).findFirst().orElseThrow();

        assertThat(childRow.type()).isEqualTo(ProductType.VARIATION_CHILD);
        assertThat(childRow.size()).isEqualTo("P");
        assertThat(kitRow.type()).isEqualTo(ProductType.PRODUCT_KIT);
    }

    @Test
    void narrowsToTheRequestedTypesForTheKitComposer() {
        UUID tenantId = setUpTenant("aurora");
        simple(tenantId, "P0001", "89.90");
        kit(tenantId, "KIT001", "150.00");
        Product parent = variationParent(tenantId, "V0001", "99.90");
        variationChild(tenantId, parent, "V0001-P", "89.90");

        Page<SellableProductResponse> page = sellableProductService.list(null, null,
                List.of(ProductType.PRODUCT, ProductType.VARIATION_CHILD), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(SellableProductResponse::sku)
                .containsExactlyInAnyOrder("P0001", "V0001-P");
    }

    @Test
    void cannotWidenTheScopeBackToANonSellableType() {
        UUID tenantId = setUpTenant("aurora");
        simple(tenantId, "P0001", "89.90");
        variationParent(tenantId, "V0001", "99.90");

        Page<SellableProductResponse> page = sellableProductService.list(null, null,
                List.of(ProductType.VARIATION_PARENT, ProductType.PRODUCT), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(SellableProductResponse::sku).containsExactly("P0001");
    }

    @Test
    void returnsNothingWhenOnlyNonSellableTypesAreAskedFor() {
        UUID tenantId = setUpTenant("aurora");
        simple(tenantId, "P0001", "89.90");
        variationParent(tenantId, "V0001", "99.90");

        Page<SellableProductResponse> page = sellableProductService.list(null, null,
                List.of(ProductType.VARIATION_PARENT), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void filtersBySearchAndStatus() {
        UUID tenantId = setUpTenant("aurora");
        simple(tenantId, "P0001", "89.90");
        Product inactiveKit = kit(tenantId, "KIT001", "150.00");
        inactiveKit.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveKit);

        Page<SellableProductResponse> page =
                sellableProductService.list("kit", ProductStatus.INACTIVE, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).sku()).isEqualTo("KIT001");
    }

    @Test
    void deniesListingWhenCallerLacksProductViewPermission() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao");
        tenant.setNome("sem-permissao");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User noPerms = new User();
        noPerms.setTenantId(tenant.getId());
        noPerms.setName("No Permissions");
        noPerms.setEmail("no-perms@sem-permissao.com.br");
        noPerms.setPasswordHash("hash");
        noPerms.setRole(Role.SALES_REP);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        org.junit.jupiter.api.Assertions.assertThrows(com.meshsuite.auth.exception.PermissionDeniedException.class,
                () -> sellableProductService.list(null, null, null, PageRequest.of(0, 10)));
    }
}
