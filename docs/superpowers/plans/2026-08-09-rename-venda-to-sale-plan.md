# Rename Venda → Sale Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the entire `venda` module (Java package, DB tables, frontend files) to English (`sale`), keeping every user-visible route/label/message in Portuguese.

**Architecture:** Pure mechanical rename — no behavior changes. Each task moves a cluster of files (`git mv`), rewrites their package/class/field names per the spec's mapping table, and updates every caller in the same commit so the build stays green throughout. Old `venda`-named files are deleted, not left behind.

**Tech Stack:** Same as the rest of the repo (Spring Boot / Java 21 / PostgreSQL 16 / Flyway backend, Vue 3 / TypeScript / Vitest frontend) — no new dependencies.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-09-rename-venda-to-sale-design.md` — read it if anything below is ambiguous.
- No user-visible text changes: page titles, button labels, column headers, error messages shown in the UI, the `/vendas` URL path, and the Vue Router route `name: 'vendas'` all stay exactly as they are today. Only code identifiers change.
- `Module.SALE` (permission enum) is already English — do not touch it.
- Migration `V26__create_venda.sql` is edited in place and renamed to `V26__create_sale.sql` (not a new `ALTER TABLE RENAME` migration) — this repo has no production data yet. This requires resetting the local Postgres volume (`docker-compose down -v` then back up) to reapply migrations from scratch; call this out in Task 1.
- Fields whose referenced entity hasn't been renamed yet keep an English field name but a Portuguese type: `Sale.order` is of type `Pedido`, `Sale.customer` is of type `Parceiro`, `Sale.salesperson` is of type `User`, `SaleItem.product` is of type `Produto`. This is intentional, not a mistake to "fix" by renaming those types too — they're out of scope for this sub-project.
- Every renamed file keeps the exact same test coverage it has today (same number of test methods, same assertions) — only names change.

---

### Task 1: Migration rename

**Files:**
- Delete: `mesh-suite-backend/src/main/resources/db/migration/V26__create_venda.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql`

**Interfaces:**
- Produces: DB tables `sale_counter`, `sale`, `sale_item` (columns below), replacing `venda_contador`/`venda`/`item_venda`.

- [ ] **Step 1: Delete the old migration and create the renamed one**

```sql
-- mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql
CREATE TABLE sale_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE sale_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY sale_counter_tenant_isolation ON sale_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE sale (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    order_id UUID NOT NULL UNIQUE REFERENCES pedido(id),
    customer_id UUID NOT NULL REFERENCES parceiro(id),
    salesperson_id UUID NOT NULL REFERENCES app_user(id),
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    icms_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ipi_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    pis_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    cofins_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_sale_tenant_number ON sale(tenant_id, number);
CREATE INDEX idx_sale_tenant_id ON sale(tenant_id);
CREATE INDEX idx_sale_customer_id ON sale(customer_id);
CREATE INDEX idx_sale_salesperson_id ON sale(salesperson_id);

ALTER TABLE sale ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale FORCE ROW LEVEL SECURITY;

CREATE POLICY sale_tenant_isolation ON sale
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE sale_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID NOT NULL REFERENCES sale(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES produto(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    icms_amount NUMERIC(12,2) NOT NULL,
    ipi_amount NUMERIC(12,2) NOT NULL,
    pis_amount NUMERIC(12,2) NOT NULL,
    cofins_amount NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_sale_item_sale_id ON sale_item(sale_id);

ALTER TABLE sale_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent sale
-- row's own RLS policy, matched by sale_id. Same pattern as item_pedido.
CREATE POLICY sale_item_tenant_isolation ON sale_item
    USING (EXISTS (
        SELECT 1 FROM sale s
        WHERE s.id = sale_item.sale_id
          AND s.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

Use `git mv` for the rename, then edit the content:

```bash
git mv mesh-suite-backend/src/main/resources/db/migration/V26__create_venda.sql \
       mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql
```

Then replace the file's contents with the SQL above.

- [ ] **Step 2: Reset the local database so Flyway reapplies migrations from scratch**

Run: `docker-compose down -v && docker-compose up -d` (from the repo root, where `docker-compose.yml` lives). This only affects your local dev Postgres volume — there is no production data. If you don't have the stack running locally, skip this step; the Testcontainers-based test suite always starts fresh and isn't affected by your local volume.

- [ ] **Step 3: Verify the migration is syntactically valid**

This can't be verified in isolation (Flyway needs Spring context to run it). It will be validated when Task 2's `SaleRepositoryTest` runs. For now, just confirm the file exists at the new path and the old one is gone:

Run: `ls mesh-suite-backend/src/main/resources/db/migration/ | grep -i sale` and `ls mesh-suite-backend/src/main/resources/db/migration/ | grep -i venda`
Expected: first command shows `V26__create_sale.sql`; second command shows nothing.

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/
git commit -m "refactor(sale): rename V26 migration from venda to sale (tables, columns, indexes, RLS policies)"
```

---

### Task 2: Domain, repository, and RLS test rename

**Files:**
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/Venda.java`, `ItemVenda.java`, `VendaContador.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/sale/domain/Sale.java`, `SaleItem.java`, `SaleCounter.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/VendaRepository.java`, `VendaContadorRepository.java`, `repository/specification/VendaSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/sale/repository/SaleRepository.java`, `SaleCounterRepository.java`, `repository/specification/SaleSpecifications.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/venda/repository/VendaRepositoryTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/sale/repository/SaleRepositoryTest.java`

**Interfaces:**
- Consumes: `com.meshsuite.pedido.domain.Pedido`, `com.meshsuite.parceiro.domain.Parceiro`, `com.meshsuite.user.domain.User`, `com.meshsuite.produto.domain.Produto` (all unchanged, still Portuguese-named — this task does not touch them).
- Produces: `Sale` (getters/setters: `getId/setId`, `getTenantId/setTenantId`, `getNumber/setNumber`, `getOrder/setOrder`, `getCustomer/setCustomer`, `getSalesperson/setSalesperson`, `getIssueDate/setIssueDate`, `getDiscount/setDiscount`, `getSubtotal/setSubtotal`, `getTotal/setTotal`, `getIcmsAmount/setIcmsAmount`, `getIpiAmount/setIpiAmount`, `getPisAmount/setPisAmount`, `getCofinsAmount/setCofinsAmount`, `getCreatedAt`, `getItems()` returning `List<SaleItem>`). `SaleItem` (`getId/setId`, `getSale/setSale`, `getProduct/setProduct`, `getQuantity/setQuantity`, `getUnitPrice/setUnitPrice`, `getTotalAmount/setTotalAmount`, `getIcmsAmount/setIcmsAmount`, `getIpiAmount/setIpiAmount`, `getPisAmount/setPisAmount`, `getCofinsAmount/setCofinsAmount`). `SaleRepository extends JpaRepository<Sale, UUID>, JpaSpecificationExecutor<Sale>`. `SaleCounterRepository extends JpaRepository<SaleCounter, UUID>`. `SaleSpecifications.withSearch(String search): Specification<Sale>`.

- [ ] **Step 1: `git mv` the domain, repository, and test files into the new package**

```bash
mkdir -p mesh-suite-backend/src/main/java/com/meshsuite/sale/domain \
         mesh-suite-backend/src/main/java/com/meshsuite/sale/repository/specification \
         mesh-suite-backend/src/test/java/com/meshsuite/sale/repository

git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/Venda.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/domain/Sale.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/ItemVenda.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/domain/SaleItem.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/VendaContador.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/domain/SaleCounter.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/VendaRepository.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/repository/SaleRepository.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/VendaContadorRepository.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/repository/SaleCounterRepository.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/specification/VendaSpecifications.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/repository/specification/SaleSpecifications.java
git mv mesh-suite-backend/src/test/java/com/meshsuite/venda/repository/VendaRepositoryTest.java \
       mesh-suite-backend/src/test/java/com/meshsuite/sale/repository/SaleRepositoryTest.java
```

- [ ] **Step 2: Replace the content of each moved file**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/domain/Sale.java
package com.meshsuite.sale.domain;

import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.user.domain.User;
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
@Table(name = "sale")
@Getter
@Setter
public class Sale {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Integer number;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Pedido order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Parceiro customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesperson_id", nullable = false)
    private User salesperson;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate = LocalDate.now();

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

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SaleItem> items = new ArrayList<>();
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/domain/SaleItem.java
package com.meshsuite.sale.domain;

import com.meshsuite.produto.domain.Produto;
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
@Table(name = "sale_item")
@Getter
@Setter
public class SaleItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Produto product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

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

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/domain/SaleCounter.java
package com.meshsuite.sale.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sale_counter")
@Getter
@Setter
public class SaleCounter {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber = 1;
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/repository/SaleRepository.java
package com.meshsuite.sale.repository;

import com.meshsuite.sale.domain.Sale;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SaleRepository extends JpaRepository<Sale, UUID>, JpaSpecificationExecutor<Sale> {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/repository/SaleCounterRepository.java
package com.meshsuite.sale.repository;

import com.meshsuite.sale.domain.SaleCounter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleCounterRepository extends JpaRepository<SaleCounter, UUID> {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/repository/specification/SaleSpecifications.java
package com.meshsuite.sale.repository.specification;

import com.meshsuite.sale.domain.Sale;
import org.springframework.data.jpa.domain.Specification;

public final class SaleSpecifications {

    private SaleSpecifications() {
    }

    public static Specification<Sale> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        Integer number = tryParseInt(search.trim());
        return (root, query, cb) -> {
            var byCustomer = cb.like(cb.lower(root.get("customer").get("nomeFantasia")), term);
            if (number != null) {
                return cb.or(byCustomer, cb.equal(root.get("number"), number));
            }
            return byCustomer;
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

```java
// mesh-suite-backend/src/test/java/com/meshsuite/sale/repository/SaleRepositoryTest.java
package com.meshsuite.sale.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.repository.PedidoRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.sale.domain.Sale;
import com.meshsuite.sale.domain.SaleCounter;
import com.meshsuite.sale.domain.SaleItem;
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

class SaleRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SaleRepository saleRepository;
    @Autowired SaleCounterRepository saleCounterRepository;
    @Autowired EntityManager entityManager;

    private Tenant createTenant(String code) {
        Tenant t = new Tenant();
        t.setCodigo(code);
        t.setNome(code);
        return tenantRepository.saveAndFlush(t);
    }

    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    private Parceiro createCustomer(UUID tenantId, String document) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(document);
        p.setNomeFantasia("Mercado Silva");
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p);
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

    private Produto createProduct(UUID tenantId, String sku) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("59.90"));
        return produtoRepository.saveAndFlush(p);
    }

    private Pedido createOrder(UUID tenantId, Parceiro customer, User salesperson, int number) {
        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setNumero(number);
        pedido.setCliente(customer);
        pedido.setVendedor(salesperson);
        return pedidoRepository.saveAndFlush(pedido);
    }

    private Sale newSale(UUID tenantId, Pedido order, Parceiro customer, User salesperson, int number) {
        Sale sale = new Sale();
        sale.setTenantId(tenantId);
        sale.setNumber(number);
        sale.setOrder(order);
        sale.setCustomer(customer);
        sale.setSalesperson(salesperson);
        return sale;
    }

    @Test
    @Transactional
    void savesSaleWithItemsViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Produto product = createProduct(tenant.getId(), "P0001");
        Pedido order = createOrder(tenant.getId(), customer, salesperson, 1);

        Sale sale = newSale(tenant.getId(), order, customer, salesperson, 1);
        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setProduct(product);
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("119.80"));
        item.setIcmsAmount(new BigDecimal("10.00"));
        item.setIpiAmount(BigDecimal.ZERO);
        item.setPisAmount(BigDecimal.ZERO);
        item.setCofinsAmount(BigDecimal.ZERO);
        sale.getItems().add(item);

        Sale saved = saleRepository.saveAndFlush(sale);
        entityManager.clear();

        Sale reloaded = saleRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getItems().get(0).getIcmsAmount()).isEqualByComparingTo("10.00");
        assertThat(reloaded.getOrder().getId()).isEqualTo(order.getId());
    }

    @Test
    @Transactional
    void orderIdMustBeUniqueAcrossSales() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Pedido order = createOrder(tenant.getId(), customer, salesperson, 1);

        saleRepository.saveAndFlush(newSale(tenant.getId(), order, customer, salesperson, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> saleRepository.saveAndFlush(newSale(tenant.getId(), order, customer, salesperson, 2)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Pedido order = createOrder(tenant.getId(), customer, salesperson, 1);
        saleRepository.saveAndFlush(newSale(tenant.getId(), order, customer, salesperson, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sale")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void saleItemRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro customer = createCustomer(tenant.getId(), "11222333000144");
        User salesperson = createSalesperson(tenant.getId(), "marina@aurora.com.br");
        Produto product = createProduct(tenant.getId(), "P0001");
        Pedido order = createOrder(tenant.getId(), customer, salesperson, 1);

        Sale sale = newSale(tenant.getId(), order, customer, salesperson, 1);
        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setProduct(product);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("59.90"));
        item.setTotalAmount(new BigDecimal("59.90"));
        item.setIcmsAmount(BigDecimal.ZERO);
        item.setIpiAmount(BigDecimal.ZERO);
        item.setPisAmount(BigDecimal.ZERO);
        item.setCofinsAmount(BigDecimal.ZERO);
        sale.getItems().add(item);
        Sale saved = saleRepository.saveAndFlush(sale);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sale_item WHERE sale_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void saleCounterRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        SaleCounter counter = new SaleCounter();
        counter.setTenantId(tenant.getId());
        counter.setNextNumber(1);
        saleCounterRepository.saveAndFlush(counter);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM sale_counter")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 3: Remove the now-empty old directories, run the test**

```bash
rmdir mesh-suite-backend/src/main/java/com/meshsuite/venda/domain \
      mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/specification \
      mesh-suite-backend/src/main/java/com/meshsuite/venda/repository \
      mesh-suite-backend/src/test/java/com/meshsuite/venda/repository 2>/dev/null || true
```

Run: `cd mesh-suite-backend && mvn -q test -Dtest=SaleRepositoryTest`
Expected: `BUILD SUCCESS`, 5 tests passed. (This also validates Task 1's migration for the first time — Flyway runs it here.)

- [ ] **Step 4: Commit**

```bash
git add -A mesh-suite-backend/src/main/java/com/meshsuite/venda mesh-suite-backend/src/main/java/com/meshsuite/sale \
           mesh-suite-backend/src/test/java/com/meshsuite/venda mesh-suite-backend/src/test/java/com/meshsuite/sale
git commit -m "refactor(sale): rename Venda/ItemVenda/VendaContador domain and repository layer to English"
```

---

### Task 3: DTO, exception, and GlobalExceptionHandler rename

**Files:**
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/VendaResponse.java`, `ItemVendaResponse.java`, `VendaSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/sale/dto/SaleResponse.java`, `SaleItemResponse.java`, `SaleSummaryResponse.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaNaoEncontradaException.java`, `VendaValidacaoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/sale/exception/SaleNotFoundException.java`, `SaleValidationException.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `SaleResponse(UUID id, Integer number, UUID orderId, Integer orderNumber, UUID customerId, String customerName, UUID salespersonId, String salespersonName, LocalDate issueDate, BigDecimal discount, BigDecimal subtotal, BigDecimal total, BigDecimal icmsAmount, BigDecimal ipiAmount, BigDecimal pisAmount, BigDecimal cofinsAmount, List<SaleItemResponse> items)`. `SaleItemResponse(UUID productId, String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal totalAmount, BigDecimal icmsAmount, BigDecimal ipiAmount, BigDecimal pisAmount, BigDecimal cofinsAmount)`. `SaleSummaryResponse(UUID id, Integer number, String customerName, LocalDate issueDate, BigDecimal total)`. `SaleNotFoundException` (no-arg), `SaleValidationException(String message)`.

- [ ] **Step 1: `git mv` and rewrite the DTOs**

```bash
mkdir -p mesh-suite-backend/src/main/java/com/meshsuite/sale/dto mesh-suite-backend/src/main/java/com/meshsuite/sale/exception

git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/VendaResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/dto/SaleResponse.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/ItemVendaResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/dto/SaleItemResponse.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/VendaSummaryResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/dto/SaleSummaryResponse.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaNaoEncontradaException.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/exception/SaleNotFoundException.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaValidacaoException.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/exception/SaleValidationException.java
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/dto/SaleItemResponse.java
package com.meshsuite.sale.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        BigDecimal icmsAmount,
        BigDecimal ipiAmount,
        BigDecimal pisAmount,
        BigDecimal cofinsAmount) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/dto/SaleResponse.java
package com.meshsuite.sale.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        Integer number,
        UUID orderId,
        Integer orderNumber,
        UUID customerId,
        String customerName,
        UUID salespersonId,
        String salespersonName,
        LocalDate issueDate,
        BigDecimal discount,
        BigDecimal subtotal,
        BigDecimal total,
        BigDecimal icmsAmount,
        BigDecimal ipiAmount,
        BigDecimal pisAmount,
        BigDecimal cofinsAmount,
        List<SaleItemResponse> items) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/dto/SaleSummaryResponse.java
package com.meshsuite.sale.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SaleSummaryResponse(
        UUID id,
        Integer number,
        String customerName,
        LocalDate issueDate,
        BigDecimal total) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/exception/SaleNotFoundException.java
package com.meshsuite.sale.exception;

public class SaleNotFoundException extends RuntimeException {
    public SaleNotFoundException() {
        super("Venda não encontrada");
    }
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/exception/SaleValidationException.java
package com.meshsuite.sale.exception;

public class SaleValidationException extends RuntimeException {
    public SaleValidationException(String message) {
        super(message);
    }
}
```

Note: the exception *messages* stay in Portuguese (`"Venda não encontrada"`) — those are user-visible error text, not code identifiers.

- [ ] **Step 2: Update `GlobalExceptionHandler`**

In `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`, replace this block (currently the last two handlers before the closing `}`):

```java
    @ExceptionHandler(com.meshsuite.venda.exception.VendaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleVendaNaoEncontrada(
            com.meshsuite.venda.exception.VendaNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.venda.exception.VendaValidacaoException.class)
    public ResponseEntity<Map<String, String>> handleVendaValidacao(
            com.meshsuite.venda.exception.VendaValidacaoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

with:

```java
    @ExceptionHandler(com.meshsuite.sale.exception.SaleNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSaleNotFound(
            com.meshsuite.sale.exception.SaleNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.sale.exception.SaleValidationException.class)
    public ResponseEntity<Map<String, String>> handleSaleValidation(
            com.meshsuite.sale.exception.SaleValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 3: Remove now-empty old directories, verify compile**

```bash
rmdir mesh-suite-backend/src/main/java/com/meshsuite/venda/dto \
      mesh-suite-backend/src/main/java/com/meshsuite/venda/exception 2>/dev/null || true
```

Run: `cd mesh-suite-backend && mvn -q clean compile`
Expected: `COMPILATION ERROR` referencing `com.meshsuite.sale.service` / `com.meshsuite.sale.controller` not existing yet — that's expected, `VendaService`/`VendaController` (Task 4/5) still import the old DTO/exception names at this point. **Do not try to fix that here** — just confirm the error is specifically about the not-yet-renamed `VendaService.java` and `VendaController.java` files failing to resolve `com.meshsuite.venda.dto.*`/`com.meshsuite.venda.exception.*` (which no longer exist), not some other unrelated error. If the error is anything else, stop and report BLOCKED.

- [ ] **Step 4: Commit**

```bash
git add -A mesh-suite-backend/src/main/java/com/meshsuite/venda mesh-suite-backend/src/main/java/com/meshsuite/sale \
           mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java
git commit -m "refactor(sale): rename Venda DTOs/exceptions to English, update GlobalExceptionHandler"
```

---

### Task 4: Service rename

**Files:**
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/venda/service/VendaService.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/sale/service/SaleService.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/venda/service/VendaServiceTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/sale/service/SaleServiceTest.java`

**Interfaces:**
- Consumes: `SaleRepository`, `Sale`, `SaleItem` (Task 2); `SaleResponse`, `SaleItemResponse`, `SaleSummaryResponse`, `SaleNotFoundException`, `SaleValidationException` (Task 3); `com.meshsuite.pedido.repository.PedidoRepository`, `com.meshsuite.pedido.exception.PedidoNaoEncontradoException` (unchanged); `com.meshsuite.fiscal.service.FiscalCalculationService` (unchanged).
- Produces: `SaleService.issue(UUID orderId): SaleResponse`, `SaleService.list(String search, Pageable pageable): Page<SaleSummaryResponse>`, `SaleService.findById(UUID id): SaleResponse`.

- [ ] **Step 1: `git mv` and write the failing test**

```bash
mkdir -p mesh-suite-backend/src/main/java/com/meshsuite/sale/service mesh-suite-backend/src/test/java/com/meshsuite/sale/service

git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/service/VendaService.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/service/SaleService.java
git mv mesh-suite-backend/src/test/java/com/meshsuite/venda/service/VendaServiceTest.java \
       mesh-suite-backend/src/test/java/com/meshsuite/sale/service/SaleServiceTest.java
```

```java
// mesh-suite-backend/src/test/java/com/meshsuite/sale/service/SaleServiceTest.java
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
```

- [ ] **Step 2: Run it to verify it fails to compile**

Run: `cd mesh-suite-backend && mvn -q test-compile`
Expected: `COMPILATION ERROR` — `SaleService` does not exist yet.

- [ ] **Step 3: Rewrite `SaleService.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/service/SaleService.java
package com.meshsuite.sale.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.fiscal.dto.FiscalCalculationResult;
import com.meshsuite.fiscal.service.FiscalCalculationService;
import com.meshsuite.pedido.domain.ItemPedido;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.pedido.exception.PedidoNaoEncontradoException;
import com.meshsuite.pedido.repository.PedidoRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.sale.domain.Sale;
import com.meshsuite.sale.domain.SaleItem;
import com.meshsuite.sale.dto.SaleItemResponse;
import com.meshsuite.sale.dto.SaleResponse;
import com.meshsuite.sale.dto.SaleSummaryResponse;
import com.meshsuite.sale.exception.SaleNotFoundException;
import com.meshsuite.sale.exception.SaleValidationException;
import com.meshsuite.sale.repository.SaleRepository;
import com.meshsuite.sale.repository.specification.SaleSpecifications;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

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

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public Page<SaleSummaryResponse> list(String search, Pageable pageable) {
        Specification<Sale> spec = Specification.where(SaleSpecifications.withSearch(search));
        return saleRepository.findAll(spec, remapCustomerNameSort(pageable)).map(this::toSummary);
    }

    // SaleSummaryResponse.customerName is a projection, not a direct Sale property --
    // the actual JPA path is the customer association's nomeFantasia. Remap here so
    // sorting by "customerName" (as sent by the frontend) doesn't blow up with a
    // PropertyReferenceException.
    private Pageable remapCustomerNameSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Sort remapped = Sort.by(pageable.getSort().stream()
                .map(order -> "customerName".equals(order.getProperty())
                        ? order.withProperty("customer.nomeFantasia")
                        : order)
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), remapped);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public SaleResponse findById(UUID id) {
        return toResponse(saleRepository.findById(id).orElseThrow(SaleNotFoundException::new));
    }

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
            Produto product = orderItem.getProduto();
            if (product.getFiscalRegistration() == null) {
                throw new SaleValidationException(
                        "O produto " + product.getNome() + " não possui cadastro fiscal aplicado");
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

    // Atomic UPDATE ... RETURNING against the tenant's single sale_counter row --
    // never COUNT(*)/MAX(number)+1, both of which race under concurrent inserts.
    // Runs inside this method's own @Transactional, so TenantContextAspect has
    // already issued SET LOCAL app.tenant_id before either native query below runs.
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO sale_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE sale_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private SaleSummaryResponse toSummary(Sale s) {
        return new SaleSummaryResponse(s.getId(), s.getNumber(), s.getCustomer().getNomeFantasia(),
                s.getIssueDate(), s.getTotal());
    }

    private SaleResponse toResponse(Sale s) {
        List<SaleItemResponse> items = s.getItems().stream()
                .map(i -> new SaleItemResponse(i.getProduct().getId(), i.getProduct().getNome(),
                        i.getQuantity(), i.getUnitPrice(), i.getTotalAmount(),
                        i.getIcmsAmount(), i.getIpiAmount(), i.getPisAmount(), i.getCofinsAmount()))
                .toList();
        return new SaleResponse(s.getId(), s.getNumber(), s.getOrder().getId(), s.getOrder().getNumero(),
                s.getCustomer().getId(), s.getCustomer().getNomeFantasia(),
                s.getSalesperson().getId(), s.getSalesperson().getName(),
                s.getIssueDate(), s.getDiscount(), s.getSubtotal(), s.getTotal(),
                s.getIcmsAmount(), s.getIpiAmount(), s.getPisAmount(), s.getCofinsAmount(), items);
    }
}
```

- [ ] **Step 4: Remove now-empty old directories, run the tests**

```bash
rmdir mesh-suite-backend/src/main/java/com/meshsuite/venda/service \
      mesh-suite-backend/src/test/java/com/meshsuite/venda/service 2>/dev/null || true
```

Run: `cd mesh-suite-backend && mvn -q test -Dtest=SaleServiceTest`
Expected: `BUILD SUCCESS`, 7 tests passed. (`mvn compile` on its own will still fail here — `VendaController.java` in Task 5 still references the old names. That's expected; Task 5 fixes it.)

- [ ] **Step 5: Commit**

```bash
git add -A mesh-suite-backend/src/main/java/com/meshsuite/venda mesh-suite-backend/src/main/java/com/meshsuite/sale \
           mesh-suite-backend/src/test/java/com/meshsuite/venda mesh-suite-backend/src/test/java/com/meshsuite/sale
git commit -m "refactor(sale): rename VendaService to SaleService (faturar->issue, listar->list, buscarPorId->findById)"
```

---

### Task 5: Controller, exception handler, and `PedidoService` message rename

**Files:**
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/venda/controller/VendaController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/sale/controller/SaleController.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaExceptionHandler.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/sale/exception/SaleExceptionHandler.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/venda/controller/VendaControllerTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/service/PedidoService.java:105`

**Interfaces:**
- Consumes: `SaleService.issue/list/findById` (Task 4).
- Produces: `POST /api/sales/issue/{orderId}` (201, `SaleResponse`), `GET /api/sales` (200, `Page<SaleSummaryResponse>`), `GET /api/sales/{id}` (200, `SaleResponse`).

- [ ] **Step 1: `git mv` and write the failing test**

```bash
mkdir -p mesh-suite-backend/src/main/java/com/meshsuite/sale/controller mesh-suite-backend/src/test/java/com/meshsuite/sale/controller

git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/controller/VendaController.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/controller/SaleController.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaExceptionHandler.java \
       mesh-suite-backend/src/main/java/com/meshsuite/sale/exception/SaleExceptionHandler.java
git mv mesh-suite-backend/src/test/java/com/meshsuite/venda/controller/VendaControllerTest.java \
       mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java
```

```java
// mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java
package com.meshsuite.sale.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.empresa.domain.Empresa;
import com.meshsuite.empresa.repository.EmpresaRepository;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.fiscal.repository.FiscalRegistrationRepository;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.repository.ProdutoRepository;
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
class SaleControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Context(String cookie, String customerId, String salespersonId, String productId) {
    }

    private Context loginAndSetUp(String code, String email, String companyCnpj) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(code);
        tenant.setNome(code);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial(code + " Ltda");
        empresa.setCnpj(companyCnpj);
        empresaRepository.saveAndFlush(empresa);

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
        userLogin.getPermissions().add(new UserPermissionGrant(Module.SALE, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.SALE, Action.CREATE));
        userRepository.saveAndFlush(userLogin);

        User salesperson = new User();
        salesperson.setTenantId(tenant.getId());
        salesperson.setName("Carla Vendedora");
        salesperson.setEmail("carla-" + code + "@" + code + ".com.br");
        salesperson.setPasswordHash("hash");
        salesperson.setRole(Role.SALES_REP);
        salesperson.setProfile(Profile.SALES);
        userRepository.saveAndFlush(salesperson);

        Parceiro customer = new Parceiro();
        customer.setTenantId(tenant.getId());
        customer.setTipoPessoa(TipoPessoa.JURIDICA);
        customer.setDocumento(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        customer.setNomeFantasia("Mercado Silva");
        customer.getPapeis().add(PapelParceiro.CLIENTE);
        parceiroRepository.saveAndFlush(customer);

        FiscalRegistration registration = new FiscalRegistration();
        registration.setTenantId(tenant.getId());
        registration.setDescription("Venda dentro do estado");
        registration.setCfop("5102");
        registration.setIcmsCst("000");
        registration.setIcmsRate(new BigDecimal("18.00"));
        registration.setIpiRate(new BigDecimal("5.00"));
        registration.setPisRate(new BigDecimal("1.65"));
        registration.setCofinsRate(new BigDecimal("7.60"));
        fiscalRegistrationRepository.saveAndFlush(registration);

        Produto product = new Produto();
        product.setTenantId(tenant.getId());
        product.setNome("Camiseta Polo");
        product.setSku("P0001-" + code);
        product.setPrecoVenda(new BigDecimal("59.90"));
        product.setFiscalRegistration(registration);
        produtoRepository.saveAndFlush(product);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Context(token, customer.getId().toString(), salesperson.getId().toString(), product.getId().toString());
    }

    private String createOrderInPreparation(Context ctx, Cookie cookie) throws Exception {
        String created = mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": [ { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 } ]
                                }
                                """.formatted(ctx.customerId(), ctx.salespersonId(), ctx.productId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/pedidos/" + orderId + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isOk());

        return orderId;
    }

    @Test
    void issuesListsAndFindsSale() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        String orderId = createOrderInPreparation(ctx, cookie);

        String created = mockMvc.perform(post("/api/sales/issue/" + orderId).cookie(cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.total").value(119.80))
                .andReturn().getResponse().getContentAsString();
        String saleId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/sales").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(1));

        mockMvc.perform(get("/api/sales/" + saleId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Mercado Silva"));

        mockMvc.perform(get("/api/pedidos/" + orderId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FATURADO"));
    }

    @Test
    void issuingAnOrderStillInDigitadoIsBadRequest() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": [ { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 } ]
                                }
                                """.formatted(ctx.customerId(), ctx.salespersonId(), ctx.productId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/sales/issue/" + orderId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issuingWithoutSalePermissionIsForbidden() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao-venda");
        tenant.setNome("sem-permissao-venda");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("sem-permissao-venda Ltda");
        empresa.setCnpj("99888777000166");
        empresaRepository.saveAndFlush(empresa);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Sem Permissão");
        user.setEmail("sem-permissao-venda@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.VIEWER);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sem-permissao-venda@aurora.com.br\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/sales").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

Run: `cd mesh-suite-backend && mvn -q test-compile`
Expected: `COMPILATION ERROR` — `SaleController` does not exist yet.

- [ ] **Step 3: Rewrite `SaleController.java` and `SaleExceptionHandler.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/controller/SaleController.java
package com.meshsuite.sale.controller;

import com.meshsuite.sale.dto.SaleResponse;
import com.meshsuite.sale.dto.SaleSummaryResponse;
import com.meshsuite.sale.service.SaleService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    public Page<SaleSummaryResponse> list(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 10, sort = "number", direction = Sort.Direction.DESC) Pageable pageable) {
        return saleService.list(busca, pageable);
    }

    @GetMapping("/{id}")
    public SaleResponse findById(@PathVariable UUID id) {
        return saleService.findById(id);
    }

    @PostMapping("/issue/{orderId}")
    public ResponseEntity<SaleResponse> issue(@PathVariable UUID orderId) {
        SaleResponse response = saleService.issue(orderId);
        return ResponseEntity.status(201).body(response);
    }
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/sale/exception/SaleExceptionHandler.java
package com.meshsuite.sale.exception;

import com.meshsuite.sale.controller.SaleController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SaleController.class)
public class SaleExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Não foi possível faturar o pedido. Tente novamente."));
    }
}
```

- [ ] **Step 4: Update the `PedidoService` error message**

In `mesh-suite-backend/src/main/java/com/meshsuite/pedido/service/PedidoService.java:105`, change:

```java
                    "Faturamento deve ser feito através do fluxo de Venda (POST /api/vendas/faturar/{pedidoId})");
```

to:

```java
                    "Faturamento deve ser feito através do fluxo de Venda (POST /api/sales/issue/{pedidoId})");
```

(Only the URL in the error text changes — the message itself stays in Portuguese, it's shown to the end user.)

- [ ] **Step 5: Remove now-empty old directories, run the tests**

```bash
rmdir mesh-suite-backend/src/main/java/com/meshsuite/venda/controller \
      mesh-suite-backend/src/main/java/com/meshsuite/venda/exception \
      mesh-suite-backend/src/main/java/com/meshsuite/venda \
      mesh-suite-backend/src/test/java/com/meshsuite/venda/controller \
      mesh-suite-backend/src/test/java/com/meshsuite/venda 2>/dev/null || true
```

Run: `cd mesh-suite-backend && mvn -q test -Dtest=SaleControllerTest`
Expected: `BUILD SUCCESS`, 3 tests passed.

Run: `cd mesh-suite-backend && mvn -q clean test`
Expected: `BUILD SUCCESS` for everything except the pre-existing, unrelated `payable` module test-isolation flake (12 errors, 0 failures — see the Venda-faturamento plan's Task 6/12 notes; still present, still out of scope, unrelated to this rename). Confirm no `venda`/`Venda` references remain anywhere:

Run: `grep -ril "venda\|Venda" mesh-suite-backend/src --include="*.java"`
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add -A mesh-suite-backend
git commit -m "refactor(sale): rename VendaController/VendaExceptionHandler to SaleController/SaleExceptionHandler; update PedidoService message"
```

---

### Task 6: Frontend API client rename

**Files:**
- Delete: `mesh-suite-frontend/src/api/vendas.ts`
- Create: `mesh-suite-frontend/src/api/sales.ts`

**Interfaces:**
- Produces: `SaleResponse`, `SaleItemResponse`, `SaleSummary`, `Page<T>`, `ListSalesParams`, `listSales(params): Promise<Page<SaleSummary>>`, `getSale(id): Promise<SaleResponse>`, `issueSale(orderId): Promise<SaleResponse>`.

- [ ] **Step 1: `git mv` and rewrite the file**

```bash
git mv mesh-suite-frontend/src/api/vendas.ts mesh-suite-frontend/src/api/sales.ts
```

```typescript
// mesh-suite-frontend/src/api/sales.ts
import { apiClient } from './client'

export interface SaleItemResponse {
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  totalAmount: number
  icmsAmount: number
  ipiAmount: number
  pisAmount: number
  cofinsAmount: number
}

export interface SaleResponse {
  id: string
  number: number
  orderId: string
  orderNumber: number
  customerId: string
  customerName: string
  salespersonId: string
  salespersonName: string
  issueDate: string
  discount: number
  subtotal: number
  total: number
  icmsAmount: number
  ipiAmount: number
  pisAmount: number
  cofinsAmount: number
  items: SaleItemResponse[]
}

export interface SaleSummary {
  id: string
  number: number
  customerName: string
  issueDate: string
  total: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListSalesParams {
  busca?: string
  page?: number
  size?: number
  sort?: string
}

export async function listSales(params: ListSalesParams): Promise<Page<SaleSummary>> {
  const { data } = await apiClient.get<Page<SaleSummary>>('/sales', { params })
  return data
}

export async function getSale(id: string): Promise<SaleResponse> {
  const { data } = await apiClient.get<SaleResponse>(`/sales/${id}`)
  return data
}

export async function issueSale(orderId: string): Promise<SaleResponse> {
  const { data } = await apiClient.post<SaleResponse>(`/sales/issue/${orderId}`)
  return data
}
```

Note: the `busca` param name stays as-is — it's the querystring key the backend's `SaleController.list(@RequestParam(required = false) String busca, ...)` expects (Task 5 kept that parameter name; renaming it would require a matching backend change that's out of scope for a pure rename task — flag this as a Minor observation in your report, don't fix it).

- [ ] **Step 2: Verify it type-checks**

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit`
Expected: errors in `VendasListView.vue`/`PedidosListView.vue`/`router/index.ts` (still importing from `@/api/vendas`, which no longer exists) — expected at this point, fixed in Tasks 7-8. Confirm the errors are specifically "Cannot find module '@/api/vendas'" in those three files, nothing else.

- [ ] **Step 3: Commit**

```bash
git add -A mesh-suite-frontend/src/api
git commit -m "refactor(sale): rename api/vendas.ts to api/sales.ts (listarVendas->listSales, buscarVenda->getSale, faturarPedido->issueSale)"
```

---

### Task 7: Frontend `SalesListView.vue` rename

**Files:**
- Delete: `mesh-suite-frontend/src/views/VendasListView.vue`, `mesh-suite-frontend/src/views/__tests__/VendasListView.spec.ts`
- Create: `mesh-suite-frontend/src/views/SalesListView.vue`, `mesh-suite-frontend/src/views/__tests__/SalesListView.spec.ts`

**Interfaces:**
- Consumes: `listSales`, `SaleSummary`, `Page` (Task 6).
- Produces: `SalesListView` component — same rendered output/labels/text as `VendasListView` today, just renamed.

- [ ] **Step 1: `git mv` and write the failing spec**

```bash
git mv mesh-suite-frontend/src/views/VendasListView.vue mesh-suite-frontend/src/views/SalesListView.vue
git mv mesh-suite-frontend/src/views/__tests__/VendasListView.spec.ts mesh-suite-frontend/src/views/__tests__/SalesListView.spec.ts
```

```typescript
// mesh-suite-frontend/src/views/__tests__/SalesListView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import SalesListView from '@/views/SalesListView.vue'
import * as salesApi from '@/api/sales'

vi.mock('@/api/sales')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/vendas', name: 'vendas', component: SalesListView }],
  })
  router.push('/vendas')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(SalesListView, { global: { plugins: [router] } }),
  }))
}

const sale = {
  id: 'v1', number: 1, customerName: 'Mercado Silva', issueDate: '2026-08-08', total: 119.8,
}

describe('SalesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(salesApi.listSales).mockResolvedValue({
      content: [sale], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the sale list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('silva')
    await flushPromises()

    expect(salesApi.listSales).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('shows an empty state when there are no sales', async () => {
    vi.mocked(salesApi.listSales).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma venda para exibir.')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(salesApi.listSales).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de vendas.')
  })
})
```

Note: `wrapper.text()` assertions (`'Nenhuma venda para exibir.'`, `'Não foi possível carregar a lista de vendas.'`) stay in Portuguese — those are the literal UI strings, which don't change.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run SalesListView`
Expected: FAIL — `Failed to resolve import "@/api/sales"` or similar, since `SalesListView.vue`'s content hasn't been rewritten yet.

- [ ] **Step 3: Rewrite `SalesListView.vue`**

Keep every line of the `<template>` and `<style scoped>` blocks byte-for-byte identical to what's in `VendasListView.vue` today (all visible text/labels/CSS stay the same) — only these things change: the variable name `venda` (the `v-for` loop variable) → `sale`, `pagina.content` iteration key/bindings referencing `venda.numero/clienteNome/dataEmissao/total` → `sale.number/customerName/issueDate/total`, and the `<script setup>` block's imports/logic. Full file:

```vue
<!-- mesh-suite-frontend/src/views/SalesListView.vue -->
<template>
  <AppShell title="Vendas">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº ou cliente..."
        data-test="busca"
        @input="carregar(0)"
      />
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Vendas</span>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nº</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-cliente" @click="toggleSort('customerName')">
            Cliente
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'customerName' }">{{ sortIcon('customerName') }}</span>
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

        <div v-for="sale in pagina.content" :key="sale.id" class="table-grid-row" :data-test="`row-${sale.id}`">
          <div class="table-grid-cell">{{ sale.number }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ sale.customerName }}</div>
          <div class="table-grid-cell">{{ formatarData(sale.issueDate) }}</div>
          <div class="table-grid-cell">{{ formatarPreco(sale.total) }}</div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma venda para exibir.</p>
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
import { listSales, type SaleSummary, type Page as ApiPage } from '@/api/sales'

const filtros = reactive({ busca: '' })
const pagina = ref<ApiPage<SaleSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const sortField = ref<'customerName' | 'issueDate' | 'total' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function sortIcon(field: 'customerName' | 'issueDate' | 'total') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'customerName' | 'issueDate' | 'total') {
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
    pagina.value = await listSales({
      busca: filtros.busca || undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de vendas.'
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
  grid-template-columns: 70px 1fr 150px 110px;
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

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run SalesListView`
Expected: 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add -A mesh-suite-frontend/src/views/VendasListView.vue mesh-suite-frontend/src/views/SalesListView.vue \
           mesh-suite-frontend/src/views/__tests__/VendasListView.spec.ts mesh-suite-frontend/src/views/__tests__/SalesListView.spec.ts
git commit -m "refactor(sale): rename VendasListView to SalesListView (visible text unchanged)"
```

---

### Task 8: Cross-references — `PedidosListView.vue`, router, and their specs

**Files:**
- Modify: `mesh-suite-frontend/src/views/PedidosListView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/PedidosListView.spec.ts`
- Modify: `mesh-suite-frontend/src/router/index.ts`

**Interfaces:**
- Consumes: `issueSale` (Task 6), `SalesListView` (Task 7).

- [ ] **Step 1: Update the test in `PedidosListView.spec.ts` first**

Change (around line 132-148):

```typescript
  it('faturns the pedido via the "Faturar" Ações item when status is Em Preparo', async () => {
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [pedidoEmPreparo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const vendasApi = await import('@/api/vendas')
    vi.spyOn(vendasApi, 'faturarPedido').mockResolvedValue({} as never)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped3"]').trigger('click')
    expect(wrapper.find('[data-test="acao-faturar"]').text()).toBe('Faturar')
    await wrapper.find('[data-test="acao-faturar"]').trigger('click')
    await flushPromises()

    expect(vendasApi.faturarPedido).toHaveBeenCalledWith('ped3')
    expect(pedidosApi.avancarStatusPedido).not.toHaveBeenCalled()
  })
```

to:

```typescript
  it('issues the sale via the "Faturar" Ações item when status is Em Preparo', async () => {
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [pedidoEmPreparo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const salesApi = await import('@/api/sales')
    vi.spyOn(salesApi, 'issueSale').mockResolvedValue({} as never)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped3"]').trigger('click')
    expect(wrapper.find('[data-test="acao-faturar"]').text()).toBe('Faturar')
    await wrapper.find('[data-test="acao-faturar"]').trigger('click')
    await flushPromises()

    expect(salesApi.issueSale).toHaveBeenCalledWith('ped3')
    expect(pedidosApi.avancarStatusPedido).not.toHaveBeenCalled()
  })
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run PedidosListView`
Expected: FAIL — `PedidosListView.vue` still imports `faturarPedido` from `@/api/vendas`, which no longer exists.

- [ ] **Step 3: Update `PedidosListView.vue`**

Change the import (currently `import { faturarPedido } from '@/api/vendas'`) to:

```typescript
import { issueSale } from '@/api/sales'
```

Change the `faturar` function's body (currently `await faturarPedido(pedido.id)`) to:

```typescript
async function faturar(pedido: PedidoSummary) {
  erro.value = ''
  try {
    await issueSale(pedido.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível faturar o pedido.'
  }
}
```

The function name `faturar` and the error message stay as they are — this file is still part of the not-yet-renamed `pedido` module, matching the spec's decision to keep local naming consistent with its Portuguese siblings (`avancar`, `excluir`).

- [ ] **Step 4: Update `router/index.ts`**

Change the import:

```typescript
import VendasListView from '@/views/VendasListView.vue'
```

to:

```typescript
import SalesListView from '@/views/SalesListView.vue'
```

Change the route registration:

```typescript
    { path: '/vendas', name: 'vendas', component: VendasListView },
```

to:

```typescript
    { path: '/vendas', name: 'vendas', component: SalesListView },
```

(Path and `name` stay `/vendas`/`'vendas'` — only the imported component identifier changes.)

- [ ] **Step 5: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run PedidosListView`
Expected: all tests pass (including the renamed one).

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all test files pass.

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit`
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add -A mesh-suite-frontend/src/views/PedidosListView.vue mesh-suite-frontend/src/views/__tests__/PedidosListView.spec.ts \
           mesh-suite-frontend/src/router/index.ts
git commit -m "refactor(sale): update PedidosListView and router to use the renamed sales API/view"
```

---

### Task 9: Full-suite verification and cleanup

**Files:** none (verification + final sweep only).

- [ ] **Step 1: Confirm no `venda` traces remain in source (Portuguese UI text is fine, code identifiers are not)**

Run: `grep -rl "com\.meshsuite\.venda\|VendaService\|VendaController\|VendaRepository\|class Venda\b" mesh-suite-backend/src --include="*.java"`
Expected: no output.

Run: `grep -rl "@/api/vendas\|VendasListView\|listarVendas\|faturarPedido\|buscarVenda" mesh-suite-frontend/src`
Expected: no output.

- [ ] **Step 2: Run the full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`
Expected: `BUILD SUCCESS` except the pre-existing, unrelated `payable` module flake (0 failures, 12 errors, all in `com.meshsuite.payable.*` — same signature documented in the venda-faturamento plan's Task 6/12; not caused by this rename). If the error count or module differs from that signature, stop and investigate — don't assume it's the same known issue.

- [ ] **Step 3: Run the full frontend suite and type-check**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all tests pass.

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit`
Expected: no errors.

- [ ] **Step 4: Manually smoke-check the docker-compose reset note is still accurate**

Confirm `mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql` is the only V26 file and no `V26__create_venda.sql` remains:

Run: `ls mesh-suite-backend/src/main/resources/db/migration/ | grep V26`
Expected: exactly one line, `V26__create_sale.sql`.

- [ ] **Step 5: Commit (if Steps 1-4 required any fixes) or confirm nothing to commit**

```bash
git status --short
```

If clean, no commit needed — this task is verification-only. If any fixes were required, commit them with a message describing exactly what was missed.
