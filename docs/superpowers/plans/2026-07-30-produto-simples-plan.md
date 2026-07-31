# Cadastro de Produto (Simples) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Produto (Simples) master-data cadastro — the second domain of `PRD-13-cadastro-comercial.md`'s "Cadastro Comercial" slice (after Cliente/Fornecedor), following the design spec at `docs/superpowers/specs/2026-07-30-produto-simples-design.md`. First of three sequential Produto plans (Simples → Kit → Com Variação); this plan covers Simples only.

**Architecture:** Backend follows the exact pattern established by `parceiro` (entity + repository + service + controller + DTO, RLS via `tenant_id`, tenant scoping entirely through `TenantContext`/`TenantContextAspect`/RLS — no manual tenant filtering in queries). Frontend adds two views (list, create/edit form) reusing `AppShell` and the `--pm-*` design tokens, wired into the router and the previously-inert "Produtos" sidebar item. The list's Ações dropdown uses `Teleport` from the start — the Cliente slice's list originally used `position: absolute` inside a `overflow: hidden` card, which silently clipped the dropdown in the browser (invisible to jsdom tests) and had to be fixed after the fact; this plan starts with the corrected pattern.

**Tech Stack:** Same as the rest of the repo — Spring Boot 3.4.5 / Java 21, Postgres 16 + Flyway + RLS, Vue 3 + TypeScript + Vite, Pinia, Vue Router. No new dependency.

## Global Constraints

