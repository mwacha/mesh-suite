package com.meshsuite.purchaseinvoice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.purchaseinvoice.domain.PurchaseInvoice;
import com.meshsuite.purchaseinvoice.domain.PurchaseInvoiceCounter;
import com.meshsuite.purchaseinvoice.domain.PurchaseInvoiceItem;
import com.meshsuite.purchaseorder.domain.PurchaseOrder;
import com.meshsuite.purchaseorder.repository.PurchaseOrderRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PurchaseInvoiceRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseInvoiceRepository purchaseInvoiceRepository;
    @Autowired PurchaseInvoiceCounterRepository purchaseInvoiceCounterRepository;
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

    private Partner criarFornecedor(UUID tenantId, String documento) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(documento);
        p.setTradeName("Tecidos Aurora");
        p.getRoles().add(PartnerRole.SUPPLIER);
        return partnerRepository.saveAndFlush(p);
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

    private Product criarProduto(UUID tenantId, String sku) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Tecido Algodão");
        p.setSku(sku);
        p.setSalePrice(new BigDecimal("25.00"));
        return productRepository.saveAndFlush(p);
    }

    private PurchaseOrder criarOrdemAberta(UUID tenantId, Partner supplier, User buyer, int number) {
        PurchaseOrder order = new PurchaseOrder();
        order.setTenantId(tenantId);
        order.setNumber(number);
        order.setSupplier(supplier);
        order.setBuyer(buyer);
        return purchaseOrderRepository.saveAndFlush(order);
    }

    private PurchaseInvoice novaCompra(UUID tenantId, PurchaseOrder order, Partner supplier, int number, String invoiceNumber) {
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setTenantId(tenantId);
        invoice.setNumber(number);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setSeries("1");
        invoice.setModel("55");
        invoice.setPurchaseOrder(order);
        invoice.setSupplier(supplier);
        invoice.setIssueDate(LocalDate.of(2026, 8, 10));
        invoice.setEntryDate(LocalDate.of(2026, 8, 12));
        return invoice;
    }

    @Test
    @Transactional
    void savesPurchaseInvoiceWithItemsViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        Product product = criarProduto(tenant.getId(), "P0001");
        PurchaseOrder order = criarOrdemAberta(tenant.getId(), supplier, buyer, 1);

        PurchaseInvoice invoice = novaCompra(tenant.getId(), order, supplier, 1, "NF-1001");
        PurchaseInvoiceItem item = new PurchaseInvoiceItem();
        item.setPurchaseInvoice(invoice);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("10"));
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setTotalValue(new BigDecimal("250.00"));
        item.setIcmsAmount(new BigDecimal("45.00"));
        item.setIpiAmount(BigDecimal.ZERO);
        item.setPisAmount(BigDecimal.ZERO);
        item.setCofinsAmount(BigDecimal.ZERO);
        invoice.getItems().add(item);

        PurchaseInvoice saved = purchaseInvoiceRepository.saveAndFlush(invoice);
        entityManager.clear();

        PurchaseInvoice reloaded = purchaseInvoiceRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getItems().get(0).getIcmsAmount()).isEqualByComparingTo("45.00");
        assertThat(reloaded.getPurchaseOrder().getId()).isEqualTo(order.getId());
    }

    @Test
    @Transactional
    void purchaseOrderIdMustBeUniqueAcrossPurchaseInvoices() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        PurchaseOrder order = criarOrdemAberta(tenant.getId(), supplier, buyer, 1);

        purchaseInvoiceRepository.saveAndFlush(novaCompra(tenant.getId(), order, supplier, 1, "NF-1001"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> purchaseInvoiceRepository.saveAndFlush(novaCompra(tenant.getId(), order, supplier, 2, "NF-1002")));
    }

    @Test
    @Transactional
    void supplierAndInvoiceNumberMustBeUniqueTogether() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        PurchaseOrder order1 = criarOrdemAberta(tenant.getId(), supplier, buyer, 1);
        PurchaseOrder order2 = criarOrdemAberta(tenant.getId(), supplier, buyer, 2);

        purchaseInvoiceRepository.saveAndFlush(novaCompra(tenant.getId(), order1, supplier, 1, "NF-1001"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> purchaseInvoiceRepository.saveAndFlush(novaCompra(tenant.getId(), order2, supplier, 2, "NF-1001")));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        PurchaseOrder order = criarOrdemAberta(tenant.getId(), supplier, buyer, 1);
        purchaseInvoiceRepository.saveAndFlush(novaCompra(tenant.getId(), order, supplier, 1, "NF-1001"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_invoice")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void purchaseInvoiceItemRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        Product product = criarProduto(tenant.getId(), "P0001");
        PurchaseOrder order = criarOrdemAberta(tenant.getId(), supplier, buyer, 1);

        PurchaseInvoice invoice = novaCompra(tenant.getId(), order, supplier, 1, "NF-1001");
        PurchaseInvoiceItem item = new PurchaseInvoiceItem();
        item.setPurchaseInvoice(invoice);
        item.setProduct(product);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setTotalValue(new BigDecimal("25.00"));
        item.setIcmsAmount(BigDecimal.ZERO);
        item.setIpiAmount(BigDecimal.ZERO);
        item.setPisAmount(BigDecimal.ZERO);
        item.setCofinsAmount(BigDecimal.ZERO);
        invoice.getItems().add(item);
        PurchaseInvoice saved = purchaseInvoiceRepository.saveAndFlush(invoice);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_invoice_item WHERE purchase_invoice_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void purchaseInvoiceCounterRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        PurchaseInvoiceCounter counter = new PurchaseInvoiceCounter();
        counter.setTenantId(tenant.getId());
        counter.setNextNumber(1);
        purchaseInvoiceCounterRepository.saveAndFlush(counter);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_invoice_counter")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
