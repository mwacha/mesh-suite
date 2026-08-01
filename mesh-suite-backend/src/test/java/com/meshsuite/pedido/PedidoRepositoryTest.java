package com.meshsuite.pedido;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PedidoContadorRepository pedidoContadorRepository;
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

    private Usuario criarVendedor(UUID tenantId, String email) {
        Usuario u = new Usuario();
        u.setTenantId(tenantId);
        u.setNome("Marina");
        u.setEmail(email);
        u.setSenhaHash("hash");
        u.setPapel(Papel.REPRESENTANTE);
        return usuarioRepository.saveAndFlush(u);
    }

    private Produto criarProduto(UUID tenantId, String sku) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("59.90"));
        return produtoRepository.saveAndFlush(p);
    }

    private Pedido novoPedido(UUID tenantId, Parceiro cliente, Usuario vendedor, int numero) {
        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setNumero(numero);
        pedido.setCliente(cliente);
        pedido.setVendedor(vendedor);
        return pedido;
    }

    @Test
    @Transactional
    void savesPedidoWithItensViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        Usuario vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Produto produto = criarProduto(tenant.getId(), "P0001");

        Pedido pedido = novoPedido(tenant.getId(), cliente, vendedor, 1);
        ItemPedido item = new ItemPedido();
        item.setPedido(pedido);
        item.setProduto(produto);
        item.setQuantidade(new BigDecimal("2"));
        item.setValorUnitario(new BigDecimal("59.90"));
        item.setValorTotal(new BigDecimal("119.80"));
        pedido.getItens().add(item);

        Pedido saved = pedidoRepository.saveAndFlush(pedido);
        entityManager.clear();

        Pedido reloaded = pedidoRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusPedido.DIGITADO);
        assertThat(reloaded.getItens()).hasSize(1);
        assertThat(reloaded.getItens().get(0).getValorTotal()).isEqualByComparingTo("119.80");
    }

    @Test
    @Transactional
    void removingAnItemFromTheListDeletesItViaOrphanRemoval() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        Usuario vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Produto produto = criarProduto(tenant.getId(), "P0001");

        Pedido pedido = novoPedido(tenant.getId(), cliente, vendedor, 1);
        ItemPedido item = new ItemPedido();
        item.setPedido(pedido);
        item.setProduto(produto);
        item.setQuantidade(BigDecimal.ONE);
        item.setValorUnitario(new BigDecimal("59.90"));
        item.setValorTotal(new BigDecimal("59.90"));
        pedido.getItens().add(item);
        Pedido saved = pedidoRepository.saveAndFlush(pedido);

        saved.getItens().clear();
        pedidoRepository.saveAndFlush(saved);
        entityManager.clear();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM item_pedido WHERE pedido_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void numeroMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        Usuario vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");

        pedidoRepository.saveAndFlush(novoPedido(tenant.getId(), cliente, vendedor, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> pedidoRepository.saveAndFlush(novoPedido(tenant.getId(), cliente, vendedor, 1)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        Usuario vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        pedidoRepository.saveAndFlush(novoPedido(tenant.getId(), cliente, vendedor, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM pedido")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void proximoNumeroIncrementsAtomicallyPerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        entityManager.createNativeQuery(
                "INSERT INTO pedido_contador (tenant_id, proximo_numero) VALUES (:tenantId, 1) " +
                        "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenant.getId())
                .executeUpdate();

        Object primeiro = entityManager.createNativeQuery(
                        "UPDATE pedido_contador SET proximo_numero = proximo_numero + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING proximo_numero - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();
        Object segundo = entityManager.createNativeQuery(
                        "UPDATE pedido_contador SET proximo_numero = proximo_numero + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING proximo_numero - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();

        assertThat(((Number) primeiro).intValue()).isEqualTo(1);
        assertThat(((Number) segundo).intValue()).isEqualTo(2);
    }

    @Test
    @Transactional
    void pedidoContadorRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        PedidoContador contador = new PedidoContador();
        contador.setTenantId(tenant.getId());
        contador.setProximoNumero(1);
        pedidoContadorRepository.saveAndFlush(contador);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM pedido_contador")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void itemPedidoRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        Usuario vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Produto produto = criarProduto(tenant.getId(), "P0001");

        Pedido pedido = novoPedido(tenant.getId(), cliente, vendedor, 1);
        ItemPedido item = new ItemPedido();
        item.setPedido(pedido);
        item.setProduto(produto);
        item.setQuantidade(new BigDecimal("2"));
        item.setValorUnitario(new BigDecimal("59.90"));
        item.setValorTotal(new BigDecimal("119.80"));
        pedido.getItens().add(item);
        Pedido saved = pedidoRepository.saveAndFlush(pedido);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM item_pedido WHERE pedido_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
