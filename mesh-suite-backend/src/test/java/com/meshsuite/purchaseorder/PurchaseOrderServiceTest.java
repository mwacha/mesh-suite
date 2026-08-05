package com.meshsuite.purchaseorder;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.purchaseorder.dto.PurchaseOrderItemRequest;
import com.meshsuite.purchaseorder.dto.PurchaseOrderRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class PurchaseOrderServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
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
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Tecidos Aurora");
        p.getPapeis().add(PapelParceiro.FORNECEDOR);
        return parceiroRepository.saveAndFlush(p).getId();
    }

    private UUID criarCliente(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p).getId();
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

        assertThrows(com.meshsuite.auth.PermissionDeniedException.class,
                () -> purchaseOrderService.list(null, null, PageRequest.of(0, 10)));
    }
}
