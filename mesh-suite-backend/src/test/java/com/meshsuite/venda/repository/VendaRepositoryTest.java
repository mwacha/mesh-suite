package com.meshsuite.venda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.repository.PedidoRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import com.meshsuite.venda.domain.ItemVenda;
import com.meshsuite.venda.domain.Venda;
import com.meshsuite.venda.domain.VendaContador;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class VendaRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired VendaRepository vendaRepository;
    @Autowired VendaContadorRepository vendaContadorRepository;
    @Autowired EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    private Parceiro criarCliente(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p);
    }

    private User criarVendedor(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u);
    }

    private Produto criarProduto(UUID tenantId, String sku) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("59.90"));
        return produtoRepository.saveAndFlush(p);
    }

    private Pedido criarPedido(UUID tenantId, Parceiro cliente, User vendedor, int numero) {
        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setNumero(numero);
        pedido.setCliente(cliente);
        pedido.setVendedor(vendedor);
        return pedidoRepository.saveAndFlush(pedido);
    }

    private Venda novaVenda(UUID tenantId, Pedido pedido, Parceiro cliente, User vendedor, int numero) {
        Venda venda = new Venda();
        venda.setTenantId(tenantId);
        venda.setNumero(numero);
        venda.setPedido(pedido);
        venda.setCliente(cliente);
        venda.setVendedor(vendedor);
        return venda;
    }

    @Test
    @Transactional
    void savesVendaWithItensViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Produto produto = criarProduto(tenant.getId(), "P0001");
        Pedido pedido = criarPedido(tenant.getId(), cliente, vendedor, 1);

        Venda venda = novaVenda(tenant.getId(), pedido, cliente, vendedor, 1);
        ItemVenda item = new ItemVenda();
        item.setVenda(venda);
        item.setProduto(produto);
        item.setQuantidade(new BigDecimal("2"));
        item.setValorUnitario(new BigDecimal("59.90"));
        item.setValorTotal(new BigDecimal("119.80"));
        item.setValorIcms(new BigDecimal("10.00"));
        item.setValorIpi(BigDecimal.ZERO);
        item.setValorPis(BigDecimal.ZERO);
        item.setValorCofins(BigDecimal.ZERO);
        venda.getItens().add(item);

        Venda saved = vendaRepository.saveAndFlush(venda);
        entityManager.clear();

        Venda reloaded = vendaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getItens()).hasSize(1);
        assertThat(reloaded.getItens().get(0).getValorIcms()).isEqualByComparingTo("10.00");
        assertThat(reloaded.getPedido().getId()).isEqualTo(pedido.getId());
    }

    @Test
    @Transactional
    void pedidoIdMustBeUniqueAcrossVendas() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Pedido pedido = criarPedido(tenant.getId(), cliente, vendedor, 1);

        vendaRepository.saveAndFlush(novaVenda(tenant.getId(), pedido, cliente, vendedor, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> vendaRepository.saveAndFlush(novaVenda(tenant.getId(), pedido, cliente, vendedor, 2)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Pedido pedido = criarPedido(tenant.getId(), cliente, vendedor, 1);
        vendaRepository.saveAndFlush(novaVenda(tenant.getId(), pedido, cliente, vendedor, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM venda")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void itemVendaRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Produto produto = criarProduto(tenant.getId(), "P0001");
        Pedido pedido = criarPedido(tenant.getId(), cliente, vendedor, 1);

        Venda venda = novaVenda(tenant.getId(), pedido, cliente, vendedor, 1);
        ItemVenda item = new ItemVenda();
        item.setVenda(venda);
        item.setProduto(produto);
        item.setQuantidade(BigDecimal.ONE);
        item.setValorUnitario(new BigDecimal("59.90"));
        item.setValorTotal(new BigDecimal("59.90"));
        item.setValorIcms(BigDecimal.ZERO);
        item.setValorIpi(BigDecimal.ZERO);
        item.setValorPis(BigDecimal.ZERO);
        item.setValorCofins(BigDecimal.ZERO);
        venda.getItens().add(item);
        Venda saved = vendaRepository.saveAndFlush(venda);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM item_venda WHERE venda_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void vendaContadorRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        VendaContador contador = new VendaContador();
        contador.setTenantId(tenant.getId());
        contador.setProximoNumero(1);
        vendaContadorRepository.saveAndFlush(contador);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM venda_contador")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
