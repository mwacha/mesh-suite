package com.meshsuite.salesorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.salesorder.dto.SalesOrderItemRequest;
import com.meshsuite.salesorder.dto.SalesOrderRequest;
import com.meshsuite.salesorder.exception.SalesOrderNotFoundException;
import com.meshsuite.salesorder.exception.SalesOrderValidationException;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
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
class SalesOrderServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired SalesOrderService salesOrderService;
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

    private UUID createCustomer(UUID tenantId, String documento) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(documento);
        p.setTradeName("Mercado Silva");
        p.getRoles().add(PartnerRole.CUSTOMER);
        return partnerRepository.saveAndFlush(p).getId();
    }

    private UUID createSupplier(UUID tenantId, String documento) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(documento);
        p.setTradeName("Tecidos Aurora");
        p.getRoles().add(PartnerRole.SUPPLIER);
        return partnerRepository.saveAndFlush(p).getId();
    }

    private UUID createSalesperson(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u).getId();
    }

    private UUID createAdministrative(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Carlos");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.ADMINISTRATIVE);
        return userRepository.saveAndFlush(u).getId();
    }

    private UUID createProduct(UUID tenantId, String sku, BigDecimal salePrice) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Camiseta Polo");
        p.setSku(sku);
        p.setSalePrice(salePrice);
        return productRepository.saveAndFlush(p).getId();
    }

    private SalesOrderRequest request(UUID customerId, UUID salespersonId, List<SalesOrderItemRequest> items, BigDecimal discount) {
        return new SalesOrderRequest(customerId, salespersonId, null, null, discount, items);
    }

    @Test
    void createsAndRetrievesSalesOrderWithNumberAndInitialStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, new BigDecimal("2"), new BigDecimal("59.90")));

        var created = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));

        assertThat(created.number()).isEqualTo(1);
        assertThat(created.status()).isEqualTo(SalesOrderStatus.DRAFT);
        assertThat(created.items()).hasSize(1);

        var found = salesOrderService.findById(created.id());
        assertThat(found.customerName()).isEqualTo("Mercado Silva");
        assertThat(found.salespersonName()).isEqualTo("Marina");
    }

    @Test
    void numberIncrementsSequentiallyPerTenant() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));

        var first = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));
        var second = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));

        assertThat(first.number()).isEqualTo(1);
        assertThat(second.number()).isEqualTo(2);
    }

    @Test
    void numberingRestartsInDifferentTenant() {
        UUID tenantA = setUpTenant("aurora");
        UUID customerA = createCustomer(tenantA, "11222333000144");
        UUID salespersonA = createSalesperson(tenantA, "marina@aurora.com.br");
        UUID productA = createProduct(tenantA, "P0001", new BigDecimal("59.90"));
        salesOrderService.create(tenantA, request(customerA, salespersonA,
                List.of(new SalesOrderItemRequest(productA, BigDecimal.ONE, new BigDecimal("59.90"))), BigDecimal.ZERO));

        UUID tenantB = setUpTenant("boreal");
        UUID customerB = createCustomer(tenantB, "11222333000144");
        UUID salespersonB = createSalesperson(tenantB, "carla@boreal.com.br");
        UUID productB = createProduct(tenantB, "P0001", new BigDecimal("39.90"));
        var createdB = salesOrderService.create(tenantB, request(customerB, salespersonB,
                List.of(new SalesOrderItemRequest(productB, BigDecimal.ONE, new BigDecimal("39.90"))), BigDecimal.ZERO));

        assertThat(createdB.number()).isEqualTo(1);
    }

    @Test
    void rejectsCustomerWithoutCustomerRole() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = createSupplier(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));

        assertThrows(SalesOrderValidationException.class,
                () -> salesOrderService.create(tenantId, request(supplierId, salespersonId, items, BigDecimal.ZERO)));
    }

    @Test
    void rejectsSalespersonWithoutSalesRepRole() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID administrativeId = createAdministrative(tenantId, "carlos@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));

        assertThrows(SalesOrderValidationException.class,
                () -> salesOrderService.create(tenantId, request(customerId, administrativeId, items, BigDecimal.ZERO)));
    }

    @Test
    void calculatesSubtotalDiscountAndTotal() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(
                new SalesOrderItemRequest(productId, new BigDecimal("2"), new BigDecimal("59.90")),
                new SalesOrderItemRequest(productId, new BigDecimal("1"), new BigDecimal("20.00")));

        var created = salesOrderService.create(tenantId, request(customerId, salespersonId, items, new BigDecimal("10.00")));

        assertThat(created.subtotal()).isEqualByComparingTo("139.80");
        assertThat(created.total()).isEqualByComparingTo("129.80");
    }

    @Test
    void itemUnitPriceDoesNotChangeWhenProductPriceChangesLater() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        var created = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));

        Product product = productRepository.findById(productId).orElseThrow();
        product.setSalePrice(new BigDecimal("99.90"));
        productRepository.saveAndFlush(product);

        var found = salesOrderService.findById(created.id());
        assertThat(found.items().get(0).unitPrice()).isEqualByComparingTo("59.90");
    }

    @Test
    void advancesFromDraftToInPreparation() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        var created = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));

        var advanced = salesOrderService.advanceStatus(created.id(), SalesOrderStatus.IN_PREPARATION);

        assertThat(advanced.status()).isEqualTo(SalesOrderStatus.IN_PREPARATION);
    }

    @Test
    void rejectsSkippingAStatusStep() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        var created = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));

        assertThrows(SalesOrderValidationException.class,
                () -> salesOrderService.advanceStatus(created.id(), SalesOrderStatus.INVOICED));
    }

    @Test
    void rejectsRegressingStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        var created = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));
        salesOrderService.advanceStatus(created.id(), SalesOrderStatus.IN_PREPARATION);

        assertThrows(SalesOrderValidationException.class,
                () -> salesOrderService.advanceStatus(created.id(), SalesOrderStatus.DRAFT));
    }

    @Test
    void countsTallyPerStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        var a = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));
        salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));
        salesOrderService.advanceStatus(a.id(), SalesOrderStatus.IN_PREPARATION);

        var counts = salesOrderService.counts();

        assertThat(counts.total()).isEqualTo(2);
        assertThat(counts.draft()).isEqualTo(1);
        assertThat(counts.inPreparation()).isEqualTo(1);
        assertThat(counts.invoiced()).isEqualTo(0);
    }

    @Test
    void listsWithSearchFilterByNumber() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        var created = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));

        var page = salesOrderService.list(String.valueOf(created.number()), null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsWithSearchFilterByCustomer() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));

        var page = salesOrderService.list("mercado silva", null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsWithSalespersonFilter() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        User otherSalesperson = new User();
        otherSalesperson.setTenantId(tenantId);
        otherSalesperson.setName("Carla");
        otherSalesperson.setEmail("carla@aurora.com.br");
        otherSalesperson.setPasswordHash("hash");
        otherSalesperson.setRole(Role.SALES_REP);
        UUID otherSalespersonId = userRepository.saveAndFlush(otherSalesperson).getId();
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));
        salesOrderService.create(tenantId, request(customerId, otherSalespersonId, items, BigDecimal.ZERO));

        var page = salesOrderService.list(null, null, salespersonId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).salespersonName()).isEqualTo("Marina");
    }

    @Test
    void deletesSalesOrder() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        var created = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));

        salesOrderService.delete(created.id());

        assertThrows(SalesOrderNotFoundException.class, () -> salesOrderService.findById(created.id()));
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
                () -> salesOrderService.list(null, null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }

    @Test
    void rejectsInvoicingViaAdvanceStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProduct(tenantId, "P0001", new BigDecimal("59.90"));
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("59.90")));
        var created = salesOrderService.create(tenantId, request(customerId, salespersonId, items, BigDecimal.ZERO));
        salesOrderService.advanceStatus(created.id(), SalesOrderStatus.IN_PREPARATION);

        assertThrows(SalesOrderValidationException.class,
                () -> salesOrderService.advanceStatus(created.id(), SalesOrderStatus.INVOICED));
    }
}
