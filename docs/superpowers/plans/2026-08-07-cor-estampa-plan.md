# Cor da Estampa Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the combined Cor/Estampa cadastro (documented in PRD-13 as two separate auxiliary registers, but built here as one, matching `layout/wireframes/13 - Cores e Estampas-v1.html`) and link `Produto` to it, mirroring the Categoria de Produto slice exactly.

**Architecture:** `CorEstampa` is a standalone RLS-scoped entity in the existing `com.meshsuite.produto` package (Portuguese naming, same convention as `Categoria`). `Produto` gets a second optional `@ManyToOne` reference alongside its existing `categoria` field. Backend and frontend both replicate the Categoria de Produto slice's structure file-for-file, with a `dataVigencia` field added throughout (required by the wireframe, absent from Categoria).

**Tech Stack:** Spring Boot 3.4.5 / Java 21, Spring Data JPA, PostgreSQL 16 (RLS), Flyway, Vue 3 + TypeScript + Vite.

## Global Constraints

- New backend code lives in `com.meshsuite.produto` package, in **Portuguese** (`CorEstampa`, `CorEstampaService`, `CorEstampaController`), exactly mirroring `Categoria`'s existing files.
- RLS pattern: `cor_estampa` table gets its own `tenant_id` column, `ENABLE`+`FORCE ROW LEVEL SECURITY`, `USING`-only policy — identical to `categoria`.
- Permission: reuse `Module.PRODUCT` (already exists) — no new `Module` value, no migration touching `user_permission_module_check`.
- `nome` is unique per tenant — DB unique index `(tenant_id, nome)` AND a pre-check in the service (same double-layer pattern as `Categoria.nome`/`Produto.sku`).
- `dataVigencia` is a required `LocalDate` field (the wireframe marks it mandatory) with **no computed business behavior** — purely informational in this slice, per the approved spec.
- Deleting a `CorEstampa` referenced by any `Produto` is rejected with a clear error message naming the count — same 3-layer defense as Categoria (DB FK restrict, service pre-check, controller 400).
- **The N+1 query mistake from the Categoria slice must not repeat here.** `CorEstampaService.listar()` must count linked produtos via a single batched query from the start (`ProdutoRepository.countByCorEstampaIdIn`, mirroring the already-fixed `countByCategoriaIdIn` pattern) — never one `countByCorEstampaId` call per row in a page.
- Exception handling: domain-specific exceptions (`CorEstampaNaoEncontradaException`, `CorEstampaNomeDuplicadoException`, `CorEstampaEmUsoException`) are registered in the existing shared `com.meshsuite.auth.GlobalExceptionHandler`, matching the pattern already used for `Categoria*`/`Produto*` exceptions. A small `CorEstampaExceptionHandler` scoped to `CorEstampaController` handles only the `DataIntegrityViolationException` race-condition fallback, mirroring `CategoriaExceptionHandler`.
- New backend integration tests use tenant `codigo` values distinct from `"aurora"`/`"boreal"` (e.g. `"aurora-corest"`) — same mitigation already applied to Categoria's tests, for the documented pre-existing `DevSeedTest` test-ordering issue.
- Next available Flyway migration versions are **V23** and **V24** (V19 is `create_municipio`, V21/V22 are Categoria's — confirm with `ls mesh-suite-backend/src/main/resources/db/migration/` before creating files, in case another slice has landed since this plan was written).

---

### Task 1: `CorEstampa` domain model, service, and `Produto` integration (backend)

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V23__create_cor_estampa.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V24__add_cor_estampa_to_produto.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampa.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaNaoEncontradaException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaNomeDuplicadoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaEmUsoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/Produto.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoRepository.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoRequest.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoResponse.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/CorEstampaRepositoryTest.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/CorEstampaServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks — standalone, mirroring `Categoria`. `Produto` already has a `categoria` field (from the earlier slice) which this task does not touch except to add a sibling field.
- Produces: `CorEstampa` entity (`id, tenantId, nome, dataVigencia, descricao, ativo, criadoEm`); `CorEstampaRepository extends JpaRepository<CorEstampa, UUID>, JpaSpecificationExecutor<CorEstampa>` with `existsByNome`/`existsByNomeAndIdNot`; `CorEstampaService` with `listar(String busca, Boolean ativo, Pageable): Page<CorEstampaResponse>`, `buscarPorId(UUID): CorEstampaResponse`, `criar(UUID tenantId, CorEstampaRequest): CorEstampaResponse`, `atualizar(UUID id, CorEstampaRequest): CorEstampaResponse`, `excluir(UUID id): void`; `CorEstampaRequest(String nome, LocalDate dataVigencia, String descricao, Boolean ativo)`; `CorEstampaResponse(UUID id, String nome, LocalDate dataVigencia, String descricao, Boolean ativo, Long produtosVinculados, Instant criadoEm)`. `Produto.corEstampa` (new `CorEstampa` field, sibling to the existing `categoria` field). `ProdutoRequest.corEstampaId: UUID` (new field, appended after `categoriaId`). `ProdutoResponse.corEstampaId: UUID, corEstampaNome: String` (new fields, appended after `categoriaNome`). Task 2 (controller) consumes `CorEstampaService` directly. Task 4 (Produto frontend) consumes the new `ProdutoRequest`/`ProdutoResponse` shape.

- [ ] **Step 1: Confirm the next free migration versions**

Run: `ls mesh-suite-backend/src/main/resources/db/migration/ | sort -V | tail -5`
Expected: highest existing version is V22. If a newer migration already exists (e.g. from another slice merged after this plan was written), use the next two free version numbers instead of V23/V24 throughout this task, and note the substitution in your report.

- [ ] **Step 2: Write the migration creating `cor_estampa`**

```sql
CREATE TABLE cor_estampa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(100) NOT NULL,
    data_vigencia DATE NOT NULL,
    descricao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_cor_estampa_tenant_nome ON cor_estampa(tenant_id, nome);
CREATE INDEX idx_cor_estampa_tenant_id ON cor_estampa(tenant_id);

ALTER TABLE cor_estampa ENABLE ROW LEVEL SECURITY;
ALTER TABLE cor_estampa FORCE ROW LEVEL SECURITY;

CREATE POLICY cor_estampa_tenant_isolation ON cor_estampa
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

Save as `mesh-suite-backend/src/main/resources/db/migration/V23__create_cor_estampa.sql`.

- [ ] **Step 3: Write the migration adding the FK column to `produto`**

```sql
ALTER TABLE produto ADD COLUMN cor_estampa_id UUID REFERENCES cor_estampa(id);
```

Save as `mesh-suite-backend/src/main/resources/db/migration/V24__add_cor_estampa_to_produto.sql`.

- [ ] **Step 4: Write `CorEstampa.java`**

```java
package com.meshsuite.produto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cor_estampa")
@Getter
@Setter
public class CorEstampa {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "data_vigencia", nullable = false)
    private LocalDate dataVigencia;

    @Column(length = 255)
    private String descricao;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
```

- [ ] **Step 5: Write `CorEstampaRepository.java`**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CorEstampaRepository extends JpaRepository<CorEstampa, UUID>, JpaSpecificationExecutor<CorEstampa> {
    boolean existsByNome(String nome);
    boolean existsByNomeAndIdNot(String nome, UUID id);
}
```

- [ ] **Step 6: Write `CorEstampaSpecifications.java`**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.domain.Specification;

public final class CorEstampaSpecifications {

    private CorEstampaSpecifications() {
    }

    public static Specification<CorEstampa> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nome")), termo);
    }

    public static Specification<CorEstampa> comAtivo(Boolean ativo) {
        if (ativo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
    }
}
```

- [ ] **Step 7: Write the three exception classes**

```java
package com.meshsuite.produto;

public class CorEstampaNaoEncontradaException extends RuntimeException {
    public CorEstampaNaoEncontradaException() {
        super("Cor/Estampa não encontrada");
    }
}
```

Save as `CorEstampaNaoEncontradaException.java`.

```java
package com.meshsuite.produto;

public class CorEstampaNomeDuplicadoException extends RuntimeException {
    public CorEstampaNomeDuplicadoException() {
        super("Já existe uma cor/estampa cadastrada com este nome");
    }
}
```

Save as `CorEstampaNomeDuplicadoException.java`.

```java
package com.meshsuite.produto;

public class CorEstampaEmUsoException extends RuntimeException {
    public CorEstampaEmUsoException(long quantidadeProdutos) {
        super("Não é possível excluir: " + quantidadeProdutos + " produto(s) usam esta cor/estampa");
    }
}
```

Save as `CorEstampaEmUsoException.java`.

- [ ] **Step 8: Write the DTOs**

```java
package com.meshsuite.produto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CorEstampaRequest(
        @NotBlank String nome,
        @NotNull LocalDate dataVigencia,
        String descricao,
        Boolean ativo) {
}
```

Save as `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaRequest.java`.

```java
package com.meshsuite.produto.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CorEstampaResponse(
        UUID id,
        String nome,
        LocalDate dataVigencia,
        String descricao,
        Boolean ativo,
        Long produtosVinculados,
        Instant criadoEm) {
}
```

Save as `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaResponse.java`.

- [ ] **Step 9: Modify `Produto.java` — add the `corEstampa` field**

Add this field right after the existing `categoria` field (before `precoVenda`):

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cor_estampa_id")
    private CorEstampa corEstampa;
```

`CorEstampa` is in the same package (`com.meshsuite.produto`), so no new import is needed; `ManyToOne`/`JoinColumn` are already covered by the existing `jakarta.persistence.*` wildcard import.

- [ ] **Step 10: Modify `ProdutoRepository.java` — add the in-use check and batched count query**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<Produto, UUID>, JpaSpecificationExecutor<Produto> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    long countByStatus(StatusProduto status);
    long countByCategoriaId(UUID categoriaId);
    long countByCorEstampaId(UUID corEstampaId);

    @Query("SELECT p.categoria.id AS categoriaId, COUNT(p) AS total FROM Produto p " +
            "WHERE p.categoria.id IN :categoriaIds GROUP BY p.categoria.id")
    List<CategoriaProdutoCount> countByCategoriaIdIn(@Param("categoriaIds") Collection<UUID> categoriaIds);

    @Query("SELECT p.corEstampa.id AS corEstampaId, COUNT(p) AS total FROM Produto p " +
            "WHERE p.corEstampa.id IN :corEstampaIds GROUP BY p.corEstampa.id")
    List<CorEstampaProdutoCount> countByCorEstampaIdIn(@Param("corEstampaIds") Collection<UUID> corEstampaIds);

    interface CategoriaProdutoCount {
        UUID getCategoriaId();
        Long getTotal();
    }

    interface CorEstampaProdutoCount {
        UUID getCorEstampaId();
        Long getTotal();
    }
}
```

This is the full file — it adds `countByCorEstampaId`, the `countByCorEstampaIdIn` batched query, and the `CorEstampaProdutoCount` projection alongside the existing `Categoria` ones (unchanged).

- [ ] **Step 11: Modify `ProdutoRequest.java` — add `corEstampaId`**

Find:

```java
public record ProdutoRequest(
        @NotBlank String nome,
        @NotBlank String sku,
        String codigoBarras,
        String marca,
        UUID categoriaId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal precoVenda,
```

Replace with:

```java
public record ProdutoRequest(
        @NotBlank String nome,
        @NotBlank String sku,
        String codigoBarras,
        String marca,
        UUID categoriaId,
        UUID corEstampaId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal precoVenda,
```

(The rest of the record — from `precoCusto` through `altura` — is unchanged.)

- [ ] **Step 12: Modify `ProdutoResponse.java` — add `corEstampaId`/`corEstampaNome`**

Find:

```java
public record ProdutoResponse(
        UUID id,
        String nome,
        String sku,
        String codigoBarras,
        String marca,
        UUID categoriaId,
        String categoriaNome,
        BigDecimal precoVenda,
```

Replace with:

```java
public record ProdutoResponse(
        UUID id,
        String nome,
        String sku,
        String codigoBarras,
        String marca,
        UUID categoriaId,
        String categoriaNome,
        UUID corEstampaId,
        String corEstampaNome,
        BigDecimal precoVenda,
```

(The rest of the record is unchanged.)

- [ ] **Step 13: Modify `ProdutoService.java` — wire in `CorEstampaRepository`**

Find:

```java
    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

    public ProdutoService(ProdutoRepository produtoRepository, CategoriaRepository categoriaRepository) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
    }
```

Replace with:

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

Find, in `aplicar(...)`:

```java
        produto.setCategoria(request.categoriaId() != null
                ? categoriaRepository.findById(request.categoriaId()).orElseThrow(CategoriaNaoEncontradaException::new)
                : null);
```

Replace with:

```java
        produto.setCategoria(request.categoriaId() != null
                ? categoriaRepository.findById(request.categoriaId()).orElseThrow(CategoriaNaoEncontradaException::new)
                : null);
        produto.setCorEstampa(request.corEstampaId() != null
                ? corEstampaRepository.findById(request.corEstampaId()).orElseThrow(CorEstampaNaoEncontradaException::new)
                : null);
```

Find, in `toResponse(...)`:

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

Replace with:

```java
    private ProdutoResponse toResponse(Produto p) {
        return new ProdutoResponse(
                p.getId(), p.getNome(), p.getSku(), p.getCodigoBarras(), p.getMarca(),
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getNome() : null,
                p.getCorEstampa() != null ? p.getCorEstampa().getId() : null,
                p.getCorEstampa() != null ? p.getCorEstampa().getNome() : null,
                p.getPrecoVenda(), p.getPrecoCusto(), p.getStatus(), p.getDescricao(), p.getQuantidadeEstoque(),
                p.getUnidadeMedida(), p.getEstoqueMinimo(), p.getEstoqueMaximo(), p.getPeso(), p.getComprimento(),
                p.getLargura(), p.getAltura());
    }
```

- [ ] **Step 14: Write `CorEstampaService.java` — batched count from the start**

```java
package com.meshsuite.produto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.produto.dto.CorEstampaRequest;
import com.meshsuite.produto.dto.CorEstampaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CorEstampaService {

    private final CorEstampaRepository corEstampaRepository;
    private final ProdutoRepository produtoRepository;

    public CorEstampaService(CorEstampaRepository corEstampaRepository, ProdutoRepository produtoRepository) {
        this.corEstampaRepository = corEstampaRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<CorEstampaResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        Specification<CorEstampa> spec = Specification.allOf(
                CorEstampaSpecifications.comBusca(busca),
                CorEstampaSpecifications.comAtivo(ativo));
        Page<CorEstampa> pagina = corEstampaRepository.findAll(spec, pageable);

        List<UUID> ids = pagina.getContent().stream().map(CorEstampa::getId).toList();
        Map<UUID, Long> contagens = ids.isEmpty()
                ? Map.of()
                : produtoRepository.countByCorEstampaIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ProdutoRepository.CorEstampaProdutoCount::getCorEstampaId,
                                ProdutoRepository.CorEstampaProdutoCount::getTotal));

        return pagina.map(corEstampa -> toResponse(corEstampa, contagens.getOrDefault(corEstampa.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public CorEstampaResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public CorEstampaResponse criar(UUID tenantId, CorEstampaRequest request) {
        validarNome(request.nome(), null);

        CorEstampa corEstampa = new CorEstampa();
        corEstampa.setTenantId(tenantId);
        aplicar(corEstampa, request);
        return toResponse(corEstampaRepository.saveAndFlush(corEstampa));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public CorEstampaResponse atualizar(UUID id, CorEstampaRequest request) {
        validarNome(request.nome(), id);

        CorEstampa corEstampa = buscarEntidadePorId(id);
        aplicar(corEstampa, request);
        return toResponse(corEstampaRepository.saveAndFlush(corEstampa));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        CorEstampa corEstampa = buscarEntidadePorId(id);
        long vinculados = produtoRepository.countByCorEstampaId(id);
        if (vinculados > 0) {
            throw new CorEstampaEmUsoException(vinculados);
        }
        corEstampaRepository.delete(corEstampa);
    }

    private CorEstampa buscarEntidadePorId(UUID id) {
        return corEstampaRepository.findById(id).orElseThrow(CorEstampaNaoEncontradaException::new);
    }

    private void validarNome(String nome, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? corEstampaRepository.existsByNome(nome)
                : corEstampaRepository.existsByNomeAndIdNot(nome, idAtual);
        if (duplicado) {
            throw new CorEstampaNomeDuplicadoException();
        }
    }

    private void aplicar(CorEstampa corEstampa, CorEstampaRequest request) {
        corEstampa.setNome(request.nome());
        corEstampa.setDataVigencia(request.dataVigencia());
        corEstampa.setDescricao(request.descricao());
        corEstampa.setAtivo(request.ativo() != null ? request.ativo() : true);
    }

    private CorEstampaResponse toResponse(CorEstampa corEstampa) {
        return toResponse(corEstampa, produtoRepository.countByCorEstampaId(corEstampa.getId()));
    }

    private CorEstampaResponse toResponse(CorEstampa corEstampa, long produtosVinculados) {
        return new CorEstampaResponse(
                corEstampa.getId(), corEstampa.getNome(), corEstampa.getDataVigencia(), corEstampa.getDescricao(),
                corEstampa.getAtivo(), produtosVinculados, corEstampa.getCriadoEm());
    }
}
```

- [ ] **Step 15: Write `CorEstampaRepositoryTest.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorEstampaRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired CorEstampaRepository corEstampaRepository;
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

    private CorEstampa novaCorEstampa(UUID tenantId, String nome) {
        CorEstampa c = new CorEstampa();
        c.setTenantId(tenantId);
        c.setNome(nome);
        c.setDataVigencia(LocalDate.of(2026, 1, 1));
        return c;
    }

    @Test
    @Transactional
    void savesCorEstampaWithDefaults() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());

        CorEstampa saved = corEstampaRepository.saveAndFlush(novaCorEstampa(tenant.getId(), "Azul Marinho"));
        entityManager.clear();

        CorEstampa reloaded = corEstampaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAtivo()).isTrue();
        assertThat(reloaded.getDataVigencia()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @Transactional
    void nomeMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());

        corEstampaRepository.saveAndFlush(novaCorEstampa(tenant.getId(), "Azul Marinho"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> corEstampaRepository.saveAndFlush(novaCorEstampa(tenant.getId(), "Azul Marinho")));
    }

    @Test
    @Transactional
    void sameNomeAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-corest");
        Tenant tenantB = createTenant("boreal-corest");

        setTenantContext(tenantA.getId());
        corEstampaRepository.saveAndFlush(novaCorEstampa(tenantA.getId(), "Azul Marinho"));

        setTenantContext(tenantB.getId());
        CorEstampa saved = corEstampaRepository.saveAndFlush(novaCorEstampa(tenantB.getId(), "Azul Marinho"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());
        corEstampaRepository.saveAndFlush(novaCorEstampa(tenant.getId(), "Azul Marinho"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM cor_estampa")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
```

- [ ] **Step 16: Run the repository test to verify it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=CorEstampaRepositoryTest`
Expected: PASS (4/4).

- [ ] **Step 17: Write `CorEstampaServiceTest.java`, including the batched-count test from the start**

```java
package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.produto.dto.CorEstampaRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorEstampaServiceTest extends AbstractIntegrationTest {

    @Autowired CorEstampaService corEstampaService;
    @Autowired ProdutoService produtoService;
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

    private CorEstampaRequest request(String nome) {
        return new CorEstampaRequest(nome, LocalDate.of(2026, 1, 1), "Descrição de teste", null);
    }

    @Test
    @Transactional
    void criaERecuperaCorEstampa() {
        setUpTenant("aurora-corest");

        var criada = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));

        var buscada = corEstampaService.buscarPorId(criada.id());
        assertThat(buscada.nome()).isEqualTo("Azul Marinho");
        assertThat(buscada.dataVigencia()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(buscada.ativo()).isTrue();
        assertThat(buscada.produtosVinculados()).isEqualTo(0L);
    }

    @Test
    @Transactional
    void rejectsDuplicateNomeOnCreate() {
        setUpTenant("aurora-corest");
        corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));

        assertThatThrownBy(() -> corEstampaService.criar(TenantContext.get(), request("Azul Marinho")))
                .isInstanceOf(CorEstampaNomeDuplicadoException.class);
    }

    @Test
    @Transactional
    void rejectsDuplicateNomeOnUpdateAgainstAnotherCorEstampa() {
        setUpTenant("aurora-corest");
        corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));
        var outra = corEstampaService.criar(TenantContext.get(), request("Vermelho Ferrari"));

        assertThatThrownBy(() -> corEstampaService.atualizar(outra.id(), request("Azul Marinho")))
                .isInstanceOf(CorEstampaNomeDuplicadoException.class);
    }

    @Test
    @Transactional
    void allowsUpdatingACorEstampaWithoutChangingItsOwnNome() {
        setUpTenant("aurora-corest");
        var criada = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));

        var atualizada = corEstampaService.atualizar(criada.id(),
                new CorEstampaRequest("Azul Marinho", LocalDate.of(2026, 3, 1), "Descrição nova", false));

        assertThat(atualizada.descricao()).isEqualTo("Descrição nova");
        assertThat(atualizada.dataVigencia()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(atualizada.ativo()).isFalse();
    }

    @Test
    @Transactional
    void deletesUnusedCorEstampa() {
        setUpTenant("aurora-corest");
        var criada = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));

        corEstampaService.excluir(criada.id());

        assertThatThrownBy(() -> corEstampaService.buscarPorId(criada.id()))
                .isInstanceOf(CorEstampaNaoEncontradaException.class);
    }

    @Test
    @Transactional
    void rejectsDeletingACorEstampaInUseByAProduto() {
        setUpTenant("aurora-corest");
        var corEstampa = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Polo", "P0001", null, null, null, corEstampa.id(),
                new BigDecimal("59.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));

        assertThatThrownBy(() -> corEstampaService.excluir(corEstampa.id()))
                .isInstanceOf(CorEstampaEmUsoException.class);
    }

    @Test
    @Transactional
    void listFiltersByAtivo() {
        setUpTenant("aurora-corest");
        corEstampaService.criar(TenantContext.get(), new CorEstampaRequest("Azul Marinho", LocalDate.of(2026, 1, 1), null, true));
        corEstampaService.criar(TenantContext.get(), new CorEstampaRequest("Descontinuada", LocalDate.of(2025, 1, 1), null, false));

        var ativas = corEstampaService.listar(null, true, PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("nome").containsExactly("Azul Marinho");
    }

    @Test
    @Transactional
    void listAggregatesProdutosVinculadosPerCorEstampaInASingleBatch() {
        setUpTenant("aurora-corest");
        var azul = corEstampaService.criar(TenantContext.get(), request("Azul Marinho"));
        var vermelho = corEstampaService.criar(TenantContext.get(), request("Vermelho Ferrari"));
        var semProdutos = corEstampaService.criar(TenantContext.get(), request("Preto"));

        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Polo", "P0001", null, null, null, azul.id(),
                new BigDecimal("59.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Camiseta Regata", "P0002", null, null, null, azul.id(),
                new BigDecimal("39.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));
        produtoService.criar(TenantContext.get(), new com.meshsuite.produto.dto.ProdutoRequest(
                "Calça Jeans", "P0003", null, null, null, vermelho.id(),
                new BigDecimal("119.90"), null, StatusProduto.ATIVO, null,
                new BigDecimal("10"), UnidadeMedida.UN, null, null, null, null, null, null));

        var pagina = corEstampaService.listar(null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(azul.id())).first()
                .satisfies(c -> assertThat(c.produtosVinculados()).isEqualTo(2L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(vermelho.id())).first()
                .satisfies(c -> assertThat(c.produtosVinculados()).isEqualTo(1L));
        assertThat(pagina.getContent())
                .filteredOn(c -> c.id().equals(semProdutos.id())).first()
                .satisfies(c -> assertThat(c.produtosVinculados()).isEqualTo(0L));
    }
}
```

- [ ] **Step 18: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=CorEstampaServiceTest,CorEstampaRepositoryTest,ProdutoServiceTest,ProdutoRepositoryTest,CategoriaServiceTest,CategoriaRepositoryTest`
Expected: PASS, all tests — confirms `CorEstampa`'s own tests pass AND the `ProdutoService`/`ProdutoRepository` changes didn't break existing Categoria or Produto behavior.

- [ ] **Step 19: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw clean test`
Expected: no NEW failures introduced by this task's diff. **Use `clean test`, not plain `test`** — a stale `target/classes` from a prior build can make Flyway see leftover migration files that no longer exist in source, producing a false "Found more than one migration with version N" failure (this happened during the Categoria de Produto slice). A known, pre-existing, order-dependent failure in `com.meshsuite.payable.*` (documented during the Financeiro Mínimo sub-project — `DevSeedTest`/`tenant_codigo_key` collision) may still appear; that is not caused by this task and is not a regression to chase here.

- [ ] **Step 20: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V23__create_cor_estampa.sql \
        mesh-suite-backend/src/main/resources/db/migration/V24__add_cor_estampa_to_produto.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampa.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaRepository.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaSpecifications.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaNaoEncontradaException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaNomeDuplicadoException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaEmUsoException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaRequest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/CorEstampaResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/Produto.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoRepository.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoRequest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/ProdutoResponse.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/CorEstampaRepositoryTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/CorEstampaServiceTest.java
git commit -m "feat(cor-estampa): add CorEstampa domain model, service, and Produto FK integration"
```

---

### Task 2: `CorEstampaController`, exception wiring, and integration tests (backend)

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaExceptionHandler.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/CorEstampaControllerTest.java`

**Interfaces:**
- Consumes: `CorEstampaService.listar/buscarPorId/criar/atualizar/excluir` (Task 1), `CorEstampaRequest`/`CorEstampaResponse` (Task 1).
- Produces: `GET/POST/PUT/DELETE /api/cores-estampas` — consumed directly by Task 3 (CorEstampa frontend).

- [ ] **Step 1: Write `CorEstampaController.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.CorEstampaRequest;
import com.meshsuite.produto.dto.CorEstampaResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cores-estampas")
public class CorEstampaController {

    private final CorEstampaService corEstampaService;

    public CorEstampaController(CorEstampaService corEstampaService) {
        this.corEstampaService = corEstampaService;
    }

    @GetMapping
    public Page<CorEstampaResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return corEstampaService.listar(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public CorEstampaResponse buscarPorId(@PathVariable UUID id) {
        return corEstampaService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<CorEstampaResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                      @Valid @RequestBody CorEstampaRequest request) {
        CorEstampaResponse response = corEstampaService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public CorEstampaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CorEstampaRequest request) {
        return corEstampaService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        corEstampaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Write `CorEstampaExceptionHandler.java`**

```java
package com.meshsuite.produto;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = CorEstampaController.class)
public class CorEstampaExceptionHandler {

    // Fallback for a race condition slipping past CorEstampaService's pre-check
    // (two concurrent requests for the same new nome) -- the DB's
    // UNIQUE(tenant_id, nome) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma cor/estampa cadastrada com este nome"));
    }
}
```

- [ ] **Step 3: Register the three named exceptions in `GlobalExceptionHandler.java`**

Append these three handlers to the end of the class, right after the existing `handleCategoriaEmUso` method, before the final closing brace:

```java
    @ExceptionHandler(com.meshsuite.produto.CorEstampaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleCorEstampaNaoEncontrada(
            com.meshsuite.produto.CorEstampaNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.CorEstampaNomeDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleCorEstampaNomeDuplicado(
            com.meshsuite.produto.CorEstampaNomeDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.CorEstampaEmUsoException.class)
    public ResponseEntity<Map<String, String>> handleCorEstampaEmUso(
            com.meshsuite.produto.CorEstampaEmUsoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 4: Write `CorEstampaControllerTest.java`**

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
class CorEstampaControllerTest extends AbstractIntegrationTest {

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

    private String corEstampaPayload(String nome) {
        return """
                {
                  "nome": "%s",
                  "dataVigencia": "2026-01-01"
                }
                """.formatted(nome);
    }

    @Test
    void createsListsUpdatesAndDeletesCorEstampa() throws Exception {
        String token = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/cores-estampas").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corEstampaPayload("Azul Marinho")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Azul Marinho"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/cores-estampas").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Azul Marinho"));

        mockMvc.perform(put("/api/cores-estampas/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corEstampaPayload("Azul Marinho Atualizado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Azul Marinho Atualizado"));

        mockMvc.perform(delete("/api/cores-estampas/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cores-estampas/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateNomeWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/cores-estampas").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corEstampaPayload("Azul Marinho")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/cores-estampas").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corEstampaPayload("Azul Marinho")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingDataVigenciaWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/cores-estampas").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Sem Vigência"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDeletingACorEstampaInUseWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/cores-estampas").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corEstampaPayload("Azul Marinho")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String corEstampaId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

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
                                """.formatted(corEstampaId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/cores-estampas/" + corEstampaId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsCorEstampa() throws Exception {
        String tokenA = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/cores-estampas").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corEstampaPayload("Azul Marinho")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal-corest", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404.
        entityManager.clear();

        mockMvc.perform(get("/api/cores-estampas/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/cores-estampas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao-ce", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/cores-estampas").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=CorEstampaControllerTest`
Expected: PASS (7/7).

- [ ] **Step 6: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw clean test`
Expected: no NEW failures beyond the known pre-existing `com.meshsuite.payable.*` ordering issue (see Task 1, Step 19). Use `clean test`, same reason as Task 1.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/CorEstampaExceptionHandler.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/CorEstampaControllerTest.java
git commit -m "feat(cor-estampa): add CorEstampaController and exception wiring"
```

---

### Task 3: CorEstampa frontend — API layer, list view, form view, routing, sidebar

**Files:**
- Create: `mesh-suite-frontend/src/api/coresEstampas.ts`
- Create: `mesh-suite-frontend/src/views/CoresEstampasListView.vue`
- Create: `mesh-suite-frontend/src/views/CorEstampaFormView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/CoresEstampasListView.spec.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/CorEstampaFormView.spec.ts`

**Interfaces:**
- Consumes: `GET/POST/PUT/DELETE /api/cores-estampas` (Task 2).
- Produces: `listarCoresEstampas`, `buscarCorEstampa`, `criarCorEstampa`, `atualizarCorEstampa`, `excluirCorEstampa` in `@/api/coresEstampas`, and the `/cores-estampas` route. Task 4 (Produto frontend) consumes `listarCoresEstampas` directly.

- [ ] **Step 1: Write `api/coresEstampas.ts`**

```ts
import { apiClient } from './client'

export interface CorEstampaRequest {
  nome: string
  dataVigencia: string
  descricao: string | null
  ativo: boolean | null
}

export interface CorEstampaResponse extends CorEstampaRequest {
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

export interface ListarCoresEstampasParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listarCoresEstampas(params: ListarCoresEstampasParams): Promise<Page<CorEstampaResponse>> {
  const { data } = await apiClient.get<Page<CorEstampaResponse>>('/cores-estampas', { params })
  return data
}

export async function buscarCorEstampa(id: string): Promise<CorEstampaResponse> {
  const { data } = await apiClient.get<CorEstampaResponse>(`/cores-estampas/${id}`)
  return data
}

export async function criarCorEstampa(payload: CorEstampaRequest): Promise<CorEstampaResponse> {
  const { data } = await apiClient.post<CorEstampaResponse>('/cores-estampas', payload)
  return data
}

export async function atualizarCorEstampa(id: string, payload: CorEstampaRequest): Promise<CorEstampaResponse> {
  const { data } = await apiClient.put<CorEstampaResponse>(`/cores-estampas/${id}`, payload)
  return data
}

export async function excluirCorEstampa(id: string): Promise<void> {
  await apiClient.delete(`/cores-estampas/${id}`)
}
```

- [ ] **Step 2: Write `CoresEstampasListView.vue`**

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
          <tr v-for="corEstampa in pagina.content" :key="corEstampa.id">
            <td>{{ corEstampa.nome }}</td>
            <td>{{ formatarData(corEstampa.dataVigencia) }}</td>
            <td>{{ corEstampa.produtosVinculados }} produtos</td>
            <td><span class="badge" :class="corEstampa.ativo ? 'badge-ATIVO' : 'badge-INATIVO'">{{ corEstampa.ativo ? 'Ativo' : 'Inativo' }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(corEstampa.id, $event)"
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
  listarCoresEstampas,
  excluirCorEstampa,
  type CorEstampaResponse,
  type Page as ApiPage,
} from '@/api/coresEstampas'

const router = useRouter()

const filtros = reactive({ busca: '', ativo: '' })
const pagina = ref<ApiPage<CorEstampaResponse>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
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
    pagina.value = await listarCoresEstampas({
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

async function excluir(corEstampa: CorEstampaResponse) {
  acoesAbertas.value = null
  if (!confirm(`Excluir a cor/estampa "${corEstampa.nome}"?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirCorEstampa(corEstampa.id)
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

- [ ] **Step 3: Write `CorEstampaFormView.vue`**

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Cor / Estampa' : 'Nova Cor / Estampa'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Cor / Estampa *</label>
            <input v-model="form.nome" data-test="nome" placeholder="Ex: Azul Marinho, Floral Primavera" />
            <p v-if="erros.nome" class="field-error">{{ erros.nome }}</p>
          </div>
          <div>
            <label class="field-label">Data de Vigência *</label>
            <input v-model="form.dataVigencia" type="date" data-test="data-vigencia" />
            <p v-if="erros.dataVigencia" class="field-error">{{ erros.dataVigencia }}</p>
          </div>
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
  buscarCorEstampa,
  criarCorEstampa,
  atualizarCorEstampa,
  type CorEstampaRequest,
} from '@/api/coresEstampas'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): CorEstampaRequest {
  return { nome: '', dataVigencia: '', descricao: '', ativo: true }
}

const form = reactive<CorEstampaRequest>(novoFormulario())
const erros = reactive<{ nome?: string; dataVigencia?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const corEstampa = await buscarCorEstampa(id)
      form.nome = corEstampa.nome
      form.dataVigencia = corEstampa.dataVigencia
      form.descricao = corEstampa.descricao
      form.ativo = corEstampa.ativo
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da cor/estampa.'
    }
  }
})

function validar(): boolean {
  erros.nome = form.nome.trim() ? undefined : 'Campo obrigatório'
  erros.dataVigencia = form.dataVigencia ? undefined : 'Campo obrigatório'
  return !erros.nome && !erros.dataVigencia
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
      await atualizarCorEstampa(id, form)
    } else {
      await criarCorEstampa(form)
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

- [ ] **Step 4: Wire up the routes**

In `mesh-suite-frontend/src/router/index.ts`, add these imports alongside the existing ones:

```ts
import CoresEstampasListView from '@/views/CoresEstampasListView.vue'
import CorEstampaFormView from '@/views/CorEstampaFormView.vue'
```

Add these three routes to the `routes` array, right after the `/categorias/:id/editar` entry:

```ts
    { path: '/cores-estampas', name: 'cores-estampas', component: CoresEstampasListView },
    { path: '/cores-estampas/novo', name: 'cores-estampas-novo', component: CorEstampaFormView },
    { path: '/cores-estampas/:id/editar', name: 'cores-estampas-editar', component: CorEstampaFormView },
```

- [ ] **Step 5: Wire up the sidebar**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, find the `Cores / Estampas` entry inside the `catalogo` group (currently `route: null`):

```ts
      { icon: '🎨', label: 'Cores / Estampas', route: null },
```

Change it to:

```ts
      { icon: '🎨', label: 'Cores / Estampas', route: '/cores-estampas' },
```

- [ ] **Step 6: Write `CoresEstampasListView.spec.ts`**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CoresEstampasListView from '@/views/CoresEstampasListView.vue'
import * as coresEstampasApi from '@/api/coresEstampas'

vi.mock('@/api/coresEstampas')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/cores-estampas', name: 'cores-estampas', component: CoresEstampasListView },
      { path: '/cores-estampas/novo', name: 'cores-estampas-novo', component: { template: '<div />' } },
      { path: '/cores-estampas/:id/editar', name: 'cores-estampas-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/cores-estampas')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> -- stub it here so it
    // renders in place instead, keeping wrapper.find() queries working.
    wrapper: mount(CoresEstampasListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const corEstampaExemplo = {
  id: 'ce-1',
  nome: 'Azul Marinho',
  dataVigencia: '2026-01-01',
  descricao: 'Cor sólida padrão',
  ativo: true,
  produtosVinculados: 3,
  criadoEm: '2026-01-01T00:00:00Z',
}

describe('CoresEstampasListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the cor/estampa list', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [corEstampaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Azul Marinho')
    expect(wrapper.text()).toContain('3 produtos')
    expect(wrapper.text()).toContain('01/01/2026')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de cores/estampas.')
  })

  it('reloads the list when the search field changes', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('Azul')
    await flushPromises()

    expect(coresEstampasApi.listarCoresEstampas).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: 'Azul' }),
    )
  })

  it('navigates to the new-cor-estampa route when the button is clicked', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-cor-estampa"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('cores-estampas-novo')
  })

  it('deletes a cor/estampa after confirmation and reloads the list', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [corEstampaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(coresEstampasApi.excluirCorEstampa).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(coresEstampasApi.excluirCorEstampa).toHaveBeenCalledWith('ce-1')
  })

  it('shows the backend message when deletion is blocked because the cor/estampa is in use', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [corEstampaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(coresEstampasApi.excluirCorEstampa).mockRejectedValue({
      response: { data: { mensagem: 'Não é possível excluir: 3 produto(s) usam esta cor/estampa' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não é possível excluir: 3 produto(s) usam esta cor/estampa')
  })
})
```

- [ ] **Step 7: Write `CorEstampaFormView.spec.ts`**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CorEstampaFormView from '@/views/CorEstampaFormView.vue'
import * as coresEstampasApi from '@/api/coresEstampas'

vi.mock('@/api/coresEstampas')

function mountWithRouter(path = '/cores-estampas/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/cores-estampas', name: 'cores-estampas', component: { template: '<div />' } },
      { path: '/cores-estampas/novo', name: 'cores-estampas-novo', component: CorEstampaFormView },
      { path: '/cores-estampas/:id/editar', name: 'cores-estampas-editar', component: CorEstampaFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(CorEstampaFormView, { global: { plugins: [router] } }),
  }))
}

describe('CorEstampaFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field errors when nome/dataVigencia are blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(coresEstampasApi.criarCorEstampa).not.toHaveBeenCalled()
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(coresEstampasApi.criarCorEstampa).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Azul Marinho')
    await wrapper.find('[data-test="data-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(coresEstampasApi.criarCorEstampa).toHaveBeenCalledWith(
      expect.objectContaining({ nome: 'Azul Marinho', dataVigencia: '2026-01-01', ativo: true }),
    )
    expect(router.currentRoute.value.name).toBe('cores-estampas')
  })

  it('shows a conflict message on duplicate nome (409)', async () => {
    vi.mocked(coresEstampasApi.criarCorEstampa).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Azul Marinho')
    await wrapper.find('[data-test="data-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma cor/estampa cadastrada com este nome')
  })

  it('toggles between Ativo and Inativo status', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="status-inativo"]').trigger('click')
    vi.mocked(coresEstampasApi.criarCorEstampa).mockResolvedValue({} as any)
    await wrapper.find('[data-test="nome"]').setValue('Azul Marinho')
    await wrapper.find('[data-test="data-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(coresEstampasApi.criarCorEstampa).toHaveBeenCalledWith(
      expect.objectContaining({ ativo: false }),
    )
  })

  it('loads existing cor/estampa data in edit mode', async () => {
    vi.mocked(coresEstampasApi.buscarCorEstampa).mockResolvedValue({
      id: 'ce-1', nome: 'Azul Marinho', dataVigencia: '2026-01-01', descricao: 'Descrição', ativo: true,
      produtosVinculados: 2, criadoEm: '2026-01-01T00:00:00Z',
    })

    const { wrapper } = await mountWithRouter('/cores-estampas/ce-1/editar')
    await flushPromises()

    expect(coresEstampasApi.buscarCorEstampa).toHaveBeenCalledWith('ce-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Azul Marinho')
    expect((wrapper.find('[data-test="data-vigencia"]').element as HTMLInputElement).value).toBe('2026-01-01')
  })

  it('shows an error message when loading cor/estampa data fails in edit mode', async () => {
    vi.mocked(coresEstampasApi.buscarCorEstampa).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/cores-estampas/ce-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da cor/estampa.')
  })
})
```

- [ ] **Step 8: Run the new tests to verify they pass**

Run: `cd mesh-suite-frontend && npm test -- --run src/views/__tests__/CoresEstampasListView.spec.ts src/views/__tests__/CorEstampaFormView.spec.ts`
Expected: PASS (6/6 + 6/6).

- [ ] **Step 9: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npm test -- --run`
Expected: no regressions (this task doesn't touch `ProdutoFormView.vue`/`api/produtos.ts` yet — those are Task 4).

- [ ] **Step 10: Commit**

```bash
git add mesh-suite-frontend/src/api/coresEstampas.ts \
        mesh-suite-frontend/src/views/CoresEstampasListView.vue \
        mesh-suite-frontend/src/views/CorEstampaFormView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/views/__tests__/CoresEstampasListView.spec.ts \
        mesh-suite-frontend/src/views/__tests__/CorEstampaFormView.spec.ts
git commit -m "feat(cor-estampa): add CoresEstampasListView, CorEstampaFormView, routing and sidebar"
```

---

### Task 4: Produto frontend — cor/estampa dropdown

**Files:**
- Modify: `mesh-suite-frontend/src/api/produtos.ts`
- Modify: `mesh-suite-frontend/src/views/ProdutoFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts`

**Interfaces:**
- Consumes: `listarCoresEstampas` from `@/api/coresEstampas` (Task 3).
- Produces: nothing new — this is the final task, it completes `Produto`'s form to match the backend's `corEstampaId`/`corEstampaNome` shape (Task 1).

- [ ] **Step 1: Modify `api/produtos.ts` — add `corEstampaId`/`corEstampaNome`**

Find:

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

Replace with:

```ts
export interface ProdutoRequest {
  nome: string
  sku: string
  codigoBarras: string
  marca: string
  categoriaId: string | null
  corEstampaId: string | null
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
  corEstampaNome: string | null
}
```

- [ ] **Step 2: Modify `ProdutoFormView.vue` — add the cor/estampa dropdown**

Find:

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
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Preço de Venda *</label>
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
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Cor / Estampa</label>
            <select v-model="form.corEstampaId" data-test="cor-estampa">
              <option :value="null">Sem cor/estampa</option>
              <option v-for="corEstampa in coresEstampas" :key="corEstampa.id" :value="corEstampa.id">
                {{ corEstampa.nome }}
              </option>
            </select>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Preço de Venda *</label>
```

Find the imports:

```ts
import { listarCategorias, type CategoriaResponse } from '@/api/categorias'
```

Replace with:

```ts
import { listarCategorias, type CategoriaResponse } from '@/api/categorias'
import { listarCoresEstampas, type CorEstampaResponse } from '@/api/coresEstampas'
```

Find:

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

Replace with:

```ts
function novoFormulario(): ProdutoRequest {
  return {
    nome: '',
    sku: '',
    codigoBarras: '',
    marca: '',
    categoriaId: null,
    corEstampaId: null,
    precoVenda: 0,
```

Find:

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

      // An inactive categoria is filtered out of the `ativo: true` list above,
      // but per design spec it must stay visible in the dropdown when it's
      // already linked to this produto (it just can't be picked as a new
      // option for produtos without one). Splice in a minimal synthetic entry
      // from the produto response itself so the <select> has a matching
      // <option> to bind to -- a full CategoriaResponse isn't needed since
      // the template only reads `id`/`nome`.
      if (
        produto.categoriaId &&
        !categorias.value.some((categoria) => categoria.id === produto.categoriaId)
      ) {
        categorias.value = [
          ...categorias.value,
          {
            id: produto.categoriaId,
            nome: produto.categoriaNome ?? '',
          } as CategoriaResponse,
        ]
      }
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
const coresEstampas = ref<CorEstampaResponse[]>([])

onMounted(async () => {
  try {
    const pagina = await listarCategorias({ ativo: true, size: 100 })
    categorias.value = pagina.content
  } catch {
    // Categoria list is a convenience dropdown, not a required field --
    // if it fails to load, the form still works with "Sem categoria" as
    // the only option, and the current value (if editing) still round-trips.
  }

  try {
    const pagina = await listarCoresEstampas({ ativo: true, size: 100 })
    coresEstampas.value = pagina.content
  } catch {
    // Same convenience-dropdown reasoning as categorias above.
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const produto = await buscarProduto(id)
      Object.assign(form, produto)

      // An inactive categoria is filtered out of the `ativo: true` list above,
      // but per design spec it must stay visible in the dropdown when it's
      // already linked to this produto (it just can't be picked as a new
      // option for produtos without one). Splice in a minimal synthetic entry
      // from the produto response itself so the <select> has a matching
      // <option> to bind to -- a full CategoriaResponse isn't needed since
      // the template only reads `id`/`nome`.
      if (
        produto.categoriaId &&
        !categorias.value.some((categoria) => categoria.id === produto.categoriaId)
      ) {
        categorias.value = [
          ...categorias.value,
          {
            id: produto.categoriaId,
            nome: produto.categoriaNome ?? '',
          } as CategoriaResponse,
        ]
      }

      // Same reasoning as the categoria splice above, mirrored for corEstampa.
      if (
        produto.corEstampaId &&
        !coresEstampas.value.some((corEstampa) => corEstampa.id === produto.corEstampaId)
      ) {
        coresEstampas.value = [
          ...coresEstampas.value,
          {
            id: produto.corEstampaId,
            nome: produto.corEstampaNome ?? '',
          } as CorEstampaResponse,
        ]
      }
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do produto.'
    }
  }
})
```

- [ ] **Step 3: Add tests to `ProdutoFormView.spec.ts`**

Find the `vi.mock('@/api/categorias')` line near the top of the file and add a sibling mock right after it:

```ts
vi.mock('@/api/categorias')
vi.mock('@/api/coresEstampas')
```

Find, in the `'loads existing produto data in edit mode'` test:

```ts
    vi.mocked(produtosApi.buscarProduto).mockResolvedValue({
      id: 'abc-123', nome: 'Camiseta Polo', sku: 'P0001', codigoBarras: '', marca: '', categoriaId: null,
      categoriaNome: null, precoVenda: 59.9, precoCusto: null, status: 'ATIVO', descricao: '', quantidadeEstoque: 10,
      unidadeMedida: 'UN', estoqueMinimo: null, estoqueMaximo: null, peso: null, comprimento: null,
      largura: null, altura: null,
    } as any)
```

Replace with:

```ts
    vi.mocked(produtosApi.buscarProduto).mockResolvedValue({
      id: 'abc-123', nome: 'Camiseta Polo', sku: 'P0001', codigoBarras: '', marca: '', categoriaId: null,
      categoriaNome: null, corEstampaId: null, corEstampaNome: null, precoVenda: 59.9, precoCusto: null,
      status: 'ATIVO', descricao: '', quantidadeEstoque: 10, unidadeMedida: 'UN', estoqueMinimo: null,
      estoqueMaximo: null, peso: null, comprimento: null, largura: null, altura: null,
    } as any)
```

Find, in the `'keeps an inactive-but-linked categoria selected in the dropdown when editing'` test's `buscarProduto` mock:

```ts
    vi.mocked(produtosApi.buscarProduto).mockResolvedValue({
      id: 'abc-123', nome: 'Camiseta Polo', sku: 'P0001', codigoBarras: '', marca: '',
      categoriaId: 'cat-inactive', categoriaNome: 'Descontinuados',
      precoVenda: 59.9, precoCusto: null, status: 'ATIVO', descricao: '', quantidadeEstoque: 10,
      unidadeMedida: 'UN', estoqueMinimo: null, estoqueMaximo: null, peso: null, comprimento: null,
      largura: null, altura: null,
    } as any)
```

Replace with:

```ts
    vi.mocked(produtosApi.buscarProduto).mockResolvedValue({
      id: 'abc-123', nome: 'Camiseta Polo', sku: 'P0001', codigoBarras: '', marca: '',
      categoriaId: 'cat-inactive', categoriaNome: 'Descontinuados', corEstampaId: null, corEstampaNome: null,
      precoVenda: 59.9, precoCusto: null, status: 'ATIVO', descricao: '', quantidadeEstoque: 10,
      unidadeMedida: 'UN', estoqueMinimo: null, estoqueMaximo: null, peso: null, comprimento: null,
      largura: null, altura: null,
    } as any)
```

Add these two new tests at the end of the `describe` block, right before the closing `})`:

```ts

  it('loads cores/estampas into the dropdown and lets the user pick one', async () => {
    const coresEstampasApi = await import('@/api/coresEstampas')
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [
        { id: 'ce-1', nome: 'Azul Marinho', dataVigencia: '2026-01-01', descricao: null, ativo: true, produtosVinculados: 0, criadoEm: '2026-01-01T00:00:00Z' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 100,
    })
    vi.mocked(produtosApi.criarProduto).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('[data-test="cor-estampa"]').setValue('ce-1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(produtosApi.criarProduto).mock.calls[0][0]
    expect(payload.corEstampaId).toBe('ce-1')
  })

  it('keeps an inactive-but-linked cor/estampa selected in the dropdown when editing', async () => {
    const coresEstampasApi = await import('@/api/coresEstampas')
    // The active-only list does NOT include this produto's cor/estampa
    // (simulating it having been deactivated after the produto was linked to it).
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [
        { id: 'ce-active', nome: 'Preto', dataVigencia: '2026-01-01', descricao: null, ativo: true, produtosVinculados: 0, criadoEm: '2026-01-01T00:00:00Z' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 100,
    })
    vi.mocked(produtosApi.buscarProduto).mockResolvedValue({
      id: 'abc-123', nome: 'Camiseta Polo', sku: 'P0001', codigoBarras: '', marca: '',
      categoriaId: null, categoriaNome: null,
      corEstampaId: 'ce-inactive', corEstampaNome: 'Floral Descontinuado',
      precoVenda: 59.9, precoCusto: null, status: 'ATIVO', descricao: '', quantidadeEstoque: 10,
      unidadeMedida: 'UN', estoqueMinimo: null, estoqueMaximo: null, peso: null, comprimento: null,
      largura: null, altura: null,
    } as any)

    const { wrapper } = await mountWithRouter('/produtos/abc-123/editar')
    await flushPromises()

    const select = wrapper.find('[data-test="cor-estampa"]').element as HTMLSelectElement
    expect(select.value).toBe('ce-inactive')
    expect(wrapper.text()).toContain('Floral Descontinuado')
  })
```

- [ ] **Step 4: Run the updated tests to verify they pass**

Run: `cd mesh-suite-frontend && npm test -- --run src/views/__tests__/ProdutoFormView.spec.ts`
Expected: PASS (10/10).

- [ ] **Step 5: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npm test -- --run`
Expected: no regressions.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/api/produtos.ts \
        mesh-suite-frontend/src/views/ProdutoFormView.vue \
        mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts
git commit -m "feat(cor-estampa): wire Produto form's cor/estampa field to the new dropdown"
```
