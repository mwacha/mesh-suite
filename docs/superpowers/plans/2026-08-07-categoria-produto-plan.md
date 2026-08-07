# Categoria de Produto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Categoria cadastro (documented in PRD-13 as "Grupo de Produto") and convert `Produto.categoria` from free text to a real reference, completing the Produto cadastro.

**Architecture:** `Categoria` is a standalone RLS-scoped entity in the existing `com.meshsuite.produto` package (Portuguese naming, matching Produto/Cliente/Parceiro — not the English convention used by the newer Compras domains). `Produto.categoria` changes from a `String` column to a `@ManyToOne` reference. Backend follows the exact CRUD/permission/exception patterns already established by `Produto` itself. Frontend follows the exact list/form patterns of `ProdutosListView.vue`/`ProdutoFormView.vue`.

**Tech Stack:** Spring Boot 3.4.5 / Java 21, Spring Data JPA, PostgreSQL 16 (RLS), Flyway, Vue 3 + TypeScript + Vite.

## Global Constraints

- New backend code lives in `com.meshsuite.produto` package, in **Portuguese** (`Categoria`, `CategoriaService`, `CategoriaController` — not English), matching the existing convention of that package.
- RLS pattern: `categoria` table gets its own `tenant_id` column, `ENABLE`+`FORCE ROW LEVEL SECURITY`, `USING`-only policy (never `WITH CHECK`) — same as `produto`/`purchase_order`/`fiscal_registration`.
- Permission: reuse `Module.PRODUCT` (already exists, already in the frontend permission matrix) — do NOT add a new `Module` enum value or migration widening the `user_permission_module_check` constraint. Permission checks live in the Service layer via `@RequiresPermission`, never in the Controller (matches `ProdutoService`/`ProdutoController` split).
- `nome` is unique per tenant — enforced by a DB unique index `(tenant_id, nome)` AND a pre-check in the service (same double-layer pattern as `Produto.sku`).
- Deleting a `Categoria` referenced by any `Produto` is rejected with a clear error message naming the count — never a silent cascade, never a hard delete that orphans a reference.
- No data migration needed for the `Produto.categoria` column swap — confirmed no test/seed data populates it today, so the old `VARCHAR` column is dropped and replaced directly, no backfill logic required.
- Exception handling: domain-specific exceptions (`CategoriaNaoEncontradaException`, `CategoriaNomeDuplicadoException`, `CategoriaEmUsoException`) are registered in the existing shared `com.meshsuite.auth.GlobalExceptionHandler` (NOT a new per-domain `@RestControllerAdvice` file) — this is the actual established pattern already used for `ProdutoNaoEncontradoException`/`SkuDuplicadoException`/`ParceiroNaoEncontradoException` etc., all listed in that one file. A small `CategoriaExceptionHandler` scoped to `CategoriaController` is still created, but only for the `DataIntegrityViolationException` race-condition fallback (mirrors `ProdutoExceptionHandler`/`ParceiroExceptionHandler`, which handle only that one case).
- New backend integration tests use tenant `codigo` values distinct from `"aurora"`/`"boreal"` (e.g. `"aurora-cat"`) — deliberate mitigation of a known, pre-existing, order-dependent test-infra bug (`DevSeedTest` permanently seeding a `codigo='aurora'` tenant into the shared Testcontainers database), documented during the Financeiro Mínimo sub-project. Not a retroactive change to existing tests.

---

### Task 1: `Categoria` domain model, service, and `Produto` integration (backend)

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V19__create_categoria.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V20__replace_produto_categoria_with_fk.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/Categoria.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaNaoEncontradaException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaNomeDuplicadoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaEmUsoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/Produto.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoRepository.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoRequest.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoResponse.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoServiceTest.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/CategoriaRepositoryTest.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/CategoriaServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks — this is the first task.
- Produces: `Categoria` entity (`id, tenantId, nome, descricao, ativo, criadoEm`); `CategoriaRepository extends JpaRepository<Categoria, UUID>, JpaSpecificationExecutor<Categoria>` with `boolean existsByNome(String)`, `boolean existsByNomeAndIdNot(String, UUID)`; `CategoriaService` with `listar(String busca, Boolean ativo, Pageable): Page<CategoriaResponse>`, `buscarPorId(UUID): CategoriaResponse`, `criar(UUID tenantId, CategoriaRequest): CategoriaResponse`, `atualizar(UUID id, CategoriaRequest): CategoriaResponse`, `excluir(UUID id): void`; `CategoriaRequest(String nome, String descricao, Boolean ativo)`; `CategoriaResponse(UUID id, String nome, String descricao, Boolean ativo, Long produtosVinculados, Instant criadoEm)`. `Produto.categoria` is now `Categoria` (was `String`). `ProdutoRequest.categoriaId: UUID` (was `categoria: String`). `ProdutoResponse.categoriaId: UUID, categoriaNome: String` (was `categoria: String`). Task 2 (controller) consumes `CategoriaService` directly. Task 4 (Produto frontend) consumes the new `ProdutoRequest`/`ProdutoResponse` shape.

- [ ] **Step 1: Write the migration creating `categoria`**

