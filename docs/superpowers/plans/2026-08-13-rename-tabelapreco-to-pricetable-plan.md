# Rename TabelaPreco → PriceTable (sub-project 4c) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename `TabelaPreco` and everything that belongs to it — the last piece of the old `com.meshsuite.produto` package — into its own top-level `com.meshsuite.pricetable` package, in English, without changing a single character of end-customer-visible text. After this plan, `com.meshsuite.produto` no longer exists.

**Architecture:** Same pattern as every prior sub-project in this initiative: rename the backend bottom-up (domain → repository → DTOs/service → controller), retarget the 3 existing `GlobalExceptionHandler.java` handlers that already point at TabelaPreco's exceptions, then rename the frontend's own files. This module is self-contained — no other module depends on any TabelaPreco type (only `PriceTableItem`'s own `@ManyToOne` to `Product`, already the renamed type since sub-project 4b) — so there are no cross-module bridge tasks this time.

**Tech Stack:** Spring Boot 3.4.5, Java 21, PostgreSQL 16, Flyway, Vue 3, TypeScript, Vitest.

## Global Constraints

- End-customer-visible text (frontend routes/paths, UI labels, button text, error messages returned to the user) stays in Portuguese, unchanged, character-for-character.
- Backend REST endpoint paths ARE code, not user-visible text, and DO get translated: `/api/tabelas-preco` → `/api/price-tables`.
- Frontend router `path` and route `name` stay in Portuguese (`/tabelas-preco`, `'tabelas-preco'`, `'tabelas-preco-novo'`, `'tabelas-preco-editar'`) — confirmed convention across every prior sub-project.
- `data-test` attribute values are stable test hooks, not translated identifiers — never change them.
- Query parameter names `busca` and `ativo` stay in Portuguese everywhere — never rename these two identifiers.
- Component-local variable/ref names that aren't literally a DTO field being read/written (`pagina`, `filtros`, `erro`, `itens`, `form`, `resultadosProdutos`, `produtoBusca`, local function names like `carregar`/`buscarProdutos`/`popularTodosOsProdutos`) stay as-is, matching the convention established in every prior sub-project — only the DTO/type field names they hold change.
- Field/name map (apply exactly, every task references this table):

  | Portuguese | English |
  |---|---|
  | `TabelaPreco` | `PriceTable` |
  | `TabelaPrecoItem` | `PriceTableItem` |
  | `TabelaPrecoController` | `PriceTableController` |
  | `TabelaPrecoService` | `PriceTableService` |
  | `TabelaPrecoRepository` | `PriceTableRepository` |
  | `TabelaPrecoSpecifications` | `PriceTableSpecifications` |
  | `TabelaPrecoRequest` | `PriceTableRequest` |
  | `TabelaPrecoResponse` | `PriceTableResponse` |
  | `TabelaPrecoSummaryResponse` | `PriceTableSummaryResponse` |
  | `TabelaPrecoItemInput` | `PriceTableItemInput` |
  | `TabelaPrecoItemResponse` | `PriceTableItemResponse` |
  | `TabelaPrecoExceptionHandler` | `PriceTableExceptionHandler` |
  | `TabelaPrecoNaoEncontradaException` | `PriceTableNotFoundException` |
  | `TabelaPrecoNomeDuplicadoException` | `DuplicatePriceTableNameException` |
  | `TabelaPrecoValidationException` | `PriceTableValidationException` |
  | `ModoSelecaoProdutos` (type) | `ProductSelectionMode` |
  | `ModoSelecaoProdutos.TODOS_PRODUTOS` / `.SELECIONAR_PRODUTOS` | `ProductSelectionMode.ALL_PRODUCTS` / `.SELECT_PRODUCTS` |
  | `MetodoAjuste` (type) | `AdjustmentMethod` |
  | `MetodoAjuste.AUTOMATICO` / `.MANUAL` | `AdjustmentMethod.AUTOMATIC` / `.MANUAL` |
  | `OperacaoAjuste` (type) | `AdjustmentOperation` |
  | `OperacaoAjuste.SOMAR` / `.SUBTRAIR` | `AdjustmentOperation.ADD` / `.SUBTRACT` |
  | `TipoValorAjuste` (type) | `AdjustmentValueType` |
  | `TipoValorAjuste.REAL` / `.PERCENTUAL` | `AdjustmentValueType.FIXED` / `.PERCENTAGE` |
  | `Arredondamento` (type) | `Rounding` |
  | `Arredondamento.NAO_ARREDONDAR/TERMINAR_EM_0/9/90/99` | `Rounding.NO_ROUNDING/END_IN_0/9/90/99` |
  | field `nome` | `name` |
  | field `ativo` | `active` |
  | field `criadoEm` | `createdAt` |
  | field `modoSelecaoProdutos` | `productSelectionMode` |
  | field `metodoAjuste` | `adjustmentMethod` |
  | field `operacaoAjuste` | `adjustmentOperation` |
  | field `tipoValorAjuste` | `adjustmentValueType` |
  | field `valorAjuste` | `adjustmentValue` |
  | field `arredondamento` | `rounding` |
  | field `inicioVigencia` | `effectiveStartDate` |
  | field `terminoVigencia` | `effectiveEndDate` |
  | field `valorMinimoVenda` | `minSalePrice` |
  | field `percentualComissaoPadrao` | `defaultCommissionPercentage` |
  | field `itens` | `items` |
  | `PriceTableItem`'s own field `tabelaPreco` | `priceTable` |
  | `PriceTableItem`'s own field `produto` | `product` |
  | field `precoNestaTabela` | `tablePrice` |
  | field `percentualComissao` | `commissionPercentage` |
  | DTO field `produtoId` | `productId` |
  | DTO field `produtoNome` | `productName` |
  | DTO field `produtoSku` | `productSku` |
  | DTO field `precoCadastrado` | `registeredPrice` |
  | table `tabela_preco` | `price_table` |
  | table `tabela_preco_item` | `price_table_item` |
  | other columns | snake_case of the field map above |

---

## Task 1: Migration

**Files:**
- Delete: `mesh-suite-backend/src/main/resources/db/migration/V25__create_tabela_preco.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V25__create_price_table.sql`

**Interfaces:**
- Produces: tables `price_table` (columns: `id, tenant_id, name, product_selection_mode, adjustment_method, adjustment_operation, adjustment_value_type, adjustment_value, rounding, effective_start_date, effective_end_date, min_sale_price, default_commission_percentage, active, created_at`) and `price_table_item` (columns: `id, price_table_id, product_id, table_price, commission_percentage`).

- [ ] **Step 1: Delete the old migration and create the renamed one**

```bash
git rm mesh-suite-backend/src/main/resources/db/migration/V25__create_tabela_preco.sql
```

Create `V25__create_price_table.sql` with this exact content:

```sql
CREATE TABLE price_table (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    product_selection_mode VARCHAR(20) NOT NULL
        CHECK (product_selection_mode IN ('ALL_PRODUCTS','SELECT_PRODUCTS')),
    adjustment_method VARCHAR(10) NOT NULL CHECK (adjustment_method IN ('AUTOMATIC','MANUAL')),
    adjustment_operation VARCHAR(10) CHECK (adjustment_operation IN ('ADD','SUBTRACT')),
    adjustment_value_type VARCHAR(12) CHECK (adjustment_value_type IN ('FIXED','PERCENTAGE')),
    adjustment_value NUMERIC(12,2),
    rounding VARCHAR(20) NOT NULL
        CHECK (rounding IN ('NO_ROUNDING','END_IN_0','END_IN_9','END_IN_90','END_IN_99')),
    effective_start_date DATE NOT NULL,
    effective_end_date DATE,
    min_sale_price NUMERIC(12,2),
    default_commission_percentage NUMERIC(5,2),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_tabela_preco_tenant_nome ON price_table(tenant_id, name);
CREATE INDEX idx_tabela_preco_tenant_id ON price_table(tenant_id);

ALTER TABLE price_table ENABLE ROW LEVEL SECURITY;
ALTER TABLE price_table FORCE ROW LEVEL SECURITY;

CREATE POLICY tabela_preco_tenant_isolation ON price_table
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE price_table_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    price_table_id UUID NOT NULL REFERENCES price_table(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    table_price NUMERIC(12,2),
    commission_percentage NUMERIC(5,2)
);

CREATE INDEX idx_tabela_preco_item_tabela_preco_id ON price_table_item(price_table_id);

ALTER TABLE price_table_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE price_table_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- price_table row's own RLS policy, matched by price_table_id. Same
-- pattern as purchase_order_item.
CREATE POLICY tabela_preco_item_tenant_isolation ON price_table_item
    USING (EXISTS (
        SELECT 1 FROM price_table pt
        WHERE pt.id = price_table_item.price_table_id
          AND pt.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

Note: index/policy names (`idx_tabela_preco_tenant_nome`, `tabela_preco_tenant_isolation`, etc.) are internal DB object names, not part of the field map — leave them exactly as shown above, matching how every prior sub-project left index/policy names alone during table renames.

- [ ] **Step 2: Verify by eye**

The rest of the codebase doesn't compile yet (later tasks handle that), so `mvn test` isn't useful here. Just double-check the SQL above matches this step's exact text before committing.

- [ ] **Step 3: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/
git commit -m "refactor(pricetable): rename V25 migration tabela_preco->price_table"
```

---

## Task 2: PriceTable domain, PriceTableItem, enums, repository, repository test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/domain/PriceTable.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/domain/PriceTableItem.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/domain/enums/ProductSelectionMode.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/domain/enums/AdjustmentMethod.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/domain/enums/AdjustmentOperation.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/domain/enums/AdjustmentValueType.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/domain/enums/Rounding.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/repository/PriceTableRepository.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/pricetable/repository/PriceTableRepositoryTest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/TabelaPreco.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/TabelaPrecoItem.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/ModoSelecaoProdutos.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/MetodoAjuste.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/OperacaoAjuste.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/TipoValorAjuste.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/Arredondamento.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/TabelaPrecoRepository.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/TabelaPrecoRepositoryTest.java`

**Interfaces:**
- Consumes: `com.meshsuite.product.domain.Product` (unchanged, from sub-project 4b).
- Produces: `com.meshsuite.pricetable.domain.PriceTable` (getter/setter per Lombok `@Getter`/`@Setter`, field list per the DDL), `com.meshsuite.pricetable.domain.PriceTableItem` (fields `priceTable: PriceTable`, `product: Product`, `tablePrice: BigDecimal`, `commissionPercentage: BigDecimal`), the 5 enums, `com.meshsuite.pricetable.repository.PriceTableRepository` with `existsByName(String)`, `existsByNameAndIdNot(String, UUID)`.

- [ ] **Step 1: Create the 5 enums**

`ProductSelectionMode.java`:
```java
package com.meshsuite.pricetable.domain.enums;

public enum ProductSelectionMode {
    ALL_PRODUCTS,
    SELECT_PRODUCTS
}
```

`AdjustmentMethod.java`:
```java
package com.meshsuite.pricetable.domain.enums;

public enum AdjustmentMethod {
    AUTOMATIC,
    MANUAL
}
```

`AdjustmentOperation.java`:
```java
package com.meshsuite.pricetable.domain.enums;

public enum AdjustmentOperation {
    ADD,
    SUBTRACT
}
```

`AdjustmentValueType.java`:
```java
package com.meshsuite.pricetable.domain.enums;

public enum AdjustmentValueType {
    FIXED,
    PERCENTAGE
}
```

`Rounding.java`:
```java
package com.meshsuite.pricetable.domain.enums;

public enum Rounding {
    NO_ROUNDING,
    END_IN_0,
    END_IN_9,
    END_IN_90,
    END_IN_99
}
```

- [ ] **Step 2: Create `PriceTable.java`**

```java
package com.meshsuite.pricetable.domain;

import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.pricetable.domain.enums.AdjustmentOperation;
import com.meshsuite.pricetable.domain.enums.AdjustmentValueType;
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
@Table(name = "price_table")
@Getter
@Setter
public class PriceTable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_selection_mode", nullable = false, length = 20)
    private ProductSelectionMode productSelectionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_method", nullable = false, length = 10)
    private AdjustmentMethod adjustmentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_operation", length = 10)
    private AdjustmentOperation adjustmentOperation;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_value_type", length = 12)
    private AdjustmentValueType adjustmentValueType;

    @Column(name = "adjustment_value", precision = 12, scale = 2)
    private BigDecimal adjustmentValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rounding rounding;

    @Column(name = "effective_start_date", nullable = false)
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    @Column(name = "min_sale_price", precision = 12, scale = 2)
    private BigDecimal minSalePrice;

    @Column(name = "default_commission_percentage", precision = 5, scale = 2)
    private BigDecimal defaultCommissionPercentage;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "priceTable", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PriceTableItem> items = new ArrayList<>();
}
```

- [ ] **Step 3: Create `PriceTableItem.java`**

```java
package com.meshsuite.pricetable.domain;

import com.meshsuite.product.domain.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "price_table_item")
@Getter
@Setter
public class PriceTableItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_table_id", nullable = false)
    private PriceTable priceTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "table_price", precision = 12, scale = 2)
    private BigDecimal tablePrice;

    @Column(name = "commission_percentage", precision = 5, scale = 2)
    private BigDecimal commissionPercentage;
}
```

- [ ] **Step 4: Create `PriceTableRepository.java`**

```java
package com.meshsuite.pricetable.repository;

import com.meshsuite.pricetable.domain.PriceTable;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PriceTableRepository extends JpaRepository<PriceTable, UUID>, JpaSpecificationExecutor<PriceTable> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
}
```

- [ ] **Step 5: Delete the 9 old files listed above**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/TabelaPreco.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/TabelaPrecoItem.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/ModoSelecaoProdutos.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/MetodoAjuste.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/OperacaoAjuste.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/TipoValorAjuste.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/Arredondamento.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/TabelaPrecoRepository.java
```

(`TabelaPrecoRepositoryTest.java` is replaced in the next step, not just deleted.)

- [ ] **Step 6: Create `PriceTableRepositoryTest.java`**

Translated from `TabelaPrecoRepositoryTest.java` (deleted next step). Method name translations: `savesTabelaPrecoWithDefaults`→`savesPriceTableWithDefaults`; `nomeMustBeUniquePerTenant`→`nameMustBeUniquePerTenant`; `sameNomeAllowedAcrossDifferentTenants`→`sameNameAllowedAcrossDifferentTenants`; `rlsHidesRowsWhenTenantContextUnset` unchanged.

```java
package com.meshsuite.pricetable.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.pricetable.domain.PriceTable;
import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PriceTableRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PriceTableRepository priceTableRepository;
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

    private PriceTable newPriceTable(UUID tenantId, String name) {
        PriceTable t = new PriceTable();
        t.setTenantId(tenantId);
        t.setName(name);
        t.setProductSelectionMode(ProductSelectionMode.ALL_PRODUCTS);
        t.setAdjustmentMethod(AdjustmentMethod.MANUAL);
        t.setRounding(Rounding.NO_ROUNDING);
        t.setEffectiveStartDate(LocalDate.of(2026, 1, 1));
        return t;
    }

    @Test
    @Transactional
    void savesPriceTableWithDefaults() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());

        PriceTable saved = priceTableRepository.saveAndFlush(newPriceTable(tenant.getId(), "Varejo"));
        entityManager.clear();

        PriceTable reloaded = priceTableRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActive()).isTrue();
        assertThat(reloaded.getEffectiveStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @Transactional
    void nameMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());

        priceTableRepository.saveAndFlush(newPriceTable(tenant.getId(), "Varejo"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> priceTableRepository.saveAndFlush(newPriceTable(tenant.getId(), "Varejo")));
    }

    @Test
    @Transactional
    void sameNameAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-tp");
        Tenant tenantB = createTenant("boreal-tp");

        setTenantContext(tenantA.getId());
        priceTableRepository.saveAndFlush(newPriceTable(tenantA.getId(), "Varejo"));

        setTenantContext(tenantB.getId());
        PriceTable saved = priceTableRepository.saveAndFlush(newPriceTable(tenantB.getId(), "Varejo"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());
        priceTableRepository.saveAndFlush(newPriceTable(tenant.getId(), "Varejo"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM price_table")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
```

```bash
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/TabelaPrecoRepositoryTest.java
```

- [ ] **Step 7: Run the new repository test**

The whole module still won't compile yet (Tasks 3-4 haven't bridged the rest). Use the relocate-test-restore technique: temporarily move every file that still imports anything from `com.meshsuite.produto.*` (which by this point is only the files this task hasn't yet deleted, plus `com.meshsuite.produto.dto.*`/`exception.*`/`service.*`/`repository.specification.*`/`controller.*` and their tests) out of `src/`, plus `shared/handler/GlobalExceptionHandler.java` (it imports fully-qualified exception classes from `com.meshsuite.produto.exception` that this task hasn't touched yet — either relocate it too, or temporarily strip just its 3 TabelaPreco-related handler methods and restore them after, whichever the prior sub-projects' established technique handles more cleanly). Run:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PriceTableRepositoryTest`

Expected: `BUILD SUCCESS`, 4 tests run, 0 failures, 0 errors.

Then restore every moved/patched file exactly (`git status --short` must show no diff beyond the new/deleted files from this task) before committing.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/pricetable/ \
        mesh-suite-backend/src/test/java/com/meshsuite/pricetable/
git commit -m "refactor(pricetable): rename TabelaPreco/TabelaPrecoItem/TabelaPrecoRepository domain+repo to English, new com.meshsuite.pricetable package"
```

---

## Task 3: PriceTable DTOs, exceptions, specifications, service, service test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/dto/PriceTableRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/dto/PriceTableResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/dto/PriceTableSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/dto/PriceTableItemInput.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/dto/PriceTableItemResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/exception/PriceTableNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/exception/DuplicatePriceTableNameException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/exception/PriceTableValidationException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/repository/specification/PriceTableSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/service/PriceTableService.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/pricetable/service/PriceTableServiceTest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoRequest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoResponse.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoSummaryResponse.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemInput.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemResponse.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/TabelaPrecoNaoEncontradaException.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/TabelaPrecoNomeDuplicadoException.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/TabelaPrecoValidationException.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/specification/TabelaPrecoSpecifications.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/service/TabelaPrecoService.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/produto/service/TabelaPrecoServiceTest.java`

**Interfaces:**
- Consumes: `PriceTable`, `PriceTableItem`, `PriceTableRepository` (Task 2); `com.meshsuite.product.domain.Product`, `com.meshsuite.product.repository.ProductRepository` (unchanged, from 4b).
- Produces: `com.meshsuite.pricetable.service.PriceTableService` with methods `listar(String busca, Boolean ativo, Pageable)`, `buscarPorId(UUID)`, `criar(UUID tenantId, PriceTableRequest)`, `atualizar(UUID id, PriceTableRequest)`, `excluir(UUID id)` — method names stay Portuguese, matching this initiative's established convention that an entity's own already-Portuguese service method names aren't retranslated (same treatment `ProductService` got in sub-project 4b).

- [ ] **Step 1: Create `PriceTableRequest.java`**

```java
package com.meshsuite.pricetable.dto;

import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.pricetable.domain.enums.AdjustmentOperation;
import com.meshsuite.pricetable.domain.enums.AdjustmentValueType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PriceTableRequest(
        @NotBlank String name,
        @NotNull ProductSelectionMode productSelectionMode,
        @NotNull AdjustmentMethod adjustmentMethod,
        AdjustmentOperation adjustmentOperation,
        AdjustmentValueType adjustmentValueType,
        BigDecimal adjustmentValue,
        @NotNull Rounding rounding,
        @NotNull LocalDate effectiveStartDate,
        LocalDate effectiveEndDate,
        BigDecimal minSalePrice,
        BigDecimal defaultCommissionPercentage,
        Boolean active,
        @NotNull List<@Valid PriceTableItemInput> items) {
}
```

- [ ] **Step 2: Create `PriceTableResponse.java`**

```java
package com.meshsuite.pricetable.dto;

import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.pricetable.domain.enums.AdjustmentOperation;
import com.meshsuite.pricetable.domain.enums.AdjustmentValueType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PriceTableResponse(
        UUID id,
        String name,
        ProductSelectionMode productSelectionMode,
        AdjustmentMethod adjustmentMethod,
        AdjustmentOperation adjustmentOperation,
        AdjustmentValueType adjustmentValueType,
        BigDecimal adjustmentValue,
        Rounding rounding,
        LocalDate effectiveStartDate,
        LocalDate effectiveEndDate,
        BigDecimal minSalePrice,
        BigDecimal defaultCommissionPercentage,
        Boolean active,
        Instant createdAt,
        List<PriceTableItemResponse> items) {
}
```

- [ ] **Step 3: Create `PriceTableSummaryResponse.java`**

```java
package com.meshsuite.pricetable.dto;

import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.AdjustmentOperation;
import com.meshsuite.pricetable.domain.enums.AdjustmentValueType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PriceTableSummaryResponse(
        UUID id,
        String name,
        AdjustmentMethod adjustmentMethod,
        AdjustmentOperation adjustmentOperation,
        AdjustmentValueType adjustmentValueType,
        BigDecimal adjustmentValue,
        LocalDate effectiveStartDate,
        LocalDate effectiveEndDate,
        Boolean active) {
}
```

- [ ] **Step 4: Create `PriceTableItemInput.java`**

```java
package com.meshsuite.pricetable.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceTableItemInput(
        @NotNull UUID productId,
        BigDecimal tablePrice,
        BigDecimal commissionPercentage) {
}
```

- [ ] **Step 5: Create `PriceTableItemResponse.java`**

```java
package com.meshsuite.pricetable.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceTableItemResponse(
        UUID productId,
        String productName,
        String productSku,
        BigDecimal registeredPrice,
        BigDecimal tablePrice,
        BigDecimal commissionPercentage) {
}
```

- [ ] **Step 6: Create the 3 exceptions**

`PriceTableNotFoundException.java`:
```java
package com.meshsuite.pricetable.exception;

public class PriceTableNotFoundException extends RuntimeException {
    public PriceTableNotFoundException() {
        super("Tabela de preço não encontrada");
    }
}
```

`DuplicatePriceTableNameException.java`:
```java
package com.meshsuite.pricetable.exception;

public class DuplicatePriceTableNameException extends RuntimeException {
    public DuplicatePriceTableNameException() {
        super("Já existe uma tabela de preço cadastrada com este nome");
    }
}
```

`PriceTableValidationException.java`:
```java
package com.meshsuite.pricetable.exception;

public class PriceTableValidationException extends RuntimeException {
    public PriceTableValidationException(String message) {
        super(message);
    }
}
```

(Message texts are user-visible — stay in Portuguese, unchanged.)

- [ ] **Step 7: Create `PriceTableSpecifications.java`**

```java
package com.meshsuite.pricetable.repository.specification;

import com.meshsuite.pricetable.domain.PriceTable;
import org.springframework.data.jpa.domain.Specification;

public final class PriceTableSpecifications {

    private PriceTableSpecifications() {
    }

    public static Specification<PriceTable> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), termo);
    }

    public static Specification<PriceTable> comAtivo(Boolean ativo) {
        if (ativo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), ativo);
    }
}
```

(This fixes the dangling `root.get("nome")`/`root.get("ativo")` string literals in place, since those fields are becoming `name`/`active` in this same task.)

- [ ] **Step 8: Create `PriceTableService.java`**

```java
package com.meshsuite.pricetable.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.pricetable.domain.PriceTable;
import com.meshsuite.pricetable.domain.PriceTableItem;
import com.meshsuite.pricetable.dto.*;
import com.meshsuite.pricetable.exception.PriceTableNotFoundException;
import com.meshsuite.pricetable.exception.DuplicatePriceTableNameException;
import com.meshsuite.pricetable.exception.PriceTableValidationException;
import com.meshsuite.pricetable.repository.PriceTableRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.pricetable.repository.specification.PriceTableSpecifications;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceTableService {

    private final PriceTableRepository tabelaPrecoRepository;
    private final ProductRepository produtoRepository;

    public PriceTableService(PriceTableRepository tabelaPrecoRepository, ProductRepository produtoRepository) {
        this.tabelaPrecoRepository = tabelaPrecoRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<PriceTableSummaryResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        Specification<PriceTable> spec = Specification.allOf(
                PriceTableSpecifications.comBusca(busca),
                PriceTableSpecifications.comAtivo(ativo));
        return tabelaPrecoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public PriceTableResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public PriceTableResponse criar(UUID tenantId, PriceTableRequest request) {
        validarNome(request.name(), null);

        PriceTable tabelaPreco = new PriceTable();
        tabelaPreco.setTenantId(tenantId);
        aplicar(tabelaPreco, request);
        return toResponse(tabelaPrecoRepository.saveAndFlush(tabelaPreco));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public PriceTableResponse atualizar(UUID id, PriceTableRequest request) {
        validarNome(request.name(), id);

        PriceTable tabelaPreco = buscarEntidadePorId(id);
        aplicar(tabelaPreco, request);
        return toResponse(tabelaPrecoRepository.saveAndFlush(tabelaPreco));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        tabelaPrecoRepository.delete(buscarEntidadePorId(id));
    }

    private PriceTable buscarEntidadePorId(UUID id) {
        return tabelaPrecoRepository.findById(id).orElseThrow(PriceTableNotFoundException::new);
    }

    private void validarNome(String name, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? tabelaPrecoRepository.existsByName(name)
                : tabelaPrecoRepository.existsByNameAndIdNot(name, idAtual);
        if (duplicado) {
            throw new DuplicatePriceTableNameException();
        }
    }

    // Clears and rebuilds the whole item list on every save -- same
    // "regenerate everything" funnel PurchaseOrderService.apply() uses.
    // No price calculation happens here: tablePrice/commissionPercentage
    // are persisted exactly as the client sent them (see Global Constraints).
    private void aplicar(PriceTable tabelaPreco, PriceTableRequest request) {
        tabelaPreco.setName(request.name());
        tabelaPreco.setProductSelectionMode(request.productSelectionMode());
        tabelaPreco.setAdjustmentMethod(request.adjustmentMethod());
        tabelaPreco.setAdjustmentOperation(request.adjustmentOperation());
        tabelaPreco.setAdjustmentValueType(request.adjustmentValueType());
        tabelaPreco.setAdjustmentValue(request.adjustmentValue());
        tabelaPreco.setRounding(request.rounding());
        tabelaPreco.setEffectiveStartDate(request.effectiveStartDate());
        tabelaPreco.setEffectiveEndDate(request.effectiveEndDate());
        tabelaPreco.setMinSalePrice(request.minSalePrice());
        tabelaPreco.setDefaultCommissionPercentage(request.defaultCommissionPercentage());
        tabelaPreco.setActive(request.active() != null ? request.active() : true);

        tabelaPreco.getItems().clear();
        for (PriceTableItemInput itemInput : request.items()) {
            Product produto = produtoRepository.findById(itemInput.productId())
                    .orElseThrow(() -> new PriceTableValidationException("Produto não encontrado"));
            PriceTableItem item = new PriceTableItem();
            item.setPriceTable(tabelaPreco);
            item.setProduct(produto);
            item.setTablePrice(itemInput.tablePrice());
            item.setCommissionPercentage(itemInput.commissionPercentage());
            tabelaPreco.getItems().add(item);
        }
    }

    private PriceTableSummaryResponse toSummary(PriceTable t) {
        return new PriceTableSummaryResponse(t.getId(), t.getName(), t.getAdjustmentMethod(), t.getAdjustmentOperation(),
                t.getAdjustmentValueType(), t.getAdjustmentValue(), t.getEffectiveStartDate(), t.getEffectiveEndDate(), t.getActive());
    }

    private PriceTableResponse toResponse(PriceTable t) {
        List<PriceTableItemResponse> items = t.getItems().stream()
                .map(i -> new PriceTableItemResponse(i.getProduct().getId(), i.getProduct().getName(),
                        i.getProduct().getSku(), i.getProduct().getSalePrice(), i.getTablePrice(),
                        i.getCommissionPercentage()))
                .toList();
        return new PriceTableResponse(t.getId(), t.getName(), t.getProductSelectionMode(), t.getAdjustmentMethod(),
                t.getAdjustmentOperation(), t.getAdjustmentValueType(), t.getAdjustmentValue(), t.getRounding(),
                t.getEffectiveStartDate(), t.getEffectiveEndDate(), t.getMinSalePrice(), t.getDefaultCommissionPercentage(),
                t.getActive(), t.getCreatedAt(), items);
    }
}
```

Note the constructor/field names (`tabelaPrecoRepository`, `produtoRepository`) and local var names (`tabelaPreco`, `produto`) are kept exactly as they were before this task — matching the same "own-entity-service keeps its established local-naming style" convention `ProductService` used in sub-project 4b.

- [ ] **Step 9: Delete the 10 produto-package equivalents**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoRequest.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoSummaryResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemInput.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/TabelaPrecoNaoEncontradaException.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/TabelaPrecoNomeDuplicadoException.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/TabelaPrecoValidationException.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/specification/TabelaPrecoSpecifications.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/service/TabelaPrecoService.java
```

- [ ] **Step 10: Create `PriceTableServiceTest.java`**

Translated from `TabelaPrecoServiceTest.java` (deleted next step). Method name translations: `criaERecuperaTabelaPrecoComItens`→`createsAndRetrievesPriceTableWithItems`; `rejectsDuplicateNomeOnCreate`→`rejectsDuplicateNameOnCreate`; `rejectsItemWithUnknownProduto`→`rejectsItemWithUnknownProduct`; `deletesTabelaPrecoAndCascadesItems`→`deletesPriceTableAndCascadesItems`; `listFiltersByAtivo`→`listFiltersByActive`; `doesNotRecalculatePricesServerSide` and `updateReplacesTheWholeItemList` unchanged.

```java
package com.meshsuite.pricetable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.pricetable.dto.PriceTableItemInput;
import com.meshsuite.pricetable.dto.PriceTableRequest;
import com.meshsuite.pricetable.exception.PriceTableNotFoundException;
import com.meshsuite.pricetable.exception.DuplicatePriceTableNameException;
import com.meshsuite.pricetable.exception.PriceTableValidationException;
import com.meshsuite.pricetable.service.PriceTableService;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

class PriceTableServiceTest extends AbstractIntegrationTest {

    @Autowired PriceTableService tabelaPrecoService;
    @Autowired ProductRepository produtoRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User caller = new User();
        caller.setTenantId(tenant.getId());
        caller.setName("Marina");
        caller.setEmail(codigo + "@aurora.com.br");
        caller.setPasswordHash("hash");
        caller.setRole(Role.ADMIN);
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private Product novoProduto(UUID tenantId, String sku, BigDecimal precoVenda) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Produto " + sku);
        p.setSku(sku);
        p.setSalePrice(precoVenda);
        return produtoRepository.saveAndFlush(p);
    }

    private PriceTableRequest request(String nome, List<PriceTableItemInput> itens) {
        return new PriceTableRequest(nome, ProductSelectionMode.SELECT_PRODUCTS, AdjustmentMethod.MANUAL,
                null, null, null, Rounding.NO_ROUNDING, LocalDate.of(2026, 1, 1), null, null, null, null, itens);
    }

    @Test
    @Transactional
    void createsAndRetrievesPriceTableWithItems() {
        UUID tenantId = setUpTenant("aurora-tp");
        Product produto = novoProduto(tenantId, "P0001", new BigDecimal("59.90"));

        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new PriceTableItemInput(produto.getId(), new BigDecimal("69.90"), new BigDecimal("5.00")))));

        var buscada = tabelaPrecoService.buscarPorId(criada.id());
        assertThat(buscada.name()).isEqualTo("Varejo");
        assertThat(buscada.items()).hasSize(1);
        assertThat(buscada.items().get(0).productId()).isEqualTo(produto.getId());
        assertThat(buscada.items().get(0).tablePrice()).isEqualByComparingTo("69.90");
        assertThat(buscada.items().get(0).registeredPrice()).isEqualByComparingTo("59.90");
    }

    @Test
    @Transactional
    void doesNotRecalculatePricesServerSide() {
        // Global Constraints: the backend persists exactly what the client sends,
        // even a price wildly different from produto.salePrice -- there is no
        // server-side formula to disagree with the client.
        UUID tenantId = setUpTenant("aurora-tp");
        Product produto = novoProduto(tenantId, "P0001", new BigDecimal("10.00"));

        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Promo", List.of(new PriceTableItemInput(produto.getId(), new BigDecimal("999.99"), null))));

        assertThat(criada.items().get(0).tablePrice()).isEqualByComparingTo("999.99");
    }

    @Test
    @Transactional
    void rejectsDuplicateNameOnCreate() {
        setUpTenant("aurora-tp");
        tabelaPrecoService.criar(TenantContext.get(), request("Varejo", List.of()));

        assertThatThrownBy(() -> tabelaPrecoService.criar(TenantContext.get(), request("Varejo", List.of())))
                .isInstanceOf(DuplicatePriceTableNameException.class);
    }

    @Test
    @Transactional
    void updateReplacesTheWholeItemList() {
        UUID tenantId = setUpTenant("aurora-tp");
        Product produtoA = novoProduto(tenantId, "P0001", new BigDecimal("10.00"));
        Product produtoB = novoProduto(tenantId, "P0002", new BigDecimal("20.00"));

        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new PriceTableItemInput(produtoA.getId(), new BigDecimal("15.00"), null))));

        var atualizada = tabelaPrecoService.atualizar(criada.id(),
                request("Varejo", List.of(new PriceTableItemInput(produtoB.getId(), new BigDecimal("25.00"), null))));

        assertThat(atualizada.items()).hasSize(1);
        assertThat(atualizada.items().get(0).productId()).isEqualTo(produtoB.getId());
    }

    @Test
    @Transactional
    void rejectsItemWithUnknownProduct() {
        setUpTenant("aurora-tp");
        UUID produtoInexistente = UUID.randomUUID();

        assertThatThrownBy(() -> tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new PriceTableItemInput(produtoInexistente, new BigDecimal("10.00"), null)))))
                .isInstanceOf(PriceTableValidationException.class);
    }

    @Test
    @Transactional
    void deletesPriceTableAndCascadesItems() {
        UUID tenantId = setUpTenant("aurora-tp");
        Product produto = novoProduto(tenantId, "P0001", new BigDecimal("10.00"));
        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new PriceTableItemInput(produto.getId(), new BigDecimal("15.00"), null))));

        tabelaPrecoService.excluir(criada.id());

        assertThatThrownBy(() -> tabelaPrecoService.buscarPorId(criada.id()))
                .isInstanceOf(PriceTableNotFoundException.class);
    }

    @Test
    @Transactional
    void listFiltersByActive() {
        setUpTenant("aurora-tp");
        var requestAtiva = new PriceTableRequest("Ativa", ProductSelectionMode.SELECT_PRODUCTS, AdjustmentMethod.MANUAL,
                null, null, null, Rounding.NO_ROUNDING, LocalDate.of(2026, 1, 1), null, null, null, true, List.of());
        var requestInativa = new PriceTableRequest("Inativa", ProductSelectionMode.SELECT_PRODUCTS, AdjustmentMethod.MANUAL,
                null, null, null, Rounding.NO_ROUNDING, LocalDate.of(2026, 1, 1), null, null, null, false, List.of());
        tabelaPrecoService.criar(TenantContext.get(), requestAtiva);
        tabelaPrecoService.criar(TenantContext.get(), requestInativa);

        var ativas = tabelaPrecoService.listar(null, true, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("name").containsExactly("Ativa");
    }
}
```

```bash
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/service/TabelaPrecoServiceTest.java
```

- [ ] **Step 11: Run the new service test in isolation**

Use relocate-test-restore (same set as Task 2 Step 7, minus `pricetable/` itself) to verify:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PriceTableServiceTest`

