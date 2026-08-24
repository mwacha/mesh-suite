package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.produto.dto.ProdutoKitItemRequest;
import com.meshsuite.produto.dto.ProdutoKitRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class ProdutoKitServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProdutoService produtoService;
    @Autowired ProdutoKitService produtoKitService;
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
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private ProdutoRequest produtoSimplesRequest(String sku, BigDecimal precoVenda) {
        return new ProdutoRequest(
                "Camiseta Polo Masculina", sku, "7891234567890", "Marca Alpha", "Vestuário",
                precoVenda, new BigDecimal("25.00"), StatusProduto.ATIVO, "Descrição de teste",
                new BigDecimal("10"), UnidadeMedida.UN, new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("0.300"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"));
    }

    @Test
    void criaKitERecuperaComPrecoVendaCalculadoDosItens() {
        UUID tenantId = setUpTenant("aurora");
        var camiseta = produtoService.criar(tenantId, produtoSimplesRequest("P0001", new BigDecimal("89.90")));
        var cinto = produtoService.criar(tenantId, produtoSimplesRequest("P0002", new BigDecimal("39.90")));

        var kit = produtoKitService.criar(tenantId, new ProdutoKitRequest(
                "Kit Look Casual", "KIT001", null, UnidadeMedida.UN, StatusProduto.ATIVO, "Kit de teste",
                List.of(new ProdutoKitItemRequest(camiseta.id(), new BigDecimal("2")),
                        new ProdutoKitItemRequest(cinto.id(), new BigDecimal("1")))));

        assertThat(kit.precoVenda()).isEqualByComparingTo("219.70"); // 2*89.90 + 1*39.90
        assertThat(kit.itens()).hasSize(2);

        var recuperado = produtoKitService.buscarPorId(kit.id());
        assertThat(recuperado.nome()).isEqualTo("Kit Look Casual");
        assertThat(recuperado.precoVenda()).isEqualByComparingTo("219.70");
    }

    @Test
    void rejeitaComponenteQueNaoEhProdutoSimples() {
        UUID tenantId = setUpTenant("aurora");
        var camiseta = produtoService.criar(tenantId, produtoSimplesRequest("P0001", new BigDecimal("89.90")));
        var outroKit = produtoKitService.criar(tenantId, new ProdutoKitRequest(
                "Kit Base", "KIT000", null, UnidadeMedida.UN, StatusProduto.ATIVO, null,
                List.of(new ProdutoKitItemRequest(camiseta.id(), BigDecimal.ONE))));

        assertThrows(ProdutoValidacaoException.class, () -> produtoKitService.criar(tenantId, new ProdutoKitRequest(
                "Kit de Kit", "KIT001", null, UnidadeMedida.UN, StatusProduto.ATIVO, null,
                List.of(new ProdutoKitItemRequest(outroKit.id(), BigDecimal.ONE)))));
    }

    @Test
    void rejeitaProdutoComponenteInexistente() {
        UUID tenantId = setUpTenant("aurora");

        assertThrows(ProdutoNaoEncontradoException.class, () -> produtoKitService.criar(tenantId, new ProdutoKitRequest(
                "Kit Inválido", "KIT001", null, UnidadeMedida.UN, StatusProduto.ATIVO, null,
                List.of(new ProdutoKitItemRequest(UUID.randomUUID(), BigDecimal.ONE)))));
    }

    @Test
    void rejeitaSkuDeKitDuplicadoComProdutoExistente() {
        UUID tenantId = setUpTenant("aurora");
        var camiseta = produtoService.criar(tenantId, produtoSimplesRequest("P0001", new BigDecimal("89.90")));

        assertThrows(SkuDuplicadoException.class, () -> produtoKitService.criar(tenantId, new ProdutoKitRequest(
                "Kit Duplicado", "P0001", null, UnidadeMedida.UN, StatusProduto.ATIVO, null,
                List.of(new ProdutoKitItemRequest(camiseta.id(), BigDecimal.ONE)))));
    }

    @Test
    void listaGeralDeProdutosIncluiKitsMasResumoContaComoUmItem() {
        UUID tenantId = setUpTenant("aurora");
        var camiseta = produtoService.criar(tenantId, produtoSimplesRequest("P0001", new BigDecimal("89.90")));
        produtoKitService.criar(tenantId, new ProdutoKitRequest(
                "Kit Look Casual", "KIT001", null, UnidadeMedida.UN, StatusProduto.ATIVO, null,
                List.of(new ProdutoKitItemRequest(camiseta.id(), BigDecimal.ONE))));

        var pagina = produtoService.listar(null, null, org.springframework.data.domain.PageRequest.of(0, 10));
        var resumo = produtoService.resumo();

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(resumo.total()).isEqualTo(2);
        assertThat(pagina.getContent()).anySatisfy(p -> assertThat(p.tipo()).isEqualTo(ProdutoTipo.PRODUCT_KIT));
    }
}
