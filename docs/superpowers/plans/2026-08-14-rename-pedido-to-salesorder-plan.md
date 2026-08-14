# Rename Pedido → SalesOrder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename `Pedido` (sales order) to `SalesOrder` throughout the backend and frontend, in its own top-level `com.meshsuite.salesorder` package, in English, while keeping every end-customer-visible string (routes, UI labels, error messages) in Portuguese unchanged. This is the last remaining sub-project of the mesh-suite English-rename initiative.

**Architecture:** The most cross-module-coupled sub-project since Parceiro/Produto. `com.meshsuite.pedido` becomes `com.meshsuite.salesorder` (straight in-place rename, own package). The `Sale` module (already renamed, merged) has a real functional dependency on `Pedido` — `SaleService.issue()` loads a `Pedido`, checks its status, and copies its data into a new `Sale` — so this plan includes a dedicated bridge task for `com.meshsuite.sale`'s files, updating only their `Pedido`-referencing code to the new `SalesOrder` types without renaming anything that belongs to `Sale` itself. All of `SalesOrder`'s own method/field names are translated fully to English (list/create/update/delete/findById style, matching `PartnerService`'s convention — chosen explicitly for this sub-project over the alternate `listar/criar/atualizar` style some earlier modules used).

**Tech Stack:** Spring Boot 3.4.5, Java 21, PostgreSQL 16, Flyway, Vue 3, TypeScript, Vitest.

## Global Constraints

- End-customer-visible text (frontend routes/labels, UI text, error messages) stays in Portuguese, unchanged, character-for-character.
- Backend REST endpoint paths ARE code, not user-visible text, and DO get translated: `/api/pedidos` → `/api/sales-orders`, sub-path `/resumo` → `/counts` (matching the sibling `purchaseorder` module's own `/counts` endpoint).
- The query parameter name `busca` stays Portuguese everywhere (matches the established convention used by every prior sub-project).
- Enum `StatusPedido` → `SalesOrderStatus`, values `DIGITADO→DRAFT`, `EM_PREPARO→IN_PREPARATION`, `FATURADO→INVOICED`. Persisted as a string, so the CHECK constraint and every wire-level JSON status value change too — this is NOT limited to Java identifiers.
- All of `SalesOrder`'s own method and field names are translated fully to English (`listar→list`, `criar→create`, `atualizar→update`, `avancarStatus→advanceStatus`, `excluir→delete`, `buscarPorId→findById`, `resumo→counts`, field `pedidoRepository→salesOrderRepository`, `parceiroRepository→partnerRepository`, `produtoRepository→productRepository`) — this was an explicit decision for this sub-project, resolving an inconsistency found between `PartnerService` (fully English) and `ProductService`/`PriceTableService` (kept Portuguese verbs).
- Name map (entities/classes): `Pedido→SalesOrder`, `ItemPedido→SalesOrderItem`, `PedidoContador→SalesOrderCounter`, `StatusPedido→SalesOrderStatus`, `PedidoRepository→SalesOrderRepository`, `PedidoContadorRepository→SalesOrderCounterRepository`, `PedidoSpecifications→SalesOrderSpecifications`, `PedidoController→SalesOrderController`, `PedidoService→SalesOrderService`, `PedidoExceptionHandler→SalesOrderExceptionHandler`, `PedidoNaoEncontradoException→SalesOrderNotFoundException`, `PedidoValidacaoException→SalesOrderValidationException`.
- DTO map: `PedidoRequest→SalesOrderRequest`, `PedidoResponse→SalesOrderResponse`, `PedidoResumoResponse→SalesOrderCountsResponse`, `PedidoStatusRequest→SalesOrderStatusRequest`, `PedidoSummaryResponse→SalesOrderSummaryResponse`, `ItemPedidoDto→SalesOrderItemRequest`, `ItemPedidoResponse→SalesOrderItemResponse`.
- Entity field map: `SalesOrder`: `numero→number`, `cliente→customer`, `vendedor→salesperson`, `dataPedido→orderDate`, `dataEntrega→deliveryDate`, `desconto→discount`, `criadoEm→createdAt`, `itens→items`. `SalesOrderItem`: `pedido→salesOrder`, `produto→product`, `quantidade→quantity`, `valorUnitario→unitPrice`, `valorTotal→totalAmount`. `SalesOrderCounter`: `proximoNumero→nextNumber`.
- DTO field map (record components, also the JSON wire field names): `SalesOrderRequest(customerId, salespersonId, orderDate, deliveryDate, discount, items)`; `SalesOrderResponse(id, number, customerId, customerName, salespersonId, salespersonName, orderDate, deliveryDate, status, discount, subtotal, total, items)`; `SalesOrderCountsResponse(total, draft, inPreparation, invoiced)`; `SalesOrderStatusRequest(status)`; `SalesOrderSummaryResponse(id, number, customerName, salespersonName, orderDate, total, status)`; `SalesOrderItemRequest(productId, quantity, unitPrice)`; `SalesOrderItemResponse(productId, productName, quantity, unitPrice, totalAmount)`.
- Table/column map: `pedido→sales_order`, `item_pedido→sales_order_item`, `pedido_contador→sales_order_counter`; `cliente_id→customer_id`, `vendedor_id→salesperson_id`, `data_pedido→order_date`, `data_entrega→delivery_date`, `criado_em→created_at`, `numero→number`; `pedido_id→sales_order_id`, `produto_id→product_id`, `quantidade→quantity`, `valor_unitario→unit_price`, `valor_total→total_amount`; `proximo_numero→next_number`. Index/RLS-policy names ARE translated too (matching `sale`'s own migration style), unlike Município where the migration was too large to fully rewrite.
- `V26__create_sale.sql` (already merged) has `order_id UUID NOT NULL UNIQUE REFERENCES pedido(id)` — this FK must be updated to `REFERENCES sales_order(id)` in the same task that renames the `pedido` table, or the Flyway migration sequence breaks.
- Frontend: `api/pedidos.ts→api/salesOrders.ts` with fully translated function/type names (`listarPedidos→listSalesOrders`, `buscarPedido→getSalesOrder`, `criarPedido→createSalesOrder`, `atualizarPedido→updateSalesOrder`, `avancarStatusPedido→advanceSalesOrderStatus`, `excluirPedido→deleteSalesOrder`, `buscarResumoPedidos→getSalesOrderCounts`); own views `PedidoFormView.vue→SalesOrderFormView.vue`, `PedidosListView.vue→SalesOrdersListView.vue` get every internal identifier and `data-test` attribute translated to English; routes/route-names stay Portuguese unchanged (`/pedidos`, `pedidos`, etc.); `router/index.ts` and `DashboardView.vue`/`.spec.ts` are bridge-only — only the imports/types/values that come from the renamed module change, every local variable/function name that belongs to those other files stays as-is.

---

## Task 1: Migration

**Files:**
- Delete: `mesh-suite-backend/src/main/resources/db/migration/V7__create_pedido.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V7__create_salesorder.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V11__create_purchase_order.sql:56` (comment only)
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V13__create_stock_movement.sql:21,23` (comment only)

**Interfaces:**
- Produces: tables `sales_order_counter(tenant_id, next_number)`, `sales_order(id, tenant_id, number, customer_id, salesperson_id, order_date, delivery_date, status, discount, subtotal, total, created_at)`, `sales_order_item(id, sales_order_id, product_id, quantity, unit_price, total_amount)`.

- [ ] **Step 1: Create the renamed migration file**

Write `mesh-suite-backend/src/main/resources/db/migration/V7__create_salesorder.sql` with this exact content:

```sql
CREATE TABLE sales_order_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE sales_order_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_order_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY sales_order_counter_tenant_isolation ON sales_order_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE sales_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    customer_id UUID NOT NULL REFERENCES partner(id),
    salesperson_id UUID NOT NULL REFERENCES usuario(id),
    order_date DATE NOT NULL DEFAULT CURRENT_DATE,
    delivery_date DATE,
    status VARCHAR(15) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','IN_PREPARATION','INVOICED')),
    discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_sales_order_tenant_number ON sales_order(tenant_id, number);
CREATE INDEX idx_sales_order_tenant_id ON sales_order(tenant_id);
CREATE INDEX idx_sales_order_customer_id ON sales_order(customer_id);
CREATE INDEX idx_sales_order_salesperson_id ON sales_order(salesperson_id);

ALTER TABLE sales_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_order FORCE ROW LEVEL SECURITY;

CREATE POLICY sales_order_tenant_isolation ON sales_order
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE sales_order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id UUID NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_sales_order_item_sales_order_id ON sales_order_item(sales_order_id);

ALTER TABLE sales_order_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_order_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent sales_order
-- row's own RLS policy, matched by sales_order_id. Same pattern as partner_contact.
CREATE POLICY sales_order_item_tenant_isolation ON sales_order_item
    USING (EXISTS (
        SELECT 1 FROM sales_order so
        WHERE so.id = sales_order_item.sales_order_id
          AND so.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

Note `salesperson_id UUID NOT NULL REFERENCES usuario(id)` keeps referencing `usuario` by name unchanged — this is historical migration text; the actual table was later renamed to `app_user` by a subsequent migration, and Postgres tracks FK targets by OID, not by the name written in old migration source, so this line does not need updating (same reasoning documented in the original `V7__create_pedido.sql`, unchanged).

The status column width changed from `VARCHAR(10)` to `VARCHAR(15)` because `IN_PREPARATION` (14 chars) is longer than `EM_PREPARO` (10 chars) — `VARCHAR(10)` would truncate/reject the new value.

- [ ] **Step 2: Delete the old migration file**

```bash
git rm mesh-suite-backend/src/main/resources/db/migration/V7__create_pedido.sql
```

- [ ] **Step 3: Fix the cross-migration FK in `V26__create_sale.sql`**

In `mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql`, change line 18 from:
```sql
    order_id UUID NOT NULL UNIQUE REFERENCES pedido(id),
```
to:
```sql
    order_id UUID NOT NULL UNIQUE REFERENCES sales_order(id),
```

This is required — `V26` runs after `V7` in the Flyway sequence, and once `V7` no longer creates a table named `pedido`, this FK must target the new `sales_order` table by name or the migration fails outright.

- [ ] **Step 4: Update the 2 stale prose comments in unrelated migrations**

In `mesh-suite-backend/src/main/resources/db/migration/V11__create_purchase_order.sql`, line 56, change:
```sql
-- pattern as item_pedido/partner_contact.
```
to:
```sql
-- pattern as sales_order_item/partner_contact.
```

In `mesh-suite-backend/src/main/resources/db/migration/V13__create_stock_movement.sql`, lines 21 and 23, change:
```sql
-- Own tenant_id column and own policy -- unlike item_pedido/purchase_order_item,
```
to:
```sql
-- Own tenant_id column and own policy -- unlike sales_order_item/purchase_order_item,
```
and:
```sql
-- row in its own right, same pattern as pedido/purchase_order themselves.
```
to:
```sql
-- row in its own right, same pattern as sales_order/purchase_order themselves.
```

These are comment-only edits with no functional effect — confirm with `git diff` that no non-comment line in either file changed.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V7__create_salesorder.sql \
        mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql \
        mesh-suite-backend/src/main/resources/db/migration/V11__create_purchase_order.sql \
        mesh-suite-backend/src/main/resources/db/migration/V13__create_stock_movement.sql
git commit -m "refactor(salesorder): rename V7 migration pedido->sales_order, fix V26 FK reference"
```

Do not run `mvn test` yet — `com.meshsuite.pedido` still exists and references the old table/columns; the backend won't compile-and-migrate cleanly until Task 2 lands. Verification happens starting in Task 2.

---

## Task 2: Domain + enum

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/domain/SalesOrder.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/domain/SalesOrderItem.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/domain/SalesOrderCounter.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/domain/enums/SalesOrderStatus.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/Pedido.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/ItemPedido.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/PedidoContador.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/enums/StatusPedido.java`

**Interfaces:**
- Consumes: Task 1's `sales_order`/`sales_order_item`/`sales_order_counter` tables.
- Produces: `com.meshsuite.salesorder.domain.SalesOrder` (getters/setters for `id, tenantId, number, customer, salesperson, orderDate, deliveryDate, status, discount, subtotal, total, createdAt, items`), `SalesOrderItem` (`id, salesOrder, product, quantity, unitPrice, totalAmount`), `SalesOrderCounter` (`tenantId, nextNumber`), `SalesOrderStatus` enum (`DRAFT, IN_PREPARATION, INVOICED`).

This task alone will NOT make the backend compile (the repository/service/controller/other-module files still reference the old package) — that's expected; full green compile lands at the end of Task 8.

- [ ] **Step 1: Create `SalesOrderStatus.java`**

```java
package com.meshsuite.salesorder.domain.enums;

public enum SalesOrderStatus {
    DRAFT,
    IN_PREPARATION,
    INVOICED
}
```

- [ ] **Step 2: Create `SalesOrder.java`**

```java
package com.meshsuite.salesorder.domain;

import com.meshsuite.partner.domain.Partner;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.user.domain.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "sales_order")
@Getter
@Setter
public class SalesOrder {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Integer number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Partner customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesperson_id", nullable = false)
    private User salesperson;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate = LocalDate.now();

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SalesOrderStatus status = SalesOrderStatus.DRAFT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SalesOrderItem> items = new ArrayList<>();
}
```

- [ ] **Step 3: Create `SalesOrderItem.java`**

```java
package com.meshsuite.salesorder.domain;

import com.meshsuite.product.domain.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sales_order_item")
@Getter
@Setter
public class SalesOrderItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
}
```

- [ ] **Step 4: Create `SalesOrderCounter.java`**

```java
package com.meshsuite.salesorder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "sales_order_counter")
@Getter
@Setter
public class SalesOrderCounter {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber = 1;
}
```

- [ ] **Step 5: Delete the 4 old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/Pedido.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/ItemPedido.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/PedidoContador.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/enums/StatusPedido.java
```

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/salesorder/domain/
git commit -m "refactor(salesorder): rename Pedido/ItemPedido/PedidoContador/StatusPedido domain to English"
```

Do not attempt to compile the whole module yet — this task's own new files have no compile errors in isolation, but the rest of the backend (repository, service, controller, Sale bridge) still references the deleted `com.meshsuite.pedido` package. That's expected; full-module compile succeeds after Task 8.

---

## Task 3: Repository + Specifications

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/repository/SalesOrderRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/repository/SalesOrderCounterRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/repository/specification/SalesOrderSpecifications.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/salesorder/repository/SalesOrderRepositoryTest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/repository/PedidoRepository.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/repository/PedidoContadorRepository.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/repository/specification/PedidoSpecifications.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/repository/PedidoRepositoryTest.java`

**Interfaces:**
- Consumes: Task 2's `SalesOrder`, `SalesOrderStatus`.
- Produces: `SalesOrderRepository` (`countByStatus(SalesOrderStatus): long`, plus standard `JpaRepository`/`JpaSpecificationExecutor`), `SalesOrderCounterRepository` (standard `JpaRepository`), `SalesOrderSpecifications.withSearch(String): Specification<SalesOrder>`, `SalesOrderSpecifications.withStatus(SalesOrderStatus): Specification<SalesOrder>`.

- [ ] **Step 1: Create `SalesOrderRepository.java`**

```java
package com.meshsuite.salesorder.repository;

import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID>, JpaSpecificationExecutor<SalesOrder> {
    long countByStatus(SalesOrderStatus status);
}
```

- [ ] **Step 2: Create `SalesOrderCounterRepository.java`**

```java
package com.meshsuite.salesorder.repository;

import com.meshsuite.salesorder.domain.SalesOrderCounter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderCounterRepository extends JpaRepository<SalesOrderCounter, UUID> {
}
```

- [ ] **Step 3: Create `SalesOrderSpecifications.java`**

```java
package com.meshsuite.salesorder.repository.specification;

import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import org.springframework.data.jpa.domain.Specification;

public final class SalesOrderSpecifications {

    private SalesOrderSpecifications() {
    }

    public static Specification<SalesOrder> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        Integer number = tryParseInt(search.trim());
        return (root, query, cb) -> {
            var byText = cb.or(
                    cb.like(cb.lower(root.get("customer").get("tradeName")), term),
                    cb.like(cb.lower(root.get("salesperson").get("name")), term));
            if (number != null) {
                return cb.or(byText, cb.equal(root.get("number"), number));
            }
            return byText;
        };
    }

    public static Specification<SalesOrder> withStatus(SalesOrderStatus status) {
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

- [ ] **Step 4: Create `SalesOrderRepositoryTest.java`**

Translated from `PedidoRepositoryTest.java` (deleted next step). Method name translations: `criarCliente→createCustomer`, `criarVendedor→createSalesperson`, `criarProduto→createProduct`, `novoPedido→newOrder`; `savesPedidoWithItensViaCascade→savesSalesOrderWithItemsViaCascade`, `removingAnItemFromTheListDeletesItViaOrphanRemoval` unchanged, `numeroMustBeUniquePerTenant→numberMustBeUniquePerTenant`, `rlsHidesRowsWhenTenantContextUnset` unchanged, `proximoNumeroIncrementsAtomicallyPerTenant→nextNumberIncrementsAtomicallyPerTenant`, `pedidoContadorRlsHidesRowsWhenTenantContextUnset→salesOrderCounterRlsHidesRowsWhenTenantContextUnset`, `itemPedidoRlsHidesRowsWhenTenantContextUnset→salesOrderItemRlsHidesRowsWhenTenantContextUnset`.

```java
package com.meshsuite.salesorder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.salesorder.domain.SalesOrderItem;
import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.SalesOrderCounter;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
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

class SalesOrderRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderCounterRepository salesOrderCounterRepository;
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

    private Partner createCustomer(UUID tenantId, String documento) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(documento);
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
        return productRepository.saveAndFlush(p);
    }

    private SalesOrder newOrder(UUID tenantId, Partner customer, User salesperson, int number) {
        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setNumber(number);
        order.setCustomer(customer);
        order.setSalesperson(salesperson);
        return order;
    }

    @Test
    @Transactional
    void savesSalesOrderWithItemsViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Product product = createProduct(tenant.getId(), "P0001");

        SalesOrder order = newOrder(tenant.getId(), customer, salesperson, 1);
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrder(order);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("119.80"));
        order.getItems().add(item);

        SalesOrder saved = salesOrderRepository.saveAndFlush(order);
        entityManager.clear();

        SalesOrder reloaded = salesOrderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SalesOrderStatus.DRAFT);
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getItems().get(0).getTotalAmount()).isEqualByComparingTo("119.80");
    }

    @Test
    @Transactional
    void removingAnItemFromTheListDeletesItViaOrphanRemoval() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Product product = createProduct(tenant.getId(), "P0001");

        SalesOrder order = newOrder(tenant.getId(), customer, salesperson, 1);
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrder(order);
        item.setProduct(product);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("59.90"));
        order.getItems().add(item);
        SalesOrder saved = salesOrderRepository.saveAndFlush(order);

        saved.getItems().clear();
        salesOrderRepository.saveAndFlush(saved);
        entityManager.clear();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sales_order_item WHERE sales_order_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void numberMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");

        salesOrderRepository.saveAndFlush(newOrder(tenant.getId(), customer, salesperson, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> salesOrderRepository.saveAndFlush(newOrder(tenant.getId(), customer, salesperson, 1)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        salesOrderRepository.saveAndFlush(newOrder(tenant.getId(), customer, salesperson, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sales_order")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void nextNumberIncrementsAtomicallyPerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        entityManager.createNativeQuery(
                "INSERT INTO sales_order_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                        "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenant.getId())
                .executeUpdate();

        Object first = entityManager.createNativeQuery(
                        "UPDATE sales_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();
        Object second = entityManager.createNativeQuery(
                        "UPDATE sales_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();

        assertThat(((Number) first).intValue()).isEqualTo(1);
        assertThat(((Number) second).intValue()).isEqualTo(2);
    }

    @Test
    @Transactional
    void salesOrderCounterRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        SalesOrderCounter counter = new SalesOrderCounter();
        counter.setTenantId(tenant.getId());
        counter.setNextNumber(1);
        salesOrderCounterRepository.saveAndFlush(counter);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sales_order_counter")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void salesOrderItemRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Partner customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Product product = createProduct(tenant.getId(), "P0001");

        SalesOrder order = newOrder(tenant.getId(), customer, salesperson, 1);
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrder(order);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("119.80"));
        order.getItems().add(item);
        SalesOrder saved = salesOrderRepository.saveAndFlush(order);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sales_order_item WHERE sales_order_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 5: Delete the 4 old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/pedido/repository/PedidoRepository.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/repository/PedidoContadorRepository.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/repository/specification/PedidoSpecifications.java \
       mesh-suite-backend/src/test/java/com/meshsuite/pedido/repository/PedidoRepositoryTest.java
```

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/salesorder/repository/ \
        mesh-suite-backend/src/test/java/com/meshsuite/salesorder/repository/
git commit -m "refactor(salesorder): rename PedidoRepository/PedidoContadorRepository/PedidoSpecifications to English"
```

Full-module compile still fails at this point (service/controller/DTOs/exceptions/Sale bridge not yet updated) — expected, do not attempt `mvn compile` yet. `SalesOrderRepositoryTest` itself won't compile standalone either until Task 2's domain classes exist (they already do, from the prior task) — it should compile and run cleanly once this task's own files are all in place.

---

## Task 4: DTOs

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/dto/SalesOrderRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/dto/SalesOrderResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/dto/SalesOrderCountsResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/dto/SalesOrderStatusRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/dto/SalesOrderSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/dto/SalesOrderItemRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/dto/SalesOrderItemResponse.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoRequest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoResponse.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoResumoResponse.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoStatusRequest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoSummaryResponse.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/ItemPedidoDto.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/ItemPedidoResponse.java`

**Interfaces:**
- Consumes: Task 2's `SalesOrderStatus`.
- Produces: exact record signatures below — Task 6 (service) and Task 7 (controller) construct/consume these.

- [ ] **Step 1: Create `SalesOrderItemRequest.java`**

```java
package com.meshsuite.salesorder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesOrderItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice) {
}
```

- [ ] **Step 2: Create `SalesOrderItemResponse.java`**

```java
package com.meshsuite.salesorder.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesOrderItemResponse(
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount) {
}
```

- [ ] **Step 3: Create `SalesOrderRequest.java`**

```java
package com.meshsuite.salesorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesOrderRequest(
        @NotNull UUID customerId,
        @NotNull UUID salespersonId,
        LocalDate orderDate,
        LocalDate deliveryDate,
        BigDecimal discount,
        @NotEmpty List<@Valid SalesOrderItemRequest> items) {
}
```

- [ ] **Step 4: Create `SalesOrderResponse.java`**

```java
package com.meshsuite.salesorder.dto;

import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesOrderResponse(
        UUID id,
        Integer number,
        UUID customerId,
        String customerName,
        UUID salespersonId,
        String salespersonName,
        LocalDate orderDate,
        LocalDate deliveryDate,
        SalesOrderStatus status,
        BigDecimal discount,
        BigDecimal subtotal,
        BigDecimal total,
        List<SalesOrderItemResponse> items) {
}
```

- [ ] **Step 5: Create `SalesOrderCountsResponse.java`**

```java
package com.meshsuite.salesorder.dto;

public record SalesOrderCountsResponse(long total, long draft, long inPreparation, long invoiced) {
}
```

- [ ] **Step 6: Create `SalesOrderStatusRequest.java`**

```java
package com.meshsuite.salesorder.dto;

import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import jakarta.validation.constraints.NotNull;

public record SalesOrderStatusRequest(@NotNull SalesOrderStatus status) {
}
```

- [ ] **Step 7: Create `SalesOrderSummaryResponse.java`**

```java
package com.meshsuite.salesorder.dto;

import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalesOrderSummaryResponse(
        UUID id,
        Integer number,
        String customerName,
        String salespersonName,
        LocalDate orderDate,
        BigDecimal total,
        SalesOrderStatus status) {
}
```

- [ ] **Step 8: Delete the 7 old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoRequest.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoResumoResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoStatusRequest.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoSummaryResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/ItemPedidoDto.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/ItemPedidoResponse.java
```

- [ ] **Step 9: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/salesorder/dto/
git commit -m "refactor(salesorder): rename Pedido DTOs to English, resolve Resumo/Summary via SalesOrderCountsResponse"
```

---

## Task 5: Exceptions + GlobalExceptionHandler retarget

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/exception/SalesOrderExceptionHandler.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/exception/SalesOrderNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/exception/SalesOrderValidationException.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/exception/PedidoExceptionHandler.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/exception/PedidoNaoEncontradoException.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/exception/PedidoValidacaoException.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java:79-89`

**Interfaces:**
- Produces: `SalesOrderNotFoundException` (no-arg constructor, message "Pedido não encontrado"), `SalesOrderValidationException(String message)`.

Note: `SalesOrderController` does not exist yet (Task 7) — `SalesOrderExceptionHandler`'s `@RestControllerAdvice(assignableTypes = ...)` will reference a type that doesn't compile until Task 7 lands. This is expected and matches the plan's task order; the whole module compiles once Task 8 finishes.

- [ ] **Step 1: Create `SalesOrderNotFoundException.java`**

```java
package com.meshsuite.salesorder.exception;

public class SalesOrderNotFoundException extends RuntimeException {
    public SalesOrderNotFoundException() {
        super("Pedido não encontrado");
    }
}
```

- [ ] **Step 2: Create `SalesOrderValidationException.java`**

```java
package com.meshsuite.salesorder.exception;

public class SalesOrderValidationException extends RuntimeException {
    public SalesOrderValidationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Create `SalesOrderExceptionHandler.java`**

```java
package com.meshsuite.salesorder.exception;

import com.meshsuite.salesorder.controller.SalesOrderController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SalesOrderController.class)
public class SalesOrderExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Não foi possível salvar o pedido. Tente novamente."));
    }
}
```

- [ ] **Step 4: Delete the 3 old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/pedido/exception/PedidoExceptionHandler.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/exception/PedidoNaoEncontradoException.java \
       mesh-suite-backend/src/main/java/com/meshsuite/pedido/exception/PedidoValidacaoException.java
```

- [ ] **Step 5: Retarget the 2 handlers in `GlobalExceptionHandler.java`**

In `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`, change lines 79-89 from:
```java
    @ExceptionHandler(com.meshsuite.pedido.exception.PedidoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handlePedidoNaoEncontrado(
            com.meshsuite.pedido.exception.PedidoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pedido.exception.PedidoValidacaoException.class)
    public ResponseEntity<Map<String, String>> handlePedidoValidacao(
            com.meshsuite.pedido.exception.PedidoValidacaoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```
to:
```java
    @ExceptionHandler(com.meshsuite.salesorder.exception.SalesOrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSalesOrderNotFound(
            com.meshsuite.salesorder.exception.SalesOrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.salesorder.exception.SalesOrderValidationException.class)
    public ResponseEntity<Map<String, String>> handleSalesOrderValidation(
            com.meshsuite.salesorder.exception.SalesOrderValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/salesorder/exception/ \
        mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java
git commit -m "refactor(salesorder): rename Pedido exceptions to English, retarget GlobalExceptionHandler"
```

---

## Task 6: Service

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/service/SalesOrderService.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/salesorder/service/SalesOrderServiceTest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/service/PedidoService.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/service/PedidoServiceTest.java`

**Interfaces:**
- Consumes: Task 2's `SalesOrder`/`SalesOrderItem`/`SalesOrderStatus`, Task 3's `SalesOrderRepository`/`SalesOrderSpecifications`, Task 4's DTOs, Task 5's `SalesOrderNotFoundException`/`SalesOrderValidationException`.
- Produces: `SalesOrderService` with public methods `list(String, SalesOrderStatus, Pageable): Page<SalesOrderSummaryResponse>`, `counts(): SalesOrderCountsResponse`, `findById(UUID): SalesOrderResponse`, `create(UUID, SalesOrderRequest): SalesOrderResponse`, `update(UUID, SalesOrderRequest): SalesOrderResponse`, `advanceStatus(UUID, SalesOrderStatus): SalesOrderResponse`, `delete(UUID): void` — Task 7 (controller) and Task 8 (Sale bridge) call these exact names.

- [ ] **Step 1: Create `SalesOrderService.java`**

```java
package com.meshsuite.salesorder.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.salesorder.domain.SalesOrderItem;
import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.salesorder.dto.*;
import com.meshsuite.salesorder.exception.SalesOrderNotFoundException;
import com.meshsuite.salesorder.exception.SalesOrderValidationException;
import com.meshsuite.salesorder.repository.SalesOrderRepository;
import com.meshsuite.salesorder.repository.specification.SalesOrderSpecifications;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final PartnerRepository partnerRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    public SalesOrderService(SalesOrderRepository salesOrderRepository, PartnerRepository partnerRepository,
                              UserRepository userRepository, ProductRepository productRepository,
                              EntityManager entityManager) {
        this.salesOrderRepository = salesOrderRepository;
        this.partnerRepository = partnerRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public Page<SalesOrderSummaryResponse> list(String search, SalesOrderStatus status, Pageable pageable) {
        Specification<SalesOrder> spec = Specification.allOf(
                SalesOrderSpecifications.withSearch(search),
                SalesOrderSpecifications.withStatus(status));
        return salesOrderRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public SalesOrderCountsResponse counts() {
        long draft = salesOrderRepository.countByStatus(SalesOrderStatus.DRAFT);
        long inPreparation = salesOrderRepository.countByStatus(SalesOrderStatus.IN_PREPARATION);
        long invoiced = salesOrderRepository.countByStatus(SalesOrderStatus.INVOICED);
        return new SalesOrderCountsResponse(draft + inPreparation + invoiced, draft, inPreparation, invoiced);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public SalesOrderResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.CREATE)
    public SalesOrderResponse create(UUID tenantId, SalesOrderRequest request) {
        Partner customer = findValidCustomer(request.customerId());
        User salesperson = findValidSalesperson(request.salespersonId());

        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setTenantId(tenantId);
        salesOrder.setNumber(nextNumber(tenantId));
        apply(salesOrder, customer, salesperson, request);
        return toResponse(salesOrderRepository.saveAndFlush(salesOrder));
    }

    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.EDIT)
    public SalesOrderResponse update(UUID id, SalesOrderRequest request) {
        Partner customer = findValidCustomer(request.customerId());
        User salesperson = findValidSalesperson(request.salespersonId());

        SalesOrder salesOrder = findEntityById(id);
        apply(salesOrder, customer, salesperson, request);
        return toResponse(salesOrderRepository.saveAndFlush(salesOrder));
    }

    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.EDIT)
    public SalesOrderResponse advanceStatus(UUID id, SalesOrderStatus newStatus) {
        if (newStatus == SalesOrderStatus.INVOICED) {
            throw new SalesOrderValidationException(
                    "Faturamento deve ser feito através do fluxo de Venda (POST /api/sales/issue/{orderId})");
        }
        SalesOrder salesOrder = findEntityById(id);
        int current = salesOrder.getStatus().ordinal();
        int target = newStatus.ordinal();
        if (target != current + 1) {
            throw new SalesOrderValidationException(
                    "Não é possível avançar de " + salesOrder.getStatus() + " para " + newStatus);
        }
        salesOrder.setStatus(newStatus);
        return toResponse(salesOrderRepository.saveAndFlush(salesOrder));
    }

    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.DELETE)
    public void delete(UUID id) {
        salesOrderRepository.delete(findEntityById(id));
    }

    private SalesOrder findEntityById(UUID id) {
        return salesOrderRepository.findById(id).orElseThrow(SalesOrderNotFoundException::new);
    }

    private Partner findValidCustomer(UUID customerId) {
        Partner partner = partnerRepository.findById(customerId)
                .orElseThrow(() -> new SalesOrderValidationException("Cliente não encontrado"));
        if (!partner.getRoles().contains(PartnerRole.CUSTOMER)) {
            throw new SalesOrderValidationException("O parceiro selecionado não tem o papel Cliente");
        }
        return partner;
    }

    private User findValidSalesperson(UUID salespersonId) {
        User user = userRepository.findById(salespersonId)
                .orElseThrow(() -> new SalesOrderValidationException("Vendedor não encontrado"));
        if (user.getRole() != Role.SALES_REP) {
            throw new SalesOrderValidationException("O usuário selecionado não tem o papel Representante");
        }
        return user;
    }

    // Atomic UPDATE ... RETURNING against the tenant's single sales_order_counter row --
    // never COUNT(*)/MAX(number)+1, both of which race under concurrent inserts.
    // Runs inside this method's own @Transactional, so TenantContextAspect has
    // already issued SET LOCAL app.tenant_id before either native query below runs.
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO sales_order_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE sales_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private void apply(SalesOrder salesOrder, Partner customer, User salesperson, SalesOrderRequest request) {
        salesOrder.setCustomer(customer);
        salesOrder.setSalesperson(salesperson);
        salesOrder.setOrderDate(request.orderDate() != null ? request.orderDate() : LocalDate.now());
        salesOrder.setDeliveryDate(request.deliveryDate());
        salesOrder.setDiscount(request.discount() != null ? request.discount() : BigDecimal.ZERO);

        salesOrder.getItems().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SalesOrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new SalesOrderValidationException("Produto não encontrado"));
            SalesOrderItem item = new SalesOrderItem();
            item.setSalesOrder(salesOrder);
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(itemRequest.unitPrice());
            BigDecimal totalAmountItem = itemRequest.quantity().multiply(itemRequest.unitPrice());
            item.setTotalAmount(totalAmountItem);
            salesOrder.getItems().add(item);
            subtotal = subtotal.add(totalAmountItem);
        }
        salesOrder.setSubtotal(subtotal);
        salesOrder.setTotal(subtotal.subtract(salesOrder.getDiscount()));
    }

    private SalesOrderSummaryResponse toSummary(SalesOrder s) {
        return new SalesOrderSummaryResponse(s.getId(), s.getNumber(), s.getCustomer().getTradeName(),
                s.getSalesperson().getName(), s.getOrderDate(), s.getTotal(), s.getStatus());
    }

    private SalesOrderResponse toResponse(SalesOrder s) {
        List<SalesOrderItemResponse> items = s.getItems().stream()
                .map(i -> new SalesOrderItemResponse(i.getProduct().getId(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPrice(), i.getTotalAmount()))
                .toList();
        return new SalesOrderResponse(s.getId(), s.getNumber(), s.getCustomer().getId(), s.getCustomer().getTradeName(),
                s.getSalesperson().getId(), s.getSalesperson().getName(), s.getOrderDate(), s.getDeliveryDate(),
                s.getStatus(), s.getDiscount(), s.getSubtotal(), s.getTotal(), items);
    }
}
```

- [ ] **Step 2: Create `SalesOrderServiceTest.java`**

Translated from `PedidoServiceTest.java` (deleted next step). Method name translations: `criarCliente→createCustomer`, `criarFornecedor→createSupplier`, `criarVendedor→createSalesperson`, `criarAdministrativo→createAdministrative`, `criarProduto→createProduct`, `request(...)` helper unchanged; `criaERecuperaPedidoComNumeroENoStatusInicial→createsAndRetrievesSalesOrderWithNumberAndInitialStatus`, `numeroIncrementaSequencialmentePorTenant→numberIncrementsSequentiallyPerTenant`, `numeracaoReiniciaEmTenantDiferente→numberingRestartsInDifferentTenant`, `rejeitaClienteSemPapelCliente→rejectsCustomerWithoutCustomerRole`, `rejeitaVendedorSemPapelRepresentante→rejectsSalespersonWithoutSalesRepRole`, `calculaSubtotalDescontoETotal→calculatesSubtotalDiscountAndTotal`, `valorUnitarioDoItemNaoMudaQuandoPrecoDoProdutoMudaDepois→itemUnitPriceDoesNotChangeWhenProductPriceChangesLater`, `avancaDeDigitadoParaEmPreparo→advancesFromDraftToInPreparation`, `rejeitaPularEtapaDeStatus→rejectsSkippingAStatusStep`, `rejeitaRetrocederStatus→rejectsRegressingStatus`, `resumoContaPorStatus→countsTallyPerStatus`, `listaComFiltroDeBuscaPorNumero→listsWithSearchFilterByNumber`, `listaComFiltroDeBuscaPorCliente→listsWithSearchFilterByCustomer`, `excluiPedido→deletesSalesOrder`, `deniesListingWhenCallerLacksOrderViewPermission` unchanged, `rejeitaFaturarViaAvancarStatus→rejectsInvoicingViaAdvanceStatus`.

```java
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

        var page = salesOrderService.list(String.valueOf(created.number()), null, PageRequest.of(0, 10));

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

        var page = salesOrderService.list("mercado silva", null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
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
                () -> salesOrderService.list(null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
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
```

- [ ] **Step 3: Delete the 2 old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/pedido/service/PedidoService.java \
       mesh-suite-backend/src/test/java/com/meshsuite/pedido/service/PedidoServiceTest.java
```

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/salesorder/service/ \
        mesh-suite-backend/src/test/java/com/meshsuite/salesorder/service/
git commit -m "refactor(salesorder): rename PedidoService to SalesOrderService with fully English method names"
```

Full-module compile still fails — `SalesOrderController` (Task 7) and `com.meshsuite.sale.*` (Task 8) still reference the old package. Expected. `SalesOrderServiceTest` itself won't run cleanly until Task 7 removes the last remaining compile blockers module-wide, but it doesn't depend on the controller directly, so it may already compile in isolation at this point.

---

## Task 7: Controller

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/salesorder/controller/SalesOrderController.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/salesorder/controller/SalesOrderControllerTest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/controller/PedidoController.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/controller/PedidoControllerTest.java`

**Interfaces:**
- Consumes: Task 4's DTOs, Task 5's `SalesOrderStatusRequest`, Task 6's `SalesOrderService`.
- Produces: `GET/POST /api/sales-orders`, `GET /api/sales-orders/counts`, `GET/PUT/DELETE /api/sales-orders/{id}`, `PATCH /api/sales-orders/{id}/status`.

- [ ] **Step 1: Create `SalesOrderController.java`**

```java
package com.meshsuite.salesorder.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.salesorder.dto.*;
import com.meshsuite.salesorder.service.SalesOrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales-orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @GetMapping
    public Page<SalesOrderSummaryResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) SalesOrderStatus status,
            @PageableDefault(size = 10, sort = "number", direction = Sort.Direction.DESC) Pageable pageable) {
        return salesOrderService.list(busca, status, pageable);
    }

    @GetMapping("/counts")
    public SalesOrderCountsResponse counts() {
        return salesOrderService.counts();
    }

    @GetMapping("/{id}")
    public SalesOrderResponse findById(@PathVariable UUID id) {
        return salesOrderService.findById(id);
    }

    @PostMapping
    public ResponseEntity<SalesOrderResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                       @Valid @RequestBody SalesOrderRequest request) {
        SalesOrderResponse response = salesOrderService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public SalesOrderResponse update(@PathVariable UUID id, @Valid @RequestBody SalesOrderRequest request) {
        return salesOrderService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public SalesOrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody SalesOrderStatusRequest request) {
        return salesOrderService.advanceStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        salesOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Create `SalesOrderControllerTest.java`**

Translated from `PedidoControllerTest.java` (deleted next step). The `Contexto` record becomes `Context` with fields `cookie, customerId, salespersonId, productId`; helper `pedidoPayload→salesOrderPayload`; test methods: `createsListsUpdatesAdvancesAndDeletesPedido→createsListsUpdatesAdvancesAndDeletesSalesOrder`, `rejectsEmptyItensWithBadRequest→rejectsEmptyItemsWithBadRequest`, `rejectsClienteWithoutClientePapelWithBadRequest→rejectsCustomerWithoutCustomerRoleWithBadRequest`, `tenantACannotAccessTenantBsPedido→tenantACannotAccessTenantBsSalesOrder`, `unauthenticatedRequestIsRejected` unchanged, `listingWithoutOrderViewPermissionIsForbidden` unchanged (already English — `Module.ORDER` is the permission module name, unrelated to this entity rename), `advancingToFaturadoViaStatusEndpointIsRejected→advancingToInvoicedViaStatusEndpointIsRejected`.

```java
package com.meshsuite.salesorder.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.repository.UserRepository;
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
class SalesOrderControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Context(String cookie, String customerId, String salespersonId, String productId) {
    }

    private Context loginAndSetUp(String codigo, String email, String companyCnpj) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName(codigo + " Ltda");
        company.setCnpj(companyCnpj);
        companyRepository.saveAndFlush(company);

        User userLogin = new User();
        userLogin.setTenantId(tenant.getId());
        userLogin.setName("Marina");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMIN);
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.CREATE));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.EDIT));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.DELETE));
        userRepository.saveAndFlush(userLogin);

        User salesperson = new User();
        salesperson.setTenantId(tenant.getId());
        salesperson.setName("Carla Vendedora");
        salesperson.setEmail("carla-" + codigo + "@" + codigo + ".com.br");
        salesperson.setPasswordHash("hash");
        salesperson.setRole(Role.SALES_REP);
        salesperson.setProfile(Profile.SALES);
        userRepository.saveAndFlush(salesperson);

        Partner customer = new Partner();
        customer.setTenantId(tenant.getId());
        customer.setPersonType(PersonType.LEGAL_ENTITY);
        customer.setDocument(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        customer.setTradeName("Mercado Silva");
        customer.getRoles().add(PartnerRole.CUSTOMER);
        partnerRepository.saveAndFlush(customer);

        Product product = new Product();
        product.setTenantId(tenant.getId());
        product.setName("Camiseta Polo");
        product.setSku("P0001-" + codigo);
        product.setSalePrice(new BigDecimal("59.90"));
        productRepository.saveAndFlush(product);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Context(token, customer.getId().toString(), salesperson.getId().toString(), product.getId().toString());
    }

    private String salesOrderPayload(Context ctx) {
        return """
                {
                  "customerId": "%s",
                  "salespersonId": "%s",
                  "discount": 0,
                  "items": [
                    { "productId": "%s", "quantity": 2, "unitPrice": 59.90 }
                  ]
                }
                """.formatted(ctx.customerId(), ctx.salespersonId(), ctx.productId());
    }

    @Test
    void createsListsUpdatesAdvancesAndDeletesSalesOrder() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/sales-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salesOrderPayload(ctx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(119.80))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/sales-orders").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(1));

        mockMvc.perform(put("/api/sales-orders/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "salespersonId": "%s",
                                  "discount": 10.00,
                                  "items": [
                                    { "productId": "%s", "quantity": 2, "unitPrice": 59.90 }
                                  ]
                                }
                                """.formatted(ctx.customerId(), ctx.salespersonId(), ctx.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(109.80));

        mockMvc.perform(patch("/api/sales-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PREPARATION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PREPARATION"));

        mockMvc.perform(patch("/api/sales-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/sales-orders/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/sales-orders/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyItemsWithBadRequest() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/sales-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "salespersonId": "%s",
                                  "discount": 0,
                                  "items": []
                                }
                                """.formatted(ctx.customerId(), ctx.salespersonId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCustomerWithoutCustomerRoleWithBadRequest() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/sales-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "salespersonId": "%s",
                                  "discount": 0,
                                  "items": [ { "productId": "%s", "quantity": 1, "unitPrice": 10.00 } ]
                                }
                                """.formatted(ctx.salespersonId(), ctx.salespersonId(), ctx.productId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsSalesOrder() throws Exception {
        Context ctxA = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/sales-orders").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salesOrderPayload(ctxA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        Context ctxB = loginAndSetUp("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/sales-orders/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/sales-orders"))
                .andExpect(status().isUnauthorized());
    }

    private String loginWithoutOrderPermission(String codigo, String email, String companyCnpj) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName(codigo + " Ltda");
        company.setCnpj(companyCnpj);
        companyRepository.saveAndFlush(company);

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
    void listingWithoutOrderViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutOrderPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/sales-orders").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void advancingToInvoicedViaStatusEndpointIsRejected() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/sales-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salesOrderPayload(ctx)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/sales-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PREPARATION\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/sales-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVOICED\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 3: Delete the 2 old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/pedido/controller/PedidoController.java \
       mesh-suite-backend/src/test/java/com/meshsuite/pedido/controller/PedidoControllerTest.java
```

- [ ] **Step 4: Delete the whole (now-empty) `com.meshsuite.pedido` tree**

```bash
find mesh-suite-backend/src/main/java/com/meshsuite/pedido mesh-suite-backend/src/test/java/com/meshsuite/pedido -type f
```

Expected: no output (every file under both trees was deleted across Tasks 2-7). If any file remains, investigate before proceeding — do not `rm -rf` the directory blindly.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/salesorder/controller/ \
        mesh-suite-backend/src/test/java/com/meshsuite/salesorder/controller/
git commit -m "refactor(salesorder): rename PedidoController to SalesOrderController, route pedidos->sales-orders"
```

Do not run the full backend test suite yet — `com.meshsuite.sale.*` and its 3 test classes still reference the deleted `com.meshsuite.pedido` package (Task 8). `mvn compile` on just this module's own new files is optional at this point but the whole-project compile will still fail until Task 8 lands.

---

## Task 8: Sale module bridge

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/sale/service/SaleService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/service/SaleServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/repository/SaleRepositoryTest.java`

**Interfaces:**
- Consumes: Task 2's `SalesOrder`/`SalesOrderItem`/`SalesOrderStatus`, Task 3's `SalesOrderRepository`, Task 5's `SalesOrderNotFoundException`.

This task makes the WHOLE backend compile and the full test suite green. `Sale`'s own identifiers (`Sale`, `SaleService`, `SaleItem`, field names like `customer`/`salesperson`/`discount`) are NOT renamed — only the `Pedido`-referencing code inside these 4 files changes.

- [ ] **Step 1: Update `SaleService.java`**

In `mesh-suite-backend/src/main/java/com/meshsuite/sale/service/SaleService.java`, change the imports (lines 8-12) from:
```java
import com.meshsuite.pedido.domain.ItemPedido;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.pedido.exception.PedidoNaoEncontradoException;
import com.meshsuite.pedido.repository.PedidoRepository;
```
to:
```java
import com.meshsuite.salesorder.domain.SalesOrderItem;
import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.salesorder.exception.SalesOrderNotFoundException;
import com.meshsuite.salesorder.repository.SalesOrderRepository;
```

Change the field declaration and constructor (lines 38-49) from:
```java
    private final SaleRepository saleRepository;
    private final PedidoRepository pedidoRepository;
    private final FiscalCalculationService fiscalCalculationService;
    private final EntityManager entityManager;

    public SaleService(SaleRepository saleRepository, PedidoRepository pedidoRepository,
                        FiscalCalculationService fiscalCalculationService, EntityManager entityManager) {
        this.saleRepository = saleRepository;
        this.pedidoRepository = pedidoRepository;
        this.fiscalCalculationService = fiscalCalculationService;
        this.entityManager = entityManager;
    }
```
to:
```java
    private final SaleRepository saleRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final FiscalCalculationService fiscalCalculationService;
    private final EntityManager entityManager;

    public SaleService(SaleRepository saleRepository, SalesOrderRepository salesOrderRepository,
                        FiscalCalculationService fiscalCalculationService, EntityManager entityManager) {
        this.saleRepository = saleRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.fiscalCalculationService = fiscalCalculationService;
        this.entityManager = entityManager;
    }
```

Change the `issue` method body (lines 82-141) from:
```java
    @Transactional
    @RequiresPermission(module = Module.SALE, action = Action.CREATE)
    public SaleResponse issue(UUID orderId) {
        Pedido order = pedidoRepository.findById(orderId).orElseThrow(PedidoNaoEncontradoException::new);
        if (order.getStatus() != StatusPedido.EM_PREPARO) {
            throw new SaleValidationException(
                    "Só é possível faturar um pedido em preparo. Status atual: " + order.getStatus());
        }

        Sale sale = new Sale();
        sale.setTenantId(order.getTenantId());
        sale.setNumber(nextNumber(order.getTenantId()));
        sale.setOrder(order);
        sale.setCustomer(order.getCliente());
        sale.setSalesperson(order.getVendedor());
        sale.setDiscount(order.getDesconto());
        sale.setSubtotal(order.getSubtotal());
        sale.setTotal(order.getTotal());

        BigDecimal totalIcms = BigDecimal.ZERO;
        BigDecimal totalIpi = BigDecimal.ZERO;
        BigDecimal totalPis = BigDecimal.ZERO;
        BigDecimal totalCofins = BigDecimal.ZERO;

        for (ItemPedido orderItem : order.getItens()) {
            Product product = orderItem.getProduto();
            if (product.getFiscalRegistration() == null) {
                throw new SaleValidationException(
                        "O produto " + product.getName() + " não possui cadastro fiscal aplicado");
            }
            FiscalCalculationResult calculation = fiscalCalculationService.calculate(
                    product.getFiscalRegistration(), orderItem.getQuantidade(), orderItem.getValorUnitario());

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setProduct(product);
            saleItem.setQuantity(orderItem.getQuantidade());
            saleItem.setUnitPrice(orderItem.getValorUnitario());
            saleItem.setTotalAmount(orderItem.getValorTotal());
            saleItem.setIcmsAmount(calculation.icmsValue());
            saleItem.setIpiAmount(calculation.ipiValue());
            saleItem.setPisAmount(calculation.pisValue());
            saleItem.setCofinsAmount(calculation.cofinsValue());
            sale.getItems().add(saleItem);

            totalIcms = totalIcms.add(calculation.icmsValue());
            totalIpi = totalIpi.add(calculation.ipiValue());
            totalPis = totalPis.add(calculation.pisValue());
            totalCofins = totalCofins.add(calculation.cofinsValue());
        }

        sale.setIcmsAmount(totalIcms);
        sale.setIpiAmount(totalIpi);
        sale.setPisAmount(totalPis);
        sale.setCofinsAmount(totalCofins);

        Sale saved = saleRepository.saveAndFlush(sale);

        order.setStatus(StatusPedido.FATURADO);
        pedidoRepository.saveAndFlush(order);

        return toResponse(saved);
    }
```
to:
```java
    @Transactional
    @RequiresPermission(module = Module.SALE, action = Action.CREATE)
    public SaleResponse issue(UUID orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId).orElseThrow(SalesOrderNotFoundException::new);
        if (order.getStatus() != SalesOrderStatus.IN_PREPARATION) {
            throw new SaleValidationException(
                    "Só é possível faturar um pedido em preparo. Status atual: " + order.getStatus());
        }

        Sale sale = new Sale();
        sale.setTenantId(order.getTenantId());
        sale.setNumber(nextNumber(order.getTenantId()));
        sale.setOrder(order);
        sale.setCustomer(order.getCustomer());
        sale.setSalesperson(order.getSalesperson());
        sale.setDiscount(order.getDiscount());
        sale.setSubtotal(order.getSubtotal());
        sale.setTotal(order.getTotal());

        BigDecimal totalIcms = BigDecimal.ZERO;
        BigDecimal totalIpi = BigDecimal.ZERO;
        BigDecimal totalPis = BigDecimal.ZERO;
        BigDecimal totalCofins = BigDecimal.ZERO;

        for (SalesOrderItem orderItem : order.getItems()) {
            Product product = orderItem.getProduct();
            if (product.getFiscalRegistration() == null) {
                throw new SaleValidationException(
                        "O produto " + product.getName() + " não possui cadastro fiscal aplicado");
            }
            FiscalCalculationResult calculation = fiscalCalculationService.calculate(
                    product.getFiscalRegistration(), orderItem.getQuantity(), orderItem.getUnitPrice());

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setProduct(product);
            saleItem.setQuantity(orderItem.getQuantity());
            saleItem.setUnitPrice(orderItem.getUnitPrice());
            saleItem.setTotalAmount(orderItem.getTotalAmount());
            saleItem.setIcmsAmount(calculation.icmsValue());
            saleItem.setIpiAmount(calculation.ipiValue());
            saleItem.setPisAmount(calculation.pisValue());
            saleItem.setCofinsAmount(calculation.cofinsValue());
            sale.getItems().add(saleItem);

            totalIcms = totalIcms.add(calculation.icmsValue());
            totalIpi = totalIpi.add(calculation.ipiValue());
            totalPis = totalPis.add(calculation.pisValue());
            totalCofins = totalCofins.add(calculation.cofinsValue());
        }

        sale.setIcmsAmount(totalIcms);
        sale.setIpiAmount(totalIpi);
        sale.setPisAmount(totalPis);
        sale.setCofinsAmount(totalCofins);

        Sale saved = saleRepository.saveAndFlush(sale);

        order.setStatus(SalesOrderStatus.INVOICED);
        salesOrderRepository.saveAndFlush(order);

        return toResponse(saved);
    }
```

Finally, in `toResponse`, change `s.getOrder().getNumero()` (near the end of the file) to `s.getOrder().getNumber()`.

- [ ] **Step 2: Update `SaleRepositoryTest.java`**

In `mesh-suite-backend/src/test/java/com/meshsuite/sale/repository/SaleRepositoryTest.java`, change the imports (lines 9-10) from:
```java
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.repository.PedidoRepository;
```
to:
```java
import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.repository.SalesOrderRepository;
```

Change line 34 from `@Autowired PedidoRepository pedidoRepository;` to `@Autowired SalesOrderRepository salesOrderRepository;`.

Change the `createOrder`/`newSale` helpers (lines 79-96) from:
```java
    private Pedido createOrder(UUID tenantId, Partner customer, User salesperson, int number) {
        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setNumero(number);
        pedido.setCliente(customer);
        pedido.setVendedor(salesperson);
        return pedidoRepository.saveAndFlush(pedido);
    }

    private Sale newSale(UUID tenantId, Pedido order, Partner customer, User salesperson, int number) {
```
to:
```java
    private SalesOrder createOrder(UUID tenantId, Partner customer, User salesperson, int number) {
        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setNumber(number);
        order.setCustomer(customer);
        order.setSalesperson(salesperson);
        return salesOrderRepository.saveAndFlush(order);
    }

    private Sale newSale(UUID tenantId, SalesOrder order, Partner customer, User salesperson, int number) {
```

Then replace every remaining bare `Pedido order = createOrder(` with `SalesOrder order = createOrder(` — this occurs at (former) lines 106, 137, 153, 173, i.e. in `savesSaleWithItemsViaCascade`, `orderIdMustBeUniqueAcrossSales`, `rlsHidesRowsWhenTenantContextUnset`, and `saleItemRlsHidesRowsWhenTenantContextUnset`.

- [ ] **Step 3: Update `SaleServiceTest.java`**

In `mesh-suite-backend/src/test/java/com/meshsuite/sale/service/SaleServiceTest.java`, change the imports (lines 15-21) from:
```java
import com.meshsuite.pedido.domain.ItemPedido;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.pedido.dto.ItemPedidoDto;
import com.meshsuite.pedido.dto.PedidoRequest;
import com.meshsuite.pedido.repository.PedidoRepository;
import com.meshsuite.pedido.service.PedidoService;
```
to:
```java
import com.meshsuite.salesorder.domain.SalesOrderItem;
import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.salesorder.dto.SalesOrderItemRequest;
import com.meshsuite.salesorder.dto.SalesOrderRequest;
import com.meshsuite.salesorder.repository.SalesOrderRepository;
import com.meshsuite.salesorder.service.SalesOrderService;
```

(`ItemPedido` isn't actually used directly in this test file's body — confirm with a search after this edit that no `ItemPedido` reference remains; if none does, this import simply isn't needed and should be dropped rather than kept as `SalesOrderItem` unused.)

Change lines 56-57 from:
```java
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PedidoService pedidoService;
```
to:
```java
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderService salesOrderService;
```

Change `createOrderInPreparation` (lines 152-159) from:
```java
    private UUID createOrderInPreparation(UUID tenantId, UUID customerId, UUID salespersonId, UUID productId,
                                           BigDecimal quantity, BigDecimal unitPrice) {
        var items = List.of(new ItemPedidoDto(productId, quantity, unitPrice));
        var request = new PedidoRequest(customerId, salespersonId, null, null, BigDecimal.ZERO, items);
        var order = pedidoService.criar(tenantId, request);
        pedidoService.avancarStatus(order.id(), StatusPedido.EM_PREPARO);
        return order.id();
    }
```
to:
```java
    private UUID createOrderInPreparation(UUID tenantId, UUID customerId, UUID salespersonId, UUID productId,
                                           BigDecimal quantity, BigDecimal unitPrice) {
        var items = List.of(new SalesOrderItemRequest(productId, quantity, unitPrice));
        var request = new SalesOrderRequest(customerId, salespersonId, null, null, BigDecimal.ZERO, items);
        var order = salesOrderService.create(tenantId, request);
        salesOrderService.advanceStatus(order.id(), SalesOrderStatus.IN_PREPARATION);
        return order.id();
    }
```

In `issuesOrderInPreparationCopyingItemsAndCalculatingTaxes` (around line 179), change:
```java
        Pedido updatedOrder = pedidoRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(StatusPedido.FATURADO);
```
to:
```java
        SalesOrder updatedOrder = salesOrderRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(SalesOrderStatus.INVOICED);
```

In `rejectsIssuingAnOrderThatIsNotInPreparation` (around line 206), change:
```java
        var items = List.of(new ItemPedidoDto(productId, BigDecimal.ONE, new BigDecimal("50.00")));
        var order = pedidoService.criar(tenantId,
                new PedidoRequest(customerId, salespersonId, null, null, BigDecimal.ZERO, items));
```
to:
```java
        var items = List.of(new SalesOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("50.00")));
        var order = salesOrderService.create(tenantId,
                new SalesOrderRequest(customerId, salespersonId, null, null, BigDecimal.ZERO, items));
```

- [ ] **Step 4: Update `SaleControllerTest.java`**

In `mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java`, change all 4 occurrences of `/api/pedidos` (lines 130, 144, 174, 184) to `/api/sales-orders`.

Change the JSON payload bodies at (former) lines 132-139 and 186-193, from:
```java
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": [ { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 } ]
                                }
                                """.formatted(ctx.customerId(), ctx.salespersonId(), ctx.productId())))
```
to:
```java
                        .content("""
                                {
                                  "customerId": "%s",
                                  "salespersonId": "%s",
                                  "discount": 0,
                                  "items": [ { "productId": "%s", "quantity": 2, "unitPrice": 59.90 } ]
                                }
                                """.formatted(ctx.customerId(), ctx.salespersonId(), ctx.productId())))
```

Change the status-advance body at (former) line 146 from `.content("{\"status\":\"EM_PREPARO\"}"))` to `.content("{\"status\":\"IN_PREPARATION\"}"))`.

Change the assertion at (former) line 176 from `.andExpect(jsonPath("$.status").value("FATURADO"));` to `.andExpect(jsonPath("$.status").value("INVOICED"));`.

Rename the test method `issuingAnOrderStillInDigitadoIsBadRequest` (former line 180) to `issuingAnOrderStillInDraftIsBadRequest` — the method body itself doesn't send a status string (it relies on the default `DRAFT` state), so no other change is needed inside it beyond the method name and the shared JSON-payload fix already applied above.

- [ ] **Step 5: Run the whole backend test suite for the first time in this plan**

Run: `cd mesh-suite-backend && mvn -q clean test`

This is the first point in the plan where the whole module compiles (`com.meshsuite.pedido` is gone, `com.meshsuite.salesorder` is complete, and every consumer — including `com.meshsuite.sale.*` — has been updated). Expected: 0 failures, 15 errors exactly matching the documented pre-existing flake (3 `CompanyRepositoryTest` + 3 `AccountsPayableControllerTest` + 1 `AccountsPayableRepositoryTest` + 8 `AccountsPayableServiceTest`) — confirm via `target/surefire-reports/*.txt` class names, don't just trust the aggregate count. If you see compile errors instead, they are almost certainly a missed `Pedido`-referencing spot in one of these 4 files or in `SalesOrderService`/`SalesOrderController` from earlier tasks — grep the compiler error's file:line, not the whole codebase, to find it.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/sale/service/SaleService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/sale/
git commit -m "refactor(salesorder): bridge Sale module to SalesOrder types, full backend suite green"
```

---

## Task 9: Frontend — `api/pedidos.ts` → `api/salesOrders.ts`

**Files:**
- Create: `mesh-suite-frontend/src/api/salesOrders.ts`
- Delete: `mesh-suite-frontend/src/api/pedidos.ts`

**Interfaces:**
- Produces: `SalesOrderStatus`, `SalesOrderItemRequest`, `SalesOrderItemResponse`, `SalesOrderRequest`, `SalesOrderResponse`, `SalesOrderSummary`, `Page<T>`, `ListSalesOrdersParams`, `SalesOrderCounts`, `listSalesOrders`, `getSalesOrder`, `createSalesOrder`, `updateSalesOrder`, `advanceSalesOrderStatus`, `deleteSalesOrder`, `getSalesOrderCounts` — Tasks 10, 11, and 12 import these exact names.

No test file exists for `api/pedidos.ts` today (confirmed — there is no `pedidos.spec.ts`), so this task creates none either; the renamed module is exercised indirectly by Tasks 10/11's view specs.

- [ ] **Step 1: Create `salesOrders.ts`**

```typescript
import { apiClient } from './client'

export type SalesOrderStatus = 'DRAFT' | 'IN_PREPARATION' | 'INVOICED'

export interface SalesOrderItemRequest {
  productId: string
  quantity: number
  unitPrice: number
}

export interface SalesOrderItemResponse extends SalesOrderItemRequest {
  productName: string
  totalAmount: number
}

export interface SalesOrderRequest {
  customerId: string
  salespersonId: string
  orderDate: string
  deliveryDate: string | null
  discount: number
  items: SalesOrderItemRequest[]
}

export interface SalesOrderResponse {
  id: string
  number: number
  customerId: string
  customerName: string
  salespersonId: string
  salespersonName: string
  orderDate: string
  deliveryDate: string | null
  status: SalesOrderStatus
  discount: number
  subtotal: number
  total: number
  items: SalesOrderItemResponse[]
}

export interface SalesOrderSummary {
  id: string
  number: number
  customerName: string
  salespersonName: string
  orderDate: string
  total: number
  status: SalesOrderStatus
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListSalesOrdersParams {
  busca?: string
  status?: SalesOrderStatus
  page?: number
  size?: number
  sort?: string
}

export interface SalesOrderCounts {
  total: number
  draft: number
  inPreparation: number
  invoiced: number
}

export async function listSalesOrders(params: ListSalesOrdersParams): Promise<Page<SalesOrderSummary>> {
  const { data } = await apiClient.get<Page<SalesOrderSummary>>('/sales-orders', { params })
  return data
}

export async function getSalesOrder(id: string): Promise<SalesOrderResponse> {
  const { data } = await apiClient.get<SalesOrderResponse>(`/sales-orders/${id}`)
  return data
}

export async function createSalesOrder(payload: SalesOrderRequest): Promise<SalesOrderResponse> {
  const { data } = await apiClient.post<SalesOrderResponse>('/sales-orders', payload)
  return data
}

export async function updateSalesOrder(id: string, payload: SalesOrderRequest): Promise<SalesOrderResponse> {
  const { data } = await apiClient.put<SalesOrderResponse>(`/sales-orders/${id}`, payload)
  return data
}

export async function advanceSalesOrderStatus(id: string, status: SalesOrderStatus): Promise<void> {
  await apiClient.patch(`/sales-orders/${id}/status`, { status })
}

export async function deleteSalesOrder(id: string): Promise<void> {
  await apiClient.delete(`/sales-orders/${id}`)
}

export async function getSalesOrderCounts(): Promise<SalesOrderCounts> {
  const { data } = await apiClient.get<SalesOrderCounts>('/sales-orders/counts')
  return data
}
```

- [ ] **Step 2: Delete the old file**

```bash
git rm mesh-suite-frontend/src/api/pedidos.ts
```

- [ ] **Step 3: Commit**

```bash
git add mesh-suite-frontend/src/api/salesOrders.ts
git commit -m "refactor(salesorder): rename frontend api/pedidos.ts to salesOrders.ts"
```

---

## Task 10: Frontend — `SalesOrderFormView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/SalesOrderFormView.vue`
- Create: `mesh-suite-frontend/src/views/__tests__/SalesOrderFormView.spec.ts`
- Delete: `mesh-suite-frontend/src/views/PedidoFormView.vue`
- Delete: `mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts`

**Interfaces:**
- Consumes: Task 9's `salesOrders.ts` exports.

This is `SalesOrder`'s own view (not a bridge) — every internal identifier and `data-test` attribute is translated to English. All rendered Portuguese text (labels, placeholders, button text, error messages) stays byte-identical.

- [ ] **Step 1: Create `SalesOrderFormView.vue`**

```vue
<template>
  <AppShell :title="editMode ? 'Editar Pedido' : 'Novo Pedido'">
    <form class="form" @submit.prevent="save">
      <section class="card">
        <h2>Dados do Pedido</h2>
        <div class="grid grid-2">
          <div class="busca-wrapper">
            <label class="field-label">Cliente *</label>
            <input
              v-model="customerSearch"
              data-test="customer-search"
              placeholder="Buscar cliente..."
              autocomplete="off"
              @input="searchCustomers"
            />
            <p v-if="errors.customerId" class="field-error">{{ errors.customerId }}</p>
            <ul v-if="customerResults.length" class="dropdown-busca" data-test="customer-results">
              <li v-for="c in customerResults" :key="c.id" @click="selectCustomer(c)">{{ c.tradeName }}</li>
            </ul>
          </div>
          <div>
            <label class="field-label">Vendedor *</label>
            <select v-model="form.salespersonId" data-test="salesperson">
              <option value="">Selecione...</option>
              <option v-for="r in salesReps" :key="r.id" :value="r.id">{{ r.name }}</option>
            </select>
            <p v-if="errors.salespersonId" class="field-error">{{ errors.salespersonId }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Data do Pedido</label>
            <input v-model="form.orderDate" type="date" data-test="order-date" />
          </div>
          <div>
            <label class="field-label">Previsão de Entrega</label>
            <input v-model="form.deliveryDate" type="date" data-test="delivery-date" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Itens</h2>
        <div class="item-adicionar">
          <div class="busca-wrapper item-produto-busca">
            <input
              v-model="productSearch"
              placeholder="Buscar produto por nome ou SKU..."
              data-test="product-search"
              autocomplete="off"
              @input="searchProducts"
            />
            <ul v-if="productResults.length" class="dropdown-busca" data-test="product-results">
              <li v-for="p in productResults" :key="p.id" @click="selectProduct(p)">{{ p.name }} ({{ p.sku }})</li>
            </ul>
          </div>
          <input
            v-model.number="itemForm.quantity"
            type="number"
            step="0.001"
            min="0.001"
            placeholder="Qtd."
            data-test="item-quantity"
          />
          <input
            v-model.number="itemForm.unitPrice"
            type="number"
            step="0.01"
            min="0"
            placeholder="Valor unit."
            data-test="item-unit-price"
          />
          <button type="button" class="btn-secondary" data-test="item-add" @click="addItem">+ Adicionar</button>
        </div>
        <p v-if="errors.items" class="field-error">{{ errors.items }}</p>

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
              <td>{{ formatPrice(item.unitPrice) }}</td>
              <td>{{ formatPrice(item.quantity * item.unitPrice) }}</td>
              <td><button type="button" class="btn-remover" data-test="item-remove" @click="removeItem(index)">✕</button></td>
            </tr>
          </tbody>
        </table>

        <div class="totais">
          <div><span>Subtotal</span><span>{{ formatPrice(subtotal) }}</span></div>
          <div>
            <span>Desconto</span>
            <input v-model.number="form.discount" type="number" step="0.01" min="0" data-test="discount" />
          </div>
          <div class="total-final"><span>Total</span><span>{{ formatPrice(total) }}</span></div>
        </div>
      </section>

      <p v-if="generalError" class="error-geral">{{ generalError }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancel">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="saving">Salvar Pedido</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { getSalesOrder, createSalesOrder, updateSalesOrder, type SalesOrderRequest, type SalesOrderItemRequest } from '@/api/salesOrders'
import { listPartners, type PartnerListItem } from '@/api/partners'
import { listSalesReps, type SalesRep } from '@/api/users'
import { listProducts, type ProductListItem } from '@/api/products'

const route = useRoute()
const router = useRouter()

const editMode = computed(() => typeof route.params.id === 'string')

interface ItemForm extends SalesOrderItemRequest {
  productName: string
}

interface FormState {
  customerId: string
  salespersonId: string
  orderDate: string
  deliveryDate: string
  discount: number
  items: ItemForm[]
}

function newFormState(): FormState {
  return {
    customerId: '',
    salespersonId: '',
    orderDate: new Date().toISOString().slice(0, 10),
    deliveryDate: '',
    discount: 0,
    items: [],
  }
}

const form = reactive<FormState>(newFormState())
const errors = reactive<{ customerId?: string; salespersonId?: string; items?: string }>({})
const generalError = ref('')
const saving = ref(false)

const customerSearch = ref('')
const customerResults = ref<PartnerListItem[]>([])
const salesReps = ref<SalesRep[]>([])

const productSearch = ref('')
const productResults = ref<ProductListItem[]>([])
const itemForm = reactive({ productId: '', productName: '', quantity: 1, unitPrice: 0 })

const subtotal = computed(() => form.items.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0))
const total = computed(() => subtotal.value - (Number(form.discount) || 0))

function formatPrice(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

async function searchCustomers() {
  if (!customerSearch.value.trim()) {
    customerResults.value = []
    return
  }
  try {
    const page = await listPartners({ busca: customerSearch.value, papel: 'CUSTOMER', size: 5 })
    customerResults.value = page.content
  } catch {
    customerResults.value = []
  }
}

function selectCustomer(customer: PartnerListItem) {
  form.customerId = customer.id
  customerSearch.value = customer.tradeName
  customerResults.value = []
}

async function searchProducts() {
  if (!productSearch.value.trim()) {
    productResults.value = []
    return
  }
  try {
    const page = await listProducts({ busca: productSearch.value, size: 5 })
    productResults.value = page.content
  } catch {
    productResults.value = []
  }
}

function selectProduct(product: ProductListItem) {
  itemForm.productId = product.id
  itemForm.productName = product.name
  itemForm.unitPrice = product.salePrice
  productSearch.value = product.name
  productResults.value = []
}

function addItem() {
  const quantity = Number(itemForm.quantity) || 0
  if (!itemForm.productId || quantity <= 0) {
    return
  }
  form.items.push({
    productId: itemForm.productId,
    productName: itemForm.productName,
    quantity,
    // Normalized here for the same reason toPayload() normalizes on submit:
    // v-model.number on a blank input yields '' (not 0), and that would flow
    // straight into form.items and later into the request payload untouched.
    unitPrice: Number(itemForm.unitPrice) || 0,
  })
  itemForm.productId = ''
  itemForm.productName = ''
  itemForm.quantity = 1
  itemForm.unitPrice = 0
  productSearch.value = ''
}

function removeItem(index: number) {
  form.items.splice(index, 1)
}

onMounted(async () => {
  try {
    salesReps.value = await listSalesReps()
  } catch {
    generalError.value = 'Não foi possível carregar a lista de vendedores.'
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const order = await getSalesOrder(id)
      form.customerId = order.customerId
      customerSearch.value = order.customerName
      form.salespersonId = order.salespersonId
      form.orderDate = order.orderDate
      form.deliveryDate = order.deliveryDate ?? ''
      form.discount = order.discount
      form.items = order.items.map((item) => ({
        productId: item.productId,
        productName: item.productName,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      }))
    } catch {
      generalError.value = 'Não foi possível carregar os dados do pedido.'
    }
  }
})

function validate(): boolean {
  errors.customerId = form.customerId ? undefined : 'Selecione um cliente'
  errors.salespersonId = form.salespersonId ? undefined : 'Selecione um vendedor'
  errors.items = form.items.length > 0 ? undefined : 'Adicione ao menos um item'
  return !errors.customerId && !errors.salespersonId && !errors.items
}

function toPayload(): SalesOrderRequest {
  return {
    customerId: form.customerId,
    salespersonId: form.salespersonId,
    orderDate: form.orderDate,
    deliveryDate: form.deliveryDate || null,
    discount: Number(form.discount) || 0,
    items: form.items.map(({ productId, quantity, unitPrice }) => ({ productId, quantity, unitPrice })),
  }
}

async function save() {
  generalError.value = ''
  if (!validate()) {
    return
  }
  saving.value = true
  try {
    const id = route.params.id
    const payload = toPayload()
    if (typeof id === 'string') {
      await updateSalesOrder(id, payload)
    } else {
      await createSalesOrder(payload)
    }
    router.push({ name: 'pedidos' })
  } catch (err: any) {
    if (err?.response?.status === 403) {
      generalError.value = 'Você não tem permissão para executar esta ação.'
    } else if (err?.response?.status === 400) {
      generalError.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      generalError.value = 'Não foi possível salvar. Tente novamente em instantes.'
    }
  } finally {
    saving.value = false
  }
}

function cancel() {
  router.push({ name: 'pedidos' })
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
  font-weight: 600;
  color: var(--pm-text-dark);
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

Note `router.push({ name: 'pedidos' })` (in both `save()` and `cancel()`) is unchanged — the route name stays Portuguese per the Global Constraints. The class names `.busca-wrapper`, `.dropdown-busca`, `.item-adicionar`, `.item-produto-busca`, `.tabela-itens`, `.btn-remover`, `.totais`, `.error-geral` are CSS classes with no test/JSON coupling — left as-is (cosmetic, matches the "don't touch what isn't load-bearing" principle; no prior sub-project retranslated scoped CSS class names either).

- [ ] **Step 2: Create `SalesOrderFormView.spec.ts`**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import SalesOrderFormView from '@/views/SalesOrderFormView.vue'
import * as salesOrdersApi from '@/api/salesOrders'
import * as partnersApi from '@/api/partners'
import * as usersApi from '@/api/users'
import * as productsApi from '@/api/products'

vi.mock('@/api/salesOrders')
vi.mock('@/api/partners')
vi.mock('@/api/users')
vi.mock('@/api/products')

function mountWithRouter(path = '/pedidos/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/pedidos', name: 'pedidos', component: { template: '<div />' } },
      { path: '/pedidos/novo', name: 'pedidos-novo', component: SalesOrderFormView },
      { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: SalesOrderFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(SalesOrderFormView, { global: { plugins: [router] } }),
  }))
}

const customerBase = {
  id: 'c1', tradeName: 'Mercado Silva', legalName: 'Mercado Silva Ltda',
  document: '11222333000144', personType: 'LEGAL_ENTITY' as const,
  city: 'São Paulo', state: 'SP', whatsapp: '', status: 'ACTIVE' as const,
}

const salesRepBase = { id: 'v1', name: 'Carla Vendedora' }

const productBase = {
  id: 'p1', name: 'Camiseta Polo', sku: 'P0001', brand: 'Marca Alpha',
  salePrice: 59.9, stockQuantity: 10, status: 'ACTIVE' as const,
}

describe('SalesOrderFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usersApi.listSalesReps).mockResolvedValue([salesRepBase])
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [customerBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(productsApi.listProducts).mockResolvedValue({
      content: [productBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
  })

  it('shows required-field errors when cliente/vendedor/itens are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione um cliente')
    expect(wrapper.text()).toContain('Selecione um vendedor')
    expect(wrapper.text()).toContain('Adicione ao menos um item')
    expect(salesOrdersApi.createSalesOrder).not.toHaveBeenCalled()
  })

  it('loads the salesReps list for the vendedor select', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(usersApi.listSalesReps).toHaveBeenCalled()
    expect(wrapper.find('[data-test="salesperson"]').text()).toContain('Carla Vendedora')
  })

  it('searches and selects a cliente via the busca dropdown', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="customer-search"]').setValue('silva')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenCalledWith(
      expect.objectContaining({ busca: 'silva', papel: 'CUSTOMER' }),
    )
    await wrapper.find('[data-test="customer-results"] li').trigger('click')

    expect((wrapper.find('[data-test="customer-search"]').element as HTMLInputElement).value).toBe('Mercado Silva')
  })

  it('searches for a produto, adds it as an item and computes totals live', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="product-search"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="product-results"] li').trigger('click')
    await wrapper.find('[data-test="item-quantity"]').setValue('2')
    await wrapper.find('[data-test="item-add"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).toContain('R$ 119,80')

    await wrapper.find('[data-test="item-remove"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Camiseta Polo')
  })

  it('normalizes a cleared unitPrice to the number 0 (not empty-string) when added immediately', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="customer-search"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="customer-results"] li').trigger('click')
    await wrapper.find('[data-test="salesperson"]').setValue('v1')

    await wrapper.find('[data-test="product-search"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="product-results"] li').trigger('click')
    // Simulate the auto-filled unit price being manually cleared, then
    // "Adicionar" clicked immediately -- v-model.number drives the underlying
    // value to '' (empty string) when cleared, and addItem() must
    // normalize that '' to 0 before it lands in form.items/payload. This must
    // NOT be refilled before clicking Adicionar, or the empty-string state
    // never reaches addItem() and the normalization guard goes untested.
    await wrapper.find('[data-test="item-unit-price"]').setValue('')
    await wrapper.find('[data-test="item-quantity"]').setValue('1')
    await wrapper.find('[data-test="item-add"]').trigger('click')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(salesOrdersApi.createSalesOrder).mock.calls[0][0]
    expect(payload.items[0].unitPrice).toBe(0)
    expect(typeof payload.items[0].unitPrice).toBe('number')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="customer-search"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="customer-results"] li').trigger('click')
    await wrapper.find('[data-test="salesperson"]').setValue('v1')

    await wrapper.find('[data-test="product-search"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="product-results"] li').trigger('click')
    await wrapper.find('[data-test="item-quantity"]').setValue('1')
    await wrapper.find('[data-test="item-add"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(salesOrdersApi.createSalesOrder).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('pedidos')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="customer-search"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="customer-results"] li').trigger('click')
    await wrapper.find('[data-test="salesperson"]').setValue('v1')

    await wrapper.find('[data-test="product-search"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="product-results"] li').trigger('click')
    await wrapper.find('[data-test="item-quantity"]').setValue('1')
    await wrapper.find('[data-test="item-add"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing pedido data in edit mode', async () => {
    vi.mocked(salesOrdersApi.getSalesOrder).mockResolvedValue({
      id: 'ped-1', number: 3, customerId: 'c1', customerName: 'Mercado Silva', salespersonId: 'v1',
      salespersonName: 'Carla Vendedora', orderDate: '2026-07-31', deliveryDate: null, status: 'DRAFT',
      discount: 0, subtotal: 119.8, total: 119.8,
      items: [{ productId: 'p1', productName: 'Camiseta Polo', quantity: 2, unitPrice: 59.9, totalAmount: 119.8 }],
    } as any)

    const { wrapper } = await mountWithRouter('/pedidos/ped-1/editar')
    await flushPromises()

    expect(salesOrdersApi.getSalesOrder).toHaveBeenCalledWith('ped-1')
    expect((wrapper.find('[data-test="customer-search"]').element as HTMLInputElement).value).toBe('Mercado Silva')
    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('shows an error message when loading pedido data fails in edit mode', async () => {
    vi.mocked(salesOrdersApi.getSalesOrder).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/pedidos/ped-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do pedido.')
  })
})
```

- [ ] **Step 3: Delete the 2 old files**

```bash
git rm mesh-suite-frontend/src/views/PedidoFormView.vue \
       mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts
```

- [ ] **Step 4: Run the new spec in isolation**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/SalesOrderFormView.spec.ts`

Expected: FAIL at this point — `router/index.ts` still imports the now-deleted `PedidoFormView.vue`, so the whole frontend fails to build/resolve until Task 12's router bridge lands. This is expected; do not attempt to fix `router/index.ts` from within this task — that's Task 12's job. If you want a green signal before Task 12, you may temporarily verify this file compiles and its own logic is sound by mounting it directly without going through `router/index.ts` (which is exactly what this spec file does — it builds its own local `router` instance, not the app's), so this particular test should actually still be able to run standalone since it doesn't import `router/index.ts`. Confirm 10/10 tests pass.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/SalesOrderFormView.vue \
        mesh-suite-frontend/src/views/__tests__/SalesOrderFormView.spec.ts
git commit -m "refactor(salesorder): rename PedidoFormView to SalesOrderFormView with English identifiers"
```

---

## Task 11: Frontend — `SalesOrdersListView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/SalesOrdersListView.vue`
- Create: `mesh-suite-frontend/src/views/__tests__/SalesOrdersListView.spec.ts`
- Delete: `mesh-suite-frontend/src/views/PedidosListView.vue`
- Delete: `mesh-suite-frontend/src/views/__tests__/PedidosListView.spec.ts`

**Interfaces:**
- Consumes: Task 9's `salesOrders.ts` exports.

- [ ] **Step 1: Create `SalesOrdersListView.vue`**

```vue
<template>
  <AppShell title="Pedidos">
    <p v-if="error" class="error-geral">{{ error }}</p>

    <PageHeader title="Pedidos" :count="countLabel">
      <button type="button" class="btn-primary" data-test="new-order" @click="newOrder">+ Novo Pedido</button>
    </PageHeader>

    <div class="toolbar">
      <input
        v-model="filters.busca"
        class="busca"
        placeholder="Buscar por nº, cliente ou vendedor..."
        data-test="busca"
        @input="load(0)"
      />
      <select v-model="filters.status" @change="load(0)">
        <option value="">Status</option>
        <option value="DRAFT">Digitado</option>
        <option value="IN_PREPARATION">Em Preparo</option>
        <option value="INVOICED">Faturado</option>
      </select>
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Pedidos</span>
        <div v-if="counts" class="table-card-stats">
          <StatPill :value="counts.total" label="Total" color="dark" />
          <StatPill :value="counts.draft" label="Digitados" color="dark" />
          <StatPill :value="counts.inPreparation" label="Em Preparo" color="amber" />
          <StatPill :value="counts.invoiced" label="Faturados" color="green" />
        </div>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nº</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-customer" @click="toggleSort('customerName')">
            Cliente
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'customerName' }">{{ sortIcon('customerName') }}</span>
          </div>
          <div class="table-grid-col">Vendedor</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-date" @click="toggleSort('orderDate')">
            Data
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'orderDate' }">{{ sortIcon('orderDate') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-total" @click="toggleSort('total')">
            Total
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'total' }">{{ sortIcon('total') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-status" @click="toggleSort('status')">
            Status
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'status' }">{{ sortIcon('status') }}</span>
          </div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="order in page.content"
          :key="order.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${order.id}`"
          @click="editOrder(order.id)"
        >
          <div class="table-grid-cell">{{ order.number }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ order.customerName }}</div>
          <div class="table-grid-cell">{{ order.salespersonName }}</div>
          <div class="table-grid-cell">{{ formatDate(order.orderDate) }}</div>
          <div class="table-grid-cell">{{ formatPrice(order.total) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(order.status)" :color="statusColor(order.status)" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="actionsFor(order)" :test-id="`btn-acoes-${order.id}`" />
          </div>
        </div>
      </div>
    </section>

    <Pagination
      :number="page.number"
      :total-pages="page.totalPages"
      :total-elements="page.totalElements"
      :size="page.size"
      @update:page="load"
      @update:size="onSizeChange"
    />
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge, { type StatusBadgeColor } from '@/components/StatusBadge.vue'
import StatPill from '@/components/StatPill.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import {
  listSalesOrders,
  getSalesOrderCounts,
  advanceSalesOrderStatus,
  deleteSalesOrder,
  type SalesOrderSummary,
  type SalesOrderCounts,
  type Page as ApiPage,
  type SalesOrderStatus,
} from '@/api/salesOrders'
import { issueSale } from '@/api/sales'

const router = useRouter()

const filters = reactive({ busca: '', status: '' })
const page = ref<ApiPage<SalesOrderSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<SalesOrderCounts | null>(null)
const sortField = ref<'customerName' | 'orderDate' | 'total' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const error = ref('')

const countLabel = computed(() => (counts.value ? `${counts.value.total} pedidos cadastrados` : undefined))

const NEXT_STATUS: Record<SalesOrderStatus, SalesOrderStatus | null> = {
  DRAFT: 'IN_PREPARATION',
  IN_PREPARATION: 'INVOICED',
  INVOICED: null,
}

const STATUS_LABEL: Record<SalesOrderStatus, string> = {
  DRAFT: 'Digitado',
  IN_PREPARATION: 'Em Preparo',
  INVOICED: 'Faturado',
}

function statusLabel(status: SalesOrderStatus) {
  return STATUS_LABEL[status]
}

function statusColor(status: SalesOrderStatus): StatusBadgeColor {
  return { DRAFT: 'gray', IN_PREPARATION: 'amber', INVOICED: 'green' }[status] as StatusBadgeColor
}

function advanceLabel(status: SalesOrderStatus) {
  const next = NEXT_STATUS[status]
  return next ? `Avançar para ${statusLabel(next)}` : null
}

function formatPrice(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatDate(date: string) {
  const [year, month, day] = date.split('-')
  return `${day}/${month}/${year}`
}

function sortIcon(field: 'customerName' | 'orderDate' | 'total' | 'status') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'customerName' | 'orderDate' | 'total' | 'status') {
  if (sortField.value === field) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortDir.value = 'asc'
  }
  load(0)
}

