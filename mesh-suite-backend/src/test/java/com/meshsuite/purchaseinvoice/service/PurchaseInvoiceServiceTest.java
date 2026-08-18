package com.meshsuite.purchaseinvoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.fiscal.repository.FiscalRegistrationRepository;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.payable.repository.AccountsPayableRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.purchaseinvoice.dto.InstallmentInput;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceRequest;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceResponse;
import com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceValidationException;
import com.meshsuite.purchaseorder.domain.PurchaseOrder;
import com.meshsuite.purchaseorder.domain.enums.PurchaseOrderStatus;
import com.meshsuite.purchaseorder.dto.PurchaseOrderItemRequest;
import com.meshsuite.purchaseorder.dto.PurchaseOrderRequest;
import com.meshsuite.purchaseorder.repository.PurchaseOrderRepository;
import com.meshsuite.purchaseorder.service.PurchaseOrderService;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.stock.repository.StockMovementRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PurchaseInvoiceServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired PurchaseInvoiceService purchaseInvoiceService;
    @Autowired StockMovementRepository stockMovementRepository;
    @Autowired AccountsPayableRepository accountsPayableRepository;
    @Autowired EntityManager entityManager;

    private UUID callerId;

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
        caller.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.PURCHASE_INVOICE, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.PURCHASE_INVOICE, Action.CREATE));
        User savedCaller = userRepository.saveAndFlush(caller);
        callerId = savedCaller.getId();

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private UUID criarFornecedor(UUID tenantId, String documento) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(documento);
        p.setTradeName("Tecidos Aurora");
        p.getRoles().add(PartnerRole.SUPPLIER);
        return partnerRepository.saveAndFlush(p).getId();
    }

    private UUID criarComprador(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Carlos Comprador");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.ADMINISTRATIVE);
        return userRepository.saveAndFlush(u).getId();
    }

    private FiscalRegistration criarCadastroFiscal(UUID tenantId) {
        FiscalRegistration registration = new FiscalRegistration();
        registration.setTenantId(tenantId);
        registration.setDescription("Compra dentro do estado");
        registration.setCfop("1102");
        registration.setIcmsCst("000");
        registration.setIcmsRate(new BigDecimal("18.00"));
        registration.setIpiRate(new BigDecimal("5.00"));
        registration.setPisRate(new BigDecimal("1.65"));
        registration.setCofinsRate(new BigDecimal("7.60"));
        return fiscalRegistrationRepository.saveAndFlush(registration);
    }

    private UUID criarProdutoComCadastroFiscal(UUID tenantId, String sku, BigDecimal precoVenda) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Tecido Algodão");
        p.setSku(sku);
        p.setSalePrice(precoVenda);
        p.setFiscalRegistration(criarCadastroFiscal(tenantId));
        return productRepository.saveAndFlush(p).getId();
    }

    private UUID criarProdutoSemCadastroFiscal(UUID tenantId, String sku, BigDecimal precoVenda) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Tecido Sem Fiscal");
        p.setSku(sku);
        p.setSalePrice(precoVenda);
        return productRepository.saveAndFlush(p).getId();
    }

    private UUID criarOrdemAberta(UUID tenantId, UUID supplierId, UUID buyerId, UUID productId,
                                   BigDecimal quantity, BigDecimal unitPrice) {
        var items = List.of(new PurchaseOrderItemRequest(productId, quantity, unitPrice));
        var request = new PurchaseOrderRequest(supplierId, buyerId, null, null, BigDecimal.ZERO, items);
        return purchaseOrderService.create(tenantId, request).id();
    }

    private PurchaseInvoiceRequest request(String invoiceNumber, BigDecimal total) {
        return new PurchaseInvoiceRequest(invoiceNumber, "1", "55",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                List.of(new InstallmentInput(total, LocalDate.of(2026, 9, 10))));
    }

    @Test
    void issuesPurchaseInvoiceCopyingItemsCalculatingTaxesAdjustingStockAndCreatingPayables() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("25.00"));
        UUID orderId = criarOrdemAberta(tenantId, supplierId, buyerId, productId,
                new BigDecimal("10"), new BigDecimal("25.00"));

        PurchaseInvoiceResponse invoice = purchaseInvoiceService.issue(orderId, request("NF-1001", new BigDecimal("250.00")), callerId);

        assertThat(invoice.number()).isEqualTo(1);
        assertThat(invoice.purchaseOrderId()).isEqualTo(orderId);
        assertThat(invoice.total()).isEqualByComparingTo("250.00");
        assertThat(invoice.items()).hasSize(1);
        assertThat(invoice.items().get(0).icmsAmount()).isEqualByComparingTo("45.00");
        assertThat(invoice.icmsAmount()).isEqualByComparingTo("45.00");

        PurchaseOrder orderAtualizado = purchaseOrderRepository.findById(orderId).orElseThrow();
        assertThat(orderAtualizado.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);

        Product produtoAtualizado = productRepository.findById(productId).orElseThrow();
        assertThat(produtoAtualizado.getStockQuantity()).isEqualByComparingTo("10");

        var movimentos = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, Pageable.ofSize(10));
        assertThat(movimentos.getContent()).hasSize(1);
        assertThat(movimentos.getContent().get(0).getReferenceId()).isEqualTo(invoice.id());

        assertThat(accountsPayableRepository.findAll()).hasSize(1);
        assertThat(accountsPayableRepository.findAll().get(0).getReferenceId()).isEqualTo(invoice.id());
        assertThat(accountsPayableRepository.findAll().get(0).getAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void numberIncrementsSequentiallyPerTenant() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("25.00"));
        UUID order1 = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));
        UUID order2 = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));

        PurchaseInvoiceResponse first = purchaseInvoiceService.issue(order1, request("NF-1001", new BigDecimal("25.00")), callerId);
        PurchaseInvoiceResponse second = purchaseInvoiceService.issue(order2, request("NF-1002", new BigDecimal("25.00")), callerId);

        assertThat(first.number()).isEqualTo(1);
        assertThat(second.number()).isEqualTo(2);
    }

    @Test
    void rejectsIssuingWhenPurchaseOrderIsNotOpen() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("25.00"));
        UUID orderId = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));
        purchaseInvoiceService.issue(orderId, request("NF-1001", new BigDecimal("25.00")), callerId);

        // Second attempt: the order is already RECEIVED from the first issuance.
        assertThatThrownBy(() -> purchaseInvoiceService.issue(orderId, request("NF-1002", new BigDecimal("25.00")), callerId))
                .isInstanceOf(PurchaseInvoiceValidationException.class)
                .hasMessageContaining("Só é possível lançar uma compra a partir de uma ordem em aberto");
    }

    @Test
    void rejectsIssuingWhenProductHasNoFiscalRegistration() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoSemCadastroFiscal(tenantId, "P0002", new BigDecimal("25.00"));
        UUID orderId = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));

        assertThatThrownBy(() -> purchaseInvoiceService.issue(orderId, request("NF-1001", new BigDecimal("25.00")), callerId))
                .isInstanceOf(PurchaseInvoiceValidationException.class)
                .hasMessageContaining("não possui cadastro fiscal aplicado");
    }

    @Test
    void rejectsDuplicateInvoiceNumberForSameSupplier() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("25.00"));
        UUID order1 = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));
        UUID order2 = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));
        purchaseInvoiceService.issue(order1, request("NF-1001", new BigDecimal("25.00")), callerId);

        assertThatThrownBy(() -> purchaseInvoiceService.issue(order2, request("NF-1001", new BigDecimal("25.00")), callerId))
                .isInstanceOf(PurchaseInvoiceValidationException.class)
                .hasMessageContaining("cadastrada para este fornecedor");
    }

    @Test
    void rejectsWhenInstallmentsSumDoesNotMatchTotal() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("25.00"));
        UUID orderId = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));

        assertThatThrownBy(() -> purchaseInvoiceService.issue(orderId, request("NF-1001", new BigDecimal("20.00")), callerId))
                .isInstanceOf(PurchaseInvoiceValidationException.class)
                .hasMessageContaining("não bate com o total da nota");
    }

    @Test
    void rejectsWhenEntryDateIsBeforeIssueDate() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("25.00"));
        UUID orderId = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));

        PurchaseInvoiceRequest invalido = new PurchaseInvoiceRequest("NF-1001", "1", "55",
                LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 10),
                List.of(new InstallmentInput(new BigDecimal("25.00"), LocalDate.of(2026, 9, 10))));

        assertThatThrownBy(() -> purchaseInvoiceService.issue(orderId, invalido, callerId))
                .isInstanceOf(PurchaseInvoiceValidationException.class)
                .hasMessageContaining("não pode ser anterior à data de emissão");
    }

    @Test
    void listsAndFindsPurchaseInvoiceById() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("25.00"));
        UUID orderId = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));
        PurchaseInvoiceResponse criada = purchaseInvoiceService.issue(orderId, request("NF-1001", new BigDecimal("25.00")), callerId);

        var pagina = purchaseInvoiceService.list(null, PageRequest.of(0, 10));
        var buscada = purchaseInvoiceService.findById(criada.id());

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(buscada.supplierName()).isEqualTo("Tecidos Aurora");
    }
}
