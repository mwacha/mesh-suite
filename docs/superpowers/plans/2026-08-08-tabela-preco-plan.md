# Tabela de Preço Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Tabela de Preço cadastro (header rule + line-item overrides per product) matching `layout/wireframes/09 - Tabela de Precos-v1.html`, as a standalone auxiliary cadastro not yet consumed by Pedido/Vendas.

**Architecture:** `TabelaPreco` (header) + `TabelaPrecoItem` (line items) follow the exact parent+item pattern already established by `PurchaseOrder`/`PurchaseOrderItem` — one API call creates/replaces the whole item list ("regenerate everything" funnel), `TabelaPrecoItem` has no `tenant_id` of its own (RLS via `EXISTS` against the parent). The price-adjustment formula (operation + rounding) is implemented **only in TypeScript**, used for live preview and the reset button — the backend persists whatever `precoNestaTabela` values the frontend sends, with no server-side recomputation.

**Tech Stack:** Spring Boot 3.4.5 / Java 21, Spring Data JPA, PostgreSQL 16 (RLS), Flyway, Vue 3 + TypeScript + Vite.

## Global Constraints

- New backend code lives in `com.meshsuite.produto` package, in **Portuguese** (`TabelaPreco`, `TabelaPrecoItem`, `TabelaPrecoService`), matching `Categoria`/`CorEstampa`'s convention.
- RLS pattern: `tabela_preco` gets its own `tenant_id` column, `ENABLE`+`FORCE ROW LEVEL SECURITY`, `USING`-only policy. `tabela_preco_item` is a true **line-item table** — no `tenant_id` column, RLS enforced via `EXISTS` against the parent `tabela_preco` row's tenant, exactly matching `purchase_order_item`'s pattern (`ON DELETE CASCADE` on the FK, index on the parent FK column).
- Permission: reuse `Module.PRODUCT` — no new `Module` value, no migration touching `user_permission_module_check`.
- `nome` is unique per tenant — DB unique index `(tenant_id, nome)` AND a pre-check in the service.
- **The price-adjustment formula (SOMAR/SUBTRAIR × REAL/PERCENTUAL, then arredondamento) is implemented ONLY in TypeScript** (`mesh-suite-frontend/src/utils/calculoTabelaPreco.ts`). The backend `TabelaPrecoService` does not recompute or validate item prices — it persists exactly what the request sends. This is a deliberate architectural decision from the design spec, not an oversight: do not add Java-side price calculation.
- Rounding is **always upward** (never below the adjusted price) — the exact algorithm per `arredondamento` value is given in Task 3's code; every task touching rounding must produce numbers matching that algorithm exactly.
- One API call creates/replaces the entire item list — `PUT /api/tabelas-preco/{id}` clears and rebuilds `itens` every time (same "regenerate everything" pattern as `PurchaseOrderService.apply()`), never a partial/incremental update.
- No exclusion-blocking rule — hard delete with `ON DELETE CASCADE` on items. Nothing consumes `TabelaPreco` yet, so there is no "em uso" concept to protect.
- New backend integration tests use tenant `codigo` values distinct from `"aurora"`/`"boreal"` (e.g. `"aurora-tp"`) — same mitigation already applied to `Categoria`/`CorEstampa`'s tests, for the documented pre-existing `DevSeedTest` test-ordering issue.
- When running the full backend suite, use `./mvnw clean test`, not plain `test` — a stale `target/classes` from a prior build can make Flyway see a leftover migration file that no longer exists in source, producing a false "Found more than one migration with version N" failure.
- Next available Flyway migration version is **V25** (confirm with `ls mesh-suite-backend/src/main/resources/db/migration/ | sort -V | tail -5` before creating files, in case another slice has landed since this plan was written).

---

### Task 1: `TabelaPreco`/`TabelaPrecoItem` domain model and service (backend)

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V25__create_tabela_preco.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPreco.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoItem.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ModoSelecaoProdutos.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/MetodoAjuste.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/OperacaoAjuste.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TipoValorAjuste.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/Arredondamento.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoNaoEncontradaException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoNomeDuplicadoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoValidationException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemInput.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/TabelaPrecoRepositoryTest.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/TabelaPrecoServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks — standalone. `Produto`/`ProdutoRepository` already exist.
- Produces: `TabelaPreco`/`TabelaPrecoItem` entities; `TabelaPrecoRepository extends JpaRepository<TabelaPreco, UUID>, JpaSpecificationExecutor<TabelaPreco>` with `existsByNome`/`existsByNomeAndIdNot`; `TabelaPrecoService` with `listar(String busca, Boolean ativo, Pageable): Page<TabelaPrecoSummaryResponse>`, `buscarPorId(UUID): TabelaPrecoResponse`, `criar(UUID tenantId, TabelaPrecoRequest): TabelaPrecoResponse`, `atualizar(UUID id, TabelaPrecoRequest): TabelaPrecoResponse`, `excluir(UUID id): void`; `TabelaPrecoRequest`/`TabelaPrecoResponse`/`TabelaPrecoItemInput`/`TabelaPrecoItemResponse`/`TabelaPrecoSummaryResponse` records (full shapes below). Task 2 (controller) consumes `TabelaPrecoService` directly.

- [ ] **Step 1: Confirm the next free migration version**

Run: `ls mesh-suite-backend/src/main/resources/db/migration/ | sort -V | tail -5`
Expected: highest existing version is V24. If a newer migration already exists, use the next free version number instead of V25 throughout this task, and note the substitution in your report.

- [ ] **Step 2: Write the migration creating `tabela_preco` and `tabela_preco_item`**

```sql
CREATE TABLE tabela_preco (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    modo_selecao_produtos VARCHAR(20) NOT NULL
        CHECK (modo_selecao_produtos IN ('TODOS_PRODUTOS','SELECIONAR_PRODUTOS')),
    metodo_ajuste VARCHAR(10) NOT NULL CHECK (metodo_ajuste IN ('AUTOMATICO','MANUAL')),
    operacao_ajuste VARCHAR(10) CHECK (operacao_ajuste IN ('SOMAR','SUBTRAIR')),
    tipo_valor_ajuste VARCHAR(12) CHECK (tipo_valor_ajuste IN ('REAL','PERCENTUAL')),
    valor_ajuste NUMERIC(12,2),
    arredondamento VARCHAR(20) NOT NULL
        CHECK (arredondamento IN ('NAO_ARREDONDAR','TERMINAR_EM_0','TERMINAR_EM_9','TERMINAR_EM_90','TERMINAR_EM_99')),
    inicio_vigencia DATE NOT NULL,
    termino_vigencia DATE,
    valor_minimo_venda NUMERIC(12,2),
    percentual_comissao_padrao NUMERIC(5,2),
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_tabela_preco_tenant_nome ON tabela_preco(tenant_id, nome);
CREATE INDEX idx_tabela_preco_tenant_id ON tabela_preco(tenant_id);

ALTER TABLE tabela_preco ENABLE ROW LEVEL SECURITY;
ALTER TABLE tabela_preco FORCE ROW LEVEL SECURITY;

CREATE POLICY tabela_preco_tenant_isolation ON tabela_preco
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE tabela_preco_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tabela_preco_id UUID NOT NULL REFERENCES tabela_preco(id) ON DELETE CASCADE,
    produto_id UUID NOT NULL REFERENCES produto(id),
    preco_nesta_tabela NUMERIC(12,2),
    percentual_comissao NUMERIC(5,2)
);

CREATE INDEX idx_tabela_preco_item_tabela_preco_id ON tabela_preco_item(tabela_preco_id);

ALTER TABLE tabela_preco_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE tabela_preco_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- tabela_preco row's own RLS policy, matched by tabela_preco_id. Same
-- pattern as purchase_order_item.
CREATE POLICY tabela_preco_item_tenant_isolation ON tabela_preco_item
    USING (EXISTS (
        SELECT 1 FROM tabela_preco tp
        WHERE tp.id = tabela_preco_item.tabela_preco_id
          AND tp.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

Save as `mesh-suite-backend/src/main/resources/db/migration/V25__create_tabela_preco.sql`.

- [ ] **Step 3: Write the five enum files**

```java
package com.meshsuite.produto;

public enum ModoSelecaoProdutos {
    TODOS_PRODUTOS,
    SELECIONAR_PRODUTOS
}
```
Save as `ModoSelecaoProdutos.java`.

```java
package com.meshsuite.produto;

public enum MetodoAjuste {
    AUTOMATICO,
    MANUAL
}
```
Save as `MetodoAjuste.java`.

```java
package com.meshsuite.produto;

public enum OperacaoAjuste {
    SOMAR,
    SUBTRAIR
}
```
Save as `OperacaoAjuste.java`.

```java
package com.meshsuite.produto;

public enum TipoValorAjuste {
    REAL,
    PERCENTUAL
}
```
Save as `TipoValorAjuste.java`.

```java
package com.meshsuite.produto;

public enum Arredondamento {
    NAO_ARREDONDAR,
    TERMINAR_EM_0,
    TERMINAR_EM_9,
    TERMINAR_EM_90,
    TERMINAR_EM_99
}
```
Save as `Arredondamento.java`.

- [ ] **Step 4: Write `TabelaPreco.java`**

```java
package com.meshsuite.produto;

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
@Table(name = "tabela_preco")
@Getter
@Setter
public class TabelaPreco {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_selecao_produtos", nullable = false, length = 20)
    private ModoSelecaoProdutos modoSelecaoProdutos;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_ajuste", nullable = false, length = 10)
    private MetodoAjuste metodoAjuste;