async function load(pageNumber: number) {
  error.value = ''
  try {
    page.value = await listSalesOrders({
      busca: filters.busca || undefined,
      status: (filters.status || undefined) as SalesOrderStatus | undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page: pageNumber,
      size: page.value.size,
    })
  } catch {
    error.value = 'Não foi possível carregar a lista de pedidos.'
  }
}

async function loadCounts() {
  error.value = ''
  try {
    counts.value = await getSalesOrderCounts()
  } catch {
    error.value = 'Não foi possível carregar o resumo de pedidos.'
  }
}

function onSizeChange(newSize: number) {
  page.value.size = newSize
  load(0)
}

function newOrder() {
  router.push({ name: 'pedidos-novo' })
}

function editOrder(id: string) {
  router.push({ name: 'pedidos-editar', params: { id } })
}

async function advance(order: SalesOrderSummary) {
  const next = NEXT_STATUS[order.status]
  if (!next) {
    return
  }
  error.value = ''
  try {
    await advanceSalesOrderStatus(order.id, next)
    await Promise.all([load(page.value.number), loadCounts()])
  } catch {
    error.value = 'Não foi possível avançar o status do pedido.'
  }
}

async function issue(order: SalesOrderSummary) {
  error.value = ''
  try {
    await issueSale(order.id)
    await Promise.all([load(page.value.number), loadCounts()])
  } catch {
    error.value = 'Não foi possível faturar o pedido.'
  }
}