- Scope is the **Simples** product type only, per `docs/superpowers/specs/2026-07-30-produto-simples-design.md`. Kit and Com Variação are separate, future plans — no `tipo` column, no "Tipo de Produto" selector in the form, no hierarchical rows in the list.
- No image upload (no file-storage infrastructure exists yet) — the "Imagem do Produto" section from the reference mockup is entirely omitted, not replaced with a URL field.
- `marca`, `categoria` are free-text fields (no dedicated cadastro domain exists for either yet).
- `unidade_medida` is a fixed enum (`UN, KG, G, L, ML, MT, CM, CX, PC, PAR, DZ`), not a cadastro domain.
- `quantidade_estoque`/`estoque_minimo`/`estoque_maximo` are freely editable fields with no movement log — they anticipate the future Estoque domain, which will own the real movement logic.
- `sku` is unique per tenant (same pattern as `parceiro.documento`). Status has only two values (`ATIVO`/`INATIVO`), both freely settable — no restricted-transition business rule like Parceiro's `EM_RISCO`.
- Unlike `ParceiroRequest`, `ProdutoRequest` **does** include a `status` field — the reference form (`ProdutosB`) shows a status toggle directly in the create/edit form, unlike the Cliente form. `PATCH /api/produtos/{id}/status` still exists too (used by the list's Ações menu), and both paths coexist: the form can set status at save time, the list can flip it afterward.
- The list's Ações menu has only **Editar / Ativar-Inativar / Excluir** — no "Ver", since (unlike Cliente) there's no separate detail/profile view in this slice; the reference prototype doesn't define one for Produto either.
- Backend tenant scoping relies entirely on `TenantContext` + `TenantContextAspect` + RLS (set automatically per-request by `JwtAuthenticationFilter`) — service methods never add an explicit `tenant_id` predicate to queries. The only place `tenantId` is touched explicitly is when constructing a **new** `Produto` entity.
- The generic `DataIntegrityViolationException` handler for the SKU-uniqueness race-condition fallback must be scoped to `ProdutoController` only (`@RestControllerAdvice(assignableTypes = ProdutoController.class)`, its own file, mirroring `ParceiroExceptionHandler`) — **not** added to the shared `GlobalExceptionHandler`, which would leak a Produto-branded 409 message onto unrelated domains' unique-constraint violations (a real bug found and fixed in the Cliente/Fornecedor plan's final review).
- Every color in new Vue `<style>` blocks is a `var(--pm-*)` custom property — no new hardcoded hex. The neutral `rgba(0, 0, 0, X)` box-shadow elevation exception still applies.
- Every data-loading/mutating function in the two new views is wrapped in try/catch with a user-facing error message (the `erro`/`erroGeral` ref convention established during the Cliente slice's final review) — applied from the start here, not added after the fact.
- Test-writing traps already found and fixed once in the Cliente/Fornecedor plan, pre-applied here so they aren't repeated:
  - Backend service tests that construct entities via `TenantContext.get()` must call `TenantContext.set(tenantId)` in their setup helper (not just the raw `SET LOCAL app.tenant_id = ...` native query, which sets the Postgres session variable but not the Java-side `ThreadLocal` the service layer reads).
  - Backend controller tests exercising cross-tenant access must call `entityManager.clear()` between the two tenants' requests inside one `@Transactional` test method — otherwise Hibernate's first-level cache returns the first tenant's already-managed entity without re-issuing SQL, masking RLS behind a false 200 instead of the expected 404.
  - Frontend tests for the list view must mount with `global: { stubs: { teleport: true } }` so the Teleported Ações dropdown renders in place instead of into `document.body`, keeping `wrapper.find(...)` queries working.

---

### Task 1: `Produto` entity, migration, repository

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V6__create_produto.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/StatusProduto.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/UnidadeMedida.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/Produto.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoRepositoryTest.java`

**Interfaces:**
- Produces: `Produto` (entity, package `com.meshsuite.produto`), `ProdutoRepository` (extends `JpaRepository<Produto, UUID>` and `JpaSpecificationExecutor<Produto>`, with `existsBySku(String)`, `existsBySkuAndIdNot(String, UUID)`, `countByStatus(StatusProduto)`). Task 2 consumes all of these.

- [ ] **Step 1: Write the migration**

```sql
CREATE TABLE produto (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    codigo_barras VARCHAR(50),
    marca VARCHAR(100),
    categoria VARCHAR(100),
    preco_venda NUMERIC(12,2) NOT NULL,
    preco_custo NUMERIC(12,2),
    status VARCHAR(10) NOT NULL DEFAULT 'ATIVO' CHECK (status IN ('ATIVO','INATIVO')),
    descricao TEXT,
    quantidade_estoque NUMERIC(12,3) NOT NULL DEFAULT 0,
    unidade_medida VARCHAR(5) NOT NULL DEFAULT 'UN'
        CHECK (unidade_medida IN ('UN','KG','G','L','ML','MT','CM','CX','PC','PAR','DZ')),
    estoque_minimo NUMERIC(12,3),
    estoque_maximo NUMERIC(12,3),
    peso NUMERIC(10,3),
    comprimento NUMERIC(10,2),
    largura NUMERIC(10,2),
    altura NUMERIC(10,2),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_produto_tenant_sku ON produto(tenant_id, sku);
CREATE INDEX idx_produto_tenant_id ON produto(tenant_id);

ALTER TABLE produto ENABLE ROW LEVEL SECURITY;
ALTER TABLE produto FORCE ROW LEVEL SECURITY;

CREATE POLICY produto_tenant_isolation ON produto
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

- [ ] **Step 2: Write the enums**

`StatusProduto.java`:
```java
package com.meshsuite.produto;

public enum StatusProduto {
    ATIVO,
    INATIVO
}
```

`UnidadeMedida.java`:
```java
package com.meshsuite.produto;

public enum UnidadeMedida {
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

- [ ] **Step 3: Write the `Produto` entity**

```java
package com.meshsuite.produto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "produto")
@Getter
@Setter
public class Produto {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "codigo_barras", length = 50)
    private String codigoBarras;

    @Column(length = 100)
    private String marca;

    @Column(length = 100)
    private String categoria;

    @Column(name = "preco_venda", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "preco_custo", precision = 12, scale = 2)
    private BigDecimal precoCusto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusProduto status = StatusProduto.ATIVO;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "quantidade_estoque", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeEstoque = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_medida", nullable = false, length = 5)
    private UnidadeMedida unidadeMedida = UnidadeMedida.UN;

    @Column(name = "estoque_minimo", precision = 12, scale = 3)
    private BigDecimal estoqueMinimo;

    @Column(name = "estoque_maximo", precision = 12, scale = 3)
    private BigDecimal estoqueMaximo;

    @Column(precision = 10, scale = 3)
    private BigDecimal peso;

    @Column(precision = 10, scale = 2)
    private BigDecimal comprimento;

    @Column(precision = 10, scale = 2)
    private BigDecimal largura;

    @Column(precision = 10, scale = 2)
    private BigDecimal altura;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
```

- [ ] **Step 4: Write the repository**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<Produto, UUID>, JpaSpecificationExecutor<Produto> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    long countByStatus(StatusProduto status);
}
```

- [ ] **Step 5: Write the repository test**

```java
package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProdutoRepository produtoRepository;
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

    private Produto novoProduto(UUID tenantId, String sku) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("59.90"));
        return p;
    }

    @Test
    @Transactional
    void savesProdutoWithDefaults() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Produto saved = produtoRepository.saveAndFlush(novoProduto(tenant.getId(), "P0001"));
        entityManager.clear();

        Produto reloaded = produtoRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusProduto.ATIVO);
        assertThat(reloaded.getUnidadeMedida()).isEqualTo(UnidadeMedida.UN);
        assertThat(reloaded.getQuantidadeEstoque()).isEqualByComparingTo("0");
    }

    @Test
    @Transactional
    void skuMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        produtoRepository.saveAndFlush(novoProduto(tenant.getId(), "P0001"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> produtoRepository.saveAndFlush(novoProduto(tenant.getId(), "P0001")));
    }

    @Test
    @Transactional
    void sameSkuAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        produtoRepository.saveAndFlush(novoProduto(tenantA.getId(), "P0001"));

        setTenantContext(tenantB.getId());
        Produto saved = produtoRepository.saveAndFlush(novoProduto(tenantB.getId(), "P0001"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        produtoRepository.saveAndFlush(novoProduto(tenant.getId(), "P0001"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM produto")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=ProdutoRepositoryTest`
Expected: PASS (4 tests). Requires Docker running (Testcontainers Postgres).

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V6__create_produto.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/ \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoRepositoryTest.java
git commit -m "feat: add Produto entity, migration, and RLS"
```

---

### Task 2: DTOs, exceptions, and `ProdutoService`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoStatusRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoResumoResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoNaoEncontradoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/SkuDuplicadoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoServiceTest.java`

**Interfaces:**
- Consumes: `Produto`, `ProdutoRepository`, `StatusProduto`, `UnidadeMedida` (Task 1).
- Produces: `ProdutoService` with methods `listar(String, StatusProduto, Pageable): Page<ProdutoSummaryResponse>`, `resumo(): ProdutoResumoResponse`, `buscarPorId(UUID): ProdutoResponse`, `criar(UUID, ProdutoRequest): ProdutoResponse`, `atualizar(UUID, ProdutoRequest): ProdutoResponse`, `atualizarStatus(UUID, StatusProduto): ProdutoResponse`, `excluir(UUID): void`. Task 3 (controller) consumes all of these. Note: unlike Parceiro, this does NOT need a `ProdutoValidacaoException` — Produto has no restricted-status-transition rule (both ATIVO/INATIVO are freely settable) and no "at least one role" rule; every other business constraint (`nome`/`sku` required, `precoVenda > 0`) is expressible directly via Jakarta Bean Validation annotations on `ProdutoRequest`, handled by Spring's default validation-failure response (already exercised successfully by `ParceiroRequest`'s own `@NotBlank`/`@NotEmpty` fields, which needed no custom exception either).

- [ ] **Step 1: Write the DTOs**