    @Enumerated(EnumType.STRING)
    @Column(name = "operacao_ajuste", length = 10)
    private OperacaoAjuste operacaoAjuste;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_valor_ajuste", length = 12)
    private TipoValorAjuste tipoValorAjuste;

    @Column(name = "valor_ajuste", precision = 12, scale = 2)
    private BigDecimal valorAjuste;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Arredondamento arredondamento;

    @Column(name = "inicio_vigencia", nullable = false)
    private LocalDate inicioVigencia;

    @Column(name = "termino_vigencia")
    private LocalDate terminoVigencia;

    @Column(name = "valor_minimo_venda", precision = 12, scale = 2)
    private BigDecimal valorMinimoVenda;

    @Column(name = "percentual_comissao_padrao", precision = 5, scale = 2)
    private BigDecimal percentualComissaoPadrao;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "tabelaPreco", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TabelaPrecoItem> itens = new ArrayList<>();
}
```

- [ ] **Step 5: Write `TabelaPrecoItem.java`**

```java
package com.meshsuite.produto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tabela_preco_item")
@Getter
@Setter
public class TabelaPrecoItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tabela_preco_id", nullable = false)
    private TabelaPreco tabelaPreco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "preco_nesta_tabela", precision = 12, scale = 2)
    private BigDecimal precoNestaTabela;

    @Column(name = "percentual_comissao", precision = 5, scale = 2)
    private BigDecimal percentualComissao;
}
```

- [ ] **Step 6: Write `TabelaPrecoRepository.java`**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TabelaPrecoRepository extends JpaRepository<TabelaPreco, UUID>, JpaSpecificationExecutor<TabelaPreco> {
    boolean existsByNome(String nome);
    boolean existsByNomeAndIdNot(String nome, UUID id);
}
```

- [ ] **Step 7: Write `TabelaPrecoSpecifications.java`**

```java
package com.meshsuite.produto;

import org.springframework.data.jpa.domain.Specification;

public final class TabelaPrecoSpecifications {

    private TabelaPrecoSpecifications() {
    }

    public static Specification<TabelaPreco> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nome")), termo);
    }

    public static Specification<TabelaPreco> comAtivo(Boolean ativo) {
        if (ativo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
    }
}
```

- [ ] **Step 8: Write the three exception classes**

```java
package com.meshsuite.produto;

public class TabelaPrecoNaoEncontradaException extends RuntimeException {
    public TabelaPrecoNaoEncontradaException() {
        super("Tabela de preço não encontrada");
    }
}
```
Save as `TabelaPrecoNaoEncontradaException.java`.

```java
package com.meshsuite.produto;

public class TabelaPrecoNomeDuplicadoException extends RuntimeException {
    public TabelaPrecoNomeDuplicadoException() {
        super("Já existe uma tabela de preço cadastrada com este nome");
    }
}
```
Save as `TabelaPrecoNomeDuplicadoException.java`.

```java
package com.meshsuite.produto;

public class TabelaPrecoValidationException extends RuntimeException {
    public TabelaPrecoValidationException(String message) {
        super(message);
    }
}
```
Save as `TabelaPrecoValidationException.java`.

- [ ] **Step 9: Write the DTOs**

```java
package com.meshsuite.produto.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TabelaPrecoItemInput(
        @NotNull UUID produtoId,
        BigDecimal precoNestaTabela,
        BigDecimal percentualComissao) {
}
```
Save as `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemInput.java`.

```java
package com.meshsuite.produto.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TabelaPrecoItemResponse(
        UUID produtoId,
        String produtoNome,
        String produtoSku,
        BigDecimal precoCadastrado,
        BigDecimal precoNestaTabela,
        BigDecimal percentualComissao) {
}
```
Save as `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemResponse.java`.

```java
package com.meshsuite.produto.dto;

import com.meshsuite.produto.Arredondamento;
import com.meshsuite.produto.MetodoAjuste;
import com.meshsuite.produto.ModoSelecaoProdutos;
import com.meshsuite.produto.OperacaoAjuste;
import com.meshsuite.produto.TipoValorAjuste;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TabelaPrecoRequest(
        @NotBlank String nome,
        @NotNull ModoSelecaoProdutos modoSelecaoProdutos,
        @NotNull MetodoAjuste metodoAjuste,
        OperacaoAjuste operacaoAjuste,
        TipoValorAjuste tipoValorAjuste,
        BigDecimal valorAjuste,
        @NotNull Arredondamento arredondamento,
        @NotNull LocalDate inicioVigencia,
        LocalDate terminoVigencia,
        BigDecimal valorMinimoVenda,
        BigDecimal percentualComissaoPadrao,
        Boolean ativo,
        @NotNull List<@Valid TabelaPrecoItemInput> itens) {
}
```
Save as `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoRequest.java`.

```java
package com.meshsuite.produto.dto;

import com.meshsuite.produto.Arredondamento;
import com.meshsuite.produto.MetodoAjuste;
import com.meshsuite.produto.ModoSelecaoProdutos;
import com.meshsuite.produto.OperacaoAjuste;
import com.meshsuite.produto.TipoValorAjuste;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TabelaPrecoResponse(
        UUID id,
        String nome,
        ModoSelecaoProdutos modoSelecaoProdutos,
        MetodoAjuste metodoAjuste,
        OperacaoAjuste operacaoAjuste,
        TipoValorAjuste tipoValorAjuste,
        BigDecimal valorAjuste,
        Arredondamento arredondamento,
        LocalDate inicioVigencia,
        LocalDate terminoVigencia,
        BigDecimal valorMinimoVenda,
        BigDecimal percentualComissaoPadrao,
        Boolean ativo,
        Instant criadoEm,
        List<TabelaPrecoItemResponse> itens) {
}
```
Save as `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoResponse.java`.

```java
package com.meshsuite.produto.dto;

import com.meshsuite.produto.MetodoAjuste;
import com.meshsuite.produto.OperacaoAjuste;
import com.meshsuite.produto.TipoValorAjuste;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TabelaPrecoSummaryResponse(
        UUID id,
        String nome,
        MetodoAjuste metodoAjuste,
        OperacaoAjuste operacaoAjuste,
        TipoValorAjuste tipoValorAjuste,
        BigDecimal valorAjuste,
        LocalDate inicioVigencia,
        LocalDate terminoVigencia,
        Boolean ativo) {
}
```
Save as `mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoSummaryResponse.java`.

- [ ] **Step 10: Write `TabelaPrecoService.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.produto.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TabelaPrecoService {

    private final TabelaPrecoRepository tabelaPrecoRepository;
    private final ProdutoRepository produtoRepository;

    public TabelaPrecoService(TabelaPrecoRepository tabelaPrecoRepository, ProdutoRepository produtoRepository) {
        this.tabelaPrecoRepository = tabelaPrecoRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<TabelaPrecoSummaryResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        Specification<TabelaPreco> spec = Specification.allOf(
                TabelaPrecoSpecifications.comBusca(busca),
                TabelaPrecoSpecifications.comAtivo(ativo));
        return tabelaPrecoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public TabelaPrecoResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public TabelaPrecoResponse criar(UUID tenantId, TabelaPrecoRequest request) {
        validarNome(request.nome(), null);

        TabelaPreco tabelaPreco = new TabelaPreco();
        tabelaPreco.setTenantId(tenantId);
        aplicar(tabelaPreco, request);
        return toResponse(tabelaPrecoRepository.saveAndFlush(tabelaPreco));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public TabelaPrecoResponse atualizar(UUID id, TabelaPrecoRequest request) {
        validarNome(request.nome(), id);

        TabelaPreco tabelaPreco = buscarEntidadePorId(id);
        aplicar(tabelaPreco, request);
        return toResponse(tabelaPrecoRepository.saveAndFlush(tabelaPreco));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        tabelaPrecoRepository.delete(buscarEntidadePorId(id));
    }

    private TabelaPreco buscarEntidadePorId(UUID id) {
        return tabelaPrecoRepository.findById(id).orElseThrow(TabelaPrecoNaoEncontradaException::new);
    }

    private void validarNome(String nome, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? tabelaPrecoRepository.existsByNome(nome)
                : tabelaPrecoRepository.existsByNomeAndIdNot(nome, idAtual);
        if (duplicado) {
            throw new TabelaPrecoNomeDuplicadoException();
        }
    }

    // Clears and rebuilds the whole item list on every save -- same
    // "regenerate everything" funnel PurchaseOrderService.apply() uses.
    // No price calculation happens here: precoNestaTabela/percentualComissao
    // are persisted exactly as the client sent them (see Global Constraints).
    private void aplicar(TabelaPreco tabelaPreco, TabelaPrecoRequest request) {
        tabelaPreco.setNome(request.nome());
        tabelaPreco.setModoSelecaoProdutos(request.modoSelecaoProdutos());
        tabelaPreco.setMetodoAjuste(request.metodoAjuste());
        tabelaPreco.setOperacaoAjuste(request.operacaoAjuste());
        tabelaPreco.setTipoValorAjuste(request.tipoValorAjuste());
        tabelaPreco.setValorAjuste(request.valorAjuste());
        tabelaPreco.setArredondamento(request.arredondamento());
        tabelaPreco.setInicioVigencia(request.inicioVigencia());
        tabelaPreco.setTerminoVigencia(request.terminoVigencia());
        tabelaPreco.setValorMinimoVenda(request.valorMinimoVenda());
        tabelaPreco.setPercentualComissaoPadrao(request.percentualComissaoPadrao());
        tabelaPreco.setAtivo(request.ativo() != null ? request.ativo() : true);

        tabelaPreco.getItens().clear();
        for (TabelaPrecoItemInput itemInput : request.itens()) {
            Produto produto = produtoRepository.findById(itemInput.produtoId())
                    .orElseThrow(() -> new TabelaPrecoValidationException("Produto não encontrado"));
            TabelaPrecoItem item = new TabelaPrecoItem();
            item.setTabelaPreco(tabelaPreco);
            item.setProduto(produto);
            item.setPrecoNestaTabela(itemInput.precoNestaTabela());
            item.setPercentualComissao(itemInput.percentualComissao());
            tabelaPreco.getItens().add(item);
        }
    }

    private TabelaPrecoSummaryResponse toSummary(TabelaPreco t) {
        return new TabelaPrecoSummaryResponse(t.getId(), t.getNome(), t.getMetodoAjuste(), t.getOperacaoAjuste(),
                t.getTipoValorAjuste(), t.getValorAjuste(), t.getInicioVigencia(), t.getTerminoVigencia(), t.getAtivo());
    }

    private TabelaPrecoResponse toResponse(TabelaPreco t) {
        List<TabelaPrecoItemResponse> itens = t.getItens().stream()
                .map(i -> new TabelaPrecoItemResponse(i.getProduto().getId(), i.getProduto().getNome(),
                        i.getProduto().getSku(), i.getProduto().getPrecoVenda(), i.getPrecoNestaTabela(),
                        i.getPercentualComissao()))
                .toList();
        return new TabelaPrecoResponse(t.getId(), t.getNome(), t.getModoSelecaoProdutos(), t.getMetodoAjuste(),
                t.getOperacaoAjuste(), t.getTipoValorAjuste(), t.getValorAjuste(), t.getArredondamento(),
                t.getInicioVigencia(), t.getTerminoVigencia(), t.getValorMinimoVenda(), t.getPercentualComissaoPadrao(),
                t.getAtivo(), t.getCriadoEm(), itens);
    }
}
```

