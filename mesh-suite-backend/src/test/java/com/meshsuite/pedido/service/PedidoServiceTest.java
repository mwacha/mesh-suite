package com.meshsuite.pedido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.pedido.dto.ItemPedidoDto;
import com.meshsuite.pedido.dto.PedidoRequest;
import com.meshsuite.pedido.exception.PedidoNaoEncontradoException;
import com.meshsuite.pedido.exception.PedidoValidacaoException;
import com.meshsuite.pedido.service.PedidoService;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.repository.ProdutoRepository;
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
        caller.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

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

    @Test
    void deniesListingWhenCallerLacksOrderViewPermission() {
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
                () -> pedidoService.listar(null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }
}
