package com.meshsuite.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.fiscal.repository.FiscalRegistrationRepository;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.pedido.domain.ItemPedido;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.pedido.dto.ItemPedidoDto;
import com.meshsuite.pedido.dto.PedidoRequest;
import com.meshsuite.pedido.repository.PedidoRepository;
import com.meshsuite.pedido.service.PedidoService;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.sale.dto.SaleResponse;
import com.meshsuite.sale.dto.SaleSummaryResponse;
import com.meshsuite.sale.exception.SaleValidationException;
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
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SaleServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PedidoService pedidoService;
    @Autowired SaleService saleService;
    @Autowired EntityManager entityManager;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private UUID setUpTenant(String code) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(code);
        tenant.setNome(code);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User caller = new User();
        caller.setTenantId(tenant.getId());
        caller.setName("Test Caller");
        caller.setEmail("caller-" + UUID.randomUUID() + "@" + code + ".com.br");
        caller.setPasswordHash("hash");
        caller.setRole(Role.ADMINISTRATIVE);
        caller.setProfile(Profile.ADMIN);
        caller.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.SALE, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.SALE, Action.CREATE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private UUID createCustomer(UUID tenantId, String document) {
        return createCustomer(tenantId, document, "Mercado Silva");
    }

    private UUID createCustomer(UUID tenantId, String document, String tradeName) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(document);
        p.setNomeFantasia(tradeName);
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p).getId();
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

    private FiscalRegistration createFiscalRegistration(UUID tenantId) {
        FiscalRegistration registration = new FiscalRegistration();
        registration.setTenantId(tenantId);
        registration.setDescription("Venda dentro do estado");
        registration.setCfop("5102");
        registration.setIcmsCst("000");
        registration.setIcmsRate(new BigDecimal("18.00"));
        registration.setIpiRate(new BigDecimal("5.00"));
        registration.setPisRate(new BigDecimal("1.65"));
        registration.setCofinsRate(new BigDecimal("7.60"));
        return fiscalRegistrationRepository.saveAndFlush(registration);
    }

    private UUID createProductWithFiscalRegistration(UUID tenantId, String sku, BigDecimal salePrice) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(salePrice);
        p.setFiscalRegistration(createFiscalRegistration(tenantId));
        return produtoRepository.saveAndFlush(p).getId();
    }

    private UUID createProductWithoutFiscalRegistration(UUID tenantId, String sku, BigDecimal salePrice) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Sem Fiscal");
        p.setSku(sku);
        p.setPrecoVenda(salePrice);
        return produtoRepository.saveAndFlush(p).getId();
    }

    private UUID createOrderInPreparation(UUID tenantId, UUID customerId, UUID salespersonId, UUID productId,
                                           BigDecimal quantity, BigDecimal unitPrice) {
        var items = List.of(new ItemPedidoDto(productId, quantity, unitPrice));
        var request = new PedidoRequest(customerId, salespersonId, null, null, BigDecimal.ZERO, items);
        var order = pedidoService.criar(tenantId, request);
        pedidoService.avancarStatus(order.id(), StatusPedido.EM_PREPARO);
        return order.id();
    }

    @Test
    void issuesOrderInPreparationCopyingItemsAndCalculatingTaxes() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProductWithFiscalRegistration(tenantId, "P0001", new BigDecimal("50.00"));
        UUID orderId = createOrderInPreparation(tenantId, customerId, salespersonId, productId,
                new BigDecimal("10"), new BigDecimal("50.00"));

        SaleResponse sale = saleService.issue(orderId);

        assertThat(sale.number()).isEqualTo(1);
        assertThat(sale.orderId()).isEqualTo(orderId);
        assertThat(sale.total()).isEqualByComparingTo("500.00");
        assertThat(sale.items()).hasSize(1);
        assertThat(sale.items().get(0).icmsAmount()).isEqualByComparingTo("90.00");
        assertThat(sale.icmsAmount()).isEqualByComparingTo("90.00");

        Pedido updatedOrder = pedidoRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(StatusPedido.FATURADO);
    }

    @Test
    void numberIncrementsSequentiallyPerTenant() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProductWithFiscalRegistration(tenantId, "P0001", new BigDecimal("50.00"));
        UUID order1 = createOrderInPreparation(tenantId, customerId, salespersonId, productId, BigDecimal.ONE, new BigDecimal("50.00"));
        UUID order2 = createOrderInPreparation(tenantId, customerId, salespersonId, productId, BigDecimal.ONE, new BigDecimal("50.00"));

        SaleResponse first = saleService.issue(order1);
        SaleResponse second = saleService.issue(order2);

        assertThat(first.number()).isEqualTo(1);
        assertThat(second.number()).isEqualTo(2);
    }

    @Test
    void rejectsIssuingAnOrderThatIsNotInPreparation() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProductWithFiscalRegistration(tenantId, "P0001", new BigDecimal("50.00"));
        var items = List.of(new ItemPedidoDto(productId, BigDecimal.ONE, new BigDecimal("50.00")));
        var order = pedidoService.criar(tenantId,
                new PedidoRequest(customerId, salespersonId, null, null, BigDecimal.ZERO, items));

        assertThrows(SaleValidationException.class, () -> saleService.issue(order.id()));
    }

    @Test
    void rejectsIssuingWhenProductHasNoFiscalRegistration() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProductWithoutFiscalRegistration(tenantId, "P0002", new BigDecimal("50.00"));
        UUID orderId = createOrderInPreparation(tenantId, customerId, salespersonId, productId, BigDecimal.ONE, new BigDecimal("50.00"));

        assertThrows(SaleValidationException.class, () -> saleService.issue(orderId));
    }

    @Test
    void issuingTheSameOrderTwiceFailsOnTheSecondAttempt() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProductWithFiscalRegistration(tenantId, "P0001", new BigDecimal("50.00"));
        UUID orderId = createOrderInPreparation(tenantId, customerId, salespersonId, productId, BigDecimal.ONE, new BigDecimal("50.00"));

        saleService.issue(orderId);

        // Second call sees the order already FATURADO (not EM_PREPARO), so it's
        // rejected by the same status guard as rejectsIssuingAnOrderThatIsNotInPreparation.
        assertThrows(SaleValidationException.class, () -> saleService.issue(orderId));
    }

    @Test
    void listsAndFindsSaleById() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerId = createCustomer(tenantId, "11222333000144");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProductWithFiscalRegistration(tenantId, "P0001", new BigDecimal("50.00"));
        UUID orderId = createOrderInPreparation(tenantId, customerId, salespersonId, productId, BigDecimal.ONE, new BigDecimal("50.00"));
        SaleResponse created = saleService.issue(orderId);

        var page = saleService.list(null, PageRequest.of(0, 10));
        var found = saleService.findById(created.id());

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(found.customerName()).isEqualTo("Mercado Silva");
    }

    @Test
    void listSortedByCustomerNameDoesNotThrow() {
        UUID tenantId = setUpTenant("aurora");
        UUID customerZeta = createCustomer(tenantId, "11222333000144", "Zeta Confeccoes");
        UUID customerAlfa = createCustomer(tenantId, "22333444000155", "Alfa Modas");
        UUID salespersonId = createSalesperson(tenantId, "marina@aurora.com.br");
        UUID productId = createProductWithFiscalRegistration(tenantId, "P0001", new BigDecimal("50.00"));
        UUID orderZeta = createOrderInPreparation(tenantId, customerZeta, salespersonId, productId,
                BigDecimal.ONE, new BigDecimal("50.00"));
        UUID orderAlfa = createOrderInPreparation(tenantId, customerAlfa, salespersonId, productId,
                BigDecimal.ONE, new BigDecimal("50.00"));
        saleService.issue(orderZeta);
        saleService.issue(orderAlfa);

        var page = saleService.list(null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "customerName")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(SaleSummaryResponse::customerName)
                .containsExactly("Alfa Modas", "Zeta Confeccoes");
    }
}
