# Compra (Nota Fiscal de Entrada) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert a `PurchaseOrder` in status `OPEN` into a `PurchaseInvoice` (fiscal purchase document, i.e. the incoming invoice / "nota fiscal de entrada") — new `PurchaseInvoice`/`PurchaseInvoiceItem` entities with a formal FK to `PurchaseOrder`, per-item tax calculation reusing `FiscalCalculationService`, stock debit via `StockService.adjustBalance`, accounts-payable installments via `AccountsPayableService.createInstallments`, a "Lançar Compra" action + form in the frontend, and a read-only listing screen.

**Architecture:** New backend module `com.meshsuite.purchaseinvoice` (controller/service/repository/domain/dto/exception), following the same layered-per-module package structure already used by `purchaseorder`/`sale`/etc. `PurchaseInvoiceService` depends one-directionally on `purchaseorder` (repository/domain), `fiscal` (`FiscalCalculationService`), `stock` (`StockService.adjustBalance`), and `payable` (`AccountsPayableService.createInstallments`) — this is the first slice in the codebase where a document issuance wires stock and payables together in one transaction. `PurchaseOrderService.updateStatus` is tightened to reject the `RECEIVED` target so status can only reach `RECEIVED` via the new issuance flow, guaranteeing every `RECEIVED` `PurchaseOrder` has exactly one `PurchaseInvoice`. Frontend adds `src/api/purchaseInvoices.ts`, a read-only `PurchaseInvoicesListView.vue`, a new `PurchaseInvoiceFormView.vue` (header fields + dynamic installment rows — unlike `Sale.issue`, this flow needs real user input, so it gets a dedicated screen instead of a one-click action), and swaps the existing "Marcar como Recebida" action in `PurchaseOrdersListView.vue` for "Lançar Compra".

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / PostgreSQL 16 (Flyway, RLS) on the backend; Vue 3 + TypeScript + Vitest on the frontend. Same stack as every other slice already in this repo — no new dependencies.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-17-compra-nota-fiscal-entrada-design.md` — read it before starting if anything below is ambiguous.
- 1 `PurchaseOrder` → 1 `PurchaseInvoice`: `purchase_invoice.purchase_order_id` is `NOT NULL UNIQUE`, a real FK.
- `PurchaseInvoice` is immutable once created: no `PUT`/`DELETE` endpoints, no status field.
- Duplicate-invoice guard: `(supplier_id, invoice_number)` is `UNIQUE` at the DB level (regra 2 do PRD) — checked pre-emptively in the service too, for a clean 400 instead of a raw constraint violation on the ordinary path.
- `entryDate >= issueDate` is a required validation (regra 7 do PRD).
- The sum of the request's `installments` must equal `PurchaseInvoice.total` exactly (regra 5 do PRD) — validated in the service before persisting anything.
- Tax calc reuses `com.meshsuite.fiscal.service.FiscalCalculationService` exactly as it exists today (flat percentage rates from `FiscalRegistration`) — do not add ICMS-ST/MVA/IPI-redução granularity.
- Stock is adjusted via `com.meshsuite.stock.service.StockService.adjustBalance(tenantId, productId, StockMovementType.INBOUND, quantity, StockMovementOrigin.PURCHASE, referenceId, userId, note)` — one call per item, inside the same transaction as the rest of the issuance. Do not touch `Product.stockQuantity` directly.
- Accounts payable installments are created via `com.meshsuite.payable.service.AccountsPayableService.createInstallments(tenantId, supplierId, referenceId, installments)` — one call for the whole invoice, inside the same transaction.
- Every new table gets `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + a tenant-isolation policy, mirroring `purchase_order`/`purchase_order_item` exactly (child tables with no `tenant_id` column use the `EXISTS` pattern against the parent).
- Every service method that's reachable via a controller gets `@RequiresPermission(module = Module.PURCHASE_INVOICE, action = Action.*)`.
- Package layout per module: `controller/`, `service/`, `repository/`, `repository/specification/`, `domain/`, `dto/`, `exception/` — same structure the rest of the backend already uses.
- Only touch of already-working code: `PurchaseOrderService.updateStatus` gets one new guard clause (reject `RECEIVED`), which requires fixing three existing `PurchaseOrderServiceTest` tests and one `PurchaseOrderControllerTest` assertion that currently rely on reaching `RECEIVED` through that method — see Task 4, it explains exactly why and how. Nothing else in `purchaseorder`/`product`/`fiscal`/`stock`/`payable` changes.
- No new frontend dependencies. No importing NF-e XML, no freight/Conhecimento de Transporte fields, no "código de indicador de pagamento" — all explicitly out of scope per the spec.

---

### Task 1: Migrations — `purchase_invoice`/`purchase_invoice_item`/`purchase_invoice_counter` tables + `PURCHASE_INVOICE` permission module

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V28__create_purchase_invoice.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V29__add_purchase_invoice_to_user_permission_module_check.sql`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/domain/enums/Module.java`

**Interfaces:**
- Produces: DB tables `purchase_invoice`, `purchase_invoice_item`, `purchase_invoice_counter` (columns listed below); enum constant `Module.PURCHASE_INVOICE`.

- [ ] **Step 1: Write the migration for the counter, `purchase_invoice`, and `purchase_invoice_item` tables**

```sql
-- mesh-suite-backend/src/main/resources/db/migration/V28__create_purchase_invoice.sql
CREATE TABLE purchase_invoice_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE purchase_invoice_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_invoice_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY purchase_invoice_counter_tenant_isolation ON purchase_invoice_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE purchase_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    invoice_number VARCHAR(20) NOT NULL,
    series VARCHAR(10) NOT NULL,
    model VARCHAR(10) NOT NULL,
    purchase_order_id UUID NOT NULL UNIQUE REFERENCES purchase_order(id),
    supplier_id UUID NOT NULL REFERENCES partner(id),
    issue_date DATE NOT NULL,
    entry_date DATE NOT NULL,
    discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    icms_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ipi_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    pis_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    cofins_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_purchase_invoice_tenant_number ON purchase_invoice(tenant_id, number);
-- Regra 2 do PRD: bloqueio de nota duplicada por fornecedor.
CREATE UNIQUE INDEX idx_purchase_invoice_supplier_invoice_number ON purchase_invoice(supplier_id, invoice_number);
CREATE INDEX idx_purchase_invoice_tenant_id ON purchase_invoice(tenant_id);
CREATE INDEX idx_purchase_invoice_supplier_id ON purchase_invoice(supplier_id);

ALTER TABLE purchase_invoice ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_invoice FORCE ROW LEVEL SECURITY;

CREATE POLICY purchase_invoice_tenant_isolation ON purchase_invoice
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE purchase_invoice_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_invoice_id UUID NOT NULL REFERENCES purchase_invoice(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_value NUMERIC(12,2) NOT NULL,
    icms_amount NUMERIC(12,2) NOT NULL,
    ipi_amount NUMERIC(12,2) NOT NULL,
    pis_amount NUMERIC(12,2) NOT NULL,
    cofins_amount NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_purchase_invoice_item_purchase_invoice_id ON purchase_invoice_item(purchase_invoice_id);

ALTER TABLE purchase_invoice_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_invoice_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- purchase_invoice row's own RLS policy, matched by purchase_invoice_id.
-- Same pattern as sale_item/purchase_order_item.
CREATE POLICY purchase_invoice_item_tenant_isolation ON purchase_invoice_item
    USING (EXISTS (
        SELECT 1 FROM purchase_invoice pi
        WHERE pi.id = purchase_invoice_item.purchase_invoice_id
          AND pi.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

- [ ] **Step 2: Write the migration adding `PURCHASE_INVOICE` to the permission module check constraint**

```sql
-- mesh-suite-backend/src/main/resources/db/migration/V29__add_purchase_invoice_to_user_permission_module_check.sql
ALTER TABLE user_permission DROP CONSTRAINT user_permission_module_check;

ALTER TABLE user_permission ADD CONSTRAINT user_permission_module_check
    CHECK (module IN ('CUSTOMER','PRODUCT','ORDER','USER','PURCHASE','STOCK','PAYABLE','SALE','PURCHASE_INVOICE'));
```

- [ ] **Step 3: Add `PURCHASE_INVOICE` to the `Module` enum**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/auth/domain/enums/Module.java
package com.meshsuite.auth.domain.enums;

public enum Module {
    CUSTOMER,
    PRODUCT,
    ORDER,
    USER,
    PURCHASE,
    STOCK,
    PAYABLE,
    SALE,
    PURCHASE_INVOICE
}
```

- [ ] **Step 4: Verify the app compiles with the new migrations**

Run: `cd mesh-suite-backend && mvn -q clean compile`
Expected: `BUILD SUCCESS`, no output on success. (Migrations themselves are validated when the test suite boots the Spring context in later tasks — Flyway fails loudly if the SQL is malformed.)

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V28__create_purchase_invoice.sql \
        mesh-suite-backend/src/main/resources/db/migration/V29__add_purchase_invoice_to_user_permission_module_check.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/domain/enums/Module.java
git commit -m "feat(purchase-invoice): add purchase_invoice/purchase_invoice_item/purchase_invoice_counter tables and PURCHASE_INVOICE permission module"
```

---

### Task 2: Domain entities, repositories, and RLS tests

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/domain/PurchaseInvoice.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/domain/PurchaseInvoiceItem.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/domain/PurchaseInvoiceCounter.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/repository/PurchaseInvoiceRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/repository/PurchaseInvoiceCounterRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/repository/specification/PurchaseInvoiceSpecifications.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseinvoice/repository/PurchaseInvoiceRepositoryTest.java`