`ProdutoRequest.java`:
```java
package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import com.meshsuite.produto.UnidadeMedida;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProdutoRequest(
        @NotBlank String nome,
        @NotBlank String sku,
        String codigoBarras,
        String marca,
        String categoria,
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

`ProdutoResponse.java`:
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
        String categoria,
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

`ProdutoSummaryResponse.java`:
```java
package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoSummaryResponse(
        UUID id,
        String nome,
        String sku,
        String marca,
        BigDecimal precoVenda,
        BigDecimal quantidadeEstoque,
        StatusProduto status) {
}
```

`ProdutoStatusRequest.java`:
```java
package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import jakarta.validation.constraints.NotNull;

public record ProdutoStatusRequest(@NotNull StatusProduto status) {
}
```

`ProdutoResumoResponse.java`:
```java
package com.meshsuite.produto.dto;

public record ProdutoResumoResponse(long total, long ativos, long inativos) {
}
```

- [ ] **Step 2: Write the exceptions**

`ProdutoNaoEncontradoException.java`:
```java
package com.meshsuite.produto;

public class ProdutoNaoEncontradoException extends RuntimeException {
    public ProdutoNaoEncontradoException() {
        super("Produto não encontrado");
    }
}
```

`SkuDuplicadoException.java`:
```java
package com.meshsuite.produto;

public class SkuDuplicadoException extends RuntimeException {
    public SkuDuplicadoException() {
        super("Já existe um produto cadastrado com este SKU");
    }
}
```

- [ ] **Step 3: Write the specifications helper**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.domain.Specification;

public final class ProdutoSpecifications {

    private ProdutoSpecifications() {
    }

    public static Specification<Produto> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nome")), termo),
                cb.like(cb.lower(root.get("sku")), termo));
    }

    public static Specification<Produto> comStatus(StatusProduto status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
```

- [ ] **Step 4: Write `ProdutoService`**

```java
package com.meshsuite.produto;

import com.meshsuite.produto.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProdutoSummaryResponse> listar(String busca, StatusProduto status, Pageable pageable) {
        Specification<Produto> spec = Specification.allOf(
                ProdutoSpecifications.comBusca(busca),
                ProdutoSpecifications.comStatus(status));
        return produtoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public ProdutoResumoResponse resumo() {
        long ativos = produtoRepository.countByStatus(StatusProduto.ATIVO);
        long inativos = produtoRepository.countByStatus(StatusProduto.INATIVO);
        return new ProdutoResumoResponse(ativos + inativos, ativos, inativos);
    }

    @Transactional(readOnly = true)
    public ProdutoResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    public ProdutoResponse criar(UUID tenantId, ProdutoRequest request) {
        validarSku(request.sku(), null);

        Produto produto = new Produto();
        produto.setTenantId(tenantId);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    public ProdutoResponse atualizar(UUID id, ProdutoRequest request) {
        validarSku(request.sku(), id);

        Produto produto = buscarEntidadePorId(id);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    public ProdutoResponse atualizarStatus(UUID id, StatusProduto novoStatus) {
        Produto produto = buscarEntidadePorId(id);
        produto.setStatus(novoStatus);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    public void excluir(UUID id) {
        produtoRepository.delete(buscarEntidadePorId(id));
    }

    private Produto buscarEntidadePorId(UUID id) {
        return produtoRepository.findById(id).orElseThrow(ProdutoNaoEncontradoException::new);
    }

    private void validarSku(String sku, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? produtoRepository.existsBySku(sku)
                : produtoRepository.existsBySkuAndIdNot(sku, idAtual);
        if (duplicado) {
            throw new SkuDuplicadoException();
        }
    }

    private void aplicar(Produto produto, ProdutoRequest request) {
        produto.setNome(request.nome());
        produto.setSku(request.sku());
        produto.setCodigoBarras(request.codigoBarras());
        produto.setMarca(request.marca());
        produto.setCategoria(request.categoria());
        produto.setPrecoVenda(request.precoVenda());
        produto.setPrecoCusto(request.precoCusto());
        produto.setStatus(request.status() != null ? request.status() : StatusProduto.ATIVO);
        produto.setDescricao(request.descricao());
        produto.setQuantidadeEstoque(request.quantidadeEstoque() != null ? request.quantidadeEstoque() : BigDecimal.ZERO);
        produto.setUnidadeMedida(request.unidadeMedida() != null ? request.unidadeMedida() : UnidadeMedida.UN);
        produto.setEstoqueMinimo(request.estoqueMinimo());
        produto.setEstoqueMaximo(request.estoqueMaximo());
        produto.setPeso(request.peso());
        produto.setComprimento(request.comprimento());
        produto.setLargura(request.largura());
        produto.setAltura(request.altura());
    }

    private ProdutoSummaryResponse toSummary(Produto p) {
        return new ProdutoSummaryResponse(
                p.getId(), p.getNome(), p.getSku(), p.getMarca(), p.getPrecoVenda(), p.getQuantidadeEstoque(), p.getStatus());
    }

    private ProdutoResponse toResponse(Produto p) {
        return new ProdutoResponse(
                p.getId(), p.getNome(), p.getSku(), p.getCodigoBarras(), p.getMarca(), p.getCategoria(),
                p.getPrecoVenda(), p.getPrecoCusto(), p.getStatus(), p.getDescricao(), p.getQuantidadeEstoque(),
                p.getUnidadeMedida(), p.getEstoqueMinimo(), p.getEstoqueMaximo(), p.getPeso(), p.getComprimento(),
                p.getLargura(), p.getAltura());
    }
}
```

- [ ] **Step 5: Add exception handlers to `GlobalExceptionHandler`**

