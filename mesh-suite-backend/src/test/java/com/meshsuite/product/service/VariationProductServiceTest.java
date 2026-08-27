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
        return new VariationChildInput(id, sku, null, new BigDecimal(price), null, new BigDecimal("10"), null, null, "M", null, null, null);
    }

    private VariationParentRequest request(String sku, List<VariationChildInput> children) {
        return new VariationParentRequest("Camiseta Polo", sku, null, null,
                new BigDecimal("89.90"), ProductStatus.ACTIVE, "Descrição", null, children, null, null);
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
    void defaultsSaleMultipleToOneForBothParentAndChildrenWhenNotProvided() {
        UUID tenantId = setUpTenant("aurora");

        var parent = variationProductService.create(tenantId, request("V0001", List.of(child(null, "V0001-P", "79.90"))));

        assertThat(parent.saleMultiple()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(parent.children().get(0).saleMultiple()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void appliesAnExplicitSaleMultipleToParentAndChildIndependently() {
        UUID tenantId = setUpTenant("aurora");
        VariationChildInput childInput = new VariationChildInput(
                null, "V0001-P", null, new BigDecimal("79.90"), null, new BigDecimal("10"), null, null, "M", null,
                new BigDecimal("2"), null);
        VariationParentRequest request = new VariationParentRequest("Camiseta Polo", "V0001", null, null,
                new BigDecimal("89.90"), ProductStatus.ACTIVE, "Descrição", null, List.of(childInput),
                new BigDecimal("5"), null);

        var parent = variationProductService.create(tenantId, request);

        assertThat(parent.saleMultiple()).isEqualByComparingTo("5");
        assertThat(parent.children().get(0).saleMultiple()).isEqualByComparingTo("2");
    }

    @Test
    void persistsAndReturnsTheVariationAxesThatGeneratedTheMatrix() {
        UUID tenantId = setUpTenant("aurora");
        var axes = List.of(
                new com.meshsuite.product.dto.VariationAxisInput("Tamanho", List.of("P", "M")),
                new com.meshsuite.product.dto.VariationAxisInput("Cor", List.of("Branco", "Vermelho")));
        VariationParentRequest request = new VariationParentRequest("Camiseta Polo", "V0001", null, null,
                new BigDecimal("89.90"), ProductStatus.ACTIVE, "Descrição", null,
                List.of(child(null, "V0001-P", "79.90")), null, axes);

        var created = variationProductService.create(tenantId, request);

        assertThat(created.variationAxes()).hasSize(2);
        assertThat(created.variationAxes().get(0).name()).isEqualTo("Tamanho");
        assertThat(created.variationAxes().get(0).values()).containsExactly("P", "M");
        assertThat(created.variationAxes().get(1).name()).isEqualTo("Cor");
        assertThat(created.variationAxes().get(1).values()).containsExactly("Branco", "Vermelho");

        var found = variationProductService.findById(created.id());
        assertThat(found.variationAxes()).isEqualTo(created.variationAxes());
    }

    @Test
    void persistsEachChildsOwnCombinationOfAxisValues() {
        // Only "Tamanho" has a column of its own (size); a value like "VERMELHA" on a
        // COR axis is not a colorway cadastro, so without variationValues it would be
        // unrecoverable on reload and the child could not be matched back to the
        // combination that generated it.
        UUID tenantId = setUpTenant("aurora");
        var axes = List.of(
                new com.meshsuite.product.dto.VariationAxisInput("Tamanho", List.of("40")),
                new com.meshsuite.product.dto.VariationAxisInput("COR", List.of("VERMELHA")));
        VariationChildInput childInput = new VariationChildInput(null, "2408-40-VERMELHA", null,
                new BigDecimal("28.25"), null, new BigDecimal("10"), null, null, "40", null, null,
                List.of("40", "VERMELHA"));
        VariationParentRequest request = new VariationParentRequest("Calcinha Conforto", "2408", null, null,
                new BigDecimal("28.25"), ProductStatus.ACTIVE, "Descrição", null, List.of(childInput), null, axes);

        var created = variationProductService.create(tenantId, request);

        assertThat(created.children()).hasSize(1);
        assertThat(created.children().get(0).variationValues()).containsExactly("40", "VERMELHA");

        var found = variationProductService.findById(created.id());
        assertThat(found.children().get(0).variationValues()).containsExactly("40", "VERMELHA");
    }

    @Test
    void returnsAnEmptyVariationValuesListWhenTheChildHasNoStoredCombination() {
        UUID tenantId = setUpTenant("aurora");
        var created = variationProductService.create(tenantId,
                request("V0001", List.of(child(null, "V0001-P", "79.90"))));

        assertThat(created.children().get(0).variationValues()).isEmpty();
    }

    @Test
    void returnsAnEmptyVariationAxesListWhenNoneWereProvided() {
        UUID tenantId = setUpTenant("aurora");

        var created = variationProductService.create(tenantId, request("V0001", List.of(child(null, "V0001-P", "79.90"))));

        assertThat(created.variationAxes()).isEmpty();
    }

    @Test
    void updateReplacesTheStoredVariationAxes() {
        UUID tenantId = setUpTenant("aurora");
        var initialAxes = List.of(new com.meshsuite.product.dto.VariationAxisInput("Tamanho", List.of("P")));
        VariationParentRequest initialRequest = new VariationParentRequest("Camiseta Polo", "V0001", null, null,
                new BigDecimal("89.90"), ProductStatus.ACTIVE, "Descrição", null,
                List.of(child(null, "V0001-P", "79.90")), null, initialAxes);
        var created = variationProductService.create(tenantId, initialRequest);

        var novosAxes = List.of(new com.meshsuite.product.dto.VariationAxisInput("Tamanho", List.of("P", "M", "G")));
        VariationParentRequest updateRequest = new VariationParentRequest("Camiseta Polo", "V0001", null, null,
                new BigDecimal("89.90"), ProductStatus.ACTIVE, "Descrição", null,
                List.of(child(null, "V0001-P", "79.90"), child(null, "V0001-M", "84.90"), child(null, "V0001-G", "89.90")),
                null, novosAxes);

        var updated = variationProductService.update(created.id(), updateRequest);

        assertThat(updated.variationAxes()).hasSize(1);
        assertThat(updated.variationAxes().get(0).values()).containsExactly("P", "M", "G");
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
    void updateWholesaleReplacesEveryChildRegardlessOfId() {
        // The Tipos de Variação matrix is the source of truth for a Variação's
        // composition -- update() deletes every existing child and recreates from the
        // request on every save (same convention as Kit/PriceTable items), rather than
        // merging by id. Even a child whose sku/price is unchanged still gets a new id.
        UUID tenantId = setUpTenant("aurora");
        var created = variationProductService.create(tenantId, request("V0001",
                List.of(child(null, "V0001-P", "79.90"), child(null, "V0001-M", "79.90"))));
        UUID originalPId = created.children().get(0).id();
        UUID originalMId = created.children().get(1).id();

        entityManager.clear();

        var updated = variationProductService.update(created.id(), request("V0001", List.of(
                child(null, "V0001-P", "84.90"),
                child(null, "V0001-G", "89.90"))));

        assertThat(updated.children()).hasSize(2);
        assertThat(updated.children()).extracting("sku").containsExactlyInAnyOrder("V0001-P", "V0001-G");
        assertThat(productRepository.findById(originalPId)).isEmpty();
        assertThat(productRepository.findById(originalMId)).isEmpty();
        Product newP = productRepository.findByIdAndType(
                updated.children().stream().filter(c -> c.sku().equals("V0001-P")).findFirst().orElseThrow().id(),
                ProductType.VARIATION_CHILD).orElseThrow();
        assertThat(newP.getSalePrice()).isEqualByComparingTo("84.90");
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