**Interfaces:**
- Consumes: `com.meshsuite.purchaseorder.domain.PurchaseOrder` (existing), `com.meshsuite.partner.domain.Partner` (existing), `com.meshsuite.product.domain.Product` (existing).
- Produces: `PurchaseInvoice` (getters/setters: `getId/setId`, `getTenantId/setTenantId`, `getNumber/setNumber`, `getInvoiceNumber/setInvoiceNumber`, `getSeries/setSeries`, `getModel/setModel`, `getPurchaseOrder/setPurchaseOrder`, `getSupplier/setSupplier`, `getIssueDate/setIssueDate`, `getEntryDate/setEntryDate`, `getDiscount/setDiscount`, `getSubtotal/setSubtotal`, `getTotal/setTotal`, `getIcmsAmount/setIcmsAmount`, `getIpiAmount/setIpiAmount`, `getPisAmount/setPisAmount`, `getCofinsAmount/setCofinsAmount`, `getCreatedAt`, `getItems()` returning `List<PurchaseInvoiceItem>`). `PurchaseInvoiceItem` (`getId/setId`, `getPurchaseInvoice/setPurchaseInvoice`, `getProduct/setProduct`, `getQuantity/setQuantity`, `getUnitPrice/setUnitPrice`, `getTotalValue/setTotalValue`, `getIcmsAmount/setIcmsAmount`, `getIpiAmount/setIpiAmount`, `getPisAmount/setPisAmount`, `getCofinsAmount/setCofinsAmount`). `PurchaseInvoiceCounter` (`getTenantId/setTenantId`, `getNextNumber/setNextNumber`). `PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, UUID>, JpaSpecificationExecutor<PurchaseInvoice>` with `findBySupplierIdAndInvoiceNumber(UUID supplierId, String invoiceNumber): Optional<PurchaseInvoice>`. `PurchaseInvoiceCounterRepository extends JpaRepository<PurchaseInvoiceCounter, UUID>`. `PurchaseInvoiceSpecifications.withSearch(String search): Specification<PurchaseInvoice>`.

- [ ] **Step 1: Write the failing RLS/cascade test**

```java
// mesh-suite-backend/src/test/java/com/meshsuite/purchaseinvoice/repository/PurchaseInvoiceRepositoryTest.java
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
```

