package com.meshsuite.sale.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.repository.PedidoRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.sale.domain.Sale;
import com.meshsuite.sale.domain.SaleCounter;
import com.meshsuite.sale.domain.SaleItem;
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

class SaleRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SaleRepository saleRepository;
    @Autowired SaleCounterRepository saleCounterRepository;
    @Autowired EntityManager entityManager;

    private Tenant createTenant(String code) {
        Tenant t = new Tenant();
        t.setCodigo(code);
        t.setNome(code);
        return tenantRepository.saveAndFlush(t);
    }

    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    private Partner createCustomer(UUID tenantId, String document) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(document);
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
        return produtoRepository.saveAndFlush(p);
    }

    private Pedido createOrder(UUID tenantId, Partner customer, User salesperson, int number) {
        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setNumero(number);
        pedido.setCliente(customer);
        pedido.setVendedor(salesperson);
        return pedidoRepository.saveAndFlush(pedido);
    }

    private Sale newSale(UUID tenantId, Pedido order, Partner customer, User salesperson, int number) {
        Sale sale = new Sale();
        sale.setTenantId(tenantId);
        sale.setNumber(number);
        sale.setOrder(order);
        sale.setCustomer(customer);
        sale.setSalesperson(salesperson);
        return sale;
    }

    @Test
    @Transactional
    void savesSaleWithItemsViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Product product = createProduct(tenant.getId(), "P0001");
        Pedido order = createOrder(tenant.getId(), customer, salesperson, 1);

        Sale sale = newSale(tenant.getId(), order, customer, salesperson, 1);
        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("119.80"));
        item.setIcmsAmount(new BigDecimal("10.00"));
        item.setIpiAmount(BigDecimal.ZERO);
        item.setPisAmount(BigDecimal.ZERO);
        item.setCofinsAmount(BigDecimal.ZERO);
        sale.getItems().add(item);

        Sale saved = saleRepository.saveAndFlush(sale);
        entityManager.clear();

        Sale reloaded = saleRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getItems().get(0).getIcmsAmount()).isEqualByComparingTo("10.00");
        assertThat(reloaded.getOrder().getId()).isEqualTo(order.getId());
    }

    @Test
    @Transactional
    void orderIdMustBeUniqueAcrossSales() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Pedido order = createOrder(tenant.getId(), customer, salesperson, 1);

        saleRepository.saveAndFlush(newSale(tenant.getId(), order, customer, salesperson, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> saleRepository.saveAndFlush(newSale(tenant.getId(), order, customer, salesperson, 2)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Pedido order = createOrder(tenant.getId(), customer, salesperson, 1);
        saleRepository.saveAndFlush(newSale(tenant.getId(), order, customer, salesperson, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sale")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void saleItemRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Product product = createProduct(tenant.getId(), "P0001");
        Pedido order = createOrder(tenant.getId(), customer, salesperson, 1);

        Sale sale = newSale(tenant.getId(), order, customer, salesperson, 1);
        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setProduct(product);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("59.90"));
        item.setIcmsAmount(BigDecimal.ZERO);
        item.setIpiAmount(BigDecimal.ZERO);
        item.setPisAmount(BigDecimal.ZERO);
        item.setCofinsAmount(BigDecimal.ZERO);
        sale.getItems().add(item);
        Sale saved = saleRepository.saveAndFlush(sale);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sale_item WHERE sale_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void saleCounterRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        SaleCounter counter = new SaleCounter();
        counter.setTenantId(tenant.getId());
        counter.setNextNumber(1);
        saleCounterRepository.saveAndFlush(counter);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sale_counter")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
