package com.meshsuite.produto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.produto.domain.enums.StatusProduto;
import com.meshsuite.produto.domain.enums.UnidadeMedida;
import com.meshsuite.produto.dto.CategoriaRequest;
import com.meshsuite.produto.exception.CategoriaEmUsoException;
import com.meshsuite.produto.exception.CategoriaNaoEncontradaException;
import com.meshsuite.produto.exception.CategoriaNomeDuplicadoException;
import com.meshsuite.produto.service.CategoriaService;
import com.meshsuite.produto.service.ProdutoService;
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

class CategoriaServiceTest extends AbstractIntegrationTest {

    @Autowired CategoriaService categoriaService;
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

    private CategoriaRequest request(String nome) {
        return new CategoriaRequest(nome, "Descrição de teste", null);
    }

    @Test
    @Transactional
    void criaERecuperaCategoria() {
        setUpTenant("aurora-cat");

        var criada = categoriaService.criar(TenantContext.get(), request("Camisas"));

        var buscada = categoriaService.buscarPorId(criada.id());
        assertThat(buscada.nome()).isEqualTo("Camisas");
        assertThat(buscada.ativo()).isTrue();
        assertThat(buscada.produtosVinculados()).isEqualTo(0L);
    }

    @Test
    @Transactional
    void rejectsDuplicateNomeOnCreate() {
        setUpTenant("aurora-cat");
        categoriaService.criar(TenantContext.get(), request("Camisas"));

        assertThatThrownBy(() -> categoriaService.criar(TenantContext.get(), request("Camisas")))
                .isInstanceOf(CategoriaNomeDuplicadoException.class);
    }

    @Test
    @Transactional
    void rejectsDuplicateNomeOnUpdateAgainstAnotherCategoria() {
        setUpTenant("aurora-cat");
        categoriaService.criar(TenantContext.get(), request("Camisas"));
        var outra = categoriaService.criar(TenantContext.get(), request("Calças"));

        assertThatThrownBy(() -> categoriaService.atualizar(outra.id(), request("Camisas")))
                .isInstanceOf(CategoriaNomeDuplicadoException.class);
    }

    @Test
    @Transactional
    void allowsUpdatingACategoriaWithoutChangingItsOwnNome() {
        setUpTenant("aurora-cat");
        var criada = categoriaService.criar(TenantContext.get(), request("Camisas"));

        var atualizada = categoriaService.atualizar(criada.id(),
                new CategoriaRequest("Camisas", "Descrição nova", false));

        assertThat(atualizada.descricao()).isEqualTo("Descrição nova");
        assertThat(atualizada.ativo()).isFalse();
    }

    @Test
    @Transactional
    void deletesUnusedCategoria() {
        setUpTenant("aurora-cat");
        var criada = categoriaService.criar(TenantContext.get(), request("Camisas"));

        categoriaService.excluir(criada.id());

        assertThatThrownBy(() -> categoriaService.buscarPorId(criada.id()))
                .isInstanceOf(CategoriaNaoEncontradaException.class);
    }

    @Test
    @Transactional
    void rejectsDeletingACategoriaInUseByAProduto() {
        setUpTenant("aurora-cat");
        var categoria = categoriaService.criar(TenantContext.get(), request("Camisas"));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Polo", "P0001", null, null, categoria.id(), null,
                new BigDecimal("59.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));

        assertThatThrownBy(() -> categoriaService.excluir(categoria.id()))
                .isInstanceOf(CategoriaEmUsoException.class);
    }

    @Test
    @Transactional
    void listFiltersByAtivo() {
        setUpTenant("aurora-cat");
        categoriaService.criar(TenantContext.get(), new CategoriaRequest("Camisas", null, true));
        categoriaService.criar(TenantContext.get(), new CategoriaRequest("Descontinuada", null, false));

        var ativas = categoriaService.listar(null, true, PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("nome").containsExactly("Camisas");
    }

    @Test
    @Transactional
    void listAggregatesProdutosVinculadosPerCategoriaInASingleBatch() {
        setUpTenant("aurora-cat");
        var camisas = categoriaService.criar(TenantContext.get(), request("Camisas"));
        var calcas = categoriaService.criar(TenantContext.get(), request("Calças"));
        var semProdutos = categoriaService.criar(TenantContext.get(), request("Acessórios"));

        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Polo", "P0001", null, null, camisas.id(), null,
                new BigDecimal("59.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Regata", "P0002", null, null, camisas.id(), null,
                new BigDecimal("39.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Calça Jeans", "P0003", null, null, calcas.id(), null,
                new BigDecimal("119.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));

        var pagina = categoriaService.listar(null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(camisas.id())).first()
                .satisfies(c -> assertThat(c.produtosVinculados()).isEqualTo(2L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(calcas.id())).first()
                .satisfies(c -> assertThat(c.produtosVinculados()).isEqualTo(1L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(semProdutos.id())).first()
                .satisfies(c -> assertThat(c.produtosVinculados()).isEqualTo(0L));
    }
}
