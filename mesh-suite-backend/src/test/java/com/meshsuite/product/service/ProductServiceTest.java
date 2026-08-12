package com.meshsuite.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.dto.ProductRequest;
import com.meshsuite.product.exception.ProductNotFoundException;
import com.meshsuite.product.exception.DuplicateSkuException;
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
class ProductServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProductService produtoService;
    @Autowired EntityManager entityManager;
    @Autowired UserRepository userRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User caller = new User();
        caller.setTenantId(tenant.getId());
        caller.setName("Test Caller");
        caller.setEmail("caller-" + UUID.randomUUID() + "@" + codigo + ".com.br");
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
                "Camiseta Polo Masculina", sku, "7891234567890", "Marca Alpha", null, null,
                salePrice, new BigDecimal("25.00"), ProductStatus.ACTIVE, "Descrição de teste",
                new BigDecimal("10"), MeasurementUnit.UN, new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("0.300"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"));
    }

    @Test
    void createsAndRetrievesProduct() {
        setUpTenant("aurora");

        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var buscado = produtoService.buscarPorId(criado.id());
        assertThat(buscado.name()).isEqualTo("Camiseta Polo Masculina");
        assertThat(buscado.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void rejectsDuplicateSkuInSameTenant() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        assertThrows(DuplicateSkuException.class,
                () -> produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void updatesProductKeepingItsOwnSku() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var atualizado = produtoService.atualizar(criado.id(), request("P0001", new BigDecimal("64.90")));

        assertThat(atualizado.salePrice()).isEqualByComparingTo("64.90");
    }

    @Test
    void rejectsUpdateToAnotherProductsSku() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        var segundo = produtoService.criar(TenantContext.get(), request("P0002", new BigDecimal("39.90")));

        assertThrows(DuplicateSkuException.class,
                () -> produtoService.atualizar(segundo.id(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void updatesStatusToInactive() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var atualizado = produtoService.atualizarStatus(criado.id(), ProductStatus.INACTIVE);

        assertThat(atualizado.status()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void summaryCountsByStatus() {
        setUpTenant("aurora");
        var a = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        produtoService.criar(TenantContext.get(), request("P0002", new BigDecimal("39.90")));
        produtoService.atualizarStatus(a.id(), ProductStatus.INACTIVE);

        var resumo = produtoService.resumo();

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.active()).isEqualTo(1);
        assertThat(resumo.inactive()).isEqualTo(1);
    }

    @Test
    void listsWithSearchFilter() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var pagina = produtoService.listar("camiseta", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).sku()).isEqualTo("P0001");
    }

    @Test
    void deletesProduct() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        produtoService.excluir(criado.id());

        assertThrows(ProductNotFoundException.class, () -> produtoService.buscarPorId(criado.id()));
    }

    @Test
    void sameSkuAllowedAcrossDifferentTenants() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        setUpTenant("boreal");
        var segundo = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("39.90")));

        assertThat(segundo.sku()).isEqualTo("P0001");
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
                () -> produtoService.listar(null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }
}
