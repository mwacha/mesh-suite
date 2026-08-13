# Rename Produto → Product (sub-project 4b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the `Produto` entity and everything that belongs to it (domain, repository, controller, service, DTOs, specifications, exceptions, enums, migrations, frontend) from Portuguese to English, moving it into its own top-level `com.meshsuite.product` package, while bridging every cross-module consumer (`category`, `colorway`, `pedido`, `purchaseorder`, `sale`, `stock`, and the not-yet-renamed `TabelaPreco`) to compile and pass against the new types — all without changing a single character of end-customer-visible text.

**Architecture:** Same pattern as the completed Sale/Company/Partner/Category/Colorway sub-projects: rename `Produto`'s own files bottom-up (domain → repository → DTOs/service → controller), then bridge every external consumer with a minimal type/getter-name swap (never touching the consumer's own identifiers), then rename the frontend's own files and bridge frontend consumers, then verify the whole suite.

**Tech Stack:** Spring Boot 3.4.5, Java 21, PostgreSQL 16, Flyway, Vue 3, TypeScript, Vitest.

## Global Constraints

- End-customer-visible text (frontend routes/paths, UI labels, button text, error messages returned to the user) stays in Portuguese, unchanged, character-for-character.
- Backend REST endpoint paths (`@RequestMapping`) ARE code, not user-visible text, and DO get translated — confirmed by every prior sub-project (`/api/categories`, `/api/partners`, `/api/colorways`, `/api/sales`). `/api/produtos` → `/api/products`.
- Frontend router `path` and route `name` stay in Portuguese (confirmed convention: Category/Colorway kept `path: '/categorias'`, `name: 'categorias'` even though the Vue component is `CategoryFormView`). Do not translate `/produtos` route paths or `'produtos'`/`'produtos-novo'`/`'produtos-editar'` route names anywhere in this plan.
- Query parameter names `busca` and `ativo` stay in Portuguese everywhere (confirmed intentional, cross-checked in the 4a final review) — never rename these two identifiers.
- `TabelaPreco`, `TabelaPrecoItem`, their DTOs/service/controller/repository/specifications/exceptions, and the enums `Arredondamento`/`MetodoAjuste`/`ModoSelecaoProdutos`/`OperacaoAjuste`/`TipoValorAjuste` stay in the `com.meshsuite.produto` package — do not rename any of them in this plan. They receive only the minimal bridge edits called out in Task 10.
- `pedido` module (Pedido, ItemPedido, PedidoService, and their own DTOs/fields like `produtoId`/`produtoNome`) is not being renamed in this plan — bridge only, per Task 6.
- Field/name map (apply exactly, every task references this table):

  | Portuguese | English |
  |---|---|
  | `Produto` | `Product` |
  | `ProdutoController` | `ProductController` |
  | `ProdutoService` | `ProductService` |
  | `ProdutoRepository` | `ProductRepository` |
  | `ProdutoSpecifications` | `ProductSpecifications` |
  | `ProdutoRequest` | `ProductRequest` |
  | `ProdutoResponse` | `ProductResponse` |
  | `ProdutoStatusRequest` | `ProductStatusRequest` |
  | `ProdutoResumoResponse` | `ProductSummaryResponse` |
  | `ProdutoSummaryResponse` | `ProductListItemResponse` |
  | `ProdutoExceptionHandler` | `ProductExceptionHandler` |
  | `ProdutoNaoEncontradoException` | `ProductNotFoundException` |
  | `SkuDuplicadoException` | `DuplicateSkuException` |
  | `StatusProduto` (enum type) | `ProductStatus` |
  | `StatusProduto.ATIVO` / `.INATIVO` | `ProductStatus.ACTIVE` / `.INACTIVE` |
  | `UnidadeMedida` (enum type) | `MeasurementUnit` (values unchanged: UN, KG, G, L, ML, MT, CM, CX, PC, PAR, DZ) |
  | `ProdutoRepository.CategoriaProdutoCount` | `ProductRepository.CategoryProductCount` |
  | `ProdutoRepository.CorEstampaProdutoCount` | `ProductRepository.ColorwayProductCount` |
  | field `nome` | `name` |
  | field `codigoBarras` | `barcode` |
  | field `marca` | `brand` |
  | field `categoria` / `categoriaId` / `categoriaNome` | `category` / `categoryId` / `categoryName` |
  | field `corEstampa` / `corEstampaId` / `corEstampaNome` | `colorway` / `colorwayId` / `colorwayName` |
  | field `precoVenda` | `salePrice` |
  | field `precoCusto` | `costPrice` |
  | field `descricao` | `description` |
  | field `quantidadeEstoque` | `stockQuantity` |
  | field `unidadeMedida` | `measurementUnit` |
  | field `estoqueMinimo` | `minStock` |
  | field `estoqueMaximo` | `maxStock` |
  | field `peso` | `weight` |
  | field `comprimento` | `length` |
  | field `largura` | `width` |
  | field `altura` | `height` |
  | field `criadoEm` | `createdAt` |
  | `sku`, `id` | unchanged |
  | table `produto` | `product` |
  | column `categoria_id` | `category_id` |
  | column `cor_estampa_id` | `colorway_id` |
  | other columns | snake_case of the field map above |

---

## Task 1: Migrations

**Files:**
- Modify (rename): `mesh-suite-backend/src/main/resources/db/migration/V6__create_produto.sql` → `V6__create_product.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V18__add_fiscal_registration_to_produto.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V22__replace_produto_categoria_with_fk.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V24__add_cor_estampa_to_produto.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V7__create_pedido.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V11__create_purchase_order.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V13__create_stock_movement.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V25__create_tabela_preco.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql`

**Interfaces:**
- Produces: table `product` with columns `id, tenant_id, name, sku, barcode, brand, category_id, colorway_id, sale_price, cost_price, status, description, stock_quantity, measurement_unit, min_stock, max_stock, weight, length, width, height, created_at, fiscal_registration_id`. `status` CHECK values `'ACTIVE'`/`'INACTIVE'`.

- [ ] **Step 1: Rename and rewrite V6**

Delete `V6__create_produto.sql`, create `V6__create_product.sql` with this exact content:

```sql
CREATE TABLE product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    barcode VARCHAR(50),
    brand VARCHAR(100),
    categoria VARCHAR(100),
    sale_price NUMERIC(12,2) NOT NULL,
    cost_price NUMERIC(12,2),
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    description TEXT,
    stock_quantity NUMERIC(12,3) NOT NULL DEFAULT 0,
    measurement_unit VARCHAR(5) NOT NULL DEFAULT 'UN'
        CHECK (measurement_unit IN ('UN','KG','G','L','ML','MT','CM','CX','PC','PAR','DZ')),
    min_stock NUMERIC(12,3),
    max_stock NUMERIC(12,3),
    weight NUMERIC(10,3),
    length NUMERIC(10,2),
    width NUMERIC(10,2),
    height NUMERIC(10,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_produto_tenant_sku ON product(tenant_id, sku);
CREATE INDEX idx_produto_tenant_id ON product(tenant_id);

ALTER TABLE product ENABLE ROW LEVEL SECURITY;
ALTER TABLE product FORCE ROW LEVEL SECURITY;

CREATE POLICY produto_tenant_isolation ON product
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

Note: index/policy names (`idx_produto_tenant_sku`, `produto_tenant_isolation`, etc.) are internal DB object names, not part of the field map — leave them exactly as shown above; do not rename them (matches how prior sub-projects left index/policy names alone during table renames, e.g. `V5`/`V21`/`V23`).

**Correction found during Task 1's review:** the DDL above now includes `categoria VARCHAR(100),` (plain-text column, kept in Portuguese and untranslated — it's dropped by V22 two steps later, never queried by application code). The original plan draft omitted this column entirely, which would have broken V22's `ALTER TABLE product DROP COLUMN categoria;` (nothing to drop) the moment Flyway replayed the migration chain. This column existed in the original `V6__create_produto.sql` as a leftover plain-text category field, superseded by the `categoria_id`/`category_id` FK added in V22 — it must exist in V6 purely so V22 has something to drop.

- [ ] **Step 2: Update V18 (adds fiscal_registration_id) to target the renamed table**

Change `V18__add_fiscal_registration_to_produto.sql`'s only line from:

```sql
ALTER TABLE produto ADD COLUMN fiscal_registration_id UUID REFERENCES fiscal_registration(id);
```

to:

```sql
ALTER TABLE product ADD COLUMN fiscal_registration_id UUID REFERENCES fiscal_registration(id);
```

Do not rename the filename (it doesn't create the `produto`/`product` table, only alters it — same pattern as `V22`/`V24` below).

- [ ] **Step 3: Update V22 (categoria FK) — table name AND column name**

Change `V22__replace_produto_categoria_with_fk.sql` from:

```sql
ALTER TABLE produto DROP COLUMN categoria;
ALTER TABLE produto ADD COLUMN categoria_id UUID REFERENCES category(id);
```

to:

```sql
ALTER TABLE product DROP COLUMN categoria;
ALTER TABLE product ADD COLUMN category_id UUID REFERENCES category(id);
```

- [ ] **Step 4: Update V24 (cor_estampa FK) — table name AND column name**

Change `V24__add_cor_estampa_to_produto.sql` from:

```sql
ALTER TABLE produto ADD COLUMN cor_estampa_id UUID REFERENCES colorway(id);
```

to:

```sql
ALTER TABLE product ADD COLUMN colorway_id UUID REFERENCES colorway(id);
```

- [ ] **Step 5: Update the 5 migrations that only reference `produto` as a foreign-key target**

These five migrations create a DIFFERENT table and merely point a FK column at `produto(id)`. Only the `REFERENCES` target changes — their own column names (already decided to leave Pedido/PurchaseOrder/Stock/TabelaPreco/Sale's own naming untouched) stay exactly as they are.

`V7__create_pedido.sql` line 43, change:
```sql
    produto_id UUID NOT NULL REFERENCES produto(id),
```
to:
```sql
    produto_id UUID NOT NULL REFERENCES product(id),
```

`V11__create_purchase_order.sql` line 43, change:
```sql
    product_id UUID NOT NULL REFERENCES produto(id),
```
to:
```sql
    product_id UUID NOT NULL REFERENCES product(id),
```

`V13__create_stock_movement.sql` line 4, change:
```sql
    product_id UUID NOT NULL REFERENCES produto(id),
```
to:
```sql
    product_id UUID NOT NULL REFERENCES product(id),
```

`V25__create_tabela_preco.sql` line 34, change:
```sql
    produto_id UUID NOT NULL REFERENCES produto(id),
```
to:
```sql
    produto_id UUID NOT NULL REFERENCES product(id),
```
(Leave line 5-6's `modo_selecao_produtos` column/CHECK untouched — that's TabelaPreco's own column, deferred to 4c.)

`V26__create_sale.sql` line 47, change:
```sql
    product_id UUID NOT NULL REFERENCES produto(id),
```
to:
```sql
    product_id UUID NOT NULL REFERENCES product(id),
```

- [ ] **Step 6: Verify migrations apply cleanly**

Run: `cd mesh-suite-backend && mvn -q flyway:info -Dflyway.url=... ` is not needed — Flyway runs automatically on Spring Boot context startup during tests. Instead, run a single lightweight context-loading test to confirm all migrations apply without error:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=DevSeedTest`

