package com.meshsuite.colorway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.colorway.dto.ColorwayRequest;
import com.meshsuite.colorway.exception.ColorwayInUseException;
import com.meshsuite.colorway.exception.ColorwayNotFoundException;
import com.meshsuite.colorway.exception.DuplicateColorwayNameException;
import com.meshsuite.colorway.service.ColorwayService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

class ColorwayServiceTest extends AbstractIntegrationTest {

    @Autowired ColorwayService colorwayService;
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

    private ColorwayRequest request(String nome) {
        return new ColorwayRequest(nome, LocalDate.of(2026, 1, 1), "Descrição de teste", null);
    }

    @Test
    @Transactional
    void createsAndRetrievesColorway() {
        setUpTenant("aurora-corest");

        var criada = colorwayService.create(TenantContext.get(), request("Azul Marinho"));

        var buscada = colorwayService.findById(criada.id());
        assertThat(buscada.name()).isEqualTo("Azul Marinho");
        assertThat(buscada.effectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(buscada.active()).isTrue();
        assertThat(buscada.linkedProducts()).isEqualTo(0L);
    }

    @Test
    @Transactional
    void rejectsDuplicateNameOnCreate() {
        setUpTenant("aurora-corest");
        colorwayService.create(TenantContext.get(), request("Azul Marinho"));

        assertThatThrownBy(() -> colorwayService.create(TenantContext.get(), request("Azul Marinho")))
                .isInstanceOf(DuplicateColorwayNameException.class);
    }

    @Test
    @Transactional
    void rejectsDuplicateNameOnUpdateAgainstAnotherColorway() {
        setUpTenant("aurora-corest");
        colorwayService.create(TenantContext.get(), request("Azul Marinho"));
        var outra = colorwayService.create(TenantContext.get(), request("Vermelho Ferrari"));

        assertThatThrownBy(() -> colorwayService.update(outra.id(), request("Azul Marinho")))
                .isInstanceOf(DuplicateColorwayNameException.class);
    }

    @Test
    @Transactional
    void allowsUpdatingAColorwayWithoutChangingItsOwnName() {
        setUpTenant("aurora-corest");
        var criada = colorwayService.create(TenantContext.get(), request("Azul Marinho"));

        var atualizada = colorwayService.update(criada.id(),
                new ColorwayRequest("Azul Marinho", LocalDate.of(2026, 3, 1), "Descrição nova", false));

        assertThat(atualizada.description()).isEqualTo("Descrição nova");
        assertThat(atualizada.effectiveDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(atualizada.active()).isFalse();
    }

    @Test
    @Transactional
    void deletesUnusedColorway() {
        setUpTenant("aurora-corest");
        var criada = colorwayService.create(TenantContext.get(), request("Azul Marinho"));

        colorwayService.delete(criada.id());

        assertThatThrownBy(() -> colorwayService.findById(criada.id()))
                .isInstanceOf(ColorwayNotFoundException.class);
    }

    @Test
    @Transactional
    void rejectsDeletingAColorwayInUseByAProduct() {
        setUpTenant("aurora-corest");
        var corEstampa = colorwayService.create(TenantContext.get(), request("Azul Marinho"));
        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Camiseta Polo", "P0001", null, null, null, corEstampa.id(),
                new BigDecimal("59.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> colorwayService.delete(corEstampa.id()))
                .isInstanceOf(ColorwayInUseException.class);
    }

    @Test
    @Transactional
    void listFiltersByActive() {
        setUpTenant("aurora-corest");
        colorwayService.create(TenantContext.get(), new ColorwayRequest("Azul Marinho", LocalDate.of(2026, 1, 1), null, true));
        colorwayService.create(TenantContext.get(), new ColorwayRequest("Descontinuada", LocalDate.of(2025, 1, 1), null, false));

        var ativas = colorwayService.list(null, true, PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("name").containsExactly("Azul Marinho");
    }

    @Test
    @Transactional
    void listAggregatesLinkedProductsPerColorwayInASingleBatch() {
        setUpTenant("aurora-corest");
        var azul = colorwayService.create(TenantContext.get(), request("Azul Marinho"));
        var vermelho = colorwayService.create(TenantContext.get(), request("Vermelho Ferrari"));
        var semProdutos = colorwayService.create(TenantContext.get(), request("Preto"));

        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Camiseta Polo", "P0001", null, null, null, azul.id(),
                new BigDecimal("59.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));
        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Camiseta Regata", "P0002", null, null, null, azul.id(),
                new BigDecimal("39.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));
        productService.create(TenantContext.get(), new com.meshsuite.product.dto.ProductRequest(
                "Calça Jeans", "P0003", null, null, null, vermelho.id(),
                new BigDecimal("119.90"), null, ProductStatus.ACTIVE, null,
                new BigDecimal("10"), MeasurementUnit.UN, null, null, null, null, null, null, null, null));

        var pagina = colorwayService.list(null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(azul.id())).first()
                .satisfies(c -> assertThat(c.linkedProducts()).isEqualTo(2L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(vermelho.id())).first()
                .satisfies(c -> assertThat(c.linkedProducts()).isEqualTo(1L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(semProdutos.id())).first()
                .satisfies(c -> assertThat(c.linkedProducts()).isEqualTo(0L));
    }

    @Test
    @Transactional
    void countsTotalActiveAndInactiveColorways() {
        setUpTenant("aurora-corest");
        colorwayService.create(TenantContext.get(), request("Azul Marinho"));
        colorwayService.create(TenantContext.get(), request("Vermelho Ferrari"));
        colorwayService.create(TenantContext.get(),
                new ColorwayRequest("Descontinuada", LocalDate.of(2025, 1, 1), null, false));

        var contagens = colorwayService.counts();

        assertThat(contagens.total()).isEqualTo(3L);
        assertThat(contagens.active()).isEqualTo(2L);
        assertThat(contagens.inactive()).isEqualTo(1L);
    }
}