async function remove(order: SalesOrderSummary) {
  if (!confirm(`Excluir o pedido nº ${order.number}?`)) {
    return
  }
  error.value = ''
  try {
    await deleteSalesOrder(order.id)
    await Promise.all([load(page.value.number), loadCounts()])
  } catch {
    error.value = 'Não foi possível excluir o pedido.'
  }
}

function actionsFor(order: SalesOrderSummary): ActionsMenuItem[] {
  const items: ActionsMenuItem[] = [
    { label: 'Editar', action: () => editOrder(order.id), testId: 'action-edit' },
  ]
  const next = NEXT_STATUS[order.status]
  if (next === 'INVOICED') {
    items.push({ label: 'Faturar', action: () => issue(order), testId: 'action-issue' })
  } else if (next) {
    items.push({ label: advanceLabel(order.status)!, action: () => advance(order), testId: 'action-advance' })
  }
  items.push({ label: 'Excluir', action: () => remove(order), danger: true, testId: 'action-delete' })
  return items
}

onMounted(() => {
  load(0)
  loadCounts()
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
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
  font-family: var(--pm-font);
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

.table-card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 12px;
}

.table-card-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--pm-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: var(--pm-font);
}

.table-card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.table-card-stats {
  display: flex;
  gap: 8px;
}