Expected: `BUILD SUCCESS`, 7 tests run, 0 failures, 0 errors. Restore all moved/patched files, confirm `git diff --stat` empty for anything outside this task's own changes.

- [ ] **Step 12: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/pricetable/ \
        mesh-suite-backend/src/test/java/com/meshsuite/pricetable/
git commit -m "refactor(pricetable): rename TabelaPreco DTOs, exceptions, specifications, and service layer to English"
```

---

## Task 4: PriceTable controller, exception handler, controller test, GlobalExceptionHandler bridge

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/controller/PriceTableController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pricetable/exception/PriceTableExceptionHandler.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/pricetable/controller/PriceTableControllerTest.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/TabelaPrecoController.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/TabelaPrecoExceptionHandler.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/TabelaPrecoControllerTest.java`

**Interfaces:**
- Consumes: `PriceTableService` (Task 3), `AuthContextService` (unchanged).
- Produces: REST endpoint at `/api/price-tables` (was `/api/tabelas-preco`).

This task also retargets `GlobalExceptionHandler.java`'s 3 existing TabelaPreco handlers (added back in sub-project 4a) — unlike every prior sub-project, this is NOT a cross-module bridge: these are TabelaPreco's OWN exception classes moving package, so it's in-scope for this task, not deferred.

- [ ] **Step 1: Create `PriceTableController.java`**

