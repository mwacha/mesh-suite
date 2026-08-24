package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.produto.dto.ProdutoVariacaoRequest;
import com.meshsuite.produto.dto.TipoVariacaoRequest;
import com.meshsuite.produto.dto.VarianteRequest;
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
class ProdutoVariacaoServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProdutoService produtoService;
    @Autowired ProdutoVariacaoService produtoVariacaoService;
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

    private ProdutoVariacaoRequest requestComDoisTipos() {
        return new ProdutoVariacaoRequest(
                "Camiseta Polo Masculina", "P0001", "Marca Alpha", "Vestuário", new BigDecimal("89.90"),
                StatusProduto.ATIVO, "Descrição de teste", UnidadeMedida.UN,
                List.of(new TipoVariacaoRequest("Tamanho", List.of("P", "M")),
                        new TipoVariacaoRequest("Cor", List.of("Branco"))),
                List.of(
                        new VarianteRequest(List.of("P", "Branco"), "P0001-P-BR", "7891234000001",
                                new BigDecimal("89.90"), new BigDecimal("40.00"), new BigDecimal("10"),
                                new BigDecimal("2"), new BigDecimal("50"), new BigDecimal("0.3"),
                                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2")),
                        new VarianteRequest(List.of("M", "Branco"), "P0001-M-BR", "7891234000002",
                                new BigDecimal("89.90"), new BigDecimal("40.00"), new BigDecimal("15"),
                                new BigDecimal("2"), new BigDecimal("50"), new BigDecimal("0.3"),
                                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"))));
    }

    @Test
    void criaProdutoComVariacaoERecupera() {
        UUID tenantId = setUpTenant("aurora");

        var criado = produtoVariacaoService.criar(tenantId, requestComDoisTipos());

        assertThat(criado.tiposVariacao()).hasSize(2);
        assertThat(criado.variantes()).hasSize(2);
        assertThat(criado.variantes().get(0).combinacao()).containsExactly("P", "Branco");

        var recuperado = produtoVariacaoService.buscarPorId(criado.id());
        assertThat(recuperado.nome()).isEqualTo("Camiseta Polo Masculina");
        assertThat(recuperado.variantes()).extracting("sku")
                .containsExactlyInAnyOrder("P0001-P-BR", "P0001-M-BR");
    }

    @Test
    void variantesGeramProdutosFilhosQueAparecemComoRaizExcluida() {
        UUID tenantId = setUpTenant("aurora");
        produtoVariacaoService.criar(tenantId, requestComDoisTipos());

        // The general product list must show the VARIATION_PARENT as a single
        // catalog entry, never its VARIATION_CHILD rows individually.
        var pagina = produtoService.listar(null, null, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).tipo()).isEqualTo(ProdutoTipo.VARIATION_PARENT);
    }

    @Test
    void rejeitaValoresDuplicadosDentroDoMesmoTipo() {
        UUID tenantId = setUpTenant("aurora");
        var request = new ProdutoVariacaoRequest(
                "Camiseta", "P0002", null, null, new BigDecimal("50.00"), StatusProduto.ATIVO, null, UnidadeMedida.UN,
                List.of(new TipoVariacaoRequest("Tamanho", List.of("P", "P"))),
                List.of(new VarianteRequest(List.of("P"), "P0002-P", null, new BigDecimal("50.00"), null,
                        null, null, null, null, null, null, null)));

        assertThrows(ProdutoValidacaoException.class, () -> produtoVariacaoService.criar(tenantId, request));
    }

    @Test
    void rejeitaVariantesComSkuRepetidoNaMesmaRequisicao() {
        UUID tenantId = setUpTenant("aurora");
        var request = new ProdutoVariacaoRequest(
                "Camiseta", "P0003", null, null, new BigDecimal("50.00"), StatusProduto.ATIVO, null, UnidadeMedida.UN,
                List.of(new TipoVariacaoRequest("Tamanho", List.of("P", "M"))),
                List.of(
                        new VarianteRequest(List.of("P"), "MESMO-SKU", null, new BigDecimal("50.00"), null,
                                null, null, null, null, null, null, null),
                        new VarianteRequest(List.of("M"), "MESMO-SKU", null, new BigDecimal("50.00"), null,
                                null, null, null, null, null, null, null)));

        assertThrows(ProdutoValidacaoException.class, () -> produtoVariacaoService.criar(tenantId, request));
    }

    @Test
    void rejeitaSkuDeVarianteJaUsadoPorOutroProduto() {
        UUID tenantId = setUpTenant("aurora");
        produtoService.criar(tenantId, new com.meshsuite.produto.dto.ProdutoRequest(
                "Produto Existente", "P0001-P-BR", null, null, null, new BigDecimal("10.00"), null,
                StatusProduto.ATIVO, null, BigDecimal.ZERO, UnidadeMedida.UN, null, null, null, null, null, null));

        assertThrows(SkuDuplicadoException.class, () -> produtoVariacaoService.criar(tenantId, requestComDoisTipos()));
    }
}
