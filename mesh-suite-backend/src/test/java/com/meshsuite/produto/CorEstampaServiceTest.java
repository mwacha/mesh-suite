package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.produto.dto.CorEstampaRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorEstampaServiceTest extends AbstractIntegrationTest {

    @Autowired CorEstampaService corEstampaService;
    @Autowired ProdutoService produtoService;
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

    private CorEstampaRequest request(String nome) {
        return new CorEstampaRequest(nome, LocalDate.of(2026, 1, 1), "Descrição de teste", null);
    }

    @Test
    @Transactional
    void criaERecuperaCorEstampa() {
        setUpTenant("aurora-corest");

        var criada = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));

        var buscada = corEstampaService.buscarPorId(criada.id());
        assertThat(buscada.nome()).isEqualTo("Azul Marinho");
        assertThat(buscada.dataVigencia()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(buscada.ativo()).isTrue();
        assertThat(buscada.produtosVinculados()).isEqualTo(0L);
    }

    @Test
    @Transactional
    void rejectsDuplicateNomeOnCreate() {
        setUpTenant("aurora-corest");
        corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));

        assertThatThrownBy(() -> corEstampaService.criar(TenantContext.get(), request("Azul Marinho")))
                .isInstanceOf(CorEstampaNomeDuplicadoException.class);
    }

    @Test
    @Transactional
    void rejectsDuplicateNomeOnUpdateAgainstAnotherCorEstampa() {
        setUpTenant("aurora-corest");
        corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));
        var outra = corEstampaService.criar(TenantContext.get(), request("Vermelho Ferrari"));

        assertThatThrownBy(() -> corEstampaService.atualizar(outra.id(), request("Azul Marinho")))
                .isInstanceOf(CorEstampaNomeDuplicadoException.class);
    }

    @Test
    @Transactional
    void allowsUpdatingACorEstampaWithoutChangingItsOwnNome() {
        setUpTenant("aurora-corest");
        var criada = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));

        var atualizada = corEstampaService.atualizar(criada.id(),
                new CorEstampaRequest("Azul Marinho", LocalDate.of(2026, 3, 1), "Descrição nova", false));

        assertThat(atualizada.descricao()).isEqualTo("Descrição nova");
        assertThat(atualizada.dataVigencia()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(atualizada.ativo()).isFalse();
    }

    @Test
    @Transactional
    void deletesUnusedCorEstampa() {
        setUpTenant("aurora-corest");
        var criada = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));

        corEstampaService.excluir(criada.id());

        assertThatThrownBy(() -> corEstampaService.buscarPorId(criada.id()))
                .isInstanceOf(CorEstampaNaoEncontradaException.class);
    }

    @Test
    @Transactional
    void rejectsDeletingACorEstampaInUseByAProduto() {
        setUpTenant("aurora-corest");
        var corEstampa = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Polo", "P0001", null, null, null, corEstampa.id(),
                new BigDecimal("59.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));

        assertThatThrownBy(() -> corEstampaService.excluir(corEstampa.id()))
                .isInstanceOf(CorEstampaEmUsoException.class);
    }

    @Test
    @Transactional
    void listFiltersByAtivo() {
        setUpTenant("aurora-corest");
        corEstampaService.criar(TenantContext.get(), new CorEstampaRequest("Azul Marinho", LocalDate.of(2026, 1, 1), null, true));
        corEstampaService.criar(TenantContext.get(), new CorEstampaRequest("Descontinuada", LocalDate.of(2025, 1, 1), null, false));

        var ativas = corEstampaService.listar(null, true, PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("nome").containsExactly("Azul Marinho");
    }

    @Test
    @Transactional
    void listAggregatesProdutosVinculadosPerCorEstampaInASingleBatch() {
        setUpTenant("aurora-corest");
        var azul = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));
        var vermelho = corEstampaService.criar(TenantContext.get(), request("Vermelho Ferrari"));
        var semProdutos = corEstampaService.criar(TenantContext.get(), request("Preto"));

        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Polo", "P0001", null, null, null, azul.id(),
                new BigDecimal("59.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Regata", "P0002", null, null, null, azul.id(),
                new BigDecimal("39.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Calça Jeans", "P0003", null, null, null, vermelho.id(),
                new BigDecimal("119.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));

        var pagina = corEstampaService.listar(null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(azul.id())).first()
                .satisfies(c -> assertThat(c.produtosVinculados()).isEqualTo(2L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(vermelho.id())).first()
                .satisfies(c -> assertThat(c.produtosVinculados()).isEqualTo(1L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(semProdutos.id())).first()
                .satisfies(c -> assertThat(c.produtosVinculados()).isEqualTo(0L));
    }
}