```sql
CREATE TABLE categoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_categoria_tenant_nome ON categoria(tenant_id, nome);
CREATE INDEX idx_categoria_tenant_id ON categoria(tenant_id);

ALTER TABLE categoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE categoria FORCE ROW LEVEL SECURITY;

CREATE POLICY categoria_tenant_isolation ON categoria
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

Save as `mesh-suite-backend/src/main/resources/db/migration/V19__create_categoria.sql`.

- [ ] **Step 2: Write the migration swapping `produto.categoria`**

```sql
ALTER TABLE produto DROP COLUMN categoria;
ALTER TABLE produto ADD COLUMN categoria_id UUID REFERENCES categoria(id);
```

Save as `mesh-suite-backend/src/main/resources/db/migration/V20__replace_produto_categoria_with_fk.sql`.

- [ ] **Step 3: Write `Categoria.java`**

```java
package com.meshsuite.produto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categoria")
@Getter
@Setter
public class Categoria {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 255)
    private String descricao;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
```

- [ ] **Step 4: Write `CategoriaRepository.java`**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID>, JpaSpecificationExecutor<Categoria> {
    boolean existsByNome(String nome);
    boolean existsByNomeAndIdNot(String nome, UUID id);
}
```

- [ ] **Step 5: Write `CategoriaSpecifications.java`**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.domain.Specification;

public final class CategoriaSpecifications {

    private CategoriaSpecifications() {
    }

    public static Specification<Categoria> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nome")), termo);
    }

    public static Specification<Categoria> comAtivo(Boolean ativo) {
        if (ativo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
    }
}
```

- [ ] **Step 6: Write the three exception classes**

```java
package com.meshsuite.produto;

public class CategoriaNaoEncontradaException extends RuntimeException {
    public CategoriaNaoEncontradaException() {
        super("Categoria não encontrada");
    }
}
```

Save as `CategoriaNaoEncontradaException.java`.

```java
package com.meshsuite.produto;

public class CategoriaNomeDuplicadoException extends RuntimeException {
    public CategoriaNomeDuplicadoException() {
        super("Já existe uma categoria cadastrada com este nome");
    }
}
```

Save as `CategoriaNomeDuplicadoException.java`.

```java
package com.meshsuite.produto;

public class CategoriaEmUsoException extends RuntimeException {
    public CategoriaEmUsoException(long quantidadeProdutos) {
        super("Não é possível excluir: " + quantidadeProdutos + " produto(s) usam esta categoria");
    }
}
```

Save as `CategoriaEmUsoException.java`.

- [ ] **Step 7: Write the DTOs**

```java
package com.meshsuite.produto.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoriaRequest(
        @NotBlank String nome,
        String descricao,
        Boolean ativo) {
}
```

Save as `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaRequest.java`.

```java
package com.meshsuite.produto.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoriaResponse(
        UUID id,
        String nome,
        String descricao,
        Boolean ativo,
        Long produtosVinculados,
        Instant criadoEm) {
}
```

Save as `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaResponse.java`.

- [ ] **Step 8: Modify `Produto.java` — replace the `categoria` field**

Find this field (currently a plain `String`):

```java
    @Column(length = 100)
    private String categoria;
```

Replace it with:

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;
```

`jakarta.persistence.*` is already imported via wildcard in this file, so `ManyToOne`/`JoinColumn`/`FetchType` need no new imports. `Categoria` is in the same package (`com.meshsuite.produto`), so it needs no import either.

- [ ] **Step 9: Modify `ProdutoRepository.java` — add the in-use check**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<Produto, UUID>, JpaSpecificationExecutor<Produto> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    long countByStatus(StatusProduto status);
    long countByCategoriaId(UUID categoriaId);
}
```

- [ ] **Step 10: Modify `ProdutoRequest.java` — replace `categoria` with `categoriaId`**

```java
package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import com.meshsuite.produto.UnidadeMedida;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoRequest(
        @NotBlank String nome,
        @NotBlank String sku,
        String codigoBarras,
        String marca,
        UUID categoriaId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal precoVenda,
        BigDecimal precoCusto,
        StatusProduto status,
        String descricao,
        BigDecimal quantidadeEstoque,
        UnidadeMedida unidadeMedida,
        BigDecimal estoqueMinimo,
        BigDecimal estoqueMaximo,
        BigDecimal peso,
        BigDecimal comprimento,
        BigDecimal largura,
        BigDecimal altura) {
}
```

- [ ] **Step 11: Modify `ProdutoResponse.java` — replace `categoria` with `categoriaId`/`categoriaNome`**

```java
package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import com.meshsuite.produto.UnidadeMedida;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoResponse(
        UUID id,
        String nome,
        String sku,
        String codigoBarras,
        String marca,
        UUID categoriaId,
        String categoriaNome,
        BigDecimal precoVenda,
        BigDecimal precoCusto,
        StatusProduto status,
        String descricao,
        BigDecimal quantidadeEstoque,
        UnidadeMedida unidadeMedida,
        BigDecimal estoqueMinimo,
        BigDecimal estoqueMaximo,
        BigDecimal peso,
        BigDecimal comprimento,
        BigDecimal largura,
        BigDecimal altura) {
}
```

- [ ] **Step 12: Modify `ProdutoService.java` — wire in `CategoriaRepository`**

Add the import and constructor dependency. Change:

```java
    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }
```

to:

```java
    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

    public ProdutoService(ProdutoRepository produtoRepository, CategoriaRepository categoriaRepository) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
    }
```

Change the `aplicar` method's categoria line. Find:

```java
        produto.setCategoria(request.categoria());
```

Replace with:

```java
        produto.setCategoria(request.categoriaId() != null
                ? categoriaRepository.findById(request.categoriaId()).orElseThrow(CategoriaNaoEncontradaException::new)
                : null);