.table-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 70px 1fr 150px 100px 110px 110px 90px;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.table-grid-header {
  background: var(--pm-bg);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  padding: 12px;
}

.table-grid-col-sortable {
  cursor: pointer;
  white-space: nowrap;
}

.table-grid-sort-icon {
  font-size: 9px;
  color: var(--pm-text-muted);
  margin-left: 2px;
}

.table-grid-sort-icon-active {
  color: var(--pm-accent);
}

.table-grid-row {
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.table-grid-row-clickable {
  cursor: pointer;
  transition: background-color 0.1s;
}

.table-grid-row-clickable:hover {
  background: var(--pm-bg);
}

.table-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
```

Note `select`'s `<option value="DRAFT">`/`<option value="IN_PREPARATION">`/`<option value="INVOICED">` values changed to match the new wire-level enum values, while their visible label text (`Digitado`/`Em Preparo`/`Faturado`) stays Portuguese and unchanged. `data-test="btn-acoes-${order.id}"` (the actions-menu trigger) is left as `btn-acoes-` — it's a dynamic per-row test id whose static prefix wasn't touched by any prior sub-project's equivalent bridge/rename either; only the STATIC single-word test ids (`novo-pedido`, `col-cliente`, etc.) get translated. This mirrors the same judgment call already applied to CSS class names.

- [ ] **Step 2: Create `SalesOrdersListView.spec.ts`**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import SalesOrdersListView from '@/views/SalesOrdersListView.vue'
import * as salesOrdersApi from '@/api/salesOrders'

vi.mock('@/api/salesOrders')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/pedidos', name: 'pedidos', component: SalesOrdersListView },
      { path: '/pedidos/novo', name: 'pedidos-novo', component: { template: '<div />' } },
      { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/pedidos')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(SalesOrdersListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const orderDraft = {
  id: 'ped1', number: 1, customerName: 'Mercado Silva', salespersonName: 'Carla Vendedora',
  orderDate: '2026-07-31', total: 119.8, status: 'DRAFT' as const,
}

const orderInvoiced = {
  id: 'ped2', number: 2, customerName: 'Padaria Aurora', salespersonName: 'Carla Vendedora',
  orderDate: '2026-07-30', total: 59.9, status: 'INVOICED' as const,
}

const orderInPreparation = {
  id: 'ped3', number: 3, customerName: 'Confecções Bela Vista', salespersonName: 'Carla Vendedora',
  orderDate: '2026-08-01', total: 200.0, status: 'IN_PREPARATION' as const,
}

describe('SalesOrdersListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(salesOrdersApi.listSalesOrders).mockResolvedValue({
      content: [orderDraft], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(salesOrdersApi.getSalesOrderCounts).mockResolvedValue({
      total: 1, draft: 1, inPreparation: 0, invoiced: 0,
    })
  })

  it('loads and displays the pedido list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
    expect(wrapper.text()).toContain('1 pedidos cadastrados')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('silva')
    await flushPromises()

    expect(salesOrdersApi.listSalesOrders).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('navigates to the create form when "+ Novo Pedido" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="new-order"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-novo')
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped1"]').trigger('click')
    await wrapper.find('[data-test="action-edit"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-editar')
    expect(router.currentRoute.value.params.id).toBe('ped1')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-ped1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-editar')
    expect(router.currentRoute.value.params.id).toBe('ped1')
  })

  it('advances the status via the "Avançar para Em Preparo" Ações item', async () => {
    vi.mocked(salesOrdersApi.advanceSalesOrderStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped1"]').trigger('click')
    expect(wrapper.find('[data-test="action-advance"]').text()).toBe('Avançar para Em Preparo')
    await wrapper.find('[data-test="action-advance"]').trigger('click')
    await flushPromises()

    expect(salesOrdersApi.advanceSalesOrderStatus).toHaveBeenCalledWith('ped1', 'IN_PREPARATION')
  })

  it('hides the "Avançar" item once a pedido is already Faturado', async () => {
    vi.mocked(salesOrdersApi.listSalesOrders).mockResolvedValue({
      content: [orderInvoiced], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped2"]').trigger('click')

    expect(wrapper.find('[data-test="action-advance"]').exists()).toBe(false)
  })

  it('issues the sale via the "Faturar" Ações item when status is Em Preparo', async () => {
    vi.mocked(salesOrdersApi.listSalesOrders).mockResolvedValue({
      content: [orderInPreparation], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const salesApi = await import('@/api/sales')
    vi.spyOn(salesApi, 'issueSale').mockResolvedValue({} as never)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped3"]').trigger('click')
    expect(wrapper.find('[data-test="action-issue"]').text()).toBe('Faturar')
    await wrapper.find('[data-test="action-issue"]').trigger('click')
    await flushPromises()

    expect(salesApi.issueSale).toHaveBeenCalledWith('ped3')
    expect(salesOrdersApi.advanceSalesOrderStatus).not.toHaveBeenCalled()
  })

  it('excludes a pedido via the Ações menu after confirming', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    vi.mocked(salesOrdersApi.deleteSalesOrder).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped1"]').trigger('click')
    await wrapper.find('[data-test="action-delete"]').trigger('click')
    await flushPromises()

    expect(salesOrdersApi.deleteSalesOrder).toHaveBeenCalledWith('ped1')
  })

  it('re-fetches with the sort param when a sortable column header is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-customer"]').trigger('click')
    await flushPromises()

    expect(salesOrdersApi.listSalesOrders).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'customerName,asc' }))
  })

  it('shows an error message when loading the pedido list fails', async () => {
    vi.mocked(salesOrdersApi.listSalesOrders).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de pedidos.')
  })
})
```

- [ ] **Step 3: Delete the 2 old files**

```bash
git rm mesh-suite-frontend/src/views/PedidosListView.vue \
       mesh-suite-frontend/src/views/__tests__/PedidosListView.spec.ts
```

- [ ] **Step 4: Run the new spec in isolation**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/SalesOrdersListView.spec.ts`

Expected: 11/11 tests pass — this spec also builds its own local router, so it doesn't depend on `router/index.ts` (Task 12) to run.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/SalesOrdersListView.vue \
        mesh-suite-frontend/src/views/__tests__/SalesOrdersListView.spec.ts
git commit -m "refactor(salesorder): rename PedidosListView to SalesOrdersListView with English identifiers"
```

---

## Task 12: Frontend — router bridge + DashboardView bridge

**Files:**
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/views/DashboardView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/DashboardView.spec.ts`

**Interfaces:**
- Consumes: Task 9's `salesOrders.ts` exports, Task 10's `SalesOrderFormView.vue`, Task 11's `SalesOrdersListView.vue`.

This is the task that makes the whole frontend build again — every other file that imported `PedidoFormView.vue`/`PedidosListView.vue`/`api/pedidos.ts` gets fixed here. `DashboardView.vue` is bridge-only: its own local variable names (`pedidoResumo`, `pedidosRecentes`, `statusPedidos`, `formatarPreco`, `formatarData`, `parceiroResumo`, `produtoResumo`) stay unchanged — only the imported symbol names/types and the wire-level enum-value strings (which actually changed meaning) are updated.

- [ ] **Step 1: Update `router/index.ts`**

In `mesh-suite-frontend/src/router/index.ts`, change lines 18-19 from:
```typescript
import PedidoFormView from '@/views/PedidoFormView.vue'
import PedidosListView from '@/views/PedidosListView.vue'
```
to:
```typescript
import SalesOrderFormView from '@/views/SalesOrderFormView.vue'
import SalesOrdersListView from '@/views/SalesOrdersListView.vue'
```

Change lines 50-52 from:
```typescript
    { path: '/pedidos', name: 'pedidos', component: PedidosListView },
    { path: '/pedidos/novo', name: 'pedidos-novo', component: PedidoFormView },
    { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: PedidoFormView },
```
to:
```typescript
    { path: '/pedidos', name: 'pedidos', component: SalesOrdersListView },
    { path: '/pedidos/novo', name: 'pedidos-novo', component: SalesOrderFormView },
    { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: SalesOrderFormView },
```

The paths and route names themselves are unchanged (Portuguese, user-visible URLs).

- [ ] **Step 2: Update `DashboardView.vue`**

First, in the `<template>` block, change the recent-orders table row (former lines 33-39) from:
```html
            <tr v-for="pedido in pedidosRecentes" :key="pedido.id">
              <td>{{ pedido.numero }}</td>
              <td>{{ pedido.clienteNome }}</td>
              <td>{{ formatarData(pedido.dataPedido) }}</td>
              <td>{{ formatarPreco(pedido.total) }}</td>
              <td>
                <span class="badge" :class="`badge-${pedido.status}`">{{ statusLabel(pedido.status) }}</span>
              </td>
```
to:
```html
            <tr v-for="pedido in pedidosRecentes" :key="pedido.id">
              <td>{{ pedido.number }}</td>
              <td>{{ pedido.customerName }}</td>
              <td>{{ formatarData(pedido.orderDate) }}</td>
              <td>{{ formatarPreco(pedido.total) }}</td>
              <td>
                <span class="badge" :class="`badge-${pedido.status}`">{{ statusLabel(pedido.status) }}</span>
              </td>
```

This is load-bearing, not cosmetic — `pedidosRecentes` is typed `SalesOrderSummary[]` after this task's script changes, and `SalesOrderSummary` has no `numero`/`clienteNome`/`dataPedido` fields (they're `number`/`customerName`/`orderDate`); `vue-tsc` would fail on this template block if left unchanged. The loop variable name `pedido` itself, `pedido.id`, `pedido.total`, and `pedido.status` are untouched since those field names didn't change.

Then, in the `<script setup>` block, change the import block (lines 92-98) from:
```typescript
import {
  buscarResumoPedidos,
  listarPedidos,
  type PedidoResumo,
  type PedidoSummary,
  type StatusPedido,
} from '@/api/pedidos'
```
to:
```typescript
import {
  getSalesOrderCounts,
  listSalesOrders,
  type SalesOrderCounts,
  type SalesOrderSummary,
  type SalesOrderStatus,
} from '@/api/salesOrders'
```

Change the ref declarations (lines 114, 117) from:
```typescript
const pedidoResumo = ref<PedidoResumo | null>(null)
```
to:
```typescript
const pedidoResumo = ref<SalesOrderCounts | null>(null)
```
and from:
```typescript
const pedidosRecentes = ref<PedidoSummary[]>([])
```
to:
```typescript
const pedidosRecentes = ref<SalesOrderSummary[]>([])
```

Change the stats computed (line 122) from:
```typescript
  { icon: '🧾', label: 'Pedidos Faturados', value: pedidoResumo.value ? String(pedidoResumo.value.faturados) : '—' },
```
to:
```typescript
  { icon: '🧾', label: 'Pedidos Faturados', value: pedidoResumo.value ? String(pedidoResumo.value.invoiced) : '—' },
```
(the `total` field is unchanged, so line 120 stays exactly as-is).

Change the STATUS_LABEL type and `statusLabel` function (lines 126-134) from:
```typescript
const STATUS_LABEL: Record<StatusPedido, string> = {
  DIGITADO: 'Digitado',
  EM_PREPARO: 'Em Preparo',
  FATURADO: 'Faturado',
}

function statusLabel(status: StatusPedido) {
  return STATUS_LABEL[status]
}
```
to:
```typescript
const STATUS_LABEL: Record<SalesOrderStatus, string> = {
  DRAFT: 'Digitado',
  IN_PREPARATION: 'Em Preparo',
  INVOICED: 'Faturado',
}

function statusLabel(status: SalesOrderStatus) {
  return STATUS_LABEL[status]
}
```

Change the `statusPedidos` computed (lines 136-144) from:
```typescript
const statusPedidos = computed(() => {
  const r = pedidoResumo.value
  if (!r) return []
  return [
    { label: 'Digitados', value: r.digitados, classe: 'DIGITADO' as StatusPedido },
    { label: 'Em Preparo', value: r.emPreparo, classe: 'EM_PREPARO' as StatusPedido },
    { label: 'Faturados', value: r.faturados, classe: 'FATURADO' as StatusPedido },
  ]
})
```
to:
```typescript
const statusPedidos = computed(() => {
  const r = pedidoResumo.value
  if (!r) return []
  return [
    { label: 'Digitados', value: r.draft, classe: 'DRAFT' as SalesOrderStatus },
    { label: 'Em Preparo', value: r.inPreparation, classe: 'IN_PREPARATION' as SalesOrderStatus },
    { label: 'Faturados', value: r.invoiced, classe: 'INVOICED' as SalesOrderStatus },
  ]
})
```

Change the `onMounted` block (lines 155-166) from:
```typescript
onMounted(async () => {
  const [pedidoR, parceiroR, produtoR, pedidosR] = await Promise.allSettled([
    buscarResumoPedidos(),
    getPartnerSummary(),
    getProductSummary(),
    listarPedidos({ page: 0, size: 5 }),
  ])
  if (pedidoR.status === 'fulfilled') pedidoResumo.value = pedidoR.value
  if (parceiroR.status === 'fulfilled') parceiroResumo.value = parceiroR.value
  if (produtoR.status === 'fulfilled') produtoResumo.value = produtoR.value
  if (pedidosR.status === 'fulfilled') pedidosRecentes.value = pedidosR.value.content
})
```
to:
```typescript
onMounted(async () => {
  const [pedidoR, parceiroR, produtoR, pedidosR] = await Promise.allSettled([
    getSalesOrderCounts(),
    getPartnerSummary(),
    getProductSummary(),
    listSalesOrders({ page: 0, size: 5 }),
  ])
  if (pedidoR.status === 'fulfilled') pedidoResumo.value = pedidoR.value
  if (parceiroR.status === 'fulfilled') parceiroResumo.value = parceiroR.value
  if (produtoR.status === 'fulfilled') produtoResumo.value = produtoR.value
  if (pedidosR.status === 'fulfilled') pedidosRecentes.value = pedidosR.value.content
})
```

Finally, update the 6 CSS selectors that are dynamically built from the (now-changed) status enum values — these are load-bearing, not cosmetic, since `:class="`badge-${pedido.status}`"` and `:class="`dot-${item.classe}`"` in the template build class names at runtime from the new `DRAFT`/`IN_PREPARATION`/`INVOICED` values. In the `<style scoped>` block, change:
```css
.badge-DIGITADO {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.badge-EM_PREPARO {
  background: var(--pm-warning-bg, var(--pm-bg));
  color: var(--pm-warning, var(--pm-text-mid));
}

.badge-FATURADO {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}
```
to:
```css
.badge-DRAFT {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.badge-IN_PREPARATION {
  background: var(--pm-warning-bg, var(--pm-bg));
  color: var(--pm-warning, var(--pm-text-mid));
}

.badge-INVOICED {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}
```
and change:
```css
.dot-DIGITADO {
  background: var(--pm-text-mid);
}

.dot-EM_PREPARO {
  background: var(--pm-warning, var(--pm-text-mid));
}

.dot-FATURADO {
  background: var(--pm-success);
}
```
to:
```css
.dot-DRAFT {
  background: var(--pm-text-mid);
}

.dot-IN_PREPARATION {
  background: var(--pm-warning, var(--pm-text-mid));
}

.dot-INVOICED {
  background: var(--pm-success);
}
```

Every other identifier in `DashboardView.vue` (`pedidoResumo`, `pedidosRecentes`, `statusPedidos`, `formatarPreco`, `formatarData`, `parceiroResumo`, `produtoResumo`, the `pedido`/`item` loop variables, `classe` field) stays exactly as it is — this is Dashboard's own file, bridge-only.

- [ ] **Step 3: Update `DashboardView.spec.ts`**

In `mesh-suite-frontend/src/views/__tests__/DashboardView.spec.ts`, change line 7 from:
```typescript
import * as pedidosApi from '@/api/pedidos'
```
to:
```typescript
import * as pedidosApi from '@/api/salesOrders'
```
(keep the local alias `pedidosApi` unchanged, per the established minimal-bridge convention).

Change line 11 from:
```typescript
vi.mock('@/api/pedidos')
```
to:
```typescript
vi.mock('@/api/salesOrders')
```

Change the `pedidoRecente` mock object (lines 34-37) from:
```typescript
const pedidoRecente = {
  id: 'ped1', numero: 41, clienteNome: 'Mercado Silva', vendedorNome: 'Carla Vendedora',
  dataPedido: '2026-08-03', total: 450, status: 'DIGITADO' as const,
}
```
to:
```typescript
const pedidoRecente = {
  id: 'ped1', number: 41, customerName: 'Mercado Silva', salespersonName: 'Carla Vendedora',
  orderDate: '2026-08-03', total: 450, status: 'DRAFT' as const,
}
```

Change the `beforeEach` mocks (lines 43-48) from:
```typescript
    vi.mocked(pedidosApi.buscarResumoPedidos).mockResolvedValue({
      total: 38, digitados: 12, emPreparo: 18, faturados: 8,
    })
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [pedidoRecente], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
```
to:
```typescript
    vi.mocked(pedidosApi.getSalesOrderCounts).mockResolvedValue({
      total: 38, draft: 12, inPreparation: 18, invoiced: 8,
    })
    vi.mocked(pedidosApi.listSalesOrders).mockResolvedValue({
      content: [pedidoRecente], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
```

Change the mock re-set inside the empty-state test (former lines 113-115) from:
```typescript
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 5,
    })
```
to:
```typescript
    vi.mocked(pedidosApi.listSalesOrders).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 5,
    })
```

No other line in this file changes — every assertion text (`'Total de Pedidos'`, `'Pedidos Faturados'`, `'Digitados'`, `'Em Preparo'`, route names `pedidos`/`pedidos-novo`/`pedidos-editar`, `data-test` selectors `novo-pedido`/`ver-todos-pedidos`) stays exactly as-is — those are either Portuguese UI text or route/data-test identifiers this task doesn't touch (the `data-test="novo-pedido"` attribute on `DashboardView.vue`'s own "+ Novo Pedido" quick-action button was never part of `SalesOrder`'s own view, so it's untouched, unlike `SalesOrdersListView.vue`'s analogous button which this plan renamed to `data-test="new-order"` in Task 11 since that one IS the `SalesOrder` module's own view).

- [ ] **Step 4: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run --run`

Expected: all test files pass (44 files total — same count as before this sub-project: 2 files deleted in Task 10, 2 created; 2 deleted in Task 11, 2 created; net zero change).

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: 0 errors. (Always use `-p tsconfig.app.json` — bare `vue-tsc --noEmit` silently reports 0 errors regardless of real breakage in this project.)

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/DashboardView.vue \
        mesh-suite-frontend/src/views/__tests__/DashboardView.spec.ts
git commit -m "refactor(salesorder): bridge router and DashboardView to SalesOrder types, full frontend suite green"
```

---

## Task 13: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`

Expected: 0 failures. Errors matching the documented pre-existing flake exactly (15 errors: 3 `CompanyRepositoryTest` + 3 `AccountsPayableControllerTest` + 1 `AccountsPayableRepositoryTest` + 8 `AccountsPayableServiceTest`) — confirm via `target/surefire-reports/*.txt` class names, not just the aggregate count.

- [ ] **Step 2: Full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run --run`

Expected: all test files pass, 0 failures.

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: 0 errors.

- [ ] **Step 3: `com.meshsuite.pedido` no longer exists**

Run: `find mesh-suite-backend/src -type d -path "*com/meshsuite/pedido*"`

Expected: no output.

- [ ] **Step 4: Broad grep audit for missed `Pedido`/`pedido` code identifiers**

Run: `grep -rln "\bPedido\b\|\bStatusPedido\b\|\bItemPedido\b" mesh-suite-backend/src --include="*.java"`

Expected: no output.

Run: `grep -rln "listarPedidos\|buscarPedido\|criarPedido\|atualizarPedido\|avancarStatusPedido\|excluirPedido\|buscarResumoPedidos\|ListarPedidosParams\|StatusPedido\b" mesh-suite-frontend/src --include="*.ts" --include="*.vue"`

Expected: no output. (Portuguese prose/UI text like "Pedidos", "Novo Pedido", route paths `/pedidos`, and the local bridge variable names in `DashboardView.vue`/`.spec.ts` like `pedidoResumo`/`pedidosRecentes`/`pedidosApi`/`pedidoRecente` are expected to remain — only bare code identifiers that reference the OLD renamed symbols matter here.)

Run: `grep -rln "DIGITADO\|EM_PREPARO\|FATURADO" mesh-suite-backend/src mesh-suite-frontend/src`

Expected: no output — every wire-level enum value should now read `DRAFT`/`IN_PREPARATION`/`INVOICED` everywhere, including test fixtures and mock data.

- [ ] **Step 5: Confirm no leftover verification-technique artifacts**

Run: `git status --short`

Expected: clean.

Report PLAN COMPLETE once all 5 verification steps pass.
