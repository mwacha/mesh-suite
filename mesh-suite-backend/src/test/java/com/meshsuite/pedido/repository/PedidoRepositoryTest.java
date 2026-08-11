package com.meshsuite.pedido.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.pedido.domain.ItemPedido;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.domain.PedidoContador;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.pedido.repository.PedidoContadorRepository;
import com.meshsuite.pedido.repository.PedidoRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PedidoRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired UserRepository userRepository;
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

    private Partner criarCliente(UUID tenantId, String documento) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(documento);
        p.setTradeName("Mercado Silva");
        p.getRoles().add(PartnerRole.CUSTOMER);
        return partnerRepository.saveAndFlush(p);
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

    private Pedido novoPedido(UUID tenantId, Partner cliente, User vendedor, int numero) {
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
        Partner cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
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
        Partner cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
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
        Partner cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");

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
        Partner cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
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
        Partner cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
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
