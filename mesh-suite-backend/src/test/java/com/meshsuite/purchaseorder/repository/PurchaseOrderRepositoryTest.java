package com.meshsuite.purchaseorder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.purchaseorder.domain.PurchaseOrder;
import com.meshsuite.purchaseorder.domain.PurchaseOrderCounter;
import com.meshsuite.purchaseorder.domain.PurchaseOrderItem;
import com.meshsuite.purchaseorder.domain.enums.PurchaseOrderStatus;
import com.meshsuite.purchaseorder.repository.PurchaseOrderCounterRepository;
import com.meshsuite.purchaseorder.repository.PurchaseOrderRepository;
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

class PurchaseOrderRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderCounterRepository purchaseOrderCounterRepository;
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

    private Parceiro criarFornecedor(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Tecidos Aurora");
        p.getPapeis().add(PapelParceiro.FORNECEDOR);
        return parceiroRepository.saveAndFlush(p);
    }

    private User criarComprador(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Carlos Comprador");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.ADMINISTRATIVE);
        return userRepository.saveAndFlush(u);
    }

    private Produto criarProduto(UUID tenantId, String sku) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Tecido Algodão");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("25.00"));
        return produtoRepository.saveAndFlush(p);
    }

    private PurchaseOrder novaOrdem(UUID tenantId, Parceiro supplier, User buyer, int number) {
        PurchaseOrder order = new PurchaseOrder();
        order.setTenantId(tenantId);
        order.setNumber(number);
        order.setSupplier(supplier);
        order.setBuyer(buyer);
        return order;
    }

    @Test
    @Transactional
    void savesPurchaseOrderWithItemsViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        Produto product = criarProduto(tenant.getId(), "P0001");

        PurchaseOrder order = novaOrdem(tenant.getId(), supplier, buyer, 1);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(order);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("10"));
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setTotalValue(new BigDecimal("250.00"));
        order.getItems().add(item);

        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        entityManager.clear();

        PurchaseOrder reloaded = purchaseOrderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PurchaseOrderStatus.OPEN);
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getItems().get(0).getTotalValue()).isEqualByComparingTo("250.00");
    }

    @Test
    @Transactional
    void removingAnItemFromTheListDeletesItViaOrphanRemoval() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        Produto product = criarProduto(tenant.getId(), "P0001");

        PurchaseOrder order = novaOrdem(tenant.getId(), supplier, buyer, 1);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(order);
        item.setProduct(product);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setTotalValue(new BigDecimal("25.00"));
        order.getItems().add(item);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);

        saved.getItems().clear();
        purchaseOrderRepository.saveAndFlush(saved);
        entityManager.clear();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_order_item WHERE purchase_order_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void numberMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");

        purchaseOrderRepository.saveAndFlush(novaOrdem(tenant.getId(), supplier, buyer, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> purchaseOrderRepository.saveAndFlush(novaOrdem(tenant.getId(), supplier, buyer, 1)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        purchaseOrderRepository.saveAndFlush(novaOrdem(tenant.getId(), supplier, buyer, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_order")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void nextNumberIncrementsAtomicallyPerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        entityManager.createNativeQuery(
                "INSERT INTO purchase_order_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                        "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenant.getId())
                .executeUpdate();

        Object first = entityManager.createNativeQuery(
                        "UPDATE purchase_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();
        Object second = entityManager.createNativeQuery(
                        "UPDATE purchase_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();

        assertThat(((Number) first).intValue()).isEqualTo(1);
        assertThat(((Number) second).intValue()).isEqualTo(2);
    }

    @Test
    @Transactional
    void purchaseOrderCounterRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        PurchaseOrderCounter counter = new PurchaseOrderCounter();
        counter.setTenantId(tenant.getId());
        counter.setNextNumber(1);
        purchaseOrderCounterRepository.saveAndFlush(counter);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_order_counter")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void purchaseOrderItemRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        Produto product = criarProduto(tenant.getId(), "P0001");

        PurchaseOrder order = novaOrdem(tenant.getId(), supplier, buyer, 1);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(order);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("10"));
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setTotalValue(new BigDecimal("250.00"));
        order.getItems().add(item);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_order_item WHERE purchase_order_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