Add these methods to the existing `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java` (these two exception types are unique to the `produto` package, so — unlike `DataIntegrityViolationException`, handled in Task 3 via a Produto-scoped advice — there's no cross-domain leakage risk in keeping them here, mirroring how `ParceiroNaoEncontradoException`/`DocumentoDuplicadoException` already live in this same file):

```java
    @ExceptionHandler(com.meshsuite.produto.ProdutoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleProdutoNaoEncontrado(
            com.meshsuite.produto.ProdutoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.SkuDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleSkuDuplicado(
            com.meshsuite.produto.SkuDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 6: Write the service test**

```java
package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.produto.dto.ProdutoRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class ProdutoServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProdutoService produtoService;
    @Autowired EntityManager entityManager;

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());
        return tenant.getId();
    }

    private ProdutoRequest request(String sku, BigDecimal precoVenda) {
        return new ProdutoRequest(
                "Camiseta Polo Masculina", sku, "7891234567890", "Marca Alpha", "Vestuário",
                precoVenda, new BigDecimal("25.00"), StatusProduto.ATIVO, "Descrição de teste",
                new BigDecimal("10"), UnidadeMedida.UN, new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("0.300"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2"));
    }

    @Test
    void criaERecuperaProduto() {
        setUpTenant("aurora");

        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var buscado = produtoService.buscarPorId(criado.id());
        assertThat(buscado.nome()).isEqualTo("Camiseta Polo Masculina");
        assertThat(buscado.status()).isEqualTo(StatusProduto.ATIVO);
    }

    @Test
    void rejeitaSkuDuplicadoNoMesmoTenant() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        assertThrows(SkuDuplicadoException.class,
                () -> produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void atualizaProdutoMantendoOProprioSku() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var atualizado = produtoService.atualizar(criado.id(), request("P0001", new BigDecimal("64.90")));

        assertThat(atualizado.precoVenda()).isEqualByComparingTo("64.90");
    }

    @Test
    void rejeitaAtualizacaoParaSkuDeOutroProduto() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        var segundo = produtoService.criar(TenantContext.get(), request("P0002", new BigDecimal("39.90")));

        assertThrows(SkuDuplicadoException.class,
                () -> produtoService.atualizar(segundo.id(), request("P0001", new BigDecimal("39.90"))));
    }

    @Test
    void atualizaStatusParaInativo() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var atualizado = produtoService.atualizarStatus(criado.id(), StatusProduto.INATIVO);

        assertThat(atualizado.status()).isEqualTo(StatusProduto.INATIVO);
    }

    @Test
    void resumoContaPorStatus() {
        setUpTenant("aurora");
        var a = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));
        produtoService.criar(TenantContext.get(), request("P0002", new BigDecimal("39.90")));
        produtoService.atualizarStatus(a.id(), StatusProduto.INATIVO);

        var resumo = produtoService.resumo();

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.ativos()).isEqualTo(1);
        assertThat(resumo.inativos()).isEqualTo(1);
    }

    @Test
    void listaComFiltroDeBusca() {
        setUpTenant("aurora");
        produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        var pagina = produtoService.listar("camiseta", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).sku()).isEqualTo("P0001");
    }

    @Test
    void excluiProduto() {
        setUpTenant("aurora");
        var criado = produtoService.criar(TenantContext.get(), request("P0001", new BigDecimal("59.90")));

        produtoService.excluir(criado.id());

        assertThrows(ProdutoNaoEncontradoException.class, () -> produtoService.buscarPorId(criado.id()));
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=ProdutoServiceTest`
Expected: PASS (8 tests).

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/produto/ \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoServiceTest.java
git commit -m "feat: add ProdutoService with validation, plus its DTOs and exceptions"
```

---

### Task 3: `ProdutoController`, scoped exception handler, and REST integration tests

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoControllerTest.java`

**Interfaces:**
- Consumes: `ProdutoService` (Task 2), `AuthContextService.Context` (existing, from `com.meshsuite.auth`).
- Produces: `GET/POST/PUT/PATCH/DELETE /api/produtos[/...]` — the full surface Task 4's frontend `src/api/produtos.ts` calls.

- [ ] **Step 1: Write the controller**

```java
package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public Page<ProdutoSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusProduto status,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return produtoService.listar(busca, status, pageable);
    }

    @GetMapping("/resumo")
    public ProdutoResumoResponse resumo() {
        return produtoService.resumo();
    }

    @GetMapping("/{id}")
    public ProdutoResponse buscarPorId(@PathVariable UUID id) {
        return produtoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ProdutoResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                  @Valid @RequestBody ProdutoRequest request) {
        ProdutoResponse response = produtoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ProdutoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ProdutoRequest request) {
        return produtoService.atualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProdutoResponse atualizarStatus(@PathVariable UUID id, @Valid @RequestBody ProdutoStatusRequest request) {
        return produtoService.atualizarStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Write the scoped exception handler**

This handler exists specifically so a SKU-uniqueness race condition (two concurrent requests for the same new SKU slipping past `ProdutoService`'s pre-check) gets a Produto-worded 409 — **without** that same generic exception type leaking a Produto-branded message onto an unrelated domain's unique-constraint violation (e.g. `tenant.codigo`, `usuario.email`, `parceiro.documento`). This is why it's `@RestControllerAdvice(assignableTypes = ProdutoController.class)` in its own file, not a method added to the shared `GlobalExceptionHandler` — exactly mirroring `com.meshsuite.parceiro.ParceiroExceptionHandler`, added during the Cliente/Fornecedor plan's final review to fix the same class of bug.

```java
package com.meshsuite.produto;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = ProdutoController.class)
public class ProdutoExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um produto cadastrado com este SKU"));
    }
}
```

- [ ] **Step 3: Write the controller integration test**

```java
package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
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
class ProdutoControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UsuarioRepository usuarioRepository;
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

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode("senha123"));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);

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
                  "nome": "Camiseta Polo Masculina",
                  "sku": "%s",
                  "precoVenda": 59.90,
                  "quantidadeEstoque": 10,
                  "unidadeMedida": "UN"
                }
                """.formatted(sku);
    }

    @Test
    void createsListsUpdatesAndDeletesProduto() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Camiseta Polo Masculina"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/produtos").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("P0001"));

        mockMvc.perform(put("/api/produtos/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Camiseta Polo Masculina Atualizada",
                                  "sku": "P0001",
                                  "precoVenda": 64.90,
                                  "quantidadeEstoque": 10,
                                  "unidadeMedida": "UN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Camiseta Polo Masculina Atualizada"));

        mockMvc.perform(patch("/api/produtos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INATIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVO"));

        mockMvc.perform(delete("/api/produtos/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/produtos/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateSkuWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingPrecoVendaWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Produto Sem Preço",
                                  "sku": "P0099"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsProduto() throws Exception {
        String tokenA = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/produtos").cookie(cookieA)
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

        mockMvc.perform(get("/api/produtos/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/produtos"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=ProdutoControllerTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Run the full backend suite**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: PASS, no regressions in `auth`/`empresa`/`usuario`/`tenant`/`parceiro` tests.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoControllerTest.java
git commit -m "feat: add ProdutoController with the /api/produtos REST endpoints"
```

---

### Task 4: Frontend API layer — `src/api/produtos.ts`

**Files:**
- Create: `mesh-suite-frontend/src/api/produtos.ts`
- Test: `mesh-suite-frontend/src/api/__tests__/produtos.spec.ts`

**Interfaces:**
- Consumes: `apiClient` (existing, `src/api/client.ts`).
- Produces: `ProdutoRequest`, `ProdutoResponse`, `ProdutoSummary`, `ProdutoResumo`, `Page<T>`, `StatusProduto`, `UnidadeMedida` types, and functions `listarProdutos`, `buscarProduto`, `criarProduto`, `atualizarProduto`, `atualizarStatusProduto`, `excluirProduto`, `buscarResumoProdutos`. Tasks 5-6 consume all of these.

- [ ] **Step 1: Write `src/api/produtos.ts`**

```typescript
import { apiClient } from './client'

export type StatusProduto = 'ATIVO' | 'INATIVO'
export type UnidadeMedida = 'UN' | 'KG' | 'G' | 'L' | 'ML' | 'MT' | 'CM' | 'CX' | 'PC' | 'PAR' | 'DZ'

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

export interface ProdutoSummary {
  id: string
  nome: string
  sku: string
  marca: string
  precoVenda: number
  quantidadeEstoque: number
  status: StatusProduto
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarProdutosParams {
  busca?: string
  status?: StatusProduto
  page?: number
  size?: number
}

export interface ProdutoResumo {
  total: number
  ativos: number
  inativos: number
}

export async function listarProdutos(params: ListarProdutosParams): Promise<Page<ProdutoSummary>> {
  const { data } = await apiClient.get<Page<ProdutoSummary>>('/produtos', { params })
  return data
}

export async function buscarProduto(id: string): Promise<ProdutoResponse> {
  const { data } = await apiClient.get<ProdutoResponse>(`/produtos/${id}`)
  return data
}

export async function criarProduto(payload: ProdutoRequest): Promise<ProdutoResponse> {
  const { data } = await apiClient.post<ProdutoResponse>('/produtos', payload)
  return data
}

export async function atualizarProduto(id: string, payload: ProdutoRequest): Promise<ProdutoResponse> {
  const { data } = await apiClient.put<ProdutoResponse>(`/produtos/${id}`, payload)
  return data
}

export async function atualizarStatusProduto(id: string, status: StatusProduto): Promise<void> {
  await apiClient.patch(`/produtos/${id}/status`, { status })
}

export async function excluirProduto(id: string): Promise<void> {
  await apiClient.delete(`/produtos/${id}`)
}

export async function buscarResumoProdutos(): Promise<ProdutoResumo> {
  const { data } = await apiClient.get<ProdutoResumo>('/produtos/resumo')
  return data
}
```

- [ ] **Step 2: Write the test**

```typescript
import { describe, it, expect, vi } from 'vitest'
import { apiClient } from '../client'
import {
  listarProdutos,
  buscarProduto,
  criarProduto,
  atualizarProduto,
  atualizarStatusProduto,
  excluirProduto,
  buscarResumoProdutos,
} from '../produtos'

vi.mock('../client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('api/produtos', () => {
  it('listarProdutos calls GET /produtos with query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 },
    })

    await listarProdutos({ busca: 'camiseta', status: 'ATIVO' })

    expect(apiClient.get).toHaveBeenCalledWith('/produtos', { params: { busca: 'camiseta', status: 'ATIVO' } })
  })

  it('buscarProduto calls GET /produtos/:id', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: {} })
    await buscarProduto('abc-123')
    expect(apiClient.get).toHaveBeenCalledWith('/produtos/abc-123')
  })

  it('criarProduto calls POST /produtos with the payload', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} })
    const payload = { nome: 'Teste' } as any
    await criarProduto(payload)
    expect(apiClient.post).toHaveBeenCalledWith('/produtos', payload)
  })

  it('atualizarProduto calls PUT /produtos/:id with the payload', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} })
    const payload = { nome: 'Teste' } as any
    await atualizarProduto('abc-123', payload)
    expect(apiClient.put).toHaveBeenCalledWith('/produtos/abc-123', payload)
  })

  it('atualizarStatusProduto calls PATCH /produtos/:id/status', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} })
    await atualizarStatusProduto('abc-123', 'INATIVO')
    expect(apiClient.patch).toHaveBeenCalledWith('/produtos/abc-123/status', { status: 'INATIVO' })
  })

  it('excluirProduto calls DELETE /produtos/:id', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ data: {} })
    await excluirProduto('abc-123')
    expect(apiClient.delete).toHaveBeenCalledWith('/produtos/abc-123')
  })

  it('buscarResumoProdutos calls GET /produtos/resumo', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { total: 0, ativos: 0, inativos: 0 } })
    await buscarResumoProdutos()
    expect(apiClient.get).toHaveBeenCalledWith('/produtos/resumo')
  })
})
```

- [ ] **Step 3: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run src/api/__tests__/produtos.spec.ts`
Expected: PASS (7 tests).

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-frontend/src/api/produtos.ts mesh-suite-frontend/src/api/__tests__/produtos.spec.ts
git commit -m "feat: add typed API layer for produtos"
```

---

### Task 5: `ProdutoFormView.vue` (create/edit)

**Files:**
- Create: `mesh-suite-frontend/src/views/ProdutoFormView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts`

**Interfaces:**
- Consumes: `buscarProduto`, `criarProduto`, `atualizarProduto`, `ProdutoRequest` (Task 4); `AppShell` (existing).
- Produces: routes `produtos-novo` (`/produtos/novo`) and `produtos-editar` (`/produtos/:id/editar`), both rendering this component. Task 6 (list) navigates to both.

- [ ] **Step 1: Write `ProdutoFormView.vue`**

Note the `paraPayload()` helper before every API call: `v-model.number` on an input left blank produces an empty string (not `null`) for optional numeric fields — sending that straight to the backend would fail `BigDecimal` deserialization with a 400 on any blank optional numeric field. `paraPayload()` normalizes empty string/`undefined`/`null` to `null` for every optional numeric field right before the request goes out, while required numeric fields (`precoVenda`, `quantidadeEstoque`) fall back to `0`.

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Produto' : 'Novo Produto'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Nome do Produto *</label>
            <input v-model="form.nome" data-test="nome" />
            <p v-if="erros.nome" class="field-error">{{ erros.nome }}</p>
          </div>
          <div>
            <label class="field-label">Código SKU *</label>
            <input v-model="form.sku" data-test="sku" />
            <p v-if="erros.sku" class="field-error">{{ erros.sku }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Código de Barra (EAN/GTIN)</label>
            <input v-model="form.codigoBarras" placeholder="7891234567890" />
          </div>
          <div>
            <label class="field-label">Marca</label>
            <input v-model="form.marca" />
          </div>
        </div>
        <div class="grid grid-3">
          <div>
            <label class="field-label">Categoria</label>
            <input v-model="form.categoria" />
          </div>
          <div>
            <label class="field-label">Preço de Venda *</label>
            <input v-model.number="form.precoVenda" type="number" step="0.01" min="0" data-test="preco-venda" />
            <p v-if="erros.precoVenda" class="field-error">{{ erros.precoVenda }}</p>
          </div>
          <div>
            <label class="field-label">Preço de Custo</label>
            <input v-model.number="form.precoCusto" type="number" step="0.01" min="0" />
          </div>
        </div>
        <div>
          <label class="field-label">Status</label>
          <select v-model="form.status">
            <option value="ATIVO">Ativo</option>
            <option value="INATIVO">Inativo</option>
          </select>
        </div>
        <div>
          <label class="field-label">Descrição</label>
          <textarea v-model="form.descricao" rows="3" placeholder="Descreva o produto..."></textarea>
        </div>
      </section>

      <div class="grid-cards">
        <section class="card">
          <h2>Estoque</h2>
          <div class="grid grid-2">
            <div>
              <label class="field-label">Qtd. em Estoque</label>
              <input v-model.number="form.quantidadeEstoque" type="number" step="1" min="0" />
            </div>
            <div>
              <label class="field-label">Unidade de Medida</label>
              <select v-model="form.unidadeMedida">
                <option v-for="unidade in UNIDADES" :key="unidade" :value="unidade">{{ unidade }}</option>
              </select>
            </div>
            <div>
              <label class="field-label">Estoque Mínimo</label>
              <input v-model.number="form.estoqueMinimo" type="number" step="1" min="0" />
            </div>
            <div>
              <label class="field-label">Estoque Máximo</label>
              <input v-model.number="form.estoqueMaximo" type="number" step="1" min="0" />
            </div>
          </div>
        </section>

        <section class="card">
          <h2>Pesos &amp; Dimensões</h2>
          <div class="grid grid-2">
            <div>
              <label class="field-label">Peso (kg)</label>
              <input v-model.number="form.peso" type="number" step="0.001" min="0" />
            </div>
            <div>
              <label class="field-label">Comprimento (cm)</label>
              <input v-model.number="form.comprimento" type="number" step="0.01" min="0" />
            </div>
            <div>
              <label class="field-label">Largura (cm)</label>
              <input v-model.number="form.largura" type="number" step="0.01" min="0" />
            </div>
            <div>
              <label class="field-label">Altura (cm)</label>
              <input v-model.number="form.altura" type="number" step="0.01" min="0" />
            </div>
          </div>
        </section>
      </div>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Produto</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  buscarProduto,
  criarProduto,
  atualizarProduto,
  type ProdutoRequest,
  type UnidadeMedida,
} from '@/api/produtos'

const UNIDADES: UnidadeMedida[] = ['UN', 'KG', 'G', 'L', 'ML', 'MT', 'CM', 'CX', 'PC', 'PAR', 'DZ']

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): ProdutoRequest {
  return {
    nome: '',
    sku: '',
    codigoBarras: '',
    marca: '',
    categoria: '',
    precoVenda: 0,
    precoCusto: null,
    status: 'ATIVO',
    descricao: '',
    quantidadeEstoque: 0,
    unidadeMedida: 'UN',
    estoqueMinimo: null,
    estoqueMaximo: null,
    peso: null,
    comprimento: null,
    largura: null,
    altura: null,
  }
}

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

function validar(): boolean {
  erros.nome = form.nome.trim() ? undefined : 'Campo obrigatório'
  erros.sku = form.sku.trim() ? undefined : 'Campo obrigatório'
  erros.precoVenda = Number(form.precoVenda) > 0 ? undefined : 'Informe um preço maior que zero'
  return !erros.nome && !erros.sku && !erros.precoVenda
}

function numeroOuNull(valor: unknown): number | null {
  return valor === '' || valor === null || valor === undefined ? null : Number(valor)
}

function paraPayload(): ProdutoRequest {
  return {
    ...form,
    precoVenda: Number(form.precoVenda) || 0,
    precoCusto: numeroOuNull(form.precoCusto),
    quantidadeEstoque: Number(form.quantidadeEstoque) || 0,
    estoqueMinimo: numeroOuNull(form.estoqueMinimo),
    estoqueMaximo: numeroOuNull(form.estoqueMaximo),
    peso: numeroOuNull(form.peso),
    comprimento: numeroOuNull(form.comprimento),
    largura: numeroOuNull(form.largura),
    altura: numeroOuNull(form.altura),
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
      await atualizarProduto(id, payload)
    } else {
      await criarProduto(payload)
    }
    router.push({ name: 'produtos' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um produto cadastrado com este SKU.'
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
  router.push({ name: 'produtos' })
}
</script>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-family: var(--pm-font);
}

.grid-cards {
  display: flex;
  gap: 12px;
}

.grid-cards .card {
  flex: 1;
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

.grid-3 {
  grid-template-columns: 1fr 1fr 1fr;
}

.field-label {
  display: block;
  font-size: 12px;
  color: var(--pm-text-mid);
  margin-bottom: 4px;
}

input,
select,
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

- [ ] **Step 2: Register the routes**

In `mesh-suite-frontend/src/router/index.ts`, add the import and the two routes:

```typescript
import ProdutoFormView from '@/views/ProdutoFormView.vue'
```
```typescript
    { path: '/produtos/novo', name: 'produtos-novo', component: ProdutoFormView },
    { path: '/produtos/:id/editar', name: 'produtos-editar', component: ProdutoFormView },
```

- [ ] **Step 3: Write the test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProdutoFormView from '@/views/ProdutoFormView.vue'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/produtos')

function mountWithRouter(path = '/produtos/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: { template: '<div />' } },
      { path: '/produtos/novo', name: 'produtos-novo', component: ProdutoFormView },
      { path: '/produtos/:id/editar', name: 'produtos-editar', component: ProdutoFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ProdutoFormView, { global: { plugins: [router] } }),
  }))
}

describe('ProdutoFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows required-field errors when nome/sku are blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(produtosApi.criarProduto).not.toHaveBeenCalled()
  })

  it('requires a preço de venda greater than zero', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Informe um preço maior que zero')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(produtosApi.criarProduto).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(produtosApi.criarProduto).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('produtos')
  })

  it('sends null (not empty string) for blank optional numeric fields', async () => {
    vi.mocked(produtosApi.criarProduto).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(produtosApi.criarProduto).mock.calls[0][0]
    expect(payload.precoCusto).toBeNull()
    expect(payload.estoqueMinimo).toBeNull()
    expect(payload.peso).toBeNull()
  })

  it('shows a conflict message on duplicate SKU (409)', async () => {
    vi.mocked(produtosApi.criarProduto).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um produto cadastrado com este SKU')
  })

  it('loads existing produto data in edit mode', async () => {
    vi.mocked(produtosApi.buscarProduto).mockResolvedValue({
      id: 'abc-123', nome: 'Camiseta Polo', sku: 'P0001', codigoBarras: '', marca: '', categoria: '',
      precoVenda: 59.9, precoCusto: null, status: 'ATIVO', descricao: '', quantidadeEstoque: 10,
      unidadeMedida: 'UN', estoqueMinimo: null, estoqueMaximo: null, peso: null, comprimento: null,
      largura: null, altura: null,
    } as any)

    const { wrapper } = await mountWithRouter('/produtos/abc-123/editar')
    await flushPromises()

    expect(produtosApi.buscarProduto).toHaveBeenCalledWith('abc-123')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Camiseta Polo')
  })

  it('shows an error message when loading produto data fails in edit mode', async () => {
    vi.mocked(produtosApi.buscarProduto).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/produtos/abc-123/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do produto.')
  })
})
```

- [ ] **Step 4: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/ProdutoFormView.spec.ts`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/ProdutoFormView.vue mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts
git commit -m "feat: add ProdutoFormView for creating and editing produtos"
```

---

### Task 6: `ProdutosListView.vue` + activate the sidebar "Produtos" item

**Files:**
- Create: `mesh-suite-frontend/src/views/ProdutosListView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/ProdutosListView.spec.ts`

**Interfaces:**
- Consumes: `listarProdutos`, `buscarResumoProdutos`, `atualizarStatusProduto`, `excluirProduto`, `ProdutoSummary`, `ProdutoResumo`, `Page`, `StatusProduto` (Task 4); `AppShell` (existing); routes `produtos-novo`/`produtos-editar` (Task 5).
- Produces: route `produtos` (`/produtos`), the sidebar's entry point into this feature.

- [ ] **Step 1: Write `ProdutosListView.vue`**

```vue
<template>
  <AppShell title="Produtos">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar produto por nome ou SKU..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="ATIVO">Ativo</option>
        <option value="INATIVO">Inativo</option>
      </select>
      <button type="button" class="btn-primary" data-test="novo-produto" @click="novoProduto">+ Novo Produto</button>
    </div>

    <div v-if="resumo" class="resumo">
      <span class="resumo-item">{{ resumo.total }} Total</span>
      <span class="resumo-item resumo-ativo">{{ resumo.ativos }} Ativos</span>
      <span class="resumo-item resumo-inativo">{{ resumo.inativos }} Inativos</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Código</th>
            <th>Produto</th>
            <th>Marca</th>
            <th>Preço de Venda</th>
            <th>Estoque</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="produto in pagina.content" :key="produto.id">
            <td>{{ produto.sku }}</td>
            <td>{{ produto.nome }}</td>
            <td>{{ produto.marca }}</td>
            <td>{{ formatarPreco(produto.precoVenda) }}</td>
            <td>{{ produto.quantidadeEstoque }}</td>
            <td><span class="badge" :class="`badge-${produto.status}`">{{ statusLabel(produto.status) }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(produto.id, $event)"
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
        v-if="produtoAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarProduto(produtoAcoesAtual.id)">Editar</div>
        <div @click="alternarStatus(produtoAcoesAtual)">
          {{ produtoAcoesAtual.status === 'INATIVO' ? 'Ativar' : 'Inativar' }}
        </div>
        <div class="acao-excluir" @click="excluir(produtoAcoesAtual)">Excluir</div>
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
  listarProdutos,
  buscarResumoProdutos,
  atualizarStatusProduto,
  excluirProduto,
  type ProdutoSummary,
  type ProdutoResumo,
  type Page as ApiPage,
  type StatusProduto,
} from '@/api/produtos'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<ProdutoSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<ProdutoResumo | null>(null)
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const produtoAcoesAtual = computed(() =>
  pagina.value.content.find((p) => p.id === acoesAbertas.value) ?? null,
)