- [ ] **Step 2: Run the test to verify it fails to compile (entities/repos don't exist yet)**

Run: `cd mesh-suite-backend && mvn -q test-compile`
Expected: `COMPILATION ERROR` — `package com.meshsuite.purchaseinvoice.domain does not exist` (or similar).

- [ ] **Step 3: Create `PurchaseInvoice.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/domain/PurchaseInvoice.java
package com.meshsuite.purchaseinvoice.domain;

import com.meshsuite.partner.domain.Partner;
import com.meshsuite.purchaseorder.domain.PurchaseOrder;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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
@Table(name = "purchase_invoice")
@Getter
@Setter
public class PurchaseInvoice {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Integer number;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(nullable = false)
    private String series;

    @Column(nullable = false)
    private String model;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false, unique = true)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Partner supplier;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "icms_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal icmsAmount = BigDecimal.ZERO;

    @Column(name = "ipi_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal ipiAmount = BigDecimal.ZERO;

    @Column(name = "pis_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal pisAmount = BigDecimal.ZERO;

    @Column(name = "cofins_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cofinsAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "purchaseInvoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PurchaseInvoiceItem> items = new ArrayList<>();
}
```

- [ ] **Step 4: Create `PurchaseInvoiceItem.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/domain/PurchaseInvoiceItem.java
package com.meshsuite.purchaseinvoice.domain;

import com.meshsuite.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "purchase_invoice_item")
@Getter
@Setter
public class PurchaseInvoiceItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", nullable = false)
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "icms_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal icmsAmount;

    @Column(name = "ipi_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal ipiAmount;

    @Column(name = "pis_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal pisAmount;

    @Column(name = "cofins_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cofinsAmount;
}
```

- [ ] **Step 5: Create `PurchaseInvoiceCounter.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/domain/PurchaseInvoiceCounter.java
package com.meshsuite.purchaseinvoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "purchase_invoice_counter")
@Getter
@Setter
public class PurchaseInvoiceCounter {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber = 1;
}
```

- [ ] **Step 6: Create the repositories and specification**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/repository/PurchaseInvoiceRepository.java
package com.meshsuite.purchaseinvoice.repository;

import com.meshsuite.purchaseinvoice.domain.PurchaseInvoice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, UUID>, JpaSpecificationExecutor<PurchaseInvoice> {
    Optional<PurchaseInvoice> findBySupplierIdAndInvoiceNumber(UUID supplierId, String invoiceNumber);
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/repository/PurchaseInvoiceCounterRepository.java
package com.meshsuite.purchaseinvoice.repository;

import com.meshsuite.purchaseinvoice.domain.PurchaseInvoiceCounter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseInvoiceCounterRepository extends JpaRepository<PurchaseInvoiceCounter, UUID> {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/repository/specification/PurchaseInvoiceSpecifications.java
package com.meshsuite.purchaseinvoice.repository.specification;

import com.meshsuite.purchaseinvoice.domain.PurchaseInvoice;
import org.springframework.data.jpa.domain.Specification;

public final class PurchaseInvoiceSpecifications {

    private PurchaseInvoiceSpecifications() {
    }

    public static Specification<PurchaseInvoice> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        Integer number = tryParseInt(search.trim());
        return (root, query, cb) -> {
            var byText = cb.or(
                    cb.like(cb.lower(root.get("supplier").get("tradeName")), term),
                    cb.like(cb.lower(root.get("invoiceNumber")), term));
            if (number != null) {
                return cb.or(byText, cb.equal(root.get("number"), number));
            }
            return byText;
        };
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

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PurchaseInvoiceRepositoryTest`
Expected: `BUILD SUCCESS`, 6 tests passed. (Requires Docker for Testcontainers — if unavailable in your environment, this is the point where you must run it before continuing; every later task builds on this passing.)

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/domain \
        mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/repository \
        mesh-suite-backend/src/test/java/com/meshsuite/purchaseinvoice/repository/PurchaseInvoiceRepositoryTest.java
git commit -m "feat(purchase-invoice): add PurchaseInvoice/PurchaseInvoiceItem/PurchaseInvoiceCounter entities and repositories"
```

---

### Task 3: DTOs and exceptions

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/InstallmentInput.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/PurchaseInvoiceRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/PurchaseInvoiceItemResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/PurchaseInvoiceResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/PurchaseInvoiceSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/exception/PurchaseInvoiceNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/exception/PurchaseInvoiceValidationException.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `InstallmentInput(BigDecimal amount, LocalDate dueDate)`. `PurchaseInvoiceRequest(String invoiceNumber, String series, String model, LocalDate issueDate, LocalDate entryDate, List<InstallmentInput> installments)`. `PurchaseInvoiceItemResponse(UUID productId, String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal totalValue, BigDecimal icmsAmount, BigDecimal ipiAmount, BigDecimal pisAmount, BigDecimal cofinsAmount)`. `PurchaseInvoiceResponse(UUID id, Integer number, String invoiceNumber, String series, String model, UUID purchaseOrderId, Integer purchaseOrderNumber, UUID supplierId, String supplierName, LocalDate issueDate, LocalDate entryDate, BigDecimal discount, BigDecimal subtotal, BigDecimal total, BigDecimal icmsAmount, BigDecimal ipiAmount, BigDecimal pisAmount, BigDecimal cofinsAmount, List<PurchaseInvoiceItemResponse> items)`. `PurchaseInvoiceSummaryResponse(UUID id, Integer number, String invoiceNumber, String supplierName, LocalDate issueDate, BigDecimal total)`. `PurchaseInvoiceNotFoundException` (no-arg constructor), `PurchaseInvoiceValidationException(String message)`.

- [ ] **Step 1: Create the DTOs**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/InstallmentInput.java
package com.meshsuite.purchaseinvoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentInput(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDate dueDate) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/PurchaseInvoiceRequest.java
package com.meshsuite.purchaseinvoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record PurchaseInvoiceRequest(
        @NotBlank String invoiceNumber,
        @NotBlank String series,
        @NotBlank String model,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate entryDate,
        @NotEmpty List<@Valid InstallmentInput> installments) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/PurchaseInvoiceItemResponse.java
package com.meshsuite.purchaseinvoice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseInvoiceItemResponse(
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalValue,
        BigDecimal icmsAmount,
        BigDecimal ipiAmount,
        BigDecimal pisAmount,
        BigDecimal cofinsAmount) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/PurchaseInvoiceResponse.java
package com.meshsuite.purchaseinvoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseInvoiceResponse(
        UUID id,
        Integer number,
        String invoiceNumber,
        String series,
        String model,
        UUID purchaseOrderId,
        Integer purchaseOrderNumber,
        UUID supplierId,
        String supplierName,
        LocalDate issueDate,
        LocalDate entryDate,
        BigDecimal discount,
        BigDecimal subtotal,
        BigDecimal total,
        BigDecimal icmsAmount,
        BigDecimal ipiAmount,
        BigDecimal pisAmount,
        BigDecimal cofinsAmount,
        List<PurchaseInvoiceItemResponse> items) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto/PurchaseInvoiceSummaryResponse.java
package com.meshsuite.purchaseinvoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseInvoiceSummaryResponse(
        UUID id,
        Integer number,
        String invoiceNumber,
        String supplierName,
        LocalDate issueDate,
        BigDecimal total) {
}
```

- [ ] **Step 2: Create the exceptions**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/exception/PurchaseInvoiceNotFoundException.java
package com.meshsuite.purchaseinvoice.exception;

public class PurchaseInvoiceNotFoundException extends RuntimeException {
    public PurchaseInvoiceNotFoundException() {
        super("Compra não encontrada");
    }
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/exception/PurchaseInvoiceValidationException.java
package com.meshsuite.purchaseinvoice.exception;

public class PurchaseInvoiceValidationException extends RuntimeException {
    public PurchaseInvoiceValidationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Wire both exceptions into `GlobalExceptionHandler`**

In `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`, add these two methods at the end of the class, right before the closing `}` (same style as every other exception mapping in this file — fully-qualified inline type, no new import):

```java
    @ExceptionHandler(com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseInvoiceNotFound(
            com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceValidationException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseInvoiceValidation(
            com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 4: Verify it compiles**

Run: `cd mesh-suite-backend && mvn -q clean compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/dto \
        mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/exception \
        mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java
git commit -m "feat(purchase-invoice): add PurchaseInvoice DTOs and exceptions"
```

---

### Task 4: Tighten `PurchaseOrderService.updateStatus` to reject `RECEIVED`

**Why this task is more involved than it looks:** unlike `SalesOrderService.advanceStatus` (which never had a test relying on reaching `INVOICED` through the generic advance method), `PurchaseOrderService.updateStatus` already ships with real behavior that three existing tests depend on: `PurchaseOrderServiceTest#marksAsReceivedFromOpen` asserts `updateStatus(id, RECEIVED)` works; `#rejectsStatusChangeOnceReceived` and `#countsByStatus` both call `updateStatus(id, RECEIVED)` purely as setup to get an order into `RECEIVED` before testing something else. Once the guard lands, all three break. This task fixes them alongside the guard, in the same commit — landing the guard without fixing them would leave the suite red.

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/service/PurchaseOrderService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/service/PurchaseOrderServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/controller/PurchaseOrderControllerTest.java`

**Interfaces:**
- Consumes: `com.meshsuite.purchaseorder.exception.PurchaseOrderValidationException` (existing), `com.meshsuite.purchaseorder.repository.PurchaseOrderRepository` (existing).
- Produces: `PurchaseOrderService.updateStatus(UUID id, PurchaseOrderStatus newStatus)` now throws `PurchaseOrderValidationException` immediately when `newStatus == PurchaseOrderStatus.RECEIVED`, before any other check.

- [ ] **Step 1: Write the failing service test — replace `marksAsReceivedFromOpen`**

In `PurchaseOrderServiceTest.java`, replace the existing `marksAsReceivedFromOpen` test (its premise — that `updateStatus` can reach `RECEIVED` — becomes false) with a test asserting the opposite:

```java
    @Test
    void rejectsMarkingReceivedViaUpdateStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));

        assertThrows(PurchaseOrderValidationException.class,
                () -> purchaseOrderService.updateStatus(created.id(), PurchaseOrderStatus.RECEIVED));
    }
```

- [ ] **Step 2: Fix `rejectsStatusChangeOnceReceived` and `countsByStatus` to reach `RECEIVED` without going through `updateStatus`**

Add `import com.meshsuite.purchaseorder.repository.PurchaseOrderRepository;` to the test file's imports (it isn't imported yet — only `PurchaseOrderService` is) and add `@Autowired PurchaseOrderRepository purchaseOrderRepository;` to the test class's field list (next to the other `@Autowired` fields), then add this private helper (it bypasses the service intentionally — these two tests need a `RECEIVED` order as *setup*, not as the thing under test, and reaching it for real now requires the full `PurchaseInvoiceService.issue` flow from Task 5, which doesn't exist yet and would be an unrelated dependency to drag into this test class):

```java
    // RECEIVED is now only reachable for real via PurchaseInvoiceService.issue
    // (Task 5) -- these two tests only need a RECEIVED order as setup for what
    // they actually test (status-change rejection, counts), so they set it
    // directly through the repository instead of depending on the whole
    // purchase-invoice issuance flow.
    private void forceReceived(UUID orderId) {
        var order = purchaseOrderRepository.findById(orderId).orElseThrow();
        order.setStatus(PurchaseOrderStatus.RECEIVED);
        purchaseOrderRepository.saveAndFlush(order);
    }
```

Change `rejectsStatusChangeOnceReceived` from:

```java
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
```

to:

```java
    @Test
    void rejectsStatusChangeOnceReceived() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProduto(tenantId, "P0001", new BigDecimal("25.00"));
        var items = List.of(new PurchaseOrderItemRequest(productId, BigDecimal.ONE, new BigDecimal("25.00")));
        var created = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        forceReceived(created.id());

        assertThrows(PurchaseOrderValidationException.class,
                () -> purchaseOrderService.updateStatus(created.id(), PurchaseOrderStatus.CANCELLED));
    }
```

Change `countsByStatus` from:

```java
        var a = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        purchaseOrderService.updateStatus(a.id(), PurchaseOrderStatus.RECEIVED);
```

to:

```java
        var a = purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        purchaseOrderService.create(tenantId, request(supplierId, buyerId, items, BigDecimal.ZERO));
        forceReceived(a.id());
```

- [ ] **Step 3: Run the tests to verify they fail (the guard doesn't exist yet, so `rejectsMarkingReceivedViaUpdateStatus` fails; the other two should still pass since `forceReceived` doesn't touch the guarded method)**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PurchaseOrderServiceTest`
Expected: `rejectsMarkingReceivedViaUpdateStatus` FAILS (no exception thrown — `updateStatus` still happily sets `RECEIVED`); the rest pass.

- [ ] **Step 4: Add the guard clause**

In `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/service/PurchaseOrderService.java`, change `updateStatus` from:

```java
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
```

to:

```java
    @Transactional
    @RequiresPermission(module = Module.PURCHASE, action = Action.EDIT)
    public PurchaseOrderResponse updateStatus(UUID id, PurchaseOrderStatus newStatus) {
        if (newStatus == PurchaseOrderStatus.RECEIVED) {
            throw new PurchaseOrderValidationException(
                    "Recebimento deve ser feito através do lançamento da Compra "
                            + "(POST /api/purchase-invoices/issue/{purchaseOrderId})");
        }
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
```

- [ ] **Step 5: Run the whole `PurchaseOrderServiceTest` class to verify everything passes now**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PurchaseOrderServiceTest`
Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 6: Fix the HTTP-level regression in `PurchaseOrderControllerTest`**

The combined test `createsListsUpdatesChangesStatusAndDeletesPurchaseOrder` currently does `PATCH .../status {"status":"RECEIVED"}` and expects `200`/`RECEIVED`, which now returns `400`. Change the status-change block from:

```java
        mockMvc.perform(patch("/api/purchase-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        mockMvc.perform(patch("/api/purchase-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());
```

to:

```java
        mockMvc.perform(patch("/api/purchase-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/purchase-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
```

(The delete step right after still works — `delete` doesn't care about status.)

- [ ] **Step 7: Run the controller test to verify it passes**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PurchaseOrderControllerTest`
Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/service/PurchaseOrderService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/service/PurchaseOrderServiceTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/controller/PurchaseOrderControllerTest.java
git commit -m "fix(purchase-order): reject marking status RECEIVED outside the Compra issuance flow"
```

---

### Task 5: `PurchaseInvoiceService` + `PurchaseInvoiceServiceTest`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/service/PurchaseInvoiceService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseinvoice/service/PurchaseInvoiceServiceTest.java`

**Interfaces:**
- Consumes: `PurchaseInvoiceRepository`/`PurchaseInvoiceCounterRepository` (Task 2), `PurchaseInvoiceRequest`/`InstallmentInput`/`PurchaseInvoiceResponse`/`PurchaseInvoiceSummaryResponse`/`PurchaseInvoiceItemResponse` (Task 3), `PurchaseInvoiceValidationException`/`PurchaseInvoiceNotFoundException` (Task 3), `com.meshsuite.purchaseorder.repository.PurchaseOrderRepository` (existing), `com.meshsuite.purchaseorder.exception.PurchaseOrderNotFoundException` (existing, reused as-is — no new "order not found" type in this module), `com.meshsuite.fiscal.service.FiscalCalculationService.calculate(FiscalRegistration, BigDecimal quantity, BigDecimal unitPrice): FiscalCalculationResult` (existing), `com.meshsuite.stock.service.StockService.adjustBalance(UUID tenantId, UUID productId, StockMovementType type, BigDecimal quantity, StockMovementOrigin origin, UUID referenceId, UUID userId, String note): StockMovementResponse` (existing), `com.meshsuite.payable.service.AccountsPayableService.createInstallments(UUID tenantId, UUID supplierId, UUID referenceId, List<AccountsPayableInstallmentInput> installments): List<AccountsPayableResponse>` (existing), `EntityManager`.
- Produces: `PurchaseInvoiceService.issue(UUID purchaseOrderId, PurchaseInvoiceRequest request, UUID userId): PurchaseInvoiceResponse`, `PurchaseInvoiceService.list(String search, Pageable pageable): Page<PurchaseInvoiceSummaryResponse>`, `PurchaseInvoiceService.findById(UUID id): PurchaseInvoiceResponse`.

- [ ] **Step 1: Write the failing service tests**

```java
// mesh-suite-backend/src/test/java/com/meshsuite/purchaseinvoice/service/PurchaseInvoiceServiceTest.java
package com.meshsuite.purchaseinvoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertThrows(PurchaseInvoiceValidationException.class,
                () -> purchaseInvoiceService.issue(orderId, request("NF-1002", new BigDecimal("25.00")), callerId));
    }

    @Test
    void rejectsIssuingWhenProductHasNoFiscalRegistration() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoSemCadastroFiscal(tenantId, "P0002", new BigDecimal("25.00"));
        UUID orderId = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));

        assertThrows(PurchaseInvoiceValidationException.class,
                () -> purchaseInvoiceService.issue(orderId, request("NF-1001", new BigDecimal("25.00")), callerId));
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

        assertThrows(PurchaseInvoiceValidationException.class,
                () -> purchaseInvoiceService.issue(order2, request("NF-1001", new BigDecimal("25.00")), callerId));
    }

    @Test
    void rejectsWhenInstallmentsSumDoesNotMatchTotal() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        UUID buyerId = criarComprador(tenantId, "carlos@aurora.com.br");
        UUID productId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("25.00"));
        UUID orderId = criarOrdemAberta(tenantId, supplierId, buyerId, productId, BigDecimal.ONE, new BigDecimal("25.00"));

        assertThrows(PurchaseInvoiceValidationException.class,
                () -> purchaseInvoiceService.issue(orderId, request("NF-1001", new BigDecimal("20.00")), callerId));
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

        assertThrows(PurchaseInvoiceValidationException.class,
                () -> purchaseInvoiceService.issue(orderId, invalido, callerId));
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
```

- [ ] **Step 2: Run the tests to verify they fail to compile**

Run: `cd mesh-suite-backend && mvn -q test-compile`
Expected: `COMPILATION ERROR` — `PurchaseInvoiceService` does not exist.

- [ ] **Step 3: Implement `PurchaseInvoiceService`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/service/PurchaseInvoiceService.java
package com.meshsuite.purchaseinvoice.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.fiscal.dto.FiscalCalculationResult;
import com.meshsuite.fiscal.service.FiscalCalculationService;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.payable.dto.AccountsPayableInstallmentInput;
import com.meshsuite.payable.service.AccountsPayableService;
import com.meshsuite.product.domain.Product;
import com.meshsuite.purchaseinvoice.domain.PurchaseInvoice;
import com.meshsuite.purchaseinvoice.domain.PurchaseInvoiceItem;
import com.meshsuite.purchaseinvoice.dto.InstallmentInput;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceItemResponse;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceRequest;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceResponse;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceSummaryResponse;
import com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceNotFoundException;
import com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceValidationException;
import com.meshsuite.purchaseinvoice.repository.PurchaseInvoiceRepository;
import com.meshsuite.purchaseinvoice.repository.specification.PurchaseInvoiceSpecifications;
import com.meshsuite.purchaseorder.domain.PurchaseOrder;
import com.meshsuite.purchaseorder.domain.PurchaseOrderItem;
import com.meshsuite.purchaseorder.domain.enums.PurchaseOrderStatus;
import com.meshsuite.purchaseorder.exception.PurchaseOrderNotFoundException;
import com.meshsuite.purchaseorder.repository.PurchaseOrderRepository;
import com.meshsuite.stock.domain.enums.StockMovementOrigin;
import com.meshsuite.stock.domain.enums.StockMovementType;
import com.meshsuite.stock.service.StockService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final FiscalCalculationService fiscalCalculationService;
    private final StockService stockService;
    private final AccountsPayableService accountsPayableService;
    private final EntityManager entityManager;

    public PurchaseInvoiceService(PurchaseInvoiceRepository purchaseInvoiceRepository,
                                   PurchaseOrderRepository purchaseOrderRepository,
                                   FiscalCalculationService fiscalCalculationService,
                                   StockService stockService,
                                   AccountsPayableService accountsPayableService,
                                   EntityManager entityManager) {
        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.fiscalCalculationService = fiscalCalculationService;
        this.stockService = stockService;
        this.accountsPayableService = accountsPayableService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE_INVOICE, action = Action.VIEW)
    public Page<PurchaseInvoiceSummaryResponse> list(String search, Pageable pageable) {
        Specification<PurchaseInvoice> spec = Specification.where(PurchaseInvoiceSpecifications.withSearch(search));
        return purchaseInvoiceRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE_INVOICE, action = Action.VIEW)
    public PurchaseInvoiceResponse findById(UUID id) {
        return toResponse(purchaseInvoiceRepository.findById(id).orElseThrow(PurchaseInvoiceNotFoundException::new));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE_INVOICE, action = Action.CREATE)
    public PurchaseInvoiceResponse issue(UUID purchaseOrderId, PurchaseInvoiceRequest request, UUID userId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        if (order.getStatus() != PurchaseOrderStatus.OPEN) {
            throw new PurchaseInvoiceValidationException(
                    "Só é possível lançar uma compra a partir de uma ordem em aberto. Status atual: " + order.getStatus());
        }

        Partner supplier = order.getSupplier();
        if (purchaseInvoiceRepository.findBySupplierIdAndInvoiceNumber(supplier.getId(), request.invoiceNumber()).isPresent()) {
            throw new PurchaseInvoiceValidationException(
                    "Já existe uma nota " + request.invoiceNumber() + " cadastrada para este fornecedor");
        }
        if (request.entryDate().isBefore(request.issueDate())) {
            throw new PurchaseInvoiceValidationException("A data de entrada não pode ser anterior à data de emissão");
        }

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setTenantId(order.getTenantId());
        invoice.setNumber(nextNumber(order.getTenantId()));
        invoice.setInvoiceNumber(request.invoiceNumber());
        invoice.setSeries(request.series());
        invoice.setModel(request.model());
        invoice.setPurchaseOrder(order);
        invoice.setSupplier(supplier);
        invoice.setIssueDate(request.issueDate());
        invoice.setEntryDate(request.entryDate());
        invoice.setDiscount(order.getDiscount());
        invoice.setSubtotal(order.getSubtotal());
        invoice.setTotal(order.getTotal());

        BigDecimal totalIcms = BigDecimal.ZERO;
        BigDecimal totalIpi = BigDecimal.ZERO;
        BigDecimal totalPis = BigDecimal.ZERO;
        BigDecimal totalCofins = BigDecimal.ZERO;

        for (PurchaseOrderItem orderItem : order.getItems()) {
            Product product = orderItem.getProduct();
            if (product.getFiscalRegistration() == null) {
                throw new PurchaseInvoiceValidationException(
                        "O produto " + product.getName() + " não possui cadastro fiscal aplicado");
            }
            FiscalCalculationResult calculation = fiscalCalculationService.calculate(
                    product.getFiscalRegistration(), orderItem.getQuantity(), orderItem.getUnitPrice());

            PurchaseInvoiceItem invoiceItem = new PurchaseInvoiceItem();
            invoiceItem.setPurchaseInvoice(invoice);
            invoiceItem.setProduct(product);
            invoiceItem.setQuantity(orderItem.getQuantity());
            invoiceItem.setUnitPrice(orderItem.getUnitPrice());
            invoiceItem.setTotalValue(orderItem.getTotalValue());
            invoiceItem.setIcmsAmount(calculation.icmsValue());
            invoiceItem.setIpiAmount(calculation.ipiValue());
            invoiceItem.setPisAmount(calculation.pisValue());
            invoiceItem.setCofinsAmount(calculation.cofinsValue());
            invoice.getItems().add(invoiceItem);

            totalIcms = totalIcms.add(calculation.icmsValue());
            totalIpi = totalIpi.add(calculation.ipiValue());
            totalPis = totalPis.add(calculation.pisValue());
            totalCofins = totalCofins.add(calculation.cofinsValue());
        }

        invoice.setIcmsAmount(totalIcms);
        invoice.setIpiAmount(totalIpi);
        invoice.setPisAmount(totalPis);
        invoice.setCofinsAmount(totalCofins);

        BigDecimal installmentsTotal = request.installments().stream()
                .map(InstallmentInput::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (installmentsTotal.compareTo(invoice.getTotal()) != 0) {
            throw new PurchaseInvoiceValidationException(
                    "A soma das parcelas (" + installmentsTotal + ") não bate com o total da nota (" + invoice.getTotal() + ")");
        }

        PurchaseInvoice saved = purchaseInvoiceRepository.saveAndFlush(invoice);

        for (PurchaseInvoiceItem item : saved.getItems()) {
            stockService.adjustBalance(order.getTenantId(), item.getProduct().getId(), StockMovementType.INBOUND,
                    item.getQuantity(), StockMovementOrigin.PURCHASE, saved.getId(), userId, null);
        }

        List<AccountsPayableInstallmentInput> payableInstallments = request.installments().stream()
                .map(i -> new AccountsPayableInstallmentInput(i.amount(), i.dueDate()))
                .toList();
        accountsPayableService.createInstallments(order.getTenantId(), supplier.getId(), saved.getId(), payableInstallments);

        order.setStatus(PurchaseOrderStatus.RECEIVED);
        purchaseOrderRepository.saveAndFlush(order);

        return toResponse(saved);
    }

    // Atomic UPDATE ... RETURNING against the tenant's single
    // purchase_invoice_counter row -- never COUNT(*)/MAX(number)+1, both of which
    // race under concurrent inserts. Same pattern as every other counter in this
    // codebase (PurchaseOrder, Sale, AccountsPayable).
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO purchase_invoice_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE purchase_invoice_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private PurchaseInvoiceSummaryResponse toSummary(PurchaseInvoice i) {
        return new PurchaseInvoiceSummaryResponse(i.getId(), i.getNumber(), i.getInvoiceNumber(),
                i.getSupplier().getTradeName(), i.getIssueDate(), i.getTotal());
    }

    private PurchaseInvoiceResponse toResponse(PurchaseInvoice i) {
        List<PurchaseInvoiceItemResponse> items = i.getItems().stream()
                .map(it -> new PurchaseInvoiceItemResponse(it.getProduct().getId(), it.getProduct().getName(),
                        it.getQuantity(), it.getUnitPrice(), it.getTotalValue(),
                        it.getIcmsAmount(), it.getIpiAmount(), it.getPisAmount(), it.getCofinsAmount()))
                .toList();
        return new PurchaseInvoiceResponse(i.getId(), i.getNumber(), i.getInvoiceNumber(), i.getSeries(), i.getModel(),
                i.getPurchaseOrder().getId(), i.getPurchaseOrder().getNumber(),
                i.getSupplier().getId(), i.getSupplier().getTradeName(),
                i.getIssueDate(), i.getEntryDate(),
                i.getDiscount(), i.getSubtotal(), i.getTotal(),
                i.getIcmsAmount(), i.getIpiAmount(), i.getPisAmount(), i.getCofinsAmount(), items);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PurchaseInvoiceServiceTest`
Expected: `BUILD SUCCESS`, 8 tests passed.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/service/PurchaseInvoiceService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/purchaseinvoice/service/PurchaseInvoiceServiceTest.java
git commit -m "feat(purchase-invoice): add PurchaseInvoiceService.issue wiring stock and accounts payable"
```

---

### Task 6: `PurchaseInvoiceController` + `PurchaseInvoiceControllerTest`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/controller/PurchaseInvoiceController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/exception/PurchaseInvoiceExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseinvoice/controller/PurchaseInvoiceControllerTest.java`

**Interfaces:**
- Consumes: `PurchaseInvoiceService.issue/list/findById` (Task 5).
- Produces: `POST /api/purchase-invoices/issue/{purchaseOrderId}` (201, `PurchaseInvoiceResponse`), `GET /api/purchase-invoices` (200, `Page<PurchaseInvoiceSummaryResponse>`), `GET /api/purchase-invoices/{id}` (200, `PurchaseInvoiceResponse`).

- [ ] **Step 1: Write the failing controller test**

```java
// mesh-suite-backend/src/test/java/com/meshsuite/purchaseinvoice/controller/PurchaseInvoiceControllerTest.java
package com.meshsuite.purchaseinvoice.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.fiscal.repository.FiscalRegistrationRepository;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class PurchaseInvoiceControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Context(String cookie, String supplierId, String buyerId, String productId) {
    }

    private Context loginAndSetUp(String code, String email, String companyCnpj) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(code);
        tenant.setNome(code);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName(code + " Ltda");
        company.setCnpj(companyCnpj);
        companyRepository.saveAndFlush(company);

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
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE_INVOICE, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE_INVOICE, Action.CREATE));
        // The login user doubles as the PurchaseOrder's buyer, same as the
        // existing PurchaseOrderControllerTest -- ADMINISTRATIVE role satisfies
        // PurchaseOrderService.findValidBuyer's role check.
        User savedBuyer = userRepository.saveAndFlush(userLogin);

        Partner supplier = new Partner();
        supplier.setTenantId(tenant.getId());
        supplier.setPersonType(PersonType.LEGAL_ENTITY);
        supplier.setDocument(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        supplier.setTradeName("Tecidos Aurora");
        supplier.getRoles().add(PartnerRole.SUPPLIER);
        partnerRepository.saveAndFlush(supplier);

        FiscalRegistration registration = new FiscalRegistration();
        registration.setTenantId(tenant.getId());
        registration.setDescription("Compra dentro do estado");
        registration.setCfop("1102");
        registration.setIcmsCst("000");
        registration.setIcmsRate(new BigDecimal("18.00"));
        registration.setIpiRate(new BigDecimal("5.00"));
        registration.setPisRate(new BigDecimal("1.65"));
        registration.setCofinsRate(new BigDecimal("7.60"));
        fiscalRegistrationRepository.saveAndFlush(registration);

        Product product = new Product();
        product.setTenantId(tenant.getId());
        product.setName("Tecido Algodão");
        product.setSku("P0001-" + code);
        product.setSalePrice(new BigDecimal("100.00"));
        product.setFiscalRegistration(registration);
        productRepository.saveAndFlush(product);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Context(token, supplier.getId().toString(), savedBuyer.getId().toString(), product.getId().toString());
    }

    private String createOpenPurchaseOrder(Context ctx, Cookie cookie) throws Exception {
        String created = mockMvc.perform(post("/api/purchase-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "buyerId": "%s",
                                  "discount": 0,
                                  "items": [ { "productId": "%s", "quantity": 2, "unitPrice": 100.00 } ]
                                }
                                """.formatted(ctx.supplierId(), ctx.buyerId(), ctx.productId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(created, "$.id");
    }

    private String issuePayload() {
        return """
                {
                  "invoiceNumber": "NF-1001",
                  "series": "1",
                  "model": "55",
                  "issueDate": "2026-08-10",
                  "entryDate": "2026-08-12",
                  "installments": [ { "amount": 200.00, "dueDate": "2026-09-10" } ]
                }
                """;
    }

    @Test
    void issuesListsAndFindsPurchaseInvoice() throws Exception {
        Context ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        String orderId = createOpenPurchaseOrder(ctx, cookie);

        String created = mockMvc.perform(post("/api/purchase-invoices/issue/" + orderId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.purchaseOrderId").value(orderId))
                .andExpect(jsonPath("$.total").value(200.00))
                .andReturn().getResponse().getContentAsString();
        String invoiceId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/purchase-invoices").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(1));

        mockMvc.perform(get("/api/purchase-invoices/" + invoiceId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierName").value("Tecidos Aurora"));

        mockMvc.perform(get("/api/purchase-orders/" + orderId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void issuingWithMismatchedInstallmentsIsBadRequest() throws Exception {
        Context ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        String orderId = createOpenPurchaseOrder(ctx, cookie);

        mockMvc.perform(post("/api/purchase-invoices/issue/" + orderId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceNumber": "NF-1001",
                                  "series": "1",
                                  "model": "55",
                                  "issueDate": "2026-08-10",
                                  "entryDate": "2026-08-12",
                                  "installments": [ { "amount": 50.00, "dueDate": "2026-09-10" } ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issuingTwiceForTheSameOrderIsBadRequest() throws Exception {
        Context ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        String orderId = createOpenPurchaseOrder(ctx, cookie);

        mockMvc.perform(post("/api/purchase-invoices/issue/" + orderId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/purchase-invoices/issue/" + orderId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceNumber": "NF-1002",
                                  "series": "1",
                                  "model": "55",
                                  "issueDate": "2026-08-10",
                                  "entryDate": "2026-08-12",
                                  "installments": [ { "amount": 200.00, "dueDate": "2026-09-10" } ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issuingWithoutPurchaseInvoicePermissionIsForbidden() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao-compra");
        tenant.setNome("sem-permissao-compra");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("sem-permissao-compra Ltda");
        company.setCnpj("99888777000166");
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Sem Permissão");
        user.setEmail("sem-permissao-compra@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.VIEWER);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sem-permissao-compra@aurora.com.br\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/purchase-invoices").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

Run: `cd mesh-suite-backend && mvn -q test-compile`
Expected: `COMPILATION ERROR` — `PurchaseInvoiceController` does not exist.

- [ ] **Step 3: Implement `PurchaseInvoiceController`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/controller/PurchaseInvoiceController.java
package com.meshsuite.purchaseinvoice.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceRequest;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceResponse;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceSummaryResponse;
import com.meshsuite.purchaseinvoice.service.PurchaseInvoiceService;
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
@RequestMapping("/api/purchase-invoices")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService purchaseInvoiceService;

    public PurchaseInvoiceController(PurchaseInvoiceService purchaseInvoiceService) {
        this.purchaseInvoiceService = purchaseInvoiceService;
    }

    @GetMapping
    public Page<PurchaseInvoiceSummaryResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "number", direction = Sort.Direction.DESC) Pageable pageable) {
        return purchaseInvoiceService.list(search, pageable);
    }

    @GetMapping("/{id}")
    public PurchaseInvoiceResponse findById(@PathVariable UUID id) {
        return purchaseInvoiceService.findById(id);
    }

    @PostMapping("/issue/{purchaseOrderId}")
    public ResponseEntity<PurchaseInvoiceResponse> issue(@PathVariable UUID purchaseOrderId,
                                                           @AuthenticationPrincipal AuthContextService.Context principal,
                                                           @Valid @RequestBody PurchaseInvoiceRequest request) {
        PurchaseInvoiceResponse response = purchaseInvoiceService.issue(purchaseOrderId, request, principal.usuarioId());
        return ResponseEntity.status(201).body(response);
    }
}
```

- [ ] **Step 4: Implement `PurchaseInvoiceExceptionHandler`** (protects against the race where two concurrent `issue` calls both pass the `OPEN` check before either commits, hitting `purchase_invoice.purchase_order_id`'s `UNIQUE` constraint — same reasoning as `SaleExceptionHandler`)

```java
// mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/exception/PurchaseInvoiceExceptionHandler.java
package com.meshsuite.purchaseinvoice.exception;

import com.meshsuite.purchaseinvoice.controller.PurchaseInvoiceController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PurchaseInvoiceController.class)
public class PurchaseInvoiceExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Não foi possível lançar a compra. Tente novamente."));
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PurchaseInvoiceControllerTest`
Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 6: Run the full backend suite to confirm no regressions**

Run: `cd mesh-suite-backend && mvn -q clean test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/controller/PurchaseInvoiceController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/purchaseinvoice/exception/PurchaseInvoiceExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/purchaseinvoice/controller/PurchaseInvoiceControllerTest.java
git commit -m "feat(purchase-invoice): add PurchaseInvoiceController with issue/list/findById endpoints"
```

---

### Task 7: Frontend — `src/api/purchaseInvoices.ts`

**Files:**
- Create: `mesh-suite-frontend/src/api/purchaseInvoices.ts`

**Interfaces:**
- Consumes: `apiClient` from `./client` (existing, same as every other `src/api/*.ts` file).
- Produces: `InstallmentInput`, `PurchaseInvoiceItemResponse`, `PurchaseInvoiceResponse`, `PurchaseInvoiceSummary`, `PurchaseInvoiceRequest`, `Page<T>` types; `listPurchaseInvoices(params): Promise<Page<PurchaseInvoiceSummary>>`, `getPurchaseInvoice(id): Promise<PurchaseInvoiceResponse>`, `issuePurchaseInvoice(purchaseOrderId, payload): Promise<PurchaseInvoiceResponse>`.

- [ ] **Step 1: Create the API module**

```typescript
// mesh-suite-frontend/src/api/purchaseInvoices.ts
import { apiClient } from './client'

export interface InstallmentInput {
  amount: number
  dueDate: string
}

export interface PurchaseInvoiceItemResponse {
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  totalValue: number
  icmsAmount: number
  ipiAmount: number
  pisAmount: number
  cofinsAmount: number
}

export interface PurchaseInvoiceResponse {
  id: string
  number: number
  invoiceNumber: string
  series: string
  model: string
  purchaseOrderId: string
  purchaseOrderNumber: number
  supplierId: string
  supplierName: string
  issueDate: string
  entryDate: string
  discount: number
  subtotal: number
  total: number
  icmsAmount: number
  ipiAmount: number
  pisAmount: number
  cofinsAmount: number
  items: PurchaseInvoiceItemResponse[]
}

export interface PurchaseInvoiceSummary {
  id: string
  number: number
  invoiceNumber: string
  supplierName: string
  issueDate: string
  total: number
}

export interface PurchaseInvoiceRequest {
  invoiceNumber: string
  series: string
  model: string
  issueDate: string
  entryDate: string
  installments: InstallmentInput[]
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListPurchaseInvoicesParams {
  search?: string
  page?: number
  size?: number
  sort?: string
}

export async function listPurchaseInvoices(params: ListPurchaseInvoicesParams): Promise<Page<PurchaseInvoiceSummary>> {
  const { data } = await apiClient.get<Page<PurchaseInvoiceSummary>>('/purchase-invoices', { params })
  return data
}

export async function getPurchaseInvoice(id: string): Promise<PurchaseInvoiceResponse> {
  const { data } = await apiClient.get<PurchaseInvoiceResponse>(`/purchase-invoices/${id}`)
  return data
}

export async function issuePurchaseInvoice(purchaseOrderId: string, payload: PurchaseInvoiceRequest): Promise<PurchaseInvoiceResponse> {
  const { data } = await apiClient.post<PurchaseInvoiceResponse>(`/purchase-invoices/issue/${purchaseOrderId}`, payload)
  return data
}
```

- [ ] **Step 2: Verify it type-checks**

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit`
Expected: no errors mentioning `src/api/purchaseInvoices.ts`.

- [ ] **Step 3: Commit**

```bash
git add mesh-suite-frontend/src/api/purchaseInvoices.ts
git commit -m "feat(purchase-invoice): add purchaseInvoices API client module"
```

---

### Task 8: Frontend — `PurchaseInvoicesListView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/PurchaseInvoicesListView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/PurchaseInvoicesListView.spec.ts`

**Interfaces:**
- Consumes: `listPurchaseInvoices` (Task 7), `AppShell`, `Pagination` components (existing, same imports as `SalesListView.vue`).
- Produces: `PurchaseInvoicesListView` component, mounted standalone (no route registered yet — that's Task 10).

- [ ] **Step 1: Write the failing component test**

```typescript
// mesh-suite-frontend/src/views/__tests__/PurchaseInvoicesListView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PurchaseInvoicesListView from '@/views/PurchaseInvoicesListView.vue'
import * as purchaseInvoicesApi from '@/api/purchaseInvoices'

vi.mock('@/api/purchaseInvoices')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/notas-fiscais-entrada', name: 'notas-fiscais-entrada', component: PurchaseInvoicesListView }],
  })
  router.push('/notas-fiscais-entrada')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PurchaseInvoicesListView, { global: { plugins: [router] } }),
  }))
}

const invoice = {
  id: 'pi1', number: 1, invoiceNumber: 'NF-1001', supplierName: 'Tecidos Aurora', issueDate: '2026-08-10', total: 200.0,
}

describe('PurchaseInvoicesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(purchaseInvoicesApi.listPurchaseInvoices).mockResolvedValue({
      content: [invoice], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the purchase invoice list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('NF-1001')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('aurora')
    await flushPromises()

    expect(purchaseInvoicesApi.listPurchaseInvoices).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'aurora' }))
  })

  it('shows an empty state when there are no purchase invoices', async () => {
    vi.mocked(purchaseInvoicesApi.listPurchaseInvoices).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma compra para exibir.')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(purchaseInvoicesApi.listPurchaseInvoices).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de compras.')
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run PurchaseInvoicesListView`
Expected: FAIL — `Failed to resolve import "@/views/PurchaseInvoicesListView.vue"`.

- [ ] **Step 3: Implement `PurchaseInvoicesListView.vue`**

```vue
<!-- mesh-suite-frontend/src/views/PurchaseInvoicesListView.vue -->
<template>
  <AppShell title="Notas de Entrada">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº ou fornecedor..."
        data-test="busca"
        @input="carregar(0)"
      />
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Compras</span>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nº</div>
          <div class="table-grid-col">Nota Fiscal</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-fornecedor" @click="toggleSort('supplierName')">
            Fornecedor
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'supplierName' }">{{ sortIcon('supplierName') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-data" @click="toggleSort('issueDate')">
            Data de Emissão
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'issueDate' }">{{ sortIcon('issueDate') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-total" @click="toggleSort('total')">
            Total
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'total' }">{{ sortIcon('total') }}</span>
          </div>
        </div>

        <div v-for="invoice in pagina.content" :key="invoice.id" class="table-grid-row" :data-test="`row-${invoice.id}`">
          <div class="table-grid-cell">{{ invoice.number }}</div>
          <div class="table-grid-cell">{{ invoice.invoiceNumber }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ invoice.supplierName }}</div>
          <div class="table-grid-cell">{{ formatarData(invoice.issueDate) }}</div>
          <div class="table-grid-cell">{{ formatarPreco(invoice.total) }}</div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma compra para exibir.</p>
    </section>

    <Pagination
      :number="pagina.number"
      :total-pages="pagina.totalPages"
      :total-elements="pagina.totalElements"
      :size="pagina.size"
      @update:page="carregar"
      @update:size="onSizeChange"
    />
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import Pagination from '@/components/Pagination.vue'
import { listPurchaseInvoices, type PurchaseInvoiceSummary, type Page as ApiPage } from '@/api/purchaseInvoices'

