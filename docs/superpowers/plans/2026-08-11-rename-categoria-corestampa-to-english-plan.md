# Rename Categoria/CorEstampa → Category/Colorway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the `Categoria` and `CorEstampa` modules to `Category` and `Colorway` — sub-project 4a of the "rename to English" initiative — moving them into their own top-level Java packages (`com.meshsuite.category`, `com.meshsuite.colorway`), while leaving every end-customer-visible string (routes, UI labels, error messages) in Portuguese, and applying only the minimal necessary bridge to the not-yet-renamed `Produto` module.

**Architecture:** Two small, structurally parallel CRUD modules renamed independently but in the same plan (Category first, then Colorway, since they don't depend on each other), followed by a bridge into `Produto`/`ProdutoService` (the only consumer), followed by the frontend's own files (which own their screens outright, unlike the Cliente views in the Parceiro sub-project), followed by full-suite verification.

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / PostgreSQL 16 / Flyway (backend); Vue 3 / TypeScript / Vitest (frontend).

## Global Constraints

- Full identifier map: see `docs/superpowers/specs/2026-08-11-rename-categoria-corestampa-to-english-design.md` section 3. Every task below implements a slice of that map — treat the spec as the source of truth for any value not repeated here.
- End-customer-visible text (error message strings, Vue Router paths like `/categorias`/`/cores-estampas`, UI labels like "Categorias", "Cores / Estampas", "Ativo", "Inativo", "Editar", "Excluir") stays in Portuguese, unchanged, in every task.
- `Category`/`Colorway` get their OWN top-level Java packages (`com.meshsuite.category.*`, `com.meshsuite.colorway.*`) — NOT nested under `com.meshsuite.produto.*` where `Categoria`/`CorEstampa` live today. This matches the pattern already used for Sale/Company/Partner.
- Query-string parameter names (`busca`, `ativo`) are NOT translated anywhere in this plan — they match the controllers' own `@RequestParam` names, unchanged from today, consistent with how the Parceiro sub-project treated `busca`/`documento`/`uf`/`cidade`/`papel`.
- The JSON error-envelope key `"mensagem"` is NOT touched — project-wide convention, out of scope (same reasoning as prior sub-projects).
- `mesh-suite-backend/src/main/resources/application.yml` has `jpa.hibernate.ddl-auto: validate` — Hibernate validates every `@Entity`'s columns against the live DB schema at Spring context boot. Task 1 (schema rename) alone leaves the app **unable to boot** until Task 2 (Category domain) and Task 5 (Colorway domain) land — expected, matches the pattern from prior sub-projects. Task 1's own verification is compile/grep-based only.
- Local Postgres must be reset (`docker compose down -v && docker compose up -d`, or the project's equivalent) before running any test from Task 2 onward, because migrations `V21`/`V23` are edited in place — same greenfield pattern as prior sub-projects.
- Known pre-existing, unrelated flake (confirmed on `main`, not caused by any rename sub-project): a full `mvn clean test` run shows 0 failures but 15 errors — 12 in `com.meshsuite.payable.*`, 3 in `CompanyRepositoryTest` — both caused by dev-seed data colliding with hardcoded fixture literals in those test classes when the whole suite shares one Postgres container. Do not attempt to fix this; Task 13's full-suite verification must reproduce exactly this signature and treat it as passing.
- **Lesson from the Parceiro sub-project, already baked into this plan**: a class of bug exists where a string literal encodes a JPA/Hibernate property path or a frontend sort-key value pointing at a renamed field, invisible to a `grep -r "Categoria"` sweep. The design spec's own research (section 2, "Fora de escopo") already confirmed `ProdutoRepository.java` and `ProdutoSpecifications.java` have no such dangling references for `Categoria`/`CorEstampa` — but Task 13's verification must re-confirm this with a fresh grep, as a safety net.

---

### Task 1: Database schema — create `category`/`colorway` tables, fix dependent FKs

**Files:**
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V21__create_categoria.sql` → rename to `V21__create_category.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V22__replace_produto_categoria_with_fk.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V23__create_cor_estampa.sql` → rename to `V23__create_colorway.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V24__add_cor_estampa_to_produto.sql`

**Interfaces:**
- Produces: DB table `category` (columns `id`, `tenant_id`, `name`, `description`, `active`, `created_at`), table `colorway` (columns `id`, `tenant_id`, `name`, `effective_date`, `description`, `active`, `created_at`) — consumed by Task 2's `Category` and Task 5's `Colorway` entities. `produto.categoria_id`/`produto.cor_estampa_id` FK columns keep their current names, now pointing at the renamed tables.

- [ ] **Step 1: Rename and rewrite the V21 migration**

```bash
git mv mesh-suite-backend/src/main/resources/db/migration/V21__create_categoria.sql \
       mesh-suite-backend/src/main/resources/db/migration/V21__create_category.sql
```

Replace the entire content of `V21__create_category.sql` with:

```sql
CREATE TABLE category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_category_tenant_name ON category(tenant_id, name);
CREATE INDEX idx_category_tenant_id ON category(tenant_id);

ALTER TABLE category ENABLE ROW LEVEL SECURITY;
ALTER TABLE category FORCE ROW LEVEL SECURITY;

CREATE POLICY category_tenant_isolation ON category
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

- [ ] **Step 2: Fix the FK target in `V22__replace_produto_categoria_with_fk.sql`**

Change:
```sql
ALTER TABLE produto DROP COLUMN categoria;
ALTER TABLE produto ADD COLUMN categoria_id UUID REFERENCES categoria(id);
```
to:
```sql
ALTER TABLE produto DROP COLUMN categoria;
ALTER TABLE produto ADD COLUMN categoria_id UUID REFERENCES category(id);
```
(the column name `categoria_id` stays — it belongs to `produto`, out of scope until sub-project 4b).

- [ ] **Step 3: Rename and rewrite the V23 migration**

```bash
git mv mesh-suite-backend/src/main/resources/db/migration/V23__create_cor_estampa.sql \
       mesh-suite-backend/src/main/resources/db/migration/V23__create_colorway.sql
```

Replace the entire content of `V23__create_colorway.sql` with:

```sql
CREATE TABLE colorway (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    effective_date DATE NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_colorway_tenant_name ON colorway(tenant_id, name);
CREATE INDEX idx_colorway_tenant_id ON colorway(tenant_id);

ALTER TABLE colorway ENABLE ROW LEVEL SECURITY;
ALTER TABLE colorway FORCE ROW LEVEL SECURITY;

CREATE POLICY colorway_tenant_isolation ON colorway
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

- [ ] **Step 4: Fix the FK target in `V24__add_cor_estampa_to_produto.sql`**

Change:
```sql
ALTER TABLE produto ADD COLUMN cor_estampa_id UUID REFERENCES cor_estampa(id);
```
to:
```sql
ALTER TABLE produto ADD COLUMN cor_estampa_id UUID REFERENCES colorway(id);
```

- [ ] **Step 5: Verify**

Run:
```bash
grep -rn "categoria\|cor_estampa" mesh-suite-backend/src/main/resources/db/migration/V21__create_category.sql \
  mesh-suite-backend/src/main/resources/db/migration/V22__replace_produto_categoria_with_fk.sql \
  mesh-suite-backend/src/main/resources/db/migration/V23__create_colorway.sql \
  mesh-suite-backend/src/main/resources/db/migration/V24__add_cor_estampa_to_produto.sql
```
Expected: only `V22`'s `DROP COLUMN categoria;`/`ADD COLUMN categoria_id` line and `V24`'s `ADD COLUMN cor_estampa_id` line should show a match (the untouched `produto` column names) — nothing else.

Run:
```bash
cd mesh-suite-backend && mvn -q compile
```
Expected: `BUILD SUCCESS`. Do NOT run `mvn test` yet — Hibernate's `ddl-auto: validate` will fail every test until Tasks 2 and 5 rename the `Categoria`/`CorEstampa` entities to match this new schema. That is expected.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V21__create_category.sql \
        mesh-suite-backend/src/main/resources/db/migration/V22__replace_produto_categoria_with_fk.sql \
        mesh-suite-backend/src/main/resources/db/migration/V23__create_colorway.sql \
        mesh-suite-backend/src/main/resources/db/migration/V24__add_cor_estampa_to_produto.sql
git commit -m "refactor(category-colorway): rename V21/V23 migrations, fix dependent FKs in V22/V24"
```

---

### Task 2: Category domain, repository, repository test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/domain/Category.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/repository/CategoryRepository.java`
- Delete (via `git rm`, content moves): `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/Categoria.java`, `mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/CategoriaRepository.java`, `mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/CategoriaRepositoryTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/category/repository/CategoryRepositoryTest.java`

**Interfaces:**
- Produces: `Category` (fields: `id`, `tenantId`, `name`, `description`, `active`, `createdAt`); `CategoryRepository` (`existsByName(String): boolean`, `existsByNameAndIdNot(String, UUID): boolean`) — consumed by Task 3.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/Categoria.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/CategoriaRepository.java
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/CategoriaRepositoryTest.java
```

- [ ] **Step 2: Create `Category.java`**

```java
package com.meshsuite.category.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category")
@Getter
@Setter
public class Category {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 3: Create `CategoryRepository.java`**

```java
package com.meshsuite.category.repository;

import com.meshsuite.category.domain.Category;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
}
```

- [ ] **Step 4: Create `CategoryRepositoryTest.java`**

Read the original at `git show HEAD:mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/CategoriaRepositoryTest.java` (deleted in Step 1 — capture its content before/while deleting, or read it from git history) and recreate it at `mesh-suite-backend/src/test/java/com/meshsuite/category/repository/CategoryRepositoryTest.java` with the exact same 4 test cases, applying:

- Package: `com.meshsuite.produto.repository` → `com.meshsuite.category.repository`.
- Imports: `com.meshsuite.produto.domain.Categoria` → `com.meshsuite.category.domain.Category`; `com.meshsuite.produto.repository.CategoriaRepository` → `com.meshsuite.category.repository.CategoryRepository`.
- Types: `Categoria→Category`, `CategoriaRepository→CategoryRepository`.
- Fields/methods used in fixtures: `.setNome(...)→.setName(...)`, `.getNome()→.getName()` (if present), `existsByNome→existsByName` (if directly called), the `@Autowired CategoriaRepository categoriaRepository` field → `@Autowired CategoryRepository categoryRepository`.
- Test method names (translate exactly): `savesCategoriaWithDefaults→savesCategoryWithDefaults`, `nomeMustBeUniquePerTenant→nameMustBeUniquePerTenant`, `sameNomeAllowedAcrossDifferentTenants→sameNameAllowedAcrossDifferentTenants`, `rlsHidesRowsWhenTenantContextUnset` (unchanged).
- Any raw SQL string in the test (e.g. `SELECT count(*) FROM categoria`) must update the table name to `category`.
- Business-data literal values (e.g. sample name strings) stay exactly as they are.

- [ ] **Step 5: Reset the local database, then verify**

```bash
docker compose down -v && docker compose up -d
```
(Or the project's equivalent reset command.)

Run:
```bash
cd mesh-suite-backend && mvn -q test -Dtest=CategoryRepositoryTest
```
Expected: `BUILD SUCCESS`, 4/4 tests pass. This is the first task to boot the full Spring context after Task 1 — a passing run proves Task 1's `category` table schema is correct.

Note: at this point `Colorway` (Task 5) doesn't exist yet, and `Produto`/`ProdutoService` still reference the now-deleted `Categoria`/`CategoriaRepository` — the whole module will not `mvn test-compile` cleanly yet. This is expected; the compile-bridge into `Produto` happens in Task 8, after both `Category` and `Colorway` exist. To verify `CategoryRepositoryTest` in isolation now, use the relocate-test-restore technique: temporarily move `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/Produto.java`, `mesh-suite-backend/src/main/java/com/meshsuite/produto/service/ProdutoService.java`, and any file that imports them (check with `grep -rl "import com.meshsuite.produto.domain.Produto\|import com.meshsuite.produto.service.ProdutoService" mesh-suite-backend/src --include="*.java"`) out of `src/` to a temp directory, run the test, then restore them exactly and verify `git status --short` is clean of anything unexpected.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/category/domain/Category.java \
        mesh-suite-backend/src/main/java/com/meshsuite/category/repository/CategoryRepository.java \
        mesh-suite-backend/src/test/java/com/meshsuite/category/repository/CategoryRepositoryTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/Categoria.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/CategoriaRepository.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/CategoriaRepositoryTest.java
git commit -m "refactor(category): rename Categoria domain/repository to Category, new com.meshsuite.category package"
```

---

### Task 3: Category DTOs, exceptions, specifications, service, service test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/dto/CategoryRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/dto/CategoryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/exception/CategoryInUseException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/exception/CategoryNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/exception/DuplicateCategoryNameException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/repository/specification/CategorySpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/service/CategoryService.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaRequest.java`, `CategoriaResponse.java`, `mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaEmUsoException.java`, `CategoriaNaoEncontradaException.java`, `CategoriaNomeDuplicadoException.java` (NOT `CategoriaExceptionHandler.java` — that one is Task 4's), `mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/specification/CategoriaSpecifications.java`, `mesh-suite-backend/src/main/java/com/meshsuite/produto/service/CategoriaService.java`, `mesh-suite-backend/src/test/java/com/meshsuite/produto/service/CategoriaServiceTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/category/service/CategoryServiceTest.java`

**Interfaces:**
- Consumes: `Category`, `CategoryRepository` (Task 2); `com.meshsuite.produto.repository.ProdutoRepository` (existing, unchanged, out of scope — has `countByCategoriaId(UUID): long`, `countByCategoriaIdIn(Collection<UUID>): List<CategoriaProdutoCount>`, and the projection interface `ProdutoRepository.CategoriaProdutoCount` with `getCategoriaId(): UUID`/`getTotal(): Long` — these Portuguese names are NOT renamed in this plan).
- Produces: `CategoryService` with `list(String search, Boolean active, Pageable pageable): Page<CategoryResponse>`, `findById(UUID id): CategoryResponse`, `create(UUID tenantId, CategoryRequest request): CategoryResponse`, `update(UUID id, CategoryRequest request): CategoryResponse`, `delete(UUID id): void` — consumed by Task 4's `CategoryController`.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaRequest.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaResponse.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaEmUsoException.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaNaoEncontradaException.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaNomeDuplicadoException.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/specification/CategoriaSpecifications.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/service/CategoriaService.java
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/service/CategoriaServiceTest.java
```

- [ ] **Step 2: Create the DTOs**

`mesh-suite-backend/src/main/java/com/meshsuite/category/dto/CategoryRequest.java`:
```java
package com.meshsuite.category.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank String name,
        String description,
        Boolean active) {
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/category/dto/CategoryResponse.java`:
```java
package com.meshsuite.category.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        Boolean active,
        Long linkedProducts,
        Instant createdAt) {
}
```

- [ ] **Step 3: Create the three exceptions**

`mesh-suite-backend/src/main/java/com/meshsuite/category/exception/CategoryInUseException.java`:
```java
package com.meshsuite.category.exception;

public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException(long productCount) {
        super("Não é possível excluir: " + productCount + " produto(s) usam esta categoria");
    }
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/category/exception/CategoryNotFoundException.java`:
```java
package com.meshsuite.category.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException() {
        super("Categoria não encontrada");
    }
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/category/exception/DuplicateCategoryNameException.java`:
```java
package com.meshsuite.category.exception;

public class DuplicateCategoryNameException extends RuntimeException {
    public DuplicateCategoryNameException() {
        super("Já existe uma categoria cadastrada com este nome");
    }
}
```

Note: the exception messages stay in Portuguese — HTTP error responses shown to the end user. The original `CategoriaNaoEncontradaException.java` had an unused import (`import com.meshsuite.produto.domain.Categoria;`, never referenced in the class body) — do not carry it forward, since the package is moving anyway and the import was never functionally needed.

- [ ] **Step 4: Create `CategorySpecifications.java`**

```java
package com.meshsuite.category.repository.specification;

import com.meshsuite.category.domain.Category;
import org.springframework.data.jpa.domain.Specification;

public final class CategorySpecifications {

    private CategorySpecifications() {
    }

    public static Specification<Category> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), term);
    }

    public static Specification<Category> withActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
```

- [ ] **Step 5: Create `CategoryService.java`**

```java
package com.meshsuite.category.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.category.domain.Category;
import com.meshsuite.category.dto.CategoryRequest;
import com.meshsuite.category.dto.CategoryResponse;
import com.meshsuite.category.exception.CategoryInUseException;
import com.meshsuite.category.exception.CategoryNotFoundException;
import com.meshsuite.category.exception.DuplicateCategoryNameException;
import com.meshsuite.category.repository.CategoryRepository;
import com.meshsuite.category.repository.specification.CategorySpecifications;
import com.meshsuite.produto.repository.ProdutoRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProdutoRepository produtoRepository;

    public CategoryService(CategoryRepository categoryRepository, ProdutoRepository produtoRepository) {
        this.categoryRepository = categoryRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<CategoryResponse> list(String search, Boolean active, Pageable pageable) {
        Specification<Category> spec = Specification.allOf(
                CategorySpecifications.withSearch(search),
                CategorySpecifications.withActive(active));
        Page<Category> page = categoryRepository.findAll(spec, pageable);

        List<UUID> ids = page.getContent().stream().map(Category::getId).toList();
        Map<UUID, Long> counts = ids.isEmpty()
                ? Map.of()
                : produtoRepository.countByCategoriaIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ProdutoRepository.CategoriaProdutoCount::getCategoriaId,
                                ProdutoRepository.CategoriaProdutoCount::getTotal));

        return page.map(category -> toResponse(category, counts.getOrDefault(category.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public CategoryResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public CategoryResponse create(UUID tenantId, CategoryRequest request) {
        validateName(request.name(), null);

        Category category = new Category();
        category.setTenantId(tenantId);
        apply(category, request);
        return toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public CategoryResponse update(UUID id, CategoryRequest request) {
        validateName(request.name(), id);

        Category category = findEntityById(id);
        apply(category, request);
        return toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void delete(UUID id) {
        Category category = findEntityById(id);
        long linked = produtoRepository.countByCategoriaId(id);
        if (linked > 0) {
            throw new CategoryInUseException(linked);
        }
        categoryRepository.delete(category);
    }

    private Category findEntityById(UUID id) {
        return categoryRepository.findById(id).orElseThrow(CategoryNotFoundException::new);
    }

    private void validateName(String name, UUID currentId) {
        boolean duplicate = currentId == null
                ? categoryRepository.existsByName(name)
                : categoryRepository.existsByNameAndIdNot(name, currentId);
        if (duplicate) {
            throw new DuplicateCategoryNameException();
        }
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name());
        category.setDescription(request.description());
        category.setActive(request.active() != null ? request.active() : true);
    }

    private CategoryResponse toResponse(Category category) {
        return toResponse(category, produtoRepository.countByCategoriaId(category.getId()));
    }

    private CategoryResponse toResponse(Category category, long linkedProducts) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getDescription(), category.getActive(),
                linkedProducts, category.getCreatedAt());
    }
}
```

Note: `import com.meshsuite.produto.repository.ProdutoRepository;` and the calls `produtoRepository.countByCategoriaIdIn(ids)`, `ProdutoRepository.CategoriaProdutoCount::getCategoriaId`, `produtoRepository.countByCategoriaId(id)` are DELIBERATELY untouched — `ProdutoRepository`'s own Portuguese method/type names are out of scope for this plan (reserved for sub-project 4b).

- [ ] **Step 6: Create `CategoryServiceTest.java`**

Read `git show HEAD:mesh-suite-backend/src/test/java/com/meshsuite/produto/service/CategoriaServiceTest.java` (8 test methods) and recreate it at `mesh-suite-backend/src/test/java/com/meshsuite/category/service/CategoryServiceTest.java` with the exact same test cases, applying:

- Package/imports: `com.meshsuite.produto.service` → `com.meshsuite.category.service`; every `com.meshsuite.produto.{domain,dto,exception,repository}.Categoria*` import → the matching `com.meshsuite.category.*` path. The import of `com.meshsuite.produto.repository.ProdutoRepository` (or its test double) stays unchanged.
- Types: `Categoria→Category`, `CategoriaRequest→CategoryRequest`, `CategoriaResponse→CategoryResponse`, `CategoriaService→CategoryService`, `CategoriaRepository→CategoryRepository`, `CategoriaEmUsoException→CategoryInUseException`, `CategoriaNaoEncontradaException→CategoryNotFoundException`, `CategoriaNomeDuplicadoException→DuplicateCategoryNameException`.
- Fields/methods: `.nome()→.name()`, `.descricao()→.description()`, `.ativo()→.active()`, `categoriaService→categoryService`, `.listar(...)→.list(...)`, `.buscarPorId(...)→.findById(...)`, `.criar(...)→.create(...)`, `.atualizar(...)→.update(...)`, `.excluir(...)→.delete(...)`.
- Test method names (translate all 8 — exact map from the design spec):
  - `criaERecuperaCategoria` → `createsAndRetrievesCategory`
  - `rejectsDuplicateNomeOnCreate` → `rejectsDuplicateNameOnCreate`
  - `rejectsDuplicateNomeOnUpdateAgainstAnotherCategoria` → `rejectsDuplicateNameOnUpdateAgainstAnotherCategory`
  - `allowsUpdatingACategoriaWithoutChangingItsOwnNome` → `allowsUpdatingACategoryWithoutChangingItsOwnName`
  - `deletesUnusedCategoria` → `deletesUnusedCategory`
  - `rejectsDeletingACategoriaInUseByAProduto` → `rejectsDeletingACategoryInUseByAProduct`
  - `listFiltersByAtivo` → `listFiltersByActive`
  - `listAggregatesProdutosVinculadosPerCategoriaInASingleBatch` → `listAggregatesLinkedProductsPerCategoryInASingleBatch`
- Business-data literals (names, descriptions used as sample text) stay exactly as they are — only identifiers and enum/field-backed values change.

- [ ] **Step 7: Verify**

Relocate `Produto.java`/`ProdutoService.java` (and anything importing them) the same way as Task 2 Step 5, run:
```bash
cd mesh-suite-backend && mvn -q test -Dtest=CategoryServiceTest
```
Expected: `BUILD SUCCESS`, 8/8 tests pass (same count as the original `CategoriaServiceTest`). Restore the relocated files; verify `git status --short` is clean of anything unexpected.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/category/dto \
        mesh-suite-backend/src/main/java/com/meshsuite/category/exception \
        mesh-suite-backend/src/main/java/com/meshsuite/category/repository/specification \
        mesh-suite-backend/src/main/java/com/meshsuite/category/service \
        mesh-suite-backend/src/test/java/com/meshsuite/category/service/CategoryServiceTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaRequest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaEmUsoException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaNaoEncontradaException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaNomeDuplicadoException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/specification/CategoriaSpecifications.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/service/CategoriaService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/service/CategoriaServiceTest.java
git commit -m "refactor(category): rename Categoria DTOs, exceptions, specifications, and service layer to English"
```

---

### Task 4: Category controller, exception handler, controller test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/controller/CategoryController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/category/exception/CategoryExceptionHandler.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/CategoriaController.java`, `mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaExceptionHandler.java`, `mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CategoriaControllerTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/category/controller/CategoryControllerTest.java`

**Interfaces:**
- Consumes: `CategoryService` and its DTOs (Task 3).
- Produces: `CategoryController` at `/api/categories` with routes `GET /api/categories`, `GET /api/categories/{id}`, `POST /api/categories`, `PUT /api/categories/{id}`, `DELETE /api/categories/{id}`.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/CategoriaController.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaExceptionHandler.java
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CategoriaControllerTest.java
```

- [ ] **Step 2: Create `CategoryController.java`**

```java
package com.meshsuite.category.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.category.dto.CategoryRequest;
import com.meshsuite.category.dto.CategoryResponse;
import com.meshsuite.category.service.CategoryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Page<CategoryResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return categoryService.list(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public CategoryResponse findById(@PathVariable UUID id) {
        return categoryService.findById(id);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                     @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

Note: `sort = "name"` (already correct above — matches `Category.name`, NOT the old `sort = "nome"`). Query-param names `busca`/`ativo` stay untranslated.

- [ ] **Step 3: Create `CategoryExceptionHandler.java`**

```java
package com.meshsuite.category.exception;

import com.meshsuite.category.controller.CategoryController;
import com.meshsuite.category.service.CategoryService;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CategoryController.class)
public class CategoryExceptionHandler {

    // Fallback for a race condition slipping past CategoryService's pre-check
    // (two concurrent requests for the same new name) -- the DB's
    // UNIQUE(tenant_id, name) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma categoria cadastrada com este nome"));
    }
}
```

- [ ] **Step 4: Create `CategoryControllerTest.java`**

Read `git show HEAD:mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CategoriaControllerTest.java` (6 test methods) and recreate it at `mesh-suite-backend/src/test/java/com/meshsuite/category/controller/CategoryControllerTest.java` with the exact same test cases, applying:

- All type/field substitutions from Task 3's table.
- URL paths: `"/api/categorias"` (and `"/api/categorias/{id}"`) → `"/api/categories"` (and matching sub-paths).
- Test method names: `createsListsUpdatesAndDeletesCategoria→createsListsUpdatesAndDeletesCategory`, `rejectsDuplicateNomeWithConflict→rejectsDuplicateNameWithConflict`, `rejectsDeletingACategoriaInUseWithBadRequest→rejectsDeletingACategoryInUseWithBadRequest`, `tenantACannotAccessTenantBsCategoria→tenantACannotAccessTenantBsCategory`, `unauthenticatedRequestIsRejected` (unchanged), `listingWithoutProductViewPermissionIsForbidden` (unchanged).
- JSON body field names in `jsonPath(...)` assertions and request payload literals: apply the field map (`nome→name`, `descricao→description`, `ativo→active`, `produtosVinculados→linkedProducts`, `criadoEm→createdAt`).

- [ ] **Step 5: Verify**

Relocate `Produto.java`/`ProdutoService.java` (and anything importing them) the same way as prior tasks. Run:
```bash
cd mesh-suite-backend && mvn -q test -Dtest=CategoryControllerTest
```
Expected: `BUILD SUCCESS`, 6/6 tests pass. Restore the relocated files; verify `git status --short` is clean of anything unexpected.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/category/controller \
        mesh-suite-backend/src/main/java/com/meshsuite/category/exception/CategoryExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/category/controller/CategoryControllerTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/CategoriaController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CategoriaExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CategoriaControllerTest.java
git commit -m "refactor(category): rename Categoria controller and exception handler to English, route categorias->categories"
```

Note: after this task, `mesh-suite-backend/src/main/java/com/meshsuite/produto/` should have zero files with "Categoria" in the name.

---

### Task 5: Colorway domain, repository, repository test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/domain/Colorway.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/repository/ColorwayRepository.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/CorEstampa.java`, `mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/CorEstampaRepository.java`, `mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/CorEstampaRepositoryTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/colorway/repository/ColorwayRepositoryTest.java`

**Interfaces:**
- Produces: `Colorway` (fields: `id`, `tenantId`, `name`, `effectiveDate`, `description`, `active`, `createdAt`); `ColorwayRepository` (`existsByName(String): boolean`, `existsByNameAndIdNot(String, UUID): boolean`) — consumed by Task 6.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/CorEstampa.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/CorEstampaRepository.java
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/CorEstampaRepositoryTest.java
```

- [ ] **Step 2: Create `Colorway.java`**

```java
package com.meshsuite.colorway.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "colorway")
@Getter
@Setter
public class Colorway {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 3: Create `ColorwayRepository.java`**

```java
package com.meshsuite.colorway.repository;

import com.meshsuite.colorway.domain.Colorway;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ColorwayRepository extends JpaRepository<Colorway, UUID>, JpaSpecificationExecutor<Colorway> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
}
```

- [ ] **Step 4: Create `ColorwayRepositoryTest.java`**

Read `git show HEAD:mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/CorEstampaRepositoryTest.java` (4 test methods) and recreate it at `mesh-suite-backend/src/test/java/com/meshsuite/colorway/repository/ColorwayRepositoryTest.java`, applying the same substitution pattern as Task 2 Step 4:

- Package/imports/types: `CorEstampa→Colorway`, `CorEstampaRepository→ColorwayRepository`, `com.meshsuite.produto.*→com.meshsuite.colorway.*`.
- Fields: `.setNome(...)→.setName(...)`, `.setDataVigencia(...)→.setEffectiveDate(...)` (if present in the fixture), `corEstampaRepository→colorwayRepository`.
- Test method names: `savesCorEstampaWithDefaults→savesColorwayWithDefaults`, `nomeMustBeUniquePerTenant→nameMustBeUniquePerTenant`, `sameNomeAllowedAcrossDifferentTenants→sameNameAllowedAcrossDifferentTenants`, `rlsHidesRowsWhenTenantContextUnset` (unchanged).
- Any raw SQL string (e.g. `SELECT count(*) FROM cor_estampa`) updates the table name to `colorway`.

- [ ] **Step 5: Verify**

Relocate `Produto.java`/`ProdutoService.java` (and anything importing them) the same way as Task 2. Run:
```bash
cd mesh-suite-backend && mvn -q test -Dtest=ColorwayRepositoryTest
```
Expected: `BUILD SUCCESS`, 4/4 tests pass. Restore the relocated files; verify `git status --short` is clean of anything unexpected.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/colorway/domain/Colorway.java \
        mesh-suite-backend/src/main/java/com/meshsuite/colorway/repository/ColorwayRepository.java \
        mesh-suite-backend/src/test/java/com/meshsuite/colorway/repository/ColorwayRepositoryTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/CorEstampa.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/CorEstampaRepository.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/repository/CorEstampaRepositoryTest.java
git commit -m "refactor(colorway): rename CorEstampa domain/repository to Colorway, new com.meshsuite.colorway package"
```

---

### Task 6: Colorway DTOs, exceptions, specifications, service, service test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/dto/ColorwayRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/dto/ColorwayResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/exception/ColorwayInUseException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/exception/ColorwayNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/exception/DuplicateColorwayNameException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/repository/specification/ColorwaySpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/service/ColorwayService.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaRequest.java`, `CorEstampaResponse.java`, `mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaEmUsoException.java`, `CorEstampaNaoEncontradaException.java`, `CorEstampaNomeDuplicadoException.java` (NOT `CorEstampaExceptionHandler.java`), `mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/specification/CorEstampaSpecifications.java`, `mesh-suite-backend/src/main/java/com/meshsuite/produto/service/CorEstampaService.java`, `mesh-suite-backend/src/test/java/com/meshsuite/produto/service/CorEstampaServiceTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/colorway/service/ColorwayServiceTest.java`

**Interfaces:**
- Consumes: `Colorway`, `ColorwayRepository` (Task 5); `com.meshsuite.produto.repository.ProdutoRepository` (existing, unchanged — `countByCorEstampaId(UUID): long`, `countByCorEstampaIdIn(Collection<UUID>): List<CorEstampaProdutoCount>`, projection `ProdutoRepository.CorEstampaProdutoCount` with `getCorEstampaId(): UUID`/`getTotal(): Long`).
- Produces: `ColorwayService` with `list(String search, Boolean active, Pageable pageable): Page<ColorwayResponse>`, `findById(UUID id): ColorwayResponse`, `create(UUID tenantId, ColorwayRequest request): ColorwayResponse`, `update(UUID id, ColorwayRequest request): ColorwayResponse`, `delete(UUID id): void` — consumed by Task 7.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaRequest.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaResponse.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaEmUsoException.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaNaoEncontradaException.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaNomeDuplicadoException.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/specification/CorEstampaSpecifications.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/service/CorEstampaService.java
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/service/CorEstampaServiceTest.java
```

- [ ] **Step 2: Create the DTOs**

`mesh-suite-backend/src/main/java/com/meshsuite/colorway/dto/ColorwayRequest.java`:
```java
package com.meshsuite.colorway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ColorwayRequest(
        @NotBlank String name,
        @NotNull LocalDate effectiveDate,
        String description,
        Boolean active) {
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/colorway/dto/ColorwayResponse.java`:
```java
package com.meshsuite.colorway.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ColorwayResponse(
        UUID id,
        String name,
        LocalDate effectiveDate,
        String description,
        Boolean active,
        Long linkedProducts,
        Instant createdAt) {
}
```

- [ ] **Step 3: Create the three exceptions**

`mesh-suite-backend/src/main/java/com/meshsuite/colorway/exception/ColorwayInUseException.java`:
```java
package com.meshsuite.colorway.exception;

public class ColorwayInUseException extends RuntimeException {
    public ColorwayInUseException(long productCount) {
        super("Não é possível excluir: " + productCount + " produto(s) usam esta cor/estampa");
    }
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/colorway/exception/ColorwayNotFoundException.java`:
```java
package com.meshsuite.colorway.exception;

public class ColorwayNotFoundException extends RuntimeException {
    public ColorwayNotFoundException() {
        super("Cor/Estampa não encontrada");
    }
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/colorway/exception/DuplicateColorwayNameException.java`:
```java
package com.meshsuite.colorway.exception;

public class DuplicateColorwayNameException extends RuntimeException {
    public DuplicateColorwayNameException() {
        super("Já existe uma cor/estampa cadastrada com este nome");
    }
}
```

- [ ] **Step 4: Create `ColorwaySpecifications.java`**

```java
package com.meshsuite.colorway.repository.specification;

import com.meshsuite.colorway.domain.Colorway;
import org.springframework.data.jpa.domain.Specification;

public final class ColorwaySpecifications {

    private ColorwaySpecifications() {
    }

    public static Specification<Colorway> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), term);
    }

    public static Specification<Colorway> withActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
```

- [ ] **Step 5: Create `ColorwayService.java`**

```java
package com.meshsuite.colorway.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.colorway.domain.Colorway;
import com.meshsuite.colorway.dto.ColorwayRequest;
import com.meshsuite.colorway.dto.ColorwayResponse;
import com.meshsuite.colorway.exception.ColorwayInUseException;
import com.meshsuite.colorway.exception.ColorwayNotFoundException;
import com.meshsuite.colorway.exception.DuplicateColorwayNameException;
import com.meshsuite.colorway.repository.ColorwayRepository;
import com.meshsuite.colorway.repository.specification.ColorwaySpecifications;
import com.meshsuite.produto.repository.ProdutoRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ColorwayService {

    private final ColorwayRepository colorwayRepository;
    private final ProdutoRepository produtoRepository;

    public ColorwayService(ColorwayRepository colorwayRepository, ProdutoRepository produtoRepository) {
        this.colorwayRepository = colorwayRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<ColorwayResponse> list(String search, Boolean active, Pageable pageable) {
        Specification<Colorway> spec = Specification.allOf(
                ColorwaySpecifications.withSearch(search),
                ColorwaySpecifications.withActive(active));
        Page<Colorway> page = colorwayRepository.findAll(spec, pageable);

        List<UUID> ids = page.getContent().stream().map(Colorway::getId).toList();
        Map<UUID, Long> counts = ids.isEmpty()
                ? Map.of()
                : produtoRepository.countByCorEstampaIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ProdutoRepository.CorEstampaProdutoCount::getCorEstampaId,
                                ProdutoRepository.CorEstampaProdutoCount::getTotal));

        return page.map(colorway -> toResponse(colorway, counts.getOrDefault(colorway.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ColorwayResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public ColorwayResponse create(UUID tenantId, ColorwayRequest request) {
        validateName(request.name(), null);

        Colorway colorway = new Colorway();
        colorway.setTenantId(tenantId);
        apply(colorway, request);
        return toResponse(colorwayRepository.saveAndFlush(colorway));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ColorwayResponse update(UUID id, ColorwayRequest request) {
        validateName(request.name(), id);

        Colorway colorway = findEntityById(id);
        apply(colorway, request);
        return toResponse(colorwayRepository.saveAndFlush(colorway));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void delete(UUID id) {
        Colorway colorway = findEntityById(id);
        long linked = produtoRepository.countByCorEstampaId(id);
        if (linked > 0) {
            throw new ColorwayInUseException(linked);
        }
        colorwayRepository.delete(colorway);
    }

    private Colorway findEntityById(UUID id) {
        return colorwayRepository.findById(id).orElseThrow(ColorwayNotFoundException::new);
    }

    private void validateName(String name, UUID currentId) {
        boolean duplicate = currentId == null
                ? colorwayRepository.existsByName(name)
                : colorwayRepository.existsByNameAndIdNot(name, currentId);
        if (duplicate) {
            throw new DuplicateColorwayNameException();
        }
    }

    private void apply(Colorway colorway, ColorwayRequest request) {
        colorway.setName(request.name());
        colorway.setEffectiveDate(request.effectiveDate());
        colorway.setDescription(request.description());
        colorway.setActive(request.active() != null ? request.active() : true);
    }

    private ColorwayResponse toResponse(Colorway colorway) {
        return toResponse(colorway, produtoRepository.countByCorEstampaId(colorway.getId()));
    }

    private ColorwayResponse toResponse(Colorway colorway, long linkedProducts) {
        return new ColorwayResponse(
                colorway.getId(), colorway.getName(), colorway.getEffectiveDate(), colorway.getDescription(),
                colorway.getActive(), linkedProducts, colorway.getCreatedAt());
    }
}
```

- [ ] **Step 6: Create `ColorwayServiceTest.java`**

Read `git show HEAD:mesh-suite-backend/src/test/java/com/meshsuite/produto/service/CorEstampaServiceTest.java` (8 test methods) and recreate it at `mesh-suite-backend/src/test/java/com/meshsuite/colorway/service/ColorwayServiceTest.java`, applying:

- Package/imports/types: same pattern as Task 3 Step 6, with `CorEstampa→Colorway` throughout.
- Fields/methods: `.nome()→.name()`, `.dataVigencia()→.effectiveDate()`, `.descricao()→.description()`, `.ativo()→.active()`, `corEstampaService→colorwayService`, method renames matching Task 3's table.
- Test method names (translate all 8 — exact map from the design spec):
  - `criaERecuperaCorEstampa` → `createsAndRetrievesColorway`
  - `rejectsDuplicateNomeOnCreate` → `rejectsDuplicateNameOnCreate`
  - `rejectsDuplicateNomeOnUpdateAgainstAnotherCorEstampa` → `rejectsDuplicateNameOnUpdateAgainstAnotherColorway`
  - `allowsUpdatingACorEstampaWithoutChangingItsOwnNome` → `allowsUpdatingAColorwayWithoutChangingItsOwnName`
  - `deletesUnusedCorEstampa` → `deletesUnusedColorway`
  - `rejectsDeletingACorEstampaInUseByAProduto` → `rejectsDeletingAColorwayInUseByAProduct`
  - `listFiltersByAtivo` → `listFiltersByActive`
  - `listAggregatesProdutosVinculadosPerCorEstampaInASingleBatch` → `listAggregatesLinkedProductsPerColorwayInASingleBatch`
- Business-data literals stay exactly as they are.

- [ ] **Step 7: Verify**

Relocate `Produto.java`/`ProdutoService.java` (and anything importing them). Run:
```bash
cd mesh-suite-backend && mvn -q test -Dtest=ColorwayServiceTest
```
Expected: `BUILD SUCCESS`, 8/8 tests pass. Restore the relocated files; verify `git status --short` is clean of anything unexpected.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/colorway/dto \
        mesh-suite-backend/src/main/java/com/meshsuite/colorway/exception \
        mesh-suite-backend/src/main/java/com/meshsuite/colorway/repository/specification \
        mesh-suite-backend/src/main/java/com/meshsuite/colorway/service \
        mesh-suite-backend/src/test/java/com/meshsuite/colorway/service/ColorwayServiceTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaRequest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaEmUsoException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaNaoEncontradaException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaNomeDuplicadoException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/repository/specification/CorEstampaSpecifications.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/service/CorEstampaService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/service/CorEstampaServiceTest.java
git commit -m "refactor(colorway): rename CorEstampa DTOs, exceptions, specifications, and service layer to English"
```

---

### Task 7: Colorway controller, exception handler, controller test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/controller/ColorwayController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/colorway/exception/ColorwayExceptionHandler.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/CorEstampaController.java`, `mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaExceptionHandler.java`, `mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CorEstampaControllerTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/colorway/controller/ColorwayControllerTest.java`

**Interfaces:**
- Consumes: `ColorwayService` and its DTOs (Task 6).
- Produces: `ColorwayController` at `/api/colorways` with routes `GET /api/colorways`, `GET /api/colorways/{id}`, `POST /api/colorways`, `PUT /api/colorways/{id}`, `DELETE /api/colorways/{id}`.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/CorEstampaController.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaExceptionHandler.java
git rm mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CorEstampaControllerTest.java
```

- [ ] **Step 2: Create `ColorwayController.java`**

```java
package com.meshsuite.colorway.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.colorway.dto.ColorwayRequest;
import com.meshsuite.colorway.dto.ColorwayResponse;
import com.meshsuite.colorway.service.ColorwayService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/colorways")
public class ColorwayController {

    private final ColorwayService colorwayService;

    public ColorwayController(ColorwayService colorwayService) {
        this.colorwayService = colorwayService;
    }

    @GetMapping
    public Page<ColorwayResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return colorwayService.list(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public ColorwayResponse findById(@PathVariable UUID id) {
        return colorwayService.findById(id);
    }

    @PostMapping
    public ResponseEntity<ColorwayResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                     @Valid @RequestBody ColorwayRequest request) {
        ColorwayResponse response = colorwayService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ColorwayResponse update(@PathVariable UUID id, @Valid @RequestBody ColorwayRequest request) {
        return colorwayService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        colorwayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 3: Create `ColorwayExceptionHandler.java`**

```java
package com.meshsuite.colorway.exception;

import com.meshsuite.colorway.controller.ColorwayController;
import com.meshsuite.colorway.service.ColorwayService;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ColorwayController.class)
public class ColorwayExceptionHandler {

    // Fallback for a race condition slipping past ColorwayService's pre-check
    // (two concurrent requests for the same new name) -- the DB's
    // UNIQUE(tenant_id, name) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma cor/estampa cadastrada com este nome"));
    }
}
```

- [ ] **Step 4: Create `ColorwayControllerTest.java`**

Read `git show HEAD:mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CorEstampaControllerTest.java` (7 test methods) and recreate it at `mesh-suite-backend/src/test/java/com/meshsuite/colorway/controller/ColorwayControllerTest.java`, applying:

- All type/field substitutions from Task 6's table.
- URL paths: `"/api/cores-estampas"` → `"/api/colorways"` (and matching sub-paths).
- Test method names: `createsListsUpdatesAndDeletesCorEstampa→createsListsUpdatesAndDeletesColorway`, `rejectsDuplicateNomeWithConflict→rejectsDuplicateNameWithConflict`, `rejectsMissingDataVigenciaWithBadRequest→rejectsMissingEffectiveDateWithBadRequest`, `rejectsDeletingACorEstampaInUseWithBadRequest→rejectsDeletingAColorwayInUseWithBadRequest`, `tenantACannotAccessTenantBsCorEstampa→tenantACannotAccessTenantBsColorway`, `unauthenticatedRequestIsRejected` (unchanged), `listingWithoutProductViewPermissionIsForbidden` (unchanged).
- JSON body field names: `nome→name`, `dataVigencia→effectiveDate`, `descricao→description`, `ativo→active`, `produtosVinculados→linkedProducts`, `criadoEm→createdAt`.

- [ ] **Step 5: Verify**

Relocate `Produto.java`/`ProdutoService.java` (and anything importing them). Run:
```bash
cd mesh-suite-backend && mvn -q test -Dtest=ColorwayControllerTest
```
Expected: `BUILD SUCCESS`, 7/7 tests pass. Restore the relocated files; verify `git status --short` is clean of anything unexpected.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/colorway/controller \
        mesh-suite-backend/src/main/java/com/meshsuite/colorway/exception/ColorwayExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/colorway/controller/ColorwayControllerTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/controller/CorEstampaController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/exception/CorEstampaExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CorEstampaControllerTest.java
git commit -m "refactor(colorway): rename CorEstampa controller and exception handler to English, route cores-estampas->colorways"
```

Note: after this task, `mesh-suite-backend/src/main/java/com/meshsuite/produto/` should have zero files with "Categoria" or "CorEstampa" in the name — verify with `find mesh-suite-backend/src/main/java/com/meshsuite/produto -iname "*categoria*" -o -iname "*corestampa*"` (expect no output).

---

### Task 8: Bridge — `Produto.java`, `ProdutoService.java`, `GlobalExceptionHandler.java`

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/Produto.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/service/ProdutoService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `Category`, `CategoryRepository`, `CategoryNotFoundException`, `DuplicateCategoryNameException`, `CategoryInUseException` (Task 3), `Colorway`, `ColorwayRepository`, `ColorwayNotFoundException`, `DuplicateColorwayNameException`, `ColorwayInUseException` (Task 6).

This is the FIRST task where the whole `mesh-suite-backend` module should `mvn test-compile` cleanly again — every other file that references `Categoria`/`CorEstampa` has now been renamed or deleted in Tasks 2-7.

**Gap found during execution**: this plan originally omitted `GlobalExceptionHandler.java` entirely (unlike the prior Parceiro→Partner plan, which had a dedicated task for it) — it has 6 `@ExceptionHandler` blocks with fully-qualified references to the now-deleted `Categoria*`/`CorEstampa*` exception classes, discovered when Task 8's own implementer found `mvn test-compile` still failing after fixing `Produto.java`/`ProdutoService.java`. Folded into this task rather than spun out separately, since it's a small, mechanical, single-file addition.

- [ ] **Step 0: Rename the 6 `@ExceptionHandler` blocks in `GlobalExceptionHandler.java`**

Rename (method name AND the fully-qualified exception type in both the `@ExceptionHandler` annotation and the parameter — keep the `"mensagem"` key and `e.getMessage()` body unchanged, and do NOT touch the `TabelaPreco*` handlers immediately after these, which belong to a separate future sub-project):

| Old method (catches) | New method (catches) | HTTP status |
|---|---|---|
| `handleCategoriaNaoEncontrada` (`com.meshsuite.produto.exception.CategoriaNaoEncontradaException`) | `handleCategoryNotFound` (`com.meshsuite.category.exception.CategoryNotFoundException`) | `NOT_FOUND` |
| `handleCategoriaNomeDuplicado` (`CategoriaNomeDuplicadoException`) | `handleDuplicateCategoryName` (`com.meshsuite.category.exception.DuplicateCategoryNameException`) | `CONFLICT` |
| `handleCategoriaEmUso` (`CategoriaEmUsoException`) | `handleCategoryInUse` (`com.meshsuite.category.exception.CategoryInUseException`) | `BAD_REQUEST` |
| `handleCorEstampaNaoEncontrada` (`CorEstampaNaoEncontradaException`) | `handleColorwayNotFound` (`com.meshsuite.colorway.exception.ColorwayNotFoundException`) | `NOT_FOUND` |
| `handleCorEstampaNomeDuplicado` (`CorEstampaNomeDuplicadoException`) | `handleDuplicateColorwayName` (`com.meshsuite.colorway.exception.DuplicateColorwayNameException`) | `CONFLICT` |
| `handleCorEstampaEmUso` (`CorEstampaEmUsoException`) | `handleColorwayInUse` (`com.meshsuite.colorway.exception.ColorwayInUseException`) | `BAD_REQUEST` |

- [ ] **Step 1: Add imports and change the two field types in `Produto.java`**

Change:
```java
package com.meshsuite.produto.domain;

import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.produto.domain.enums.StatusProduto;
import com.meshsuite.produto.domain.enums.UnidadeMedida;
import jakarta.persistence.*;
```
to:
```java
package com.meshsuite.produto.domain;

import com.meshsuite.category.domain.Category;
import com.meshsuite.colorway.domain.Colorway;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.produto.domain.enums.StatusProduto;
import com.meshsuite.produto.domain.enums.UnidadeMedida;
import jakarta.persistence.*;
```

Change:
```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cor_estampa_id")
    private CorEstampa corEstampa;
```
to:
```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Category categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cor_estampa_id")
    private Colorway corEstampa;
```

(field names `categoria`/`corEstampa` and the `@JoinColumn` names stay unchanged — `Produto`'s own naming is out of scope until sub-project 4b).

- [ ] **Step 2: Update `ProdutoService.java`'s imports, field types, and the two `.getNome()` accessor calls**

Change:
```java
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.domain.enums.StatusProduto;
import com.meshsuite.produto.domain.enums.UnidadeMedida;
import com.meshsuite.produto.dto.*;
import com.meshsuite.produto.exception.CategoriaNaoEncontradaException;
import com.meshsuite.produto.exception.CorEstampaNaoEncontradaException;
import com.meshsuite.produto.exception.ProdutoNaoEncontradoException;
import com.meshsuite.produto.exception.SkuDuplicadoException;
import com.meshsuite.produto.repository.CategoriaRepository;
import com.meshsuite.produto.repository.CorEstampaRepository;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.produto.repository.specification.ProdutoSpecifications;
```
to:
```java
import com.meshsuite.category.exception.CategoryNotFoundException;
import com.meshsuite.category.repository.CategoryRepository;
import com.meshsuite.colorway.exception.ColorwayNotFoundException;
import com.meshsuite.colorway.repository.ColorwayRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.domain.enums.StatusProduto;
import com.meshsuite.produto.domain.enums.UnidadeMedida;
import com.meshsuite.produto.dto.*;
import com.meshsuite.produto.exception.ProdutoNaoEncontradoException;
import com.meshsuite.produto.exception.SkuDuplicadoException;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.produto.repository.specification.ProdutoSpecifications;
```

Change:
```java
    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final CorEstampaRepository corEstampaRepository;

    public ProdutoService(ProdutoRepository produtoRepository, CategoriaRepository categoriaRepository,
                           CorEstampaRepository corEstampaRepository) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.corEstampaRepository = corEstampaRepository;
    }
```
to:
```java
    private final ProdutoRepository produtoRepository;
    private final CategoryRepository categoriaRepository;
    private final ColorwayRepository corEstampaRepository;

    public ProdutoService(ProdutoRepository produtoRepository, CategoryRepository categoriaRepository,
                           ColorwayRepository corEstampaRepository) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.corEstampaRepository = corEstampaRepository;
    }
```

(field names `categoriaRepository`/`corEstampaRepository` and the constructor parameter names stay unchanged — only their declared TYPES change).

Change:
```java
        produto.setCategoria(request.categoriaId() != null
                ? categoriaRepository.findById(request.categoriaId()).orElseThrow(CategoriaNaoEncontradaException::new)
                : null);
        produto.setCorEstampa(request.corEstampaId() != null
                ? corEstampaRepository.findById(request.corEstampaId()).orElseThrow(CorEstampaNaoEncontradaException::new)
                : null);
```
to:
```java
        produto.setCategoria(request.categoriaId() != null
                ? categoriaRepository.findById(request.categoriaId()).orElseThrow(CategoryNotFoundException::new)
                : null);
        produto.setCorEstampa(request.corEstampaId() != null
                ? corEstampaRepository.findById(request.corEstampaId()).orElseThrow(ColorwayNotFoundException::new)
                : null);
```

Change (the two accessor calls that MUST change — `Category`/`Colorway` no longer have a `getNome()` method, only `getName()`):
```java
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getNome() : null,
                p.getCorEstampa() != null ? p.getCorEstampa().getId() : null,
                p.getCorEstampa() != null ? p.getCorEstampa().getNome() : null,
```
to:
```java
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getName() : null,
                p.getCorEstampa() != null ? p.getCorEstampa().getId() : null,
                p.getCorEstampa() != null ? p.getCorEstampa().getName() : null,
```

- [ ] **Step 3: Verify the whole module compiles and tests pass**

Run:
```bash
cd mesh-suite-backend && mvn -q test-compile
```
Expected: `BUILD SUCCESS` — this is the first time since Task 1 that the whole module compiles without relocating any files.

Run:
```bash
grep -rn "com\.meshsuite\.produto\.domain\.Categoria\b\|com\.meshsuite\.produto\.domain\.CorEstampa\b\|com\.meshsuite\.produto\.repository\.CategoriaRepository\|com\.meshsuite\.produto\.repository\.CorEstampaRepository" mesh-suite-backend/src --include="*.java"
```
Expected: no output.

Run:
```bash
mvn -q test -Dtest='CategoryRepositoryTest,CategoryServiceTest,CategoryControllerTest,ColorwayRepositoryTest,ColorwayServiceTest,ColorwayControllerTest,ProdutoServiceTest,ProdutoControllerTest,ProdutoRepositoryTest'
```
Expected: `BUILD SUCCESS`, all tests pass — this confirms `ProdutoServiceTest`/`ProdutoControllerTest` (which were not renamed, but now compile against the bridged types) are still green. Paste the REAL command output when reporting — a prior task in this initiative was caught fabricating a "BUILD SUCCESS" claim that was actually a failure.

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/produto/domain/Produto.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/service/ProdutoService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java
git commit -m "refactor(produto): bridge-patch Produto/ProdutoService/GlobalExceptionHandler to consume the renamed Category/Colorway types"
```

---

### Task 9: Frontend `api/categorias.ts` → `categories.ts`, `api/coresEstampas.ts` → `colorways.ts`

**Files:**
- Create: `mesh-suite-frontend/src/api/categories.ts`
- Create: `mesh-suite-frontend/src/api/colorways.ts`
- Delete: `mesh-suite-frontend/src/api/categorias.ts`, `mesh-suite-frontend/src/api/coresEstampas.ts`
- Create/Modify their spec files if present under `mesh-suite-frontend/src/api/__tests__/` (check with `find mesh-suite-frontend/src/api/__tests__ -iname "*categoria*" -o -iname "*coresestampa*"` — if none exist, skip this file's steps; the design spec's audit found the dedicated specs live under `views/__tests__/`, not `api/__tests__/`, for this pair of modules)

**Interfaces:**
- Produces: types `CategoryRequest`, `CategoryResponse`, `ListCategoriesParams`, `Page<T>`; functions `listCategories`, `getCategory`, `createCategory`, `updateCategory`, `deleteCategory` — consumed by Task 10. Types `ColorwayRequest`, `ColorwayResponse`, `ListColorwaysParams`; functions `listColorways`, `getColorway`, `createColorway`, `updateColorway`, `deleteColorway` — consumed by Task 11.

- [ ] **Step 1: Confirm there are no dedicated api-layer spec files for these two, then delete the old files**

```bash
find mesh-suite-frontend/src/api/__tests__ -iname "*categoria*" -o -iname "*coresestampa*" -o -iname "*corestampa*"
```
If this returns files, read them and mirror them into `categories.spec.ts`/`colorways.spec.ts` following the same pattern as Task 8 of the Parceiro sub-project's plan (import path, function/type names, literal values). If it returns nothing (expected), proceed.

```bash
git rm mesh-suite-frontend/src/api/categorias.ts
git rm mesh-suite-frontend/src/api/coresEstampas.ts
```

- [ ] **Step 2: Create `categories.ts`**

```typescript
import { apiClient } from './client'

export interface CategoryRequest {
  name: string
  description: string | null
  active: boolean | null
}

export interface CategoryResponse extends CategoryRequest {
  id: string
  linkedProducts: number
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListCategoriesParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listCategories(params: ListCategoriesParams): Promise<Page<CategoryResponse>> {
  const { data } = await apiClient.get<Page<CategoryResponse>>('/categories', { params })
  return data
}

export async function getCategory(id: string): Promise<CategoryResponse> {
  const { data } = await apiClient.get<CategoryResponse>(`/categories/${id}`)
  return data
}

export async function createCategory(payload: CategoryRequest): Promise<CategoryResponse> {
  const { data } = await apiClient.post<CategoryResponse>('/categories', payload)
  return data
}

export async function updateCategory(id: string, payload: CategoryRequest): Promise<CategoryResponse> {
  const { data } = await apiClient.put<CategoryResponse>(`/categories/${id}`, payload)
  return data
}

export async function deleteCategory(id: string): Promise<void> {
  await apiClient.delete(`/categories/${id}`)
}
```

- [ ] **Step 3: Create `colorways.ts`**

```typescript
import { apiClient } from './client'

export interface ColorwayRequest {
  name: string
  effectiveDate: string
  description: string | null
  active: boolean | null
}

export interface ColorwayResponse extends ColorwayRequest {
  id: string
  linkedProducts: number
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListColorwaysParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listColorways(params: ListColorwaysParams): Promise<Page<ColorwayResponse>> {
  const { data } = await apiClient.get<Page<ColorwayResponse>>('/colorways', { params })
  return data
}

export async function getColorway(id: string): Promise<ColorwayResponse> {
  const { data } = await apiClient.get<ColorwayResponse>(`/colorways/${id}`)
  return data
}

export async function createColorway(payload: ColorwayRequest): Promise<ColorwayResponse> {
  const { data } = await apiClient.post<ColorwayResponse>('/colorways', payload)
  return data
}

export async function updateColorway(id: string, payload: ColorwayRequest): Promise<ColorwayResponse> {
  const { data } = await apiClient.put<ColorwayResponse>(`/colorways/${id}`, payload)
  return data
}

export async function deleteColorway(id: string): Promise<void> {
  await apiClient.delete(`/colorways/${id}`)
}
```

Note: `ListCategoriesParams`/`ListColorwaysParams`' own field names (`busca`, `ativo`) stay untranslated — they match the backend controllers' `@RequestParam` names from Tasks 4/7.

- [ ] **Step 4: Verify**

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`
Expected: errors will appear for `views/CategoriaFormView.vue`, `CategoriasListView.vue`, `CorEstampaFormView.vue`, `CoresEstampasListView.vue`, and `ProdutoFormView.vue` — they still import from the now-deleted `@/api/categorias`/`@/api/coresEstampas`. This is expected; Tasks 10-12 fix them. Confirm the errors are ONLY in those 5 files (no unrelated breakage).

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/api/categories.ts mesh-suite-frontend/src/api/colorways.ts \
        mesh-suite-frontend/src/api/categorias.ts mesh-suite-frontend/src/api/coresEstampas.ts
git commit -m "refactor(category-colorway): rename frontend api/categorias.ts and api/coresEstampas.ts to English"
```

---

### Task 10: Frontend `CategoryFormView.vue`, `CategoriesListView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/CategoryFormView.vue`, `mesh-suite-frontend/src/views/CategoriesListView.vue`
- Delete: `mesh-suite-frontend/src/views/CategoriaFormView.vue`, `mesh-suite-frontend/src/views/CategoriasListView.vue`
- Create/Delete their specs: `mesh-suite-frontend/src/views/__tests__/CategoryFormView.spec.ts` (from `CategoriaFormView.spec.ts`), `mesh-suite-frontend/src/views/__tests__/CategoriesListView.spec.ts` (from `CategoriasListView.spec.ts`)

**Interfaces:**
- Consumes: `categories.ts`'s exports (Task 9).

Visible Portuguese text (labels, page titles, button text, placeholders, status labels "Ativo"/"Inativo") is NOT touched anywhere in this task — only imports, types, and API-layer field/function names.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-frontend/src/views/CategoriaFormView.vue
git rm mesh-suite-frontend/src/views/CategoriasListView.vue
```

- [ ] **Step 2: Create `CategoryFormView.vue`**

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Categoria' : 'Nova Categoria'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <div>
          <label class="field-label">Nome *</label>
          <input v-model="form.name" data-test="nome" placeholder="Ex: Camisas" />
          <p v-if="erros.name" class="field-error">{{ erros.name }}</p>
        </div>
        <div>
          <label class="field-label">Descrição</label>
          <textarea v-model="form.description" data-test="descricao" rows="3" placeholder="Descrição opcional..."></textarea>
        </div>
        <div>
          <label class="field-label">Status</label>
          <div class="status-toggle">
            <button
              type="button"
              class="status-btn"
              :class="{ 'status-btn-active-ativo': form.active }"
              data-test="status-ativo"
              @click="form.active = true"
            >
              Ativo
            </button>
            <button
              type="button"
              class="status-btn"
              :class="{ 'status-btn-active-inativo': !form.active }"
              data-test="status-inativo"
              @click="form.active = false"
            >
              Inativo
            </button>
          </div>
        </div>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Categoria</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getCategory,
  createCategory,
  updateCategory,
  type CategoryRequest,
} from '@/api/categories'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): CategoryRequest {
  return { name: '', description: '', active: true }
}

const form = reactive<CategoryRequest>(novoFormulario())
const erros = reactive<{ name?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const category = await getCategory(id)
      form.name = category.name
      form.description = category.description
      form.active = category.active
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da categoria.'
    }
  }
})

function validar(): boolean {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
  return !erros.name
}

async function salvar() {
  erroGeral.value = ''
  if (!validar()) {
    return
  }
  salvando.value = true
  try {
    const id = route.params.id
    if (typeof id === 'string') {
      await updateCategory(id, form)
    } else {
      await createCategory(form)
    }
    router.push({ name: 'categorias' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma categoria cadastrada com este nome.'
    } else if (err?.response?.status === 403) {
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
  router.push({ name: 'categorias' })
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

.field-label {
  display: block;
  font-size: 12px;
  color: var(--pm-text-mid);
  margin-bottom: 4px;
}

input,
textarea {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  color: var(--pm-text-dark);
  font-size: 13px;
  font-family: var(--pm-font);
  margin-bottom: 10px;
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: -6px 0 10px;
}

.status-toggle {
  display: flex;
  gap: 8px;
}

.status-btn {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 999px;
  padding: 6px 16px;
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.status-btn-active-ativo {
  border-color: var(--pm-success);
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.status-btn-active-inativo {
  border-color: var(--pm-error);
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.error-geral {
  color: var(--pm-error);
  font-size: 14px;
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

- [ ] **Step 3: Create `CategoriesListView.vue`**

```vue
<template>
  <AppShell title="Categorias">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar categoria por nome..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.ativo" data-test="filtro-status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="true">Ativo</option>
        <option value="false">Inativo</option>
      </select>
      <button type="button" class="btn-primary" data-test="nova-categoria" @click="novaCategoria">+ Nova Categoria</button>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nome</th>
            <th>Descrição</th>
            <th>Produtos</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="category in pagina.content" :key="category.id">
            <td>{{ category.name }}</td>
            <td>{{ category.description }}</td>
            <td>{{ category.linkedProducts }} produtos</td>
            <td><span class="badge" :class="category.active ? 'badge-ATIVO' : 'badge-INATIVO'">{{ category.active ? 'Ativo' : 'Inativo' }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(category.id, $event)"
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
        v-if="categoriaAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarCategoria(categoriaAcoesAtual.id)">Editar</div>
        <div data-test="acao-excluir" class="acao-excluir" @click="excluir(categoriaAcoesAtual)">Excluir</div>
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
  listCategories,
  deleteCategory,
  type CategoryResponse,
  type Page as ApiPage,
} from '@/api/categories'

const router = useRouter()

const filtros = reactive({ busca: '', ativo: '' })
const pagina = ref<ApiPage<CategoryResponse>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const categoriaAcoesAtual = computed(() =>
  pagina.value.content.find((c) => c.id === acoesAbertas.value) ?? null,
)

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listCategories({
      busca: filtros.busca || undefined,
      ativo: filtros.ativo === '' ? undefined : filtros.ativo === 'true',
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de categorias.'
  }
}

function novaCategoria() {
  router.push({ name: 'categorias-novo' })
}

function editarCategoria(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'categorias-editar', params: { id } })
}

function toggleAcoes(id: string, event: MouseEvent) {
  if (acoesAbertas.value === id) {
    acoesAbertas.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  posicaoDropdown.value = {
    top: `${rect.bottom + 4}px`,
    left: `${rect.right - 120}px`,
  }
  acoesAbertas.value = id
}

async function excluir(category: CategoryResponse) {
  acoesAbertas.value = null
  if (!confirm(`Excluir a categoria "${category.name}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deleteCategory(category.id)
    await carregar(pagina.value.number)
  } catch (err: any) {
    erro.value = err?.response?.data?.mensagem ?? 'Não foi possível excluir a categoria.'
  }
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

.badge-ATIVO {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.badge-INATIVO {
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
  min-width: 120px;
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

Note: local variable/function names (`categoriaAcoesAtual`, `editarCategoria`, `novaCategoria`, `carregar`, `filtros`, `pagina`, `erro`, `acoesAbertas`) stay unchanged — only the imported API types/functions and the object PROPERTY accesses (`category.name`, `category.description`, `category.linkedProducts`, `category.active`, `category.id`) change, since those are the renamed wire-contract fields from Task 9. Visible text ("Categorias", "Nova Categoria", "Nome", "Descrição", "Produtos", "Status", "Ativo", "Inativo", "Ações", "Editar", "Excluir", "Página", error messages) is byte-for-byte unchanged.

- [ ] **Step 4: Create the two spec files**

Read `git show HEAD:mesh-suite-frontend/src/views/__tests__/CategoriaFormView.spec.ts` and `git show HEAD:mesh-suite-frontend/src/views/__tests__/CategoriasListView.spec.ts` (before deleting them) and recreate them at `CategoryFormView.spec.ts`/`CategoriesListView.spec.ts`, applying: the import path (`'../CategoriaFormView.vue'→'../CategoryFormView.vue'`, same for the list view), the mocked module path (`vi.mock('@/api/categorias', ...)→vi.mock('@/api/categories', ...)`), every mocked/asserted function and type name per Task 9's map, and every literal fixture field name (`nome→name`, `descricao→description`, `ativo→active`, `produtosVinculados→linkedProducts`, `criadoEm→createdAt`). Assertions on VISIBLE rendered text do not change.

```bash
git rm mesh-suite-frontend/src/views/__tests__/CategoriaFormView.spec.ts
git rm mesh-suite-frontend/src/views/__tests__/CategoriasListView.spec.ts
```

- [ ] **Step 5: Verify**

Run: `cd mesh-suite-frontend && npx vitest run CategoryFormView CategoriesListView`
Expected: both spec files pass, same test count as the originals.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/views/CategoryFormView.vue mesh-suite-frontend/src/views/CategoriesListView.vue \
        mesh-suite-frontend/src/views/__tests__/CategoryFormView.spec.ts mesh-suite-frontend/src/views/__tests__/CategoriesListView.spec.ts \
        mesh-suite-frontend/src/views/CategoriaFormView.vue mesh-suite-frontend/src/views/CategoriasListView.vue \
        mesh-suite-frontend/src/views/__tests__/CategoriaFormView.spec.ts mesh-suite-frontend/src/views/__tests__/CategoriasListView.spec.ts
git commit -m "refactor(category): rename CategoriaFormView/CategoriasListView to CategoryFormView/CategoriesListView"
```

---

### Task 11: Frontend `ColorwayFormView.vue`, `ColorwaysListView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/ColorwayFormView.vue`, `mesh-suite-frontend/src/views/ColorwaysListView.vue`
- Delete: `mesh-suite-frontend/src/views/CorEstampaFormView.vue`, `mesh-suite-frontend/src/views/CoresEstampasListView.vue`
- Create/Delete their specs: `mesh-suite-frontend/src/views/__tests__/ColorwayFormView.spec.ts` (from `CorEstampaFormView.spec.ts`), `mesh-suite-frontend/src/views/__tests__/ColorwaysListView.spec.ts` (from `CoresEstampasListView.spec.ts`)

**Interfaces:**
- Consumes: `colorways.ts`'s exports (Task 9).

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-frontend/src/views/CorEstampaFormView.vue
git rm mesh-suite-frontend/src/views/CoresEstampasListView.vue
```

- [ ] **Step 2: Create `ColorwayFormView.vue`**

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Cor / Estampa' : 'Nova Cor / Estampa'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Cor / Estampa *</label>
            <input v-model="form.name" data-test="nome" placeholder="Ex: Azul Marinho, Floral Primavera" />
            <p v-if="erros.name" class="field-error">{{ erros.name }}</p>
          </div>
          <div>
            <label class="field-label">Data de Vigência *</label>
            <input v-model="form.effectiveDate" type="date" data-test="data-vigencia" />
            <p v-if="erros.effectiveDate" class="field-error">{{ erros.effectiveDate }}</p>
          </div>
        </div>
        <div>
          <label class="field-label">Descrição</label>
          <textarea v-model="form.description" data-test="descricao" rows="3" placeholder="Descrição opcional..."></textarea>
        </div>
        <div>
          <label class="field-label">Status</label>
          <div class="status-toggle">
            <button
              type="button"
              class="status-btn"
              :class="{ 'status-btn-active-ativo': form.active }"
              data-test="status-ativo"
              @click="form.active = true"
            >
              Ativo
            </button>
            <button
              type="button"
              class="status-btn"
              :class="{ 'status-btn-active-inativo': !form.active }"
              data-test="status-inativo"
              @click="form.active = false"
            >
              Inativo
            </button>
          </div>
        </div>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Cor / Estampa</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getColorway,
  createColorway,
  updateColorway,
  type ColorwayRequest,
} from '@/api/colorways'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): ColorwayRequest {
  return { name: '', effectiveDate: '', description: '', active: true }
}

const form = reactive<ColorwayRequest>(novoFormulario())
const erros = reactive<{ name?: string; effectiveDate?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const colorway = await getColorway(id)
      form.name = colorway.name
      form.effectiveDate = colorway.effectiveDate
      form.description = colorway.description
      form.active = colorway.active
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da cor/estampa.'
    }
  }
})

function validar(): boolean {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
  erros.effectiveDate = form.effectiveDate ? undefined : 'Campo obrigatório'
  return !erros.name && !erros.effectiveDate
}

async function salvar() {
  erroGeral.value = ''
  if (!validar()) {
    return
  }
  salvando.value = true
  try {
    const id = route.params.id
    if (typeof id === 'string') {
      await updateColorway(id, form)
    } else {
      await createColorway(form)
    }
    router.push({ name: 'cores-estampas' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma cor/estampa cadastrada com este nome.'
    } else if (err?.response?.status === 403) {
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
  router.push({ name: 'cores-estampas' })
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
textarea {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  color: var(--pm-text-dark);
  font-size: 13px;
  font-family: var(--pm-font);
  margin-bottom: 10px;
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: -6px 0 10px;
}

.status-toggle {
  display: flex;
  gap: 8px;
}

.status-btn {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 999px;
  padding: 6px 16px;
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.status-btn-active-ativo {
  border-color: var(--pm-success);
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.status-btn-active-inativo {
  border-color: var(--pm-error);
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.error-geral {
  color: var(--pm-error);
  font-size: 14px;
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

- [ ] **Step 3: Create `ColorwaysListView.vue`**

```vue
<template>
  <AppShell title="Cores / Estampas">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar cor ou estampa por nome..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.ativo" data-test="filtro-status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="true">Ativo</option>
        <option value="false">Inativo</option>
      </select>
      <button type="button" class="btn-primary" data-test="nova-cor-estampa" @click="novaCorEstampa">+ Nova Cor / Estampa</button>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nome</th>
            <th>Vigência</th>
            <th>Produtos</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="colorway in pagina.content" :key="colorway.id">
            <td>{{ colorway.name }}</td>
            <td>{{ formatarData(colorway.effectiveDate) }}</td>
            <td>{{ colorway.linkedProducts }} produtos</td>
            <td><span class="badge" :class="colorway.active ? 'badge-ATIVO' : 'badge-INATIVO'">{{ colorway.active ? 'Ativo' : 'Inativo' }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(colorway.id, $event)"
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
        v-if="corEstampaAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarCorEstampa(corEstampaAcoesAtual.id)">Editar</div>
        <div data-test="acao-excluir" class="acao-excluir" @click="excluir(corEstampaAcoesAtual)">Excluir</div>
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
  listColorways,
  deleteColorway,
  type ColorwayResponse,
  type Page as ApiPage,
} from '@/api/colorways'

const router = useRouter()

const filtros = reactive({ busca: '', ativo: '' })
const pagina = ref<ApiPage<ColorwayResponse>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const corEstampaAcoesAtual = computed(() =>
  pagina.value.content.find((c) => c.id === acoesAbertas.value) ?? null,
)

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listColorways({
      busca: filtros.busca || undefined,
      ativo: filtros.ativo === '' ? undefined : filtros.ativo === 'true',
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de cores/estampas.'
  }
}

function novaCorEstampa() {
  router.push({ name: 'cores-estampas-novo' })
}

function editarCorEstampa(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'cores-estampas-editar', params: { id } })
}

function toggleAcoes(id: string, event: MouseEvent) {
  if (acoesAbertas.value === id) {
    acoesAbertas.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  posicaoDropdown.value = {
    top: `${rect.bottom + 4}px`,
    left: `${rect.right - 120}px`,
  }
  acoesAbertas.value = id
}

async function excluir(colorway: ColorwayResponse) {
  acoesAbertas.value = null
  if (!confirm(`Excluir a cor/estampa "${colorway.name}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deleteColorway(colorway.id)
    await carregar(pagina.value.number)
  } catch (err: any) {
    erro.value = err?.response?.data?.mensagem ?? 'Não foi possível excluir a cor/estampa.'
  }
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

.badge-ATIVO {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.badge-INATIVO {
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
  min-width: 120px;
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

Note: local variable/function names (`corEstampaAcoesAtual`, `editarCorEstampa`, `novaCorEstampa`, `formatarData`, `filtros`, `pagina`, `erro`) stay unchanged — only imported API types/functions and object property accesses (`colorway.name`, `colorway.effectiveDate`, `colorway.linkedProducts`, `colorway.active`, `colorway.id`) change. Visible text unchanged.

- [ ] **Step 4: Create the two spec files**

Read `git show HEAD:mesh-suite-frontend/src/views/__tests__/CorEstampaFormView.spec.ts` and `git show HEAD:mesh-suite-frontend/src/views/__tests__/CoresEstampasListView.spec.ts` and recreate them at `ColorwayFormView.spec.ts`/`ColorwaysListView.spec.ts`, applying the same pattern as Task 10 Step 4: import paths, mocked module path (`@/api/coresEstampas→@/api/colorways`), function/type names, and literal fixture field names (`nome→name`, `dataVigencia→effectiveDate`, `descricao→description`, `ativo→active`, `produtosVinculados→linkedProducts`, `criadoEm→createdAt`).

```bash
git rm mesh-suite-frontend/src/views/__tests__/CorEstampaFormView.spec.ts
git rm mesh-suite-frontend/src/views/__tests__/CoresEstampasListView.spec.ts
```

- [ ] **Step 5: Verify**

Run: `cd mesh-suite-frontend && npx vitest run ColorwayFormView ColorwaysListView`
Expected: both spec files pass, same test count as the originals.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/views/ColorwayFormView.vue mesh-suite-frontend/src/views/ColorwaysListView.vue \
        mesh-suite-frontend/src/views/__tests__/ColorwayFormView.spec.ts mesh-suite-frontend/src/views/__tests__/ColorwaysListView.spec.ts \
        mesh-suite-frontend/src/views/CorEstampaFormView.vue mesh-suite-frontend/src/views/CoresEstampasListView.vue \
        mesh-suite-frontend/src/views/__tests__/CorEstampaFormView.spec.ts mesh-suite-frontend/src/views/__tests__/CoresEstampasListView.spec.ts
git commit -m "refactor(colorway): rename CorEstampaFormView/CoresEstampasListView to ColorwayFormView/ColorwaysListView"
```

---

### Task 12: Frontend bridge — `router/index.ts`, `ProdutoFormView.vue`

**Files:**
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/views/ProdutoFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts` (if it mocks `@/api/categorias`/`@/api/coresEstampas`)

**Interfaces:**
- Consumes: `categories.ts`, `colorways.ts` (Task 9).

- [ ] **Step 1: Update `router/index.ts`'s component imports**

Change the import lines (find them with `grep -n "CategoriaFormView\|CategoriasListView\|CorEstampaFormView\|CoresEstampasListView" mesh-suite-frontend/src/router/index.ts`) from importing `CategoriaFormView`, `CategoriasListView`, `CorEstampaFormView`, `CoresEstampasListView` to importing `CategoryFormView`, `CategoriesListView`, `ColorwayFormView`, `ColorwaysListView` (matching whatever import style — named or default — the file already uses for its other view imports), and update the 6 route entries' `component:` values to reference the new names:
```typescript
{ path: '/categorias', name: 'categorias', component: CategoriesListView },
{ path: '/categorias/novo', name: 'categorias-novo', component: CategoryFormView },
{ path: '/categorias/:id/editar', name: 'categorias-editar', component: CategoryFormView },
{ path: '/cores-estampas', name: 'cores-estampas', component: ColorwaysListView },
{ path: '/cores-estampas/novo', name: 'cores-estampas-novo', component: ColorwayFormView },
{ path: '/cores-estampas/:id/editar', name: 'cores-estampas-editar', component: ColorwayFormView },
```
The `path`/`name` values (`/categorias`, `categorias`, `/cores-estampas`, `cores-estampas`, etc.) are visible-URL/route-name strings — do NOT change them.

- [ ] **Step 2: Update `ProdutoFormView.vue`'s imports and Category/Colorway field access**

Change:
```typescript
import { listarCategorias, type CategoriaResponse } from '@/api/categorias'
import { listarCoresEstampas, type CorEstampaResponse } from '@/api/coresEstampas'
```
to:
```typescript
import { listCategories, type CategoryResponse } from '@/api/categories'
import { listColorways, type ColorwayResponse } from '@/api/colorways'
```

Update the two `ref<...>` declarations and their usages:
```typescript
const categorias = ref<CategoriaResponse[]>([])
const coresEstampas = ref<CorEstampaResponse[]>([])
```
to:
```typescript
const categorias = ref<CategoryResponse[]>([])
const coresEstampas = ref<ColorwayResponse[]>([])
```
(local variable names `categorias`/`coresEstampas` stay unchanged — only the generic type parameter changes).

Update the two data-fetch calls:
```typescript
const pagina = await listarCategorias({ ativo: true, size: 100 })
```
to:
```typescript
const pagina = await listCategories({ ativo: true, size: 100 })
```
and the equivalent `listarCoresEstampas({ ativo: true, size: 100 })` → `listColorways({ ativo: true, size: 100 })`.

Update the two `as CategoriaResponse`/`as CorEstampaResponse` type assertions (in the "splice a synthetic entry for an inactive category not in the loaded list" blocks) to `as CategoryResponse`/`as ColorwayResponse`.

Update the two property-access points where a loop variable's `.nome` is read (this is the ONLY functional change beyond types/imports — `categoria.nome`/`corEstampa.nome` reads a field on the renamed `CategoryResponse`/`ColorwayResponse` objects, which no longer has a `nome` property):
```html
<option v-for="categoria in categorias" :key="categoria.id" :value="categoria.id">
  {{ categoria.nome }}
```
to:
```html
<option v-for="categoria in categorias" :key="categoria.id" :value="categoria.id">
  {{ categoria.name }}
```
and the equivalent `{{ corEstampa.nome }}` → `{{ corEstampa.name }}`. Also update the two places building a synthetic fallback object, which set `nome: produto.categoriaNome ?? ''` / `nome: produto.corEstampaNome ?? ''` — the OBJECT KEY (`nome:`) must become `name:` to match `CategoryResponse`/`ColorwayResponse`'s actual shape, but `produto.categoriaNome`/`produto.corEstampaNome` (fields read from `Produto`'s own, not-yet-renamed DTO) stay exactly as they are.

All other identifiers in this file (`categorias`, `coresEstampas`, `categoria`, `corEstampa` as loop variables, `form.categoriaId`, `form.corEstampaId`, the visible label text "Categoria"/"Cor / Estampa"/"Sem categoria") stay unchanged.

- [ ] **Step 3: Check and update `ProdutoFormView.spec.ts` if needed**

```bash
grep -n "categorias\|coresEstampas\|listarCategorias\|listarCoresEstampas" mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts
```
If this file mocks `@/api/categorias`/`@/api/coresEstampas` or asserts against `listarCategorias`/`listarCoresEstampas`/`CategoriaResponse`-shaped fixture objects (e.g. `{ nome: '...' }`), update the mock path, function names, and fixture field names (`nome→name`) to match. Visible-text assertions do not change.

- [ ] **Step 4: Verify**

Run: `cd mesh-suite-frontend && npx vitest run` (full suite — should now be fully green, same file/test count as before this whole sub-project started, since Tasks 9-12 have touched every remaining consumer).
Expected: all pass.

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`
(Always use the `-p tsconfig.app.json` flag — plain `npx vue-tsc --noEmit` silently reports 0 errors regardless of real breakage in this project.)
Expected: 0 errors.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/router/index.ts mesh-suite-frontend/src/views/ProdutoFormView.vue \
        mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts
git commit -m "refactor(category-colorway): update router and ProdutoFormView to consume the renamed categories/colorways APIs"
```

---

### Task 13: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Confirm no `categoria`/`Categoria`/`corEstampa`/`CorEstampa` code traces remain, Portuguese business data and UI text excepted**

Run:
```bash
grep -ril "com\.meshsuite\.produto\.domain\.Categoria\b\|com\.meshsuite\.produto\.domain\.CorEstampa\b\|CategoriaRepository\|CorEstampaRepository\|CategoriaService\|CorEstampaService\|CategoriaController\|CorEstampaController" mesh-suite-backend/src --include="*.java"
```
Expected: no output.

Run:
```bash
grep -rl "categoria\|cor_estampa" mesh-suite-backend/src/main/resources --include="*.sql"
```
Expected: no output beyond the `V22`/`V24` FK-fix lines already reviewed in Task 1, and the `produto`-owned column name `categoria`/`categoria_id`/`cor_estampa_id` in `V6__create_produto.sql`/`V22`/`V24` — spot-check that any match is one of these known, correctly-untouched `produto`-column references, not a leftover reference to the old `categoria`/`cor_estampa` tables.

Run:
```bash
grep -ril "categorias\.ts\|coresEstampas\.ts\|CategoriaResponse\|CorEstampaResponse\|CategoriaRequest\|CorEstampaRequest" mesh-suite-frontend/src --include="*.ts" --include="*.vue"
```
Expected: no output.

Run (the dangling-property-string-literal sweep — the lesson from the Parceiro sub-project):
```bash
grep -rn '"nome"\|"ativo"\|"dataVigencia"' mesh-suite-backend/src/main/java/com/meshsuite/produto --include="*.java"
```
Review each hit: any Criteria API `.get("nome")`/`.get("ativo")` navigating through `Produto.categoria`/`Produto.corEstampa` (e.g. `root.get("categoria").get("nome")`) would be a missed bridge fix. Expected: no such hit — `ProdutoSpecifications.java`'s own `.get("nome")` calls (if any) operate directly on `Produto`'s own `nome` field, which is untouched and correctly out of scope.

- [ ] **Step 2: Confirm exactly one V21 and one V23 migration file**

Run: `ls mesh-suite-backend/src/main/resources/db/migration/ | grep -E "V21__|V23__"`
Expected: exactly two lines, `V21__create_category.sql` and `V23__create_colorway.sql`.

- [ ] **Step 3: Run the full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`
Expected: `BUILD SUCCESS` except the pre-existing, unrelated test-isolation flake — 0 failures, 15 errors, 12 in `com.meshsuite.payable.*` and 3 in `CompanyRepositoryTest` (see Global Constraints). If the error count, module, or class names differ from this signature, stop and investigate — it likely means a rename in this plan is incomplete somewhere the earlier greps didn't catch.

- [ ] **Step 4: Run the full frontend suite and type-check**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all tests pass, same file/test count as before this sub-project.

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`
Expected: no errors.

- [ ] **Step 5: Commit (only if Steps 1-4 required fixes) or confirm nothing to commit**

```bash
git status --short
```
If clean, no commit needed — this task is verification-only. If any fixes were required, commit them with a message describing exactly what was missed.
