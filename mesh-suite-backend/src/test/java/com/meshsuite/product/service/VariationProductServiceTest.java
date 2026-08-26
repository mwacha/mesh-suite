package com.meshsuite.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.VariationChildInput;
import com.meshsuite.product.dto.VariationParentRequest;
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
class VariationProductServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired VariationProductService variationProductService;
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

    private VariationChildInput child(UUID id, String sku, String price) {
        return new VariationChildInput(id, sku, null, new BigDecimal(price), null, new BigDecimal("10"), null, null, "M", null);
    }

    private VariationParentRequest request(String sku, List<VariationChildInput> children) {
        return new VariationParentRequest("Camiseta Polo", sku, "Marca Alpha", null,
                new BigDecimal("89.90"), ProductStatus.ACTIVE, "Descrição", null, children);
    }

    @Test
    void createsParentAndChildrenLinkedToIt() {
        UUID tenantId = setUpTenant("aurora");

        var parent = variationProductService.create(tenantId, request("V0001",
                List.of(child(null, "V0001-P", "79.90"), child(null, "V0001-M", "79.90"))));

        assertThat(parent.children()).hasSize(2);
        Product parentEntity = productRepository.findByIdAndType(parent.id(), ProductType.VARIATION_PARENT).orElseThrow();
        for (var childResponse : parent.children()) {
            Product childEntity = productRepository.findByIdAndType(childResponse.id(), ProductType.VARIATION_CHILD).orElseThrow();
            assertThat(childEntity.getParentProduct().getId()).isEqualTo(parentEntity.getId());
        }
    }

    @Test
    void childrenInheritNameStatusAndMeasurementUnitFromParent() {
        UUID tenantId = setUpTenant("aurora");

        var parent = variationProductService.create(tenantId, request("V0001", List.of(child(null, "V0001-P", "79.90"))));

        Product childEntity = productRepository.findByIdAndType(parent.children().get(0).id(), ProductType.VARIATION_CHILD).orElseThrow();
        assertThat(childEntity.getName()).isEqualTo("Camiseta Polo");
        assertThat(childEntity.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void rejectsDuplicateSkuAmongChildrenInTheSameRequest() {
        UUID tenantId = setUpTenant("aurora");

        assertThatThrownBy(() -> variationProductService.create(tenantId, request("V0001",
                List.of(child(null, "V0001-P", "79.90"), child(null, "V0001-P", "79.90")))))
                .isInstanceOf(ProductValidationException.class);
    }

    @Test
    void rejectsAChildSkuAlreadyUsedByAnotherProduct() {
        UUID tenantId = setUpTenant("aurora");
        variationProductService.create(tenantId, request("V0001", List.of(child(null, "TAKEN", "79.90"))));

        assertThatThrownBy(() -> variationProductService.create(tenantId, request("V0002",
                List.of(child(null, "TAKEN", "79.90")))))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void updateReplacesAddsAndRemovesChildrenByid() {
        UUID tenantId = setUpTenant("aurora");
        var created = variationProductService.create(tenantId, request("V0001",
                List.of(child(null, "V0001-P", "79.90"), child(null, "V0001-M", "79.90"))));
        UUID keptChildId = created.children().get(0).id();
        UUID removedChildId = created.children().get(1).id();

        var updated = variationProductService.update(created.id(), request("V0001", List.of(
                child(keptChildId, "V0001-P", "84.90"),
                child(null, "V0001-G", "89.90"))));

        assertThat(updated.children()).hasSize(2);
        assertThat(updated.children()).extracting("sku").containsExactlyInAnyOrder("V0001-P", "V0001-G");
        assertThat(productRepository.findById(removedChildId)).isEmpty();
        assertThat(productRepository.findById(keptChildId)).isPresent();
        Product kept = productRepository.findById(keptChildId).orElseThrow();
        assertThat(kept.getSalePrice()).isEqualByComparingTo("84.90");
    }

    @Test
    void deletingTheParentCascadesToItsChildren() {
        UUID tenantId = setUpTenant("aurora");
        var created = variationProductService.create(tenantId, request("V0001",
                List.of(child(null, "V0001-P", "79.90"))));
        UUID childId = created.children().get(0).id();

        // Without this, the child Product created above stays managed in this test's
        // single Hibernate session; deleting the parent then makes Hibernate process
        // that unrelated-but-resident entity during flush, which throws spuriously.
        // A real request has no such carryover -- delete() only ever loads the row
        // being deleted, never its children -- so this is test-only staleness, same
        // class of issue the cross-tenant controller tests clear() for.
        entityManager.clear();

        variationProductService.delete(created.id());

        assertThatThrownBy(() -> variationProductService.findById(created.id())).isInstanceOf(ProductNotFoundException.class);
        assertThat(productRepository.findById(childId)).isEmpty();
    }

    @Test
    void simpleProductServiceCannotSeeAVariationParentById() {
        UUID tenantId = setUpTenant("aurora");
        var created = variationProductService.create(tenantId, request("V0001", List.of(child(null, "V0001-P", "79.90"))));

        assertThat(productRepository.findByIdAndType(created.id(), ProductType.PRODUCT)).isEmpty();
    }
}
