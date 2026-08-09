package com.meshsuite.venda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.fiscal.repository.FiscalRegistrationRepository;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.pedido.domain.ItemPedido;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.pedido.dto.ItemPedidoDto;
import com.meshsuite.pedido.dto.PedidoRequest;
import com.meshsuite.pedido.repository.PedidoRepository;
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
import com.meshsuite.venda.dto.VendaResponse;
import com.meshsuite.venda.exception.VendaValidacaoException;
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
class VendaServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PedidoService pedidoService;
    @Autowired VendaService vendaService;
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
        caller.getPermissions().add(new UserPermissionGrant(Module.SALE, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.SALE, Action.CREATE));
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

    private UUID criarVendedor(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u).getId();
    }

    private FiscalRegistration criarCadastroFiscal(UUID tenantId) {
        FiscalRegistration registration = new FiscalRegistration();
        registration.setTenantId(tenantId);
        registration.setDescription("Venda dentro do estado");
        registration.setCfop("5102");
        registration.setIcmsCst("000");
        registration.setIcmsRate(new BigDecimal("18.00"));
        registration.setIpiRate(new BigDecimal("5.00"));
        registration.setPisRate(new BigDecimal("1.65"));
        registration.setCofinsRate(new BigDecimal("7.60"));
        return fiscalRegistrationRepository.saveAndFlush(registration);
    }

    private UUID criarProdutoComCadastroFiscal(UUID tenantId, String sku, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(precoVenda);
        p.setFiscalRegistration(criarCadastroFiscal(tenantId));
        return produtoRepository.saveAndFlush(p).getId();
    }

    private UUID criarProdutoSemCadastroFiscal(UUID tenantId, String sku, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Sem Fiscal");
        p.setSku(sku);
        p.setPrecoVenda(precoVenda);
        return produtoRepository.saveAndFlush(p).getId();
    }

    private UUID criarPedidoEmPreparo(UUID tenantId, UUID clienteId, UUID vendedorId, UUID produtoId,
                                       BigDecimal quantidade, BigDecimal valorUnitario) {
        var itens = List.of(new ItemPedidoDto(produtoId, quantidade, valorUnitario));
        var request = new PedidoRequest(clienteId, vendedorId, null, null, BigDecimal.ZERO, itens);
        var pedido = pedidoService.criar(tenantId, request);
        pedidoService.avancarStatus(pedido.id(), StatusPedido.EM_PREPARO);
        return pedido.id();
    }

    @Test
    void faturaPedidoEmPreparoCopiandoItensECalculandoTributos() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        UUID pedidoId = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId,
                new BigDecimal("10"), new BigDecimal("50.00"));

        VendaResponse venda = vendaService.faturar(pedidoId);

        assertThat(venda.numero()).isEqualTo(1);
        assertThat(venda.pedidoId()).isEqualTo(pedidoId);
        assertThat(venda.total()).isEqualByComparingTo("500.00");
        assertThat(venda.itens()).hasSize(1);
        assertThat(venda.itens().get(0).valorIcms()).isEqualByComparingTo("90.00");
        assertThat(venda.valorIcms()).isEqualByComparingTo("90.00");

        Pedido pedidoAtualizado = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedidoAtualizado.getStatus()).isEqualTo(StatusPedido.FATURADO);
    }

    @Test
    void numeroIncrementaSequencialmentePorTenant() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        UUID pedido1 = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));
        UUID pedido2 = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));

        VendaResponse primeira = vendaService.faturar(pedido1);
        VendaResponse segunda = vendaService.faturar(pedido2);

        assertThat(primeira.numero()).isEqualTo(1);
        assertThat(segunda.numero()).isEqualTo(2);
    }

    @Test
    void rejeitaFaturarPedidoQueNaoEstaEmPreparo() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("50.00")));
        var pedido = pedidoService.criar(tenantId,
                new PedidoRequest(clienteId, vendedorId, null, null, BigDecimal.ZERO, itens));

        assertThrows(VendaValidacaoException.class, () -> vendaService.faturar(pedido.id()));
    }

    @Test
    void rejeitaFaturarQuandoProdutoNaoTemCadastroFiscal() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoSemCadastroFiscal(tenantId, "P0002", new BigDecimal("50.00"));
        UUID pedidoId = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));

        assertThrows(VendaValidacaoException.class, () -> vendaService.faturar(pedidoId));
    }

    @Test
    void faturarDuasVezesOMesmoPedidoFalhaNaSegundaTentativa() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        UUID pedidoId = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));

        vendaService.faturar(pedidoId);

        // Second call sees the pedido already FATURADO (not EM_PREPARO), so it's
        // rejected by the same status guard as rejeitaFaturarPedidoQueNaoEstaEmPreparo.
        assertThrows(VendaValidacaoException.class, () -> vendaService.faturar(pedidoId));
    }

    @Test
    void listaEBuscaVendaPorId() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        UUID pedidoId = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));
        VendaResponse criada = vendaService.faturar(pedidoId);

        var pagina = vendaService.listar(null, PageRequest.of(0, 10));
        var buscada = vendaService.buscarPorId(criada.id());

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(buscada.clienteNome()).isEqualTo("Mercado Silva");
    }
}
