package com.meshsuite.pedido;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.pedido.dto.ItemPedidoDto;
import com.meshsuite.pedido.dto.PedidoRequest;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class PedidoServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoService pedidoService;
    @Autowired EntityManager entityManager;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());
        return tenant.getId();
    }

    private UUID criarCliente(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p).getId();
    }

    private UUID criarFornecedor(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Tecidos Aurora");
        p.getPapeis().add(PapelParceiro.FORNECEDOR);
        return parceiroRepository.saveAndFlush(p).getId();
    }

    private UUID criarVendedor(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u).getId();
    }

    private UUID criarAdministrativo(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Carlos");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.ADMINISTRATIVE);
        return userRepository.saveAndFlush(u).getId();
    }

    private UUID criarProduto(UUID tenantId, String sku, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(precoVenda);
        return produtoRepository.saveAndFlush(p).getId();
    }

    private PedidoRequest request(UUID clienteId, UUID vendedorId, List<ItemPedidoDto> itens, BigDecimal desconto) {
        return new PedidoRequest(clienteId, vendedorId, null, null, desconto, itens);
    }

    @Test
    void criaERecuperaPedidoComNumeroENoStatusInicial() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, new BigDecimal("2"), new BigDecimal("59.90")));

        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        assertThat(criado.numero()).isEqualTo(1);
        assertThat(criado.status()).isEqualTo(StatusPedido.DIGITADO);
        assertThat(criado.itens()).hasSize(1);

        var buscado = pedidoService.buscarPorId(criado.id());
        assertThat(buscado.clienteNome()).isEqualTo("Mercado Silva");
        assertThat(buscado.vendedorNome()).isEqualTo("Marina");
    }

    @Test
    void numeroIncrementaSequencialmentePorTenant() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));

        var primeiro = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));
        var segundo = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        assertThat(primeiro.numero()).isEqualTo(1);
        assertThat(segundo.numero()).isEqualTo(2);
    }

    @Test
    void numeracaoReiniciaEmTenantDiferente() {
        UUID tenantA = setUpTenant("aurora");
        UUID clienteA = criarCliente(tenantA, "11222333000144");
        UUID vendedorA = criarVendedor(tenantA, "marina@aurora.com.br");
        UUID produtoA = criarProduto(tenantA, "P0001", new BigDecimal("59.90"));
        pedidoService.criar(tenantA, request(clienteA, vendedorA,
                List.of(new ItemPedidoDto(produtoA, BigDecimal.ONE, new BigDecimal("59.90"))), BigDecimal.ZERO));

        UUID tenantB = setUpTenant("boreal");
        UUID clienteB = criarCliente(tenantB, "11222333000144");
        UUID vendedorB = criarVendedor(tenantB, "carla@boreal.com.br");
        UUID produtoB = criarProduto(tenantB, "P0001", new BigDecimal("39.90"));
        var criadoB = pedidoService.criar(tenantB, request(clienteB, vendedorB,
                List.of(new ItemPedidoDto(produtoB, BigDecimal.ONE, new BigDecimal("39.90"))), BigDecimal.ZERO));

        assertThat(criadoB.numero()).isEqualTo(1);
    }

    @Test
    void rejeitaClienteSemPapelCliente() {
        UUID tenantId = setUpTenant("aurora");
        UUID fornecedorId = criarFornecedor(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));

        assertThrows(PedidoValidacaoException.class,
                () -> pedidoService.criar(tenantId, request(fornecedorId, vendedorId, itens, BigDecimal.ZERO)));
    }

    @Test
    void rejeitaVendedorSemPapelRepresentante() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID administrativoId = criarAdministrativo(tenantId, "carlos@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));

        assertThrows(PedidoValidacaoException.class,
                () -> pedidoService.criar(tenantId, request(clienteId, administrativoId, itens, BigDecimal.ZERO)));
    }

    @Test
    void calculaSubtotalDescontoETotal() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(
                new ItemPedidoDto(produtoId, new BigDecimal("2"), new BigDecimal("59.90")),
                new ItemPedidoDto(produtoId, new BigDecimal("1"), new BigDecimal("20.00")));

        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, new BigDecimal("10.00")));

        assertThat(criado.subtotal()).isEqualByComparingTo("139.80");
        assertThat(criado.total()).isEqualByComparingTo("129.80");
    }

    @Test
    void valorUnitarioDoItemNaoMudaQuandoPrecoDoProdutoMudaDepois() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        Produto produto = produtoRepository.findById(produtoId).orElseThrow();
        produto.setPrecoVenda(new BigDecimal("99.90"));
        produtoRepository.saveAndFlush(produto);

        var buscado = pedidoService.buscarPorId(criado.id());
        assertThat(buscado.itens().get(0).valorUnitario()).isEqualByComparingTo("59.90");
    }

    @Test
    void avancaDeDigitadoParaEmPreparo() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        var avancado = pedidoService.avancarStatus(criado.id(), StatusPedido.EM_PREPARO);

        assertThat(avancado.status()).isEqualTo(StatusPedido.EM_PREPARO);
    }

    @Test
    void rejeitaPularEtapaDeStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        assertThrows(PedidoValidacaoException.class,
                () -> pedidoService.avancarStatus(criado.id(), StatusPedido.FATURADO));
    }

    @Test
    void rejeitaRetrocederStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));
        pedidoService.avancarStatus(criado.id(), StatusPedido.EM_PREPARO);

        assertThrows(PedidoValidacaoException.class,
                () -> pedidoService.avancarStatus(criado.id(), StatusPedido.DIGITADO));
    }

    @Test
    void resumoContaPorStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var a = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));
        pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));
        pedidoService.avancarStatus(a.id(), StatusPedido.EM_PREPARO);

        var resumo = pedidoService.resumo();

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.digitados()).isEqualTo(1);
        assertThat(resumo.emPreparo()).isEqualTo(1);
        assertThat(resumo.faturados()).isEqualTo(0);
    }

    @Test
    void listaComFiltroDeBuscaPorNumero() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        var pagina = pedidoService.listar(String.valueOf(criado.numero()), null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listaComFiltroDeBuscaPorCliente() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        var pagina = pedidoService.listar("mercado silva", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    @Test
    void excluiPedido() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        pedidoService.excluir(criado.id());

        assertThrows(PedidoNaoEncontradoException.class, () -> pedidoService.buscarPorId(criado.id()));
    }
}
