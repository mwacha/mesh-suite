package com.meshsuite.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.category.dto.CategoryRequest;
import com.meshsuite.category.exception.CategoryInUseException;
import com.meshsuite.category.exception.CategoryNotFoundException;
import com.meshsuite.category.exception.DuplicateCategoryNameException;
import com.meshsuite.category.service.CategoryService;
import com.meshsuite.product.service.SimpleProductService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

class CategoryServiceTest extends AbstractIntegrationTest {

    @Autowired CategoryService categoryService;
    @Autowired SimpleProductService productService;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User caller = new User();
        caller.setTenantId(tenant.getId());
        caller.setName("Marina");
        caller.setEmail(codigo + "@aurora.com.br");
        caller.setPasswordHash("hash");
        caller.setRole(Role.ADMIN);
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

    private CategoryRequest request(String nome) {
        return new CategoryRequest(nome, "Descrição de teste", null);
    }

    @Test
    @Transactional
    void createsAndRetrievesCategory() {
        setUpTenant("aurora-cat");

        var criada = categoryService.create(TenantContext.get(), request("Camisas"));

        var buscada = categoryService.findById(criada.id());
        assertThat(buscada.name()).isEqualTo("Camisas");
        assertThat(buscada.active()).isTrue();
        assertThat(buscada.linkedProducts()).isEqualTo(0L);
    }

    @Test
    @Transactional
    void rejectsDuplicateNameOnCreate() {
        setUpTenant("aurora-cat");
        categoryService.create(TenantContext.get(), request("Camisas"));

        assertThatThrownBy(() -> categoryService.create(TenantContext.get(), request("Camisas")))
                .isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    @Transactional
    void rejectsDuplicateNameOnUpdateAgainstAnotherCategory() {
        setUpTenant("aurora-cat");
        categoryService.create(TenantContext.get(), request("Camisas"));
        var outra = categoryService.create(TenantContext.get(), request("Calças"));

        assertThatThrownBy(() -> categoryService.update(outra.id(), request("Camisas")))
                .isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    @Transactional
    void allowsUpdatingACategoryWithoutChangingItsOwnName() {
        setUpTenant("aurora-cat");
        var criada = categoryService.create(TenantContext.get(), request("Camisas"));

        var atualizada = categoryService.update(criada.id(),
                new CategoryRequest("Camisas", "Descrição nova", false));

        assertThat(atualizada.description()).isEqualTo("Descrição nova");
        assertThat(atualizada.active()).isFalse();
    }

    @Test
    @Transactional
    void deletesUnusedCategory() {
        setUpTenant("aurora-cat");
        var criada = categoryService.create(TenantContext.get(), request("Camisas"));

        categoryService.delete(criada.id());

        assertThatThrownBy(() -> categoryService.findById(criada.id()))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @Transactional
    void rejectsDeletingACategoryInUseByAProduct() {
        setUpTenant("aurora-cat");
        var categoria = categoryService.create(TenantContext.get(), request("Camisas"));
        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Camiseta Polo", "P0001", null, null, categoria.id(), null,
                new BigDecimal("59.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> categoryService.delete(categoria.id()))
                .isInstanceOf(CategoryInUseException.class);
    }

    @Test
    @Transactional
    void listFiltersByActive() {
        setUpTenant("aurora-cat");
        categoryService.create(TenantContext.get(), new CategoryRequest("Camisas", null, true));
        categoryService.create(TenantContext.get(), new CategoryRequest("Descontinuada", null, false));

        var ativas = categoryService.list(null, true, PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("name").containsExactly("Camisas");
    }

    @Test
    @Transactional
    void listAggregatesLinkedProductsPerCategoryInASingleBatch() {
        setUpTenant("aurora-cat");
        var camisas = categoryService.create(TenantContext.get(), request("Camisas"));
        var calcas = categoryService.create(TenantContext.get(), request("Calças"));
        var semProdutos = categoryService.create(TenantContext.get(), request("Acessórios"));

        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Camiseta Polo", "P0001", null, null, camisas.id(), null,
                new BigDecimal("59.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));
        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Camiseta Regata", "P0002", null, null, camisas.id(), null,
                new BigDecimal("39.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));
        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Calça Jeans", "P0003", null, null, calcas.id(), null,
                new BigDecimal("119.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));

        var pagina = categoryService.list(null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(camisas.id())).first()
                .satisfies(c -> assertThat(c.linkedProducts()).isEqualTo(2L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(calcas.id())).first()
                .satisfies(c -> assertThat(c.linkedProducts()).isEqualTo(1L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(semProdutos.id())).first()
                .satisfies(c -> assertThat(c.linkedProducts()).isEqualTo(0L));
    }
}