const filtros = reactive({ busca: '' })
const pagina = ref<ApiPage<PurchaseInvoiceSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const sortField = ref<'supplierName' | 'issueDate' | 'total' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function sortIcon(field: 'supplierName' | 'issueDate' | 'total') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'supplierName' | 'issueDate' | 'total') {
  if (sortField.value === field) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortDir.value = 'asc'
  }
  carregar(0)
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listPurchaseInvoices({
      search: filtros.busca || undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de compras.'
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

onMounted(() => {
  carregar(0)
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

.toolbar input {
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
  font-family: var(--pm-font);
}

.table-card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.table-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 60px 120px 1fr 150px 110px;
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

.table-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}
</style>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd mesh-suite-frontend && npx vitest run PurchaseInvoicesListView`
Expected: all 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/PurchaseInvoicesListView.vue mesh-suite-frontend/src/views/__tests__/PurchaseInvoicesListView.spec.ts
git commit -m "feat(purchase-invoice): add read-only PurchaseInvoicesListView"
```

---

### Task 9: Frontend — `PurchaseInvoiceFormView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/PurchaseInvoiceFormView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/PurchaseInvoiceFormView.spec.ts`

**Interfaces:**
- Consumes: `getPurchaseOrder` (from `@/api/purchaseOrders`, existing), `issuePurchaseInvoice`/`PurchaseInvoiceRequest`/`InstallmentInput` (Task 7), `AppShell` component (existing).
- Produces: `PurchaseInvoiceFormView` component, reads `route.params.id` (the `PurchaseOrder` id) to load the order and drives the issuance. No route registered yet — that's Task 10.

- [ ] **Step 1: Write the failing component test**

```typescript
// mesh-suite-frontend/src/views/__tests__/PurchaseInvoiceFormView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PurchaseInvoiceFormView from '@/views/PurchaseInvoiceFormView.vue'
import * as purchaseOrdersApi from '@/api/purchaseOrders'
import * as purchaseInvoicesApi from '@/api/purchaseInvoices'

vi.mock('@/api/purchaseOrders')
vi.mock('@/api/purchaseInvoices')

const order = {
  id: 'po1', number: 7, supplierId: 's1', supplierName: 'Tecidos Aurora', buyerId: 'b1', buyerName: 'Carlos Comprador',
  orderDate: '2026-08-01', expectedDeliveryDate: null, status: 'OPEN' as const, discount: 0, subtotal: 200, total: 200,
  items: [{ productId: 'p1', productName: 'Tecido Algodão', quantity: 2, unitPrice: 100, totalValue: 200 }],
}

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/compras/:id/nota-fiscal', name: 'compras-nota-fiscal', component: PurchaseInvoiceFormView },
      { path: '/compras', name: 'compras', component: { template: '<div />' } },
    ],
  })
  router.push('/compras/po1/nota-fiscal')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PurchaseInvoiceFormView, { global: { plugins: [router] } }),
  }))
}

describe('PurchaseInvoiceFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(purchaseOrdersApi.getPurchaseOrder).mockResolvedValue(order)
  })

  it('loads the purchase order and shows its read-only items and total', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('Tecido Algodão')
    expect(wrapper.text()).toContain('R$ 200,00')
  })

  it('keeps the submit button disabled until the installments sum matches the total', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nota-numero"]').setValue('NF-1001')
    await wrapper.find('[data-test="nota-serie"]').setValue('1')
    await wrapper.find('[data-test="nota-modelo"]').setValue('55')
    await wrapper.find('[data-test="nota-data-emissao"]').setValue('2026-08-10')
    await wrapper.find('[data-test="nota-data-entrada"]').setValue('2026-08-12')

    expect(wrapper.find('[data-test="salvar"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="parcela-adicionar"]').trigger('click')
    await wrapper.find('[data-test="parcela-valor-0"]').setValue('100')
    await wrapper.find('[data-test="parcela-vencimento-0"]').setValue('2026-09-01')
    await wrapper.find('[data-test="parcela-adicionar"]').trigger('click')
    await wrapper.find('[data-test="parcela-valor-1"]').setValue('100')
    await wrapper.find('[data-test="parcela-vencimento-1"]').setValue('2026-10-01')

    expect(wrapper.find('[data-test="salvar"]').attributes('disabled')).toBeUndefined()
  })

  it('issues the purchase invoice and navigates to the list on success', async () => {
    vi.mocked(purchaseInvoicesApi.issuePurchaseInvoice).mockResolvedValue({} as never)
    const { wrapper, router } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nota-numero"]').setValue('NF-1001')
    await wrapper.find('[data-test="nota-serie"]').setValue('1')
    await wrapper.find('[data-test="nota-modelo"]').setValue('55')
    await wrapper.find('[data-test="nota-data-emissao"]').setValue('2026-08-10')
    await wrapper.find('[data-test="nota-data-entrada"]').setValue('2026-08-12')
    await wrapper.find('[data-test="parcela-adicionar"]').trigger('click')
    await wrapper.find('[data-test="parcela-valor-0"]').setValue('200')
    await wrapper.find('[data-test="parcela-vencimento-0"]').setValue('2026-09-10')

    await wrapper.find('[data-test="salvar"]').trigger('click')
    await flushPromises()

    expect(purchaseInvoicesApi.issuePurchaseInvoice).toHaveBeenCalledWith('po1', {
      invoiceNumber: 'NF-1001',
      series: '1',
      model: '55',
      issueDate: '2026-08-10',
      entryDate: '2026-08-12',
      installments: [{ amount: 200, dueDate: '2026-09-10' }],
    })
    expect(router.currentRoute.value.name).toBe('compras')
  })

  it('shows an error message when issuing fails', async () => {
    vi.mocked(purchaseInvoicesApi.issuePurchaseInvoice).mockRejectedValue(new Error('boom'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nota-numero"]').setValue('NF-1001')
    await wrapper.find('[data-test="nota-serie"]').setValue('1')
    await wrapper.find('[data-test="nota-modelo"]').setValue('55')
    await wrapper.find('[data-test="nota-data-emissao"]').setValue('2026-08-10')
    await wrapper.find('[data-test="nota-data-entrada"]').setValue('2026-08-12')
    await wrapper.find('[data-test="parcela-adicionar"]').trigger('click')
    await wrapper.find('[data-test="parcela-valor-0"]').setValue('200')
    await wrapper.find('[data-test="parcela-vencimento-0"]').setValue('2026-09-10')

    await wrapper.find('[data-test="salvar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível lançar a compra.')
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run PurchaseInvoiceFormView`
Expected: FAIL — `Failed to resolve import "@/views/PurchaseInvoiceFormView.vue"`.

- [ ] **Step 3: Implement `PurchaseInvoiceFormView.vue`**

```vue
<!-- mesh-suite-frontend/src/views/PurchaseInvoiceFormView.vue -->
<template>
  <AppShell title="Lançar Compra">
    <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

    <section v-if="order" class="card">
      <h3 class="card-title">Ordem de Compra nº {{ order.number }}</h3>
      <div class="resumo-ordem">
        <div><span>Fornecedor</span><span>{{ order.supplierName }}</span></div>
        <div><span>Total</span><span>{{ formatarPreco(order.total) }}</span></div>
      </div>

      <table class="tabela-itens">
        <thead>
          <tr>
            <th>Produto</th>
            <th>Qtd.</th>
            <th>Valor Unit.</th>
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in order.items" :key="item.productId">
            <td>{{ item.productName }}</td>
            <td>{{ item.quantity }}</td>
            <td>{{ formatarPreco(item.unitPrice) }}</td>
            <td>{{ formatarPreco(item.totalValue) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="order" class="card">
      <h3 class="card-title">Dados da Nota Fiscal</h3>
      <div class="campos-nota">
        <label>
          Número
          <input v-model="form.invoiceNumber" data-test="nota-numero" />
        </label>
        <label>
          Série
          <input v-model="form.series" data-test="nota-serie" />
        </label>
        <label>
          Modelo
          <input v-model="form.model" data-test="nota-modelo" />
        </label>
        <label>
          Data de Emissão
          <input v-model="form.issueDate" type="date" data-test="nota-data-emissao" />
        </label>
        <label>
          Data de Entrada
          <input v-model="form.entryDate" type="date" data-test="nota-data-entrada" />
        </label>
      </div>
    </section>

    <section v-if="order" class="card">
      <h3 class="card-title">Parcelas</h3>
      <button type="button" class="btn-secondary" data-test="parcela-adicionar" @click="adicionarParcela">+ Adicionar Parcela</button>

      <table v-if="form.installments.length" class="tabela-itens">
        <thead>
          <tr>
            <th>Valor</th>
            <th>Vencimento</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(parcela, index) in form.installments" :key="index">
            <td>
              <input v-model.number="parcela.amount" type="number" step="0.01" min="0" :data-test="`parcela-valor-${index}`" />
            </td>
            <td>
              <input v-model="parcela.dueDate" type="date" :data-test="`parcela-vencimento-${index}`" />
            </td>
            <td><button type="button" class="btn-remover" :data-test="`parcela-remover-${index}`" @click="removerParcela(index)">✕</button></td>
          </tr>
        </tbody>
      </table>

      <div class="totais">
        <div><span>Soma das parcelas</span><span :data-test="'soma-parcelas'">{{ formatarPreco(somaParcelas) }}</span></div>
        <div><span>Total da nota</span><span>{{ formatarPreco(order.total) }}</span></div>
      </div>
      <p v-if="form.installments.length && !parcelasBatem" class="field-error">
        A soma das parcelas precisa ser igual ao total da nota.
      </p>
    </section>

    <div v-if="order" class="acoes">
      <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
      <button type="button" class="btn-primary" data-test="salvar" :disabled="!podeSalvar" @click="salvar">Salvar</button>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { getPurchaseOrder, type PurchaseOrderResponse } from '@/api/purchaseOrders'
import { issuePurchaseInvoice, type InstallmentInput } from '@/api/purchaseInvoices'

const route = useRoute()
const router = useRouter()

const order = ref<PurchaseOrderResponse | null>(null)
const erroGeral = ref('')
const salvando = ref(false)

interface FormState {
  invoiceNumber: string
  series: string
  model: string
  issueDate: string
  entryDate: string
  installments: InstallmentInput[]
}

const form = reactive<FormState>({
  invoiceNumber: '',
  series: '',
  model: '',
  issueDate: '',
  entryDate: '',
  installments: [],
})

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

const somaParcelas = computed(() =>
  form.installments.reduce((soma, p) => soma + (Number(p.amount) || 0), 0),
)

// Same HALF_UP-at-2-decimals comparison the backend does with BigDecimal --
// floating point sums can land a cent off (e.g. 0.1 + 0.2), so comparing
// rounded cents avoids a false mismatch that would never reproduce server-side.
const parcelasBatem = computed(() => {
  if (!order.value) {
    return false
  }
  return Math.round(somaParcelas.value * 100) === Math.round(order.value.total * 100)
})

const podeSalvar = computed(() =>
  !salvando.value &&
  form.invoiceNumber.trim() !== '' &&
  form.series.trim() !== '' &&
  form.model.trim() !== '' &&
  form.issueDate !== '' &&
  form.entryDate !== '' &&
  form.installments.length > 0 &&
  parcelasBatem.value,
)

function adicionarParcela() {
  form.installments.push({ amount: 0, dueDate: '' })
}

function removerParcela(index: number) {
  form.installments.splice(index, 1)
}

function cancelar() {
  router.push({ name: 'compras' })
}

async function salvar() {
  erroGeral.value = ''
  salvando.value = true
  try {
    await issuePurchaseInvoice(route.params.id as string, {
      invoiceNumber: form.invoiceNumber,
      series: form.series,
      model: form.model,
      issueDate: form.issueDate,
      entryDate: form.entryDate,
      installments: form.installments,
    })
    router.push({ name: 'compras' })
  } catch {
    erroGeral.value = 'Não foi possível lançar a compra.'
  } finally {
    salvando.value = false
  }
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id !== 'string') {
    return
  }
  try {
    order.value = await getPurchaseOrder(id)
  } catch {
    erroGeral.value = 'Não foi possível carregar a ordem de compra.'
  }
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  font-family: var(--pm-font);
}

.card-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0 0 12px;
}

.resumo-ordem {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  font-size: 13px;
}

.resumo-ordem div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.resumo-ordem span:first-child {
  color: var(--pm-text-mid);
  font-size: 11px;
  text-transform: uppercase;
}

.campos-nota {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.campos-nota label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--pm-text-mid);
}

.campos-nota input,
.tabela-itens input {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  background: var(--pm-white);
}

.tabela-itens {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-top: 12px;
}

.tabela-itens th {
  text-align: left;
  font-size: 11px;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  padding: 6px 8px;
  border-bottom: 1px solid var(--pm-border-light);
}

.tabela-itens td {
  padding: 6px 8px;
  border-bottom: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.btn-remover {
  background: none;
  border: none;
  color: var(--pm-error);
  cursor: pointer;
  font-size: 13px;
}

.totais {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
  font-size: 13px;
}

.totais > div {
  display: flex;
  justify-content: space-between;
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 8px 0 0;
}

.acoes {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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
  font-family: var(--pm-font);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: var(--pm-font);
}
</style>
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run PurchaseInvoiceFormView`
Expected: all 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/PurchaseInvoiceFormView.vue mesh-suite-frontend/src/views/__tests__/PurchaseInvoiceFormView.spec.ts
git commit -m "feat(purchase-invoice): add PurchaseInvoiceFormView with dynamic installments"
```

---

### Task 10: Frontend — router + sidebar entries

**Files:**
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Modify: `mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts`

**Interfaces:**
- Consumes: `PurchaseInvoicesListView` (Task 8), `PurchaseInvoiceFormView` (Task 9).
- Produces: routes `{ path: '/compras/:id/nota-fiscal', name: 'compras-nota-fiscal', component: PurchaseInvoiceFormView }` and `{ path: '/notas-fiscais-entrada', name: 'notas-fiscais-entrada', component: PurchaseInvoicesListView }`; sidebar nav item `Notas de Entrada` under the `compras` group.

- [ ] **Step 1: Write the failing sidebar test**

`AppSidebar.spec.ts` already has a test `'navigates to /compras when Compras is clicked'` that builds its own self-contained router (not the shared top-level `mountWithRouter`, which doesn't register `/compras`) — add a new test right after it, following the exact same shape:

```typescript
  it('navigates to /notas-fiscais-entrada when Notas de Entrada is clicked', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/notas-fiscais-entrada', name: 'notas-fiscais-entrada', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    await wrapper.find('[data-test="group-compras"]').trigger('click')
    await wrapper.find('[data-test="nav-Notas de Entrada"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/notas-fiscais-entrada')
  })
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run AppSidebar`
Expected: FAIL — `[data-test="nav-Notas de Entrada"]` not found.

- [ ] **Step 3: Register the routes**

In `mesh-suite-frontend/src/router/index.ts`, add the imports next to `PurchaseOrdersListView`:

```typescript
import PurchaseInvoicesListView from '@/views/PurchaseInvoicesListView.vue'
import PurchaseInvoiceFormView from '@/views/PurchaseInvoiceFormView.vue'
```

and add the routes next to the `compras` routes:

```typescript
    { path: '/compras/:id/nota-fiscal', name: 'compras-nota-fiscal', component: PurchaseInvoiceFormView },
    { path: '/notas-fiscais-entrada', name: 'notas-fiscais-entrada', component: PurchaseInvoicesListView },
```

- [ ] **Step 4: Add the sidebar item**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, change the `compras` group's `items` array from:

```typescript
    items: [{ icon: '📥', label: 'Compras', route: '/compras' }],
```

to:

```typescript
    items: [
      { icon: '📥', label: 'Compras', route: '/compras' },
      { icon: '🧾', label: 'Notas de Entrada', route: '/notas-fiscais-entrada' },
    ],
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd mesh-suite-frontend && npx vitest run AppSidebar`
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/router/index.ts mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts
git commit -m "feat(purchase-invoice): register purchase invoice routes and sidebar entry"
```

---

### Task 11: Frontend — "Lançar Compra" action on `PurchaseOrdersListView.vue`

**Files:**
- Modify: `mesh-suite-frontend/src/views/PurchaseOrdersListView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/PurchaseOrdersListView.spec.ts`

**Interfaces:**
- Consumes: nothing new at the API layer — this is pure navigation, unlike Sale's one-click `faturarPedido` (Compra needs the user to fill in note data and installments first, so the action navigates to `PurchaseInvoiceFormView` instead of calling an API directly).
- Produces: for an order with `status === 'OPEN'`, the Ações menu now shows a "Lançar Compra" item (`testId: 'acao-lancar-compra'`) that navigates to `compras-nota-fiscal` instead of calling `updatePurchaseOrderStatus(id, 'RECEIVED')`. The existing "Cancelar" item is unchanged.

- [ ] **Step 1: Write the failing test**

In `PurchaseOrdersListView.spec.ts`, `mountWithRouter`'s route list (lines 10-18) doesn't yet know about the new form route — add it next to the other `compras` routes:

```typescript
      { path: '/compras/:id/nota-fiscal', name: 'compras-nota-fiscal', component: { template: '<div />' } },
```

Replace the existing test `'marks the order as received via the Ações menu'` (uses the `ordemAberta` fixture already defined at the top of the file, id `'po1'`):

```typescript
  it('marks the order as received via the Ações menu', async () => {
    vi.mocked(purchaseOrdersApi.updatePurchaseOrderStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-po1"]').trigger('click')
    await wrapper.find('[data-test="acao-receber"]').trigger('click')
    await flushPromises()

    expect(purchaseOrdersApi.updatePurchaseOrderStatus).toHaveBeenCalledWith('po1', 'RECEIVED')
  })
```

with:

```typescript
  it('navigates to the Lançar Compra screen via the Ações menu', async () => {
    const { wrapper, router } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-po1"]').trigger('click')
    expect(wrapper.find('[data-test="acao-lancar-compra"]').text()).toBe('Lançar Compra')
    await wrapper.find('[data-test="acao-lancar-compra"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('compras-nota-fiscal')
    expect(router.currentRoute.value.params.id).toBe('po1')
    expect(purchaseOrdersApi.updatePurchaseOrderStatus).not.toHaveBeenCalled()
  })
```

Also update the existing `'hides the receber/cancelar actions once an order is already Recebida'` test (lines 138-149): change its final two assertions from:

```typescript
    expect(wrapper.find('[data-test="acao-receber"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="acao-cancelar"]').exists()).toBe(false)
```

to:

```typescript
    expect(wrapper.find('[data-test="acao-lancar-compra"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="acao-cancelar"]').exists()).toBe(false)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run PurchaseOrdersListView`
Expected: FAIL — `[data-test="acao-lancar-compra"]` not found (the item is still labeled "Marcar como Recebida" with `testId: 'acao-receber'`).

- [ ] **Step 3: Update `PurchaseOrdersListView.vue`**

Remove the `marcarComoRecebida` function entirely and change `acoesPara` from:

```typescript
async function marcarComoRecebida(ordem: PurchaseOrderSummary) {
  erro.value = ''
  try {
    await updatePurchaseOrderStatus(ordem.id, 'RECEIVED')
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status da ordem de compra.'
  }
}

async function cancelarOrdem(ordem: PurchaseOrderSummary) {
  erro.value = ''
  try {
    await updatePurchaseOrderStatus(ordem.id, 'CANCELLED')
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status da ordem de compra.'
  }
}

async function excluir(ordem: PurchaseOrderSummary) {
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

function acoesPara(ordem: PurchaseOrderSummary): ActionsMenuItem[] {
  const itens: ActionsMenuItem[] = [
    { label: 'Editar', action: () => editarOrdem(ordem.id), testId: 'acao-editar' },
  ]
  if (ordem.status === 'OPEN') {
    itens.push({ label: 'Marcar como Recebida', action: () => marcarComoRecebida(ordem), testId: 'acao-receber' })
    itens.push({ label: 'Cancelar', action: () => cancelarOrdem(ordem), testId: 'acao-cancelar' })
  }
  itens.push({ label: 'Excluir', action: () => excluir(ordem), danger: true, testId: 'acao-excluir' })
  return itens
}
```

to:

```typescript
function lancarCompra(ordem: PurchaseOrderSummary) {
  router.push({ name: 'compras-nota-fiscal', params: { id: ordem.id } })
}

async function cancelarOrdem(ordem: PurchaseOrderSummary) {
  erro.value = ''
  try {
    await updatePurchaseOrderStatus(ordem.id, 'CANCELLED')
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status da ordem de compra.'
  }
}

async function excluir(ordem: PurchaseOrderSummary) {
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

function acoesPara(ordem: PurchaseOrderSummary): ActionsMenuItem[] {
  const itens: ActionsMenuItem[] = [
    { label: 'Editar', action: () => editarOrdem(ordem.id), testId: 'acao-editar' },
  ]
  if (ordem.status === 'OPEN') {
    itens.push({ label: 'Lançar Compra', action: () => lancarCompra(ordem), testId: 'acao-lancar-compra' })
    itens.push({ label: 'Cancelar', action: () => cancelarOrdem(ordem), testId: 'acao-cancelar' })
  }
  itens.push({ label: 'Excluir', action: () => excluir(ordem), danger: true, testId: 'acao-excluir' })
  return itens
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run PurchaseOrdersListView`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/PurchaseOrdersListView.vue mesh-suite-frontend/src/views/__tests__/PurchaseOrdersListView.spec.ts
git commit -m "feat(purchase-invoice): replace Marcar como Recebida with Lançar Compra in PurchaseOrdersListView"
```

---

### Task 12: `PURCHASE_INVOICE` permission wiring in the frontend

**Files:**
- Modify: `mesh-suite-frontend/src/api/users.ts`
- Modify: `mesh-suite-frontend/src/views/UserFormView.vue`

**Interfaces:**
- Consumes: nothing new.
- Produces: `ModuleName` now includes `'PURCHASE_INVOICE'`; the permission grid in `UserFormView.vue` renders a `PURCHASE_INVOICE` row; `DEFAULT_MATRIX` grants `PURCHASE_INVOICE:VIEW`/`PURCHASE_INVOICE:CREATE` by default to `ADMIN` and `MANAGER` profiles, and `PURCHASE_INVOICE:VIEW` to `VIEWER`.

- [ ] **Step 1: Add `'PURCHASE_INVOICE'` to `ModuleName`**

In `mesh-suite-frontend/src/api/users.ts`, change:

```typescript
export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE' | 'PAYABLE' | 'SALE'
```

to:

```typescript
export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE' | 'PAYABLE' | 'SALE' | 'PURCHASE_INVOICE'
```

- [ ] **Step 2: Add `PURCHASE_INVOICE` to `MODULES`/`MODULE_LABELS`/`DEFAULT_MATRIX` in `UserFormView.vue`**

Change:

```typescript
const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER', 'PURCHASE', 'PAYABLE', 'SALE']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
  PURCHASE: 'Compras',
  PAYABLE: 'Contas a Pagar',
  SALE: 'Vendas',
}
```

to:

```typescript
const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER', 'PURCHASE', 'PAYABLE', 'SALE', 'PURCHASE_INVOICE']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
  PURCHASE: 'Compras',
  PAYABLE: 'Contas a Pagar',
  SALE: 'Vendas',
  PURCHASE_INVOICE: 'Notas de Entrada',
}
```

Change the `DEFAULT_MATRIX` (only `ADMIN`'s exclusion filter, and the `MANAGER`/`VIEWER` explicit lists, change — `SALES` profile has no business reason to see incoming purchase invoices, so it's left untouched):

```typescript
const DEFAULT_MATRIX: Record<Profile, Permission[]> = {
  ADMIN: [
    ...MODULES.flatMap((m) => ACTIONS.filter((a) =>
      !(m === 'USER' && a === 'DELETE') && !(m === 'PAYABLE' && (a === 'CREATE' || a === 'DELETE'))
        && !(m === 'SALE' && (a === 'EDIT' || a === 'DELETE'))
        && !(m === 'PURCHASE_INVOICE' && (a === 'EDIT' || a === 'DELETE')),
    ).map((a) => ({ module: m, action: a }))),
  ],
  MANAGER: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' }, { module: 'PRODUCT', action: 'CREATE' }, { module: 'PRODUCT', action: 'EDIT' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
    { module: 'PURCHASE', action: 'VIEW' }, { module: 'PURCHASE', action: 'CREATE' }, { module: 'PURCHASE', action: 'EDIT' },
    { module: 'PAYABLE', action: 'VIEW' }, { module: 'PAYABLE', action: 'EDIT' },
    { module: 'SALE', action: 'VIEW' }, { module: 'SALE', action: 'CREATE' },
    { module: 'PURCHASE_INVOICE', action: 'VIEW' }, { module: 'PURCHASE_INVOICE', action: 'CREATE' },
    { module: 'USER', action: 'VIEW' },
  ],
  SALES: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
    { module: 'SALE', action: 'VIEW' }, { module: 'SALE', action: 'CREATE' },
  ],
  VIEWER: [
    { module: 'CUSTOMER', action: 'VIEW' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' },
    { module: 'PURCHASE', action: 'VIEW' },
    { module: 'PAYABLE', action: 'VIEW' },
    { module: 'SALE', action: 'VIEW' },
    { module: 'PURCHASE_INVOICE', action: 'VIEW' },
  ],
}
```

- [ ] **Step 3: Run the existing `UserFormView` test suite to confirm nothing broke**

Run: `cd mesh-suite-frontend && npx vitest run UserFormView`
Expected: all existing tests still pass (they assert specific `perm-{MODULE}-{ACTION}` checkboxes by `data-test`, none of which reference `PURCHASE_INVOICE`, so adding a new row doesn't affect them).

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-frontend/src/api/users.ts mesh-suite-frontend/src/views/UserFormView.vue
git commit -m "feat(purchase-invoice): add PURCHASE_INVOICE to the permission module list and default matrix"
```

---

### Task 13: Full-suite verification

**Files:** none (verification only), plus `prd/ORDEM-EXECUCAO.md`.

- [ ] **Step 1: Run the full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`
Expected: `BUILD SUCCESS`. (Requires Docker for Testcontainers.)

- [ ] **Step 2: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all tests pass.

- [ ] **Step 3: Type-check the frontend**

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit`
Expected: no errors.

- [ ] **Step 4: Update `ORDEM-EXECUCAO.md`**

In `prd/ORDEM-EXECUCAO.md`, update row 5 (Compras) to mark it done. Change:

```
| 5 | Compras | `PRD-07-compras.md` | Ordem de Compra e Compra (nota fiscal de entrada). |
```

to:

```
| 5 | Compras | `PRD-07-compras.md` | **Concluído** (Ordem de Compra + Compra/nota fiscal de entrada, 1:1, débito de estoque e parcelas de contas a pagar automáticos, cálculo fiscal simplificado por item). Sem importação de NF-e, sem frete/Conhecimento de Transporte — ver riscos na spec. |
```

- [ ] **Step 5: Commit**

```bash
git add prd/ORDEM-EXECUCAO.md
git commit -m "docs: mark Compras as concluído in ORDEM-EXECUCAO.md"
```
