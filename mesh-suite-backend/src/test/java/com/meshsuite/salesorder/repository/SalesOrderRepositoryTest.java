package com.meshsuite.salesorder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.salesorder.domain.SalesOrderItem;
import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.SalesOrderCounter;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
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

class SalesOrderRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderCounterRepository salesOrderCounterRepository;
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

    private Partner createCustomer(UUID tenantId, String documento) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(documento);
        p.setTradeName("Mercado Silva");
        p.getRoles().add(PartnerRole.CUSTOMER);
        return partnerRepository.saveAndFlush(p);
    }

    private User createSalesperson(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u);
    }

    private Product createProduct(UUID tenantId, String sku) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Camiseta Polo");
        p.setSku(sku);
        p.setSalePrice(new BigDecimal("59.90"));
        return productRepository.saveAndFlush(p);
    }

    private SalesOrder newOrder(UUID tenantId, Partner customer, User salesperson, int number) {
        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setNumber(number);
        order.setCustomer(customer);
        order.setSalesperson(salesperson);
        return order;
    }

    @Test
    @Transactional
    void savesSalesOrderWithItemsViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Product product = createProduct(tenant.getId(), "P0001");

        SalesOrder order = newOrder(tenant.getId(), customer, salesperson, 1);
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrder(order);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("119.80"));
        order.getItems().add(item);

        SalesOrder saved = salesOrderRepository.saveAndFlush(order);
        entityManager.clear();

        SalesOrder reloaded = salesOrderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SalesOrderStatus.DRAFT);
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getItems().get(0).getTotalAmount()).isEqualByComparingTo("119.80");
    }

    @Test
    @Transactional
    void removingAnItemFromTheListDeletesItViaOrphanRemoval() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Product product = createProduct(tenant.getId(), "P0001");

        SalesOrder order = newOrder(tenant.getId(), customer, salesperson, 1);
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrder(order);
        item.setProduct(product);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("59.90"));
        order.getItems().add(item);
        SalesOrder saved = salesOrderRepository.saveAndFlush(order);

        saved.getItems().clear();
        salesOrderRepository.saveAndFlush(saved);
        entityManager.clear();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sales_order_item WHERE sales_order_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void numberMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");

        salesOrderRepository.saveAndFlush(newOrder(tenant.getId(), customer, salesperson, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> salesOrderRepository.saveAndFlush(newOrder(tenant.getId(), customer, salesperson, 1)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        salesOrderRepository.saveAndFlush(newOrder(tenant.getId(), customer, salesperson, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sales_order")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void nextNumberIncrementsAtomicallyPerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        entityManager.createNativeQuery(
                "INSERT INTO sales_order_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                        "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenant.getId())
                .executeUpdate();

        Object first = entityManager.createNativeQuery(
                        "UPDATE sales_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();
        Object second = entityManager.createNativeQuery(
                        "UPDATE sales_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();

        assertThat(((Number) first).intValue()).isEqualTo(1);
        assertThat(((Number) second).intValue()).isEqualTo(2);
    }

    @Test
    @Transactional
    void salesOrderCounterRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        SalesOrderCounter counter = new SalesOrderCounter();
        counter.setTenantId(tenant.getId());
        counter.setNextNumber(1);
        salesOrderCounterRepository.saveAndFlush(counter);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sales_order_counter")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void salesOrderItemRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Product product = createProduct(tenant.getId(), "P0001");

        SalesOrder order = newOrder(tenant.getId(), customer, salesperson, 1);
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrder(order);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("119.80"));
        order.getItems().add(item);
        SalesOrder saved = salesOrderRepository.saveAndFlush(order);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sales_order_item WHERE sales_order_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