- [ ] **Step 11: Write `TabelaPrecoRepositoryTest.java`**

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

class TabelaPrecoRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired TabelaPrecoRepository tabelaPrecoRepository;
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

    private TabelaPreco novaTabelaPreco(UUID tenantId, String nome) {
        TabelaPreco t = new TabelaPreco();
        t.setTenantId(tenantId);
        t.setNome(nome);
        t.setModoSelecaoProdutos(ModoSelecaoProdutos.TODOS_PRODUTOS);
        t.setMetodoAjuste(MetodoAjuste.MANUAL);
        t.setArredondamento(Arredondamento.NAO_ARREDONDAR);
        t.setInicioVigencia(LocalDate.of(2026, 1, 1));
        return t;
    }

    @Test
    @Transactional
    void savesTabelaPrecoWithDefaults() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());

        TabelaPreco saved = tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenant.getId(), "Varejo"));
        entityManager.clear();

        TabelaPreco reloaded = tabelaPrecoRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAtivo()).isTrue();
        assertThat(reloaded.getInicioVigencia()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @Transactional
    void nomeMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());

        tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenant.getId(), "Varejo"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenant.getId(), "Varejo")));
    }

    @Test
    @Transactional
    void sameNomeAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-tp");
        Tenant tenantB = createTenant("boreal-tp");

        setTenantContext(tenantA.getId());
        tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenantA.getId(), "Varejo"));

        setTenantContext(tenantB.getId());
        TabelaPreco saved = tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenantB.getId(), "Varejo"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());
        tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenant.getId(), "Varejo"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM tabela_preco")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
```

- [ ] **Step 12: Run the repository test to verify it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=TabelaPrecoRepositoryTest`
Expected: PASS (4/4).

- [ ] **Step 13: Write `TabelaPrecoServiceTest.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.produto.dto.TabelaPrecoItemInput;
import com.meshsuite.produto.dto.TabelaPrecoRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabelaPrecoServiceTest extends AbstractIntegrationTest {

    @Autowired TabelaPrecoService tabelaPrecoService;
    @Autowired ProdutoRepository produtoRepository;
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

    private Produto novoProduto(UUID tenantId, String sku, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Produto " + sku);
        p.setSku(sku);
        p.setPrecoVenda(precoVenda);
        return produtoRepository.saveAndFlush(p);
    }

    private TabelaPrecoRequest request(String nome, List<TabelaPrecoItemInput> itens) {
        return new TabelaPrecoRequest(nome, ModoSelecaoProdutos.SELECIONAR_PRODUTOS, MetodoAjuste.MANUAL,
                null, null, null, Arredondamento.NAO_ARREDONDAR, LocalDate.of(2026, 1, 1), null, null, null, null, itens);
    }

    @Test
    @Transactional
    void criaERecuperaTabelaPrecoComItens() {
        UUID tenantId = setUpTenant("aurora-tp");
        Produto produto = novoProduto(tenantId, "P0001", new BigDecimal("59.90"));

        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new TabelaPrecoItemInput(produto.getId(), new BigDecimal("69.90"), new BigDecimal("5.00")))));

        var buscada = tabelaPrecoService.buscarPorId(criada.id());
        assertThat(buscada.nome()).isEqualTo("Varejo");
        assertThat(buscada.itens()).hasSize(1);
        assertThat(buscada.itens().get(0).produtoId()).isEqualTo(produto.getId());
        assertThat(buscada.itens().get(0).precoNestaTabela()).isEqualByComparingTo("69.90");
        assertThat(buscada.itens().get(0).precoCadastrado()).isEqualByComparingTo("59.90");
    }

    @Test
    @Transactional
    void doesNotRecalculatePricesServerSide() {
        // Global Constraints: the backend persists exactly what the client sends,
        // even a price wildly different from produto.precoVenda -- there is no
        // server-side formula to disagree with the client.
        UUID tenantId = setUpTenant("aurora-tp");
        Produto produto = novoProduto(tenantId, "P0001", new BigDecimal("10.00"));

        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Promo", List.of(new TabelaPrecoItemInput(produto.getId(), new BigDecimal("999.99"), null))));

        assertThat(criada.itens().get(0).precoNestaTabela()).isEqualByComparingTo("999.99");
    }

    @Test
    @Transactional
    void rejectsDuplicateNomeOnCreate() {
        setUpTenant("aurora-tp");
        tabelaPrecoService.criar(TenantContext.get(), request("Varejo", List.of()));

        assertThatThrownBy(() -> tabelaPrecoService.criar(TenantContext.get(), request("Varejo", List.of())))
                .isInstanceOf(TabelaPrecoNomeDuplicadoException.class);
    }

    @Test
    @Transactional
    void updateReplacesTheWholeItemList() {
        UUID tenantId = setUpTenant("aurora-tp");
        Produto produtoA = novoProduto(tenantId, "P0001", new BigDecimal("10.00"));
        Produto produtoB = novoProduto(tenantId, "P0002", new BigDecimal("20.00"));

        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new TabelaPrecoItemInput(produtoA.getId(), new BigDecimal("15.00"), null))));

        var atualizada = tabelaPrecoService.atualizar(criada.id(),
                request("Varejo", List.of(new TabelaPrecoItemInput(produtoB.getId(), new BigDecimal("25.00"), null))));

        assertThat(atualizada.itens()).hasSize(1);
        assertThat(atualizada.itens().get(0).produtoId()).isEqualTo(produtoB.getId());
    }

    @Test
    @Transactional
    void rejectsItemWithUnknownProduto() {
        setUpTenant("aurora-tp");
        UUID produtoInexistente = UUID.randomUUID();

        assertThatThrownBy(() -> tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new TabelaPrecoItemInput(produtoInexistente, new BigDecimal("10.00"), null)))))
                .isInstanceOf(TabelaPrecoValidationException.class);
    }

    @Test
    @Transactional
    void deletesTabelaPrecoAndCascadesItems() {
        UUID tenantId = setUpTenant("aurora-tp");
        Produto produto = novoProduto(tenantId, "P0001", new BigDecimal("10.00"));
        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new TabelaPrecoItemInput(produto.getId(), new BigDecimal("15.00"), null))));

        tabelaPrecoService.excluir(criada.id());

        assertThatThrownBy(() -> tabelaPrecoService.buscarPorId(criada.id()))
                .isInstanceOf(TabelaPrecoNaoEncontradaException.class);
    }

    @Test
    @Transactional
    void listFiltersByAtivo() {
        setUpTenant("aurora-tp");
        var requestAtiva = new TabelaPrecoRequest("Ativa", ModoSelecaoProdutos.SELECIONAR_PRODUTOS, MetodoAjuste.MANUAL,
                null, null, null, Arredondamento.NAO_ARREDONDAR, LocalDate.of(2026, 1, 1), null, null, null, true, List.of());
        var requestInativa = new TabelaPrecoRequest("Inativa", ModoSelecaoProdutos.SELECIONAR_PRODUTOS, MetodoAjuste.MANUAL,
                null, null, null, Arredondamento.NAO_ARREDONDAR, LocalDate.of(2026, 1, 1), null, null, null, false, List.of());
        tabelaPrecoService.criar(TenantContext.get(), requestAtiva);
        tabelaPrecoService.criar(TenantContext.get(), requestInativa);

        var ativas = tabelaPrecoService.listar(null, true, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("nome").containsExactly("Ativa");
    }
}
```