```java
package com.meshsuite.pricetable.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.pricetable.dto.PriceTableRequest;
import com.meshsuite.pricetable.dto.PriceTableResponse;
import com.meshsuite.pricetable.dto.PriceTableSummaryResponse;
import com.meshsuite.pricetable.service.PriceTableService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/price-tables")
public class PriceTableController {

    private final PriceTableService tabelaPrecoService;

    public PriceTableController(PriceTableService tabelaPrecoService) {
        this.tabelaPrecoService = tabelaPrecoService;
    }

    @GetMapping
    public Page<PriceTableSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return tabelaPrecoService.listar(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public PriceTableResponse buscarPorId(@PathVariable UUID id) {
        return tabelaPrecoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<PriceTableResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                        @Valid @RequestBody PriceTableRequest request) {
        PriceTableResponse response = tabelaPrecoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PriceTableResponse atualizar(@PathVariable UUID id, @Valid @RequestBody PriceTableRequest request) {
        return tabelaPrecoService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        tabelaPrecoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Create `PriceTableExceptionHandler.java`**

```java
package com.meshsuite.pricetable.exception;

import com.meshsuite.pricetable.controller.PriceTableController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PriceTableController.class)
public class PriceTableExceptionHandler {

    // Fallback for a race condition slipping past PriceTableService's pre-check
    // (two concurrent requests for the same new name) -- the DB's
    // UNIQUE(tenant_id, name) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma tabela de preço cadastrada com este nome"));
    }
}
```

- [ ] **Step 3: Bridge `GlobalExceptionHandler.java`'s 3 TabelaPreco handlers**

Change:
```java
    @ExceptionHandler(com.meshsuite.produto.exception.TabelaPrecoNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleTabelaPrecoNaoEncontrada(
            com.meshsuite.produto.exception.TabelaPrecoNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.TabelaPrecoNomeDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleTabelaPrecoNomeDuplicado(
            com.meshsuite.produto.exception.TabelaPrecoNomeDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.TabelaPrecoValidationException.class)
    public ResponseEntity<Map<String, String>> handleTabelaPrecoValidation(
            com.meshsuite.produto.exception.TabelaPrecoValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```
to:
```java
    @ExceptionHandler(com.meshsuite.pricetable.exception.PriceTableNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePriceTableNotFound(
            com.meshsuite.pricetable.exception.PriceTableNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pricetable.exception.DuplicatePriceTableNameException.class)
    public ResponseEntity<Map<String, String>> handleDuplicatePriceTableName(
            com.meshsuite.pricetable.exception.DuplicatePriceTableNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pricetable.exception.PriceTableValidationException.class)
    public ResponseEntity<Map<String, String>> handlePriceTableValidation(
            com.meshsuite.pricetable.exception.PriceTableValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

Every other handler in the file (Partner, Pedido, PurchaseOrder, Stock, AccountsPayable, Category, Colorway, Sale, Product) stays untouched.

- [ ] **Step 4: Delete the 2 produto-package equivalents**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/TabelaPrecoController.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/TabelaPrecoExceptionHandler.java
```

- [ ] **Step 5: Create `PriceTableControllerTest.java`**

Translated from `TabelaPrecoControllerTest.java` (deleted next step). Method name translations: `createsListsUpdatesAndDeletesTabelaPreco`→`createsListsUpdatesAndDeletesPriceTable`; `rejectsDuplicateNomeWithConflict`→`rejectsDuplicateNameWithConflict`; `rejectsMissingInicioVigenciaWithBadRequest`→`rejectsMissingEffectiveStartDateWithBadRequest`; `tenantACannotAccessTenantBsTabelaPreco`→`tenantACannotAccessTenantBsPriceTable`; `unauthenticatedRequestIsRejected` and `listingWithoutProductViewPermissionIsForbidden` unchanged. JSON payload keys change to match the renamed record fields (`nome`→`name`, `modoSelecaoProdutos`→`productSelectionMode`, `metodoAjuste`→`adjustmentMethod`, `arredondamento`→`rounding`, `inicioVigencia`→`effectiveStartDate`, `itens`→`items`, `produtoId`→`productId`, `precoNestaTabela`→`tablePrice`, `percentualComissao`→`commissionPercentage`; enum wire values `SELECIONAR_PRODUTOS`→`SELECT_PRODUCTS`, `NAO_ARREDONDAR`→`NO_ROUNDING`, `MANUAL` unchanged). URL paths change from `/api/tabelas-preco` to `/api/price-tables`. The local `Contexto` record and its `produtoId` component, and the `produtoRepository` field, stay as-is (test scaffolding referencing the `Product` fixture, not TabelaPreco's own field).

```java
package com.meshsuite.pricetable.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
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

@Transactional
class PriceTableControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository produtoRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, String produtoId) {
    }

    private Contexto loginAndSetUp(String codigo, String email, String companyCnpj) throws Exception {
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
        user.setName("Marina");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.ADMIN);
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.CREATE));
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.EDIT));
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.DELETE));
        userRepository.saveAndFlush(user);

        Product produto = new Product();
        produto.setTenantId(tenant.getId());
        produto.setName("Camiseta Polo");
        produto.setSku("P0001");
        produto.setSalePrice(new BigDecimal("59.90"));
        produtoRepository.saveAndFlush(produto);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Contexto(token, produto.getId().toString());
    }

    private String loginWithoutProductPermission(String codigo, String email, String companyCnpj) throws Exception {
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

    private String tabelaPrecoPayload(String nome, String produtoId) {
        return """
                {
                  "name": "%s",
                  "productSelectionMode": "SELECT_PRODUCTS",
                  "adjustmentMethod": "MANUAL",
                  "rounding": "NO_ROUNDING",
                  "effectiveStartDate": "2026-01-01",
                  "items": [
                    { "productId": "%s", "tablePrice": 69.90, "commissionPercentage": 5.00 }
                  ]
                }
                """.formatted(nome, produtoId);
    }

    @Test
    void createsListsUpdatesAndDeletesPriceTable() throws Exception {
        Contexto ctx = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/price-tables").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctx.produtoId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Varejo"))
                .andExpect(jsonPath("$.items[0].tablePrice").value(69.90))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/price-tables").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Varejo"));

        mockMvc.perform(put("/api/price-tables/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo Atualizado", ctx.produtoId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Varejo Atualizado"));

        mockMvc.perform(delete("/api/price-tables/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/price-tables/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateNameWithConflict() throws Exception {
        Contexto ctx = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/price-tables").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctx.produtoId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/price-tables").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctx.produtoId())))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingEffectiveStartDateWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/price-tables").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sem Vigência",
                                  "productSelectionMode": "SELECT_PRODUCTS",
                                  "adjustmentMethod": "MANUAL",
                                  "rounding": "NO_ROUNDING",
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsPriceTable() throws Exception {
        Contexto ctxA = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/price-tables").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctxA.produtoId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        Contexto ctxB = loginAndSetUp("boreal-tp", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404.
        entityManager.clear();

        mockMvc.perform(get("/api/price-tables/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/price-tables"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao-tp", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/price-tables").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

```bash
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/TabelaPrecoControllerTest.java
```

- [ ] **Step 6: Run the new controller test in isolation**

At this point every backend main-source file has been touched (Tasks 1-4 cover the full main-source dependency graph — this sub-project has no cross-module bridges), so the whole backend module should compile. No relocate-test-restore is needed for this step.

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PriceTableControllerTest`

Expected: `BUILD SUCCESS`, 6 tests run, 0 failures, 0 errors.

- [ ] **Step 7: Run the FULL backend test suite**

Run: `cd mesh-suite-backend && mvn -q clean test`

Expected: 0 failures. Errors should match the documented pre-existing flake exactly — 15 errors (3 `CompanyRepositoryTest` + 3 `AccountsPayableControllerTest` + 1 `AccountsPayableRepositoryTest` + 8 `AccountsPayableServiceTest`), confirmed identical after every prior sub-project's merge. If the error count or the specific failing classes differ from this signature, investigate before proceeding — do not assume it's the same flake without checking `target/surefire-reports/*.txt` for the exact class names.

Also confirm: `find mesh-suite-backend/src -type d -path "*com/meshsuite/produto*"` returns nothing — `com.meshsuite.produto` no longer exists anywhere in the backend.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/pricetable/ \
        mesh-suite-backend/src/test/java/com/meshsuite/pricetable/ \
        mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java
git commit -m "refactor(pricetable): rename TabelaPreco controller and exception handler to English, route tabelas-preco->price-tables, retarget GlobalExceptionHandler"
```

---

## Task 5: Frontend — `api/tabelasPreco.ts` → `api/priceTables.ts`

**Files:**
- Create: `mesh-suite-frontend/src/api/priceTables.ts`
- Delete: `mesh-suite-frontend/src/api/tabelasPreco.ts`

**Interfaces:**
- Produces: `ProductSelectionMode`, `AdjustmentMethod`, `AdjustmentOperation`, `AdjustmentValueType`, `Rounding`, `PriceTableItemInput`, `PriceTableItemResponse`, `PriceTableRequest`, `PriceTableResponse`, `PriceTableSummary`, `Page<T>`, `ListPriceTablesParams`, functions `listPriceTables`, `getPriceTable`, `createPriceTable`, `updatePriceTable`, `deletePriceTable`.

- [ ] **Step 1: Create `priceTables.ts`**

```typescript
import { apiClient } from './client'

export type ProductSelectionMode = 'ALL_PRODUCTS' | 'SELECT_PRODUCTS'
export type AdjustmentMethod = 'AUTOMATIC' | 'MANUAL'
export type AdjustmentOperation = 'ADD' | 'SUBTRACT'
export type AdjustmentValueType = 'FIXED' | 'PERCENTAGE'
export type Rounding = 'NO_ROUNDING' | 'END_IN_0' | 'END_IN_9' | 'END_IN_90' | 'END_IN_99'

export interface PriceTableItemInput {
  productId: string
  tablePrice: number | null
  commissionPercentage: number | null
}

export interface PriceTableItemResponse extends PriceTableItemInput {
  productName: string
  productSku: string
  registeredPrice: number
}

export interface PriceTableRequest {
  name: string
  productSelectionMode: ProductSelectionMode
  adjustmentMethod: AdjustmentMethod
  adjustmentOperation: AdjustmentOperation | null
  adjustmentValueType: AdjustmentValueType | null
  adjustmentValue: number | null
  rounding: Rounding
  effectiveStartDate: string
  effectiveEndDate: string | null
  minSalePrice: number | null
  defaultCommissionPercentage: number | null
  active: boolean | null
  items: PriceTableItemInput[]
}

export interface PriceTableResponse {
  id: string
  name: string
  productSelectionMode: ProductSelectionMode
  adjustmentMethod: AdjustmentMethod
  adjustmentOperation: AdjustmentOperation | null
  adjustmentValueType: AdjustmentValueType | null
  adjustmentValue: number | null
  rounding: Rounding
  effectiveStartDate: string
  effectiveEndDate: string | null
  minSalePrice: number | null
  defaultCommissionPercentage: number | null
  active: boolean
  createdAt: string
  items: PriceTableItemResponse[]
}

export interface PriceTableSummary {
  id: string
  name: string
  adjustmentMethod: AdjustmentMethod
  adjustmentOperation: AdjustmentOperation | null
  adjustmentValueType: AdjustmentValueType | null
  adjustmentValue: number | null
  effectiveStartDate: string
  effectiveEndDate: string | null
  active: boolean
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListPriceTablesParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listPriceTables(params: ListPriceTablesParams): Promise<Page<PriceTableSummary>> {
  const { data } = await apiClient.get<Page<PriceTableSummary>>('/price-tables', { params })
  return data
}

export async function getPriceTable(id: string): Promise<PriceTableResponse> {
  const { data } = await apiClient.get<PriceTableResponse>(`/price-tables/${id}`)
  return data
}

export async function createPriceTable(payload: PriceTableRequest): Promise<PriceTableResponse> {
  const { data } = await apiClient.post<PriceTableResponse>('/price-tables', payload)
  return data
}

export async function updatePriceTable(id: string, payload: PriceTableRequest): Promise<PriceTableResponse> {
  const { data } = await apiClient.put<PriceTableResponse>(`/price-tables/${id}`, payload)
  return data
}

export async function deletePriceTable(id: string): Promise<void> {
  await apiClient.delete(`/price-tables/${id}`)
}
```

(`busca`/`ativo` stay as the query param names per Global Constraints.)

- [ ] **Step 2: Delete the old file**

```bash
git rm mesh-suite-frontend/src/api/tabelasPreco.ts
```

- [ ] **Step 3: Verify the new file type-checks standalone**

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: errors from every other file that still imports `@/api/tabelasPreco` (Tasks 7-9 haven't run yet) — expected at this point. Confirm the errors are ONLY "Cannot find module '@/api/tabelasPreco'" in files this plan will touch later, not a syntax/type error inside `priceTables.ts` itself.

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-frontend/src/api/priceTables.ts
git commit -m "refactor(pricetable): rename frontend api/tabelasPreco.ts to priceTables.ts"
```

---

## Task 6: Frontend — `utils/calculoTabelaPreco.ts` → `utils/priceCalculation.ts`

**Files:**
- Create: `mesh-suite-frontend/src/utils/priceCalculation.ts`
- Create: `mesh-suite-frontend/src/utils/__tests__/priceCalculation.spec.ts`
- Delete: `mesh-suite-frontend/src/utils/calculoTabelaPreco.ts`
- Delete: `mesh-suite-frontend/src/utils/__tests__/calculoTabelaPreco.spec.ts`

**Interfaces:**
- Produces: `AdjustmentRule` (type), `calculateAdjustedPrice(precoBase: number, regra: AdjustmentRule): number`.

This is a pure calculation module with no dependency on `api/priceTables.ts` or any Vue component — purely self-contained logic, translated for the same reason every other TabelaPreco-only file is: it's pricing-table logic, not tied to any other module.

- [ ] **Step 1: Create `priceCalculation.ts`**

```typescript
export type AdjustmentOperation = 'ADD' | 'SUBTRACT'
export type AdjustmentValueType = 'FIXED' | 'PERCENTAGE'
export type Rounding = 'NO_ROUNDING' | 'END_IN_0' | 'END_IN_9' | 'END_IN_90' | 'END_IN_99'

export interface AdjustmentRule {
  adjustmentOperation: AdjustmentOperation
  adjustmentValueType: AdjustmentValueType
  adjustmentValue: number
  rounding: Rounding
}

// Rounding always goes UP (never below the adjusted price). Every candidate
// value in a rounding rule's set is `k * period + offset` for integer k >= 0,
// expressed in cents to avoid floating-point drift:
//   NO_ROUNDING:  no candidate set, value returned as-is (rounded to the cent)
//   END_IN_0:   period=1000, offset=0    (...,100.00, 110.00, 120.00,...)
//   END_IN_9:   period=1000, offset=900  (...,99.00, 109.00, 119.00,...)
//   END_IN_90:  period=100,  offset=90   (...,ends in ,90)
//   END_IN_99:  period=100,  offset=99   (...,ends in ,99)
const ROUNDING_RULES: Record<Exclude<Rounding, 'NO_ROUNDING'>, { period: number; offset: number }> = {
  END_IN_0: { period: 1000, offset: 0 },
  END_IN_9: { period: 1000, offset: 900 },
  END_IN_90: { period: 100, offset: 90 },
  END_IN_99: { period: 100, offset: 99 },
}

function roundUp(valor: number, rounding: Rounding): number {
  const centavos = Math.round(valor * 100)
  if (rounding === 'NO_ROUNDING') {
    return centavos / 100
  }
  const { period, offset } = ROUNDING_RULES[rounding]
  const k = Math.ceil((centavos - offset) / period)
  const alvoCentavos = k * period + offset
  return alvoCentavos / 100
}

export function calculateAdjustedPrice(precoBase: number, regra: AdjustmentRule): number {
  let ajustado: number
  if (regra.adjustmentOperation === 'ADD') {
    ajustado = regra.adjustmentValueType === 'FIXED'
      ? precoBase + regra.adjustmentValue
      : precoBase * (1 + regra.adjustmentValue / 100)
  } else {
    ajustado = regra.adjustmentValueType === 'FIXED'
      ? precoBase - regra.adjustmentValue
      : precoBase * (1 - regra.adjustmentValue / 100)
  }
  return roundUp(ajustado, regra.rounding)
}
```

(Internal local variable/parameter names — `valor`, `centavos`, `alvoCentavos`, `precoBase`, `ajustado`, `regra` — stay as-is, matching this initiative's established convention of only translating exported identifiers and DTO/type field names, not every internal local variable.)

- [ ] **Step 2: Create `priceCalculation.spec.ts`**

```typescript
import { describe, it, expect } from 'vitest'
import { calculateAdjustedPrice, type AdjustmentRule } from '../priceCalculation'

describe('calculateAdjustedPrice', () => {
  it('somar + real, sem arredondamento', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 10, rounding: 'NO_ROUNDING' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(110, 2)
  })

  it('somar + percentual, sem arredondamento', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'PERCENTAGE', adjustmentValue: 10, rounding: 'NO_ROUNDING' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(110, 2)
  })

  it('subtrair + real, sem arredondamento', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'SUBTRACT', adjustmentValueType: 'FIXED', adjustmentValue: 10, rounding: 'NO_ROUNDING' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(90, 2)
  })

  it('subtrair + percentual, sem arredondamento', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'SUBTRACT', adjustmentValueType: 'PERCENTAGE', adjustmentValue: 20, rounding: 'NO_ROUNDING' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(80, 2)
  })

  it('terminar em 0 arredonda pra cima', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_0' }
    expect(calculateAdjustedPrice(117.32, regra)).toBeCloseTo(120, 2)
  })

  it('terminar em 9 arredonda pra cima', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_9' }
    expect(calculateAdjustedPrice(117.32, regra)).toBeCloseTo(119, 2)
  })

  it('terminar em ,90 arredonda pra cima', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_90' }
    expect(calculateAdjustedPrice(117.32, regra)).toBeCloseTo(117.90, 2)
  })

  it('terminar em ,99 arredonda pra cima', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_99' }
    expect(calculateAdjustedPrice(117.32, regra)).toBeCloseTo(117.99, 2)
  })

  it('valor exato já na regra não muda', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_0' }
    expect(calculateAdjustedPrice(120, regra)).toBeCloseTo(120, 2)
  })

  it('combina ajuste percentual com arredondamento terminar em 9', () => {
    // base 100, +12% = 112.00 -> arredonda pra próximo terminando em 9 (119.00)
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'PERCENTAGE', adjustmentValue: 12, rounding: 'END_IN_9' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(119, 2)
  })
})
```

(Test description strings stay as-is — they're prose, not code identifiers.)

- [ ] **Step 3: Delete the 2 old files**

```bash
git rm mesh-suite-frontend/src/utils/calculoTabelaPreco.ts \
       mesh-suite-frontend/src/utils/__tests__/calculoTabelaPreco.spec.ts
```

- [ ] **Step 4: Run the new test**

Run: `cd mesh-suite-frontend && npx vitest run src/utils/__tests__/priceCalculation.spec.ts`

Expected: 10 tests pass.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/utils/priceCalculation.ts \
        mesh-suite-frontend/src/utils/__tests__/priceCalculation.spec.ts
git rm mesh-suite-frontend/src/utils/calculoTabelaPreco.ts \
       mesh-suite-frontend/src/utils/__tests__/calculoTabelaPreco.spec.ts
git commit -m "refactor(pricetable): rename frontend utils/calculoTabelaPreco.ts to priceCalculation.ts"
```

---

## Task 7: Frontend — `TabelaPrecoFormView.vue` → `PriceTableFormView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/PriceTableFormView.vue`
- Create: `mesh-suite-frontend/src/views/__tests__/PriceTableFormView.spec.ts`
- Delete: `mesh-suite-frontend/src/views/TabelaPrecoFormView.vue`
- Delete: `mesh-suite-frontend/src/views/__tests__/TabelaPrecoFormView.spec.ts`

**Interfaces:**
- Consumes: `priceTables.ts` (Task 5) — `getPriceTable`, `createPriceTable`, `updatePriceTable`, `type PriceTableRequest`, `type PriceTableItemInput`; `priceCalculation.ts` (Task 6) — `calculateAdjustedPrice`, `type AdjustmentRule`; `products.ts` (unchanged, from 4b) — `listProducts`, `type ProductListItem`.

This is PriceTable's own screen (not a borrowed view), renamed like every prior sub-project's own screens. Read `mesh-suite-frontend/src/views/TabelaPrecoFormView.vue`'s CURRENT content yourself before editing — every visible Portuguese string (titles, labels, placeholders, button text, error messages, option labels like "Todos os Produtos"/"Automático"/"Não arredondar") and every `data-test` attribute value must stay character-for-character identical. Route names (`'tabelas-preco'`) stay unchanged. Only code identifiers (imported names, TypeScript types, object/DTO field names, and the raw enum WIRE VALUES used in `v-model`/comparisons/click-handlers, e.g. `'AUTOMATICO'`, `'SOMAR'`, `'REAL'`, `'NAO_ARREDONDAR'`, `'TODOS_PRODUTOS'`, `'SELECIONAR_PRODUTOS'`) change.

- [ ] **Step 1: Create `PriceTableFormView.vue`**

Apply these exact substitutions to a copy of the current `TabelaPrecoFormView.vue`:

**Import block** — change:
```typescript
import {
  buscarTabelaPreco,
  criarTabelaPreco,
  atualizarTabelaPreco,
  type TabelaPrecoRequest,
  type TabelaPrecoItemInput,
} from '@/api/tabelasPreco'
import { listProducts, type ProductListItem } from '@/api/products'
import { calcularPrecoAjustado, type RegraAjuste } from '@/utils/calculoTabelaPreco'
```
to:
```typescript
import {
  getPriceTable,
  createPriceTable,
  updatePriceTable,
  type PriceTableRequest,
  type PriceTableItemInput,
} from '@/api/priceTables'
import { listProducts, type ProductListItem } from '@/api/products'
import { calculateAdjustedPrice, type AdjustmentRule } from '@/utils/priceCalculation'
```

**`interface ItemForm`**: `extends TabelaPrecoItemInput` → `extends PriceTableItemInput`; its own extra fields `produtoNome: string; produtoSku: string; precoCadastrado: number` → `productName: string; productSku: string; registeredPrice: number`.

**`function novoFormulario(): TabelaPrecoRequest`** → `: PriceTableRequest`; every key in its returned object: `nome`→`name`, `modoSelecaoProdutos`→`productSelectionMode` (value `'TODOS_PRODUTOS'`→`'ALL_PRODUCTS'`), `metodoAjuste`→`adjustmentMethod` (value `'AUTOMATICO'`→`'AUTOMATIC'`), `operacaoAjuste`→`adjustmentOperation` (value `'SOMAR'`→`'ADD'`), `tipoValorAjuste`→`adjustmentValueType` (value `'REAL'`→`'FIXED'`), `valorAjuste`→`adjustmentValue`, `arredondamento`→`rounding` (value `'NAO_ARREDONDAR'`→`'NO_ROUNDING'`), `inicioVigencia`→`effectiveStartDate`, `terminoVigencia`→`effectiveEndDate`, `valorMinimoVenda`→`minSalePrice`, `percentualComissaoPadrao`→`defaultCommissionPercentage`, `ativo`→`active`, `itens`→`items`.

**`const form = reactive<TabelaPrecoRequest>(...)`** → `reactive<PriceTableRequest>(...)`. Local variable name `form` and every other top-level ref/reactive name (`itens`, `erros`, `erroGeral`, `salvando`, `produtoBusca`, `resultadosProdutos`, `filtroPreenchimento`, `itensExibidos`) stays unchanged — only their generic type parameters and the object keys/DTO fields they hold change.

**`erros = reactive<{ nome?: string; inicioVigencia?: string }>({})`** → `reactive<{ name?: string; effectiveStartDate?: string }>({})`.

**`itensExibidos` computed**: every `item.precoNestaTabela` → `item.tablePrice` (3 occurrences inside the filter body).

**`margem(item: ItemForm)`**: `item.precoCadastrado` → `item.registeredPrice`; `item.precoNestaTabela` → `item.tablePrice` (2 occurrences).

**`regraAtual(): RegraAjuste`** → `: AdjustmentRule`; return object: `operacaoAjuste: form.operacaoAjuste ?? 'SOMAR'`→`adjustmentOperation: form.adjustmentOperation ?? 'ADD'`, `tipoValorAjuste: form.tipoValorAjuste ?? 'REAL'`→`adjustmentValueType: form.adjustmentValueType ?? 'FIXED'`, `valorAjuste: form.valorAjuste ?? 0`→`adjustmentValue: form.adjustmentValue ?? 0`, `arredondamento: form.arredondamento`→`rounding: form.rounding`.

**`precoParaNovoItem(precoBase: number): number | null`** (name/param stay): `form.metodoAjuste === 'AUTOMATICO'`→`form.adjustmentMethod === 'AUTOMATIC'`; `calcularPrecoAjustado(precoBase, regraAtual())`→`calculateAdjustedPrice(precoBase, regraAtual())`.

**`watch(...)`**: dependency array `[form.metodoAjuste, form.operacaoAjuste, form.tipoValorAjuste, form.valorAjuste, form.arredondamento]`→`[form.adjustmentMethod, form.adjustmentOperation, form.adjustmentValueType, form.adjustmentValue, form.rounding]`; body `if (form.modoSelecaoProdutos !== 'TODOS_PRODUTOS')`→`if (form.productSelectionMode !== 'ALL_PRODUCTS')`; `{ ...item, precoNestaTabela: precoParaNovoItem(item.precoCadastrado) }`→`{ ...item, tablePrice: precoParaNovoItem(item.registeredPrice) }`.

**`popularTodosOsProdutos()`** (name stays): `listProducts({ status: 'ACTIVE', size: 1000 })` unchanged; mapped object: `{ produtoId: p.id, produtoNome: p.name, produtoSku: p.sku, precoCadastrado: p.salePrice, precoNestaTabela: precoParaNovoItem(p.salePrice), percentualComissao: form.percentualComissaoPadrao }`→`{ productId: p.id, productName: p.name, productSku: p.sku, registeredPrice: p.salePrice, tablePrice: precoParaNovoItem(p.salePrice), commissionPercentage: form.defaultCommissionPercentage }`.

**`aoMudarModoSelecao()`** (name stays): `if (form.modoSelecaoProdutos === 'TODOS_PRODUTOS')`→`if (form.productSelectionMode === 'ALL_PRODUCTS')`.

**`buscarProdutos()`** (name stays): `listProducts({ busca: produtoBusca.value, status: 'ACTIVE', size: 5 })` unchanged; `.some((i) => i.produtoId === p.id)`→`.some((i) => i.productId === p.id)`.

**`adicionarProduto(produto: ProductListItem)`** (name/param stay): pushed object `{ produtoId: produto.id, produtoNome: produto.name, produtoSku: produto.sku, precoCadastrado: produto.salePrice, precoNestaTabela: precoParaNovoItem(produto.salePrice), percentualComissao: form.percentualComissaoPadrao }`→`{ productId: produto.id, productName: produto.name, productSku: produto.sku, registeredPrice: produto.salePrice, tablePrice: precoParaNovoItem(produto.salePrice), commissionPercentage: form.defaultCommissionPercentage }`.

**`removerItem`/`resetarItem`** (names stay): `item.precoNestaTabela = precoParaNovoItem(item.precoCadastrado)`→`item.tablePrice = precoParaNovoItem(item.registeredPrice)`.

**`onMounted`**: `await buscarTabelaPreco(id)`→`await getPriceTable(id)`; every `form.X = tabela.Y` assignment follows the same field map (`form.nome = tabela.nome`→`form.name = tabela.name`; `form.modoSelecaoProdutos = tabela.modoSelecaoProdutos`→`form.productSelectionMode = tabela.productSelectionMode`; `form.metodoAjuste`→`form.adjustmentMethod`; `form.operacaoAjuste`→`form.adjustmentOperation`; `form.tipoValorAjuste`→`form.adjustmentValueType`; `form.valorAjuste`→`form.adjustmentValue`; `form.arredondamento`→`form.rounding`; `form.inicioVigencia`→`form.effectiveStartDate`; `form.terminoVigencia`→`form.effectiveEndDate`; `form.valorMinimoVenda`→`form.minSalePrice`; `form.percentualComissaoPadrao`→`form.defaultCommissionPercentage`; `form.ativo`→`form.active`); `itens.value = tabela.itens.map(...)`→`tabela.items.map(...)` with the mapped object's keys following the same field map (`produtoId`→`productId`, `produtoNome`→`productName`, `produtoSku`→`productSku`, `precoCadastrado`→`registeredPrice`, `precoNestaTabela`→`tablePrice`, `percentualComissao`→`commissionPercentage`); `else if (form.modoSelecaoProdutos === 'TODOS_PRODUTOS')`→`form.productSelectionMode === 'ALL_PRODUCTS'`.

**`validar()`**: `erros.nome = form.nome.trim() ? ...`→`erros.name = form.name.trim() ? ...`; `erros.inicioVigencia = form.inicioVigencia ? ...`→`erros.effectiveStartDate = form.effectiveStartDate ? ...`; `return !erros.nome && !erros.inicioVigencia`→`!erros.name && !erros.effectiveStartDate`.

**`paraPayload(): TabelaPrecoRequest`** → `: PriceTableRequest`; `valorAjuste: form.metodoAjuste === 'AUTOMATICO' ? Number(form.valorAjuste) || 0 : null`→`adjustmentValue: form.adjustmentMethod === 'AUTOMATIC' ? Number(form.adjustmentValue) || 0 : null`; `itens: itens.value.map(({ produtoId, precoNestaTabela, percentualComissao }) => ({ produtoId, precoNestaTabela, percentualComissao }))`→`items: itens.value.map(({ productId, tablePrice, commissionPercentage }) => ({ productId, tablePrice, commissionPercentage }))`.

**`salvar()`**: `await atualizarTabelaPreco(id, payload)`→`await updatePriceTable(id, payload)`; `await criarTabelaPreco(payload)`→`await createPriceTable(payload)`. Route push `{ name: 'tabelas-preco' }` unchanged.

**Template**: every `form.X`/`item.X`/`erros.X` binding in `<template>` follows the exact same field-name substitutions as above (`v-model="form.nome"`→`v-model="form.name"`, `erros.nome`→`erros.name` ×2, `v-model="form.modoSelecaoProdutos"`→`v-model="form.productSelectionMode"` with option `value="TODOS_PRODUTOS"`→`value="ALL_PRODUCTS"` and `value="SELECIONAR_PRODUTOS"`→`value="SELECT_PRODUCTS"` — labels "Todos os Produtos"/"Selecionar os Produtos" unchanged; `form.metodoAjuste === 'AUTOMATICO'`→`form.adjustmentMethod === 'AUTOMATIC'` and its click-handler assignment, `form.metodoAjuste === 'MANUAL'`→`form.adjustmentMethod === 'MANUAL'` and its click-handler (value `'MANUAL'` itself unchanged); `v-if="form.metodoAjuste === 'AUTOMATICO'"`→`v-if="form.adjustmentMethod === 'AUTOMATIC'"`; `form.operacaoAjuste === 'SOMAR'`→`form.adjustmentOperation === 'ADD'` and click-handler, `form.operacaoAjuste === 'SUBTRAIR'`→`form.adjustmentOperation === 'SUBTRACT'` and click-handler; `form.tipoValorAjuste === 'REAL'`→`form.adjustmentValueType === 'FIXED'` and click-handler, `form.tipoValorAjuste === 'PERCENTUAL'`→`form.adjustmentValueType === 'PERCENTAGE'` and click-handler; `v-model.number="form.valorAjuste"`→`v-model.number="form.adjustmentValue"`; `v-model="form.arredondamento"`→`v-model="form.rounding"` with option values `NAO_ARREDONDAR/TERMINAR_EM_0/TERMINAR_EM_9/TERMINAR_EM_90/TERMINAR_EM_99`→`NO_ROUNDING/END_IN_0/END_IN_9/END_IN_90/END_IN_99` (labels "Não arredondar"/"Terminar em 0"/"Terminar em 9"/"Terminar em ,90"/"Terminar em ,99" unchanged); `v-model="form.inicioVigencia"`→`v-model="form.effectiveStartDate"`, `erros.inicioVigencia`→`erros.effectiveStartDate`; `v-model="form.terminoVigencia"`→`v-model="form.effectiveEndDate"`; `v-model.number="form.valorMinimoVenda"`→`v-model.number="form.minSalePrice"`; `v-model.number="form.percentualComissaoPadrao"`→`v-model.number="form.defaultCommissionPercentage"`; `v-if="form.modoSelecaoProdutos === 'SELECIONAR_PRODUTOS'"`→`v-if="form.productSelectionMode === 'SELECT_PRODUCTS'"`; `:key="item.produtoId"`→`:key="item.productId"`; `{{ item.produtoNome }}`→`{{ item.productName }}`; `{{ item.produtoSku }}`→`{{ item.productSku }}`; `formatarPreco(item.precoCadastrado)`→`formatarPreco(item.registeredPrice)`; `v-model.number="item.precoNestaTabela"`→`v-model.number="item.tablePrice"`; `v-model.number="item.percentualComissao"`→`v-model.number="item.commissionPercentage"`.

Every visible text node, every `data-test` attribute value, every CSS class name (including `toggle-btn--ativo`, `badge-ATIVO`, `badge-INATIVO` if present in this file), the `:title="modoEdicao ? 'Editar Tabela de Preço' : 'Nova Tabela de Preço'"` binding, and all `<style scoped>` content are byte-identical to the original — nothing in this paragraph changes.

- [ ] **Step 2: Create `PriceTableFormView.spec.ts`**

Translate `TabelaPrecoFormView.spec.ts` mechanically: import path `@/views/TabelaPrecoFormView.vue`→`@/views/PriceTableFormView.vue`; `import * as tabelasPrecoApi from '@/api/tabelasPreco'`→`'@/api/priceTables'` (keep the local alias `tabelasPrecoApi` unchanged); `vi.mock('@/api/tabelasPreco')`→`vi.mock('@/api/priceTables')`; router route `component: TabelaPrecoFormView`→`component: PriceTableFormView` (2 occurrences); every `tabelasPrecoApi.criarTabelaPreco`→`tabelasPrecoApi.createPriceTable`, `tabelasPrecoApi.atualizarTabelaPreco`→`tabelasPrecoApi.updatePriceTable`, `tabelasPrecoApi.buscarTabelaPreco`→`tabelasPrecoApi.getPriceTable`; the `expect.objectContaining({ nome: 'Varejo', inicioVigencia: '2026-01-01' })` assertion→`{ name: 'Varejo', effectiveStartDate: '2026-01-01' }`; the mocked `buscarTabelaPreco` resolved value's keys all follow the field map (`id, name, productSelectionMode: 'SELECT_PRODUCTS', adjustmentMethod: 'MANUAL', adjustmentOperation: null, adjustmentValueType: null, adjustmentValue: null, rounding: 'NO_ROUNDING', effectiveStartDate, effectiveEndDate: null, minSalePrice: null, defaultCommissionPercentage: null, active: true, createdAt, items: [{ productId, productName, productSku, registeredPrice, tablePrice, commissionPercentage }]`); `wrapper.find('[data-test="modo-selecao"]').setValue('SELECIONAR_PRODUTOS')`→`.setValue('SELECT_PRODUCTS')` (every occurrence — this is a raw wire value sent through the select, not a label); `produtoAtivo` fixture (already has English `Product`-shaped fields from sub-project 4b: `name`, `sku`, `brand`, `salePrice`, `stockQuantity`, `status`) stays unchanged. Every `data-test` selector value and every assertion against Portuguese UI text (e.g. `'Já existe uma tabela de preço cadastrada com este nome'`) stays unchanged.

- [ ] **Step 3: Delete the 2 old files**

```bash
git rm mesh-suite-frontend/src/views/TabelaPrecoFormView.vue \
       mesh-suite-frontend/src/views/__tests__/TabelaPrecoFormView.spec.ts
```

- [ ] **Step 4: Run the new test in isolation**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PriceTableFormView.spec.ts`

Expected: all 9 tests pass, matching the original file's pass count exactly (pure translation, no test logic changes). If a test fails, it's almost certainly a missed field-name substitution — fix and rerun.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/PriceTableFormView.vue \
        mesh-suite-frontend/src/views/__tests__/PriceTableFormView.spec.ts
git commit -m "refactor(pricetable): rename TabelaPrecoFormView to PriceTableFormView"
```

---

## Task 8: Frontend — `TabelasPrecoListView.vue` → `PriceTablesListView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/PriceTablesListView.vue`
- Create: `mesh-suite-frontend/src/views/__tests__/PriceTablesListView.spec.ts`
- Delete: `mesh-suite-frontend/src/views/TabelasPrecoListView.vue`
- Delete: `mesh-suite-frontend/src/views/__tests__/TabelasPrecoListView.spec.ts`

**Interfaces:**
- Consumes: `priceTables.ts` (Task 5) — `listPriceTables`, `deletePriceTable`, `type PriceTableSummary`, `type Page`.

Same rules as Task 7: every visible Portuguese string, every `data-test` value, and route names/paths stay unchanged. Read `mesh-suite-frontend/src/views/TabelasPrecoListView.vue`'s CURRENT content before editing.

- [ ] **Step 1: Create `PriceTablesListView.vue`**

Apply these exact substitutions to a copy of the current `TabelasPrecoListView.vue`:

**Import block** — change:
```typescript
import {
  listarTabelasPreco,
  excluirTabelaPreco,
  type TabelaPrecoSummary,
  type Page as ApiPage,
} from '@/api/tabelasPreco'
```
to:
```typescript
import {
  listPriceTables,
  deletePriceTable,
  type PriceTableSummary,
  type Page as ApiPage,
} from '@/api/priceTables'
```

**`pagina = ref<ApiPage<TabelaPrecoSummary>>(...)`** → `ref<ApiPage<PriceTableSummary>>(...)`.

**`resumoMetodoAjuste(tabela: TabelaPrecoSummary)`** → `(tabela: PriceTableSummary)`; body: `tabela.metodoAjuste === 'MANUAL'`→`tabela.adjustmentMethod === 'MANUAL'` (value unchanged); `tabela.operacaoAjuste === 'SUBTRAIR' ? 'Subtrair' : 'Somar'`→`tabela.adjustmentOperation === 'SUBTRACT' ? 'Subtrair' : 'Somar'` (Portuguese labels unchanged); `tabela.tipoValorAjuste === 'PERCENTUAL'`→`tabela.adjustmentValueType === 'PERCENTAGE'`; both remaining `tabela.valorAjuste` references→`tabela.adjustmentValue`.

**`carregar(page)`**: `pagina.value = await listarTabelasPreco({...})`→`await listPriceTables({...})` (the params object's `busca`/`ativo` keys stay unchanged).

**`excluir(tabela)`**: `await excluirTabelaPreco(tabela.id)`→`await deletePriceTable(tabela.id)`; confirm-dialog text `` `Excluir a tabela de preço "${tabela.nome}"?` `` → `` `Excluir a tabela de preço "${tabela.nome}"?` `` becomes `` `Excluir a tabela de preço "${tabela.name}"?` `` — only the interpolated property changes, the surrounding Portuguese sentence is byte-identical.

**Template**: `v-for="tabela in pagina.content"` unchanged (local loop-variable name); `{{ tabela.nome }}`→`{{ tabela.name }}`; `resumoMetodoAjuste(tabela)` call unchanged; `formatarData(tabela.inicioVigencia)`→`formatarData(tabela.effectiveStartDate)`; `tabela.terminoVigencia ? formatarData(tabela.terminoVigencia) : '—'`→`tabela.effectiveEndDate ? formatarData(tabela.effectiveEndDate) : '—'`; `tabela.ativo ? 'badge-ATIVO' : 'badge-INATIVO'`→`tabela.active ? 'badge-ATIVO' : 'badge-INATIVO'` (the CSS class name strings `'badge-ATIVO'`/`'badge-INATIVO'` themselves are internal styling identifiers, NOT part of the field map — leave them exactly as-is, only the `tabela.ativo` source property changes to `tabela.active`); `{{ tabela.ativo ? 'Ativo' : 'Inativo' }}`→`{{ tabela.active ? 'Ativo' : 'Inativo' }}` (labels unchanged). The status-filter `<select v-model="filtros.ativo">` and its `value="true"`/`value="false"` options are UNCHANGED — `filtros.ativo` is the `ativo` query-parameter binding (stays Portuguese per Global Constraints), a completely different thing from the per-row `tabela.ativo` entity field that's changing above.

Every visible text node, every `data-test` value, every CSS class name, and all `<style scoped>` content are byte-identical to the original.

- [ ] **Step 2: Create `PriceTablesListView.spec.ts`**

Translate `TabelasPrecoListView.spec.ts` mechanically: import path `@/views/TabelasPrecoListView.vue`→`@/views/PriceTablesListView.vue`; `import * as tabelasPrecoApi from '@/api/tabelasPreco'`→`'@/api/priceTables'` (alias unchanged); `vi.mock('@/api/tabelasPreco')`→`vi.mock('@/api/priceTables')`; router route `component: TabelasPrecoListView`→`component: PriceTablesListView`; the `tabelaExemplo` fixture: `{ id: 'tp-1', nome: 'Varejo', metodoAjuste: 'AUTOMATICO' as const, operacaoAjuste: 'SOMAR' as const, tipoValorAjuste: 'REAL' as const, valorAjuste: 10, inicioVigencia: '2026-01-01', terminoVigencia: null, ativo: true }`→`{ id: 'tp-1', name: 'Varejo', adjustmentMethod: 'AUTOMATIC' as const, adjustmentOperation: 'ADD' as const, adjustmentValueType: 'FIXED' as const, adjustmentValue: 10, effectiveStartDate: '2026-01-01', effectiveEndDate: null, active: true }`; every `tabelasPrecoApi.listarTabelasPreco`→`tabelasPrecoApi.listPriceTables`; `tabelasPrecoApi.excluirTabelaPreco`→`tabelasPrecoApi.deletePriceTable`; the fixture override `{ ...tabelaExemplo, metodoAjuste: 'MANUAL', operacaoAjuste: null, tipoValorAjuste: null, valorAjuste: null }`→`{ ...tabelaExemplo, adjustmentMethod: 'MANUAL', adjustmentOperation: null, adjustmentValueType: null, adjustmentValue: null }`. Every `data-test` selector and every assertion against Portuguese UI text (`'Varejo'`, `'Automático · Somar'`, `'Manual'`, `'Não foi possível carregar a lista de tabelas de preço.'`) stays unchanged.

- [ ] **Step 3: Delete the 2 old files**

```bash
git rm mesh-suite-frontend/src/views/TabelasPrecoListView.vue \
       mesh-suite-frontend/src/views/__tests__/TabelasPrecoListView.spec.ts
```

- [ ] **Step 4: Run the new test in isolation**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PriceTablesListView.spec.ts`

Expected: all 5 tests pass, matching the original file's pass count exactly.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/PriceTablesListView.vue \
        mesh-suite-frontend/src/views/__tests__/PriceTablesListView.spec.ts
git commit -m "refactor(pricetable): rename TabelasPrecoListView to PriceTablesListView"
```

---

## Task 9: Frontend bridge — `router/index.ts`

**Files:**
- Modify: `mesh-suite-frontend/src/router/index.ts`

**Interfaces:**
- Consumes: `PriceTableFormView.vue` (Task 7), `PriceTablesListView.vue` (Task 8).

`AppSidebar.vue` needs NO change — it only holds the unchanged route path `/tabelas-preco` and the unchanged Portuguese label `'Tab. Preços'`.

- [ ] **Step 1: Bridge `router/index.ts`**

Change:
```typescript
import TabelasPrecoListView from '@/views/TabelasPrecoListView.vue'
import TabelaPrecoFormView from '@/views/TabelaPrecoFormView.vue'
```
to:
```typescript
import PriceTablesListView from '@/views/PriceTablesListView.vue'
import PriceTableFormView from '@/views/PriceTableFormView.vue'
```

Change:
```typescript
    { path: '/tabelas-preco', name: 'tabelas-preco', component: TabelasPrecoListView },
    { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: TabelaPrecoFormView },
    { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: TabelaPrecoFormView },
```
to:
```typescript
    { path: '/tabelas-preco', name: 'tabelas-preco', component: PriceTablesListView },
    { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: PriceTableFormView },
    { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: PriceTableFormView },
```

(`path`/`name` values unchanged per Global Constraints — only the imported component identifiers change.)

- [ ] **Step 2: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run --run`

Expected: all test files pass (44 files total — same count as before this sub-project started, since Tasks 6-8 each renamed 1 file 1-for-1).

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: 0 errors — this is the first point in this sub-project where the frontend is fully green end-to-end. (Do not omit `-p tsconfig.app.json` — bare `vue-tsc --noEmit` silently reports 0 errors regardless of real breakage in this project.)

Also confirm: `find mesh-suite-frontend/src -iname "*tabelapreco*" -o -iname "*tabelaspreco*"` returns nothing.

- [ ] **Step 3: Commit**

```bash
git add mesh-suite-frontend/src/router/index.ts
git commit -m "refactor(pricetable): update router to consume the renamed PriceTable views"
```

---

## Task 10: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`

Expected: 0 failures. Errors matching the documented pre-existing flake exactly (15 errors: 3 `CompanyRepositoryTest` + 3 `AccountsPayableControllerTest` + 1 `AccountsPayableRepositoryTest` + 8 `AccountsPayableServiceTest`) — confirm via `find target/surefire-reports -name "*.txt" | xargs grep -h "^Tests run:" | awk -F'[ ,]+' '{tests+=$3; failures+=$5; errors+=$7} END {print tests, failures, errors}'` and cross-check the specific failing class names against the signature documented in this initiative's memory. If anything differs, investigate before proceeding.

- [ ] **Step 2: Full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run --run`

Expected: all test files pass, 0 failures.

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: 0 errors.

- [ ] **Step 3: `com.meshsuite.produto` no longer exists**

Run: `find mesh-suite-backend/src -type d -path "*com/meshsuite/produto*"`

Expected: no output — the directory tree is gone entirely (both `main` and `test`).

- [ ] **Step 4: Broad grep audit for missed `TabelaPreco`/`tabela_preco`/`tabela-preco` identifiers**

Run: `grep -rln "TabelaPreco\b\|ModoSelecaoProdutos\|MetodoAjuste\|OperacaoAjuste\|TipoValorAjuste\|Arredondamento\b" mesh-suite-backend/src --include="*.java"`

Expected: no output (Portuguese prose inside comments or user-visible error-message string literals is fine and expected — only bare-word occurrences of the renamed class/type identifiers are a problem).

Run the same style of grep across `mesh-suite-frontend/src` for `TabelaPreco`/`RegraAjuste`/`calcularPrecoAjustado`/`@/api/tabelasPreco`/`@/utils/calculoTabelaPreco` — expected: no output.

- [ ] **Step 5: Dangling-property-string-literal re-sweep**

Run: `grep -rn "\"nome\"\|\"ativo\"\|'nome'\|'ativo'" mesh-suite-backend/src/main/java/com/meshsuite/pricetable mesh-suite-frontend/src/views/PriceTable*.vue mesh-suite-frontend/src/utils/priceCalculation.ts`

For every hit, confirm it's already correctly using the new English field names (`root.get("name")`, `root.get("active")`) — this scope is narrow (only this sub-project's own files) since there are no cross-module bridges to re-check this time.

Also run: `grep -n "sort = \"nome\"\|sort=\"nome\"" mesh-suite-backend/src/main/java/com/meshsuite/pricetable/controller/PriceTableController.java` — expected: no output (`@PageableDefault(sort = "name")` should already be correct from Task 4).

- [ ] **Step 6: Confirm no leftover verification-technique artifacts**

Run: `git status --short`

Expected: clean — confirms every relocate-test-restore cycle across Tasks 2-4 fully restored its moved/patched files.

Report PLAN COMPLETE once all 6 verification steps pass.
