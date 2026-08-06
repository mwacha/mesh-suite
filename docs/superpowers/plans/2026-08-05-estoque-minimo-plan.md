# Estoque Mínimo (StockMovement) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an atomic stock-balance-adjustment mechanism on top of the existing `Produto.quantidadeEstoque` field, backed by an append-only `StockMovement` ledger, plus a single read-only history endpoint — the minimum needed for the future Compra (sub-project 5) to debit/credit stock.

**Architecture:** New backend-only package `com.meshsuite.stock`. `StockService.adjustBalance(...)` is a plain Java service method with no HTTP caller yet (Compra will call it directly when built); the only externally-reachable piece is `GET /api/stock-movements`. No frontend at all in this plan.

**Tech Stack:** Spring Boot 3.4.5/Java 21 backend (unchanged), Postgres 16 RLS, Flyway migrations `V13`/`V14`.

## Global Constraints

- New package `com.meshsuite.stock` (English — new code, not a rename of anything Portuguese). Table `stock_movement`, migration `V13`.
- `Produto.quantidadeEstoque` (already exists, `BigDecimal(12,3)`, column `quantidade_estoque`) is the balance of record — no new balance column anywhere. It must only ever be modified through `StockService.adjustBalance`, never via a direct `produtoRepository.save`.
- **Atomic adjustment, not check-then-update.** For `INBOUND`: `UPDATE produto SET quantidade_estoque = quantidade_estoque + :quantity WHERE id = :productId RETURNING quantidade_estoque`. For `OUTBOUND`: the negative-balance guard lives IN the same atomic statement's `WHERE` clause (`AND quantidade_estoque >= :quantity`), not as a separate read-then-check step — a separate check would reopen the exact race condition this pattern exists to avoid. If the `OUTBOUND` guard fails, zero rows match and the query returns no result; the service turns that into `StockValidationException`.
- `StockMovement` is append-only — no update/delete endpoint or method in this plan.
- `adjustBalance` carries no `@RequiresPermission` — it's an internal service method with no direct HTTP caller, same as how other services don't re-check a different domain's permission when reading/writing across a FK (e.g. `PedidoService` doesn't check `Module.CUSTOMER` when reading a `Parceiro`).
- Only `StockService.history(...)` (and the controller method that calls it) is permission-gated: `@RequiresPermission(module = Module.STOCK, action = Action.VIEW)`. New `Module.STOCK` enum value.
- `user_permission.module`'s Postgres CHECK constraint must be widened to include `'STOCK'` (migration `V14`) — mirrors how `V12` widened it for `PURCHASE`. Without this, no `user_permission` row can ever be inserted with `module = 'STOCK'`, even directly via SQL/API.
- **No frontend in this plan.** `Module.STOCK` is deliberately NOT added to `UserFormView.vue`'s `MODULES`/`DEFAULT_MATRIX` — there is no screen yet that would make granting it useful. Revisit when the first Estoque-domain screen is built.
- Route: `GET /api/stock-movements?productId=...` — a top-level route (not nested under `/api/produtos`, which is Produto's existing Portuguese path), matching the same top-level pattern already used by `/api/purchase-orders`.
- `StockMovementOrigin` has two values now: `MANUAL` (used by tests and any future manual-adjustment path) and `PURCHASE` (reserved for sub-project 5 — no caller uses it yet, but the field exists so Compra doesn't need a schema change later).
- `StockMovementType` has two values: `INBOUND`, `OUTBOUND`. No `TRANSFERENCIA`/`ESTOQUE_INICIAL` (out of scope, see spec section 2).
- Spec: `docs/superpowers/specs/2026-08-05-estoque-minimo-design.md`.

---

### Task 1: Backend — StockMovement domain model, migrations, and `StockService`

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V13__create_stock_movement.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V14__add_stock_to_user_permission_module_check.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/Module.java` (modify — add `STOCK`)
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/stock/StockMovementType.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/stock/StockMovementOrigin.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/stock/StockMovement.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/stock/StockMovementRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/stock/StockValidationException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/stock/dto/StockMovementResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/stock/StockService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/stock/StockMovementRepositoryTest.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/stock/StockServiceTest.java`

**Interfaces:**
- Consumes: `Produto`/`ProdutoRepository`/`ProdutoNaoEncontradoException` (existing, package `com.meshsuite.produto`), `User`/`UserRepository` (existing, package `com.meshsuite.user`).
- Produces: `StockService.adjustBalance(UUID tenantId, UUID productId, StockMovementType type, BigDecimal quantity, StockMovementOrigin origin, UUID referenceId, UUID userId, String note): StockMovementResponse` and `StockService.history(UUID productId, Pageable pageable): Page<StockMovementResponse>`. Task 2 (`StockMovementController`) consumes `history(...)` directly, with this exact signature.

- [ ] **Step 1: Write the migrations**

`V13__create_stock_movement.sql`:

```sql
CREATE TABLE stock_movement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    product_id UUID NOT NULL REFERENCES produto(id),
    type VARCHAR(10) NOT NULL CHECK (type IN ('INBOUND','OUTBOUND')),
    quantity NUMERIC(12,3) NOT NULL,
    origin VARCHAR(10) NOT NULL CHECK (origin IN ('MANUAL','PURCHASE')),
    reference_id UUID,
    balance_after NUMERIC(12,3) NOT NULL,
    user_id UUID NOT NULL REFERENCES app_user(id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movement_tenant_id ON stock_movement(tenant_id);
CREATE INDEX idx_stock_movement_product_id ON stock_movement(product_id);

ALTER TABLE stock_movement ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_movement FORCE ROW LEVEL SECURITY;

-- Own tenant_id column and own policy -- unlike item_pedido/purchase_order_item,
-- this isn't a line item of a single parent header; it's a standalone ledger
-- row in its own right, same pattern as pedido/purchase_order themselves.
CREATE POLICY stock_movement_tenant_isolation ON stock_movement
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

`V14__add_stock_to_user_permission_module_check.sql`:

```sql
ALTER TABLE user_permission DROP CONSTRAINT user_permission_module_check;

ALTER TABLE user_permission ADD CONSTRAINT user_permission_module_check
    CHECK (module IN ('CUSTOMER','PRODUCT','ORDER','USER','PURCHASE','STOCK'));
```

- [ ] **Step 2: Add `STOCK` to `Module.java`**

```java
package com.meshsuite.auth;

public enum Module {
    CUSTOMER,
    PRODUCT,
    ORDER,
    USER,
    PURCHASE,
    STOCK
}
```

- [ ] **Step 3: Write `StockMovementType.java` and `StockMovementOrigin.java`**

```java
package com.meshsuite.stock;

public enum StockMovementType {
    INBOUND,
    OUTBOUND
}
```

```java
package com.meshsuite.stock;

public enum StockMovementOrigin {
    MANUAL,
    PURCHASE
}
```

- [ ] **Step 4: Write `StockMovement.java`**

```java
package com.meshsuite.stock;

import com.meshsuite.produto.Produto;
import com.meshsuite.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movement")
@Getter
@Setter
public class StockMovement {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Produto product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockMovementType type;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockMovementOrigin origin;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 3)
    private BigDecimal balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 5: Write `StockMovementRepository.java`**

```java
package com.meshsuite.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);
}
```

- [ ] **Step 6: Write `StockValidationException.java`**

```java
package com.meshsuite.stock;

public class StockValidationException extends RuntimeException {
    public StockValidationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 7: Write `dto/StockMovementResponse.java`**

```java
package com.meshsuite.stock.dto;

import com.meshsuite.stock.StockMovementOrigin;
import com.meshsuite.stock.StockMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID productId,
        String productName,
        StockMovementType type,
        BigDecimal quantity,
        StockMovementOrigin origin,
        UUID referenceId,
        BigDecimal balanceAfter,
        UUID userId,
        String userName,
        String note,
        Instant createdAt) {
}
```

- [ ] **Step 8: Write `StockService.java`**

```java
package com.meshsuite.stock;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoNaoEncontradoException;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.stock.dto.StockMovementResponse;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class StockService {

    private final StockMovementRepository stockMovementRepository;
    private final ProdutoRepository produtoRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public StockService(StockMovementRepository stockMovementRepository, ProdutoRepository produtoRepository,
                         UserRepository userRepository, EntityManager entityManager) {
        this.stockMovementRepository = stockMovementRepository;
        this.produtoRepository = produtoRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public StockMovementResponse adjustBalance(UUID tenantId, UUID productId, StockMovementType type,
                                                BigDecimal quantity, StockMovementOrigin origin,
                                                UUID referenceId, UUID userId, String note) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockValidationException("A quantidade deve ser maior que zero");
        }

        Produto product = produtoRepository.findById(productId)
                .orElseThrow(ProdutoNaoEncontradoException::new);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new StockValidationException("Usuário responsável não encontrado"));

        BigDecimal newBalance = applyAtomicAdjustment(productId, type, quantity);

        StockMovement movement = new StockMovement();
        movement.setTenantId(tenantId);
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setOrigin(origin);
        movement.setReferenceId(referenceId);
        movement.setBalanceAfter(newBalance);
        movement.setUser(user);
        movement.setNote(note);

        return toResponse(stockMovementRepository.saveAndFlush(movement));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.STOCK, action = Action.VIEW)
    public Page<StockMovementResponse> history(UUID productId, Pageable pageable) {
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable).map(this::toResponse);
    }

    // Atomic UPDATE ... RETURNING against the product's own balance column --
    // never read-then-write. For OUTBOUND, the WHERE clause itself guards
    // against a negative result (quantidade_estoque >= :quantity): if the
    // guard fails, zero rows match and the query returns no result, all
    // inside the same atomic statement -- a separate check-then-update would
    // reopen the exact race condition this pattern exists to avoid.
    private BigDecimal applyAtomicAdjustment(UUID productId, StockMovementType type, BigDecimal quantity) {
        String sql = type == StockMovementType.INBOUND
                ? "UPDATE produto SET quantidade_estoque = quantidade_estoque + :quantity " +
                        "WHERE id = :productId RETURNING quantidade_estoque"
                : "UPDATE produto SET quantidade_estoque = quantidade_estoque - :quantity " +
                        "WHERE id = :productId AND quantidade_estoque >= :quantity RETURNING quantidade_estoque";

        List<?> result = entityManager.createNativeQuery(sql)
                .setParameter("quantity", quantity)
                .setParameter("productId", productId)
                .getResultList();

        if (result.isEmpty()) {
            throw new StockValidationException("Saldo insuficiente para esta saída");
        }
        return (BigDecimal) result.get(0);
    }

    private StockMovementResponse toResponse(StockMovement m) {
        return new StockMovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getNome(), m.getType(),
                m.getQuantity(), m.getOrigin(), m.getReferenceId(), m.getBalanceAfter(),
                m.getUser().getId(), m.getUser().getName(), m.getNote(), m.getCreatedAt());
    }
}
```

- [ ] **Step 9: Write the failing repository RLS test**

```java
package com.meshsuite.stock;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired UserRepository userRepository;
    @Autowired StockMovementRepository stockMovementRepository;
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

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Produto produto = new Produto();
        produto.setTenantId(tenant.getId());
        produto.setNome("Tecido Algodão");
        produto.setSku("P0001");
        produto.setPrecoVenda(new BigDecimal("25.00"));
        produto = produtoRepository.saveAndFlush(produto);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Carlos");
        user.setEmail("carlos@aurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMINISTRATIVE);
        user = userRepository.saveAndFlush(user);

        StockMovement movement = new StockMovement();
        movement.setTenantId(tenant.getId());
        movement.setProduct(produto);
        movement.setType(StockMovementType.INBOUND);
        movement.setQuantity(new BigDecimal("5.000"));
        movement.setOrigin(StockMovementOrigin.MANUAL);
        movement.setBalanceAfter(new BigDecimal("5.000"));
        movement.setUser(user);
        stockMovementRepository.saveAndFlush(movement);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM stock_movement")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 10: Write the failing service test**

```java
package com.meshsuite.stock;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoNaoEncontradoException;
import com.meshsuite.produto.ProdutoRepository;
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

        assertThrows(com.meshsuite.auth.PermissionDeniedException.class,
                () -> stockService.history(UUID.randomUUID(), PageRequest.of(0, 10)));
    }
}
```

- [ ] **Step 11: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=StockMovementRepositoryTest,StockServiceTest`
Expected: PASS (1/1 + 8/8).

- [ ] **Step 12: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V13__create_stock_movement.sql \
        mesh-suite-backend/src/main/resources/db/migration/V14__add_stock_to_user_permission_module_check.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/Module.java \
        mesh-suite-backend/src/main/java/com/meshsuite/stock/ \
        mesh-suite-backend/src/test/java/com/meshsuite/stock/
git commit -m "feat(stock): add StockMovement ledger, Module.STOCK and StockService"
```

---

### Task 2: Backend — `StockMovementController`, exception handling, integration tests

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/stock/StockMovementController.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/stock/StockMovementControllerTest.java`

**Interfaces:**
- Consumes: `StockService.history(UUID, Pageable): Page<StockMovementResponse>` and `StockService.adjustBalance(...)` (Task 1, used directly by the test to seed data — there is no write endpoint to call instead).

- [ ] **Step 1: Write `StockMovementController.java`**

```java
package com.meshsuite.stock;

import com.meshsuite.stock.dto.StockMovementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements")
public class StockMovementController {

    private final StockService stockService;

    public StockMovementController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public Page<StockMovementResponse> history(
            @RequestParam UUID productId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return stockService.history(productId, pageable);
    }
}
```

- [ ] **Step 2: Register `StockValidationException` in `GlobalExceptionHandler`**

Append this handler to the end of the class, right after the existing `handlePurchaseOrderValidation` method, before the final closing brace:

```java
    @ExceptionHandler(com.meshsuite.stock.StockValidationException.class)
    public ResponseEntity<Map<String, String>> handleStockValidation(
            com.meshsuite.stock.StockValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

`ProdutoNaoEncontradoException` (thrown by `StockService.adjustBalance` when the product doesn't exist) is already registered in `GlobalExceptionHandler` → 404 — no new handler needed for it.

- [ ] **Step 3: Write the failing controller test**

```java
package com.meshsuite.stock;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.auth.Module;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class StockMovementControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired StockService stockService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, UUID tenantId, UUID productId, UUID userId) {
    }

    private Contexto loginAndSetUp(String codigo, String email, String cnpjEmpresa) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial(codigo + " Ltda");
        empresa.setCnpj(cnpjEmpresa);
        empresaRepository.saveAndFlush(empresa);

        User userLogin = new User();
        userLogin.setTenantId(tenant.getId());
        userLogin.setName("Carlos");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMIN);
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.STOCK, Action.VIEW));
        User savedUser = userRepository.saveAndFlush(userLogin);

        Produto produto = new Produto();
        produto.setTenantId(tenant.getId());
        produto.setNome("Tecido Algodão");
        produto.setSku("P0001-" + codigo);
        produto.setPrecoVenda(new BigDecimal("25.00"));
        produtoRepository.saveAndFlush(produto);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Contexto(token, tenant.getId(), produto.getId(), savedUser.getId());
    }

    @Test
    void returnsMovementHistoryForProduct() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        stockService.adjustBalance(ctx.tenantId(), ctx.productId(), StockMovementType.INBOUND,
                new BigDecimal("10.000"), StockMovementOrigin.MANUAL, null, ctx.userId(), "Carga inicial");

        mockMvc.perform(get("/api/stock-movements").param("productId", ctx.productId().toString()).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].quantity").value(10.0))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(10.0))
                .andExpect(jsonPath("$.content[0].note").value("Carga inicial"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/stock-movements").param("productId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyWithoutStockViewPermissionIsForbidden() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao");
        tenant.setNome("sem-permissao");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("sem-permissao Ltda");
        empresa.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(empresa);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Sem Permissão");
        user.setEmail("sem-permissao@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.VIEWER);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sem-permissao@aurora.com.br\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/stock-movements").param("productId", UUID.randomUUID().toString()).cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=StockMovementControllerTest`
Expected: PASS (3/3).

- [ ] **Step 5: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS, no regressions anywhere (Parceiro/Produto/Pedido/Auth/User/PurchaseOrder/Stock all green).

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/stock/StockMovementController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/stock/StockMovementControllerTest.java
git commit -m "feat(stock): add StockMovementController and history endpoint"
```