- [ ] **Step 14: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=TabelaPrecoServiceTest,TabelaPrecoRepositoryTest`
Expected: PASS (7/7 + 4/4).

- [ ] **Step 15: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw clean test`
Expected: no NEW failures beyond the known pre-existing `com.meshsuite.payable.*` ordering issue.

- [ ] **Step 16: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V25__create_tabela_preco.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPreco.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoItem.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/ModoSelecaoProdutos.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/MetodoAjuste.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/OperacaoAjuste.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TipoValorAjuste.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/Arredondamento.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoRepository.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoSpecifications.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoNaoEncontradaException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoNomeDuplicadoException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoValidationException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemInput.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoItemResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoRequest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/dto/TabelaPrecoSummaryResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/TabelaPrecoRepositoryTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/TabelaPrecoServiceTest.java
git commit -m "feat(tabela-preco): add TabelaPreco/TabelaPrecoItem domain model and service"
```

---

### Task 2: `TabelaPrecoController`, exception wiring, and integration tests (backend)

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoExceptionHandler.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/produto/TabelaPrecoControllerTest.java`

**Interfaces:**
- Consumes: `TabelaPrecoService.listar/buscarPorId/criar/atualizar/excluir` (Task 1).
- Produces: `GET/POST/PUT/DELETE /api/tabelas-preco` — consumed by Task 4 (list) and Task 5 (form).

- [ ] **Step 1: Write `TabelaPrecoController.java`**

```java
package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.TabelaPrecoRequest;
import com.meshsuite.produto.dto.TabelaPrecoResponse;
import com.meshsuite.produto.dto.TabelaPrecoSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tabelas-preco")
public class TabelaPrecoController {

    private final TabelaPrecoService tabelaPrecoService;

    public TabelaPrecoController(TabelaPrecoService tabelaPrecoService) {
        this.tabelaPrecoService = tabelaPrecoService;
    }

    @GetMapping
    public Page<TabelaPrecoSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return tabelaPrecoService.listar(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public TabelaPrecoResponse buscarPorId(@PathVariable UUID id) {
        return tabelaPrecoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<TabelaPrecoResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                        @Valid @RequestBody TabelaPrecoRequest request) {
        TabelaPrecoResponse response = tabelaPrecoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public TabelaPrecoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody TabelaPrecoRequest request) {
        return tabelaPrecoService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        tabelaPrecoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Write `TabelaPrecoExceptionHandler.java`**

```java
package com.meshsuite.produto;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = TabelaPrecoController.class)
public class TabelaPrecoExceptionHandler {

    // Fallback for a race condition slipping past TabelaPrecoService's pre-check
    // (two concurrent requests for the same new nome) -- the DB's
    // UNIQUE(tenant_id, nome) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma tabela de preço cadastrada com este nome"));
    }
}
```

- [ ] **Step 3: Register the three named exceptions in `GlobalExceptionHandler.java`**

Append these three handlers to the end of the class, right after the existing `handleCorEstampaEmUso` method, before the final closing brace:

```java
    @ExceptionHandler(com.meshsuite.produto.TabelaPrecoNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleTabelaPrecoNaoEncontrada(
            com.meshsuite.produto.TabelaPrecoNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.TabelaPrecoNomeDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleTabelaPrecoNomeDuplicado(
            com.meshsuite.produto.TabelaPrecoNomeDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.TabelaPrecoValidationException.class)
    public ResponseEntity<Map<String, String>> handleTabelaPrecoValidation(
            com.meshsuite.produto.TabelaPrecoValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 4: Write `TabelaPrecoControllerTest.java`**

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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class TabelaPrecoControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, String produtoId) {
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

        Produto produto = new Produto();
        produto.setTenantId(tenant.getId());
        produto.setNome("Camiseta Polo");
        produto.setSku("P0001");
        produto.setPrecoVenda(new BigDecimal("59.90"));
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

    private String tabelaPrecoPayload(String nome, String produtoId) {
        return """
                {
                  "nome": "%s",
                  "modoSelecaoProdutos": "SELECIONAR_PRODUTOS",
                  "metodoAjuste": "MANUAL",
                  "arredondamento": "NAO_ARREDONDAR",
                  "inicioVigencia": "2026-01-01",
                  "itens": [
                    { "produtoId": "%s", "precoNestaTabela": 69.90, "percentualComissao": 5.00 }
                  ]
                }
                """.formatted(nome, produtoId);
    }

    @Test
    void createsListsUpdatesAndDeletesTabelaPreco() throws Exception {
        Contexto ctx = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/tabelas-preco").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctx.produtoId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Varejo"))
                .andExpect(jsonPath("$.itens[0].precoNestaTabela").value(69.90))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/tabelas-preco").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Varejo"));

        mockMvc.perform(put("/api/tabelas-preco/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo Atualizado", ctx.produtoId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Varejo Atualizado"));

        mockMvc.perform(delete("/api/tabelas-preco/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tabelas-preco/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateNomeWithConflict() throws Exception {
        Contexto ctx = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/tabelas-preco").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctx.produtoId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tabelas-preco").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctx.produtoId())))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingInicioVigenciaWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/tabelas-preco").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Sem Vigência",
                                  "modoSelecaoProdutos": "SELECIONAR_PRODUTOS",
                                  "metodoAjuste": "MANUAL",
                                  "arredondamento": "NAO_ARREDONDAR",
                                  "itens": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsTabelaPreco() throws Exception {
        Contexto ctxA = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/tabelas-preco").cookie(cookieA)
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

        mockMvc.perform(get("/api/tabelas-preco/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/tabelas-preco"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao-tp", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/tabelas-preco").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=TabelaPrecoControllerTest`
Expected: PASS (6/6).

- [ ] **Step 6: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw clean test`
Expected: no NEW failures beyond the known pre-existing `com.meshsuite.payable.*` ordering issue.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/TabelaPrecoExceptionHandler.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/TabelaPrecoControllerTest.java
git commit -m "feat(tabela-preco): add TabelaPrecoController and exception wiring"
```

---

### Task 3: Frontend foundation — API layer and pricing formula module

**Files:**
- Create: `mesh-suite-frontend/src/api/tabelasPreco.ts`
- Create: `mesh-suite-frontend/src/utils/calculoTabelaPreco.ts`
- Test: `mesh-suite-frontend/src/utils/__tests__/calculoTabelaPreco.spec.ts`

**Interfaces:**
- Consumes: `GET/POST/PUT/DELETE /api/tabelas-preco` (Task 2).
- Produces: `listarTabelasPreco`, `buscarTabelaPreco`, `criarTabelaPreco`, `atualizarTabelaPreco`, `excluirTabelaPreco` in `@/api/tabelasPreco`; `calcularPrecoAjustado(precoBase: number, regra: RegraAjuste): number` and the `RegraAjuste` type in `@/utils/calculoTabelaPreco`. Tasks 4 and 5 consume the API module; Task 5 also consumes the formula module.

- [ ] **Step 1: Write `api/tabelasPreco.ts`**

```ts
import { apiClient } from './client'

export type ModoSelecaoProdutos = 'TODOS_PRODUTOS' | 'SELECIONAR_PRODUTOS'
export type MetodoAjuste = 'AUTOMATICO' | 'MANUAL'
export type OperacaoAjuste = 'SOMAR' | 'SUBTRAIR'
export type TipoValorAjuste = 'REAL' | 'PERCENTUAL'
export type Arredondamento = 'NAO_ARREDONDAR' | 'TERMINAR_EM_0' | 'TERMINAR_EM_9' | 'TERMINAR_EM_90' | 'TERMINAR_EM_99'

export interface TabelaPrecoItemInput {
  produtoId: string
  precoNestaTabela: number | null
  percentualComissao: number | null
}

export interface TabelaPrecoItemResponse extends TabelaPrecoItemInput {
  produtoNome: string
  produtoSku: string
  precoCadastrado: number
}

export interface TabelaPrecoRequest {
  nome: string
  modoSelecaoProdutos: ModoSelecaoProdutos
  metodoAjuste: MetodoAjuste
  operacaoAjuste: OperacaoAjuste | null
  tipoValorAjuste: TipoValorAjuste | null
  valorAjuste: number | null
  arredondamento: Arredondamento
  inicioVigencia: string
  terminoVigencia: string | null
  valorMinimoVenda: number | null
  percentualComissaoPadrao: number | null
  ativo: boolean | null
  itens: TabelaPrecoItemInput[]
}

export interface TabelaPrecoResponse {
  id: string
  nome: string
  modoSelecaoProdutos: ModoSelecaoProdutos
  metodoAjuste: MetodoAjuste
  operacaoAjuste: OperacaoAjuste | null
  tipoValorAjuste: TipoValorAjuste | null
  valorAjuste: number | null
  arredondamento: Arredondamento
  inicioVigencia: string
  terminoVigencia: string | null
  valorMinimoVenda: number | null
  percentualComissaoPadrao: number | null
  ativo: boolean
  criadoEm: string
  itens: TabelaPrecoItemResponse[]
}

export interface TabelaPrecoSummary {
  id: string
  nome: string
  metodoAjuste: MetodoAjuste
  operacaoAjuste: OperacaoAjuste | null
  tipoValorAjuste: TipoValorAjuste | null
  valorAjuste: number | null
  inicioVigencia: string
  terminoVigencia: string | null
  ativo: boolean
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarTabelasPrecoParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listarTabelasPreco(params: ListarTabelasPrecoParams): Promise<Page<TabelaPrecoSummary>> {
  const { data } = await apiClient.get<Page<TabelaPrecoSummary>>('/tabelas-preco', { params })
  return data
}

export async function buscarTabelaPreco(id: string): Promise<TabelaPrecoResponse> {
  const { data } = await apiClient.get<TabelaPrecoResponse>(`/tabelas-preco/${id}`)
  return data
}

export async function criarTabelaPreco(payload: TabelaPrecoRequest): Promise<TabelaPrecoResponse> {
  const { data } = await apiClient.post<TabelaPrecoResponse>('/tabelas-preco', payload)
  return data
}

export async function atualizarTabelaPreco(id: string, payload: TabelaPrecoRequest): Promise<TabelaPrecoResponse> {
  const { data } = await apiClient.put<TabelaPrecoResponse>(`/tabelas-preco/${id}`, payload)
  return data
}

export async function excluirTabelaPreco(id: string): Promise<void> {
  await apiClient.delete(`/tabelas-preco/${id}`)
}
```

- [ ] **Step 2: Write the failing `calculoTabelaPreco.spec.ts`**

```ts
import { describe, it, expect } from 'vitest'
import { calcularPrecoAjustado, type RegraAjuste } from '../calculoTabelaPreco'

describe('calcularPrecoAjustado', () => {
  it('somar + real, sem arredondamento', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 10, arredondamento: 'NAO_ARREDONDAR' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(110, 2)
  })

  it('somar + percentual, sem arredondamento', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'PERCENTUAL', valorAjuste: 10, arredondamento: 'NAO_ARREDONDAR' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(110, 2)
  })

  it('subtrair + real, sem arredondamento', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SUBTRAIR', tipoValorAjuste: 'REAL', valorAjuste: 10, arredondamento: 'NAO_ARREDONDAR' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(90, 2)
  })

  it('subtrair + percentual, sem arredondamento', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SUBTRAIR', tipoValorAjuste: 'PERCENTUAL', valorAjuste: 20, arredondamento: 'NAO_ARREDONDAR' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(80, 2)
  })

  it('terminar em 0 arredonda pra cima', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_0' }
    expect(calcularPrecoAjustado(117.32, regra)).toBeCloseTo(120, 2)
  })

  it('terminar em 9 arredonda pra cima', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_9' }
    expect(calcularPrecoAjustado(117.32, regra)).toBeCloseTo(119, 2)
  })

  it('terminar em ,90 arredonda pra cima', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_90' }
    expect(calcularPrecoAjustado(117.32, regra)).toBeCloseTo(117.90, 2)
  })

  it('terminar em ,99 arredonda pra cima', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_99' }
    expect(calcularPrecoAjustado(117.32, regra)).toBeCloseTo(117.99, 2)
  })

  it('valor exato já na regra não muda', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_0' }
    expect(calcularPrecoAjustado(120, regra)).toBeCloseTo(120, 2)
  })

  it('combina ajuste percentual com arredondamento terminar em 9', () => {
    // base 100, +12% = 112.00 -> arredonda pra próximo terminando em 9 (119.00)
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'PERCENTUAL', valorAjuste: 12, arredondamento: 'TERMINAR_EM_9' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(119, 2)
  })
})
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd mesh-suite-frontend && npm test -- --run src/utils/__tests__/calculoTabelaPreco.spec.ts`
Expected: FAIL — `calculoTabelaPreco` module does not exist yet.

- [ ] **Step 4: Write `calculoTabelaPreco.ts`**

```ts
export type OperacaoAjuste = 'SOMAR' | 'SUBTRAIR'
export type TipoValorAjuste = 'REAL' | 'PERCENTUAL'
export type Arredondamento = 'NAO_ARREDONDAR' | 'TERMINAR_EM_0' | 'TERMINAR_EM_9' | 'TERMINAR_EM_90' | 'TERMINAR_EM_99'

