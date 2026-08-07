package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.produto.dto.ProdutoRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class ProdutoServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProdutoService produtoService;
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

    private ProdutoRequest request(String sku, BigDecimal precoVenda) {
        return new ProdutoRequest(
                "Camiseta Polo Masculina", sku, "7891234567890", "Marca Alpha", null,
                precoVenda, new BigDecimal("25.00"), StatusProduto.ATIVO, "Descrição de teste",
                new BigDecimal("10"), UnidadeMedida.UN, new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("0.300"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"));
    }

    @Test
    void criaERecuperaProduto() {
        setUpTenant("aurora");

        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var buscado = produtoService.buscarPorId(criado.id());
        assertThat(buscado.nome()).isEqualTo("Camiseta Polo Masculina");
        assertThat(buscado.status()).isEqualTo(StatusProduto.ATIVO);
    }

    @Test
    void rejeitaSkuDuplicadoNoMesmoTenant() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        assertThrows(SkuDuplicadoException.class,
                () -> produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void atualizaProdutoMantendoOProprioSku() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var atualizado = produtoService.atualizar(criado.id(), request("P0001", new BigDecimal("64.90")));

        assertThat(atualizado.precoVenda()).isEqualByComparingTo("64.90");
    }

    @Test
    void rejeitaAtualizacaoParaSkuDeOutroProduto() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        var segundo = produtoService.criar(TenantContext.get(), request("P0002", new BigDecimal("39.90")));

        assertThrows(SkuDuplicadoException.class,
                () -> produtoService.atualizar(segundo.id(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void atualizaStatusParaInativo() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var atualizado = produtoService.atualizarStatus(criado.id(), StatusProduto.INATIVO);

        assertThat(atualizado.status()).isEqualTo(StatusProduto.INATIVO);
    }

    @Test
    void resumoContaPorStatus() {
        setUpTenant("aurora");
        var a = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        produtoService.criar(TenantContext.get(), request("P0002", new BigDecimal("39.90")));
        produtoService.atualizarStatus(a.id(), StatusProduto.INATIVO);

        var resumo = produtoService.resumo();

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.ativos()).isEqualTo(1);
        assertThat(resumo.inativos()).isEqualTo(1);
    }

    @Test
    void listaComFiltroDeBusca() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var pagina = produtoService.listar("camiseta", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).sku()).isEqualTo("P0001");
    }

    @Test
    void excluiProduto() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        produtoService.excluir(criado.id());

        assertThrows(ProdutoNaoEncontradoException.class, () -> produtoService.buscarPorId(criado.id()));
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

        assertThrows(com.meshsuite.auth.PermissionDeniedException.class,
                () -> produtoService.listar(null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }
}
