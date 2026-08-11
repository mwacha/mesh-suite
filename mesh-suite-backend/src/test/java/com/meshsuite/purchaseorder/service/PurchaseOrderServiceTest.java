package com.meshsuite.purchaseorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.exception.PermissionDeniedException;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.purchaseorder.domain.enums.PurchaseOrderStatus;
import com.meshsuite.purchaseorder.dto.PurchaseOrderItemRequest;
import com.meshsuite.purchaseorder.dto.PurchaseOrderRequest;
import com.meshsuite.purchaseorder.exception.PurchaseOrderNotFoundException;
import com.meshsuite.purchaseorder.exception.PurchaseOrderValidationException;
import com.meshsuite.purchaseorder.service.PurchaseOrderService;
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
class PurchaseOrderServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PurchaseOrderService purchaseOrderService;
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
        caller.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

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

    private UUID criarCliente(UUID tenantId, String documento) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(documento);
        p.setTradeName("Mercado Silva");
        p.getRoles().add(PartnerRole.CUSTOMER);
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

    private UUID criarVendedor(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u).getId();
    }

    private UUID criarProduto(UUID tenantId, String sku, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Tecido Algodão");
        p.setSku(sku);
        p.setPrecoVenda(precoVenda);
        return produtoRepository.saveAndFlush(p).getId();
    }

    private PurchaseOrderRequest request(UUID supplierId, UUID buyerId, List<PurchaseOrderItemRequest> items, BigDecimal discount) {
        return new PurchaseOrderRequest(supplierId, buyerId, null, null, discount, items);
    }

    @Test
    void createsAndRetrievesPurchaseOrderWithNumberAndInitialStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, new BigDecimal("10"), new BigDecimal("25.00")));

        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));

        assertThat(created.number()).isEqualTo(1);
        assertThat(created.status()).isEqualTo(PurchaseOrderStatus.OPEN);
        assertThat(created.items()).hasSize(1);

        var found = purchaseOrderService.findById(created.id());
        assertThat(found.supplierName()).isEqualTo("Tecidos Aurora");
        assertThat(found.buyerName()).isEqualTo("Carlos Comprador");
    }

    @Test
    void numberIncrementsSequentiallyPerTenant() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));

        var first = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        var second = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));

        assertThat(first.number()).isEqualTo(1);
        assertThat(second.number()).isEqualTo(2);
    }

    @Test
    void rejectsSupplierWithoutFornecedorPapel() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));

        assertThrows(PurchaseOrderValidationException.class,
                () -> purchaseOrderService.create(tenantId, request(clienteId, buyerId, items, BigDecimal.ZERO)));
    }

    @Test
    void rejectsBuyerWithoutAdministrativeRole() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));

        assertThrows(PurchaseOrderValidationException.class,
                () -> purchaseOrderService.create(tenantId, request(supplierId, vendedorId, items, BigDecimal.ZERO)));
    }

    @Test
    void rejectsCreationWhenLoggedInUserIsNotAdminOrAdministrative() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));

        // Tem o grant PURCHASE.CREATE, mas não o papel exigido -- prova que a checagem
        // de papel em garantirPapelAutorizado() é independente do @RequiresPermission.
        User vendedorComPermissao = new User();
        vendedorComPermissao.setTenantId(tenantId);
        vendedorComPermissao.setName("Zeca Vendedor");
        vendedorComPermissao.setEmail("zeca-" + UUID.randomUUID() + "@aurora.com.br");
        vendedorComPermissao.setPasswordHash("hash");
        vendedorComPermissao.setRole(Role.SALES_REP);
        vendedorComPermissao.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.CREATE));
        User savedVendedor = userRepository.saveAndFlush(vendedorComPermissao);

        var principal = new AuthContextService.Context(savedVendedor.getId(), tenantId, Role.SALES_REP.name());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThrows(PermissionDeniedException.class,
                () -> purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO)));
    }

    @Test
    void calculatesSubtotalDiscountAndTotal() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(
                new PurchaseOrderItemRequest(productId, new BigDecimal("10"), new BigDecimal("25.00")),
                new PurchaseOrderItemRequest(productId, new BigDecimal("5"), new BigDecimal("10.00")));

        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, new BigDecimal("20.00")));

        assertThat(created.subtotal()).isEqualByComparingTo("300.00");
        assertThat(created.total()).isEqualByComparingTo("280.00");
    }

    @Test
    void rejectsDiscountGreaterThanSubtotal() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));

        assertThrows(PurchaseOrderValidationException.class,
                () -> purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, new BigDecimal("30.00"))));
    }

    @Test
    void unitPriceOfItemDoesNotChangeWhenProductPriceChangesLater() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));

        Produto product = produtoRepository.findById(productId).orElseThrow();
        product.setPrecoVenda(new BigDecimal("99.90"));
        produtoRepository.saveAndFlush(product);

        var found = purchaseOrderService.findById(created.id());
        assertThat(found.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
    }

    @Test
    void updatesOpenPurchaseOrder() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));

        var updatedItems = List.of(new PurchaseOrderItemRequest(productId, new BigDecimal("10"), new BigDecimal("25.00")));
        var updated = purchaseOrderService.update(created.id(), request(supplierId, buyerId, updatedItems, BigDecimal.ZERO));

        assertThat(updated.total()).isEqualByComparingTo("250.00");
        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().get(0).quantity()).isEqualByComparingTo("10");
    }

    @Test
    void rejectsUpdateOnceReceived() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        purchaseOrderService.updateStatus(created.id(), PurchaseOrderStatus.RECEIVED);

        assertThrows(PurchaseOrderValidationException.class,
                () -> purchaseOrderService.update(created.id(), request(supplierId, buyerId, items, BigDecimal.ZERO)));
    }

    @Test
    void marksAsReceivedFromOpen() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));

        var updated = purchaseOrderService.updateStatus(created.id(), PurchaseOrderStatus.RECEIVED);

        assertThat(updated.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
    }

    @Test
    void cancelsFromOpen() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));

        var updated = purchaseOrderService.updateStatus(created.id(), PurchaseOrderStatus.CANCELLED);

        assertThat(updated.status()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    }

    @Test
    void rejectsStatusChangeOnceReceived() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        purchaseOrderService.updateStatus(created.id(), PurchaseOrderStatus.RECEIVED);

        assertThrows(PurchaseOrderValidationException.class,
                () -> purchaseOrderService.updateStatus(created.id(), PurchaseOrderStatus.CANCELLED));
    }

    @Test
    void countsByStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var a = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        purchaseOrderService.updateStatus(a.id(), PurchaseOrderStatus.RECEIVED);

        var counts = purchaseOrderService.counts();

        assertThat(counts.total()).isEqualTo(2);
        assertThat(counts.open()).isEqualTo(1);
        assertThat(counts.received()).isEqualTo(1);
        assertThat(counts.cancelled()).isEqualTo(0);
    }

    @Test
    void listsWithSearchFilterByNumber() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));

        var page = purchaseOrderService.list(String.valueOf(created.number()), null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void deletesPurchaseOrder() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));

        purchaseOrderService.delete(created.id());

        assertThrows(PurchaseOrderNotFoundException.class, () -> purchaseOrderService.findById(created.id()));
    }

    @Test
    void deniesListingWhenCallerLacksPurchaseViewPermission() {
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
        noPerms.setRole(Role.ADMINISTRATIVE);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(com.meshsuite.auth.exception.PermissionDeniedException.class,
                () -> purchaseOrderService.list(null, null, PageRequest.of(0, 10)));
    }
}