export interface RegraAjuste {
  operacaoAjuste: OperacaoAjuste
  tipoValorAjuste: TipoValorAjuste
  valorAjuste: number
  arredondamento: Arredondamento
}

// Rounding always goes UP (never below the adjusted price). Every candidate
// value in a rounding rule's set is `k * period + offset` for integer k >= 0,
// expressed in cents to avoid floating-point drift:
//   NAO_ARREDONDAR:  no candidate set, value returned as-is (rounded to the cent)
//   TERMINAR_EM_0:   period=1000, offset=0    (...,100.00, 110.00, 120.00,...)
//   TERMINAR_EM_9:   period=1000, offset=900  (...,99.00, 109.00, 119.00,...)
//   TERMINAR_EM_90:  period=100,  offset=90   (...,ends in ,90)
//   TERMINAR_EM_99:  period=100,  offset=99   (...,ends in ,99)
const REGRAS_ARREDONDAMENTO: Record<Exclude<Arredondamento, 'NAO_ARREDONDAR'>, { period: number; offset: number }> = {
  TERMINAR_EM_0: { period: 1000, offset: 0 },
  TERMINAR_EM_9: { period: 1000, offset: 900 },
  TERMINAR_EM_90: { period: 100, offset: 90 },
  TERMINAR_EM_99: { period: 100, offset: 99 },
}

function arredondarParaCima(valor: number, arredondamento: Arredondamento): number {
  const centavos = Math.round(valor * 100)
  if (arredondamento === 'NAO_ARREDONDAR') {
    return centavos / 100
  }
  const { period, offset } = REGRAS_ARREDONDAMENTO[arredondamento]
  const k = Math.ceil((centavos - offset) / period)
  const alvoCentavos = k * period + offset
  return alvoCentavos / 100
}

