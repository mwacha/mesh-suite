package com.meshsuite.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.ProductRequest;
import com.meshsuite.product.exception.ProductNotFoundException;
import com.meshsuite.product.exception.DuplicateSkuException;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SimpleProductServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired SimpleProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired EntityManager entityManager;
    @Autowired UserRepository userRepository;

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
        caller.setProfile(Profile.ADMIN);
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private ProductRequest request(String sku, BigDecimal salePrice) {
        return new ProductRequest(
                "Camiseta Polo Masculina", sku, "7891234567890", null, null, null,
                salePrice, new BigDecimal("25.00"), ProductStatus.ACTIVE, "Descrição de teste",
                new BigDecimal("10"), MeasurementUnit.UN, new BigDecimal("2"), new BigDecimal("50"), "M",
                new BigDecimal("0.300"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"), null);
    }

    @Test
    void createsAndRetrievesProduct() {
        setUpTenant("aurora");

        var created = productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var found = productService.findById(created.id());
        assertThat(found.name()).isEqualTo("Camiseta Polo Masculina");
        assertThat(found.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(found.size()).isEqualTo("M");
    }

    @Test
    void defaultsSaleMultipleToOneWhenNotProvided() {
        setUpTenant("aurora");

        var created = productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        assertThat(productService.findById(created.id()).saleMultiple()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void createsProductWithAnExplicitSaleMultiple() {
        setUpTenant("aurora");
        ProductRequest request = new ProductRequest(
                "Camiseta Polo Masculina", "P0001", "7891234567890", null, null, null,
                new BigDecimal("59.90"), new BigDecimal("25.00"), ProductStatus.ACTIVE, "Descrição de teste",
                new BigDecimal("10"), MeasurementUnit.UN, new BigDecimal("2"), new BigDecimal("50"), "M",
                new BigDecimal("0.300"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"),
                new BigDecimal("6"));

        var created = productService.create(TenantContext.get(), request);

        assertThat(productService.findById(created.id()).saleMultiple()).isEqualByComparingTo("6");
    }

    @Test
    void rejectsDuplicateSkuInSameTenant() {
        setUpTenant("aurora");
        productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        assertThrows(DuplicateSkuException.class,
                () -> productService.create(TenantContext.get(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void updatesProductKeepingItsOwnSku() {
        setUpTenant("aurora");
        var created = productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var updated = productService.update(created.id(), request("P0001", new BigDecimal("64.90")));

        assertThat(updated.salePrice()).isEqualByComparingTo("64.90");
    }

    @Test
    void rejectsUpdateToAnotherProductsSku() {
        setUpTenant("aurora");
        productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        var second = productService.create(TenantContext.get(), request("P0002", new BigDecimal("39.90")));

        assertThrows(DuplicateSkuException.class,
                () -> productService.update(second.id(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void updatesStatusToInactive() {
        setUpTenant("aurora");
        var created = productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        productService.updateStatus(created.id(), ProductStatus.INACTIVE);

        assertThat(productService.findById(created.id()).status()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void summaryCountsByStatus() {
        setUpTenant("aurora");
        var a = productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        productService.create(TenantContext.get(), request("P0002", new BigDecimal("39.90")));
        productService.updateStatus(a.id(), ProductStatus.INACTIVE);

        var summary = productService.summary();

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.active()).isEqualTo(1);
        assertThat(summary.inactive()).isEqualTo(1);
    }

    @Test
    void listsWithSearchFilter() {
        setUpTenant("aurora");
        productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var page = productService.list("camiseta", null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).sku()).isEqualTo("P0001");
    }

    @Test
    void deletesProduct() {
        setUpTenant("aurora");
        var created = productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        productService.delete(created.id());

        assertThrows(ProductNotFoundException.class, () -> productService.findById(created.id()));
    }

    @Test
    void sameSkuAllowedAcrossDifferentTenants() {
        setUpTenant("aurora");
        productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        setUpTenant("boreal");
        var second = productService.create(TenantContext.get(), request("P0001", new BigDecimal("39.90")));

        assertThat(second.sku()).isEqualTo("P0001");
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
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(com.meshsuite.auth.exception.PermissionDeniedException.class,
                () -> productService.list(null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }

    // Phase 1: introducing ProductType must not let non-PRODUCT rows leak into the
    // pre-existing Simples listing/summary/findById -- inserted directly since
    // Kit/Variation services don't exist yet at this phase.
    @Test
    void excludesNonProductTypesFromListingSummaryAndLookup() {
        setUpTenant("aurora");
        var simple = productService.create(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        Product kit = new Product();
        kit.setTenantId(TenantContext.get());
        kit.setType(ProductType.PRODUCT_KIT);
        kit.setName("Kit Combo");
        kit.setSku("KIT001");
        kit.setSalePrice(new BigDecimal("10.00"));
        productRepository.saveAndFlush(kit);

        var page = productService.list(null, null, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting("id").containsExactly(simple.id());

        var summary = productService.summary();
        assertThat(summary.total()).isEqualTo(1);

        assertThrows(ProductNotFoundException.class, () -> productService.findById(kit.getId()));
    }
}