Expected: `BUILD SUCCESS`, 1 test run, 0 failures, 0 errors. (This will legitimately still show compile errors from the rest of the codebase referencing the not-yet-renamed `Produto` class in the same module — that's expected at this point in the plan. If `mvn test -Dtest=DevSeedTest` cannot compile the whole module, instead verify migration syntax by eye against the DDL shown above and move on; Task 2 is what makes the module compile again.)

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/
git commit -m "refactor(product): rename V6 migration produto->product, fix dependent FK targets"
```

---

## Task 2: Product domain, repository, repository test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/domain/Product.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/domain/enums/ProductStatus.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/domain/enums/MeasurementUnit.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/repository/ProductRepository.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/product/repository/ProductRepositoryTest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/Produto.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/StatusProduto.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/UnidadeMedida.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/ProdutoRepository.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/ProdutoRepositoryTest.java`

**Interfaces:**
- Produces: `com.meshsuite.product.domain.Product` (fields per the field map, `category`/`colorway` typed `Category`/`Colorway`), `com.meshsuite.product.domain.enums.ProductStatus{ACTIVE,INACTIVE}`, `com.meshsuite.product.domain.enums.MeasurementUnit{UN,KG,G,L,ML,MT,CM,CX,PC,PAR,DZ}`, `com.meshsuite.product.repository.ProductRepository` with `existsBySku(String)`, `existsBySkuAndIdNot(String,UUID)`, `countByStatus(ProductStatus)`, `countByCategoryId(UUID)`, `countByColorwayId(UUID)`, `countByCategoryIdIn(Collection<UUID>)` returning `List<ProductRepository.CategoryProductCount>`, `countByColorwayIdIn(Collection<UUID>)` returning `List<ProductRepository.ColorwayProductCount>`, nested projection interfaces `CategoryProductCount{getCategoryId(), getTotal()}` and `ColorwayProductCount{getColorwayId(), getTotal()}`.
- Consumes: `com.meshsuite.category.domain.Category`, `com.meshsuite.colorway.domain.Colorway`, `com.meshsuite.fiscal.domain.FiscalRegistration` (all already exist, unchanged).

- [ ] **Step 1: Create `ProductStatus.java`**

```java
package com.meshsuite.product.domain.enums;

public enum ProductStatus {
    ACTIVE,
    INACTIVE
}
```

- [ ] **Step 2: Create `MeasurementUnit.java`**

```java
package com.meshsuite.product.domain.enums;

public enum MeasurementUnit {
    UN,
    KG,
    G,
    L,
    ML,
    MT,
    CM,
    CX,
    PC,
    PAR,
    DZ
}
```

- [ ] **Step 3: Create `Product.java`**

```java
package com.meshsuite.product.domain;

import com.meshsuite.category.domain.Category;
import com.meshsuite.colorway.domain.Colorway;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "product")
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "barcode", length = 50)
    private String barcode;

    @Column(length = 100)
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "colorway_id")
    private Colorway colorway;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "stock_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_unit", nullable = false, length = 5)
    private MeasurementUnit measurementUnit = MeasurementUnit.UN;

    @Column(name = "min_stock", precision = 12, scale = 3)
    private BigDecimal minStock;

    @Column(name = "max_stock", precision = 12, scale = 3)
    private BigDecimal maxStock;

    @Column(precision = 10, scale = 3)
    private BigDecimal weight;

    @Column(precision = 10, scale = 2)
    private BigDecimal length;

    @Column(precision = 10, scale = 2)
    private BigDecimal width;

    @Column(precision = 10, scale = 2)
    private BigDecimal height;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_registration_id")
    private FiscalRegistration fiscalRegistration;
}
```

- [ ] **Step 4: Create `ProductRepository.java`**

```java
package com.meshsuite.product.repository;

import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    long countByStatus(ProductStatus status);
    long countByCategoryId(UUID categoryId);
    long countByColorwayId(UUID colorwayId);

    @Query("SELECT p.category.id AS categoryId, COUNT(p) AS total FROM Product p " +
            "WHERE p.category.id IN :categoryIds GROUP BY p.category.id")
    List<CategoryProductCount> countByCategoryIdIn(@Param("categoryIds") Collection<UUID> categoryIds);

    @Query("SELECT p.colorway.id AS colorwayId, COUNT(p) AS total FROM Product p " +
            "WHERE p.colorway.id IN :colorwayIds GROUP BY p.colorway.id")
    List<ColorwayProductCount> countByColorwayIdIn(@Param("colorwayIds") Collection<UUID> colorwayIds);

    interface CategoryProductCount {
        UUID getCategoryId();
        Long getTotal();
    }

    interface ColorwayProductCount {
        UUID getColorwayId();
        Long getTotal();
    }
}
```

- [ ] **Step 5: Delete the 5 old files listed above**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/Produto.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/StatusProduto.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/enums/UnidadeMedida.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/ProdutoRepository.java
```

(`ProdutoRepositoryTest.java` is replaced in the next step, not just deleted.)

- [ ] **Step 6: Create `ProductRepositoryTest.java`**

Translated from `ProdutoRepositoryTest.java` (deleted). Method name translations: `savesProdutoWithDefaults`→`savesProductWithDefaults`; `skuMustBeUniquePerTenant`, `sameSkuAllowedAcrossDifferentTenants`, `rlsHidesRowsWhenTenantContextUnset` are unchanged (no Portuguese in the name).

```java
package com.meshsuite.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class ProductRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProductRepository productRepository;
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

    private Product newProduct(UUID tenantId, String sku) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Camiseta Polo");
        p.setSku(sku);
        p.setSalePrice(new BigDecimal("59.90"));
        return p;
    }

    @Test
    @Transactional
    void savesProductWithDefaults() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Product saved = productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001"));
        entityManager.clear();

        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(reloaded.getMeasurementUnit()).isEqualTo(MeasurementUnit.UN);
        assertThat(reloaded.getStockQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @Transactional
    void skuMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001")));
    }

    @Test
    @Transactional
    void sameSkuAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        productRepository.saveAndFlush(newProduct(tenantA.getId(), "P0001"));

        setTenantContext(tenantB.getId());
        Product saved = productRepository.saveAndFlush(newProduct(tenantB.getId(), "P0001"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        productRepository.saveAndFlush(newProduct(tenant.getId(), "P0001"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM product")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
```

```bash
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/ProdutoRepositoryTest.java
```

- [ ] **Step 7: Run the new repository test**

The whole module still won't compile yet (Tasks 3-10 haven't bridged the rest). Use the relocate-test-restore technique: temporarily move every file that still imports `com.meshsuite.produto.domain.Produto`/`ProdutoRepository` out of `src/` (note the exact list — everything in `produto/`, plus `category/service/CategoryService.java`, `colorway/service/ColorwayService.java`, `pedido/`, `purchaseorder/`, `sale/`, `stock/`, `shared/handler/GlobalExceptionHandler.java`, and their test counterparts), run:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=ProductRepositoryTest`

Expected: `BUILD SUCCESS`, 4 tests run, 0 failures, 0 errors.

Then restore every moved file exactly (`git status --short` must show no diff beyond the new/deleted files from this task) before committing.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/product/ \
        mesh-suite-backend/src/test/java/com/meshsuite/product/
git commit -m "refactor(product): rename Produto/ProdutoRepository domain+repo to Product, new com.meshsuite.product package"
```

---

## Task 3: Product DTOs, exceptions, specifications, service, service test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/dto/ProductRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/dto/ProductResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/dto/ProductStatusRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/dto/ProductSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/dto/ProductListItemResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/exception/ProductNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/exception/DuplicateSkuException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/repository/specification/ProductSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/service/ProductService.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/product/service/ProductServiceTest.java`
- Delete the produto-package equivalents of all the above.

**Interfaces:**
- Consumes: `Product`, `ProductRepository` (Task 2); `CategoryRepository`, `CategoryNotFoundException` (`com.meshsuite.category.*`); `ColorwayRepository`, `ColorwayNotFoundException` (`com.meshsuite.colorway.*`) — all already exist, unchanged.
- Produces: `com.meshsuite.product.service.ProductService` with methods `listar(String busca, ProductStatus status, Pageable)`, `resumo()`, `buscarPorId(UUID)`, `criar(UUID tenantId, ProductRequest)`, `atualizar(UUID id, ProductRequest)`, `atualizarStatus(UUID id, ProductStatus)`, `excluir(UUID id)` (method names on the service itself are NOT translated — Category/Colorway's services used English method names `list`/`create`/etc., but Produto's own service used Portuguese method names `listar`/`criar`/`atualizar`/`excluir`/`resumo`/`buscarPorId`; this plan keeps them as-is since the design spec's name map covers types and fields, not method names, and no prior sub-project retranslated Produto's own already-Portuguese method names — only rename what's in the Global Constraints table above).

- [ ] **Step 1: Create `ProductRequest.java`**

```java
package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank String name,
        @NotBlank String sku,
        String barcode,
        String brand,
        UUID categoryId,
        UUID colorwayId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal salePrice,
        BigDecimal costPrice,
        ProductStatus status,
        String description,
        BigDecimal stockQuantity,
        MeasurementUnit measurementUnit,
        BigDecimal minStock,
        BigDecimal maxStock,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height) {
}
```

- [ ] **Step 2: Create `ProductResponse.java`**

```java
package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String sku,
        String barcode,
        String brand,
        UUID categoryId,
        String categoryName,
        UUID colorwayId,
        String colorwayName,
        BigDecimal salePrice,
        BigDecimal costPrice,
        ProductStatus status,
        String description,
        BigDecimal stockQuantity,
        MeasurementUnit measurementUnit,
        BigDecimal minStock,
        BigDecimal maxStock,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height) {
}
```

- [ ] **Step 3: Create `ProductStatusRequest.java`**

```java
package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull ProductStatus status) {
}
```

- [ ] **Step 4: Create `ProductSummaryResponse.java`** (dashboard counters — was `ProdutoResumoResponse`)

```java
package com.meshsuite.product.dto;

public record ProductSummaryResponse(long total, long active, long inactive) {
}
```

- [ ] **Step 5: Create `ProductListItemResponse.java`** (list row — was `ProdutoSummaryResponse`)

```java
package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductListItemResponse(
        UUID id,
        String name,
        String sku,
        String brand,
        BigDecimal salePrice,
        BigDecimal stockQuantity,
        ProductStatus status) {
}
```

- [ ] **Step 6: Create `ProductNotFoundException.java`**

```java
package com.meshsuite.product.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException() {
        super("Produto não encontrado");
    }
}
```

(The message text is user-visible — stays in Portuguese, unchanged from the original.)

- [ ] **Step 7: Create `DuplicateSkuException.java`**

```java
package com.meshsuite.product.exception;

public class DuplicateSkuException extends RuntimeException {
    public DuplicateSkuException() {
        super("Já existe um produto cadastrado com este SKU");
    }
}
```

- [ ] **Step 8: Create `ProductSpecifications.java`**

```java
package com.meshsuite.product.repository.specification;

import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), termo),
                cb.like(cb.lower(root.get("sku")), termo));
    }

    public static Specification<Product> comStatus(ProductStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
```

(This fixes the dangling `root.get("nome")` string literal in place, since `Produto.nome` is becoming `Product.name` in this same task — not a "6th occurrence" of the bug class, just the field's own rename.)

- [ ] **Step 9: Create `ProductService.java`**

```java
package com.meshsuite.product.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.category.exception.CategoryNotFoundException;
import com.meshsuite.category.repository.CategoryRepository;
import com.meshsuite.colorway.exception.ColorwayNotFoundException;
import com.meshsuite.colorway.repository.ColorwayRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.dto.*;
import com.meshsuite.product.exception.ProductNotFoundException;
import com.meshsuite.product.exception.DuplicateSkuException;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.product.repository.specification.ProductSpecifications;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository produtoRepository;
    private final CategoryRepository categoriaRepository;
    private final ColorwayRepository corEstampaRepository;

    public ProductService(ProductRepository produtoRepository, CategoryRepository categoriaRepository,
                           ColorwayRepository corEstampaRepository) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.corEstampaRepository = corEstampaRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<ProductListItemResponse> listar(String busca, ProductStatus status, Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.comBusca(busca),
                ProductSpecifications.comStatus(status));
        return produtoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProductSummaryResponse resumo() {
        long ativos = produtoRepository.countByStatus(ProductStatus.ACTIVE);
        long inativos = produtoRepository.countByStatus(ProductStatus.INACTIVE);
        return new ProductSummaryResponse(ativos + inativos, ativos, inativos);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProductResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public ProductResponse criar(UUID tenantId, ProductRequest request) {
        validarSku(request.sku(), null);

        Product produto = new Product();
        produto.setTenantId(tenantId);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProductResponse atualizar(UUID id, ProductRequest request) {
        validarSku(request.sku(), id);

        Product produto = buscarEntidadePorId(id);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProductResponse atualizarStatus(UUID id, ProductStatus novoStatus) {
        Product produto = buscarEntidadePorId(id);
        produto.setStatus(novoStatus);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        produtoRepository.delete(buscarEntidadePorId(id));
    }

    private Product buscarEntidadePorId(UUID id) {
        return produtoRepository.findById(id).orElseThrow(ProductNotFoundException::new);
    }

    private void validarSku(String sku, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? produtoRepository.existsBySku(sku)
                : produtoRepository.existsBySkuAndIdNot(sku, idAtual);
        if (duplicado) {
            throw new DuplicateSkuException();
        }
    }

    private void aplicar(Product produto, ProductRequest request) {
        produto.setName(request.name());
        produto.setSku(request.sku());
        produto.setBarcode(request.barcode());
        produto.setBrand(request.brand());
        produto.setCategory(request.categoryId() != null
                ? categoriaRepository.findById(request.categoryId()).orElseThrow(CategoryNotFoundException::new)
                : null);
        produto.setColorway(request.colorwayId() != null
                ? corEstampaRepository.findById(request.colorwayId()).orElseThrow(ColorwayNotFoundException::new)
                : null);
        produto.setSalePrice(request.salePrice());
        produto.setCostPrice(request.costPrice());
        produto.setStatus(request.status() != null ? request.status() : ProductStatus.ACTIVE);
        produto.setDescription(request.description());
        produto.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : BigDecimal.ZERO);
        produto.setMeasurementUnit(request.measurementUnit() != null ? request.measurementUnit() : MeasurementUnit.UN);
        produto.setMinStock(request.minStock());
        produto.setMaxStock(request.maxStock());
        produto.setWeight(request.weight());
        produto.setLength(request.length());
        produto.setWidth(request.width());
        produto.setHeight(request.height());
    }

    private ProductListItemResponse toSummary(Product p) {
        return new ProductListItemResponse(
                p.getId(), p.getName(), p.getSku(), p.getBrand(), p.getSalePrice(), p.getStockQuantity(), p.getStatus());
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(), p.getName(), p.getSku(), p.getBarcode(), p.getBrand(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getColorway() != null ? p.getColorway().getId() : null,
                p.getColorway() != null ? p.getColorway().getName() : null,
                p.getSalePrice(), p.getCostPrice(), p.getStatus(), p.getDescription(), p.getStockQuantity(),
                p.getMeasurementUnit(), p.getMinStock(), p.getMaxStock(), p.getWeight(), p.getLength(),
                p.getWidth(), p.getHeight());
    }
}
```

Note the constructor parameter/field names (`produtoRepository`, `categoriaRepository`, `corEstampaRepository`) are kept exactly as they were in the original `Produto.java`'s bridge from sub-project 4a (`ProdutoService.java`'s own fields were already `categoriaRepository: CategoryRepository`/`corEstampaRepository: ColorwayRepository` before this task) — this plan does not rename these fields, only their type already changed in 4a, matching the "minimal bridge, don't rename what isn't required" convention applied consistently across this whole initiative.

- [ ] **Step 10: Delete the 8 produto-package equivalents**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoRequest.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoStatusRequest.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoResumoResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoSummaryResponse.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/ProdutoNaoEncontradoException.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/SkuDuplicadoException.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/specification/ProdutoSpecifications.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/service/ProdutoService.java
```

- [ ] **Step 11: Create `ProductServiceTest.java`**

Translated from `ProdutoServiceTest.java` (deleted next step). Method name translations: `criaERecuperaProduto`→`createsAndRetrievesProduct`; `rejeitaSkuDuplicadoNoMesmoTenant`→`rejectsDuplicateSkuInSameTenant`; `atualizaProdutoMantendoOProprioSku`→`updatesProductKeepingItsOwnSku`; `rejeitaAtualizacaoParaSkuDeOutroProduto`→`rejectsUpdateToAnotherProductsSku`; `atualizaStatusParaInativo`→`updatesStatusToInactive`; `resumoContaPorStatus`→`summaryCountsByStatus`; `listaComFiltroDeBusca`→`listsWithSearchFilter`; `excluiProduto`→`deletesProduct`; `sameSkuAllowedAcrossDifferentTenants` and `deniesListingWhenCallerLacksProductViewPermission` are unchanged (no Portuguese in the name).

```java
package com.meshsuite.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.dto.ProductRequest;
import com.meshsuite.product.exception.ProductNotFoundException;
import com.meshsuite.product.exception.DuplicateSkuException;
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
class ProductServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProductService produtoService;
    @Autowired EntityManager entityManager;
    @Autowired UserRepository userRepository;

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

    private ProductRequest request(String sku, BigDecimal salePrice) {
        return new ProductRequest(
                "Camiseta Polo Masculina", sku, "7891234567890", "Marca Alpha", null, null,
                salePrice, new BigDecimal("25.00"), ProductStatus.ACTIVE, "Descrição de teste",
                new BigDecimal("10"), MeasurementUnit.UN, new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("0.300"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"));
    }

    @Test
    void createsAndRetrievesProduct() {
        setUpTenant("aurora");

        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var buscado = produtoService.buscarPorId(criado.id());
        assertThat(buscado.name()).isEqualTo("Camiseta Polo Masculina");
        assertThat(buscado.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void rejectsDuplicateSkuInSameTenant() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        assertThrows(DuplicateSkuException.class,
                () -> produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void updatesProductKeepingItsOwnSku() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var atualizado = produtoService.atualizar(criado.id(), request("P0001", new BigDecimal("64.90")));

        assertThat(atualizado.salePrice()).isEqualByComparingTo("64.90");
    }

    @Test
    void rejectsUpdateToAnotherProductsSku() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        var segundo = produtoService.criar(TenantContext.get(), request("P0002", new BigDecimal("39.90")));

        assertThrows(DuplicateSkuException.class,
                () -> produtoService.atualizar(segundo.id(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void updatesStatusToInactive() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var atualizado = produtoService.atualizarStatus(criado.id(), ProductStatus.INACTIVE);

        assertThat(atualizado.status()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void summaryCountsByStatus() {
        setUpTenant("aurora");
        var a = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        produtoService.criar(TenantContext.get(), request("P0002", new BigDecimal("39.90")));
        produtoService.atualizarStatus(a.id(), ProductStatus.INACTIVE);

        var resumo = produtoService.resumo();

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.active()).isEqualTo(1);
        assertThat(resumo.inactive()).isEqualTo(1);
    }

    @Test
    void listsWithSearchFilter() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var pagina = produtoService.listar("camiseta", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).sku()).isEqualTo("P0001");
    }

    @Test
    void deletesProduct() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        produtoService.excluir(criado.id());

        assertThrows(ProductNotFoundException.class, () -> produtoService.buscarPorId(criado.id()));
    }

    @Test
    void sameSkuAllowedAcrossDifferentTenants() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        setUpTenant("boreal");
        var segundo = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("39.90")));

        assertThat(segundo.sku()).isEqualTo("P0001");
    }

    @Test
    void deniesListingWhenCallerLacksProductViewPermission() {
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
                () -> produtoService.listar(null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }
}
```

```bash
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/service/ProdutoServiceTest.java
```

- [ ] **Step 12: Run the new service test in isolation**

Use relocate-test-restore (same file set as Task 2 Step 7, minus `product/` itself) to verify:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=ProductServiceTest`

Expected: `BUILD SUCCESS`, 9 tests run, 0 failures, 0 errors. Restore all moved files, confirm `git diff --stat` empty for anything outside this task's own changes.

- [ ] **Step 13: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/product/ \
        mesh-suite-backend/src/test/java/com/meshsuite/product/
git commit -m "refactor(product): rename Produto DTOs, exceptions, specifications, and service layer to English"
```

---

## Task 4: Product controller, exception handler, controller test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/controller/ProductController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/product/exception/ProductExceptionHandler.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/product/controller/ProductControllerTest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/ProdutoController.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/ProdutoExceptionHandler.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/ProdutoControllerTest.java`

**Interfaces:**
- Consumes: `ProductService` (Task 3), `AuthContextService` (unchanged).
- Produces: REST endpoint at `/api/products` (was `/api/produtos` — endpoint paths are code, per Global Constraints).

- [ ] **Step 1: Create `ProductController.java`**

```java
package com.meshsuite.product.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.dto.*;
import com.meshsuite.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService produtoService;

    public ProductController(ProductService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public Page<ProductListItemResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return produtoService.listar(busca, status, pageable);
    }

    @GetMapping("/resumo")
    public ProductSummaryResponse resumo() {
        return produtoService.resumo();
    }

    @GetMapping("/{id}")
    public ProductResponse buscarPorId(@PathVariable UUID id) {
        return produtoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                  @Valid @RequestBody ProductRequest request) {
        ProductResponse response = produtoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ProductResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return produtoService.atualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProductResponse atualizarStatus(@PathVariable UUID id, @Valid @RequestBody ProductStatusRequest request) {
        return produtoService.atualizarStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
```

Note: `/resumo` sub-path is kept (matches the existing convention already used unchanged by this endpoint; it is not part of the Portuguese/English field map since it was never covered by the design's field map — leave as-is, this mirrors how `/api/categories` etc. kept their own existing sub-paths).

- [ ] **Step 2: Create `ProductExceptionHandler.java`**

```java
package com.meshsuite.product.exception;

import com.meshsuite.product.controller.ProductController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um produto cadastrado com este SKU"));
    }
}
```

- [ ] **Step 3: Delete the 2 produto-package equivalents**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/ProdutoController.java \
       mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/ProdutoExceptionHandler.java
```

- [ ] **Step 4: Create `ProductControllerTest.java`**

Translated from `ProdutoControllerTest.java` (deleted next step). Method name translations: `createsListsUpdatesAndDeletesProduto`→`createsListsUpdatesAndDeletesProduct`; `rejectsMissingPrecoVendaWithBadRequest`→`rejectsMissingSalePriceWithBadRequest`; `tenantACannotAccessTenantBsProduto`→`tenantACannotAccessTenantBsProduct`; `rejectsDuplicateSkuWithConflict`, `unauthenticatedRequestIsRejected`, `listingWithoutProductViewPermissionIsForbidden` are unchanged. JSON payload keys change to match the renamed record fields (`nome`→`name`, `precoVenda`→`salePrice`, `quantidadeEstoque`→`stockQuantity`, `unidadeMedida`→`measurementUnit`; `sku`/`status` unchanged). URL paths change from `/api/produtos` to `/api/products`.

```java
package com.meshsuite.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String loginAndGetCookie(String codigo, String email, String companyCnpj) throws Exception {
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

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
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

    private String produtoPayload(String sku) {
        return """
                {
                  "name": "Camiseta Polo Masculina",
                  "sku": "%s",
                  "salePrice": 59.90,
                  "stockQuantity": 10,
                  "measurementUnit": "UN"
                }
                """.formatted(sku);
    }

    @Test
    void createsListsUpdatesAndDeletesProduct() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/products").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Camiseta Polo Masculina"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/products").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("P0001"));

        mockMvc.perform(put("/api/products/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Camiseta Polo Masculina Atualizada",
                                  "sku": "P0001",
                                  "salePrice": 64.90,
                                  "stockQuantity": 10,
                                  "measurementUnit": "UN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Camiseta Polo Masculina Atualizada"));

        mockMvc.perform(patch("/api/products/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(delete("/api/products/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateSkuWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/products").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingSalePriceWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/products").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Produto Sem Preço",
                                  "sku": "P0099"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsProduct() throws Exception {
        String tokenA = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/products").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/products/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/products").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

```bash
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/ProdutoControllerTest.java
```

**Correction found during Task 4's review:** `ProductControllerTest` cannot pass with `shared/handler/GlobalExceptionHandler.java` left untouched — 4 of its 6 tests exercise HTTP status codes (404/409/403) that only `GlobalExceptionHandler` maps, and it still points at the Portuguese `ProdutoNaoEncontradoException`/`SkuDuplicadoException` classes Task 3 already deleted. This is the same shape of plan gap the Category/Colorway sub-project hit at its own controller task (its plan also originally missed this shared file) — the fix is pulled forward into this task instead of waiting for what was originally planned as Task 5, since Task 4's own test can't go green without it. Task 5 (below) has been trimmed accordingly — do not repeat this edit there.

- [ ] **Step 5: Bridge `GlobalExceptionHandler.java`'s 2 Product handlers (pulled forward from the original Task 5)**

This file is NOT part of `com.meshsuite.product` — it's a shared cross-cutting handler at `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`. Update ONLY these 2 handlers (every other handler in the file, including the 3 `TabelaPreco*` ones immediately below them, stays untouched):

Change:
```java
    @ExceptionHandler(com.meshsuite.produto.exception.ProdutoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleProdutoNaoEncontrado(
            com.meshsuite.produto.exception.ProdutoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.SkuDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleSkuDuplicado(
            com.meshsuite.produto.exception.SkuDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }
```
to:
```java
    @ExceptionHandler(com.meshsuite.product.exception.ProductNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProductNotFound(
            com.meshsuite.product.exception.ProductNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.product.exception.DuplicateSkuException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateSku(
            com.meshsuite.product.exception.DuplicateSkuException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 6: Run the new controller test in isolation**

Use relocate-test-restore to verify (this file is no longer part of the relocation set, since it's now fixed in place):

Run: `cd mesh-suite-backend && mvn -q test -Dtest=ProductControllerTest`

Expected: `BUILD SUCCESS`, 6 tests run, 0 failures, 0 errors. Restore all moved files (except `GlobalExceptionHandler.java`, which keeps this task's fix).

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/product/ \
        mesh-suite-backend/src/test/java/com/meshsuite/product/ \
        mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java
git commit -m "refactor(product): rename Produto controller and exception handler to English, route produtos->products"
```

---

## Task 5: Bridge — Category/Colorway services

**Note:** this task's original scope also included bridging `shared/handler/GlobalExceptionHandler.java`'s 2 Product-related handlers — that step was pulled forward into Task 4 (see the correction note there) because `ProductControllerTest` couldn't pass without it. Do NOT repeat that edit here; `GlobalExceptionHandler.java` is not touched by this task at all.

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/category/service/CategoryService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/service/ColorwayService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/category/service/CategoryServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/colorway/service/ColorwayServiceTest.java`

**Interfaces:**
- Consumes: `ProductRepository`, `ProductRepository.CategoryProductCount`, `ProductRepository.ColorwayProductCount` (Task 2); `ProductService`, `ProductRequest`, `ProductStatus`, `MeasurementUnit` (Task 3).

- [ ] **Step 1: Bridge `CategoryService.java`**

Change the import `com.meshsuite.produto.repository.ProdutoRepository` to `com.meshsuite.product.repository.ProductRepository`. Change the field/parameter type `ProdutoRepository` to `ProductRepository` (keep the field name `produtoRepository` unchanged — matches this initiative's established minimal-bridge convention). Change `produtoRepository.countByCategoriaIdIn(ids)` to `produtoRepository.countByCategoryIdIn(ids)`. Change `ProdutoRepository.CategoriaProdutoCount::getCategoriaId` to `ProductRepository.CategoryProductCount::getCategoryId` and `ProdutoRepository.CategoriaProdutoCount::getTotal` to `ProductRepository.CategoryProductCount::getTotal`. Change `produtoRepository.countByCategoriaId(id)` (2 call sites: `delete()` and the private `toResponse(Category)` overload) to `produtoRepository.countByCategoryId(id)`. No other line in this file changes.

- [ ] **Step 2: Bridge `ColorwayService.java`**

Same pattern: import `ProdutoRepository`→`ProductRepository`; field/param type only; `countByCorEstampaIdIn`→`countByColorwayIdIn`; `ProdutoRepository.CorEstampaProdutoCount::getCorEstampaId`→`ProductRepository.ColorwayProductCount::getColorwayId`; `ProdutoRepository.CorEstampaProdutoCount::getTotal`→`ProductRepository.ColorwayProductCount::getTotal`; `countByCorEstampaId(id)`→`countByColorwayId(id)` (2 call sites: `delete()` and `toResponse(Colorway)`).

- [ ] **Step 3: Bridge `CategoryServiceTest.java` fixtures**

Change imports `com.meshsuite.produto.domain.enums.StatusProduto`→`com.meshsuite.product.domain.enums.ProductStatus`, `com.meshsuite.produto.domain.enums.UnidadeMedida`→`com.meshsuite.product.domain.enums.MeasurementUnit`, `com.meshsuite.produto.service.ProdutoService`→`com.meshsuite.product.service.ProductService` (keep the field name `produtoService` unchanged). In every inline `new com.meshsuite.produto.dto.ProdutoRequest(...)` call, change the fully-qualified type to `com.meshsuite.product.dto.ProductRequest`, and change every `StatusProduto.ATIVO` to `ProductStatus.ACTIVE`, every `UnidadeMedida.UN` to `MeasurementUnit.UN` — the positional argument order and every other literal value in the constructor call stays exactly the same. There are 4 such call sites in this file (in `rejectsDeletingACategoryInUseByAProduct`, `listAggregatesLinkedProductsPerCategoryInASingleBatch`, and 2 more further down the file with the same shape — apply the same substitution to all of them).

- [ ] **Step 4: Bridge `ColorwayServiceTest.java` fixtures**

Same substitutions as Step 3, applied to this file's 4 analogous `new com.meshsuite.produto.dto.ProdutoRequest(...)` call sites.

- [ ] **Step 5: Run cross-module tests in isolation**

Use relocate-test-restore for the remaining not-yet-bridged modules (`pedido`, `purchaseorder`, `sale`, `stock`, `produto`'s TabelaPreco files) to verify:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=CategoryServiceTest,ColorwayServiceTest`

Expected: `BUILD SUCCESS`, all tests pass (whatever the current total is for these 2 files — no test was added or removed, only fixtures translated), 0 failures, 0 errors. Restore all moved files.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/category/service/CategoryService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/colorway/service/ColorwayService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/category/service/CategoryServiceTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/colorway/service/ColorwayServiceTest.java
git commit -m "refactor(product): bridge CategoryService/ColorwayService to consume renamed Product types"
```

---

## Task 6: Bridge — pedido module

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/ItemPedido.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/service/PedidoService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/repository/PedidoRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/service/PedidoServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/controller/PedidoControllerTest.java`

**Interfaces:**
- Consumes: `Product`, `ProductRepository` (Task 2).

Pedido's own field names (`produto`, `produtoId`, `produtoNome` on `ItemPedido`/`ItemPedidoDto`/`ItemPedidoResponse`) are NOT renamed in this task — only the type they reference changes.

- [ ] **Step 1: Bridge `ItemPedido.java`**

Change the import `com.meshsuite.produto.domain.Produto` to `com.meshsuite.product.domain.Product`. Change the field type from `private Produto produto;` to `private Product produto;` (field name `produto` unchanged; `@JoinColumn(name = "produto_id"...)` unchanged — that FK column name stays Pedido's own, per Global Constraints).

- [ ] **Step 2: Bridge `PedidoService.java`**

Change imports `com.meshsuite.produto.domain.Produto`→`com.meshsuite.product.domain.Product`, `com.meshsuite.produto.repository.ProdutoRepository`→`com.meshsuite.product.repository.ProductRepository`. Change the field/constructor parameter type only (keep the field name `produtoRepository` unchanged). In `aplicar(...)`, change the local variable declaration `Produto produto = produtoRepository.findById(dto.produtoId())...` to `Product produto = produtoRepository.findById(dto.produtoId())...` (local var name `produto` unchanged). In `toResponse(...)`, change `i.getProduto().getNome()` to `i.getProduto().getName()` — this is the one call site that must follow `Product`'s new getter name; nothing else in `ItemPedidoResponse`'s construction changes.

- [ ] **Step 3: Bridge `PedidoRepositoryTest.java`**

Change imports `com.meshsuite.produto.domain.Produto`→`com.meshsuite.product.domain.Product`, `com.meshsuite.produto.repository.ProdutoRepository`→`com.meshsuite.product.repository.ProductRepository`. Every `Produto` type reference in this file becomes `Product` (local variable names like `p` stay). Change `p.setNome("Camiseta Polo")` to `p.setName("Camiseta Polo")` (line ~73) and `p.setPrecoVenda(new BigDecimal("59.90"))` to `p.setSalePrice(new BigDecimal("59.90"))` (line ~75).

- [ ] **Step 4: Bridge `PedidoServiceTest.java`**

Same import changes as Step 3. Change `p.setNome("Camiseta Polo")`→`p.setName(...)` and `p.setPrecoVenda(precoVenda)`→`p.setSalePrice(precoVenda)` (~line 128, 130), and the later `produto.setPrecoVenda(new BigDecimal("99.90"))`→`produto.setSalePrice(new BigDecimal("99.90"))` (~line 241). Local variable names (`p`, `produto`) stay unchanged.

- [ ] **Step 5: Bridge `PedidoControllerTest.java`**

Same import changes as Step 3. Change `produto.setNome("Camiseta Polo")`→`produto.setName(...)` and `produto.setPrecoVenda(new BigDecimal("59.90"))`→`produto.setSalePrice(...)` (~lines 97, 99). Local variable name `produto` stays unchanged.

- [ ] **Step 6: Run pedido tests in isolation**

Use relocate-test-restore for the remaining not-yet-bridged modules (`purchaseorder`, `sale`, `stock`, `produto`'s TabelaPreco files) to verify:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PedidoRepositoryTest,PedidoServiceTest,PedidoControllerTest`

Expected: `BUILD SUCCESS`, all tests pass, 0 failures, 0 errors. Restore all moved files.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/pedido/ \
        mesh-suite-backend/src/test/java/com/meshsuite/pedido/
git commit -m "refactor(product): bridge pedido module to consume the renamed Product type"
```

---

## Task 7: Bridge — purchaseorder module

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/domain/PurchaseOrderItem.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/service/PurchaseOrderService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/repository/PurchaseOrderRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/service/PurchaseOrderServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/controller/PurchaseOrderControllerTest.java`

**Interfaces:**
- Consumes: `Product`, `ProductRepository` (Task 2).

`purchaseorder`'s own field names (`product`, `productId`, `productName` on `PurchaseOrderItem`/DTOs) are already English and are NOT touched — only the imported type changes.

- [ ] **Step 1: Bridge `PurchaseOrderItem.java`**

Change the import `com.meshsuite.produto.domain.Produto` to `com.meshsuite.product.domain.Product`. Change the field type from `private Produto product;` to `private Product product;` (field name `product` unchanged; `@JoinColumn(name = "product_id"...)` unchanged).

- [ ] **Step 2: Bridge `PurchaseOrderService.java`**

Change imports `com.meshsuite.produto.domain.Produto`→`com.meshsuite.product.domain.Product`, `com.meshsuite.produto.repository.ProdutoRepository`→`com.meshsuite.product.repository.ProductRepository`. Change the field/constructor parameter type only (field name `produtoRepository` unchanged). In `apply(...)`, change `Produto product = produtoRepository.findById(dto.productId())...` to `Product product = produtoRepository.findById(dto.productId())...`. In `toResponse(...)`, change `i.getProduct().getNome()` to `i.getProduct().getName()` — the one call site that must follow `Product`'s new getter.

- [ ] **Step 3: Bridge `PurchaseOrderRepositoryTest.java`**

Change imports `com.meshsuite.produto.domain.Produto`→`com.meshsuite.product.domain.Product`, `com.meshsuite.produto.repository.ProdutoRepository`→`com.meshsuite.product.repository.ProductRepository`. Change `p.setNome("Tecido Algodão")`→`p.setName(...)` and `p.setPrecoVenda(new BigDecimal("25.00"))`→`p.setSalePrice(...)` (~lines 73, 75).

- [ ] **Step 4: Bridge `PurchaseOrderServiceTest.java`**

Same import changes. Change `p.setNome("Tecido Algodão")`→`p.setName(...)` and `p.setPrecoVenda(precoVenda)`→`p.setSalePrice(precoVenda)` (~lines 129, 131), and `product.setPrecoVenda(new BigDecimal("99.90"))`→`product.setSalePrice(...)` (~line 262).

- [ ] **Step 5: Bridge `PurchaseOrderControllerTest.java`**

Same import changes. Change `produto.setNome("Tecido Algodão")`→`produto.setName(...)` and `produto.setPrecoVenda(new BigDecimal("25.00"))`→`produto.setSalePrice(...)` (~lines 88, 90).

- [ ] **Step 6: Run purchaseorder tests in isolation**

Use relocate-test-restore for the remaining not-yet-bridged modules (`sale`, `stock`, `produto`'s TabelaPreco files) to verify:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PurchaseOrderRepositoryTest,PurchaseOrderServiceTest,PurchaseOrderControllerTest`

Expected: `BUILD SUCCESS`, all tests pass, 0 failures, 0 errors. Restore all moved files.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/ \
        mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/
git commit -m "refactor(product): bridge purchaseorder module to consume the renamed Product type"
```

---

## Task 8: Bridge — sale module

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/sale/domain/SaleItem.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/sale/service/SaleService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/repository/SaleRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/service/SaleServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java`

**Interfaces:**
- Consumes: `Product`, `ProductRepository` (Task 2).

- [ ] **Step 1: Bridge `SaleItem.java`**

Change the import `com.meshsuite.produto.domain.Produto` to `com.meshsuite.product.domain.Product`. Change the field type from `private Produto product;` to `private Product product;` (field name `product` unchanged; `@JoinColumn(name = "product_id"...)` unchanged).

- [ ] **Step 2: Bridge `SaleService.java`**

Change the import `com.meshsuite.produto.domain.Produto` to `com.meshsuite.product.domain.Product` (this file has no `ProdutoRepository` dependency — it only reads `Produto` off `ItemPedido.getProduto()`). In `issue(...)`, change `Produto product = orderItem.getProduto();` to `Product product = orderItem.getProduto();`. Change `product.getNome()` to `product.getName()` (the fiscal-registration-missing error message interpolation — the Portuguese message text itself, `" não possui cadastro fiscal aplicado"`, stays unchanged). In `toResponse(...)`, change `i.getProduct().getNome()` to `i.getProduct().getName()`.

- [ ] **Step 3: Bridge `SaleRepositoryTest.java`**

Change imports `com.meshsuite.produto.domain.Produto`→`com.meshsuite.product.domain.Product`, `com.meshsuite.produto.repository.ProdutoRepository`→`com.meshsuite.product.repository.ProductRepository`. Change `p.setNome("Camiseta Polo")`→`p.setName(...)` and `p.setPrecoVenda(new BigDecimal("59.90"))`→`p.setSalePrice(...)` (~lines 73, 75).

- [ ] **Step 4: Bridge `SaleServiceTest.java`**

Same import changes. Change `p.setNome("Camiseta Polo")`→`p.setName(...)` and `p.setPrecoVenda(salePrice)`→`p.setSalePrice(salePrice)` (~lines 136, 138), and the second product's `p.setNome("Camiseta Sem Fiscal")`→`p.setName(...)`, `p.setPrecoVenda(salePrice)`→`p.setSalePrice(salePrice)` (~lines 146, 148).

- [ ] **Step 5: Bridge `SaleControllerTest.java`**

Same import changes. Change `product.setNome("Camiseta Polo")`→`product.setName(...)` and `product.setPrecoVenda(new BigDecimal("59.90"))`→`product.setSalePrice(...)` (~lines 111, 113).

- [ ] **Step 6: Run sale tests in isolation**

Use relocate-test-restore for the remaining not-yet-bridged modules (`stock`, `produto`'s TabelaPreco files) to verify:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=SaleRepositoryTest,SaleServiceTest,SaleControllerTest`

Expected: `BUILD SUCCESS`, all tests pass, 0 failures, 0 errors. Restore all moved files.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/sale/ \
        mesh-suite-backend/src/test/java/com/meshsuite/sale/
git commit -m "refactor(product): bridge sale module to consume the renamed Product type"
```

---

## Task 9: Bridge — stock module (including a raw-SQL string-literal fix)

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/stock/domain/StockMovement.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/stock/service/StockService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/stock/repository/StockMovementRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/stock/service/StockServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/stock/controller/StockMovementControllerTest.java`

**Interfaces:**
- Consumes: `Product`, `ProductRepository`, `ProductNotFoundException` (Tasks 2-3).

**Important:** `StockService.applyAtomicAdjustment()` contains a raw native SQL string that references the `produto` table and `quantidade_estoque` column directly (`entityManager.createNativeQuery(sql)`, not JPA/Hibernate-mediated) — this is a new instance of the dangling-property-string-literal bug class the [[feedback_dangling_property_string_literals]] lesson warns about, found by grepping the whole codebase for raw SQL mentioning `produto`/`quantidade_estoque` outside the `produto` package. It will NOT fail at compile time if missed — it will fail (or silently target the wrong/nonexistent table) only when this code path actually runs. Step 2 below fixes it explicitly.

- [ ] **Step 1: Bridge `StockMovement.java`**

Change the import `com.meshsuite.produto.domain.Produto` to `com.meshsuite.product.domain.Product`. Change the field type from `private Produto product;` to `private Product product;` (field name `product` unchanged; `@JoinColumn(name = "product_id"...)` unchanged).

- [ ] **Step 2: Bridge `StockService.java`, including the raw SQL string**

Change imports `com.meshsuite.produto.domain.Produto`→`com.meshsuite.product.domain.Product`, `com.meshsuite.produto.exception.ProdutoNaoEncontradoException`→`com.meshsuite.product.exception.ProductNotFoundException`, `com.meshsuite.produto.repository.ProdutoRepository`→`com.meshsuite.product.repository.ProductRepository`. Change the field/constructor parameter type only (field name `produtoRepository` unchanged). In `adjustBalance(...)`, change `Produto product = produtoRepository.findById(productId).orElseThrow(ProdutoNaoEncontradoException::new);` to `Product product = produtoRepository.findById(productId).orElseThrow(ProductNotFoundException::new);`.

In `applyAtomicAdjustment(...)`, change the raw SQL string from:

```java
        String sql = type == StockMovementType.INBOUND
                ? "UPDATE produto SET quantidade_estoque = quantidade_estoque + :quantity " +
                        "WHERE id = :productId RETURNING quantidade_estoque"
                : "UPDATE produto SET quantidade_estoque = quantidade_estoque - :quantity " +
                        "WHERE id = :productId AND quantidade_estoque >= :quantity RETURNING quantidade_estoque";
```

to:

```java
        String sql = type == StockMovementType.INBOUND
                ? "UPDATE product SET stock_quantity = stock_quantity + :quantity " +
                        "WHERE id = :productId RETURNING stock_quantity"
                : "UPDATE product SET stock_quantity = stock_quantity - :quantity " +
                        "WHERE id = :productId AND stock_quantity >= :quantity RETURNING stock_quantity";
```

In `toResponse(...)`, change `m.getProduct().getNome()` to `m.getProduct().getName()`.

- [ ] **Step 3: Bridge `StockMovementRepositoryTest.java`**

Change imports `com.meshsuite.produto.domain.Produto`→`com.meshsuite.product.domain.Product`, `com.meshsuite.produto.repository.ProdutoRepository`→`com.meshsuite.product.repository.ProductRepository`. Change `produto.setNome("Tecido Algodão")`→`produto.setName(...)` and `produto.setPrecoVenda(new BigDecimal("25.00"))`→`produto.setSalePrice(...)` (~lines 50, 52).

- [ ] **Step 4: Bridge `StockServiceTest.java`**

Same import changes plus `com.meshsuite.produto.exception.ProdutoNaoEncontradoException`→`com.meshsuite.product.exception.ProductNotFoundException` if this test references the exception type directly (check the file — if present, change it). Change `p.setNome("Tecido Algodão")`→`p.setName(...)`, `p.setPrecoVenda(new BigDecimal("25.00"))`→`p.setSalePrice(...)`, `p.setQuantidadeEstoque(quantidadeInicial)`→`p.setStockQuantity(quantidadeInicial)` (~lines 79, 81, 82). Change the two assertions `assertThat(reloaded.getQuantidadeEstoque())...` to `assertThat(reloaded.getStockQuantity())...` (~lines 107, 133).

- [ ] **Step 5: Bridge `StockMovementControllerTest.java`**

Same import changes as Step 3. Change `produto.setNome("Tecido Algodão")`→`produto.setName(...)` and `produto.setPrecoVenda(new BigDecimal("25.00"))`→`produto.setSalePrice(...)` (~lines 75, 77).

- [ ] **Step 6: Run stock tests in isolation**

Use relocate-test-restore for the remaining not-yet-bridged files (`produto`'s TabelaPreco files) to verify:

Run: `cd mesh-suite-backend && mvn -q test -Dtest=StockMovementRepositoryTest,StockServiceTest,StockMovementControllerTest`

Expected: `BUILD SUCCESS`, all tests pass, 0 failures, 0 errors — including the balance-adjustment tests, which is the functional proof that the raw-SQL fix in Step 2 targets the right table/column. Restore all moved files.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/stock/ \
        mesh-suite-backend/src/test/java/com/meshsuite/stock/
git commit -m "refactor(product): bridge stock module to consume the renamed Product type, fix raw-SQL column/table names"
```

---

## Task 10: Bridge — TabelaPrecoService, TabelaPrecoItem (stays in com.meshsuite.produto)

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/TabelaPrecoItem.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/service/TabelaPrecoService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/service/TabelaPrecoServiceTest.java` (only if it references `Produto`/`ProdutoRepository` — check first; if it doesn't, skip this file)
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/TabelaPrecoRepositoryTest.java` (same conditional check)
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/TabelaPrecoControllerTest.java` (same conditional check)

**Interfaces:**
- Consumes: `Product`, `ProductRepository` (Task 2).

This is the one place in the whole codebase where `TabelaPreco`'s own field names (`produto`, `produtoId` on `TabelaPrecoItemInput`/`TabelaPrecoItemResponse`) are deliberately left untouched — TabelaPreco's own rename is 4c's job — only the imported `Produto` type becomes `Product`.

- [ ] **Step 1: Bridge `TabelaPrecoItem.java`**

Change the import `com.meshsuite.produto.domain.Produto` — wait, this file is already IN `com.meshsuite.produto.domain`, so `Produto` was referenced without an import (same-package). Now that `Product` lives in a different package, ADD the import `import com.meshsuite.product.domain.Product;` and change the field declaration from `private Produto produto;` to `private Product produto;` (field name `produto` unchanged; `@JoinColumn(name = "produto_id"...)` unchanged).

- [ ] **Step 2: Bridge `TabelaPrecoService.java`**

This file is in `com.meshsuite.produto.service` and previously referenced `Produto`/`ProdutoRepository` from the sibling `com.meshsuite.produto.domain`/`com.meshsuite.produto.repository` packages, which also required no import (same top-level package tree convention doesn't apply here — check the current file: it explicitly imports `com.meshsuite.produto.domain.Produto` and `com.meshsuite.produto.repository.ProdutoRepository` already, since domain/repository are sub-packages). Change these two imports to `com.meshsuite.product.domain.Product` and `com.meshsuite.product.repository.ProductRepository`. Change the field/constructor parameter type only (field name `produtoRepository` unchanged). In `aplicar(...)`, change:

```java
        for (TabelaPrecoItemInput itemInput : request.itens()) {
            Produto produto = produtoRepository.findById(itemInput.produtoId())
                    .orElseThrow(() -> new TabelaPrecoValidationException("Produto não encontrado"));
```

to:

```java
        for (TabelaPrecoItemInput itemInput : request.itens()) {
            Product produto = produtoRepository.findById(itemInput.produtoId())
                    .orElseThrow(() -> new TabelaPrecoValidationException("Produto não encontrado"));
```

(the exception message stays in Portuguese, unchanged — it's user-visible). In `toResponse(...)`, change:

```java
        List<TabelaPrecoItemResponse> itens = t.getItens().stream()
                .map(i -> new TabelaPrecoItemResponse(i.getProduto().getId(), i.getProduto().getNome(),
                        i.getProduto().getSku(), i.getProduto().getPrecoVenda(), i.getPrecoNestaTabela(),
                        i.getPercentualComissao()))
                .toList();
```

to:

```java
        List<TabelaPrecoItemResponse> itens = t.getItens().stream()
                .map(i -> new TabelaPrecoItemResponse(i.getProduto().getId(), i.getProduto().getName(),
                        i.getProduto().getSku(), i.getProduto().getSalePrice(), i.getPrecoNestaTabela(),
                        i.getPercentualComissao()))
                .toList();
```

(only `.getNome()`→`.getName()` and `.getPrecoVenda()`→`.getSalePrice()` change — `.getSku()`, `.getPrecoNestaTabela()`, `.getPercentualComissao()` are TabelaPreco's own untouched fields).

- [ ] **Step 3: Check and bridge the 3 TabelaPreco test files**

Run: `grep -ln "Produto\b\|ProdutoRepository" mesh-suite-backend/src/test/java/com/meshsuite/produto/service/TabelaPrecoServiceTest.java mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/TabelaPrecoRepositoryTest.java mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/TabelaPrecoControllerTest.java`

For each file the grep matches, apply the same bridge pattern as Task 6 Step 3: import `com.meshsuite.produto.domain.Produto`→`com.meshsuite.product.domain.Product` (or add the import if same-package, per Step 1's note), `com.meshsuite.produto.repository.ProdutoRepository`→`com.meshsuite.product.repository.ProductRepository` (or add if same-package), and translate any `.setNome(...)`/`.setPrecoVenda(...)` calls on a `Produto`/`Product`-typed fixture variable to `.setName(...)`/`.setSalePrice(...)` — using the exact variable names found in that file. If a file has no match, skip it (do not modify).

- [ ] **Step 4: Run TabelaPreco tests**

At this point every backend main-source file has been bridged (Tasks 1-10 cover the full main-source dependency graph), so the whole backend module should compile. No relocate-test-restore is needed for this step.

Run: `cd mesh-suite-backend && mvn -q test -Dtest=TabelaPrecoServiceTest,TabelaPrecoRepositoryTest,TabelaPrecoControllerTest`

Expected: `BUILD SUCCESS`, all tests pass, 0 failures, 0 errors.

- [ ] **Step 5: Run the FULL backend test suite**

Run: `cd mesh-suite-backend && mvn -q clean test`

Expected: 0 failures. Errors should match the documented pre-existing flake exactly — 15 errors (12 `com.meshsuite.payable.*` + 3 `CompanyRepositoryTest`), confirmed identical after every prior sub-project's merge. If the error count or the specific failing classes differ from this signature, investigate before proceeding — do not assume it's the same flake without checking `target/surefire-reports/*.txt` for the exact class names.

**Correction found during Task 10's review:** the full suite also surfaces 2 FAILURES (not the documented flake's errors) not accounted for above: `CategoryControllerTest.rejectsDeletingACategoryInUseWithBadRequest` and `ColorwayControllerTest.rejectsDeletingAColorwayInUseWithBadRequest`, both `expected:<201> but was:<404>`. Root cause: both tests (written in the earlier, already-merged Category/Colorway sub-project) POST a fixture-product-creation request to the literal path `/api/produtos` with a JSON body using the old Portuguese field names — a hardcoded dependency on Produto's pre-rename REST contract that this sub-project's Task 4 broke by moving the endpoint to `/api/products` with English field names. This is the same "dangling property string literal" bug class the initiative has hit before, just manifesting as a REST path + JSON body instead of a JPA property path. Fix both call sites:

In `mesh-suite-backend/src/test/java/com/meshsuite/category/controller/CategoryControllerTest.java`, change:
```java
        mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Camiseta Polo",
                                  "sku": "P0001",
                                  "categoriaId": "%s",
                                  "precoVenda": 59.90,
                                  "quantidadeEstoque": 10,
                                  "unidadeMedida": "UN"
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated());
```
to:
```java
        mockMvc.perform(post("/api/products").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Camiseta Polo",
                                  "sku": "P0001",
                                  "categoryId": "%s",
                                  "salePrice": 59.90,
                                  "stockQuantity": 10,
                                  "measurementUnit": "UN"
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated());
```

In `mesh-suite-backend/src/test/java/com/meshsuite/colorway/controller/ColorwayControllerTest.java`, change:
```java
        mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Camiseta Polo",
                                  "sku": "P0001",
                                  "corEstampaId": "%s",
                                  "precoVenda": 59.90,
                                  "quantidadeEstoque": 10,
                                  "unidadeMedida": "UN"
                                }
                                """.formatted(colorwayId)))
                .andExpect(status().isCreated());
```
to:
```java
        mockMvc.perform(post("/api/products").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Camiseta Polo",
                                  "sku": "P0001",
                                  "colorwayId": "%s",
                                  "salePrice": 59.90,
                                  "stockQuantity": 10,
                                  "measurementUnit": "UN"
                                }
                                """.formatted(colorwayId)))
                .andExpect(status().isCreated());
```

Re-run the full suite after this fix; the 2 failures should disappear, leaving exactly the documented 15-error flake signature.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/TabelaPrecoItem.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/service/TabelaPrecoService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/ \
        mesh-suite-backend/src/test/java/com/meshsuite/category/controller/CategoryControllerTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/colorway/controller/ColorwayControllerTest.java
git commit -m "refactor(product): bridge TabelaPreco (domain+service) to consume the renamed Product type"
```

---

## Task 11: Frontend — `api/produtos.ts` → `api/products.ts`

**Files:**
- Create: `mesh-suite-frontend/src/api/products.ts`
- Delete: `mesh-suite-frontend/src/api/produtos.ts`

**Interfaces:**
- Produces: `ProductRequest`, `ProductResponse`, `ProductListItem`, `ProductSummary`, `ListProductsParams`, `Page<T>`, functions `listProducts`, `getProduct`, `createProduct`, `updateProduct`, `updateProductStatus`, `deleteProduct`, `getProductSummary`. Backend calls hit `/products` (base path already includes `/api` via `apiClient`, matching every existing api file's convention — confirm by checking `apiClient`'s baseURL, but do not change it).

- [ ] **Step 1: Create `products.ts`**

```typescript
import { apiClient } from './client'

export type ProductStatus = 'ACTIVE' | 'INACTIVE'
export type MeasurementUnit = 'UN' | 'KG' | 'G' | 'L' | 'ML' | 'MT' | 'CM' | 'CX' | 'PC' | 'PAR' | 'DZ'

export interface ProductRequest {
  name: string
  sku: string
  barcode: string
  brand: string
  categoryId: string | null
  colorwayId: string | null
  salePrice: number
  costPrice: number | null
  status: ProductStatus
  description: string
  stockQuantity: number
  measurementUnit: MeasurementUnit
  minStock: number | null
  maxStock: number | null
  weight: number | null
  length: number | null
  width: number | null
  height: number | null
}

export interface ProductResponse extends ProductRequest {
  id: string
  categoryName: string | null
  colorwayName: string | null
}

export interface ProductListItem {
  id: string
  name: string
  sku: string
  brand: string
  salePrice: number
  stockQuantity: number
  status: ProductStatus
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListProductsParams {
  busca?: string
  status?: ProductStatus
  page?: number
  size?: number
  sort?: string
}

export interface ProductSummary {
  total: number
  active: number
  inactive: number
}

export async function listProducts(params: ListProductsParams): Promise<Page<ProductListItem>> {
  const { data } = await apiClient.get<Page<ProductListItem>>('/products', { params })
  return data
}

export async function getProduct(id: string): Promise<ProductResponse> {
  const { data } = await apiClient.get<ProductResponse>(`/products/${id}`)
  return data
}

export async function createProduct(payload: ProductRequest): Promise<ProductResponse> {
  const { data } = await apiClient.post<ProductResponse>('/products', payload)
  return data
}

export async function updateProduct(id: string, payload: ProductRequest): Promise<ProductResponse> {
  const { data } = await apiClient.put<ProductResponse>(`/products/${id}`, payload)
  return data
}

export async function updateProductStatus(id: string, status: ProductStatus): Promise<void> {
  await apiClient.patch(`/products/${id}/status`, { status })
}

export async function deleteProduct(id: string): Promise<void> {
  await apiClient.delete(`/products/${id}`)
}

export async function getProductSummary(): Promise<ProductSummary> {
  const { data } = await apiClient.get<ProductSummary>('/products/resumo')
  return data
}
```

(`busca` stays as the query param name per Global Constraints; `/resumo` sub-path kept per Task 4's note.)

- [ ] **Step 2: Delete the old file**

```bash
git rm mesh-suite-frontend/src/api/produtos.ts
```

- [ ] **Step 3: Verify the new file type-checks standalone**

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: errors from every other file that still imports `@/api/produtos` (Tasks 12-13 haven't run yet) — that's expected at this point. Confirm the errors are ONLY "Cannot find module '@/api/produtos'" (or similar) in files this plan will touch later, not a syntax/type error inside `products.ts` itself.

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-frontend/src/api/products.ts
git commit -m "refactor(product): rename frontend api/produtos.ts to products.ts"
```

---

## Task 12: Frontend — Product's own views (`ProductFormView.vue`, `ProductsListView.vue`)

**Files:**
- Create: `mesh-suite-frontend/src/views/ProductFormView.vue`
- Create: `mesh-suite-frontend/src/views/ProductsListView.vue`
- Create: `mesh-suite-frontend/src/views/__tests__/ProductFormView.spec.ts`
- Create: `mesh-suite-frontend/src/views/__tests__/ProductsListView.spec.ts`
- Delete: `mesh-suite-frontend/src/views/ProdutoFormView.vue`
- Delete: `mesh-suite-frontend/src/views/ProdutosListView.vue`
- Delete: `mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts`
- Delete: `mesh-suite-frontend/src/views/__tests__/ProdutosListView.spec.ts`

**Interfaces:**
- Consumes: `products.ts` (Task 11) — `getProduct`, `createProduct`, `updateProduct`, `type ProductRequest`, `type ProductStatus`, `type MeasurementUnit`, `listProducts`, `getProductSummary`, `updateProductStatus`, `deleteProduct`, `type ProductListItem`, `type ProductSummary`, `type Page`.

These are Product's own screens (not borrowed views), renamed like Category/Colorway's own views in 4a — every visible Portuguese string (labels, placeholders, button text, error messages) must be preserved character-for-character.

- [ ] **Step 1: Create `ProductFormView.vue`**

Byte-identical to the current `ProdutoFormView.vue`, with these substitutions ONLY (every visible template string — `'Editar Produto'`, `'Novo Produto'`, `'Nome do Produto *'`, `'Salvar Produto'`, all field labels, all error messages — stays character-for-character unchanged):

- Script import block: change
  ```typescript
  import {
    buscarProduto,
    criarProduto,
    atualizarProduto,
    type ProdutoRequest,
    type StatusProduto,
    type UnidadeMedida,
  } from '@/api/produtos'
  ```
  to
  ```typescript
  import {
    getProduct,
    createProduct,
    updateProduct,
    type ProductRequest,
    type ProductStatus,
    type MeasurementUnit,
  } from '@/api/products'
  ```
- Every `UnidadeMedida`→`MeasurementUnit`, `StatusProduto`→`ProductStatus`, `ProdutoRequest`→`ProductRequest` type annotation.
- `function novoFormulario(): ProdutoRequest` → `function novoFormulario(): ProductRequest`, and its returned object's keys: `nome`→`name`, `codigoBarras`→`barcode`, `marca`→`brand`, `categoriaId`→`categoryId`, `corEstampaId`→`colorwayId`, `precoVenda`→`salePrice`, `precoCusto`→`costPrice`, `status: 'ATIVO'`→`status: 'ACTIVE'`, `descricao`→`description`, `quantidadeEstoque`→`stockQuantity`, `unidadeMedida`→`measurementUnit`, `estoqueMinimo`→`minStock`, `estoqueMaximo`→`maxStock`, `peso`→`weight`, `comprimento`→`length`, `largura`→`width`, `altura`→`height`.
- `const form = reactive<ProdutoRequest>(...)` → `reactive<ProductRequest>(...)`.
- `STATUS_OPCOES: { value: StatusProduto; label: string }[] = [{ value: 'ATIVO', label: 'Ativo' }, { value: 'INATIVO', label: 'Inativo' }]` → `{ value: ProductStatus; label: string }[] = [{ value: 'ACTIVE', label: 'Ativo' }, { value: 'INACTIVE', label: 'Inativo' }]` — the `label` text stays Portuguese, only the `value` wire-values translate.
- `UNIDADES: UnidadeMedida[]` → `UNIDADES: MeasurementUnit[]` (array contents unchanged: `['UN','KG','G','L','ML','MT','CM','CX','PC','PAR','DZ']`).
- Template bindings: `v-model="form.nome"`→`v-model="form.name"`, `erros.nome`→`erros.name` (and the `erros` reactive object's key `nome`→`name`), `form.codigoBarras`→`form.barcode`, `form.marca`→`form.brand`, `form.categoriaId`→`form.categoryId`, `form.corEstampaId`→`form.colorwayId`, `form.precoVenda`→`form.salePrice` (×2, including `erros.precoVenda`→`erros.salePrice`), `form.precoCusto`→`form.costPrice`, `form.status === opt.value && opt.value === 'ATIVO'`→`=== 'ACTIVE'`, `'INATIVO'`→`'INACTIVE'` (class bindings `status-pill--ativo`/`status-pill--inativo` CSS class NAMES stay unchanged — they're not part of the field map, just local CSS class identifiers), `form.descricao`→`form.description`, `form.quantidadeEstoque`→`form.stockQuantity`, `form.unidadeMedida`→`form.measurementUnit`, `form.estoqueMinimo`→`form.minStock`, `form.estoqueMaximo`→`form.maxStock`, `form.peso`→`form.weight`, `form.comprimento`→`form.length`, `form.largura`→`form.width`, `form.altura`→`form.height`.
- `erros = reactive<{ nome?: string; sku?: string; precoVenda?: string }>({})` → `reactive<{ name?: string; sku?: string; salePrice?: string }>({})`.
- `function validar()`: `erros.nome = form.nome.trim() ? ...`→`erros.name = form.name.trim() ? ...`, `erros.sku = form.sku.trim() ? ...` unchanged, `erros.precoVenda = Number(form.precoVenda) > 0 ? ...`→`erros.salePrice = Number(form.salePrice) > 0 ? ...`, `return !erros.nome && !erros.sku && !erros.precoVenda`→`return !erros.name && !erros.sku && !erros.salePrice`.
- `function paraPayload(): ProdutoRequest` → `: ProductRequest`; every key inside follows the same field map (`precoVenda`→`salePrice`, `precoCusto`→`costPrice`, `quantidadeEstoque`→`stockQuantity`, `estoqueMinimo`→`minStock`, `estoqueMaximo`→`maxStock`, `peso`→`weight`, `comprimento`→`length`, `largura`→`width`, `altura`→`height`).
- `onMounted`: `const produto = await buscarProduto(id)` → `const produto = await getProduct(id)` (local variable name `produto` unchanged, matches this initiative's convention of not renaming a consumer's own local variable names). `produto.categoriaId`→`produto.categoryId`, `produto.categoriaNome`→`produto.categoryName`, `produto.corEstampaId`→`produto.colorwayId`, `produto.corEstampaNome`→`produto.colorwayName`. Comments mentioning "categoria"/"produto" in prose can stay as-is or be lightly reworded — not required, no functional effect.
- `await atualizarProduto(id, payload)` → `await updateProduct(id, payload)`; `await criarProduto(payload)` → `await createProduct(payload)`.
- `import { listCategories, type CategoryResponse } from '@/api/categories'` and `import { listColorways, type ColorwayResponse } from '@/api/colorways'` stay unchanged (already renamed in 4a).
- Everything else (all HTML structure, all CSS in `<style scoped>`, all Portuguese label/placeholder/button text, all `data-test` attribute VALUES since they're test hooks not translated identifiers — check the existing file: `data-test="nome"`, `data-test="sku"`, `data-test="categoria"`, `data-test="cor-estampa"`, `data-test="preco-venda"`, `data-test="preco-custo"` — these `data-test` string VALUES stay exactly as-is, unchanged, matching the precedent that `data-test` attributes are treated as stable test hooks, not code identifiers subject to the field map) is byte-identical to the original.

- [ ] **Step 2: Create `ProductsListView.vue`**

Byte-identical to the current `ProdutosListView.vue`, with these substitutions ONLY (visible text — `title="Produtos"`, `"+ Novo Produto"`, `"Lista de Produtos"`, `"Total"`/`"Ativos"`/`"Inativos"`, `"Produto"`, `"Marca"`, `"Preço de Venda"`, `"Estoque"`, `"Status"`, `"Código"`, all error messages, the `"${resumo.value.total} produtos cadastrados"` template string, the confirm dialog text, all button labels — every one of these stays character-for-character unchanged):

- Import block: change
  ```typescript
  import {
    listarProdutos,
    buscarResumoProdutos,
    atualizarStatusProduto,
    excluirProduto,
    type ProdutoSummary,
    type ProdutoResumo,
    type Page as ApiPage,
    type StatusProduto,
  } from '@/api/produtos'
  ```
  to
  ```typescript
  import {
    listProducts,
    getProductSummary,
    updateProductStatus,
    deleteProduct,
    type ProductListItem,
    type ProductSummary,
    type Page as ApiPage,
    type ProductStatus,
  } from '@/api/products'
  ```
- `pagina = ref<ApiPage<ProdutoSummary>>(...)` → `ref<ApiPage<ProductListItem>>(...)`.
- `resumo = ref<ProdutoResumo | null>(null)` → `ref<ProductSummary | null>(null)`.
- `sortField = ref<'nome' | 'precoVenda' | 'status' | null>(null)` → `ref<'name' | 'salePrice' | 'status' | null>(null)` — this is the dangling-property-string-literal fix flagged during design: the raw string sent to the backend's `sort` query param must match `Product`'s new field names.
- `function statusLabel(status: StatusProduto)` → `(status: ProductStatus)`; the returned label map `{ ATIVO: 'Ativo', INATIVO: 'Inativo' }[status]` → `{ ACTIVE: 'Ativo', INACTIVE: 'Inativo' }[status]` — only the map's KEYS translate, the label VALUES stay Portuguese.
- `function sortIcon(field: 'nome' | 'precoVenda' | 'status')` → `(field: 'name' | 'salePrice' | 'status')`.
- `function toggleSort(field: 'nome' | 'precoVenda' | 'status')` → `(field: 'name' | 'salePrice' | 'status')`.
- Template: `@click="toggleSort('nome')"` → `@click="toggleSort('name')"`, `sortField === 'nome'` → `=== 'name'`, `sortIcon('nome')` → `sortIcon('name')` (2 occurrences: sort-icon-active class binding and the icon call itself), `@click="toggleSort('precoVenda')"` → `toggleSort('salePrice')`, `sortField === 'precoVenda'` → `=== 'salePrice'`, `sortIcon('precoVenda')` → `sortIcon('salePrice')` (×2). The visible column HEADER TEXT ("Produto", "Preço de Venda") stays unchanged — only the sort-key strings change. `data-test="col-nome"` and `data-test="col-preco"` stay exactly as-is (test hooks, not translated).
- `async function carregar(page: number)`: `await listarProdutos({...})` → `await listProducts({...})`; inside the params object, `busca: filtros.busca || undefined` stays unchanged (query param name), `status: (filtros.status || undefined) as StatusProduto | undefined` → `as ProductStatus | undefined`.
- `async function carregarResumo()`: `resumo.value = await buscarResumoProdutos()` → `await getProductSummary()`.
- Template: `v-for="produto in pagina.content"` — local loop variable `produto` stays unchanged (matches convention). `produto.sku` unchanged. `produto.nome` → `produto.name`. `produto.marca` → `produto.brand`. `formatarPreco(produto.precoVenda)` → `formatarPreco(produto.salePrice)`. `produto.quantidadeEstoque` → `produto.stockQuantity`. `statusLabel(produto.status)` unchanged (param name). `produto.status === 'ATIVO' ? 'green' : 'red'` → `=== 'ACTIVE' ? 'green' : 'red'`.
- `async function alternarStatus(produto: ProdutoSummary)` → `(produto: ProductListItem)`. `const novoStatus = produto.status === 'INATIVO' ? 'ATIVO' : 'INATIVO'` → `produto.status === 'INACTIVE' ? 'ACTIVE' : 'INACTIVE'`. `await atualizarStatusProduto(produto.id, novoStatus)` → `await updateProductStatus(produto.id, novoStatus)`.
- `async function excluir(produto: ProdutoSummary)` → `(produto: ProductListItem)`. Confirm dialog text `` `Excluir o produto "${produto.nome}"?` `` → `` `Excluir o produto "${produto.name}"?` `` (only the interpolated property changes, the surrounding Portuguese sentence stays identical). `await excluirProduto(produto.id)` → `await deleteProduct(produto.id)`.
- `function acoesPara(produto: ProdutoSummary)` → `(produto: ProductListItem)`. `produto.status === 'INATIVO' ? 'Ativar' : 'Inativar'` → `=== 'INACTIVE' ? 'Ativar' : 'Inativar'` (labels stay Portuguese).
- `resumo.ativos`/`resumo.inativos` in the `<StatPill>` bindings → `resumo.active`/`resumo.inactive` (the `label="Ativos"`/`label="Inativos"` attribute VALUES stay Portuguese, unchanged).
- `countLabel`: `` `${resumo.value.total} produtos cadastrados` `` stays character-for-character (Portuguese, unchanged) — `resumo.value.total` field name itself is unchanged (`total` wasn't renamed).
- Everything else (all CSS, all remaining Portuguese text, `data-test="busca"`, `data-test="novo-produto"`, all other `data-test` values, route names `'produtos'`/`'produtos-novo'`/`'produtos-editar'` per Global Constraints) is byte-identical to the original.

- [ ] **Step 3: Create `ProductFormView.spec.ts`**

Translate `ProdutoFormView.spec.ts` (243 lines) mechanically: change the import path from `@/views/ProdutoFormView.vue`/whatever relative path it uses to `@/views/ProductFormView.vue`; change every `vi.mock('@/api/produtos')` to `vi.mock('@/api/products')`; change every `import * as produtosApi from '@/api/produtos'`-style import to point at `@/api/products` (keep the local alias name, e.g. `produtosApi`, unchanged — matches this initiative's convention); update every mocked function name referenced through that alias (`produtosApi.buscarProduto`→`produtosApi.getProduct`, `produtosApi.criarProduto`→`produtosApi.createProduct`, `produtosApi.atualizarProduto`→`produtosApi.updateProduct`, etc. — match whichever ones this file actually calls); translate every test fixture object shaped like `ProdutoRequest`/`ProdutoResponse` using the same field map as Step 1 (`nome`→`name`, `precoVenda`→`salePrice`, `precoCusto`→`costPrice`, `quantidadeEstoque`→`stockQuantity`, `unidadeMedida`→`measurementUnit`, `estoqueMinimo`→`minStock`, `estoqueMaximo`→`maxStock`, `peso`→`weight`, `comprimento`→`length`, `largura`→`width`, `altura`→`height`, `codigoBarras`→`barcode`, `marca`→`brand`, `categoriaId`→`categoryId`, `categoriaNome`→`categoryName`, `corEstampaId`→`colorwayId`, `corEstampaNome`→`colorwayName`, `status: 'ATIVO'`/`'INATIVO'`→`'ACTIVE'`/`'INACTIVE'`); translate every assertion that reads `wrapper.find('[data-test="nome"]')`-style selectors — actually leave `data-test` attribute VALUES unchanged (per Step 1's note, these are stable test hooks); every assertion text comparing against Portuguese UI copy (labels, error messages) stays unchanged. This mirrors exactly what the 4a plan's Task 12 did for this same file when Category/Colorway were bridged into it — the same fixture-translation scope applies here for Product's own fixtures.

- [ ] **Step 4: Create `ProductsListView.spec.ts`**

Same mechanical translation approach as Step 3, applied to `ProdutosListView.spec.ts` (123 lines), using `ProductsListView.vue`'s new shape from Step 2 as the reference for which functions/types/fields the mocks and fixtures must match (`listProducts`, `getProductSummary`, `updateProductStatus`, `deleteProduct`, `ProductListItem`, `ProductSummary`).

- [ ] **Step 5: Delete the 4 old files**

```bash
git rm mesh-suite-frontend/src/views/ProdutoFormView.vue \
       mesh-suite-frontend/src/views/ProdutosListView.vue \
       mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts \
       mesh-suite-frontend/src/views/__tests__/ProdutosListView.spec.ts
```

- [ ] **Step 6: Run the new view tests in isolation**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/ProductFormView.spec.ts src/views/__tests__/ProductsListView.spec.ts`

Expected: all tests pass, matching the original files' pass count exactly (no test added, removed, or logic-changed — pure translation). If failures point at a missed field-name substitution, fix it and rerun.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/views/ProductFormView.vue \
        mesh-suite-frontend/src/views/ProductsListView.vue \
        mesh-suite-frontend/src/views/__tests__/ProductFormView.spec.ts \
        mesh-suite-frontend/src/views/__tests__/ProductsListView.spec.ts
git commit -m "refactor(product): rename ProdutoFormView/ProdutosListView to ProductFormView/ProductsListView"
```

---

## Task 13: Frontend bridge — router, DashboardView, PedidoFormView, PurchaseOrderFormView, TabelaPrecoFormView

**Files:**
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/views/DashboardView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/DashboardView.spec.ts`
- Modify: `mesh-suite-frontend/src/views/PedidoFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts`
- Modify: `mesh-suite-frontend/src/views/PurchaseOrderFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/PurchaseOrderFormView.spec.ts`
- Modify: `mesh-suite-frontend/src/views/TabelaPrecoFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/TabelaPrecoFormView.spec.ts`

**Interfaces:**
- Consumes: `products.ts` (Task 11), `ProductFormView.vue`/`ProductsListView.vue` (Task 12).

`AppSidebar.vue` needs NO change — it only holds the unchanged route path `/produtos` and the unchanged Portuguese label `'Produtos'`.

- [ ] **Step 1: Bridge `router/index.ts`**

Change:
```typescript
import ProdutoFormView from '@/views/ProdutoFormView.vue'
import ProdutosListView from '@/views/ProdutosListView.vue'
```
to:
```typescript
import ProductFormView from '@/views/ProductFormView.vue'
import ProductsListView from '@/views/ProductsListView.vue'
```
Change:
```typescript
    { path: '/produtos', name: 'produtos', component: ProdutosListView },
    { path: '/produtos/novo', name: 'produtos-novo', component: ProdutoFormView },
    { path: '/produtos/:id/editar', name: 'produtos-editar', component: ProdutoFormView },
```
to:
```typescript
    { path: '/produtos', name: 'produtos', component: ProductsListView },
    { path: '/produtos/novo', name: 'produtos-novo', component: ProductFormView },
    { path: '/produtos/:id/editar', name: 'produtos-editar', component: ProductFormView },
```
(`path` and `name` values are unchanged per Global Constraints — only the imported component identifiers change.)

- [ ] **Step 2: Bridge `DashboardView.vue`**

Change:
```typescript
import { buscarResumoProdutos, type ProdutoResumo } from '@/api/produtos'
```
to:
```typescript
import { getProductSummary, type ProductSummary } from '@/api/products'
```
Change `const produtoResumo = ref<ProdutoResumo | null>(null)` to `const produtoResumo = ref<ProductSummary | null>(null)` (local variable name `produtoResumo` unchanged). Change `produtoResumo.value.ativos` (inside the stat-card array, `label: 'Produtos Ativos'` stays Portuguese unchanged) to `produtoResumo.value.active`. Change the destructured `Promise.allSettled` result variable name — it's already `produtoR`, unchanged — and its resolved-value assignment `buscarResumoProdutos()` to `getProductSummary()`; the `if (produtoR.status === 'fulfilled') produtoResumo.value = produtoR.value` line's logic is unchanged, only the awaited function name changes. The button `@click="router.push({ name: 'produtos-novo' })"` and its label `+ Novo Produto` are unchanged (route name, Portuguese text).

- [ ] **Step 3: Bridge `DashboardView.spec.ts`**

Change `import * as produtosApi from '@/api/produtos'` to `import * as produtosApi from '@/api/products'` (alias unchanged), `vi.mock('@/api/produtos')` to `vi.mock('@/api/products')`. Change the mock fixture:
```typescript
    vi.mocked(produtosApi.buscarResumoProdutos).mockResolvedValue({
      total: 900, ativos: 856, inativos: 44,
```
to:
```typescript
    vi.mocked(produtosApi.getProductSummary).mockResolvedValue({
      total: 900, active: 856, inactive: 44,
```
Also update the rejected-mock call `vi.mocked(produtosApi.buscarResumoProdutos).mockRejectedValue(...)` to `vi.mocked(produtosApi.getProductSummary).mockRejectedValue(...)`. Leave the router-name assertion `expect(router.currentRoute.value.name).toBe('produtos-novo')` and all `'Produtos Ativos'`/`'+ Novo Produto'` text assertions unchanged.

- [ ] **Step 4: Bridge `PedidoFormView.vue`**

Change `import { listarProdutos, type ProdutoSummary } from '@/api/produtos'` to `import { listProducts, type ProductListItem } from '@/api/products'`. Change `resultadosProdutos = ref<ProdutoSummary[]>([])` to `ref<ProductListItem[]>([])`. Change `const pagina = await listarProdutos({ busca: produtoBusca.value, size: 5 })` to `await listProducts({ busca: produtoBusca.value, size: 5 })` (local names `produtoBusca`, `pagina`, `resultadosProdutos` unchanged; `busca` param name unchanged). Change `function selecionarProduto(produto: ProdutoSummary)` to `(produto: ProductListItem)`. Inside it: `itemForm.produtoNome = produto.nome` → `= produto.name` (left side `itemForm.produtoNome` — Pedido's own field — stays unchanged, only the right side reads the renamed `Product` field); `itemForm.valorUnitario = produto.precoVenda` → `= produto.salePrice`; `produtoBusca.value = produto.nome` → `= produto.name`. Template: `{{ p.nome }} ({{ p.sku }})` where `p` iterates `resultadosProdutos` (typed `ProductListItem[]`) → `{{ p.name }} ({{ p.sku }})`. Every other identifier in this file (`produtoId`, `produtoNome`, `itemForm`, `buscarProdutos`, `resultadosProdutos`, all Portuguese labels/placeholders) is Pedido's own and stays unchanged.

- [ ] **Step 5: Bridge `PedidoFormView.spec.ts`**

Change `import * as produtosApi from '@/api/produtos'` to `'@/api/products'` (alias unchanged), `vi.mock('@/api/produtos')` to `vi.mock('@/api/products')`. Change the fixture:
```typescript
const produtoBase = {
  id: 'p1', nome: 'Camiseta Polo', sku: 'P0001', marca: 'Marca Alpha',
  precoVenda: 59.9, quantidadeEstoque: 10, status: 'ATIVO' as const,
```
to:
```typescript
const produtoBase = {
  id: 'p1', name: 'Camiseta Polo', sku: 'P0001', brand: 'Marca Alpha',
  salePrice: 59.9, stockQuantity: 10, status: 'ACTIVE' as const,
```
Change `vi.mocked(produtosApi.listarProdutos).mockResolvedValue(...)` to `vi.mocked(produtosApi.listProducts).mockResolvedValue(...)` (every occurrence in the file). Leave `itens: [{ produtoId: 'p1', produtoNome: 'Camiseta Polo', ... }]` unchanged — that's Pedido's own submitted-payload shape, not touched by this rename.

- [ ] **Step 6: Bridge `PurchaseOrderFormView.vue`**

Same pattern as Step 4: change the import to `listProducts`/`ProductListItem` from `@/api/products`. Change `resultadosProdutos = ref<ProdutoSummary[]>([])` to `ref<ProductListItem[]>([])`. Change `const pagina = await listarProdutos({ busca: produtoBusca.value, size: 5 })` to `await listProducts(...)`. Change `function selecionarProduto(produto: ProdutoSummary)` to `(produto: ProductListItem)`; inside it, `itemForm.productName = produto.nome` → `= produto.name` (left side `itemForm.productName` is this module's own already-English field, unchanged; only the right side changes), `itemForm.unitPrice = produto.precoVenda` → `= produto.salePrice`, `produtoBusca.value = produto.nome` → `= produto.name`. Template: `{{ p.nome }} ({{ p.sku }})` → `{{ p.name }} ({{ p.sku }})`.

- [ ] **Step 7: Bridge `PurchaseOrderFormView.spec.ts`**

Same pattern as Step 5: import path, mock path, fixture field translation (`produtoBase = { id: 'p1', name: 'Tecido Algodão', sku: 'P0001', brand: 'Marca Alpha', salePrice: 25.0, stockQuantity: 100, status: 'ACTIVE' as const, ... }`), `produtosApi.listarProdutos`→`produtosApi.listProducts`.

- [ ] **Step 8: Bridge `TabelaPrecoFormView.vue`**

Change `import { listarProdutos, type ProdutoSummary } from '@/api/produtos'` to `import { listProducts, type ProductListItem } from '@/api/products'`. Change `resultadosProdutos = ref<ProdutoSummary[]>([])` to `ref<ProductListItem[]>([])`. Change every `await listarProdutos({...})` call to `await listProducts({...})` — there are 3 call sites (`popularTodosOsProdutos`, `buscarProdutos`, and the one inside the edit-mode hydration around line 331); each passes `status: 'ATIVO'` as a filter param — change every one of these to `status: 'ACTIVE'` (this is the ProductStatus wire value, not user-visible text). Change `function adicionarProduto(produto: ProdutoSummary)` to `(produto: ProductListItem)`; inside it, `produtoNome: produto.nome` → `= produto.name` (left side is TabelaPreco's own field, unchanged), `produtoSku: produto.sku` unchanged, `precoCadastrado: produto.precoVenda` → `= produto.salePrice`, `precoNestaTabela: precoParaNovoItem(produto.precoVenda)` → `precoParaNovoItem(produto.salePrice)`. Template: `{{ p.nome }} ({{ p.sku }})` → `{{ p.name }} ({{ p.sku }})`. Everything else in this file (`modoSelecaoProdutos`, `produtoId`, `produtoNome`, `produtoSku`, all TabelaPreco-domain identifiers and Portuguese text) is out of scope for this sub-project (deferred to 4c) and stays unchanged.

- [ ] **Step 9: Bridge `TabelaPrecoFormView.spec.ts`**

Change import/mock path to `@/api/products`. Change the fixture:
```typescript
const produtoAtivo = { id: 'prod-1', nome: 'Camiseta Polo', sku: 'P0001', marca: '', precoVenda: 100, quantidadeEstoque: 10, status: 'ATIVO' as const }
```
to:
```typescript
const produtoAtivo = { id: 'prod-1', name: 'Camiseta Polo', sku: 'P0001', brand: '', salePrice: 100, stockQuantity: 10, status: 'ACTIVE' as const }
```
Change every `vi.mocked(produtosApi.listarProdutos)` to `vi.mocked(produtosApi.listProducts)`. Change the second fixture spread `{ ...produtoAtivo, id: 'prod-2', nome: 'Bermuda', sku: 'P0002' }` to `{ ...produtoAtivo, id: 'prod-2', name: 'Bermuda', sku: 'P0002' }`. Leave `itens: [{ produtoId: 'prod-1', produtoNome: 'Camiseta Polo', produtoSku: 'P0001', precoCadastrado: 100, precoNestaTabela: 120, percentualComissao: 5 }]` unchanged (TabelaPreco's own field names, out of scope). Leave the `expect(...).toBeCloseTo(150, 2) // produtoAtivo.precoVenda=100...`-style comments as-is or update the comment text to `produtoAtivo.salePrice=100` for accuracy — either is acceptable, it has no functional effect (comment only).

- [ ] **Step 10: Run all bridged frontend tests**

Run: `cd mesh-suite-frontend && npx vitest run --run`

Expected: all test files pass (44 files total once Tasks 11-13 are all applied — same count as before this sub-project started, since no test file was added or removed net of the 2 renames in Task 12).

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: 0 errors.

- [ ] **Step 11: Commit**

```bash
git add mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/DashboardView.vue \
        mesh-suite-frontend/src/views/__tests__/DashboardView.spec.ts \
        mesh-suite-frontend/src/views/PedidoFormView.vue \
        mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts \
        mesh-suite-frontend/src/views/PurchaseOrderFormView.vue \
        mesh-suite-frontend/src/views/__tests__/PurchaseOrderFormView.spec.ts \
        mesh-suite-frontend/src/views/TabelaPrecoFormView.vue \
        mesh-suite-frontend/src/views/__tests__/TabelaPrecoFormView.spec.ts
git commit -m "refactor(product): update router and DashboardView/PedidoFormView/PurchaseOrderFormView/TabelaPrecoFormView to consume the renamed products API"
```

---

## Task 14: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`

Expected: 0 failures. Errors matching the documented pre-existing flake exactly (15 errors: 12 `com.meshsuite.payable.*` + 3 `CompanyRepositoryTest`) — confirm via `find target/surefire-reports -name "*.txt" | xargs grep -h "^Tests run:" | awk -F'[ ,]+' '{tests+=$3; failures+=$5; errors+=$7} END {print tests, failures, errors}'` and cross-check the specific failing class names against the signature documented in this initiative's memory. If anything differs, investigate before proceeding.

- [ ] **Step 2: Full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run --run`

Expected: all test files pass, 0 failures.

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: 0 errors. (Do not omit `-p tsconfig.app.json` — bare `npx vue-tsc --noEmit` silently reports 0 errors regardless of real breakage in this project.)

- [ ] **Step 3: Broad grep audit for missed `Produto`/`produto` identifiers**

Run: `grep -rln "Produto\b\|\bproduto\b" mesh-suite-backend/src/main/java --include="*.java" | grep -v "/produto/"`

Expected: no output outside `com.meshsuite.produto` (TabelaPreco's own files, and any remaining Portuguese prose/comments referencing "produto" as a plain word are fine — only bare-word occurrences of the class/field identifiers `Produto`/`produto` OUTSIDE the `produto` package are a problem, and only if they're actual code references, not comments or string literals that are themselves user-visible Portuguese text).

Run the same grep pattern across `mesh-suite-frontend/src` (excluding `.vue`/`.ts` files' Portuguese prose in templates/comments/labels, which is expected to still say "produto" since that's user-visible text) to confirm no leftover `import ... from '@/api/produtos'` or `ProdutoSummary`/`ProdutoResumo`/`StatusProduto` (as opposed to `StatusPedido`, which is unrelated and must NOT be touched) type references remain.

- [ ] **Step 4: Dangling-property-string-literal re-sweep**

Run: `grep -rn "\"nome\"\|\"precoVenda\"\|\"quantidadeEstoque\"\|'nome'\|'precoVenda'\|'quantidadeEstoque'" mesh-suite-backend/src mesh-suite-frontend/src`

For every hit, confirm it's either (a) inside `TabelaPreco*`'s own untouched files (its own `nome` field, unrelated to Product), (b) inside `Pedido`'s own untouched files, (c) Portuguese user-visible text, or (d) already correctly renamed in this sub-project's own files. Confirm zero hits remain inside any Product-owned file (`product/`, `category/service/CategoryService.java`, `colorway/service/ColorwayService.java`, `pedido/service/PedidoService.java`, `purchaseorder/service/PurchaseOrderService.java`, `sale/service/SaleService.java`, `stock/service/StockService.java`, `produto/service/TabelaPrecoService.java`) other than the ones already fixed by this plan.

- [ ] **Step 5: Confirm no leftover verification-technique artifacts**

Run: `git status --short`

Expected: clean (nothing untracked or modified outside what's already committed) — confirms every relocate-test-restore cycle across Tasks 2-10 fully restored its moved files.

Report PLAN COMPLETE once all 4 verification steps pass.
