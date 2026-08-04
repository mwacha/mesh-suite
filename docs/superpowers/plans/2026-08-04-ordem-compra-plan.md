# Ordem de Compra (PurchaseOrder) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Ordem de Compra (PurchaseOrder) — an internal, non-fiscal purchase order document (fornecedor, comprador, itens, desconto/total, status Aberta/Recebida/Cancelada) — as sub-project 1 of the "Compras" initiative.

**Architecture:** Mirrors the existing Pedido slice almost exactly (entity + item + per-tenant counter, RLS via the same `EXISTS`-on-parent pattern, `@RequiresPermission`-gated service, `Specification`-based search, list+form Vue views). The two deliberate differences: (1) status is a two-way branch from `OPEN` (`RECEIVED` or `CANCELLED`), not a linear chain like Pedido's `DIGITADO→EM_PREPARO→FATURADO`, so the status-update endpoint takes an explicit target status rather than "advance to next"; (2) this is new code, named in English per the standing directive (Pedido/Parceiro/Produto stay Portuguese — this is not a rename of them).

**Tech Stack:** Spring Boot 3.4.5/Java 21 backend (unchanged), Vue 3 + TypeScript + Vite frontend (unchanged), Postgres 16 RLS, Flyway migration `V11`.

## Global Constraints

