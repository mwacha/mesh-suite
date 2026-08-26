package com.meshsuite.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.KitItemInput;
import com.meshsuite.product.dto.KitProductRequest;
import com.meshsuite.product.exception.DuplicateSkuException;
import com.meshsuite.product.exception.ProductNotFoundException;
import com.meshsuite.product.exception.ProductValidationException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class KitProductServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired KitProductService kitProductService;
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
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        return tenant.getId();
    }

    private Product component(UUID tenantId, String sku, String price) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Componente " + sku);
        p.setSku(sku);
        p.setSalePrice(new BigDecimal(price));
        return productRepository.saveAndFlush(p);
    }

    private KitProductRequest request(String sku, List<KitItemInput> items) {
        return new KitProductRequest("Kit Combo", sku, null, null, null, "Kit de teste", items);
    }

    @Test
    void calculatesTotalPriceFromComponentQuantities() {
        UUID tenantId = setUpTenant("aurora");
        Product shirt = component(tenantId, "P0001", "89.90");
        Product pants = component(tenantId, "P0002", "149.90");

        var kit = kitProductService.create(tenantId, request("KIT001", List.of(
                new KitItemInput(shirt.getId(), new BigDecimal("2")),
                new KitItemInput(pants.getId(), new BigDecimal("1")))));

        // 2*89.90 + 1*149.90 = 329.70
        assertThat(kit.totalPrice()).isEqualByComparingTo("329.70");
        assertThat(kit.items()).hasSize(2);
    }

    @Test
    void recalculatesTotalPriceOnUpdate() {
        UUID tenantId = setUpTenant("aurora");
        Product shirt = component(tenantId, "P0001", "89.90");
        var kit = kitProductService.create(tenantId, request("KIT001",
                List.of(new KitItemInput(shirt.getId(), new BigDecimal("1")))));

        Product pants = component(tenantId, "P0002", "149.90");
        var updated = kitProductService.update(kit.id(), request("KIT001",
                List.of(new KitItemInput(pants.getId(), new BigDecimal("3")))));

        assertThat(updated.totalPrice()).isEqualByComparingTo("449.70");
        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().get(0).componentProductId()).isEqualTo(pants.getId());
    }

    @Test
    void rejectsAnUnknownComponent() {
        UUID tenantId = setUpTenant("aurora");

        assertThatThrownBy(() -> kitProductService.create(tenantId, request("KIT001",
                List.of(new KitItemInput(UUID.randomUUID(), BigDecimal.ONE)))))
                .isInstanceOf(ProductValidationException.class);
    }

    @Test
    void rejectsAnotherKitAsAComponent() {
        UUID tenantId = setUpTenant("aurora");
        Product shirt = component(tenantId, "P0001", "89.90");
        var innerKit = kitProductService.create(tenantId, request("KIT001",
                List.of(new KitItemInput(shirt.getId(), BigDecimal.ONE))));

        assertThatThrownBy(() -> kitProductService.create(tenantId, request("KIT002",
                List.of(new KitItemInput(innerKit.id(), BigDecimal.ONE)))))
                .isInstanceOf(ProductValidationException.class);
    }

    @Test
    void rejectsAVariationParentAsAComponent() {
        UUID tenantId = setUpTenant("aurora");
        Product parent = new Product();
        parent.setTenantId(tenantId);
        parent.setType(ProductType.VARIATION_PARENT);
        parent.setName("Camiseta com Variação");
        parent.setSku("V0001");
        parent.setSalePrice(new BigDecimal("50.00"));
        productRepository.saveAndFlush(parent);

        assertThatThrownBy(() -> kitProductService.create(tenantId, request("KIT001",
                List.of(new KitItemInput(parent.getId(), BigDecimal.ONE)))))
                .isInstanceOf(ProductValidationException.class);
    }

    @Test
    void rejectsDuplicateSku() {
        UUID tenantId = setUpTenant("aurora");
        Product shirt = component(tenantId, "P0001", "89.90");
        kitProductService.create(tenantId, request("KIT001", List.of(new KitItemInput(shirt.getId(), BigDecimal.ONE))));

        assertThatThrownBy(() -> kitProductService.create(tenantId, request("KIT001",
                List.of(new KitItemInput(shirt.getId(), BigDecimal.ONE)))))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void deletingAKitDoesNotDeleteItsComponents() {
        UUID tenantId = setUpTenant("aurora");
        Product shirt = component(tenantId, "P0001", "89.90");
        var kit = kitProductService.create(tenantId, request("KIT001",
                List.of(new KitItemInput(shirt.getId(), BigDecimal.ONE))));

        kitProductService.delete(kit.id());

        assertThatThrownBy(() -> kitProductService.findById(kit.id())).isInstanceOf(ProductNotFoundException.class);
        assertThat(productRepository.findById(shirt.getId())).isPresent();
    }

    @Test
    void deletingAComponentStillReferencedByAKitIsBlockedByTheDatabase() {
        UUID tenantId = setUpTenant("aurora");
        Product shirt = component(tenantId, "P0001", "89.90");
        kitProductService.create(tenantId, request("KIT001", List.of(new KitItemInput(shirt.getId(), BigDecimal.ONE))));

        // A plain native DELETE exercises the product_kit_item FK directly, without
        // Hibernate's own cascade/dirty-checking machinery (irrelevant here) in the way.
        UUID componentId = shirt.getId();
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("DELETE FROM product WHERE id = :id")
                    .setParameter("id", componentId)
                    .executeUpdate();
        }).hasMessageContaining("product_kit_item_component_product_id_fkey");
    }

    @Test
    void simpleProductServiceCannotSeeOrDeleteAKitById() {
        UUID tenantId = setUpTenant("aurora");
        Product shirt = component(tenantId, "P0001", "89.90");
        var kit = kitProductService.create(tenantId, request("KIT001",
                List.of(new KitItemInput(shirt.getId(), BigDecimal.ONE))));

        assertThat(productRepository.findByIdAndType(kit.id(), ProductType.PRODUCT)).isEmpty();
    }
}
