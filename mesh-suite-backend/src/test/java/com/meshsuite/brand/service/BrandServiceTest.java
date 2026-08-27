package com.meshsuite.brand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.brand.dto.BrandRequest;
import com.meshsuite.brand.exception.BrandInUseException;
import com.meshsuite.brand.exception.BrandNotFoundException;
import com.meshsuite.brand.exception.DuplicateBrandNameException;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
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

class BrandServiceTest extends AbstractIntegrationTest {

    @Autowired BrandService brandService;
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

    private BrandRequest request(String nome) {
        return new BrandRequest(nome, null);
    }

    @Test
    @Transactional
    void createsAndRetrievesBrand() {
        setUpTenant("aurora-corest");

        var criada = brandService.create(TenantContext.get(), request("Marca Alpha"));

        var buscada = brandService.findById(criada.id());
        assertThat(buscada.name()).isEqualTo("Marca Alpha");
        assertThat(buscada.active()).isTrue();
        assertThat(buscada.linkedProducts()).isEqualTo(0L);
    }

    @Test
    @Transactional
    void rejectsDuplicateNameOnCreate() {
        setUpTenant("aurora-corest");
        brandService.create(TenantContext.get(), request("Marca Alpha"));

        assertThatThrownBy(() -> brandService.create(TenantContext.get(), request("Marca Alpha")))
                .isInstanceOf(DuplicateBrandNameException.class);
    }

    @Test
    @Transactional
    void rejectsDuplicateNameOnUpdateAgainstAnotherBrand() {
        setUpTenant("aurora-corest");
        brandService.create(TenantContext.get(), request("Marca Alpha"));
        var outra = brandService.create(TenantContext.get(), request("Marca Beta"));

        assertThatThrownBy(() -> brandService.update(outra.id(), request("Marca Alpha")))
                .isInstanceOf(DuplicateBrandNameException.class);
    }

    @Test
    @Transactional
    void allowsUpdatingABrandWithoutChangingItsOwnName() {
        setUpTenant("aurora-corest");
        var criada = brandService.create(TenantContext.get(), request("Marca Alpha"));

        var atualizada = brandService.update(criada.id(), new BrandRequest("Marca Alpha", false));

        assertThat(atualizada.active()).isFalse();
    }

    @Test
    @Transactional
    void deletesUnusedBrand() {
        setUpTenant("aurora-corest");
        var criada = brandService.create(TenantContext.get(), request("Marca Alpha"));

        brandService.delete(criada.id());

        assertThatThrownBy(() -> brandService.findById(criada.id()))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    @Transactional
    void rejectsDeletingABrandInUseByAProduct() {
        setUpTenant("aurora-corest");
        var marca = brandService.create(TenantContext.get(), request("Marca Alpha"));
        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Camiseta Polo", "P0001", null, marca.id(), null, null,
                new BigDecimal("59.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> brandService.delete(marca.id()))
                .isInstanceOf(BrandInUseException.class);
    }

    @Test
    @Transactional
    void listFiltersByActive() {
        setUpTenant("aurora-corest");
        brandService.create(TenantContext.get(), new BrandRequest("Marca Alpha", true));
        brandService.create(TenantContext.get(), new BrandRequest("Marca Descontinuada", false));

        var ativas = brandService.list(null, true, PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("name").containsExactly("Marca Alpha");
    }

    @Test
    @Transactional
    void countsTotalActiveAndInactiveBrands() {
        setUpTenant("aurora-corest");
        brandService.create(TenantContext.get(), new BrandRequest("Marca Alpha", true));
        brandService.create(TenantContext.get(), new BrandRequest("Marca Beta", true));
        brandService.create(TenantContext.get(), new BrandRequest("Marca Descontinuada", false));

        var contagens = brandService.counts();

        assertThat(contagens.total()).isEqualTo(3L);
        assertThat(contagens.active()).isEqualTo(2L);
        assertThat(contagens.inactive()).isEqualTo(1L);
    }

    @Test
    @Transactional
    void listAggregatesLinkedProductsPerBrandInASingleBatch() {
        setUpTenant("aurora-corest");
        var alpha = brandService.create(TenantContext.get(), request("Marca Alpha"));
        var beta = brandService.create(TenantContext.get(), request("Marca Beta"));
        var semProdutos = brandService.create(TenantContext.get(), request("Marca Gamma"));

        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Camiseta Polo", "P0001", null, alpha.id(), null, null,
                new BigDecimal("59.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));
        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Camiseta Regata", "P0002", null, alpha.id(), null, null,
                new BigDecimal("39.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));
        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Calça Jeans", "P0003", null, beta.id(), null, null,
                new BigDecimal("119.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));

        var pagina = brandService.list(null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .filteredOn(b -> b.id().equals(alpha.id())).first()
                .satisfies(b -> assertThat(b.linkedProducts()).isEqualTo(2L));
        assertThat(pagina.getContent())
                .filteredOn(b -> b.id().equals(beta.id())).first()
                .satisfies(b -> assertThat(b.linkedProducts()).isEqualTo(1L));
        assertThat(pagina.getContent())
                .filteredOn(b -> b.id().equals(semProdutos.id())).first()
                .satisfies(b -> assertThat(b.linkedProducts()).isEqualTo(0L));
    }
}