```

Change `toResponse`. Find:

```java
    private ProdutoResponse toResponse(Produto p) {
        return new ProdutoResponse(
                p.getId(), p.getNome(), p.getSku(), p.getCodigoBarras(), p.getMarca(), p.getCategoria(),
                p.getPrecoVenda(), p.getPrecoCusto(), p.getStatus(), p.getDescricao(), p.getQuantidadeEstoque(),
                p.getUnidadeMedida(), p.getEstoqueMinimo(), p.getEstoqueMaximo(), p.getPeso(), p.getComprimento(),
                p.getLargura(), p.getAltura());
    }
```

Replace with:

```java
    private ProdutoResponse toResponse(Produto p) {
        return new ProdutoResponse(
                p.getId(), p.getNome(), p.getSku(), p.getCodigoBarras(), p.getMarca(),
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getNome() : null,
                p.getPrecoVenda(), p.getPrecoCusto(), p.getStatus(), p.getDescricao(), p.getQuantidadeEstoque(),
                p.getUnidadeMedida(), p.getEstoqueMinimo(), p.getEstoqueMaximo(), p.getPeso(), p.getComprimento(),
                p.getLargura(), p.getAltura());
    }
```

- [ ] **Step 13: Fix the one existing call site broken by `ProdutoRequest`'s field change**

In `mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoServiceTest.java`, find:

```java
    private ProdutoRequest request(String sku, BigDecimal precoVenda) {
        return new ProdutoRequest(
                "Camiseta Polo Masculina", sku, "7891234567890", "Marca Alpha", "Vestuário",
                precoVenda, new BigDecimal("25.00"), StatusProduto.ATIVO, "Descrição de teste",
                new BigDecimal("10"), UnidadeMedida.UN, new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("0.300"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"));
    }
```

Replace `"Vestuário"` (a `String` in the old `categoria` position) with `null` (the new `categoriaId: UUID` position — this test doesn't exercise categoria, so `null` is correct):

```java
    private ProdutoRequest request(String sku, BigDecimal precoVenda) {
        return new ProdutoRequest(
                "Camiseta Polo Masculina", sku, "7891234567890", "Marca Alpha", null,
                precoVenda, new BigDecimal("25.00"), StatusProduto.ATIVO, "Descrição de teste",
                new BigDecimal("10"), UnidadeMedida.UN, new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("0.300"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"));
    }
```

- [ ] **Step 14: Write `CategoriaService.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.produto.dto.CategoriaRequest;
import com.meshsuite.produto.dto.CategoriaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProdutoRepository produtoRepository;

    public CategoriaService(CategoriaRepository categoriaRepository, ProdutoRepository produtoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<CategoriaResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        Specification<Categoria> spec = Specification.allOf(
                CategoriaSpecifications.comBusca(busca),
                CategoriaSpecifications.comAtivo(ativo));
        return categoriaRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public CategoriaResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public CategoriaResponse criar(UUID tenantId, CategoriaRequest request) {
        validarNome(request.nome(), null);

        Categoria categoria = new Categoria();
        categoria.setTenantId(tenantId);
        aplicar(categoria, request);
        return toResponse(categoriaRepository.saveAndFlush(categoria));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public CategoriaResponse atualizar(UUID id, CategoriaRequest request) {
        validarNome(request.nome(), id);

        Categoria categoria = buscarEntidadePorId(id);
        aplicar(categoria, request);
        return toResponse(categoriaRepository.saveAndFlush(categoria));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        Categoria categoria = buscarEntidadePorId(id);
        long vinculados = produtoRepository.countByCategoriaId(id);
        if (vinculados > 0) {
            throw new CategoriaEmUsoException(vinculados);
        }
        categoriaRepository.delete(categoria);
    }

    private Categoria buscarEntidadePorId(UUID id) {
        return categoriaRepository.findById(id).orElseThrow(CategoriaNaoEncontradaException::new);
    }

    private void validarNome(String nome, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? categoriaRepository.existsByNome(nome)
                : categoriaRepository.existsByNomeAndIdNot(nome, idAtual);
        if (duplicado) {
            throw new CategoriaNomeDuplicadoException();
        }
    }

    private void aplicar(Categoria categoria, CategoriaRequest request) {
        categoria.setNome(request.nome());
        categoria.setDescricao(request.descricao());
        categoria.setAtivo(request.ativo() != null ? request.ativo() : true);
    }

    private CategoriaResponse toResponse(Categoria categoria) {
        long produtosVinculados = produtoRepository.countByCategoriaId(categoria.getId());
        return new CategoriaResponse(
                categoria.getId(), categoria.getNome(), categoria.getDescricao(), categoria.getAtivo(),
                produtosVinculados, categoria.getCriadoEm());
    }
}
```

- [ ] **Step 15: Write `CategoriaRepositoryTest.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoriaRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaRepository categoriaRepository;
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

    private Categoria novaCategoria(UUID tenantId, String nome) {
        Categoria c = new Categoria();
        c.setTenantId(tenantId);
        c.setNome(nome);
        return c;
    }

    @Test
    @Transactional
    void savesCategoriaWithDefaults() {
        Tenant tenant = createTenant("aurora-cat");
        setTenantContext(tenant.getId());

        Categoria saved = categoriaRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas"));
        entityManager.clear();

        Categoria reloaded = categoriaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAtivo()).isTrue();
    }

    @Test
    @Transactional
    void nomeMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-cat");
        setTenantContext(tenant.getId());

        categoriaRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> categoriaRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas")));
    }

    @Test
    @Transactional
    void sameNomeAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-cat");
        Tenant tenantB = createTenant("boreal-cat");

        setTenantContext(tenantA.getId());
        categoriaRepository.saveAndFlush(novaCategoria(tenantA.getId(), "Camisas"));

        setTenantContext(tenantB.getId());
        Categoria saved = categoriaRepository.saveAndFlush(novaCategoria(tenantB.getId(), "Camisas"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-cat");
        setTenantContext(tenant.getId());
        categoriaRepository.saveAndFlush(novaCategoria(tenant.getId(), "Camisas"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM categoria")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
```

- [ ] **Step 16: Run the repository test to verify it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=CategoriaRepositoryTest`
Expected: PASS (4/4).

- [ ] **Step 17: Write the failing `CategoriaServiceTest.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.CategoriaRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantContext;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoriaServiceTest extends AbstractIntegrationTest {

    @Autowired CategoriaService categoriaService;
    @Autowired ProdutoService produtoService;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        TenantContext.set(tenant.getId());

        User caller = new User();
        caller.setTenantId(tenant.getId());
        caller.setName("Marina");
        caller.setEmail(codigo + "@aurora.com.br");
        caller.setPasswordHash("hash");
        caller.setRole(Role.ADMIN);
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private CategoriaRequest request(String nome) {
        return new CategoriaRequest(nome, "Descrição de teste", null);
    }

    @Test
    @Transactional
    void criaERecuperaCategoria() {
        setUpTenant("aurora-cat");

        var criada = categoriaService.criar(TenantContext.get(), request("Camisas"));

        var buscada = categoriaService.buscarPorId(criada.id());
        assertThat(buscada.nome()).isEqualTo("Camisas");
        assertThat(buscada.ativo()).isTrue();
        assertThat(buscada.produtosVinculados()).isEqualTo(0L);
    }

    @Test
    @Transactional
    void rejectsDuplicateNomeOnCreate() {
        setUpTenant("aurora-cat");
        categoriaService.criar(TenantContext.get(), request("Camisas"));

        assertThatThrownBy(() -> categoriaService.criar(TenantContext.get(), request("Camisas")))
                .isInstanceOf(CategoriaNomeDuplicadoException.class);
    }

    @Test
    @Transactional
    void rejectsDuplicateNomeOnUpdateAgainstAnotherCategoria() {
        setUpTenant("aurora-cat");
        categoriaService.criar(TenantContext.get(), request("Camisas"));
        var outra = categoriaService.criar(TenantContext.get(), request("Calças"));

        assertThatThrownBy(() -> categoriaService.atualizar(outra.id(), request("Camisas")))
                .isInstanceOf(CategoriaNomeDuplicadoException.class);
    }

    @Test
    @Transactional
    void allowsUpdatingACategoriaWithoutChangingItsOwnNome() {
        setUpTenant("aurora-cat");
        var criada = categoriaService.criar(TenantContext.get(), request("Camisas"));

        var atualizada = categoriaService.atualizar(criada.id(),
                new CategoriaRequest("Camisas", "Descrição nova", false));

        assertThat(atualizada.descricao()).isEqualTo("Descrição nova");
        assertThat(atualizada.ativo()).isFalse();
    }

    @Test
    @Transactional
    void deletesUnusedCategoria() {
        setUpTenant("aurora-cat");
        var criada = categoriaService.criar(TenantContext.get(), request("Camisas"));

        categoriaService.excluir(criada.id());

        assertThatThrownBy(() -> categoriaService.buscarPorId(criada.id()))
                .isInstanceOf(CategoriaNaoEncontradaException.class);
    }

    @Test
    @Transactional
    void rejectsDeletingACategoriaInUseByAProduto() {
        setUpTenant("aurora-cat");
        var categoria = categoriaService.criar(TenantContext.get(), request("Camisas"));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Polo", "P0001", null, null, categoria.id(),
                new BigDecimal("59.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));

        assertThatThrownBy(() -> categoriaService.excluir(categoria.id()))
                .isInstanceOf(CategoriaEmUsoException.class);
    }

    @Test
    @Transactional
    void listFiltersByAtivo() {
        setUpTenant("aurora-cat");
        categoriaService.criar(TenantContext.get(), new CategoriaRequest("Camisas", null, true));
        categoriaService.criar(TenantContext.get(), new CategoriaRequest("Descontinuada", null, false));

        var ativas = categoriaService.listar(null, true, PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("nome").containsExactly("Camisas");
    }
}
```

- [ ] **Step 18: Run the test to verify it fails**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=CategoriaServiceTest`
Expected: FAIL to compile — `CategoriaService` does not exist yet (Steps 3-14 above create it; if you're following the plan in order, `CategoriaService` was already written in Step 14 — in that case this step instead confirms the test compiles and passes, run it as a sanity check of the whole task rather than a strict red step).

- [ ] **Step 19: Run the full test to verify it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=CategoriaServiceTest,CategoriaRepositoryTest,ProdutoServiceTest,ProdutoRepositoryTest`
Expected: PASS, all tests — confirms `Categoria`'s own tests pass AND the `ProdutoService`/`ProdutoRepository` changes didn't break existing Produto behavior.

- [ ] **Step 20: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: no NEW failures introduced by this task's diff. A known, pre-existing, order-dependent failure in `com.meshsuite.payable.*` (documented during the Financeiro Mínimo sub-project — `DevSeedTest`/`tenant_codigo_key` collision) may still appear; that is not caused by this task and is not a regression to chase here.

- [ ] **Step 21: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V19__create_categoria.sql \
        mesh-suite-backend/src/main/resources/db/migration/V20__replace_produto_categoria_with_fk.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/Categoria.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaRepository.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaSpecifications.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaNaoEncontradaException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaNomeDuplicadoException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaEmUsoException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaRequest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CategoriaResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/Produto.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoRepository.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoRequest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoResponse.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoServiceTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/CategoriaRepositoryTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/CategoriaServiceTest.java
git commit -m "feat(categoria): add Categoria domain model, service, and Produto FK integration"
```

---

### Task 2: `CategoriaController`, exception wiring, and integration tests (backend)

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaExceptionHandler.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/CategoriaControllerTest.java`

**Interfaces:**
- Consumes: `CategoriaService.listar/buscarPorId/criar/atualizar/excluir` (Task 1), `CategoriaRequest`/`CategoriaResponse` (Task 1).
- Produces: `GET/POST/PUT/DELETE /api/categorias` — consumed directly by Task 3 (Categoria frontend).

- [ ] **Step 1: Write `CategoriaController.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.CategoriaRequest;
import com.meshsuite.produto.dto.CategoriaResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public Page<CategoriaResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return categoriaService.listar(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public CategoriaResponse buscarPorId(@PathVariable UUID id) {
        return categoriaService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<CategoriaResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                     @Valid @RequestBody CategoriaRequest request) {
        CategoriaResponse response = categoriaService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public CategoriaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CategoriaRequest request) {
        return categoriaService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        categoriaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Write `CategoriaExceptionHandler.java`**

```java
package com.meshsuite.produto;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = CategoriaController.class)
public class CategoriaExceptionHandler {

    // Fallback for a race condition slipping past CategoriaService's pre-check
    // (two concurrent requests for the same new nome) -- the DB's
    // UNIQUE(tenant_id, nome) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma categoria cadastrada com este nome"));
    }
}
```

- [ ] **Step 3: Register the three named exceptions in `GlobalExceptionHandler.java`**

Append these three handlers to the end of the class, right after the existing `handleAccountsPayableValidation` method, before the final closing brace:

```java
    @ExceptionHandler(com.meshsuite.produto.CategoriaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleCategoriaNaoEncontrada(
            com.meshsuite.produto.CategoriaNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.CategoriaNomeDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleCategoriaNomeDuplicado(
            com.meshsuite.produto.CategoriaNomeDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.CategoriaEmUsoException.class)
    public ResponseEntity<Map<String, String>> handleCategoriaEmUso(
            com.meshsuite.produto.CategoriaEmUsoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 4: Write `CategoriaControllerTest.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.auth.Module;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class CategoriaControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String loginAndGetCookie(String codigo, String email, String cnpjEmpresa) throws Exception {
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

    private String loginWithoutProductPermission(String codigo, String email, String cnpjEmpresa) throws Exception {
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

    private String categoriaPayload(String nome) {
        return """
                {
                  "nome": "%s"
                }
                """.formatted(nome);
    }

    @Test
    void createsListsUpdatesAndDeletesCategoria() throws Exception {
        String token = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/categorias").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoriaPayload("Camisas")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Camisas"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/categorias").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Camisas"));

        mockMvc.perform(put("/api/categorias/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoriaPayload("Camisas Atualizadas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Camisas Atualizadas"));

        mockMvc.perform(delete("/api/categorias/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categorias/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateNomeWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/categorias").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoriaPayload("Camisas")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categorias").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoriaPayload("Camisas")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDeletingACategoriaInUseWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/categorias").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoriaPayload("Camisas")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String categoriaId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

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
                                """.formatted(categoriaId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categorias/" + categoriaId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsCategoria() throws Exception {
        String tokenA = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/categorias").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoriaPayload("Camisas")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal-cat", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404.
        entityManager.clear();

        mockMvc.perform(get("/api/categorias/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao-cat", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/categorias").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=CategoriaControllerTest`
Expected: PASS (6/6).

- [ ] **Step 6: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: no NEW failures beyond the known pre-existing `com.meshsuite.payable.*` ordering issue (see Task 1, Step 20).

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CategoriaExceptionHandler.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/CategoriaControllerTest.java
git commit -m "feat(categoria): add CategoriaController and exception wiring"
```

---

### Task 3: Categoria frontend — API layer, list view, form view, routing, sidebar

**Files:**
- Create: `mesh-suite-frontend/src/api/categorias.ts`
- Create: `mesh-suite-frontend/src/views/CategoriasListView.vue`
- Create: `mesh-suite-frontend/src/views/CategoriaFormView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/CategoriasListView.spec.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/CategoriaFormView.spec.ts`

**Interfaces:**
- Consumes: `GET/POST/PUT/DELETE /api/categorias` (Task 2).
- Produces: `listarCategorias`, `buscarCategoria`, `criarCategoria`, `atualizarCategoria`, `excluirCategoria` in `@/api/categorias`, and the `/categorias` route. Task 4 (Produto frontend) consumes `listarCategorias` directly.

- [ ] **Step 1: Write `api/categorias.ts`**

```ts
import { apiClient } from './client'

export interface CategoriaRequest {
  nome: string
  descricao: string | null
  ativo: boolean | null
}

export interface CategoriaResponse extends CategoriaRequest {
  id: string
  produtosVinculados: number
  criadoEm: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarCategoriasParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listarCategorias(params: ListarCategoriasParams): Promise<Page<CategoriaResponse>> {
  const { data } = await apiClient.get<Page<CategoriaResponse>>('/categorias', { params })
  return data
}

export async function buscarCategoria(id: string): Promise<CategoriaResponse> {
  const { data } = await apiClient.get<CategoriaResponse>(`/categorias/${id}`)
  return data
}

export async function criarCategoria(payload: CategoriaRequest): Promise<CategoriaResponse> {
  const { data } = await apiClient.post<CategoriaResponse>('/categorias', payload)
  return data
}

export async function atualizarCategoria(id: string, payload: CategoriaRequest): Promise<CategoriaResponse> {
  const { data } = await apiClient.put<CategoriaResponse>(`/categorias/${id}`, payload)
  return data
}

export async function excluirCategoria(id: string): Promise<void> {
  await apiClient.delete(`/categorias/${id}`)
}
```

- [ ] **Step 2: Write `CategoriasListView.vue`**

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
          <tr v-for="categoria in pagina.content" :key="categoria.id">
            <td>{{ categoria.nome }}</td>
            <td>{{ categoria.descricao }}</td>
            <td>{{ categoria.produtosVinculados }} produtos</td>
            <td><span class="badge" :class="categoria.ativo ? 'badge-ATIVO' : 'badge-INATIVO'">{{ categoria.ativo ? 'Ativo' : 'Inativo' }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(categoria.id, $event)"
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
  listarCategorias,
  excluirCategoria,
  type CategoriaResponse,
  type Page as ApiPage,
} from '@/api/categorias'

const router = useRouter()

const filtros = reactive({ busca: '', ativo: '' })
const pagina = ref<ApiPage<CategoriaResponse>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const categoriaAcoesAtual = computed(() =>
  pagina.value.content.find((c) => c.id === acoesAbertas.value) ?? null,
)

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listarCategorias({
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

async function excluir(categoria: CategoriaResponse) {
  acoesAbertas.value = null
  if (!confirm(`Excluir a categoria "${categoria.nome}"?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirCategoria(categoria.id)
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

- [ ] **Step 3: Write `CategoriaFormView.vue`**

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Categoria' : 'Nova Categoria'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <div>
          <label class="field-label">Nome *</label>
          <input v-model="form.nome" data-test="nome" placeholder="Ex: Camisas" />
          <p v-if="erros.nome" class="field-error">{{ erros.nome }}</p>
        </div>
        <div>
          <label class="field-label">Descrição</label>
          <textarea v-model="form.descricao" data-test="descricao" rows="3" placeholder="Descrição opcional..."></textarea>
        </div>
        <div>
          <label class="field-label">Status</label>
          <div class="status-toggle">
            <button
              type="button"
              class="status-btn"
              :class="{ 'status-btn-active-ativo': form.ativo }"
              data-test="status-ativo"
              @click="form.ativo = true"
            >
              Ativo
            </button>
            <button
              type="button"
              class="status-btn"
              :class="{ 'status-btn-active-inativo': !form.ativo }"
              data-test="status-inativo"
              @click="form.ativo = false"
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
  buscarCategoria,
  criarCategoria,
  atualizarCategoria,
  type CategoriaRequest,
} from '@/api/categorias'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): CategoriaRequest {
  return { nome: '', descricao: '', ativo: true }
}

const form = reactive<CategoriaRequest>(novoFormulario())
const erros = reactive<{ nome?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const categoria = await buscarCategoria(id)
      form.nome = categoria.nome
      form.descricao = categoria.descricao
      form.ativo = categoria.ativo
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da categoria.'
    }
  }
})

function validar(): boolean {
  erros.nome = form.nome.trim() ? undefined : 'Campo obrigatório'
  return !erros.nome
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
      await atualizarCategoria(id, form)
    } else {
      await criarCategoria(form)
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

- [ ] **Step 4: Wire up the routes**

In `mesh-suite-frontend/src/router/index.ts`, add these imports alongside the existing ones:

```ts
import CategoriasListView from '@/views/CategoriasListView.vue'
import CategoriaFormView from '@/views/CategoriaFormView.vue'
```

Add these three routes to the `routes` array, right after the `/produtos/:id/editar` entry:

```ts
    { path: '/categorias', name: 'categorias', component: CategoriasListView },
    { path: '/categorias/novo', name: 'categorias-novo', component: CategoriaFormView },
    { path: '/categorias/:id/editar', name: 'categorias-editar', component: CategoriaFormView },
```

- [ ] **Step 5: Wire up the sidebar**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, find the `Categorias` entry inside the `catalogo` group (currently `route: null`):

```ts
      { icon: '🗂', label: 'Categorias', route: null },
```

Change it to:

```ts
      { icon: '🗂', label: 'Categorias', route: '/categorias' },
```

- [ ] **Step 6: Write `CategoriasListView.spec.ts`**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CategoriasListView from '@/views/CategoriasListView.vue'
import * as categoriasApi from '@/api/categorias'

vi.mock('@/api/categorias')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/categorias', name: 'categorias', component: CategoriasListView },
      { path: '/categorias/novo', name: 'categorias-novo', component: { template: '<div />' } },
      { path: '/categorias/:id/editar', name: 'categorias-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/categorias')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(CategoriasListView, { global: { plugins: [router] } }),
  }))
}

const categoriaExemplo = {
  id: 'cat-1',
  nome: 'Camisas',
  descricao: 'Camisas em geral',
  ativo: true,
  produtosVinculados: 3,
  criadoEm: '2026-01-01T00:00:00Z',
}

describe('CategoriasListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the category list', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [categoriaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Camisas')
    expect(wrapper.text()).toContain('3 produtos')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de categorias.')
  })

  it('reloads the list when the search field changes', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('Cam')
    await flushPromises()

    expect(categoriasApi.listarCategorias).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: 'Cam' }),
    )
  })

  it('navigates to the new-category route when the button is clicked', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-categoria"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('categorias-novo')
  })

  it('deletes a category after confirmation and reloads the list', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [categoriaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(categoriasApi.excluirCategoria).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(categoriasApi.excluirCategoria).toHaveBeenCalledWith('cat-1')
  })

  it('shows the backend message when deletion is blocked because the category is in use', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [categoriaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(categoriasApi.excluirCategoria).mockRejectedValue({
      response: { data: { mensagem: 'Não é possível excluir: 3 produto(s) usam esta categoria' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não é possível excluir: 3 produto(s) usam esta categoria')
  })
})
```

- [ ] **Step 7: Write `CategoriaFormView.spec.ts`**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CategoriaFormView from '@/views/CategoriaFormView.vue'
import * as categoriasApi from '@/api/categorias'

vi.mock('@/api/categorias')

function mountWithRouter(path = '/categorias/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/categorias', name: 'categorias', component: { template: '<div />' } },
      { path: '/categorias/novo', name: 'categorias-novo', component: CategoriaFormView },
      { path: '/categorias/:id/editar', name: 'categorias-editar', component: CategoriaFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(CategoriaFormView, { global: { plugins: [router] } }),
  }))
}

describe('CategoriaFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows a required-field error when nome is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(categoriasApi.criarCategoria).not.toHaveBeenCalled()
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(categoriasApi.criarCategoria).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camisas')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(categoriasApi.criarCategoria).toHaveBeenCalledWith(
      expect.objectContaining({ nome: 'Camisas', ativo: true }),
    )
    expect(router.currentRoute.value.name).toBe('categorias')
  })

  it('shows a conflict message on duplicate nome (409)', async () => {
    vi.mocked(categoriasApi.criarCategoria).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camisas')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma categoria cadastrada com este nome')
  })

  it('toggles between Ativo and Inativo status', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="status-inativo"]').trigger('click')
    vi.mocked(categoriasApi.criarCategoria).mockResolvedValue({} as any)
    await wrapper.find('[data-test="nome"]').setValue('Camisas')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(categoriasApi.criarCategoria).toHaveBeenCalledWith(
      expect.objectContaining({ ativo: false }),
    )
  })

  it('loads existing categoria data in edit mode', async () => {
    vi.mocked(categoriasApi.buscarCategoria).mockResolvedValue({
      id: 'cat-1', nome: 'Camisas', descricao: 'Descrição', ativo: true,
      produtosVinculados: 2, criadoEm: '2026-01-01T00:00:00Z',
    })

    const { wrapper } = await mountWithRouter('/categorias/cat-1/editar')
    await flushPromises()

    expect(categoriasApi.buscarCategoria).toHaveBeenCalledWith('cat-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Camisas')
  })

  it('shows an error message when loading categoria data fails in edit mode', async () => {
    vi.mocked(categoriasApi.buscarCategoria).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/categorias/cat-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da categoria.')
  })
})
```

- [ ] **Step 8: Run the new tests to verify they pass**

Run: `cd mesh-suite-frontend && npm test -- --run src/views/__tests__/CategoriasListView.spec.ts src/views/__tests__/CategoriaFormView.spec.ts`
Expected: PASS (6/6 + 6/6).

- [ ] **Step 9: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npm test -- --run`
Expected: BUILD SUCCESS, no regressions anywhere (this task doesn't touch `ProdutoFormView.vue`/`api/produtos.ts` yet — those are Task 4).

- [ ] **Step 10: Commit**

```bash
git add mesh-suite-frontend/src/api/categorias.ts \
        mesh-suite-frontend/src/views/CategoriasListView.vue \
        mesh-suite-frontend/src/views/CategoriaFormView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/views/__tests__/CategoriasListView.spec.ts \
        mesh-suite-frontend/src/views/__tests__/CategoriaFormView.spec.ts
git commit -m "feat(categoria): add CategoriasListView, CategoriaFormView, routing and sidebar"
```

---

### Task 4: Produto frontend — categoria dropdown

**Files:**
- Modify: `mesh-suite-frontend/src/api/produtos.ts`
- Modify: `mesh-suite-frontend/src/views/ProdutoFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts`

**Interfaces:**
- Consumes: `listarCategorias` from `@/api/categorias` (Task 3).
- Produces: nothing new — this is the final task, it completes `Produto`'s form to match the backend's `categoriaId`/`categoriaNome` shape (Task 1).

- [ ] **Step 1: Modify `api/produtos.ts` — replace `categoria` with `categoriaId`/`categoriaNome`**

Find:

```ts
export interface ProdutoRequest {
  nome: string
  sku: string
  codigoBarras: string
  marca: string
  categoria: string
  precoVenda: number
  precoCusto: number | null
  status: StatusProduto
  descricao: string
  quantidadeEstoque: number
  unidadeMedida: UnidadeMedida
  estoqueMinimo: number | null
  estoqueMaximo: number | null
  peso: number | null
  comprimento: number | null
  largura: number | null
  altura: number | null
}

export interface ProdutoResponse extends ProdutoRequest {
  id: string
}
```

Replace with:

```ts
export interface ProdutoRequest {
  nome: string
  sku: string
  codigoBarras: string
  marca: string
  categoriaId: string | null
  precoVenda: number
  precoCusto: number | null
  status: StatusProduto
  descricao: string
  quantidadeEstoque: number
  unidadeMedida: UnidadeMedida
  estoqueMinimo: number | null
  estoqueMaximo: number | null
  peso: number | null
  comprimento: number | null
  largura: number | null
  altura: number | null
}

export interface ProdutoResponse extends ProdutoRequest {
  id: string
  categoriaNome: string | null
}
```

- [ ] **Step 2: Modify `ProdutoFormView.vue` — swap the text input for a dropdown**

Find:

```html
          <div>
            <label class="field-label">Categoria</label>
            <input v-model="form.categoria" />
          </div>
```

Replace with:

```html
          <div>
            <label class="field-label">Categoria</label>
            <select v-model="form.categoriaId" data-test="categoria">
              <option :value="null">Sem categoria</option>
              <option v-for="categoria in categorias" :key="categoria.id" :value="categoria.id">
                {{ categoria.nome }}
              </option>
            </select>
          </div>
```

Find the imports:

```ts
import {
  buscarProduto,
  criarProduto,
  atualizarProduto,
  type ProdutoRequest,
  type UnidadeMedida,
} from '@/api/produtos'
```

Replace with:

```ts
import {
  buscarProduto,
  criarProduto,
  atualizarProduto,
  type ProdutoRequest,
  type UnidadeMedida,
} from '@/api/produtos'
import { listarCategorias, type CategoriaResponse } from '@/api/categorias'
```

Find:

```ts
function novoFormulario(): ProdutoRequest {
  return {
    nome: '',
    sku: '',
    codigoBarras: '',
    marca: '',
    categoria: '',
    precoVenda: 0,
```

Replace with:

```ts
function novoFormulario(): ProdutoRequest {
  return {
    nome: '',
    sku: '',
    codigoBarras: '',
    marca: '',
    categoriaId: null,
    precoVenda: 0,
```

Find:

```ts
const form = reactive<ProdutoRequest>(novoFormulario())
const erros = reactive<{ nome?: string; sku?: string; precoVenda?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const produto = await buscarProduto(id)
      Object.assign(form, produto)
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do produto.'
    }
  }
})
```

Replace with:

```ts
const form = reactive<ProdutoRequest>(novoFormulario())
const erros = reactive<{ nome?: string; sku?: string; precoVenda?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)
const categorias = ref<CategoriaResponse[]>([])

onMounted(async () => {
  try {
    const pagina = await listarCategorias({ ativo: true, size: 100 })
    categorias.value = pagina.content
  } catch {
    // Categoria list is a convenience dropdown, not a required field --
    // if it fails to load, the form still works with "Sem categoria" as
    // the only option, and the current value (if editing) still round-trips.
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const produto = await buscarProduto(id)
      Object.assign(form, produto)
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do produto.'
    }
  }
})
```

- [ ] **Step 3: Update the stale mock in `ProdutoFormView.spec.ts`**

Find, in the `'loads existing produto data in edit mode'` test:

```ts
    vi.mocked(produtosApi.buscarProduto).mockResolvedValue({
      id: 'abc-123', nome: 'Camiseta Polo', sku: 'P0001', codigoBarras: '', marca: '', categoria: '',
      precoVenda: 59.9, precoCusto: null, status: 'ATIVO', descricao: '', quantidadeEstoque: 10,
      unidadeMedida: 'UN', estoqueMinimo: null, estoqueMaximo: null, peso: null, comprimento: null,
      largura: null, altura: null,
    } as any)
```

Replace with:

```ts
    vi.mocked(produtosApi.buscarProduto).mockResolvedValue({
      id: 'abc-123', nome: 'Camiseta Polo', sku: 'P0001', codigoBarras: '', marca: '', categoriaId: null,
      categoriaNome: null, precoVenda: 59.9, precoCusto: null, status: 'ATIVO', descricao: '', quantidadeEstoque: 10,
      unidadeMedida: 'UN', estoqueMinimo: null, estoqueMaximo: null, peso: null, comprimento: null,
      largura: null, altura: null,
    } as any)
```

Add this new test at the end of the `describe` block, right before the closing `})`:

```ts

  it('loads categorias into the dropdown and lets the user pick one', async () => {
    const categoriasApi = await import('@/api/categorias')
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [
        { id: 'cat-1', nome: 'Camisas', descricao: null, ativo: true, produtosVinculados: 0, criadoEm: '2026-01-01T00:00:00Z' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 100,
    })
    vi.mocked(produtosApi.criarProduto).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('[data-test="categoria"]').setValue('cat-1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(produtosApi.criarProduto).mock.calls[0][0]
    expect(payload.categoriaId).toBe('cat-1')
  })
```

Add this mock declaration at the top of the file, right after the existing `vi.mock('@/api/produtos')`:

```ts
vi.mock('@/api/categorias')
```

- [ ] **Step 4: Run the updated tests to verify they pass**

Run: `cd mesh-suite-frontend && npm test -- --run src/views/__tests__/ProdutoFormView.spec.ts`
Expected: PASS (8/8).

- [ ] **Step 5: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npm test -- --run`
Expected: BUILD SUCCESS, no regressions.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/api/produtos.ts \
        mesh-suite-frontend/src/views/ProdutoFormView.vue \
        mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts
git commit -m "feat(categoria): wire Produto form's categoria field to the new dropdown"
```