export function calcularPrecoAjustado(precoBase: number, regra: RegraAjuste): number {
  let ajustado: number
  if (regra.operacaoAjuste === 'SOMAR') {
    ajustado = regra.tipoValorAjuste === 'REAL'
      ? precoBase + regra.valorAjuste
      : precoBase * (1 + regra.valorAjuste / 100)
  } else {
    ajustado = regra.tipoValorAjuste === 'REAL'
      ? precoBase - regra.valorAjuste
      : precoBase * (1 - regra.valorAjuste / 100)
  }
  return arredondarParaCima(ajustado, regra.arredondamento)
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd mesh-suite-frontend && npm test -- --run src/utils/__tests__/calculoTabelaPreco.spec.ts`
Expected: PASS (10/10).

- [ ] **Step 6: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npm test -- --run`
Expected: no regressions.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/api/tabelasPreco.ts \
        mesh-suite-frontend/src/utils/calculoTabelaPreco.ts \
        mesh-suite-frontend/src/utils/__tests__/calculoTabelaPreco.spec.ts
git commit -m "feat(tabela-preco): add API layer and pricing calculation module"
```

---

### Task 4: `TabelasPrecoListView.vue` — routing and sidebar

**Files:**
- Create: `mesh-suite-frontend/src/views/TabelasPrecoListView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/TabelasPrecoListView.spec.ts`

**Interfaces:**
- Consumes: `listarTabelasPreco`, `excluirTabelaPreco` from `@/api/tabelasPreco` (Task 3).
- Produces: the `/tabelas-preco` route. Task 5 (form) consumes the `tabelas-preco`/`tabelas-preco-novo`/`tabelas-preco-editar` route names this task defines.

- [ ] **Step 1: Write `TabelasPrecoListView.vue`**

```vue
<template>
  <AppShell title="Tabelas de Preço">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar tabela por nome..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.ativo" data-test="filtro-status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="true">Ativo</option>
        <option value="false">Inativo</option>
      </select>
      <button type="button" class="btn-primary" data-test="nova-tabela" @click="novaTabela">+ Nova Tabela</button>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nome da Tabela</th>
            <th>Método de Ajuste</th>
            <th>Início</th>
            <th>Término</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tabela in pagina.content" :key="tabela.id">
            <td>{{ tabela.nome }}</td>
            <td>{{ resumoMetodoAjuste(tabela) }}</td>
            <td>{{ formatarData(tabela.inicioVigencia) }}</td>
            <td>{{ tabela.terminoVigencia ? formatarData(tabela.terminoVigencia) : '—' }}</td>
            <td><span class="badge" :class="tabela.ativo ? 'badge-ATIVO' : 'badge-INATIVO'">{{ tabela.ativo ? 'Ativo' : 'Inativo' }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(tabela.id, $event)"
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
        v-if="tabelaAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarTabela(tabelaAcoesAtual.id)">Editar</div>
        <div data-test="acao-excluir" class="acao-excluir" @click="excluir(tabelaAcoesAtual)">Excluir</div>
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
  listarTabelasPreco,
  excluirTabelaPreco,
  type TabelaPrecoSummary,
  type Page as ApiPage,
} from '@/api/tabelasPreco'

const router = useRouter()

const filtros = reactive({ busca: '', ativo: '' })
const pagina = ref<ApiPage<TabelaPrecoSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const tabelaAcoesAtual = computed(() =>
  pagina.value.content.find((t) => t.id === acoesAbertas.value) ?? null,
)

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function resumoMetodoAjuste(tabela: TabelaPrecoSummary) {
  if (tabela.metodoAjuste === 'MANUAL') {
    return 'Manual'
  }
  const operacao = tabela.operacaoAjuste === 'SUBTRAIR' ? 'Subtrair' : 'Somar'
  const valor = tabela.tipoValorAjuste === 'PERCENTUAL'
    ? `${tabela.valorAjuste ?? 0}%`
    : (tabela.valorAjuste ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
  return `Automático · ${operacao} ${valor}`
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listarTabelasPreco({
      busca: filtros.busca || undefined,
      ativo: filtros.ativo === '' ? undefined : filtros.ativo === 'true',
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de tabelas de preço.'
  }
}

function novaTabela() {
  router.push({ name: 'tabelas-preco-novo' })
}

function editarTabela(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'tabelas-preco-editar', params: { id } })
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

async function excluir(tabela: TabelaPrecoSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir a tabela de preço "${tabela.nome}"?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirTabelaPreco(tabela.id)
    await carregar(pagina.value.number)
  } catch (err: any) {
    erro.value = err?.response?.data?.mensagem ?? 'Não foi possível excluir a tabela de preço.'
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

- [ ] **Step 2: Wire up the routes**

In `mesh-suite-frontend/src/router/index.ts`, add this import alongside the existing ones:

```ts
import TabelasPrecoListView from '@/views/TabelasPrecoListView.vue'
```

Note: Task 5 will add `TabelaPrecoFormView` as a separate import and its two routes right after these — this task only wires up the list route:

```ts
    { path: '/tabelas-preco', name: 'tabelas-preco', component: TabelasPrecoListView },
```

Add it to the `routes` array right after the `/cores-estampas/:id/editar` entry.

- [ ] **Step 3: Wire up the sidebar**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, find the `Tab. Preços` entry inside the `vendas` group (currently `route: null`):

```ts
      { icon: '💰', label: 'Tab. Preços', route: null },
```

Change it to:

```ts
      { icon: '💰', label: 'Tab. Preços', route: '/tabelas-preco' },
```

- [ ] **Step 4: Write `TabelasPrecoListView.spec.ts`**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import TabelasPrecoListView from '@/views/TabelasPrecoListView.vue'
import * as tabelasPrecoApi from '@/api/tabelasPreco'

vi.mock('@/api/tabelasPreco')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/tabelas-preco', name: 'tabelas-preco', component: TabelasPrecoListView },
      { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: { template: '<div />' } },
      { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/tabelas-preco')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> -- stub it here so it
    // renders in place instead, keeping wrapper.find() queries working.
    wrapper: mount(TabelasPrecoListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const tabelaExemplo = {
  id: 'tp-1',
  nome: 'Varejo',
  metodoAjuste: 'AUTOMATICO' as const,
  operacaoAjuste: 'SOMAR' as const,
  tipoValorAjuste: 'REAL' as const,
  valorAjuste: 10,
  inicioVigencia: '2026-01-01',
  terminoVigencia: null,
  ativo: true,
}

describe('TabelasPrecoListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the tabela de preço list', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockResolvedValue({
      content: [tabelaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Varejo')
    expect(wrapper.text()).toContain('Automático · Somar')
  })

  it('shows Manual for tabelas without an automatic rule', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockResolvedValue({
      content: [{ ...tabelaExemplo, metodoAjuste: 'MANUAL', operacaoAjuste: null, tipoValorAjuste: null, valorAjuste: null }],
      totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Manual')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de tabelas de preço.')
  })

  it('navigates to the new-tabela route when the button is clicked', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-tabela"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('tabelas-preco-novo')
  })

  it('deletes a tabela after confirmation and reloads the list', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockResolvedValue({
      content: [tabelaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(tabelasPrecoApi.excluirTabelaPreco).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(tabelasPrecoApi.excluirTabelaPreco).toHaveBeenCalledWith('tp-1')
  })
})
```

- [ ] **Step 5: Run the new test to verify it passes**

Run: `cd mesh-suite-frontend && npm test -- --run src/views/__tests__/TabelasPrecoListView.spec.ts`
Expected: PASS (5/5).

- [ ] **Step 6: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npm test -- --run`
Expected: no regressions. `TabelaPrecoFormView` doesn't exist yet, so the `/tabelas-preco/novo` and `/tabelas-preco/:id/editar` routes are only added in Task 5 — that's expected, not a bug in this task.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/views/TabelasPrecoListView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/views/__tests__/TabelasPrecoListView.spec.ts
git commit -m "feat(tabela-preco): add TabelasPrecoListView, routing and sidebar"
```

---

### Task 5: `TabelaPrecoFormView.vue` — regras, itens, live preview

**Files:**
- Create: `mesh-suite-frontend/src/views/TabelaPrecoFormView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/TabelaPrecoFormView.spec.ts`

**Interfaces:**
- Consumes: `buscarTabelaPreco`/`criarTabelaPreco`/`atualizarTabelaPreco` from `@/api/tabelasPreco` (Task 3), `calcularPrecoAjustado`/`RegraAjuste` from `@/utils/calculoTabelaPreco` (Task 3), `listarProdutos` from `@/api/produtos` (already exists), the `tabelas-preco`/`tabelas-preco-novo`/`tabelas-preco-editar` route names (Task 4).
- Produces: nothing new — this is the final task, it completes the Tabela de Preço feature.

**Behavior notes (read before implementing):**
- **When `modoSelecaoProdutos` is set to `TODOS_PRODUTOS`** (either because the form starts that way on creation, or the user switches to it), the item list is populated from every active produto (`listarProdutos({ status: 'ATIVO', size: 1000 })`), with `precoNestaTabela` computed via `calcularPrecoAjustado` if `metodoAjuste === 'AUTOMATICO'`, or `null` (Pendente) if `MANUAL`. Switching the mode away and back re-populates from scratch.
- **Per spec §5 ("recalcula sempre que a regra muda"), in `TODOS_PRODUTOS` mode every item's `precoNestaTabela` is recomputed live whenever any ajuste rule field changes** (`metodoAjuste`, `operacaoAjuste`, `tipoValorAjuste`, `valorAjuste`, `arredondamento`) — implemented as a `watch` over those fields that, when `modoSelecaoProdutos === 'TODOS_PRODUTOS'`, remaps every item's `precoNestaTabela` through `precoParaNovoItem(item.precoCadastrado)`. This is deliberately reactive: a price the user typed by hand into an item row is overwritten the next time a rule field changes, because in this mode items stay fully rule-driven — there is no "detach this one item from the rule" concept in the spec. The per-item reset button still calls the same recompute for a single item, which matters mainly once `SELECIONAR_PRODUTOS` items are involved (see below).
- **`modoSelecaoProdutos === 'SELECIONAR_PRODUTOS'`**: the item list starts empty (both on creation and if editing an existing tabela already in this mode, its saved items load normally). A search box (mirroring `PurchaseOrderFormView.vue`'s produto search pattern) lets the user find and add individual active produtos not already in the list; each newly-added item gets its `precoNestaTabela` computed once at add-time via the current rule (or `null` if `MANUAL`). Unlike `TODOS_PRODUTOS`, changing the rule afterward does NOT retroactively touch already-added items — spec §5's "recalcula sempre que a regra muda" clause is scoped to `TODOS_PRODUTOS` only; for `SELECIONAR_PRODUTOS` items, only a direct edit or that item's reset button changes its price.
- **Preenchido/Pendente/Todos filter** (spec §5): a `<select>` above the items table filters the visible rows by whether `precoNestaTabela` is set. Filtering only affects which rows render — the underlying `itens` array index used for `removerItem`/`resetarItem`/data-test attributes always refers to the real (unfiltered) position, via a computed `itensExibidos` that pairs each visible item with its real index.
- **Margem** column: computed inline in the template as `(item.precoNestaTabela - item.precoCadastrado) / item.precoCadastrado`, not stored anywhere.

- [ ] **Step 1: Write `TabelaPrecoFormView.vue`**

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Tabela de Preço' : 'Nova Tabela de Preço'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Regras da Tabela</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Nome da tabela de preços *</label>
            <input v-model="form.nome" data-test="nome" />
            <p v-if="erros.nome" class="field-error">{{ erros.nome }}</p>
          </div>
          <div>
            <label class="field-label">Como quer escolher os produtos desta tabela? *</label>
            <select v-model="form.modoSelecaoProdutos" data-test="modo-selecao" @change="aoMudarModoSelecao">
              <option value="TODOS_PRODUTOS">Todos os Produtos</option>
              <option value="SELECIONAR_PRODUTOS">Selecionar os Produtos</option>
            </select>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Método de ajuste *</label>
            <div class="toggle-pair">
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.metodoAjuste === 'AUTOMATICO' }" data-test="metodo-automatico" @click="form.metodoAjuste = 'AUTOMATICO'">Automático</button>
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.metodoAjuste === 'MANUAL' }" data-test="metodo-manual" @click="form.metodoAjuste = 'MANUAL'">Manual</button>
            </div>
          </div>
          <div v-if="form.metodoAjuste === 'AUTOMATICO'">
            <label class="field-label">Operação</label>
            <div class="toggle-pair">
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.operacaoAjuste === 'SOMAR' }" data-test="operacao-somar" @click="form.operacaoAjuste = 'SOMAR'">Somar</button>
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.operacaoAjuste === 'SUBTRAIR' }" data-test="operacao-subtrair" @click="form.operacaoAjuste = 'SUBTRAIR'">Subtrair</button>
            </div>
            <div class="toggle-pair" style="margin-top: 6px">
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.tipoValorAjuste === 'REAL' }" data-test="tipo-real" @click="form.tipoValorAjuste = 'REAL'">R$</button>
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.tipoValorAjuste === 'PERCENTUAL' }" data-test="tipo-percentual" @click="form.tipoValorAjuste = 'PERCENTUAL'">%</button>
              <input v-model.number="form.valorAjuste" type="number" step="0.01" min="0" data-test="valor-ajuste" />
            </div>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Arredondamento *</label>
            <select v-model="form.arredondamento" data-test="arredondamento">
              <option value="NAO_ARREDONDAR">Não arredondar</option>
              <option value="TERMINAR_EM_0">Terminar em 0</option>
              <option value="TERMINAR_EM_9">Terminar em 9</option>
              <option value="TERMINAR_EM_90">Terminar em ,90</option>
              <option value="TERMINAR_EM_99">Terminar em ,99</option>
            </select>
          </div>
          <div class="grid grid-2">
            <div>
              <label class="field-label">Início de vigência *</label>
              <input v-model="form.inicioVigencia" type="date" data-test="inicio-vigencia" />
              <p v-if="erros.inicioVigencia" class="field-error">{{ erros.inicioVigencia }}</p>
            </div>
            <div>
              <label class="field-label">Término de vigência</label>
              <input v-model="form.terminoVigencia" type="date" data-test="termino-vigencia" />
            </div>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Valor mínimo para venda (R$)</label>
            <input v-model.number="form.valorMinimoVenda" type="number" step="0.01" min="0" />
          </div>
          <div>
            <label class="field-label">% de Comissão (padrão dos itens)</label>
            <input v-model.number="form.percentualComissaoPadrao" type="number" step="0.01" min="0" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Itens na Tabela</h2>

        <div v-if="form.modoSelecaoProdutos === 'SELECIONAR_PRODUTOS'" class="busca-wrapper">
          <input
            v-model="produtoBusca"
            placeholder="Buscar produto por nome ou SKU..."
            data-test="produto-busca"
            autocomplete="off"
            @input="buscarProdutos"
          />
          <ul v-if="resultadosProdutos.length" class="dropdown-busca" data-test="produto-resultados">
            <li v-for="p in resultadosProdutos" :key="p.id" @click="adicionarProduto(p)">{{ p.nome }} ({{ p.sku }})</li>
          </ul>
        </div>

        <div v-if="itens.length" class="filtro-itens">
          <label class="field-label">Mostrar</label>
          <select v-model="filtroPreenchimento" data-test="filtro-preenchimento">
            <option value="TODOS">Todos</option>
            <option value="PREENCHIDO">Preenchido</option>
            <option value="PENDENTE">Pendente</option>
          </select>
        </div>

        <table v-if="itensExibidos.length" class="tabela-itens">
          <thead>
            <tr>
              <th>Nome do item</th>
              <th>Código</th>
              <th>Preço cadastrado</th>
              <th>Preço nesta tabela</th>
              <th>Margem</th>
              <th>% Comissão</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="{ item, indexReal } in itensExibidos" :key="item.produtoId">
              <td>{{ item.produtoNome }}</td>
              <td>{{ item.produtoSku }}</td>
              <td>{{ formatarPreco(item.precoCadastrado) }}</td>
              <td>
                <input
                  v-model.number="item.precoNestaTabela"
                  type="number"
                  step="0.01"
                  min="0"
                  :data-test="`item-preco-${indexReal}`"
                />
                <button type="button" :data-test="`item-reset-${indexReal}`" @click="resetarItem(indexReal)" title="Recalcular pela regra">↺</button>
              </td>
              <td>{{ margem(item) }}</td>
              <td>
                <input v-model.number="item.percentualComissao" type="number" step="0.01" min="0" :data-test="`item-comissao-${indexReal}`" />
              </td>
              <td><button type="button" class="btn-remover" :data-test="`item-remover-${indexReal}`" @click="removerItem(indexReal)">✕</button></td>
            </tr>
          </tbody>
        </table>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Tabela</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  buscarTabelaPreco,
  criarTabelaPreco,
  atualizarTabelaPreco,
  type TabelaPrecoRequest,
  type TabelaPrecoItemInput,
} from '@/api/tabelasPreco'
import { listarProdutos, type ProdutoSummary } from '@/api/produtos'
import { calcularPrecoAjustado, type RegraAjuste } from '@/utils/calculoTabelaPreco'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

interface ItemForm extends TabelaPrecoItemInput {
  produtoNome: string
  produtoSku: string
  precoCadastrado: number
}

function novoFormulario(): TabelaPrecoRequest {
  return {
    nome: '',
    modoSelecaoProdutos: 'TODOS_PRODUTOS',
    metodoAjuste: 'AUTOMATICO',
    operacaoAjuste: 'SOMAR',
    tipoValorAjuste: 'REAL',
    valorAjuste: 0,
    arredondamento: 'NAO_ARREDONDAR',
    inicioVigencia: new Date().toISOString().slice(0, 10),
    terminoVigencia: null,
    valorMinimoVenda: null,
    percentualComissaoPadrao: null,
    ativo: true,
    itens: [],
  }
}

const form = reactive<TabelaPrecoRequest>(novoFormulario())
const itens = ref<ItemForm[]>([])
const erros = reactive<{ nome?: string; inicioVigencia?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

const produtoBusca = ref('')
const resultadosProdutos = ref<ProdutoSummary[]>([])

const filtroPreenchimento = ref<'TODOS' | 'PREENCHIDO' | 'PENDENTE'>('TODOS')

const itensExibidos = computed(() =>
  itens.value
    .map((item, indexReal) => ({ item, indexReal }))
    .filter(({ item }) => {
      if (filtroPreenchimento.value === 'PREENCHIDO') return item.precoNestaTabela !== null
      if (filtroPreenchimento.value === 'PENDENTE') return item.precoNestaTabela === null
      return true
    }),
)

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function margem(item: ItemForm) {
  if (!item.precoCadastrado || item.precoNestaTabela === null) {
    return '0%'
  }
  const pct = ((item.precoNestaTabela - item.precoCadastrado) / item.precoCadastrado) * 100
  return `${pct.toFixed(0)}%`
}

function regraAtual(): RegraAjuste {
  return {
    operacaoAjuste: form.operacaoAjuste ?? 'SOMAR',
    tipoValorAjuste: form.tipoValorAjuste ?? 'REAL',
    valorAjuste: form.valorAjuste ?? 0,
    arredondamento: form.arredondamento,
  }
}

function precoParaNovoItem(precoBase: number): number | null {
  return form.metodoAjuste === 'AUTOMATICO' ? calcularPrecoAjustado(precoBase, regraAtual()) : null
}

// Spec §5: "recalcula sempre que a regra muda" -- in TODOS_PRODUTOS mode, every
// item's price is rule-driven and gets overwritten live whenever the rule
// changes. Scoped to TODOS_PRODUTOS only: SELECIONAR_PRODUTOS items are only
// touched by a direct edit or their own reset button.
watch(
  () => [form.metodoAjuste, form.operacaoAjuste, form.tipoValorAjuste, form.valorAjuste, form.arredondamento],
  () => {
    if (form.modoSelecaoProdutos !== 'TODOS_PRODUTOS') {
      return
    }
    itens.value = itens.value.map((item) => ({
      ...item,
      precoNestaTabela: precoParaNovoItem(item.precoCadastrado),
    }))
  },
)

async function popularTodosOsProdutos() {
  try {
    const pagina = await listarProdutos({ status: 'ATIVO', size: 1000 })
    itens.value = pagina.content.map((p) => ({
      produtoId: p.id,
      produtoNome: p.nome,
      produtoSku: p.sku,
      precoCadastrado: p.precoVenda,
      precoNestaTabela: precoParaNovoItem(p.precoVenda),
      percentualComissao: form.percentualComissaoPadrao,
    }))
  } catch {
    erroGeral.value = 'Não foi possível carregar a lista de produtos.'
  }
}

function aoMudarModoSelecao() {
  if (form.modoSelecaoProdutos === 'TODOS_PRODUTOS') {
    popularTodosOsProdutos()
  } else {
    itens.value = []
  }
}

async function buscarProdutos() {
  if (!produtoBusca.value.trim()) {
    resultadosProdutos.value = []
    return
  }
  try {
    const pagina = await listarProdutos({ busca: produtoBusca.value, status: 'ATIVO', size: 5 })
    resultadosProdutos.value = pagina.content.filter((p) => !itens.value.some((i) => i.produtoId === p.id))
  } catch {
    resultadosProdutos.value = []
  }
}

function adicionarProduto(produto: ProdutoSummary) {
  itens.value.push({
    produtoId: produto.id,
    produtoNome: produto.nome,
    produtoSku: produto.sku,
    precoCadastrado: produto.precoVenda,
    precoNestaTabela: precoParaNovoItem(produto.precoVenda),
    percentualComissao: form.percentualComissaoPadrao,
  })
  produtoBusca.value = ''
  resultadosProdutos.value = []
}

function removerItem(index: number) {
  itens.value.splice(index, 1)
}

function resetarItem(index: number) {
  const item = itens.value[index]
  item.precoNestaTabela = precoParaNovoItem(item.precoCadastrado)
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const tabela = await buscarTabelaPreco(id)
      form.nome = tabela.nome
      form.modoSelecaoProdutos = tabela.modoSelecaoProdutos
      form.metodoAjuste = tabela.metodoAjuste
      form.operacaoAjuste = tabela.operacaoAjuste
      form.tipoValorAjuste = tabela.tipoValorAjuste
      form.valorAjuste = tabela.valorAjuste
      form.arredondamento = tabela.arredondamento
      form.inicioVigencia = tabela.inicioVigencia
      form.terminoVigencia = tabela.terminoVigencia
      form.valorMinimoVenda = tabela.valorMinimoVenda
      form.percentualComissaoPadrao = tabela.percentualComissaoPadrao
      form.ativo = tabela.ativo
      itens.value = tabela.itens.map((i) => ({
        produtoId: i.produtoId,
        produtoNome: i.produtoNome,
        produtoSku: i.produtoSku,
        precoCadastrado: i.precoCadastrado,
        precoNestaTabela: i.precoNestaTabela,
        percentualComissao: i.percentualComissao,
      }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da tabela de preço.'
    }
  } else if (form.modoSelecaoProdutos === 'TODOS_PRODUTOS') {
    await popularTodosOsProdutos()
  }
})

function validar(): boolean {
  erros.nome = form.nome.trim() ? undefined : 'Campo obrigatório'
  erros.inicioVigencia = form.inicioVigencia ? undefined : 'Campo obrigatório'
  return !erros.nome && !erros.inicioVigencia
}

function paraPayload(): TabelaPrecoRequest {
  return {
    ...form,
    valorAjuste: form.metodoAjuste === 'AUTOMATICO' ? Number(form.valorAjuste) || 0 : null,
    itens: itens.value.map(({ produtoId, precoNestaTabela, percentualComissao }) => ({
      produtoId,
      precoNestaTabela,
      percentualComissao,
    })),
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
      await atualizarTabelaPreco(id, payload)
    } else {
      await criarTabelaPreco(payload)
    }
    router.push({ name: 'tabelas-preco' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma tabela de preço cadastrada com este nome.'
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
  router.push({ name: 'tabelas-preco' })
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

.toggle-pair {
  display: flex;
  gap: 6px;
}

.toggle-btn {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.toggle-btn--ativo {
  background: var(--pm-accent);
  color: var(--pm-white);
  border-color: var(--pm-accent);
}

.busca-wrapper {
  position: relative;
  margin-bottom: 10px;
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

.filtro-itens {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.filtro-itens select {
  width: auto;
}

.tabela-itens {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
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

- [ ] **Step 2: Wire up the create/edit routes**

In `mesh-suite-frontend/src/router/index.ts`, add this import alongside the existing ones (right after `TabelasPrecoListView`):

```ts
import TabelaPrecoFormView from '@/views/TabelaPrecoFormView.vue'
```

Add these two routes to the `routes` array, right after the `/tabelas-preco` entry Task 4 added:

```ts
    { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: TabelaPrecoFormView },
    { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: TabelaPrecoFormView },
```

- [ ] **Step 3: Write `TabelaPrecoFormView.spec.ts`**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import TabelaPrecoFormView from '@/views/TabelaPrecoFormView.vue'
import * as tabelasPrecoApi from '@/api/tabelasPreco'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/tabelasPreco')
vi.mock('@/api/produtos')

function mountWithRouter(path = '/tabelas-preco/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/tabelas-preco', name: 'tabelas-preco', component: { template: '<div />' } },
      { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: TabelaPrecoFormView },
      { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: TabelaPrecoFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(TabelaPrecoFormView, { global: { plugins: [router] } }),
  }))
}

const produtoAtivo = { id: 'prod-1', nome: 'Camiseta Polo', sku: 'P0001', marca: '', precoVenda: 100, quantidadeEstoque: 10, status: 'ATIVO' as const }

describe('TabelaPrecoFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field errors when nome/inicioVigencia are blank on submit', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('')
    await wrapper.find('[data-test="inicio-vigencia"]').setValue('')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(tabelasPrecoApi.criarTabelaPreco).not.toHaveBeenCalled()
  })

  it('populates items from all active produtos in TODOS_PRODUTOS mode, with live-calculated prices', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    // default rule is AUTOMATICO/SOMAR/REAL/valorAjuste=0 -> preço = precoVenda
    expect(wrapper.text()).toContain('Camiseta Polo')
    const precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(100, 2)
  })

  it('recalculates every item live when the ajuste rule changes in TODOS_PRODUTOS mode', async () => {
    // Per spec §5 ("recalcula sempre que a regra muda"): TODOS_PRODUTOS items stay
    // fully rule-driven, so a manually typed price is overwritten the next time a
    // rule field changes -- this is deliberate, not a bug.
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="item-preco-0"]').setValue('250')
    await wrapper.find('[data-test="valor-ajuste"]').setValue('50')
    await flushPromises()

    const precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(150, 2) // produtoAtivo.precoVenda=100, SOMAR+REAL+50
  })

  it('starts empty in SELECIONAR_PRODUTOS mode and adds an item via search', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="modo-selecao"]').setValue('SELECIONAR_PRODUTOS')
    await flushPromises()
    expect(wrapper.find('.tabela-itens').exists()).toBe(false)

    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    await wrapper.find('[data-test="produto-busca"]').setValue('Camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('does not auto-recalculate SELECIONAR_PRODUTOS items when the rule changes, but the reset button does', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="modo-selecao"]').setValue('SELECIONAR_PRODUTOS')
    await flushPromises()

    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    await wrapper.find('[data-test="produto-busca"]').setValue('Camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="item-preco-0"]').setValue('999')
    await wrapper.find('[data-test="valor-ajuste"]').setValue('20')
    await flushPromises()

    let precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(999, 2)

    await wrapper.find('[data-test="item-reset-0"]').trigger('click')
    await flushPromises()

    precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(120, 2) // produtoAtivo.precoVenda=100, SOMAR+REAL+20
  })

  it('filters the item list by Preenchido/Pendente', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoAtivo, { ...produtoAtivo, id: 'prod-2', nome: 'Bermuda', sku: 'P0002' }],
      totalElements: 2, totalPages: 1, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    // Switching to MANUAL clears every item's price to Pendente (null) via the
    // same rule-change watcher, since TODOS_PRODUTOS items are always rule-driven.
    await wrapper.find('[data-test="metodo-manual"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="item-preco-0"]').setValue('120')
    await flushPromises()

    await wrapper.find('[data-test="filtro-preenchimento"]').setValue('PENDENTE')
    await flushPromises()
    expect(wrapper.text()).toContain('Bermuda')
    expect(wrapper.text()).not.toContain('Camiseta Polo')

    await wrapper.find('[data-test="filtro-preenchimento"]').setValue('PREENCHIDO')
    await flushPromises()
    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).not.toContain('Bermuda')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    vi.mocked(tabelasPrecoApi.criarTabelaPreco).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Varejo')
    await wrapper.find('[data-test="inicio-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(tabelasPrecoApi.criarTabelaPreco).toHaveBeenCalledWith(
      expect.objectContaining({ nome: 'Varejo', inicioVigencia: '2026-01-01' }),
    )
    expect(router.currentRoute.value.name).toBe('tabelas-preco')
  })

  it('shows a conflict message on duplicate nome (409)', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    vi.mocked(tabelasPrecoApi.criarTabelaPreco).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Varejo')
    await wrapper.find('[data-test="inicio-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma tabela de preço cadastrada com este nome')
  })

  it('loads existing tabela data in edit mode', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    vi.mocked(tabelasPrecoApi.buscarTabelaPreco).mockResolvedValue({
      id: 'tp-1', nome: 'Varejo', modoSelecaoProdutos: 'SELECIONAR_PRODUTOS', metodoAjuste: 'MANUAL',
      operacaoAjuste: null, tipoValorAjuste: null, valorAjuste: null, arredondamento: 'NAO_ARREDONDAR',
      inicioVigencia: '2026-01-01', terminoVigencia: null, valorMinimoVenda: null, percentualComissaoPadrao: null,
      ativo: true, criadoEm: '2026-01-01T00:00:00Z',
      itens: [{ produtoId: 'prod-1', produtoNome: 'Camiseta Polo', produtoSku: 'P0001', precoCadastrado: 100, precoNestaTabela: 120, percentualComissao: 5 }],
    })

    const { wrapper } = await mountWithRouter('/tabelas-preco/tp-1/editar')
    await flushPromises()

    expect(tabelasPrecoApi.buscarTabelaPreco).toHaveBeenCalledWith('tp-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Varejo')
    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('shows an error message when loading tabela data fails in edit mode', async () => {
    vi.mocked(tabelasPrecoApi.buscarTabelaPreco).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/tabelas-preco/tp-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da tabela de preço.')
  })
})
```

- [ ] **Step 4: Run the new tests to verify they pass**

Run: `cd mesh-suite-frontend && npm test -- --run src/views/__tests__/TabelaPrecoFormView.spec.ts`
Expected: PASS (11/11).

- [ ] **Step 5: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npm test -- --run`
Expected: no regressions.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/views/TabelaPrecoFormView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/__tests__/TabelaPrecoFormView.spec.ts
git commit -m "feat(tabela-preco): add TabelaPrecoFormView with regras, itens, and live preview"
```