function statusLabel(status: StatusProduto) {
  return { ATIVO: 'Ativo', INATIVO: 'Inativo' }[status]
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listarProdutos({
      busca: filtros.busca || undefined,
      status: (filtros.status || undefined) as StatusProduto | undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de produtos.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await buscarResumoProdutos()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de produtos.'
  }
}

function novoProduto() {
  router.push({ name: 'produtos-novo' })
}

function editarProduto(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'produtos-editar', params: { id } })
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

async function alternarStatus(produto: ProdutoSummary) {
  acoesAbertas.value = null
  erro.value = ''
  const novoStatus = produto.status === 'INATIVO' ? 'ATIVO' : 'INATIVO'
  try {
    await atualizarStatusProduto(produto.id, novoStatus)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status.'
  }
}

async function excluir(produto: ProdutoSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir o produto "${produto.nome}"?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirProduto(produto.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir o produto.'
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

.resumo-ativo {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.resumo-inativo {
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

- [ ] **Step 2: Register the route**

In `mesh-suite-frontend/src/router/index.ts`, add the import and route:

```typescript
import ProdutosListView from '@/views/ProdutosListView.vue'
```
```typescript
    { path: '/produtos', name: 'produtos', component: ProdutosListView },
```

- [ ] **Step 3: Activate the sidebar item**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, change the Produtos nav item's `route` from `null` to `'/produtos'` (the `isActive` function's `startsWith` handling already generalizes to any non-`/` route, including this one — no further change needed there):

```typescript
  { icon: '📦', label: 'Produtos', route: '/produtos' },
```

- [ ] **Step 4: Write the test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProdutosListView from '@/views/ProdutosListView.vue'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/produtos')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: ProdutosListView },
      { path: '/produtos/novo', name: 'produtos-novo', component: { template: '<div />' } },
      { path: '/produtos/:id/editar', name: 'produtos-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/produtos')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(ProdutosListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const produtoBase = {
  id: 'p1', nome: 'Camiseta Polo', sku: 'P0001', marca: 'Marca Alpha',
  precoVenda: 59.9, quantidadeEstoque: 10, status: 'ATIVO' as const,
}

describe('ProdutosListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(produtosApi.buscarResumoProdutos).mockResolvedValue({ total: 1, ativos: 1, inativos: 0 })
  })

  it('loads and displays the product list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).toContain('1 Total')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('camiseta')
    await flushPromises()

    expect(produtosApi.listarProdutos).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'camiseta' }))
  })

  it('navigates to the create form when "+ Novo Produto" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-produto"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-novo')
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('toggles a product status via the Ações menu', async () => {
    vi.mocked(produtosApi.atualizarStatusProduto).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('.btn-acoes').trigger('click')
    await wrapper.findAll('.dropdown-acoes div')[1].trigger('click')
    await flushPromises()

    expect(produtosApi.atualizarStatusProduto).toHaveBeenCalledWith('p1', 'INATIVO')
  })

  it('shows an error message when loading the product list fails', async () => {
    vi.mocked(produtosApi.listarProdutos).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de produtos.')
  })
})
```

- [ ] **Step 5: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/ProdutosListView.spec.ts`
Expected: PASS (6 tests).

- [ ] **Step 6: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: PASS, no regressions in the auth/dashboard/cliente tests.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/views/ProdutosListView.vue mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/views/__tests__/ProdutosListView.spec.ts
git commit -m "feat: add ProdutosListView and activate the sidebar Produtos item"
```

---

## Final verification

- [ ] Run `cd mesh-suite-backend && ./mvnw test` — full backend suite passes (existing `auth`/`empresa`/`usuario`/`tenant`/`parceiro` tests + new `produto` tests).
- [ ] Run `cd mesh-suite-frontend && npx vitest run` — full frontend suite passes.
- [ ] Run `cd mesh-suite-frontend && npm run build` — production build succeeds.
- [ ] Run the app (`./devup.sh`), log in, click "Produtos" in the sidebar: create a product (Nome, SKU, Preço de Venda are the only required fields), confirm it appears in the list with the right status badge and formatted price, use "Ações → Editar" to change a field and save, use "Ações → Ativar/Inativar" to toggle status, confirm the dropdown menu is fully visible (not clipped) regardless of which row it's opened from, then "Ações → Excluir" to remove the test product.
