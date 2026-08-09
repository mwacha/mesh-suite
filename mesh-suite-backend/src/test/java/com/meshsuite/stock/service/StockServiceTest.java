package com.meshsuite.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.exception.ProdutoNaoEncontradoException;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.stock.domain.enums.StockMovementOrigin;
import com.meshsuite.stock.domain.enums.StockMovementType;
import com.meshsuite.stock.exception.StockValidationException;
import com.meshsuite.stock.service.StockService;
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
class StockServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired StockService stockService;
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
        caller.getPermissions().add(new UserPermissionGrant(Module.STOCK, Action.VIEW));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private UUID criarProduto(UUID tenantId, String sku, BigDecimal quantidadeInicial) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Tecido Algodão");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("25.00"));
        p.setQuantidadeEstoque(quantidadeInicial);
        return produtoRepository.saveAndFlush(p).getId();
    }

    private UUID criarUsuarioResponsavel(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Carlos Responsável");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.ADMINISTRATIVE);
        return userRepository.saveAndFlush(u).getId();
    }

    @Test
    void increasesBalanceOnInbound() {
        UUID tenantId = setUpTenant("aurora");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("10.000"));
        UUID userId = criarUsuarioResponsavel(tenantId, "carlos@aurora.com.br");

        var movement = stockService.adjustBalance(tenantId, productId, StockMovementType.INBOUND,
                new BigDecimal("5.000"), StockMovementOrigin.MANUAL, null, userId, "Ajuste teste");

        assertThat(movement.balanceAfter()).isEqualByComparingTo("15.000");
        Produto reloaded = produtoRepository.findById(productId).orElseThrow();
        assertThat(reloaded.getQuantidadeEstoque()).isEqualByComparingTo("15.000");
    }

    @Test
    void decreasesBalanceOnOutbound() {
        UUID tenantId = setUpTenant("aurora");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("10.000"));
        UUID userId = criarUsuarioResponsavel(tenantId, "carlos@aurora.com.br");

        var movement = stockService.adjustBalance(tenantId, productId, StockMovementType.OUTBOUND,
                new BigDecimal("4.000"), StockMovementOrigin.MANUAL, null, userId, null);

        assertThat(movement.balanceAfter()).isEqualByComparingTo("6.000");
    }

    @Test
    void rejectsOutboundThatWouldGoNegative() {
        UUID tenantId = setUpTenant("aurora");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("3.000"));
        UUID userId = criarUsuarioResponsavel(tenantId, "carlos@aurora.com.br");

        assertThrows(StockValidationException.class,
                () -> stockService.adjustBalance(tenantId, productId, StockMovementType.OUTBOUND,
                        new BigDecimal("5.000"), StockMovementOrigin.MANUAL, null, userId, null));

        Produto reloaded = produtoRepository.findById(productId).orElseThrow();
        assertThat(reloaded.getQuantidadeEstoque()).isEqualByComparingTo("3.000");
    }

    @Test
    void rejectsZeroOrNegativeQuantity() {
        UUID tenantId = setUpTenant("aurora");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("10.000"));
        UUID userId = criarUsuarioResponsavel(tenantId, "carlos@aurora.com.br");

        assertThrows(StockValidationException.class,
                () -> stockService.adjustBalance(tenantId, productId, StockMovementType.INBOUND,
                        BigDecimal.ZERO, StockMovementOrigin.MANUAL, null, userId, null));
        assertThrows(StockValidationException.class,
                () -> stockService.adjustBalance(tenantId, productId, StockMovementType.INBOUND,
                        new BigDecimal("-1"), StockMovementOrigin.MANUAL, null, userId, null));
    }

    @Test
    void rejectsUnknownProduct() {
        UUID tenantId = setUpTenant("aurora");
        UUID userId = criarUsuarioResponsavel(tenantId, "carlos@aurora.com.br");

        assertThrows(ProdutoNaoEncontradoException.class,
                () -> stockService.adjustBalance(tenantId, UUID.randomUUID(), StockMovementType.INBOUND,
                        BigDecimal.ONE, StockMovementOrigin.MANUAL, null, userId, null));
    }

    @Test
    void recordsMovementWithReferenceIdAndNote() {
        UUID tenantId = setUpTenant("aurora");
        UUID productId = criarProduto(tenantId, "P0001", BigDecimal.ZERO);
        UUID userId = criarUsuarioResponsavel(tenantId, "carlos@aurora.com.br");
        UUID referenceId = UUID.randomUUID();

        var movement = stockService.adjustBalance(tenantId, productId, StockMovementType.INBOUND,
                new BigDecimal("8.000"), StockMovementOrigin.PURCHASE, referenceId, userId, "Recebimento de compra");

        assertThat(movement.referenceId()).isEqualTo(referenceId);
        assertThat(movement.note()).isEqualTo("Recebimento de compra");
        assertThat(movement.origin()).isEqualTo(StockMovementOrigin.PURCHASE);
    }

    @Test
    void historyReturnsMovementsForProductNewestFirst() {
        UUID tenantId = setUpTenant("aurora");
        UUID productId = criarProduto(tenantId, "P0001", BigDecimal.ZERO);
        UUID userId = criarUsuarioResponsavel(tenantId, "carlos@aurora.com.br");
        stockService.adjustBalance(tenantId, productId, StockMovementType.INBOUND,
                new BigDecimal("1.000"), StockMovementOrigin.MANUAL, null, userId, null);
        stockService.adjustBalance(tenantId, productId, StockMovementType.INBOUND,
                new BigDecimal("2.000"), StockMovementOrigin.MANUAL, null, userId, null);

        var page = stockService.history(productId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).quantity()).isEqualByComparingTo("2.000");
    }

    @Test
    void deniesHistoryWhenCallerLacksStockViewPermission() {
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
                () -> stockService.history(UUID.randomUUID(), PageRequest.of(0, 10)));
    }
}