- New backend package `com.meshsuite.purchaseorder`; new tables `purchase_order`, `purchase_order_item`, `purchase_order_counter`. All entity/field/method names in English (this is new code, not a rename of Pedido/Parceiro/Produto, which stay Portuguese).
- UI-visible text, route paths, and route names stay Portuguese, matching the sibling slices exactly: `/compras`, `/compras/novo`, `/compras/:id/editar` with route names `compras`, `compras-novo`, `compras-editar`.
- Internal Vue `<script setup>` local variable/function names (`form`, `erros`, `erroGeral`, `salvando`, `fornecedorBusca`, etc.) stay Portuguese, matching the real precedent already set in `UserFormView.vue` (English file/type names, Portuguese local script variables) — do not translate these to English.
- Permission module: new `Module.PURCHASE` enum value, covering this slice (and the future Compra nota-fiscal slice). Every `PurchaseOrderService` method is `@RequiresPermission(module = Module.PURCHASE, action = ...)`.
- Status: `PurchaseOrderStatus { OPEN, RECEIVED, CANCELLED }`. Only `OPEN` may transition, to either `RECEIVED` or `CANCELLED` — not a "next status" chain. Once `RECEIVED` or `CANCELLED`, the order is terminal: no further status changes, no editing of supplier/buyer/items (return `PurchaseOrderValidationException` from `update()` too, not just `updateStatus()`).
- Fornecedor validation: must have `PapelParceiro.FORNECEDOR` in `Parceiro.papeis` — no active-status check (matches `PedidoService.buscarClienteValido`'s real precedent, which checks only papel, not status).
- Comprador ("buyer") validation: must have `Role.ADMINISTRATIVE` — no active-status check (matches `PedidoService.buscarVendedorValido`'s real precedent).
- **Deliberate deviation from Pedido**: discount must not exceed subtotal (`PurchaseOrderValidationException` if it does) — this is a real PRD-07 rule (`Compra`'s "regra de negócio 6") that Pedido's own PRD never had preserved; do not treat this as an inconsistency to "fix" back to Pedido's unchecked behavior.
- Physical delete is supported (`DELETE /api/purchase-orders/{id}`), matching the real Pedido/Parceiro/Produto precedent (all three have hard delete; only User doesn't, due to an FK without cascade that doesn't apply here).
- No cronograma de entregas parciais, no condição de pagamento, no tabela de preço, no telefone field — see spec section 2 for why each is out of scope.
- No structural link to a future `Compra` entity yet — do not add a nullable FK "just in case."
- Numbering: sequential per tenant via `purchase_order_counter`, same atomic `UPDATE ... RETURNING` pattern as `pedido_contador` — never `COUNT(*)`/`MAX(number)+1`.
- Spec: `docs/superpowers/specs/2026-08-04-ordem-compra-design.md`.

---

### Task 1: Backend — PurchaseOrder domain model (migration, entities, repositories)

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V11__create_purchase_order.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderStatus.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrder.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderItem.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderCounter.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderCounterRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/PurchaseOrderRepositoryTest.java`

**Interfaces:**
- Produces: `PurchaseOrder` (fields: `id`, `tenantId`, `number: Integer`, `supplier: Parceiro`, `buyer: User`, `orderDate: LocalDate`, `expectedDeliveryDate: LocalDate`, `status: PurchaseOrderStatus`, `discount/subtotal/total: BigDecimal`, `createdAt: Instant`, `items: List<PurchaseOrderItem>`), `PurchaseOrderItem` (fields: `id`, `purchaseOrder`, `product: Produto`, `quantity: BigDecimal`, `unitPrice: BigDecimal`, `totalValue: BigDecimal`), `PurchaseOrderRepository.countByStatus(PurchaseOrderStatus)`. Task 2 consumes all of these directly.

- [ ] **Step 1: Write the migration**

```sql
CREATE TABLE purchase_order_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE purchase_order_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY purchase_order_counter_tenant_isolation ON purchase_order_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE purchase_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    supplier_id UUID NOT NULL REFERENCES parceiro(id),
    buyer_id UUID NOT NULL REFERENCES app_user(id),
    order_date DATE NOT NULL DEFAULT CURRENT_DATE,
    expected_delivery_date DATE,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RECEIVED','CANCELLED')),
    discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_purchase_order_tenant_number ON purchase_order(tenant_id, number);
CREATE INDEX idx_purchase_order_tenant_id ON purchase_order(tenant_id);
CREATE INDEX idx_purchase_order_supplier_id ON purchase_order(supplier_id);
CREATE INDEX idx_purchase_order_buyer_id ON purchase_order(buyer_id);

ALTER TABLE purchase_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order FORCE ROW LEVEL SECURITY;

CREATE POLICY purchase_order_tenant_isolation ON purchase_order
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE purchase_order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES produto(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_value NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_purchase_order_item_purchase_order_id ON purchase_order_item(purchase_order_id);

ALTER TABLE purchase_order_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- purchase_order row's own RLS policy, matched by purchase_order_id. Same
-- pattern as item_pedido/parceiro_contato.
CREATE POLICY purchase_order_item_tenant_isolation ON purchase_order_item
    USING (EXISTS (
        SELECT 1 FROM purchase_order po
        WHERE po.id = purchase_order_item.purchase_order_id
          AND po.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

`buyer_id` references `app_user`, the table's current real name (V8 already renamed it; do not write `usuario` here).

- [ ] **Step 2: Write `PurchaseOrderStatus.java`**

```java
package com.meshsuite.purchaseorder;

public enum PurchaseOrderStatus {
    OPEN,
    RECEIVED,
    CANCELLED
}
```

- [ ] **Step 3: Write `PurchaseOrder.java`**

```java
package com.meshsuite.purchaseorder;

import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_order")
@Getter
@Setter
public class PurchaseOrder {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Integer number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Parceiro supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate = LocalDate.now();

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PurchaseOrderStatus status = PurchaseOrderStatus.OPEN;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PurchaseOrderItem> items = new ArrayList<>();
}
```

- [ ] **Step 4: Write `PurchaseOrderItem.java`**

```java
package com.meshsuite.purchaseorder;

import com.meshsuite.produto.Produto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_item")
@Getter
@Setter
public class PurchaseOrderItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Produto product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalValue;
}
```

- [ ] **Step 5: Write `PurchaseOrderCounter.java`**

```java
package com.meshsuite.purchaseorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "purchase_order_counter")
@Getter
@Setter
public class PurchaseOrderCounter {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber = 1;
}
```

- [ ] **Step 6: Write `PurchaseOrderRepository.java`**

```java
package com.meshsuite.purchaseorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {
    long countByStatus(PurchaseOrderStatus status);
}
```

- [ ] **Step 7: Write `PurchaseOrderCounterRepository.java`**

```java
package com.meshsuite.purchaseorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseOrderCounterRepository extends JpaRepository<PurchaseOrderCounter, UUID> {
}
```

- [ ] **Step 8: Write the failing repository/RLS test**

```java
package com.meshsuite.purchaseorder;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
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

class PurchaseOrderRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderCounterRepository purchaseOrderCounterRepository;
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

    private Parceiro criarFornecedor(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Tecidos Aurora");
        p.getPapeis().add(PapelParceiro.FORNECEDOR);
        return parceiroRepository.saveAndFlush(p);
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

    private Produto criarProduto(UUID tenantId, String sku) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Tecido Algodão");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("25.00"));
        return produtoRepository.saveAndFlush(p);
    }

    private PurchaseOrder novaOrdem(UUID tenantId, Parceiro supplier, User buyer, int number) {
        PurchaseOrder order = new PurchaseOrder();
        order.setTenantId(tenantId);
        order.setNumber(number);
        order.setSupplier(supplier);
        order.setBuyer(buyer);
        return order;
    }

    @Test
    @Transactional
    void savesPurchaseOrderWithItemsViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        Produto product = criarProduto(tenant.getId(), "P0001");

        PurchaseOrder order = novaOrdem(tenant.getId(), supplier, buyer, 1);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(order);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("10"));
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setTotalValue(new BigDecimal("250.00"));
        order.getItems().add(item);

        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        entityManager.clear();

        PurchaseOrder reloaded = purchaseOrderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PurchaseOrderStatus.OPEN);
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getItems().get(0).getTotalValue()).isEqualByComparingTo("250.00");
    }

    @Test
    @Transactional
    void removingAnItemFromTheListDeletesItViaOrphanRemoval() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        Produto product = criarProduto(tenant.getId(), "P0001");

        PurchaseOrder order = novaOrdem(tenant.getId(), supplier, buyer, 1);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(order);
        item.setProduct(product);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setTotalValue(new BigDecimal("25.00"));
        order.getItems().add(item);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);

        saved.getItems().clear();
        purchaseOrderRepository.saveAndFlush(saved);
        entityManager.clear();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_order_item WHERE purchase_order_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void numberMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");

        purchaseOrderRepository.saveAndFlush(novaOrdem(tenant.getId(), supplier, buyer, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> purchaseOrderRepository.saveAndFlush(novaOrdem(tenant.getId(), supplier, buyer, 1)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        purchaseOrderRepository.saveAndFlush(novaOrdem(tenant.getId(), supplier, buyer, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_order")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void nextNumberIncrementsAtomicallyPerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        entityManager.createNativeQuery(
                "INSERT INTO purchase_order_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                        "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenant.getId())
                .executeUpdate();

        Object first = entityManager.createNativeQuery(
                        "UPDATE purchase_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();
        Object second = entityManager.createNativeQuery(
                        "UPDATE purchase_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();

        assertThat(((Number) first).intValue()).isEqualTo(1);
        assertThat(((Number) second).intValue()).isEqualTo(2);
    }

    @Test
    @Transactional
    void purchaseOrderCounterRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        PurchaseOrderCounter counter = new PurchaseOrderCounter();
        counter.setTenantId(tenant.getId());
        counter.setNextNumber(1);
        purchaseOrderCounterRepository.saveAndFlush(counter);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_order_counter")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void purchaseOrderItemRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro supplier = criarFornecedor(tenant.getId(), "11222333000144");
        User buyer = criarComprador(tenant.getId(), "carlos@aurora.com.br");
        Produto product = criarProduto(tenant.getId(), "P0001");

        PurchaseOrder order = novaOrdem(tenant.getId(), supplier, buyer, 1);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(order);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("10"));
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setTotalValue(new BigDecimal("250.00"));
        order.getItems().add(item);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM purchase_order_item WHERE purchase_order_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=PurchaseOrderRepositoryTest`
Expected: PASS (7/7).

- [ ] **Step 10: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V11__create_purchase_order.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/
git commit -m "feat(purchase-order): add PurchaseOrder/PurchaseOrderItem/PurchaseOrderCounter entities, migration and repositories"
```

---

### Task 2: Backend — `Module.PURCHASE`, DTOs, exceptions, specifications, `PurchaseOrderService`

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/Module.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/dto/PurchaseOrderItemRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/dto/PurchaseOrderItemResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/dto/PurchaseOrderRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/dto/PurchaseOrderResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/dto/PurchaseOrderSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/dto/PurchaseOrderCountsResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/dto/PurchaseOrderStatusRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderValidationException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/PurchaseOrderServiceTest.java`

**Interfaces:**
- Consumes: `PurchaseOrder`/`PurchaseOrderItem`/`PurchaseOrderCounter`/`PurchaseOrderRepository`/`PurchaseOrderCounterRepository` from Task 1. `Parceiro`/`ParceiroRepository`/`PapelParceiro.FORNECEDOR` (existing), `Produto`/`ProdutoRepository` (existing), `User`/`UserRepository`/`Role.ADMINISTRATIVE` (existing).
- Produces: `PurchaseOrderService` with methods `list(String search, PurchaseOrderStatus status, Pageable pageable): Page<PurchaseOrderSummaryResponse>`, `counts(): PurchaseOrderCountsResponse`, `findById(UUID id): PurchaseOrderResponse`, `create(UUID tenantId, PurchaseOrderRequest request): PurchaseOrderResponse`, `update(UUID id, PurchaseOrderRequest request): PurchaseOrderResponse`, `updateStatus(UUID id, PurchaseOrderStatus newStatus): PurchaseOrderResponse`, `delete(UUID id): void`. Task 3 (`PurchaseOrderController`) consumes all seven directly, with these exact signatures.

- [ ] **Step 1: Add `PURCHASE` to `Module.java`**

```java
package com.meshsuite.auth;

public enum Module {
    CUSTOMER,
    PRODUCT,
    ORDER,
    USER,
    PURCHASE
}
```

- [ ] **Step 2: Write the DTOs**

`dto/PurchaseOrderItemRequest.java`:

```java
package com.meshsuite.purchaseorder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice) {
}
```

`dto/PurchaseOrderItemResponse.java`:

```java
package com.meshsuite.purchaseorder.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalValue) {
}
```

`dto/PurchaseOrderRequest.java`:

```java
package com.meshsuite.purchaseorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(
        @NotNull UUID supplierId,
        @NotNull UUID buyerId,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        BigDecimal discount,
        @NotEmpty List<@Valid PurchaseOrderItemRequest> items) {
}
```

`dto/PurchaseOrderResponse.java`:

```java
package com.meshsuite.purchaseorder.dto;

import com.meshsuite.purchaseorder.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        Integer number,
        UUID supplierId,
        String supplierName,
        UUID buyerId,
        String buyerName,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        PurchaseOrderStatus status,
        BigDecimal discount,
        BigDecimal subtotal,
        BigDecimal total,
        List<PurchaseOrderItemResponse> items) {
}
```

`dto/PurchaseOrderSummaryResponse.java`:

```java
package com.meshsuite.purchaseorder.dto;

import com.meshsuite.purchaseorder.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseOrderSummaryResponse(
        UUID id,
        Integer number,
        String supplierName,
        String buyerName,
        LocalDate orderDate,
        BigDecimal total,
        PurchaseOrderStatus status) {
}
```

`dto/PurchaseOrderCountsResponse.java`:

```java
package com.meshsuite.purchaseorder.dto;

public record PurchaseOrderCountsResponse(long total, long open, long received, long cancelled) {
}
```

`dto/PurchaseOrderStatusRequest.java`:

```java
package com.meshsuite.purchaseorder.dto;

import com.meshsuite.purchaseorder.PurchaseOrderStatus;
import jakarta.validation.constraints.NotNull;

public record PurchaseOrderStatusRequest(@NotNull PurchaseOrderStatus status) {
}
```

- [ ] **Step 3: Write the exceptions**

`PurchaseOrderNotFoundException.java`:

```java
package com.meshsuite.purchaseorder;

public class PurchaseOrderNotFoundException extends RuntimeException {
    public PurchaseOrderNotFoundException() {
        super("Ordem de compra não encontrada");
    }
}
```

`PurchaseOrderValidationException.java`:

```java
package com.meshsuite.purchaseorder;

public class PurchaseOrderValidationException extends RuntimeException {
    public PurchaseOrderValidationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Write `PurchaseOrderSpecifications.java`**

```java
package com.meshsuite.purchaseorder;

import org.springframework.data.jpa.domain.Specification;

public final class PurchaseOrderSpecifications {

    private PurchaseOrderSpecifications() {
    }

    public static Specification<PurchaseOrder> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        Integer number = tryParseInt(search.trim());
        return (root, query, cb) -> {
            var byText = cb.or(
                    cb.like(cb.lower(root.get("supplier").get("nomeFantasia")), term),
                    cb.like(cb.lower(root.get("buyer").get("name")), term));
            if (number != null) {
                return cb.or(byText, cb.equal(root.get("number"), number));
            }
            return byText;
        };
    }

    public static Specification<PurchaseOrder> withStatus(PurchaseOrderStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Integer tryParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 5: Write `PurchaseOrderService.java`**

```java
package com.meshsuite.purchaseorder;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.purchaseorder.dto.*;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ParceiroRepository parceiroRepository;
    private final UserRepository userRepository;
    private final ProdutoRepository produtoRepository;
    private final EntityManager entityManager;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository, ParceiroRepository parceiroRepository,
                                 UserRepository userRepository, ProdutoRepository produtoRepository,
                                 EntityManager entityManager) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.parceiroRepository = parceiroRepository;
        this.userRepository = userRepository;
        this.produtoRepository = produtoRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE, action = Action.VIEW)
    public Page<PurchaseOrderSummaryResponse> list(String search, PurchaseOrderStatus status, Pageable pageable) {
        Specification<PurchaseOrder> spec = Specification.allOf(
                PurchaseOrderSpecifications.withSearch(search),
                PurchaseOrderSpecifications.withStatus(status));
        return purchaseOrderRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE, action = Action.VIEW)
    public PurchaseOrderCountsResponse counts() {
        long open = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.OPEN);
        long received = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.RECEIVED);
        long cancelled = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.CANCELLED);
        return new PurchaseOrderCountsResponse(open + received + cancelled, open, received, cancelled);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE, action = Action.VIEW)
    public PurchaseOrderResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE, action = Action.CREATE)
    public PurchaseOrderResponse create(UUID tenantId, PurchaseOrderRequest request) {
        Parceiro supplier = findValidSupplier(request.supplierId());
        User buyer = findValidBuyer(request.buyerId());

        PurchaseOrder order = new PurchaseOrder();
        order.setTenantId(tenantId);
        order.setNumber(nextNumber(tenantId));
        apply(order, supplier, buyer, request);
        return toResponse(purchaseOrderRepository.saveAndFlush(order));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE, action = Action.EDIT)
    public PurchaseOrderResponse update(UUID id, PurchaseOrderRequest request) {
        Parceiro supplier = findValidSupplier(request.supplierId());
        User buyer = findValidBuyer(request.buyerId());

        PurchaseOrder order = findEntityById(id);
        if (order.getStatus() != PurchaseOrderStatus.OPEN) {
            throw new PurchaseOrderValidationException(
                    "Não é possível editar uma ordem de compra " + order.getStatus());
        }
        apply(order, supplier, buyer, request);
        return toResponse(purchaseOrderRepository.saveAndFlush(order));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE, action = Action.EDIT)
    public PurchaseOrderResponse updateStatus(UUID id, PurchaseOrderStatus newStatus) {
        PurchaseOrder order = findEntityById(id);
        if (order.getStatus() != PurchaseOrderStatus.OPEN) {
            throw new PurchaseOrderValidationException(
                    "Não é possível alterar o status de uma ordem " + order.getStatus());
        }
        if (newStatus == PurchaseOrderStatus.OPEN) {
            throw new PurchaseOrderValidationException("Status inválido: " + newStatus);
        }
        order.setStatus(newStatus);
        return toResponse(purchaseOrderRepository.saveAndFlush(order));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE, action = Action.DELETE)
    public void delete(UUID id) {
        purchaseOrderRepository.delete(findEntityById(id));
    }

    private PurchaseOrder findEntityById(UUID id) {
        return purchaseOrderRepository.findById(id).orElseThrow(PurchaseOrderNotFoundException::new);
    }

    private Parceiro findValidSupplier(UUID supplierId) {
        Parceiro parceiro = parceiroRepository.findById(supplierId)
                .orElseThrow(() -> new PurchaseOrderValidationException("Fornecedor não encontrado"));
        if (!parceiro.getPapeis().contains(PapelParceiro.FORNECEDOR)) {
            throw new PurchaseOrderValidationException("O parceiro selecionado não tem o papel Fornecedor");
        }
        return parceiro;
    }

    private User findValidBuyer(UUID buyerId) {
        User user = userRepository.findById(buyerId)
                .orElseThrow(() -> new PurchaseOrderValidationException("Comprador não encontrado"));
        if (user.getRole() != Role.ADMINISTRATIVE) {
            throw new PurchaseOrderValidationException("O usuário selecionado não tem o papel Administrativo");
        }
        return user;
    }

    // Atomic UPDATE ... RETURNING against the tenant's single
    // purchase_order_counter row -- never COUNT(*)/MAX(number)+1, both of which
    // race under concurrent inserts. Runs inside this method's own
    // @Transactional, so TenantContextAspect has already issued SET LOCAL
    // app.tenant_id before either native query below runs.
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO purchase_order_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE purchase_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private void apply(PurchaseOrder order, Parceiro supplier, User buyer, PurchaseOrderRequest request) {
        order.setSupplier(supplier);
        order.setBuyer(buyer);
        order.setOrderDate(request.orderDate() != null ? request.orderDate() : LocalDate.now());
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        BigDecimal discount = request.discount() != null ? request.discount() : BigDecimal.ZERO;

        order.getItems().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (PurchaseOrderItemRequest dto : request.items()) {
            Produto product = produtoRepository.findById(dto.productId())
                    .orElseThrow(() -> new PurchaseOrderValidationException("Produto não encontrado"));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(order);
            item.setProduct(product);
            item.setQuantity(dto.quantity());
            item.setUnitPrice(dto.unitPrice());
            BigDecimal itemTotal = dto.quantity().multiply(dto.unitPrice());
            item.setTotalValue(itemTotal);
            order.getItems().add(item);
            subtotal = subtotal.add(itemTotal);
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new PurchaseOrderValidationException("O desconto não pode ser maior que o valor dos produtos");
        }
        order.setDiscount(discount);
        order.setSubtotal(subtotal);
        order.setTotal(subtotal.subtract(discount));
    }

    private PurchaseOrderSummaryResponse toSummary(PurchaseOrder o) {
        return new PurchaseOrderSummaryResponse(o.getId(), o.getNumber(), o.getSupplier().getNomeFantasia(),
                o.getBuyer().getName(), o.getOrderDate(), o.getTotal(), o.getStatus());
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder o) {
        List<PurchaseOrderItemResponse> items = o.getItems().stream()
                .map(i -> new PurchaseOrderItemResponse(i.getProduct().getId(), i.getProduct().getNome(),
                        i.getQuantity(), i.getUnitPrice(), i.getTotalValue()))
                .toList();
        return new PurchaseOrderResponse(o.getId(), o.getNumber(), o.getSupplier().getId(), o.getSupplier().getNomeFantasia(),
                o.getBuyer().getId(), o.getBuyer().getName(), o.getOrderDate(), o.getExpectedDeliveryDate(),
                o.getStatus(), o.getDiscount(), o.getSubtotal(), o.getTotal(), items);
    }
}
```

- [ ] **Step 6: Write the failing service test**

```java
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
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=PurchaseOrderServiceTest`
Expected: PASS (14/14).

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/Module.java \
        mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/
git commit -m "feat(purchase-order): add Module.PURCHASE, DTOs, exceptions, specifications and PurchaseOrderService"
```

---

### Task 3: Backend — `PurchaseOrderController`, exception handling, buyer picker endpoint, integration tests

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderExceptionHandler.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/BuyerResponse.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserController.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/PurchaseOrderControllerTest.java`

**Interfaces:**
- Consumes: `PurchaseOrderService` (Task 2), `UserRepository.findByRoleOrderByName` (existing).
- Produces: `GET/POST/PUT /api/purchase-orders`, `GET /api/purchase-orders/counts`, `GET/PUT/PATCH/DELETE /api/purchase-orders/{id}` (+`/status`), `GET /api/users/buyers`. Task 4/5 (frontend) consume these routes directly.

- [ ] **Step 1: Write `PurchaseOrderController.java`**

```java
package com.meshsuite.purchaseorder;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.purchaseorder.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    public Page<PurchaseOrderSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @PageableDefault(size = 10, sort = "number", direction = Sort.Direction.DESC) Pageable pageable) {
        return purchaseOrderService.list(search, status, pageable);
    }

    @GetMapping("/counts")
    public PurchaseOrderCountsResponse counts() {
        return purchaseOrderService.counts();
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse findById(@PathVariable UUID id) {
        return purchaseOrderService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                          @Valid @RequestBody PurchaseOrderRequest request) {
        PurchaseOrderResponse response = purchaseOrderService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PurchaseOrderResponse update(@PathVariable UUID id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public PurchaseOrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody PurchaseOrderStatusRequest request) {
        return purchaseOrderService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        purchaseOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Write `PurchaseOrderExceptionHandler.java`**

```java
package com.meshsuite.purchaseorder;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = PurchaseOrderController.class)
public class PurchaseOrderExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Não foi possível salvar a ordem de compra. Tente novamente."));
    }
}
```

- [ ] **Step 3: Register `PurchaseOrderNotFoundException`/`PurchaseOrderValidationException` in `GlobalExceptionHandler`**

Append these two handlers to the end of the class, right after the existing `handlePedidoValidacao` method, before the final closing brace:

```java
    @ExceptionHandler(com.meshsuite.purchaseorder.PurchaseOrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseOrderNotFound(
            com.meshsuite.purchaseorder.PurchaseOrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.purchaseorder.PurchaseOrderValidationException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseOrderValidation(
            com.meshsuite.purchaseorder.PurchaseOrderValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 4: Write `dto/BuyerResponse.java` and add the `/buyers` endpoint to `UserController`**

`mesh-suite-backend/src/main/java/com/meshsuite/user/dto/BuyerResponse.java`:

```java
package com.meshsuite.user.dto;

import java.util.UUID;

public record BuyerResponse(UUID id, String name) {
}
```

In `UserController.java`, add this method right after the existing `salesReps()` method (which already carries `@Transactional(readOnly = true)` — see its comment for why that annotation is required here too):

```java
    // Deliberately bypasses UserService/@RequiresPermission -- support lookup
    // for the Ordem de Compra form's buyer picker, not "viewing the Users
    // module". Needs its own @Transactional for the same reason /sales-reps
    // does (see that method's comment above): TenantContextAspect only fires
    // around methods carrying that annotation directly, and
    // findByRoleOrderByName's RLS-scoped query is invisible to every tenant
    // without app.tenant_id set.
    @Transactional(readOnly = true)
    @GetMapping("/buyers")
    public List<BuyerResponse> buyers() {
        return userRepository.findByRoleOrderByName(Role.ADMINISTRATIVE).stream()
                .map(u -> new BuyerResponse(u.getId(), u.getName()))
                .toList();
    }
```

- [ ] **Step 5: Write the failing controller test**

```java
package com.meshsuite.purchaseorder;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.auth.Module;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class PurchaseOrderControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, String supplierId, String buyerId, String productId) {
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
        userLogin.setName("Carlos Comprador");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMINISTRATIVE);
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.CREATE));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.EDIT));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.DELETE));
        User savedBuyer = userRepository.saveAndFlush(userLogin);

        Parceiro supplier = new Parceiro();
        supplier.setTenantId(tenant.getId());
        supplier.setTipoPessoa(TipoPessoa.JURIDICA);
        supplier.setDocumento(cnpjEmpresa.equals("11222333000144") ? "55666777000155" : "11222333000144");
        supplier.setNomeFantasia("Tecidos Aurora");
        supplier.getPapeis().add(PapelParceiro.FORNECEDOR);
        parceiroRepository.saveAndFlush(supplier);

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
        return new Contexto(token, supplier.getId().toString(), savedBuyer.getId().toString(), produto.getId().toString());
    }

    private String purchaseOrderPayload(Contexto ctx) {
        return """
                {
                  "supplierId": "%s",
                  "buyerId": "%s",
                  "discount": 0,
                  "items": [
                    { "productId": "%s", "quantity": 2, "unitPrice": 100.00 }
                  ]
                }
                """.formatted(ctx.supplierId(), ctx.buyerId(), ctx.productId());
    }

    @Test
    void createsListsUpdatesChangesStatusAndDeletesPurchaseOrder() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/purchase-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseOrderPayload(ctx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.total").value(200.00))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/purchase-orders").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(1));

        mockMvc.perform(put("/api/purchase-orders/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "buyerId": "%s",
                                  "discount": 20.00,
                                  "items": [
                                    { "productId": "%s", "quantity": 2, "unitPrice": 100.00 }
                                  ]
                                }
                                """.formatted(ctx.supplierId(), ctx.buyerId(), ctx.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(180.00));

        mockMvc.perform(patch("/api/purchase-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        mockMvc.perform(patch("/api/purchase-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/purchase-orders/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/purchase-orders/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyItemsWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/purchase-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "buyerId": "%s",
                                  "discount": 0,
                                  "items": []
                                }
                                """.formatted(ctx.supplierId(), ctx.buyerId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSupplierWithoutFornecedorPapelWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/purchase-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "buyerId": "%s",
                                  "discount": 0,
                                  "items": [ { "productId": "%s", "quantity": 1, "unitPrice": 10.00 } ]
                                }
                                """.formatted(ctx.buyerId(), ctx.buyerId(), ctx.productId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsPurchaseOrder() throws Exception {
        Contexto ctxA = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/purchase-orders").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseOrderPayload(ctxA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        Contexto ctxB = loginAndSetUp("boreal", "marina@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/purchase-orders/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/purchase-orders"))
                .andExpect(status().isUnauthorized());
    }

    private String loginWithoutPurchasePermission(String codigo, String email, String cnpjEmpresa) throws Exception {
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

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Sem Permissão");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.VIEWER);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    @Test
    void listingWithoutPurchaseViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutPurchasePermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/purchase-orders").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void buyersEndpointReturnsRealContentAndWorksWithoutPurchasePermission() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(get("/api/users/buyers").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Carlos Comprador"));
    }
}
```

The last test (`buyersEndpointReturnsRealContentAndWorksWithoutPurchasePermission`) exists specifically to avoid the content-blind-assertion gap already found and fixed once in `UserControllerTest.salesRepsEndpointWorksEvenWithoutUserPermission` — assert on actual response content, not just `status().isOk()`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=PurchaseOrderControllerTest`
Expected: PASS (7/7).

- [ ] **Step 7: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS, no regressions anywhere (Parceiro/Produto/Pedido/Auth/User/PurchaseOrder all green).

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/PurchaseOrderExceptionHandler.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/dto/BuyerResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/UserController.java \
        mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/PurchaseOrderControllerTest.java
git commit -m "feat(purchase-order): add PurchaseOrderController, buyers picker endpoint and RLS-safe integration tests"
```

---

### Task 4: Frontend — API layer + `PurchaseOrderFormView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/api/purchaseOrders.ts`
- Modify: `mesh-suite-frontend/src/api/users.ts`
- Create: `mesh-suite-frontend/src/views/PurchaseOrderFormView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/PurchaseOrderFormView.spec.ts`

**Interfaces:**
- Consumes: `GET/POST/PUT /api/purchase-orders(/{id})`, `GET /api/users/buyers`, `listarParceiros`/`listarProdutos` (existing, from `@/api/parceiros`/`@/api/produtos`).
- Produces: `listPurchaseOrders`, `getPurchaseOrder`, `createPurchaseOrder`, `updatePurchaseOrder`, `updatePurchaseOrderStatus`, `deletePurchaseOrder`, `getPurchaseOrderCounts` in `@/api/purchaseOrders`; `listBuyers`/`Buyer` in `@/api/users`. Task 5 consumes `listPurchaseOrders`/`getPurchaseOrderCounts`/`updatePurchaseOrderStatus`/`deletePurchaseOrder` with these exact names.

- [ ] **Step 1: Write `api/purchaseOrders.ts`**

```ts
import { apiClient } from './client'

export type PurchaseOrderStatus = 'OPEN' | 'RECEIVED' | 'CANCELLED'

export interface PurchaseOrderItemRequest {
  productId: string
  quantity: number
  unitPrice: number
}

export interface PurchaseOrderItemResponse extends PurchaseOrderItemRequest {
  productName: string
  totalValue: number
}

export interface PurchaseOrderRequest {
  supplierId: string
  buyerId: string
  orderDate: string
  expectedDeliveryDate: string | null
  discount: number
  items: PurchaseOrderItemRequest[]
}

export interface PurchaseOrderResponse {
  id: string
  number: number
  supplierId: string
  supplierName: string
  buyerId: string
  buyerName: string
  orderDate: string
  expectedDeliveryDate: string | null
  status: PurchaseOrderStatus
  discount: number
  subtotal: number
  total: number
  items: PurchaseOrderItemResponse[]
}

export interface PurchaseOrderSummary {
  id: string
  number: number
  supplierName: string
  buyerName: string
  orderDate: string
  total: number
  status: PurchaseOrderStatus
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListPurchaseOrdersParams {
  search?: string
  status?: PurchaseOrderStatus
  page?: number
  size?: number
}

export interface PurchaseOrderCounts {
  total: number
  open: number
  received: number
  cancelled: number
}

export async function listPurchaseOrders(params: ListPurchaseOrdersParams): Promise<Page<PurchaseOrderSummary>> {
  const { data } = await apiClient.get<Page<PurchaseOrderSummary>>('/purchase-orders', { params })
  return data
}

export async function getPurchaseOrder(id: string): Promise<PurchaseOrderResponse> {
  const { data } = await apiClient.get<PurchaseOrderResponse>(`/purchase-orders/${id}`)
  return data
}

export async function createPurchaseOrder(payload: PurchaseOrderRequest): Promise<PurchaseOrderResponse> {
  const { data } = await apiClient.post<PurchaseOrderResponse>('/purchase-orders', payload)
  return data
}

export async function updatePurchaseOrder(id: string, payload: PurchaseOrderRequest): Promise<PurchaseOrderResponse> {
  const { data } = await apiClient.put<PurchaseOrderResponse>(`/purchase-orders/${id}`, payload)
  return data
}

export async function updatePurchaseOrderStatus(id: string, status: PurchaseOrderStatus): Promise<void> {
  await apiClient.patch(`/purchase-orders/${id}/status`, { status })
}

export async function deletePurchaseOrder(id: string): Promise<void> {
  await apiClient.delete(`/purchase-orders/${id}`)
}

export async function getPurchaseOrderCounts(): Promise<PurchaseOrderCounts> {
  const { data } = await apiClient.get<PurchaseOrderCounts>('/purchase-orders/counts')
  return data
}
```

- [ ] **Step 2: Add `Buyer`/`listBuyers` to `api/users.ts`**

Add this block right after the existing `listSalesReps` function (before the `Role` type export):

```ts
export interface Buyer {
  id: string
  name: string
}

export async function listBuyers(): Promise<Buyer[]> {
  const { data } = await apiClient.get<Buyer[]>('/users/buyers')
  return data
}
```

- [ ] **Step 3: Write `PurchaseOrderFormView.vue`**

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Ordem de Compra' : 'Nova Ordem de Compra'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados da Ordem</h2>
        <div class="grid grid-2">
          <div class="busca-wrapper">
            <label class="field-label">Fornecedor *</label>
            <input
              v-model="fornecedorBusca"
              data-test="fornecedor-busca"
              placeholder="Buscar fornecedor..."
              autocomplete="off"
              @input="buscarFornecedores"
            />
            <p v-if="erros.supplierId" class="field-error">{{ erros.supplierId }}</p>
            <ul v-if="resultadosFornecedores.length" class="dropdown-busca" data-test="fornecedor-resultados">
              <li v-for="f in resultadosFornecedores" :key="f.id" @click="selecionarFornecedor(f)">{{ f.nomeFantasia }}</li>
            </ul>
          </div>
          <div>
            <label class="field-label">Comprador *</label>
            <select v-model="form.buyerId" data-test="comprador">
              <option value="">Selecione...</option>
              <option v-for="c in compradores" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <p v-if="erros.buyerId" class="field-error">{{ erros.buyerId }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Data da Ordem</label>
            <input v-model="form.orderDate" type="date" data-test="data-ordem" />
          </div>
          <div>
            <label class="field-label">Previsão de Entrega</label>
            <input v-model="form.expectedDeliveryDate" type="date" data-test="data-entrega" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Itens</h2>
        <div class="item-adicionar">
          <div class="busca-wrapper item-produto-busca">
            <input
              v-model="produtoBusca"
              placeholder="Buscar produto por nome ou SKU..."
              data-test="produto-busca"
              autocomplete="off"
              @input="buscarProdutos"
            />
            <ul v-if="resultadosProdutos.length" class="dropdown-busca" data-test="produto-resultados">
              <li v-for="p in resultadosProdutos" :key="p.id" @click="selecionarProduto(p)">{{ p.nome }} ({{ p.sku }})</li>
            </ul>
          </div>
          <input
            v-model.number="itemForm.quantity"
            type="number"
            step="0.001"
            min="0.001"
            placeholder="Qtd."
            data-test="item-quantidade"
          />
          <input
            v-model.number="itemForm.unitPrice"
            type="number"
            step="0.01"
            min="0"
            placeholder="Valor unit."
            data-test="item-valor-unitario"
          />
          <button type="button" class="btn-secondary" data-test="item-adicionar" @click="adicionarItem">+ Adicionar</button>
        </div>
        <p v-if="erros.items" class="field-error">{{ erros.items }}</p>

        <table v-if="form.items.length" class="tabela-itens">
          <thead>
            <tr>
              <th>Produto</th>
              <th>Qtd.</th>
              <th>Valor Unit.</th>
              <th>Total</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in form.items" :key="index">
              <td>{{ item.productName }}</td>
              <td>{{ item.quantity }}</td>
              <td>{{ formatarPreco(item.unitPrice) }}</td>
              <td>{{ formatarPreco(item.quantity * item.unitPrice) }}</td>
              <td><button type="button" class="btn-remover" data-test="item-remover" @click="removerItem(index)">✕</button></td>
            </tr>
          </tbody>
        </table>

        <div class="totais">
          <div><span>Subtotal</span><span>{{ formatarPreco(subtotal) }}</span></div>
          <div>
            <span>Desconto</span>
            <input v-model.number="form.discount" type="number" step="0.01" min="0" data-test="desconto" />
          </div>
          <div class="total-final"><span>Total</span><span>{{ formatarPreco(total) }}</span></div>
        </div>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Ordem</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getPurchaseOrder,
  createPurchaseOrder,
  updatePurchaseOrder,
  type PurchaseOrderRequest,
  type PurchaseOrderItemRequest,
} from '@/api/purchaseOrders'
import { listarParceiros, type ParceiroSummary } from '@/api/parceiros'
import { listBuyers, type Buyer } from '@/api/users'
import { listarProdutos, type ProdutoSummary } from '@/api/produtos'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

interface ItemForm extends PurchaseOrderItemRequest {
  productName: string
}

interface FormState {
  supplierId: string
  buyerId: string
  orderDate: string
  expectedDeliveryDate: string
  discount: number
  items: ItemForm[]
}

function novoFormulario(): FormState {
  return {
    supplierId: '',
    buyerId: '',
    orderDate: new Date().toISOString().slice(0, 10),
    expectedDeliveryDate: '',
    discount: 0,
    items: [],
  }
}

const form = reactive<FormState>(novoFormulario())
const erros = reactive<{ supplierId?: string; buyerId?: string; items?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

const fornecedorBusca = ref('')
const resultadosFornecedores = ref<ParceiroSummary[]>([])
const compradores = ref<Buyer[]>([])

const produtoBusca = ref('')
const resultadosProdutos = ref<ProdutoSummary[]>([])
const itemForm = reactive({ productId: '', productName: '', quantity: 1, unitPrice: 0 })

const subtotal = computed(() => form.items.reduce((soma, item) => soma + item.quantity * item.unitPrice, 0))
const total = computed(() => subtotal.value - (Number(form.discount) || 0))

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

async function buscarFornecedores() {
  if (!fornecedorBusca.value.trim()) {
    resultadosFornecedores.value = []
    return
  }
  try {
    const pagina = await listarParceiros({ busca: fornecedorBusca.value, papel: 'FORNECEDOR', size: 5 })
    resultadosFornecedores.value = pagina.content
  } catch {
    resultadosFornecedores.value = []
  }
}

function selecionarFornecedor(fornecedor: ParceiroSummary) {
  form.supplierId = fornecedor.id
  fornecedorBusca.value = fornecedor.nomeFantasia
  resultadosFornecedores.value = []
}

async function buscarProdutos() {
  if (!produtoBusca.value.trim()) {
    resultadosProdutos.value = []
    return
  }
  try {
    const pagina = await listarProdutos({ busca: produtoBusca.value, size: 5 })
    resultadosProdutos.value = pagina.content
  } catch {
    resultadosProdutos.value = []
  }
}

function selecionarProduto(produto: ProdutoSummary) {
  itemForm.productId = produto.id
  itemForm.productName = produto.nome
  itemForm.unitPrice = produto.precoVenda
  produtoBusca.value = produto.nome
  resultadosProdutos.value = []
}

function adicionarItem() {
  const quantity = Number(itemForm.quantity) || 0
  if (!itemForm.productId || quantity <= 0) {
    return
  }
  form.items.push({
    productId: itemForm.productId,
    productName: itemForm.productName,
    quantity,
    // Normalized here for the same reason paraPayload() normalizes on submit:
    // v-model.number on a blank input yields '' (not 0), and that would flow
    // straight into form.items and later into the request payload untouched.
    unitPrice: Number(itemForm.unitPrice) || 0,
  })
  itemForm.productId = ''
  itemForm.productName = ''
  itemForm.quantity = 1
  itemForm.unitPrice = 0
  produtoBusca.value = ''
}

function removerItem(index: number) {
  form.items.splice(index, 1)
}

onMounted(async () => {
  try {
    compradores.value = await listBuyers()
  } catch {
    erroGeral.value = 'Não foi possível carregar a lista de compradores.'
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const order = await getPurchaseOrder(id)
      form.supplierId = order.supplierId
      fornecedorBusca.value = order.supplierName
      form.buyerId = order.buyerId
      form.orderDate = order.orderDate
      form.expectedDeliveryDate = order.expectedDeliveryDate ?? ''
      form.discount = order.discount
      form.items = order.items.map((item) => ({
        productId: item.productId,
        productName: item.productName,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da ordem de compra.'
    }
  }
})

function validar(): boolean {
  erros.supplierId = form.supplierId ? undefined : 'Selecione um fornecedor'
  erros.buyerId = form.buyerId ? undefined : 'Selecione um comprador'
  erros.items = form.items.length > 0 ? undefined : 'Adicione ao menos um item'
  return !erros.supplierId && !erros.buyerId && !erros.items
}

function paraPayload(): PurchaseOrderRequest {
  return {
    supplierId: form.supplierId,
    buyerId: form.buyerId,
    orderDate: form.orderDate,
    expectedDeliveryDate: form.expectedDeliveryDate || null,
    discount: Number(form.discount) || 0,
    items: form.items.map(({ productId, quantity, unitPrice }) => ({ productId, quantity, unitPrice })),
  }
}

async function salvar() {
  erroGeral.value = ''
  if (!validar()) {
    return
  }
  salvando.value = true
  try {
    const id = route.params.id
    const payload = paraPayload()
    if (typeof id === 'string') {
      await updatePurchaseOrder(id, payload)
    } else {
      await createPurchaseOrder(payload)
    }
    router.push({ name: 'compras' })
  } catch (err: any) {
    if (err?.response?.status === 403) {
      erroGeral.value = 'Você não tem permissão para executar esta ação.'
    } else if (err?.response?.status === 400) {
      erroGeral.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      erroGeral.value = 'Não foi possível salvar. Tente novamente em instantes.'
    }
  } finally {
    salvando.value = false
  }
}

function cancelar() {
  router.push({ name: 'compras' })
}
</script>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-family: var(--pm-font);
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px;
}

.card h2 {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0 0 12px;
}

.grid {
  display: grid;
  gap: 0 14px;
  margin-bottom: 10px;
}

.grid-2 {
  grid-template-columns: 1fr 1fr;
}

.field-label {
  display: block;
  font-size: 12px;
  color: var(--pm-text-mid);
  margin-bottom: 4px;
}

input,
select {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  color: var(--pm-text-dark);
  font-size: 13px;
  font-family: var(--pm-font);
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 4px 0 0;
}

.error-geral {
  color: var(--pm-error);
  font-size: 14px;
}

.busca-wrapper {
  position: relative;
}

.dropdown-busca {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin: 4px 0 0;
  padding: 4px 0;
  list-style: none;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 28px rgba(0, 0, 0, 0.12);
  z-index: 10;
  max-height: 200px;
  overflow-y: auto;
}

.dropdown-busca li {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--pm-text-dark);
  cursor: pointer;
}

.item-adicionar {
  display: grid;
  grid-template-columns: 1fr 100px 120px auto;
  gap: 8px;
  align-items: start;
  margin-bottom: 10px;
}

.item-produto-busca {
  min-width: 0;
}

.tabela-itens {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-bottom: 12px;
}

.tabela-itens th {
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  background: var(--pm-bg);
  padding: 6px 10px;
}

.tabela-itens td {
  padding: 6px 10px;
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.btn-remover {
  border: none;
  background: none;
  color: var(--pm-error);
  cursor: pointer;
  font-size: 13px;
}

.totais {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 260px;
  margin-left: auto;
  font-size: 13px;
  color: var(--pm-text-dark);
}

.totais > div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.totais input {
  width: 100px;
  text-align: right;
}

.total-final {
  font-weight: 700;
  font-size: 14px;
  border-top: 1px solid var(--pm-border-light);
  padding-top: 6px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn-primary,
.btn-secondary {
  border-radius: 8px;
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--pm-font);
  cursor: pointer;
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
}
</style>
```

- [ ] **Step 4: Add the `compras-novo`/`compras-editar` routes**

In `mesh-suite-frontend/src/router/index.ts`, add the import and the two routes. This step only adds the form routes — the list route (`compras`) is added in Task 5 alongside `PurchaseOrdersListView.vue`, so add a temporary placeholder list route here that Task 5 will replace, to keep this task's tests self-contained:

Add near the top, with the other view imports:

```ts
import PurchaseOrderFormView from '@/views/PurchaseOrderFormView.vue'
```

Add to the `routes` array, after the `usuarios-editar` route:

```ts
    { path: '/compras/novo', name: 'compras-novo', component: PurchaseOrderFormView },
    { path: '/compras/:id/editar', name: 'compras-editar', component: PurchaseOrderFormView },
```

Do not add the `/compras` list route or its import yet — Task 5 adds both together with `PurchaseOrdersListView.vue`.

- [ ] **Step 5: Write the failing view test**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PurchaseOrderFormView from '@/views/PurchaseOrderFormView.vue'
import * as purchaseOrdersApi from '@/api/purchaseOrders'
import * as parceirosApi from '@/api/parceiros'
import * as usersApi from '@/api/users'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/purchaseOrders')
vi.mock('@/api/parceiros')
vi.mock('@/api/users')
vi.mock('@/api/produtos')

function mountWithRouter(path = '/compras/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/compras', name: 'compras', component: { template: '<div />' } },
      { path: '/compras/novo', name: 'compras-novo', component: PurchaseOrderFormView },
      { path: '/compras/:id/editar', name: 'compras-editar', component: PurchaseOrderFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PurchaseOrderFormView, { global: { plugins: [router] } }),
  }))
}

const fornecedorBase = {
  id: 'f1', nomeFantasia: 'Tecidos Aurora', razaoSocial: 'Tecidos Aurora Ltda',
  documento: '11222333000144', cidade: 'São Paulo', uf: 'SP', whatsapp: '', status: 'ATIVO' as const,
}

const compradorBase = { id: 'b1', name: 'Carlos Comprador' }

const produtoBase = {
  id: 'p1', nome: 'Tecido Algodão', sku: 'P0001', marca: 'Marca Alpha',
  precoVenda: 25.0, quantidadeEstoque: 100, status: 'ATIVO' as const,
}

describe('PurchaseOrderFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usersApi.listBuyers).mockResolvedValue([compradorBase])
    vi.mocked(parceirosApi.listarParceiros).mockResolvedValue({
      content: [fornecedorBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
  })

  it('shows required-field errors when fornecedor/comprador/items are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione um fornecedor')
    expect(wrapper.text()).toContain('Selecione um comprador')
    expect(wrapper.text()).toContain('Adicione ao menos um item')
    expect(purchaseOrdersApi.createPurchaseOrder).not.toHaveBeenCalled()
  })

  it('loads the compradores list for the comprador select', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(usersApi.listBuyers).toHaveBeenCalled()
    expect(wrapper.find('[data-test="comprador"]').text()).toContain('Carlos Comprador')
  })

  it('searches and selects a fornecedor via the busca dropdown', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="fornecedor-busca"]').setValue('aurora')
    await flushPromises()

    expect(parceirosApi.listarParceiros).toHaveBeenCalledWith(
      expect.objectContaining({ busca: 'aurora', papel: 'FORNECEDOR' }),
    )
    await wrapper.find('[data-test="fornecedor-resultados"] li').trigger('click')

    expect((wrapper.find('[data-test="fornecedor-busca"]').element as HTMLInputElement).value).toBe('Tecidos Aurora')
  })

  it('searches for a produto, adds it as an item and computes totals live', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="produto-busca"]').setValue('algodão')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('10')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Tecido Algodão')
    expect(wrapper.text()).toContain('R$ 250,00')

    await wrapper.find('[data-test="item-remover"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Tecido Algodão')
  })

  it('normalizes a cleared unitPrice to the number 0 (not empty-string) when added immediately', async () => {
    vi.mocked(purchaseOrdersApi.createPurchaseOrder).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="fornecedor-busca"]').setValue('aurora')
    await flushPromises()
    await wrapper.find('[data-test="fornecedor-resultados"] li').trigger('click')
    await wrapper.find('[data-test="comprador"]').setValue('b1')

    await wrapper.find('[data-test="produto-busca"]').setValue('algodão')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    // Simulate the auto-filled valor unitário being manually cleared, then
    // "Adicionar" clicked immediately -- v-model.number drives the underlying
    // value to '' (empty string) when cleared, and adicionarItem() must
    // normalize that '' to 0 before it lands in form.items/payload. This must
    // NOT be refilled before clicking Adicionar, or the empty-string state
    // never reaches adicionarItem() and the normalization guard goes untested.
    await wrapper.find('[data-test="item-valor-unitario"]').setValue('')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(purchaseOrdersApi.createPurchaseOrder).mock.calls[0][0]
    expect(payload.items[0].unitPrice).toBe(0)
    expect(typeof payload.items[0].unitPrice).toBe('number')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(purchaseOrdersApi.createPurchaseOrder).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="fornecedor-busca"]').setValue('aurora')
    await flushPromises()
    await wrapper.find('[data-test="fornecedor-resultados"] li').trigger('click')
    await wrapper.find('[data-test="comprador"]').setValue('b1')

    await wrapper.find('[data-test="produto-busca"]').setValue('algodão')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(purchaseOrdersApi.createPurchaseOrder).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('compras')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(purchaseOrdersApi.createPurchaseOrder).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="fornecedor-busca"]').setValue('aurora')
    await flushPromises()
    await wrapper.find('[data-test="fornecedor-resultados"] li').trigger('click')
    await wrapper.find('[data-test="comprador"]').setValue('b1')

    await wrapper.find('[data-test="produto-busca"]').setValue('algodão')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing purchase order data in edit mode', async () => {
    vi.mocked(purchaseOrdersApi.getPurchaseOrder).mockResolvedValue({
      id: 'po-1', number: 3, supplierId: 'f1', supplierName: 'Tecidos Aurora', buyerId: 'b1',
      buyerName: 'Carlos Comprador', orderDate: '2026-08-04', expectedDeliveryDate: null, status: 'OPEN',
      discount: 0, subtotal: 250.0, total: 250.0,
      items: [{ productId: 'p1', productName: 'Tecido Algodão', quantity: 10, unitPrice: 25.0, totalValue: 250.0 }],
    } as any)

    const { wrapper } = await mountWithRouter('/compras/po-1/editar')
    await flushPromises()

    expect(purchaseOrdersApi.getPurchaseOrder).toHaveBeenCalledWith('po-1')
    expect((wrapper.find('[data-test="fornecedor-busca"]').element as HTMLInputElement).value).toBe('Tecidos Aurora')
    expect(wrapper.text()).toContain('Tecido Algodão')
  })

  it('shows an error message when loading purchase order data fails in edit mode', async () => {
    vi.mocked(purchaseOrdersApi.getPurchaseOrder).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/compras/po-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da ordem de compra.')
  })
})
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PurchaseOrderFormView.spec.ts`
Expected: PASS (9/9).

- [ ] **Step 7: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all test files pass, no regressions. `router/index.ts` will fail to resolve `compras`-named routes used only by Task 5 — none exist yet, which is fine since no test references them before Task 5 adds them.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-frontend/src/api/purchaseOrders.ts \
        mesh-suite-frontend/src/api/users.ts \
        mesh-suite-frontend/src/views/PurchaseOrderFormView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/__tests__/PurchaseOrderFormView.spec.ts
git commit -m "feat(purchase-order): add purchaseOrders API layer, buyers picker and PurchaseOrderFormView"
```

---

### Task 5: Frontend — `PurchaseOrdersListView.vue`, routing and sidebar activation

**Files:**
- Create: `mesh-suite-frontend/src/views/PurchaseOrdersListView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/PurchaseOrdersListView.spec.ts`
- Test: `mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts`

**Interfaces:**
- Consumes: `listPurchaseOrders`, `getPurchaseOrderCounts`, `updatePurchaseOrderStatus`, `deletePurchaseOrder`, `PurchaseOrderSummary`, `PurchaseOrderCounts`, `PurchaseOrderStatus`, `Page` from `@/api/purchaseOrders` (Task 4).

- [ ] **Step 1: Write `PurchaseOrdersListView.vue`**

```vue
<template>
  <AppShell title="Ordens de Compra">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº, fornecedor ou comprador..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="OPEN">Aberta</option>
        <option value="RECEIVED">Recebida</option>
        <option value="CANCELLED">Cancelada</option>
      </select>
      <button type="button" class="btn-primary" data-test="nova-ordem" @click="novaOrdem">+ Nova Ordem de Compra</button>
    </div>

    <div v-if="resumo" class="resumo">
      <span class="resumo-item">{{ resumo.total }} Total</span>
      <span class="resumo-item resumo-open">{{ resumo.open }} Abertas</span>
      <span class="resumo-item resumo-received">{{ resumo.received }} Recebidas</span>
      <span class="resumo-item resumo-cancelled">{{ resumo.cancelled }} Canceladas</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nº</th>
            <th>Fornecedor</th>
            <th>Comprador</th>
            <th>Data</th>
            <th>Total</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ordem in pagina.content" :key="ordem.id">
            <td>{{ ordem.number }}</td>
            <td>{{ ordem.supplierName }}</td>
            <td>{{ ordem.buyerName }}</td>
            <td>{{ formatarData(ordem.orderDate) }}</td>
            <td>{{ formatarPreco(ordem.total) }}</td>
            <td><span class="badge" :class="`badge-${ordem.status}`">{{ statusLabel(ordem.status) }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(ordem.id, $event)"
              >
                Ações
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <Teleport to="body">
      <div
        v-if="ordemAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarOrdem(ordemAcoesAtual.id)">Editar</div>
        <div v-if="ordemAcoesAtual.status === 'OPEN'" data-test="acao-receber" @click="marcarComoRecebida(ordemAcoesAtual)">
          Marcar como Recebida
        </div>
        <div v-if="ordemAcoesAtual.status === 'OPEN'" data-test="acao-cancelar" @click="cancelarOrdem(ordemAcoesAtual)">
          Cancelar
        </div>
        <div class="acao-excluir" data-test="acao-excluir" @click="excluir(ordemAcoesAtual)">Excluir</div>
      </div>
    </Teleport>

    <div class="paginacao">
      <button type="button" :disabled="pagina.number === 0" @click="carregar(pagina.number - 1)">‹</button>
      <span>Página {{ pagina.number + 1 }} de {{ Math.max(pagina.totalPages, 1) }}</span>
      <button type="button" :disabled="pagina.number + 1 >= pagina.totalPages" @click="carregar(pagina.number + 1)">›</button>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  listPurchaseOrders,
  getPurchaseOrderCounts,
  updatePurchaseOrderStatus,
  deletePurchaseOrder,
  type PurchaseOrderSummary,
  type PurchaseOrderCounts,
  type Page as ApiPage,
  type PurchaseOrderStatus,
} from '@/api/purchaseOrders'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<PurchaseOrderSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<PurchaseOrderCounts | null>(null)
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const ordemAcoesAtual = computed(() =>
  pagina.value.content.find((o) => o.id === acoesAbertas.value) ?? null,
)

const STATUS_LABEL: Record<PurchaseOrderStatus, string> = {
  OPEN: 'Aberta',
  RECEIVED: 'Recebida',
  CANCELLED: 'Cancelada',
}

function statusLabel(status: PurchaseOrderStatus) {
  return STATUS_LABEL[status]
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listPurchaseOrders({
      search: filtros.busca || undefined,
      status: (filtros.status || undefined) as PurchaseOrderStatus | undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de ordens de compra.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await getPurchaseOrderCounts()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de ordens de compra.'
  }
}

function novaOrdem() {
  router.push({ name: 'compras-novo' })
}

function editarOrdem(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'compras-editar', params: { id } })
}

function toggleAcoes(id: string, event: MouseEvent) {
  if (acoesAbertas.value === id) {
    acoesAbertas.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  posicaoDropdown.value = {
    top: `${rect.bottom + 4}px`,
    left: `${rect.right - 160}px`,
  }
  acoesAbertas.value = id
}

async function marcarComoRecebida(ordem: PurchaseOrderSummary) {
  acoesAbertas.value = null
  erro.value = ''
  try {
    await updatePurchaseOrderStatus(ordem.id, 'RECEIVED')
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status da ordem de compra.'
  }
}

async function cancelarOrdem(ordem: PurchaseOrderSummary) {
  acoesAbertas.value = null
  erro.value = ''
  try {
    await updatePurchaseOrderStatus(ordem.id, 'CANCELLED')
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status da ordem de compra.'
  }
}

async function excluir(ordem: PurchaseOrderSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir a ordem de compra nº ${ordem.number}?`)) {
    return
  }
  erro.value = ''
  try {
    await deletePurchaseOrder(ordem.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir a ordem de compra.'
  }
}

onMounted(() => {
  carregar(0)
  carregarResumo()
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  font-family: var(--pm-font);
}

.busca {
  flex: 1;
}

.toolbar input,
.toolbar select {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  background: var(--pm-white);
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.resumo {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.resumo-item {
  background: var(--pm-bg);
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--pm-text-dark);
}

.resumo-open {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.resumo-received {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.resumo-cancelled {
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 12px;
}

.tabela {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  font-family: var(--pm-font);
}

.tabela th {
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  background: var(--pm-bg);
  padding: 8px 12px;
}

.tabela td {
  padding: 8px 12px;
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.badge-OPEN {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.badge-RECEIVED {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.badge-CANCELLED {
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.btn-acoes {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}

.dropdown-acoes {
  position: fixed;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  min-width: 160px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 28px rgba(0, 0, 0, 0.12);
  z-index: 10;
}

.dropdown-acoes div {
  padding: 8px 12px;
  font-size: 12px;
  cursor: pointer;
  color: var(--pm-text-dark);
}

.acao-excluir {
  color: var(--pm-error);
}

.paginacao {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 13px;
  color: var(--pm-text-mid);
}

.paginacao button {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  border-radius: 6px;
  width: 28px;
  height: 28px;
  cursor: pointer;
}

.paginacao button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
```

- [ ] **Step 2: Add the `compras` list route**

In `mesh-suite-frontend/src/router/index.ts`, add the import:

```ts
import PurchaseOrdersListView from '@/views/PurchaseOrdersListView.vue'
```

Add to the `routes` array, right before the `compras-novo` route added in Task 4:

```ts
    { path: '/compras', name: 'compras', component: PurchaseOrdersListView },
```

- [ ] **Step 3: Activate the "Compras" sidebar item**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, add a new entry to the `navItems` array, alphabetically between `Clientes` and `Empresa` (matching the array's existing alphabetical ordering after `Home`):

```ts
const navItems: NavItem[] = [
  { icon: '🏠', label: 'Home', route: '/' },
  { icon: '👥', label: 'Clientes', route: '/clientes' },
  { icon: '📥', label: 'Compras', route: '/compras' },
  { icon: '🏢', label: 'Empresa', route: null },
  { icon: '🏷', label: 'Marcas', route: null },
  { icon: '💳', label: 'Pagamentos', route: null },
  { icon: '📋', label: 'Pedidos', route: '/pedidos' },
  { icon: '🔒', label: 'Permissões', route: null },
  { icon: '📦', label: 'Produtos', route: '/produtos' },
  { icon: '💰', label: 'Tab. Preços', route: null },
  { icon: '👤', label: 'Usuários', route: '/usuarios' },
]
```

- [ ] **Step 4: Write the failing view test**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PurchaseOrdersListView from '@/views/PurchaseOrdersListView.vue'
import * as purchaseOrdersApi from '@/api/purchaseOrders'

vi.mock('@/api/purchaseOrders')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/compras', name: 'compras', component: PurchaseOrdersListView },
      { path: '/compras/novo', name: 'compras-novo', component: { template: '<div />' } },
      { path: '/compras/:id/editar', name: 'compras-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/compras')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(PurchaseOrdersListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const ordemAberta = {
  id: 'po1', number: 1, supplierName: 'Tecidos Aurora', buyerName: 'Carlos Comprador',
  orderDate: '2026-08-03', total: 250.0, status: 'OPEN' as const,
}

const ordemRecebida = {
  id: 'po2', number: 2, supplierName: 'Botões Boreal', buyerName: 'Carlos Comprador',
  orderDate: '2026-08-02', total: 90.0, status: 'RECEIVED' as const,
}

describe('PurchaseOrdersListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(purchaseOrdersApi.listPurchaseOrders).mockResolvedValue({
      content: [ordemAberta], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(purchaseOrdersApi.getPurchaseOrderCounts).mockResolvedValue({
      total: 1, open: 1, received: 0, cancelled: 0,
    })
  })

  it('loads and displays the purchase order list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('1 Total')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('aurora')
    await flushPromises()

    expect(purchaseOrdersApi.listPurchaseOrders).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'aurora' }))
  })

  it('navigates to the create form when "+ Nova Ordem de Compra" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-ordem"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('compras-novo')
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('compras-editar')
    expect(router.currentRoute.value.params.id).toBe('po1')
  })

  it('marks the order as received via the Ações menu', async () => {
    vi.mocked(purchaseOrdersApi.updatePurchaseOrderStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-receber"]').trigger('click')
    await flushPromises()

    expect(purchaseOrdersApi.updatePurchaseOrderStatus).toHaveBeenCalledWith('po1', 'RECEIVED')
  })

  it('cancels the order via the Ações menu', async () => {
    vi.mocked(purchaseOrdersApi.updatePurchaseOrderStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-cancelar"]').trigger('click')
    await flushPromises()

    expect(purchaseOrdersApi.updatePurchaseOrderStatus).toHaveBeenCalledWith('po1', 'CANCELLED')
  })

  it('hides the receber/cancelar actions once an order is already Recebida', async () => {
    vi.mocked(purchaseOrdersApi.listPurchaseOrders).mockResolvedValue({
      content: [ordemRecebida], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')

    expect(wrapper.find('[data-test="acao-receber"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="acao-cancelar"]').exists()).toBe(false)
  })

  it('excludes an order via the Ações menu after confirming', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    vi.mocked(purchaseOrdersApi.deletePurchaseOrder).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(purchaseOrdersApi.deletePurchaseOrder).toHaveBeenCalledWith('po1')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(purchaseOrdersApi.listPurchaseOrders).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de ordens de compra.')
  })
})
```

- [ ] **Step 5: Add a sidebar navigation test**

Add this test to the end of `mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts`, inside the existing `describe('AppSidebar', ...)` block:

```ts
  it('navigates to /compras when Compras is clicked', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/compras', name: 'compras', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    await wrapper.find('[data-test="nav-Compras"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/compras')
  })
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PurchaseOrdersListView.spec.ts src/components/__tests__/AppSidebar.spec.ts`
Expected: PASS (9/9 + 6/6).

- [ ] **Step 7: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all test files pass, no regressions.

- [ ] **Step 8: Manually verify in the browser**

Do not start the dev server yourself — ask the user to start it (`devup.sh` or manually), then check: sidebar shows "Compras" linking to `/compras`; the list loads, filters by busca/status; "+ Nova Ordem de Compra" opens the form; creating an order with a fornecedor, comprador and at least one item succeeds and redirects back to the list; "Marcar como Recebida"/"Cancelar" only show for `OPEN` orders and update status; "Excluir" removes the row after confirmation.

- [ ] **Step 9: Commit**

```bash
git add mesh-suite-frontend/src/views/PurchaseOrdersListView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/views/__tests__/PurchaseOrdersListView.spec.ts \
        mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts
git commit -m "feat(purchase-order): add PurchaseOrdersListView, activate Compras sidebar nav"
```

---

### Task 6: Frontend — Add `PURCHASE` to the permission system

**Files:**
- Modify: `mesh-suite-frontend/src/api/users.ts`
- Modify: `mesh-suite-frontend/src/views/UserFormView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts`

**Interfaces:**
- Consumes: `Module.PURCHASE` (backend, Task 2) — the frontend's `ModuleName` union must include `'PURCHASE'` to type-check permission payloads sent to `POST/PUT /api/users`.

- [ ] **Step 1: Add `'PURCHASE'` to `ModuleName` in `api/users.ts`**

```ts
export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE'
```

- [ ] **Step 2: Add `PURCHASE` to `UserFormView.vue`'s `MODULES`, `MODULE_LABELS` and `DEFAULT_MATRIX`**

```ts
const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER', 'PURCHASE']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
  PURCHASE: 'Compras',
}
```

`DEFAULT_MATRIX`'s `ADMIN` entry already grants every `Module`×`Action` combination via `MODULES.flatMap(...)`, so it automatically picks up `PURCHASE` once the array above changes — no edit needed there. Update the other three profiles:

```ts
const DEFAULT_MATRIX: Record<Profile, Permission[]> = {
  ADMIN: [
    ...MODULES.flatMap((m) => ACTIONS.filter((a) => !(m === 'USER' && a === 'DELETE')).map((a) => ({ module: m, action: a }))),
  ],
  MANAGER: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' }, { module: 'PRODUCT', action: 'CREATE' }, { module: 'PRODUCT', action: 'EDIT' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
    { module: 'PURCHASE', action: 'VIEW' }, { module: 'PURCHASE', action: 'CREATE' }, { module: 'PURCHASE', action: 'EDIT' },
    { module: 'USER', action: 'VIEW' },
  ],
  SALES: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
  ],
  VIEWER: [
    { module: 'CUSTOMER', action: 'VIEW' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' },
    { module: 'PURCHASE', action: 'VIEW' },
  ],
}
```

`SALES` deliberately gets no `PURCHASE` grants — a sales rep has no business role in purchasing. `MANAGER` gets the same VIEW/CREATE/EDIT shape it already has for the other operational modules; `VIEWER` gets read-only, matching its existing pattern.

- [ ] **Step 3: Add a permission-grid regression test**

Add this test to `mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts`, alongside the existing permission-grid tests (find the test asserting `perm-CUSTOMER-VIEW`/`perm-CUSTOMER-CREATE` checked state and add this one next to it):

```ts
  it('includes Compras in the permission grid and pre-checks it for the Admin profile', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="profile"]').setValue('ADMIN')
    await wrapper.find('[data-test="profile"]').trigger('change')

    expect(wrapper.text()).toContain('Compras')
    expect((wrapper.find('[data-test="perm-PURCHASE-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PURCHASE-CREATE"]').element as HTMLInputElement).checked).toBe(true)
  })
```

If the existing test file's helper is not named `mountWithRouter` or the profile select does not carry `data-test="profile"`, read the existing file first and match its actual helper name and selectors instead of introducing new ones.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/UserFormView.spec.ts`
Expected: PASS, including the new test.

- [ ] **Step 5: Run the full frontend suite and the full backend suite to check for regressions**

Run: `cd mesh-suite-frontend && npx vitest run`
Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS / all tests pass on both, no regressions anywhere.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/api/users.ts \
        mesh-suite-frontend/src/views/UserFormView.vue \
        mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts
git commit -m "feat(purchase-order): add PURCHASE module to the permission grid and default matrix"
```
