package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.produto.dto.ProdutoRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class ProdutoServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProdutoService produtoService;
    @Autowired EntityManager entityManager;

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());
        return tenant.getId();
    }

    private ProdutoRequest request(String sku, BigDecimal precoVenda) {
        return new ProdutoRequest(
                "Camiseta Polo Masculina", sku, "7891234567890", "Marca Alpha", "Vestuário",
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
}
