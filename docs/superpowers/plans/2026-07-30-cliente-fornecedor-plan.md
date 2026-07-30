# Cadastro de Cliente/Fornecedor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the first real business domain on top of the login/multitenant foundation and the PediMais layout: a unified Cliente/Fornecedor cadastro (backend CRUD + three frontend screens), following `prd/PRD-13-cadastro-comercial.md` scoped to Cliente/Fornecedor only, per `prd/ORDEM-EXECUCAO.md` item 2.

**Architecture:** Backend follows the exact pattern already established by `empresa`/`usuario` (entity + repository + service + controller + DTO, RLS via `tenant_id`, `TenantContext`/`TenantContextAspect` for automatic tenant scoping inside `@Transactional` methods — no manual tenant filtering needed in queries, only explicit `tenantId` assignment when constructing new entities). Frontend adds three views (list, create/edit form, tabbed detail) reusing `AppShell` and the `--pm-*` design tokens, wired into the existing router and the previously-inert "Clientes" sidebar item.

**Tech Stack:** Same as the rest of the repo — Spring Boot 3.4.5 / Java 21, Postgres 16 + Flyway + RLS, Vue 3 + TypeScript + Vite, Pinia, Vue Router. New: a direct frontend call to the public ViaCEP API (`https://viacep.com.br/ws/{cep}/json/`, no key, CORS-enabled) for CEP-to-address autofill — no new backend dependency.

## Global Constraints

- Scope is the **Cliente/Fornecedor** slice of `PRD-13-cadastro-comercial.md` only. Transportadora/Contador/Prestador de Serviço roles, Tabela de Preço, Modelo/Ficha Técnica, and the auxiliary characteristic registries are out of scope (see `prd/ORDEM-EXECUCAO.md` item 2 and the design spec `docs/superpowers/specs/2026-07-29-cliente-fornecedor-design.md`).
- **Correction vs. the legacy system**: a parceiro has a **set of papéis** on one record (not one papel per record) — the same CNPJ can be both CLIENTE and FORNECEDOR without duplicating the registration. `documento` (CPF/CNPJ) is **unique per tenant globally**, not per papel.
- The `PapelParceiro` enum includes `TRANSPORTADORA` for forward compatibility (avoids a future migration when Expedição/Logística is built), but the UI never lets a user select it — the checkbox is rendered but disabled, matching the same inert-item pattern already used for undeveloped-domain nav items and buttons (`cursor: not-allowed`, no functional path). Business validation requires at least one of CLIENTE or FORNECEDOR.
- `StatusParceiro` has three values (`ATIVO`, `EM_RISCO`, `BLOQUEADO`) to match the reference listing's badges, but only `ATIVO`/`BLOQUEADO` are reachable through the API — `PATCH /api/parceiros/{id}/status` rejects `EM_RISCO` with 400. Nothing computes `EM_RISCO` yet (it depends on the Financeiro domain); it exists in the schema/enum only so a future task doesn't need a migration to add it.
- RLS: every new table gets `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY`, matching `empresa`/`usuario`. Child tables (`parceiro_papel`, `parceiro_contato`) do **not** get their own `tenant_id` column — their RLS policy checks tenant ownership through an `EXISTS` subquery against the parent `parceiro` row (see Task 1). This keeps `Parceiro.papeis` a plain Hibernate `@ElementCollection` (no extra non-mapped columns to manage) while still enforcing tenant isolation at the DB level on every table.
- Backend tenant scoping relies entirely on the existing `TenantContext` + `TenantContextAspect` + RLS mechanism (set automatically per-request by `JwtAuthenticationFilter` for every authenticated call) — service methods never add an explicit `tenant_id` predicate to queries (mirrors `TenantQueryService`, not the one-off bypass queries in `AuthService`). The only place `tenantId` is touched explicitly is when constructing a **new** `Parceiro` entity, since RLS can't infer what value to assign on insert.
- Every color in new/touched Vue `<style>` blocks is a `var(--pm-*)` custom property — no new hardcoded hex. The neutral `rgba(0, 0, 0, X)` box-shadow exception (elevation, not brand color) from the PediMais layout plan still applies.
- Simplifications from the reference mockup (`layout/PediMais Prototipo.html`), each preserving the same **fields** but with a simpler widget than the mockup's bespoke component — call these out in review, don't treat them as spec gaps:
  - List filters (Status, Nr. Documento, UF, Cidade) are plain `<select>`/`<input>` elements, not the mockup's custom multi-category filter popover (`AdvancedFilters`).
  - Pagination is Prev/Next + "página X de Y", not the mockup's numbered page-button row.
  - Row deletion uses the browser's native `confirm()` dialog, not a custom modal (no modal component exists yet in this codebase).
- No new npm/Maven dependency is needed. `com.jayway.jsonpath.JsonPath` (used in Task 3's controller tests) is already on the test classpath transitively via `spring-boot-starter-test`.

---

### Task 1: `Parceiro`/`ParceiroContato` entities, migration, repository

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V5__create_parceiro.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/TipoPessoa.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/PapelParceiro.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/StatusParceiro.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/IndicadorIe.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/Parceiro.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroContato.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroRepositoryTest.java`

**Interfaces:**
- Produces: `Parceiro` (entity, package `com.meshsuite.parceiro`), `ParceiroContato` (entity), `ParceiroRepository` (extends `JpaRepository<Parceiro, UUID>` and `JpaSpecificationExecutor<Parceiro>`, with `existsByDocumento(String)`, `existsByDocumentoAndIdNot(String, UUID)`, `countByStatus(StatusParceiro)`). Task 2 consumes all of these.

- [ ] **Step 1: Write the migration**

```sql
CREATE TABLE parceiro (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    tipo_pessoa VARCHAR(10) NOT NULL CHECK (tipo_pessoa IN ('FISICA','JURIDICA')),
    documento VARCHAR(14) NOT NULL,
    nome_fantasia VARCHAR(255) NOT NULL,
    razao_social VARCHAR(255),
    status VARCHAR(10) NOT NULL DEFAULT 'ATIVO' CHECK (status IN ('ATIVO','EM_RISCO','BLOQUEADO')),
    emails_cobranca VARCHAR(500),
    whatsapp VARCHAR(20),
    indicador_ie VARCHAR(20) CHECK (indicador_ie IN ('NAO_CONTRIBUINTE','CONTRIBUINTE','CONTRIBUINTE_ISENTO')),
    inscricao_estadual VARCHAR(20),
    inscricao_municipal VARCHAR(20),
    inscricao_suframa VARCHAR(20),
    cep VARCHAR(8),
    logradouro VARCHAR(255),
    numero VARCHAR(20),
    bairro VARCHAR(100),
    complemento VARCHAR(100),
    uf VARCHAR(2),
    cidade VARCHAR(100),
    observacao TEXT,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_parceiro_tenant_documento ON parceiro(tenant_id, documento);
CREATE INDEX idx_parceiro_tenant_id ON parceiro(tenant_id);

ALTER TABLE parceiro ENABLE ROW LEVEL SECURITY;
ALTER TABLE parceiro FORCE ROW LEVEL SECURITY;

CREATE POLICY parceiro_tenant_isolation ON parceiro
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE parceiro_papel (
    parceiro_id UUID NOT NULL REFERENCES parceiro(id) ON DELETE CASCADE,
    papel VARCHAR(20) NOT NULL CHECK (papel IN ('CLIENTE','FORNECEDOR','TRANSPORTADORA')),
    PRIMARY KEY (parceiro_id, papel)
);

ALTER TABLE parceiro_papel ENABLE ROW LEVEL SECURITY;
ALTER TABLE parceiro_papel FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- parceiro row's own RLS policy, matched by parceiro_id. This keeps the
-- Hibernate @ElementCollection mapping on Parceiro.papeis simple (just
-- parceiro_id + papel, nothing extra for the app to populate on insert).
CREATE POLICY parceiro_papel_tenant_isolation ON parceiro_papel
    USING (EXISTS (
        SELECT 1 FROM parceiro p
        WHERE p.id = parceiro_papel.parceiro_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));


CREATE TABLE parceiro_contato (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parceiro_id UUID NOT NULL REFERENCES parceiro(id) ON DELETE CASCADE,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    telefone_comercial VARCHAR(20),
    telefone_celular VARCHAR(20),
    cargo VARCHAR(100)
);

CREATE INDEX idx_parceiro_contato_parceiro_id ON parceiro_contato(parceiro_id);

ALTER TABLE parceiro_contato ENABLE ROW LEVEL SECURITY;
ALTER TABLE parceiro_contato FORCE ROW LEVEL SECURITY;

CREATE POLICY parceiro_contato_tenant_isolation ON parceiro_contato
    USING (EXISTS (
        SELECT 1 FROM parceiro p
        WHERE p.id = parceiro_contato.parceiro_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

- [ ] **Step 2: Write the enums**

`TipoPessoa.java`:
```java
package com.meshsuite.parceiro;

public enum TipoPessoa {
    FISICA,
    JURIDICA
}
```

`PapelParceiro.java`:
```java
package com.meshsuite.parceiro;

public enum PapelParceiro {
    CLIENTE,
    FORNECEDOR,
    TRANSPORTADORA
}
```

`StatusParceiro.java`:
```java
package com.meshsuite.parceiro;

public enum StatusParceiro {
    ATIVO,
    EM_RISCO,
    BLOQUEADO
}
```

`IndicadorIe.java`:
```java
package com.meshsuite.parceiro;

public enum IndicadorIe {
    NAO_CONTRIBUINTE,
    CONTRIBUINTE,
    CONTRIBUINTE_ISENTO
}
```

- [ ] **Step 3: Write the `Parceiro` entity**

```java
package com.meshsuite.parceiro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "parceiro")
@Getter
@Setter
public class Parceiro {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa", nullable = false, length = 10)
    private TipoPessoa tipoPessoa;

    @Column(nullable = false, length = 14)
    private String documento;

    @Column(name = "nome_fantasia", nullable = false)
    private String nomeFantasia;

    @Column(name = "razao_social")
    private String razaoSocial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusParceiro status = StatusParceiro.ATIVO;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parceiro_papel", joinColumns = @JoinColumn(name = "parceiro_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 20)
    private Set<PapelParceiro> papeis = new HashSet<>();

    @Column(name = "emails_cobranca", length = 500)
    private String emailsCobranca;

    @Column(length = 20)
    private String whatsapp;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicador_ie", length = 20)
    private IndicadorIe indicadorIe;

    @Column(name = "inscricao_estadual", length = 20)
    private String inscricaoEstadual;

    @Column(name = "inscricao_municipal", length = 20)
    private String inscricaoMunicipal;

    @Column(name = "inscricao_suframa", length = 20)
    private String inscricaoSuframa;

    @Column(length = 8)
    private String cep;

    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String complemento;

    @Column(length = 2)
    private String uf;

    @Column(length = 100)
    private String cidade;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "parceiro", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ParceiroContato> contatos = new ArrayList<>();
}
```

- [ ] **Step 4: Write the `ParceiroContato` entity**

```java
package com.meshsuite.parceiro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "parceiro_contato")
@Getter
@Setter
public class ParceiroContato {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parceiro_id", nullable = false)
    private Parceiro parceiro;

    @Column(nullable = false)
    private String nome;

    private String email;

    @Column(name = "telefone_comercial", length = 20)
    private String telefoneComercial;

    @Column(name = "telefone_celular", length = 20)
    private String telefoneCelular;

    @Column(length = 100)
    private String cargo;
}
```

- [ ] **Step 5: Write the repository**

```java
package com.meshsuite.parceiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ParceiroRepository extends JpaRepository<Parceiro, UUID>, JpaSpecificationExecutor<Parceiro> {
    boolean existsByDocumento(String documento);
    boolean existsByDocumentoAndIdNot(String documento, UUID id);
    long countByStatus(StatusParceiro status);
}
```

- [ ] **Step 6: Write the repository test**

```java
package com.meshsuite.parceiro;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParceiroRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
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

    private Parceiro novoParceiro(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.setRazaoSocial("Mercado Silva Ltda");
        p.setPapeis(Set.of(PapelParceiro.CLIENTE));
        return p;
    }

    @Test
    @Transactional
    void savesParceiroWithPapeisAndContatos() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Parceiro parceiro = novoParceiro(tenant.getId(), "11222333000144");
        ParceiroContato contato = new ParceiroContato();
        contato.setParceiro(parceiro);
        contato.setNome("Ana Souza");
        contato.setCargo("Financeiro");
        parceiro.getContatos().add(contato);

        Parceiro saved = parceiroRepository.saveAndFlush(parceiro);
        entityManager.clear();

        Parceiro reloaded = parceiroRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPapeis()).containsExactly(PapelParceiro.CLIENTE);
        assertThat(reloaded.getContatos()).hasSize(1);
        assertThat(reloaded.getContatos().get(0).getNome()).isEqualTo("Ana Souza");
        assertThat(reloaded.getStatus()).isEqualTo(StatusParceiro.ATIVO);
    }

    @Test
    @Transactional
    void documentoMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        parceiroRepository.saveAndFlush(novoParceiro(tenant.getId(), "11222333000144"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> parceiroRepository.saveAndFlush(novoParceiro(tenant.getId(), "11222333000144")));
    }

    @Test
    @Transactional
    void sameDocumentoAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        parceiroRepository.saveAndFlush(novoParceiro(tenantA.getId(), "11222333000144"));

        setTenantContext(tenantB.getId());
        Parceiro saved = parceiroRepository.saveAndFlush(novoParceiro(tenantB.getId(), "11222333000144"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesParceiroAndChildRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Parceiro parceiro = novoParceiro(tenant.getId(), "11222333000144");
        ParceiroContato contato = new ParceiroContato();
        contato.setParceiro(parceiro);
        contato.setNome("Ana Souza");
        parceiro.getContatos().add(contato);
        parceiroRepository.saveAndFlush(parceiro);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long parceiroCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM parceiro")
                .getSingleResult()).longValue();
        Long papelCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM parceiro_papel")
                .getSingleResult()).longValue();
        Long contatoCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM parceiro_contato")
                .getSingleResult()).longValue();

        assertThat(parceiroCount).isZero();
        assertThat(papelCount).isZero();
        assertThat(contatoCount).isZero();
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=ParceiroRepositoryTest`
Expected: PASS (4 tests). Requires Docker running (Testcontainers Postgres).

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V5__create_parceiro.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ \
        mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroRepositoryTest.java
git commit -m "feat: add Parceiro/ParceiroContato entities, migration, and RLS"
```

---

### Task 2: DTOs, exceptions, and `ParceiroService`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroContatoDto.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroStatusRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroResumoResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroNaoEncontradoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/DocumentoDuplicadoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroValidacaoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroServiceTest.java`

**Interfaces:**
- Consumes: `Parceiro`, `ParceiroContato`, `ParceiroRepository`, `TipoPessoa`, `PapelParceiro`, `StatusParceiro`, `IndicadorIe` (Task 1).
- Produces: `ParceiroService` with methods `listar(String, StatusParceiro, TipoPessoa, String, String, Pageable): Page<ParceiroSummaryResponse>`, `resumo(): ParceiroResumoResponse`, `buscarPorId(UUID): ParceiroResponse`, `criar(UUID, ParceiroRequest): ParceiroResponse`, `atualizar(UUID, ParceiroRequest): ParceiroResponse`, `atualizarStatus(UUID, StatusParceiro): ParceiroResponse`, `excluir(UUID): void`. Task 3 (controller) consumes all of these.

- [ ] **Step 1: Write the DTOs**

`ParceiroContatoDto.java`:
```java
package com.meshsuite.parceiro.dto;

public record ParceiroContatoDto(
        String nome,
        String email,
        String telefoneComercial,
        String telefoneCelular,
        String cargo) {
}
```

`ParceiroRequest.java`:
```java
package com.meshsuite.parceiro.dto;

import com.meshsuite.parceiro.IndicadorIe;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.TipoPessoa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record ParceiroRequest(
        @NotNull TipoPessoa tipoPessoa,
        @NotBlank String documento,
        @NotBlank String nomeFantasia,
        String razaoSocial,
        @NotEmpty Set<PapelParceiro> papeis,
        String emailsCobranca,
        String whatsapp,
        IndicadorIe indicadorIe,
        String inscricaoEstadual,
        String inscricaoMunicipal,
        String inscricaoSuframa,
        String cep,
        String logradouro,
        String numero,
        String bairro,
        String complemento,
        String uf,
        String cidade,
        String observacao,
        List<ParceiroContatoDto> contatos) {
}
```

`ParceiroResponse.java`:
```java
package com.meshsuite.parceiro.dto;

import com.meshsuite.parceiro.IndicadorIe;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.StatusParceiro;
import com.meshsuite.parceiro.TipoPessoa;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ParceiroResponse(
        UUID id,
        TipoPessoa tipoPessoa,
        String documento,
        String nomeFantasia,
        String razaoSocial,
        StatusParceiro status,
        Set<PapelParceiro> papeis,
        String emailsCobranca,
        String whatsapp,
        IndicadorIe indicadorIe,
        String inscricaoEstadual,
        String inscricaoMunicipal,
        String inscricaoSuframa,
        String cep,
        String logradouro,
        String numero,
        String bairro,
        String complemento,
        String uf,
        String cidade,
        String observacao,
        List<ParceiroContatoDto> contatos) {
}
```

`ParceiroSummaryResponse.java`:
```java
package com.meshsuite.parceiro.dto;

import com.meshsuite.parceiro.StatusParceiro;

import java.util.UUID;

public record ParceiroSummaryResponse(
        UUID id,
        String nomeFantasia,
        String razaoSocial,
        String documento,
        String cidade,
        String uf,
        String whatsapp,
        StatusParceiro status) {
}
```

`ParceiroStatusRequest.java`:
```java
package com.meshsuite.parceiro.dto;

import com.meshsuite.parceiro.StatusParceiro;
import jakarta.validation.constraints.NotNull;

public record ParceiroStatusRequest(@NotNull StatusParceiro status) {
}
```

`ParceiroResumoResponse.java`:
```java
package com.meshsuite.parceiro.dto;

public record ParceiroResumoResponse(long total, long ativos, long emRisco, long bloqueados) {
}
```

- [ ] **Step 2: Write the exceptions**

`ParceiroNaoEncontradoException.java`:
```java
package com.meshsuite.parceiro;

public class ParceiroNaoEncontradoException extends RuntimeException {
    public ParceiroNaoEncontradoException() {
        super("Parceiro não encontrado");
    }
}
```

`DocumentoDuplicadoException.java`:
```java
package com.meshsuite.parceiro;

public class DocumentoDuplicadoException extends RuntimeException {
    public DocumentoDuplicadoException() {
        super("Já existe um parceiro cadastrado com este documento");
    }
}
```

`ParceiroValidacaoException.java`:
```java
package com.meshsuite.parceiro;

public class ParceiroValidacaoException extends RuntimeException {
    public ParceiroValidacaoException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Write the specifications helper**

```java
package com.meshsuite.parceiro;

import org.springframework.data.jpa.domain.Specification;

public final class ParceiroSpecifications {

    private ParceiroSpecifications() {
    }

    public static Specification<Parceiro> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nomeFantasia")), termo),
                cb.like(cb.lower(root.get("razaoSocial")), termo));
    }

    public static Specification<Parceiro> comStatus(StatusParceiro status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Parceiro> comTipoPessoa(TipoPessoa tipoPessoa) {
        if (tipoPessoa == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("tipoPessoa"), tipoPessoa);
    }

    public static Specification<Parceiro> comUf(String uf) {
        if (uf == null || uf.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("uf"), uf);
    }

    public static Specification<Parceiro> comCidade(String cidade) {
        if (cidade == null || cidade.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("cidade"), cidade);
    }
}
```

- [ ] **Step 4: Write `ParceiroService`**

```java
package com.meshsuite.parceiro;

import com.meshsuite.parceiro.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ParceiroService {

    private final ParceiroRepository parceiroRepository;

    public ParceiroService(ParceiroRepository parceiroRepository) {
        this.parceiroRepository = parceiroRepository;
    }

    @Transactional(readOnly = true)
    public Page<ParceiroSummaryResponse> listar(String busca, StatusParceiro status, TipoPessoa tipoDocumento,
                                                 String uf, String cidade, Pageable pageable) {
        Specification<Parceiro> spec = Specification.allOf(
                ParceiroSpecifications.comBusca(busca),
                ParceiroSpecifications.comStatus(status),
                ParceiroSpecifications.comTipoPessoa(tipoDocumento),
                ParceiroSpecifications.comUf(uf),
                ParceiroSpecifications.comCidade(cidade));
        return parceiroRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public ParceiroResumoResponse resumo() {
        long ativos = parceiroRepository.countByStatus(StatusParceiro.ATIVO);
        long emRisco = parceiroRepository.countByStatus(StatusParceiro.EM_RISCO);
        long bloqueados = parceiroRepository.countByStatus(StatusParceiro.BLOQUEADO);
        return new ParceiroResumoResponse(ativos + emRisco + bloqueados, ativos, emRisco, bloqueados);
    }

    @Transactional(readOnly = true)
    public ParceiroResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    public ParceiroResponse criar(UUID tenantId, ParceiroRequest request) {
        validar(request, null);

        Parceiro parceiro = new Parceiro();
        parceiro.setTenantId(tenantId);
        aplicar(parceiro, request);
        return toResponse(parceiroRepository.saveAndFlush(parceiro));
    }

    @Transactional
    public ParceiroResponse atualizar(UUID id, ParceiroRequest request) {
        validar(request, id);

        Parceiro parceiro = buscarEntidadePorId(id);
        aplicar(parceiro, request);
        return toResponse(parceiroRepository.saveAndFlush(parceiro));
    }

    @Transactional
    public ParceiroResponse atualizarStatus(UUID id, StatusParceiro novoStatus) {
        if (novoStatus != StatusParceiro.ATIVO && novoStatus != StatusParceiro.BLOQUEADO) {
            throw new ParceiroValidacaoException("Só é possível definir o status como ATIVO ou BLOQUEADO manualmente");
        }
        Parceiro parceiro = buscarEntidadePorId(id);
        parceiro.setStatus(novoStatus);
        return toResponse(parceiroRepository.saveAndFlush(parceiro));
    }

    @Transactional
    public void excluir(UUID id) {
        parceiroRepository.delete(buscarEntidadePorId(id));
    }

    private Parceiro buscarEntidadePorId(UUID id) {
        return parceiroRepository.findById(id).orElseThrow(ParceiroNaoEncontradoException::new);
    }

    private void validar(ParceiroRequest request, UUID idAtual) {
        boolean semPapelAtivo = request.papeis().stream()
                .noneMatch(p -> p == PapelParceiro.CLIENTE || p == PapelParceiro.FORNECEDOR);
        if (semPapelAtivo) {
            throw new ParceiroValidacaoException("Selecione ao menos o papel Cliente ou Fornecedor");
        }

        int tamanhoEsperado = request.tipoPessoa() == TipoPessoa.FISICA ? 11 : 14;
        if (request.documento().length() != tamanhoEsperado) {
            throw new ParceiroValidacaoException(
                    request.tipoPessoa() == TipoPessoa.FISICA
                            ? "CPF deve ter 11 dígitos"
                            : "CNPJ deve ter 14 dígitos");
        }

        boolean duplicado = idAtual == null
                ? parceiroRepository.existsByDocumento(request.documento())
                : parceiroRepository.existsByDocumentoAndIdNot(request.documento(), idAtual);
        if (duplicado) {
            throw new DocumentoDuplicadoException();
        }
    }

    private void aplicar(Parceiro parceiro, ParceiroRequest request) {
        parceiro.setTipoPessoa(request.tipoPessoa());
        parceiro.setDocumento(request.documento());
        parceiro.setNomeFantasia(request.nomeFantasia());
        parceiro.setRazaoSocial(request.razaoSocial());
        parceiro.setPapeis(Set.copyOf(request.papeis()));
        parceiro.setEmailsCobranca(request.emailsCobranca());
        parceiro.setWhatsapp(request.whatsapp());
        parceiro.setIndicadorIe(request.indicadorIe());
        parceiro.setInscricaoEstadual(request.inscricaoEstadual());
        parceiro.setInscricaoMunicipal(request.inscricaoMunicipal());
        parceiro.setInscricaoSuframa(request.inscricaoSuframa());
        parceiro.setCep(request.cep());
        parceiro.setLogradouro(request.logradouro());
        parceiro.setNumero(request.numero());
        parceiro.setBairro(request.bairro());
        parceiro.setComplemento(request.complemento());
        parceiro.setUf(request.uf());
        parceiro.setCidade(request.cidade());
        parceiro.setObservacao(request.observacao());

        parceiro.getContatos().clear();
        List<ParceiroContatoDto> contatos = request.contatos() == null ? List.of() : request.contatos();
        for (ParceiroContatoDto dto : contatos) {
            ParceiroContato contato = new ParceiroContato();
            contato.setParceiro(parceiro);
            contato.setNome(dto.nome());
            contato.setEmail(dto.email());
            contato.setTelefoneComercial(dto.telefoneComercial());
            contato.setTelefoneCelular(dto.telefoneCelular());
            contato.setCargo(dto.cargo());
            parceiro.getContatos().add(contato);
        }
    }

    private ParceiroSummaryResponse toSummary(Parceiro p) {
        return new ParceiroSummaryResponse(
                p.getId(), p.getNomeFantasia(), p.getRazaoSocial(), p.getDocumento(),
                p.getCidade(), p.getUf(), p.getWhatsapp(), p.getStatus());
    }

    private ParceiroResponse toResponse(Parceiro p) {
        List<ParceiroContatoDto> contatos = p.getContatos().stream()
                .map(c -> new ParceiroContatoDto(c.getNome(), c.getEmail(), c.getTelefoneComercial(),
                        c.getTelefoneCelular(), c.getCargo()))
                .toList();
        return new ParceiroResponse(
                p.getId(), p.getTipoPessoa(), p.getDocumento(), p.getNomeFantasia(), p.getRazaoSocial(),
                p.getStatus(), p.getPapeis(), p.getEmailsCobranca(), p.getWhatsapp(), p.getIndicadorIe(),
                p.getInscricaoEstadual(), p.getInscricaoMunicipal(), p.getInscricaoSuframa(), p.getCep(),
                p.getLogradouro(), p.getNumero(), p.getBairro(), p.getComplemento(), p.getUf(), p.getCidade(),
                p.getObservacao(), contatos);
    }
}
```

- [ ] **Step 5: Add exception handlers to `GlobalExceptionHandler`**

Add these methods to the existing `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java` (import `com.meshsuite.parceiro.*` and `org.springframework.dao.DataIntegrityViolationException` at the top):

```java
    @ExceptionHandler(com.meshsuite.parceiro.ParceiroNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleParceiroNaoEncontrado(
            com.meshsuite.parceiro.ParceiroNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.parceiro.DocumentoDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleDocumentoDuplicado(
            com.meshsuite.parceiro.DocumentoDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.parceiro.ParceiroValidacaoException.class)
    public ResponseEntity<Map<String, String>> handleParceiroValidacao(
            com.meshsuite.parceiro.ParceiroValidacaoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    // Fallback for a race condition slipping past ParceiroService's pre-check
    // (two concurrent requests for the same new documento) -- the DB's
    // UNIQUE(tenant_id, documento) constraint is the actual source of truth.
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um parceiro cadastrado com este documento"));
    }
```

(Fully-qualified names are used inline instead of new imports to keep this diff a pure addition — feel free to add proper `import` lines at the top of the file and drop the qualification if that reads cleaner; either is fine.)

- [ ] **Step 6: Write the service test**

```java
package com.meshsuite.parceiro;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.parceiro.dto.ParceiroContatoDto;
import com.meshsuite.parceiro.dto.ParceiroRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class ParceiroServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroService parceiroService;
    @Autowired EntityManager entityManager;

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        return tenant.getId();
    }

    private ParceiroRequest request(String documento, Set<PapelParceiro> papeis) {
        return new ParceiroRequest(
                TipoPessoa.JURIDICA, documento, "Mercado Silva", "Mercado Silva Ltda", papeis,
                "financeiro@mercadosilva.com.br", "(11) 99999-9999", IndicadorIe.CONTRIBUINTE,
                "123456789", null, null,
                "01310100", "Av. Paulista", "1000", "Bela Vista", null, "SP", "São Paulo",
                "Cliente antigo", List.of(new ParceiroContatoDto("Ana Souza", "ana@mercadosilva.com.br",
                        "(11) 3333-3333", "(11) 98888-8888", "Financeiro")));
    }

    @Test
    void criaERecuperaParceiro() {
        setUpTenant("aurora");

        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        var buscado = parceiroService.buscarPorId(criado.id());
        assertThat(buscado.nomeFantasia()).isEqualTo("Mercado Silva");
        assertThat(buscado.papeis()).containsExactly(PapelParceiro.CLIENTE);
        assertThat(buscado.contatos()).hasSize(1);
    }

    @Test
    void rejeitaParceiroSemPapelClienteOuFornecedor() {
        setUpTenant("aurora");

        assertThrows(ParceiroValidacaoException.class,
                () -> parceiroService.criar(TenantContext.get(),
                        request("11222333000144", Set.of(PapelParceiro.TRANSPORTADORA))));
    }

    @Test
    void rejeitaDocumentoDuplicadoNoMesmoTenant() {
        setUpTenant("aurora");
        parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        assertThrows(DocumentoDuplicadoException.class,
                () -> parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.FORNECEDOR))));
    }

    @Test
    void rejeitaAtualizacaoDeStatusParaEmRisco() {
        setUpTenant("aurora");
        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        assertThrows(ParceiroValidacaoException.class,
                () -> parceiroService.atualizarStatus(criado.id(), StatusParceiro.EM_RISCO));
    }

    @Test
    void atualizaStatusParaBloqueado() {
        setUpTenant("aurora");
        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        var atualizado = parceiroService.atualizarStatus(criado.id(), StatusParceiro.BLOQUEADO);

        assertThat(atualizado.status()).isEqualTo(StatusParceiro.BLOQUEADO);
    }

    @Test
    void resumoContaPorStatus() {
        setUpTenant("aurora");
        var a = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));
        parceiroService.criar(TenantContext.get(), request("55666777000155", Set.of(PapelParceiro.FORNECEDOR)));
        parceiroService.atualizarStatus(a.id(), StatusParceiro.BLOQUEADO);

        var resumo = parceiroService.resumo();

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.ativos()).isEqualTo(1);
        assertThat(resumo.bloqueados()).isEqualTo(1);
    }

    @Test
    void listaComFiltroDeBusca() {
        setUpTenant("aurora");
        parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        var pagina = parceiroService.listar("silva", null, null, null, null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).nomeFantasia()).isEqualTo("Mercado Silva");
    }

    @Test
    void excluiParceiro() {
        setUpTenant("aurora");
        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        parceiroService.excluir(criado.id());

        assertThrows(ParceiroNaoEncontradoException.class, () -> parceiroService.buscarPorId(criado.id()));
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=ParceiroServiceTest`
Expected: PASS (8 tests).

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroServiceTest.java
git commit -m "feat: add ParceiroService with validation, plus its DTOs and exceptions"
```

---

### Task 3: `ParceiroController` and REST integration tests

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroController.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroControllerTest.java`

**Interfaces:**
- Consumes: `ParceiroService` (Task 2), `AuthContextService.Context` (existing, from `com.meshsuite.auth`).
- Produces: `GET/POST/PUT/PATCH/DELETE /api/parceiros[/...]` — the full surface Task 5-7's frontend `src/api/parceiros.ts` (Task 4) calls.

- [ ] **Step 1: Write the controller**

```java
package com.meshsuite.parceiro;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.parceiro.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/parceiros")
public class ParceiroController {

    private final ParceiroService parceiroService;

    public ParceiroController(ParceiroService parceiroService) {
        this.parceiroService = parceiroService;
    }

    @GetMapping
    public Page<ParceiroSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusParceiro status,
            @RequestParam(required = false) TipoPessoa tipoDocumento,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String cidade,
            @PageableDefault(size = 10, sort = "nomeFantasia") Pageable pageable) {
        return parceiroService.listar(busca, status, tipoDocumento, uf, cidade, pageable);
    }

    @GetMapping("/resumo")
    public ParceiroResumoResponse resumo() {
        return parceiroService.resumo();
    }

    @GetMapping("/{id}")
    public ParceiroResponse buscarPorId(@PathVariable UUID id) {
        return parceiroService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ParceiroResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                   @Valid @RequestBody ParceiroRequest request) {
        ParceiroResponse response = parceiroService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ParceiroResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ParceiroRequest request) {
        return parceiroService.atualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    public ParceiroResponse atualizarStatus(@PathVariable UUID id, @Valid @RequestBody ParceiroStatusRequest request) {
        return parceiroService.atualizarStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        parceiroService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Write the controller integration test**

```java
package com.meshsuite.parceiro;

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
class ParceiroControllerTest extends AbstractIntegrationTest {

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

    private String parceiroPayload(String documento) {
        return """
                {
                  "tipoPessoa": "JURIDICA",
                  "documento": "%s",
                  "nomeFantasia": "Mercado Silva",
                  "razaoSocial": "Mercado Silva Ltda",
                  "papeis": ["CLIENTE"],
                  "cidade": "São Paulo",
                  "uf": "SP",
                  "contatos": []
                }
                """.formatted(documento);
    }

    @Test
    void createsListsUpdatesAndDeletesParceiro() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/parceiros").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeFantasia").value("Mercado Silva"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/parceiros").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nomeFantasia").value("Mercado Silva"));

        mockMvc.perform(put("/api/parceiros/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoPessoa": "JURIDICA",
                                  "documento": "22333444000155",
                                  "nomeFantasia": "Mercado Silva Atualizado",
                                  "papeis": ["CLIENTE","FORNECEDOR"],
                                  "contatos": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeFantasia").value("Mercado Silva Atualizado"))
                .andExpect(jsonPath("$.papeis.length()").value(2));

        mockMvc.perform(patch("/api/parceiros/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOQUEADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOQUEADO"));

        mockMvc.perform(delete("/api/parceiros/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/parceiros/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateDocumentoWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/parceiros").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/parceiros").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsStatusUpdateToEmRisco() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String body = mockMvc.perform(post("/api/parceiros").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(patch("/api/parceiros/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_RISCO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsParceiro() throws Exception {
        String tokenA = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/parceiros").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        mockMvc.perform(get("/api/parceiros/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/parceiros"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=ParceiroControllerTest`
Expected: PASS (5 tests).

- [ ] **Step 4: Run the full backend suite**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: PASS, no regressions in `auth`/`empresa`/`usuario`/`tenant` tests.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroController.java \
        mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroControllerTest.java
git commit -m "feat: add ParceiroController with the /api/parceiros REST endpoints"
```

---

### Task 4: Frontend API layer — `src/api/parceiros.ts` + `src/api/cep.ts`

**Files:**
- Create: `mesh-suite-frontend/src/api/parceiros.ts`
- Create: `mesh-suite-frontend/src/api/cep.ts`
- Test: `mesh-suite-frontend/src/api/__tests__/parceiros.spec.ts`
- Test: `mesh-suite-frontend/src/api/__tests__/cep.spec.ts`

**Interfaces:**
- Consumes: `apiClient` (existing, `src/api/client.ts`).
- Produces: `ParceiroRequest`, `ParceiroResponse`, `ParceiroSummary`, `ParceiroContato`, `ParceiroResumo`, `Page<T>`, `TipoPessoa`, `PapelParceiro`, `StatusParceiro`, `IndicadorIe` types, and functions `listarParceiros`, `buscarParceiro`, `criarParceiro`, `atualizarParceiro`, `atualizarStatusParceiro`, `excluirParceiro`, `buscarResumoParceiros` (from `parceiros.ts`); `buscarEnderecoPorCep` (from `cep.ts`). Tasks 5-7 consume all of these.

- [ ] **Step 1: Write `src/api/parceiros.ts`**

```typescript
import { apiClient } from './client'

export type TipoPessoa = 'FISICA' | 'JURIDICA'
export type PapelParceiro = 'CLIENTE' | 'FORNECEDOR' | 'TRANSPORTADORA'
export type StatusParceiro = 'ATIVO' | 'EM_RISCO' | 'BLOQUEADO'
export type IndicadorIe = 'NAO_CONTRIBUINTE' | 'CONTRIBUINTE' | 'CONTRIBUINTE_ISENTO'

export interface ParceiroContato {
  nome: string
  email: string
  telefoneComercial: string
  telefoneCelular: string
  cargo: string
}

export interface ParceiroRequest {
  tipoPessoa: TipoPessoa
  documento: string
  nomeFantasia: string
  razaoSocial: string
  papeis: PapelParceiro[]
  emailsCobranca: string
  whatsapp: string
  indicadorIe: IndicadorIe | null
  inscricaoEstadual: string
  inscricaoMunicipal: string
  inscricaoSuframa: string
  cep: string
  logradouro: string
  numero: string
  bairro: string
  complemento: string
  uf: string
  cidade: string
  observacao: string
  contatos: ParceiroContato[]
}

export interface ParceiroResponse extends ParceiroRequest {
  id: string
  status: StatusParceiro
}

export interface ParceiroSummary {
  id: string
  nomeFantasia: string
  razaoSocial: string
  documento: string
  cidade: string
  uf: string
  whatsapp: string
  status: StatusParceiro
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarParceirosParams {
  busca?: string
  status?: StatusParceiro
  tipoDocumento?: TipoPessoa
  uf?: string
  cidade?: string
  page?: number
  size?: number
}

export interface ParceiroResumo {
  total: number
  ativos: number
  emRisco: number
  bloqueados: number
}

export async function listarParceiros(params: ListarParceirosParams): Promise<Page<ParceiroSummary>> {
  const { data } = await apiClient.get<Page<ParceiroSummary>>('/parceiros', { params })
  return data
}

export async function buscarParceiro(id: string): Promise<ParceiroResponse> {
  const { data } = await apiClient.get<ParceiroResponse>(`/parceiros/${id}`)
  return data
}

export async function criarParceiro(payload: ParceiroRequest): Promise<ParceiroResponse> {
  const { data } = await apiClient.post<ParceiroResponse>('/parceiros', payload)
  return data
}

export async function atualizarParceiro(id: string, payload: ParceiroRequest): Promise<ParceiroResponse> {
  const { data } = await apiClient.put<ParceiroResponse>(`/parceiros/${id}`, payload)
  return data
}

export async function atualizarStatusParceiro(id: string, status: StatusParceiro): Promise<void> {
  await apiClient.patch(`/parceiros/${id}/status`, { status })
}

export async function excluirParceiro(id: string): Promise<void> {
  await apiClient.delete(`/parceiros/${id}`)
}

export async function buscarResumoParceiros(): Promise<ParceiroResumo> {
  const { data } = await apiClient.get<ParceiroResumo>('/parceiros/resumo')
  return data
}
```

- [ ] **Step 2: Write `src/api/cep.ts`**

```typescript
export interface EnderecoViaCep {
  logradouro: string
  bairro: string
  localidade: string
  uf: string
}

export async function buscarEnderecoPorCep(cep: string): Promise<EnderecoViaCep | null> {
  const cepLimpo = cep.replace(/\D/g, '')
  if (cepLimpo.length !== 8) {
    return null
  }

  let response: Response
  try {
    response = await fetch(`https://viacep.com.br/ws/${cepLimpo}/json/`)
  } catch {
    return null
  }
  if (!response.ok) {
    return null
  }

  const data = await response.json()
  if (data.erro) {
    return null
  }
  return {
    logradouro: data.logradouro,
    bairro: data.bairro,
    localidade: data.localidade,
    uf: data.uf,
  }
}
```

- [ ] **Step 3: Write the `parceiros.ts` test**

```typescript
import { describe, it, expect, vi } from 'vitest'
import { apiClient } from '../client'
import {
  listarParceiros,
  buscarParceiro,
  criarParceiro,
  atualizarParceiro,
  atualizarStatusParceiro,
  excluirParceiro,
  buscarResumoParceiros,
} from '../parceiros'

vi.mock('../client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('api/parceiros', () => {
  it('listarParceiros calls GET /parceiros with query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 },
    })

    await listarParceiros({ busca: 'silva', status: 'ATIVO' })

    expect(apiClient.get).toHaveBeenCalledWith('/parceiros', { params: { busca: 'silva', status: 'ATIVO' } })
  })

  it('buscarParceiro calls GET /parceiros/:id', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: {} })
    await buscarParceiro('abc-123')
    expect(apiClient.get).toHaveBeenCalledWith('/parceiros/abc-123')
  })

  it('criarParceiro calls POST /parceiros with the payload', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} })
    const payload = { nomeFantasia: 'Teste' } as any
    await criarParceiro(payload)
    expect(apiClient.post).toHaveBeenCalledWith('/parceiros', payload)
  })

  it('atualizarParceiro calls PUT /parceiros/:id with the payload', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} })
    const payload = { nomeFantasia: 'Teste' } as any
    await atualizarParceiro('abc-123', payload)
    expect(apiClient.put).toHaveBeenCalledWith('/parceiros/abc-123', payload)
  })

  it('atualizarStatusParceiro calls PATCH /parceiros/:id/status', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} })
    await atualizarStatusParceiro('abc-123', 'BLOQUEADO')
    expect(apiClient.patch).toHaveBeenCalledWith('/parceiros/abc-123/status', { status: 'BLOQUEADO' })
  })

  it('excluirParceiro calls DELETE /parceiros/:id', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ data: {} })
    await excluirParceiro('abc-123')
    expect(apiClient.delete).toHaveBeenCalledWith('/parceiros/abc-123')
  })

  it('buscarResumoParceiros calls GET /parceiros/resumo', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { total: 0, ativos: 0, emRisco: 0, bloqueados: 0 } })
    await buscarResumoParceiros()
    expect(apiClient.get).toHaveBeenCalledWith('/parceiros/resumo')
  })
})
```

- [ ] **Step 4: Write the `cep.ts` test**

```typescript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { buscarEnderecoPorCep } from '../cep'

describe('buscarEnderecoPorCep', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns the address when ViaCEP finds the CEP', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ logradouro: 'Av. Paulista', bairro: 'Bela Vista', localidade: 'São Paulo', uf: 'SP' }),
    })
    vi.stubGlobal('fetch', mockFetch)

    const endereco = await buscarEnderecoPorCep('01310-100')

    expect(mockFetch).toHaveBeenCalledWith('https://viacep.com.br/ws/01310100/json/')
    expect(endereco).toEqual({ logradouro: 'Av. Paulista', bairro: 'Bela Vista', localidade: 'São Paulo', uf: 'SP' })
  })

  it('returns null when ViaCEP reports the CEP does not exist', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ erro: true }) }))

    const endereco = await buscarEnderecoPorCep('00000000')

    expect(endereco).toBeNull()
  })

  it('returns null for a malformed CEP without calling the API', async () => {
    const mockFetch = vi.fn()
    vi.stubGlobal('fetch', mockFetch)

    const endereco = await buscarEnderecoPorCep('123')

    expect(endereco).toBeNull()
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('returns null when the request fails or the network errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))
    expect(await buscarEnderecoPorCep('01310100')).toBeNull()

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network error')))
    expect(await buscarEnderecoPorCep('01310100')).toBeNull()
  })
})
```

- [ ] **Step 5: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run src/api/__tests__/parceiros.spec.ts src/api/__tests__/cep.spec.ts`
Expected: PASS (11 tests).

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/api/parceiros.ts mesh-suite-frontend/src/api/cep.ts \
        mesh-suite-frontend/src/api/__tests__/parceiros.spec.ts mesh-suite-frontend/src/api/__tests__/cep.spec.ts
git commit -m "feat: add typed API layer for parceiros and ViaCEP lookup"
```

---

### Task 5: `ClienteFormView.vue` (create/edit)

**Files:**
- Create: `mesh-suite-frontend/src/views/ClienteFormView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/ClienteFormView.spec.ts`

**Interfaces:**
- Consumes: `buscarParceiro`, `criarParceiro`, `atualizarParceiro`, `ParceiroRequest`, `PapelParceiro` (Task 4); `AppShell` (existing).
- Produces: routes `clientes-novo` (`/clientes/novo`) and `clientes-editar` (`/clientes/:id/editar`), both rendering this component. Task 6 (list) navigates to `clientes-novo`/`clientes-editar`; Task 7 (detail) navigates to `clientes-editar`.

- [ ] **Step 1: Write `ClienteFormView.vue`**

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Cliente' : 'Novo Cliente'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados Gerais</h2>
        <div class="grid grid-3">
          <div>
            <label class="field-label">Tipo de Pessoa *</label>
            <select v-model="form.tipoPessoa">
              <option value="JURIDICA">Jurídica</option>
              <option value="FISICA">Física</option>
            </select>
          </div>
          <div>
            <label class="field-label">CNPJ / CPF *</label>
            <input v-model="form.documento" data-test="documento" />
          </div>
          <div>
            <label class="field-label">Nome Fantasia *</label>
            <input v-model="form.nomeFantasia" data-test="nomeFantasia" />
            <p v-if="erros.nomeFantasia" class="field-error">{{ erros.nomeFantasia }}</p>
          </div>
        </div>
        <div>
          <label class="field-label">Razão Social</label>
          <input v-model="form.razaoSocial" />
        </div>
        <div>
          <label class="field-label">
            Tipo de Papel * <span class="hint">(pode selecionar mais de uma opção)</span>
          </label>
          <div class="checkbox-row">
            <label class="checkbox-label">
              <input type="checkbox" :checked="form.papeis.includes('CLIENTE')" @change="togglePapel('CLIENTE')" />
              Cliente
            </label>
            <label class="checkbox-label">
              <input type="checkbox" :checked="form.papeis.includes('FORNECEDOR')" @change="togglePapel('FORNECEDOR')" />
              Fornecedor
            </label>
            <label
              class="checkbox-label checkbox-inert"
              title="Pertence ao domínio Expedição/Logística, ainda não implementado"
            >
              <input type="checkbox" disabled />
              Transportadora
            </label>
          </div>
          <p v-if="erros.papeis" class="field-error">{{ erros.papeis }}</p>
        </div>
      </section>

      <section class="card">
        <h2>Contato para Cobrança e Faturamento</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">E-mail(s)</label>
            <input v-model="form.emailsCobranca" placeholder="email@exemplo.com.br" />
          </div>
          <div>
            <label class="field-label">Número do WhatsApp</label>
            <input v-model="form.whatsapp" placeholder="(11) 99999-9999" />
          </div>
        </div>
        <p class="hint">Para inserir mais de um e-mail, use a vírgula</p>
      </section>

      <section class="card">
        <h2>Informações Fiscais</h2>
        <div class="grid grid-4">
          <div>
            <label class="field-label">Indicador de Inscrição Estadual</label>
            <select v-model="form.indicadorIe">
              <option :value="null">Selecione...</option>
              <option value="NAO_CONTRIBUINTE">Não contribuinte</option>
              <option value="CONTRIBUINTE">Contribuinte</option>
              <option value="CONTRIBUINTE_ISENTO">Contribuinte isento</option>
            </select>
          </div>
          <div>
            <label class="field-label">Inscrição Estadual</label>
            <input v-model="form.inscricaoEstadual" />
          </div>
          <div>
            <label class="field-label">Inscrição Municipal</label>
            <input v-model="form.inscricaoMunicipal" />
          </div>
          <div>
            <label class="field-label">Inscrição Suframa</label>
            <input v-model="form.inscricaoSuframa" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Endereço</h2>
        <div class="grid grid-3">
          <div>
            <label class="field-label">CEP</label>
            <div class="input-action">
              <input v-model="form.cep" data-test="cep" />
              <button type="button" data-test="buscar-cep" @click="buscarCep">Buscar dados</button>
            </div>
            <p v-if="erroCep" class="field-error">{{ erroCep }}</p>
          </div>
          <div>
            <label class="field-label">Endereço</label>
            <input v-model="form.logradouro" data-test="logradouro" />
          </div>
          <div>
            <label class="field-label">Número</label>
            <input v-model="form.numero" />
          </div>
        </div>
        <div class="grid grid-4">
          <div>
            <label class="field-label">Estado</label>
            <select v-model="form.uf" data-test="uf">
              <option value="">UF</option>
              <option v-for="estado in UFS" :key="estado" :value="estado">{{ estado }}</option>
            </select>
          </div>
          <div>
            <label class="field-label">Cidade</label>
            <input v-model="form.cidade" data-test="cidade" />
          </div>
          <div>
            <label class="field-label">Bairro</label>
            <input v-model="form.bairro" />
          </div>
          <div>
            <label class="field-label">Complemento</label>
            <input v-model="form.complemento" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Outros Contatos</h2>
        <div v-for="(contato, index) in form.contatos" :key="index" class="grid grid-contato">
          <input v-model="contato.nome" placeholder="Nome" />
          <input v-model="contato.email" placeholder="email@exemplo.com" />
          <input v-model="contato.telefoneComercial" placeholder="(11) 3333-3333" />
          <input v-model="contato.telefoneCelular" placeholder="(11) 99999-9999" />
          <input v-model="contato.cargo" placeholder="Ex: Financeiro" />
          <button type="button" class="btn-remove" @click="removerContato(index)">🗑</button>
        </div>
        <button type="button" class="btn-add-contato" @click="adicionarContato">+ Adicionar Contato</button>
      </section>

      <section class="card">
        <h2>Observação</h2>
        <textarea v-model="form.observacao" rows="4" placeholder="Informações adicionais sobre o cliente..."></textarea>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Cliente</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  buscarParceiro,
  criarParceiro,
  atualizarParceiro,
  type ParceiroRequest,
  type PapelParceiro,
} from '@/api/parceiros'
import { buscarEnderecoPorCep } from '@/api/cep'

const UFS = [
  'AC', 'AL', 'AM', 'AP', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MG', 'MS', 'MT', 'PA', 'PB',
  'PE', 'PI', 'PR', 'RJ', 'RN', 'RO', 'RR', 'RS', 'SC', 'SE', 'SP', 'TO',
]

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): ParceiroRequest {
  return {
    tipoPessoa: 'JURIDICA',
    documento: '',
    nomeFantasia: '',
    razaoSocial: '',
    papeis: ['CLIENTE'],
    emailsCobranca: '',
    whatsapp: '',
    indicadorIe: null,
    inscricaoEstadual: '',
    inscricaoMunicipal: '',
    inscricaoSuframa: '',
    cep: '',
    logradouro: '',
    numero: '',
    bairro: '',
    complemento: '',
    uf: '',
    cidade: '',
    observacao: '',
    contatos: [],
  }
}

const form = reactive<ParceiroRequest>(novoFormulario())
const erros = reactive<{ nomeFantasia?: string; papeis?: string }>({})
const erroGeral = ref('')
const erroCep = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    const parceiro = await buscarParceiro(id)
    Object.assign(form, parceiro)
  }
})

function togglePapel(papel: PapelParceiro) {
  const index = form.papeis.indexOf(papel)
  if (index === -1) {
    form.papeis.push(papel)
  } else {
    form.papeis.splice(index, 1)
  }
}

function adicionarContato() {
  form.contatos.push({ nome: '', email: '', telefoneComercial: '', telefoneCelular: '', cargo: '' })
}

function removerContato(index: number) {
  form.contatos.splice(index, 1)
}

async function buscarCep() {
  erroCep.value = ''
  const endereco = await buscarEnderecoPorCep(form.cep)
  if (!endereco) {
    erroCep.value = 'CEP não encontrado — preencha o endereço manualmente'
    return
  }
  form.logradouro = endereco.logradouro
  form.bairro = endereco.bairro
  form.cidade = endereco.localidade
  form.uf = endereco.uf
}

function validar(): boolean {
  erros.nomeFantasia = form.nomeFantasia.trim() ? undefined : 'Campo obrigatório'
  erros.papeis = form.papeis.some((p) => p === 'CLIENTE' || p === 'FORNECEDOR')
    ? undefined
    : 'Selecione ao menos Cliente ou Fornecedor'
  return !erros.nomeFantasia && !erros.papeis
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
      await atualizarParceiro(id, form)
    } else {
      await criarParceiro(form)
    }
    router.push({ name: 'clientes' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um parceiro cadastrado com este documento.'
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
  router.push({ name: 'clientes' })
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

.grid-3 {
  grid-template-columns: 200px 1fr 1fr;
}

.grid-4 {
  grid-template-columns: repeat(4, 1fr);
}

.grid-contato {
  grid-template-columns: 1fr 1fr 130px 130px 130px 36px;
  align-items: end;
}

.field-label {
  display: block;
  font-size: 12px;
  color: var(--pm-text-mid);
  margin-bottom: 4px;
}

.hint {
  font-size: 11px;
  color: var(--pm-text-muted);
  margin: 0 0 8px;
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

.checkbox-row {
  display: flex;
  gap: 24px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--pm-text-dark);
}

.checkbox-inert {
  cursor: not-allowed;
  color: var(--pm-text-muted);
}

.input-action {
  display: flex;
  gap: 6px;
}

.input-action button {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.btn-remove {
  width: 36px;
  height: 36px;
  border: 1px solid var(--pm-error-bg);
  background: var(--pm-error-bg);
  color: var(--pm-error);
  border-radius: 8px;
  cursor: pointer;
}

.btn-add-contato {
  background: none;
  border: 1.5px dashed var(--pm-accent);
  color: var(--pm-accent);
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  cursor: pointer;
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

In `mesh-suite-frontend/src/router/index.ts`, add the import and the two routes (anywhere in the `routes` array — order doesn't matter to vue-router):

```typescript
import ClienteFormView from '@/views/ClienteFormView.vue'
```
```typescript
    { path: '/clientes/novo', name: 'clientes-novo', component: ClienteFormView },
    { path: '/clientes/:id/editar', name: 'clientes-editar', component: ClienteFormView },
```

- [ ] **Step 3: Write the test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ClienteFormView from '@/views/ClienteFormView.vue'
import * as parceirosApi from '@/api/parceiros'
import * as cepApi from '@/api/cep'

vi.mock('@/api/parceiros')
vi.mock('@/api/cep')

function mountWithRouter(path = '/clientes/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/clientes', name: 'clientes', component: { template: '<div />' } },
      { path: '/clientes/novo', name: 'clientes-novo', component: ClienteFormView },
      { path: '/clientes/:id/editar', name: 'clientes-editar', component: ClienteFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ClienteFormView, { global: { plugins: [router] } }),
  }))
}

describe('ClienteFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows a required-field error when nomeFantasia is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(parceirosApi.criarParceiro).not.toHaveBeenCalled()
  })

  it('requires at least Cliente or Fornecedor to be selected', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    // Cliente starts checked by default -- one toggle unchecks it, leaving papeis empty.
    await wrapper.find('input[type="checkbox"]').setValue(false)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione ao menos Cliente ou Fornecedor')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(parceirosApi.criarParceiro).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(parceirosApi.criarParceiro).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('clientes')
  })

  it('shows a conflict message on duplicate documento (409)', async () => {
    vi.mocked(parceirosApi.criarParceiro).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um parceiro cadastrado com este documento')
  })

  it('fills address fields when CEP lookup succeeds', async () => {
    vi.mocked(cepApi.buscarEnderecoPorCep).mockResolvedValue({
      logradouro: 'Av. Paulista', bairro: 'Bela Vista', localidade: 'São Paulo', uf: 'SP',
    })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="cep"]').setValue('01310100')
    await wrapper.find('[data-test="buscar-cep"]').trigger('click')
    await flushPromises()

    expect((wrapper.find('[data-test="logradouro"]').element as HTMLInputElement).value).toBe('Av. Paulista')
    expect((wrapper.find('[data-test="cidade"]').element as HTMLInputElement).value).toBe('São Paulo')
  })

  it('shows an error message when CEP lookup fails, without blocking manual entry', async () => {
    vi.mocked(cepApi.buscarEnderecoPorCep).mockResolvedValue(null)
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="cep"]').setValue('00000000')
    await wrapper.find('[data-test="buscar-cep"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('CEP não encontrado')
  })

  it('loads existing parceiro data in edit mode', async () => {
    vi.mocked(parceirosApi.buscarParceiro).mockResolvedValue({
      id: 'abc-123', tipoPessoa: 'JURIDICA', documento: '11222333000144', nomeFantasia: 'Mercado Silva',
      razaoSocial: '', status: 'ATIVO', papeis: ['CLIENTE'], emailsCobranca: '', whatsapp: '',
      indicadorIe: null, inscricaoEstadual: '', inscricaoMunicipal: '', inscricaoSuframa: '',
      cep: '', logradouro: '', numero: '', bairro: '', complemento: '', uf: '', cidade: '',
      observacao: '', contatos: [],
    } as any)

    const { wrapper } = await mountWithRouter('/clientes/abc-123/editar')
    await flushPromises()

    expect(parceirosApi.buscarParceiro).toHaveBeenCalledWith('abc-123')
    expect((wrapper.find('[data-test="nomeFantasia"]').element as HTMLInputElement).value).toBe('Mercado Silva')
  })
})
```

- [ ] **Step 4: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/ClienteFormView.spec.ts`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/ClienteFormView.vue mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/__tests__/ClienteFormView.spec.ts
git commit -m "feat: add ClienteFormView for creating and editing parceiros"
```

---

### Task 6: `ClientesListView.vue` + activate the sidebar "Clientes" item

**Files:**
- Create: `mesh-suite-frontend/src/views/ClientesListView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/ClientesListView.spec.ts`

**Interfaces:**
- Consumes: `listarParceiros`, `buscarResumoParceiros`, `atualizarStatusParceiro`, `excluirParceiro`, `ParceiroSummary`, `ParceiroResumo`, `Page`, `StatusParceiro` (Task 4); `AppShell` (existing); routes `clientes-novo`/`clientes-editar` (Task 5).
- Produces: route `clientes` (`/clientes`), the sidebar's entry point into this feature. Task 7 (detail) is reached from this list.

- [ ] **Step 1: Write `ClientesListView.vue`**

```vue
<template>
  <AppShell title="Clientes">
    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar cliente por nome..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="ATIVO">Ativo</option>
        <option value="EM_RISCO">Em Risco</option>
        <option value="BLOQUEADO">Bloqueado</option>
      </select>
      <select v-model="filtros.tipoDocumento" @change="carregar(0)">
        <option value="">Tipo de Documento</option>
        <option value="JURIDICA">CNPJ</option>
        <option value="FISICA">CPF</option>
      </select>
      <input v-model="filtros.uf" placeholder="UF" @change="carregar(0)" />
      <input v-model="filtros.cidade" placeholder="Cidade" @change="carregar(0)" />
      <button type="button" class="btn-primary" data-test="novo-cliente" @click="novoCliente">+ Novo Cliente</button>
    </div>

    <div v-if="resumo" class="resumo">
      <span class="resumo-item">{{ resumo.total }} Total</span>
      <span class="resumo-item resumo-ativo">{{ resumo.ativos }} Ativos</span>
      <span class="resumo-item resumo-risco">{{ resumo.emRisco }} Em Risco</span>
      <span class="resumo-item resumo-bloqueado">{{ resumo.bloqueados }} Bloqueados</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nome / Razão Social</th>
            <th>Cidade</th>
            <th>Telefone</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="parceiro in pagina.content" :key="parceiro.id">
            <td>
              <span class="link-nome" data-test="abrir-cliente" @click="abrirCliente(parceiro.id)">
                {{ parceiro.nomeFantasia }}
              </span>
            </td>
            <td>{{ parceiro.cidade }}</td>
            <td>{{ parceiro.whatsapp }}</td>
            <td><span class="badge" :class="`badge-${parceiro.status}`">{{ statusLabel(parceiro.status) }}</span></td>
            <td class="acoes">
              <button type="button" class="btn-acoes" @click="toggleAcoes(parceiro.id)">Ações</button>
              <div v-if="acoesAbertas === parceiro.id" class="dropdown-acoes">
                <div @click="editarCliente(parceiro.id)">Editar</div>
                <div @click="alternarStatus(parceiro)">
                  {{ parceiro.status === 'BLOQUEADO' ? 'Ativar' : 'Bloquear' }}
                </div>
                <div class="acao-excluir" @click="excluir(parceiro)">Excluir</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div class="paginacao">
      <button type="button" :disabled="pagina.number === 0" @click="carregar(pagina.number - 1)">‹</button>
      <span>Página {{ pagina.number + 1 }} de {{ Math.max(pagina.totalPages, 1) }}</span>
      <button type="button" :disabled="pagina.number + 1 >= pagina.totalPages" @click="carregar(pagina.number + 1)">›</button>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  listarParceiros,
  buscarResumoParceiros,
  atualizarStatusParceiro,
  excluirParceiro,
  type ParceiroSummary,
  type ParceiroResumo,
  type Page as ApiPage,
  type StatusParceiro,
  type TipoPessoa,
} from '@/api/parceiros'

const router = useRouter()

const filtros = reactive({ busca: '', status: '', tipoDocumento: '', uf: '', cidade: '' })
const pagina = ref<ApiPage<ParceiroSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<ParceiroResumo | null>(null)
const acoesAbertas = ref<string | null>(null)

function statusLabel(status: StatusParceiro) {
  return { ATIVO: 'Ativo', EM_RISCO: 'Em Risco', BLOQUEADO: 'Bloqueado' }[status]
}

async function carregar(page: number) {
  pagina.value = await listarParceiros({
    busca: filtros.busca || undefined,
    status: (filtros.status || undefined) as StatusParceiro | undefined,
    tipoDocumento: (filtros.tipoDocumento || undefined) as TipoPessoa | undefined,
    uf: filtros.uf || undefined,
    cidade: filtros.cidade || undefined,
    page,
    size: pagina.value.size,
  })
}

async function carregarResumo() {
  resumo.value = await buscarResumoParceiros()
}

function novoCliente() {
  router.push({ name: 'clientes-novo' })
}

function abrirCliente(id: string) {
  router.push({ name: 'clientes-detalhe', params: { id } })
}

function editarCliente(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'clientes-editar', params: { id } })
}

function toggleAcoes(id: string) {
  acoesAbertas.value = acoesAbertas.value === id ? null : id
}

async function alternarStatus(parceiro: ParceiroSummary) {
  acoesAbertas.value = null
  const novoStatus = parceiro.status === 'BLOQUEADO' ? 'ATIVO' : 'BLOQUEADO'
  await atualizarStatusParceiro(parceiro.id, novoStatus)
  await Promise.all([carregar(pagina.value.number), carregarResumo()])
}

async function excluir(parceiro: ParceiroSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir o cliente "${parceiro.nomeFantasia}"?`)) {
    return
  }
  await excluirParceiro(parceiro.id)
  await Promise.all([carregar(pagina.value.number), carregarResumo()])
}

onMounted(() => {
  carregar(0)
  carregarResumo()
})
</script>

<style scoped>
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

.resumo-risco {
  background: var(--pm-warning-bg);
  color: var(--pm-warning);
}

.resumo-bloqueado {
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

.link-nome {
  color: var(--pm-accent);
  cursor: pointer;
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

.badge-EM_RISCO {
  background: var(--pm-warning-bg);
  color: var(--pm-warning);
}

.badge-BLOQUEADO {
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.acoes {
  position: relative;
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
  position: absolute;
  right: 0;
  top: 100%;
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
import ClientesListView from '@/views/ClientesListView.vue'
```
```typescript
    { path: '/clientes', name: 'clientes', component: ClientesListView },
```

- [ ] **Step 3: Activate the sidebar item and fix sub-route highlighting**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, change the Clientes nav item's `route` from `null` to `'/clientes'`:

```typescript
  { icon: '👥', label: 'Clientes', route: '/clientes' },
```

And update `isActive` so `/clientes/novo`, `/clientes/:id`, `/clientes/:id/editar` all still highlight the "Clientes" item (a plain `===` match only works for `/clientes` exactly):

```typescript
function isActive(item: NavItem) {
  if (item.route === null) {
    return false
  }
  return item.route === '/' ? route.path === '/' : route.path.startsWith(item.route)
}
```

- [ ] **Step 4: Write the test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ClientesListView from '@/views/ClientesListView.vue'
import * as parceirosApi from '@/api/parceiros'

vi.mock('@/api/parceiros')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/clientes', name: 'clientes', component: ClientesListView },
      { path: '/clientes/novo', name: 'clientes-novo', component: { template: '<div />' } },
      { path: '/clientes/:id/editar', name: 'clientes-editar', component: { template: '<div />' } },
      { path: '/clientes/:id', name: 'clientes-detalhe', component: { template: '<div />' } },
    ],
  })
  router.push('/clientes')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ClientesListView, { global: { plugins: [router] } }),
  }))
}

const parceiroBase = {
  id: 'p1', nomeFantasia: 'Mercado Silva', razaoSocial: 'Mercado Silva Ltda',
  documento: '11222333000144', cidade: 'São Paulo', uf: 'SP', whatsapp: '(11) 3456-7890',
  status: 'ATIVO' as const,
}

describe('ClientesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(parceirosApi.listarParceiros).mockResolvedValue({
      content: [parceiroBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(parceirosApi.buscarResumoParceiros).mockResolvedValue({ total: 1, ativos: 1, emRisco: 0, bloqueados: 0 })
  })

  it('loads and displays the client list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
    expect(wrapper.text()).toContain('1 Total')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('silva')
    await flushPromises()

    expect(parceirosApi.listarParceiros).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('navigates to the create form when "+ Novo Cliente" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-cliente"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-novo')
  })

  it('navigates to the detail view when a client name is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="abrir-cliente"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-detalhe')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('toggles a client status via the Ações menu', async () => {
    vi.mocked(parceirosApi.atualizarStatusParceiro).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('.btn-acoes').trigger('click')
    await wrapper.findAll('.dropdown-acoes div')[1].trigger('click')
    await flushPromises()

    expect(parceirosApi.atualizarStatusParceiro).toHaveBeenCalledWith('p1', 'BLOQUEADO')
  })
})
```

- [ ] **Step 5: Update `AppSidebar.spec.ts` for the now-active Clientes item**

The existing test `does not navigate when an inert item (Pedidos) is clicked` already uses Pedidos (still inert) as its example, so it's unaffected. Add one new test to `mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts` confirming Clientes is now navigable and sub-routes still highlight it:

```typescript
  it('navigates to /clientes when Clientes is clicked, and highlights it from a sub-route', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/clientes', name: 'clientes', component: { template: '<div />' } },
        { path: '/clientes/novo', name: 'clientes-novo', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    await wrapper.find('[data-test="nav-Clientes"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/clientes')
    expect(wrapper.find('[data-test="nav-Clientes"]').classes()).toContain('nav-item-active')

    await router.push('/clientes/novo')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="nav-Clientes"]').classes()).toContain('nav-item-active')
  })
```

(This test needs its own `createRouter`/`mount` call rather than the file's shared `mountWithRouter()` helper, since it needs `/clientes` and `/clientes/novo` routes the helper doesn't declare.)

- [ ] **Step 6: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/ClientesListView.spec.ts src/components/__tests__/AppSidebar.spec.ts`
Expected: PASS (5 + 5 tests).

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/views/ClientesListView.vue mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/views/__tests__/ClientesListView.spec.ts \
        mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts
git commit -m "feat: add ClientesListView and activate the sidebar Clientes item"
```

---

### Task 7: `ClienteDetailView.vue` (tabbed profile)

**Files:**
- Create: `mesh-suite-frontend/src/views/ClienteDetailView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/ClienteDetailView.spec.ts`

**Interfaces:**
- Consumes: `buscarParceiro`, `listarParceiros`, `ParceiroResponse`, `ParceiroSummary` (Task 4); `AppShell` (existing); route `clientes-editar` (Task 5).
- Produces: route `clientes-detalhe` (`/clientes/:id`), navigated to from Task 6's list.

- [ ] **Step 1: Write `ClienteDetailView.vue`**

```vue
<template>
  <AppShell title="Cliente">
    <div class="detalhe">
      <aside class="rail">
        <input v-model="buscaRail" class="busca-rail" placeholder="Buscar cliente..." @input="carregarRail" />
        <div
          v-for="item in listaRail"
          :key="item.id"
          class="item-rail"
          :class="{ 'item-rail-ativo': item.id === parceiroId }"
          @click="selecionar(item.id)"
        >
          <div class="item-rail-nome">{{ item.nomeFantasia }}</div>
          <div class="item-rail-info">{{ item.cidade }}</div>
        </div>
      </aside>

      <div v-if="parceiro" class="painel">
        <div class="painel-header">
          <h1>{{ parceiro.nomeFantasia }}</h1>
          <div class="painel-acoes">
            <button type="button" class="btn-secondary" @click="editar">✏️ Editar</button>
            <button
              type="button"
              class="btn-primary btn-inert"
              title="Cadastro de pedidos fora de escopo desta fatia"
            >
              + Pedido
            </button>
          </div>
        </div>

        <div class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab"
            type="button"
            class="tab"
            :class="{ 'tab-ativa': abaAtiva === tab }"
            @click="abaAtiva = tab"
          >
            {{ tab }}
          </button>
        </div>

        <div v-if="abaAtiva === 'Dados'" class="grid grid-2">
          <div><label class="field-label">Razão Social</label><input :value="parceiro.razaoSocial" readonly /></div>
          <div><label class="field-label">CNPJ / CPF</label><input :value="parceiro.documento" readonly /></div>
          <div><label class="field-label">Nome Fantasia</label><input :value="parceiro.nomeFantasia" readonly /></div>
          <div><label class="field-label">Inscrição Estadual</label><input :value="parceiro.inscricaoEstadual" readonly /></div>
          <div>
            <label class="field-label">Tabela de Preço</label>
            <select disabled title="Depende do domínio Financeiro, ainda não implementado"><option>—</option></select>
          </div>
          <div>
            <label class="field-label">Limite de Crédito</label>
            <input disabled placeholder="—" title="Depende do domínio Financeiro, ainda não implementado" />
          </div>
          <div>
            <label class="field-label">Forma de Pagamento</label>
            <select disabled title="Depende do domínio Financeiro, ainda não implementado"><option>—</option></select>
          </div>
          <div>
            <label class="field-label">Vendedor Responsável</label>
            <select disabled title="Depende de atribuição de vendedor, ainda não implementada"><option>—</option></select>
          </div>
        </div>

        <div v-else-if="abaAtiva === 'Endereços'" class="endereco">
          <p>{{ parceiro.logradouro }}, {{ parceiro.numero }} — {{ parceiro.bairro }}</p>
          <p>{{ parceiro.cidade }} / {{ parceiro.uf }} — CEP {{ parceiro.cep }}</p>
        </div>

        <div v-else-if="abaAtiva === 'Contatos'">
          <div v-if="parceiro.contatos.length === 0" class="estado-vazio">Nenhum contato cadastrado</div>
          <div v-for="(contato, index) in parceiro.contatos" :key="index" class="contato-item">
            <strong>{{ contato.nome }}</strong> — {{ contato.cargo }}
            <div>{{ contato.email }} · {{ contato.telefoneComercial }}</div>
          </div>
        </div>

        <div v-else-if="abaAtiva === 'Pedidos'" class="estado-vazio">Nenhum pedido ainda</div>

        <div v-else-if="abaAtiva === 'Financeiro'" class="estado-vazio">Nenhum lançamento financeiro ainda</div>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { buscarParceiro, listarParceiros, type ParceiroResponse, type ParceiroSummary } from '@/api/parceiros'

const route = useRoute()
const router = useRouter()

const tabs = ['Dados', 'Endereços', 'Contatos', 'Pedidos', 'Financeiro'] as const
const abaAtiva = ref<(typeof tabs)[number]>('Dados')

const parceiroId = ref('')
const parceiro = ref<ParceiroResponse | null>(null)
const listaRail = ref<ParceiroSummary[]>([])
const buscaRail = ref('')

async function carregarParceiro(id: string) {
  parceiroId.value = id
  parceiro.value = await buscarParceiro(id)
  abaAtiva.value = 'Dados'
}

async function carregarRail() {
  const pagina = await listarParceiros({ busca: buscaRail.value || undefined, page: 0, size: 10 })
  listaRail.value = pagina.content
}

function selecionar(id: string) {
  router.push({ name: 'clientes-detalhe', params: { id } })
}

function editar() {
  router.push({ name: 'clientes-editar', params: { id: parceiroId.value } })
}

watch(
  () => route.params.id,
  (id) => {
    if (typeof id === 'string') {
      carregarParceiro(id)
    }
  },
)

onMounted(() => {
  carregarRail()
  const id = route.params.id
  if (typeof id === 'string') {
    carregarParceiro(id)
  }
})
</script>

<style scoped>
.detalhe {
  display: flex;
  gap: 16px;
  font-family: var(--pm-font);
}

.rail {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.busca-rail {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  margin-bottom: 6px;
  box-sizing: border-box;
  width: 100%;
}

.item-rail {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  background: var(--pm-white);
}

.item-rail-ativo {
  border-color: var(--pm-accent);
  background: var(--pm-accent-bg);
}

.item-rail-nome {
  font-size: 12px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.item-rail-info {
  font-size: 11px;
  color: var(--pm-text-muted);
}

.painel {
  flex: 1;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px;
}

.painel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.painel-header h1 {
  font-size: 18px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0;
}

.painel-acoes {
  display: flex;
  gap: 8px;
}

.btn-primary,
.btn-secondary {
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
}

.btn-inert {
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
}

.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--pm-border-light);
  margin-bottom: 14px;
}

.tab {
  background: none;
  border: none;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--pm-text-mid);
  cursor: pointer;
  border-bottom: 2px solid transparent;
}

.tab-ativa {
  color: var(--pm-accent);
  border-bottom-color: var(--pm-accent);
  font-weight: 600;
}

.grid {
  display: grid;
  gap: 12px 16px;
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
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  color: var(--pm-text-dark);
  font-family: var(--pm-font);
}

input:disabled,
select:disabled,
input[readonly] {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.endereco p {
  font-size: 13px;
  color: var(--pm-text-dark);
  margin: 0 0 4px;
}

.contato-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--pm-border-light);
  font-size: 13px;
  color: var(--pm-text-dark);
}

.estado-vazio {
  color: var(--pm-text-muted);
  font-size: 13px;
  padding: 24px 0;
  text-align: center;
}
</style>
```

- [ ] **Step 2: Register the route**

In `mesh-suite-frontend/src/router/index.ts`, add the import and route:

```typescript
import ClienteDetailView from '@/views/ClienteDetailView.vue'
```
```typescript
    { path: '/clientes/:id', name: 'clientes-detalhe', component: ClienteDetailView },
```

**Route ordering note**: vue-router matches routes in registration order for overlapping patterns. `/clientes/novo` and `/clientes/:id/editar` (Task 5) and `/clientes` (Task 6) must be registered **before** this catch-all-looking `/clientes/:id`, or `/clientes/novo` would incorrectly match `:id = 'novo'` here instead. Since Tasks 5 and 6 land first and this task only adds one more route, just append this route after the existing ones (don't reorder the array) and it's safe.

- [ ] **Step 3: Write the test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ClienteDetailView from '@/views/ClienteDetailView.vue'
import * as parceirosApi from '@/api/parceiros'

vi.mock('@/api/parceiros')

const parceiroCompleto = {
  id: 'p1', tipoPessoa: 'JURIDICA', documento: '11222333000144', nomeFantasia: 'Mercado Silva',
  razaoSocial: 'Mercado Silva Ltda', status: 'ATIVO', papeis: ['CLIENTE'], emailsCobranca: '', whatsapp: '',
  indicadorIe: null, inscricaoEstadual: '', inscricaoMunicipal: '', inscricaoSuframa: '',
  cep: '01310100', logradouro: 'Av. Paulista', numero: '1000', bairro: 'Bela Vista', complemento: '',
  uf: 'SP', cidade: 'São Paulo', observacao: '', contatos: [],
} as any

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/clientes/:id', name: 'clientes-detalhe', component: ClienteDetailView },
      { path: '/clientes/:id/editar', name: 'clientes-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/clientes/p1')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ClienteDetailView, { global: { plugins: [router] } }),
  }))
}

describe('ClienteDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(parceirosApi.buscarParceiro).mockResolvedValue(parceiroCompleto)
    vi.mocked(parceirosApi.listarParceiros).mockResolvedValue({
      content: [{
        id: 'p1', nomeFantasia: 'Mercado Silva', razaoSocial: '', documento: '',
        cidade: 'São Paulo', uf: 'SP', whatsapp: '', status: 'ATIVO',
      }],
      totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the selected client on the Dados tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
    expect((wrapper.find('input[readonly]').element as HTMLInputElement).value).toBe('Mercado Silva Ltda')
  })

  it('shows an empty state on the Pedidos tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const pedidosTab = wrapper.findAll('.tab').find((t) => t.text() === 'Pedidos')!
    await pedidosTab.trigger('click')

    expect(wrapper.text()).toContain('Nenhum pedido ainda')
  })

  it('navigates to the edit form when Editar is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('button.btn-secondary').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })
})
```

- [ ] **Step 4: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/ClienteDetailView.spec.ts`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/ClienteDetailView.vue mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/__tests__/ClienteDetailView.spec.ts
git commit -m "feat: add ClienteDetailView with tabbed profile"
```

---

## Final verification

- [ ] Run `cd mesh-suite-backend && ./mvnw test` — full backend suite passes (existing `auth`/`empresa`/`usuario`/`tenant` tests + new `parceiro` tests).
- [ ] Run `cd mesh-suite-frontend && npx vitest run` — full frontend suite passes.
- [ ] Run `cd mesh-suite-frontend && npm run build` — production build succeeds.
- [ ] Run the app (`./devup.sh`), log in, click "Clientes" in the sidebar: create a client (fill Dados Gerais + at least Cliente or Fornecedor), confirm it appears in the list with the right status badge, click its name to open the detail view and see the tabs (Pedidos/Financeiro show empty states), click "✏️ Editar" to return to the form, use the CEP field's "Buscar dados" button with a real CEP (e.g. `01310100`) and confirm address fields populate, add/remove an "Outros Contatos" row, save, then use the list's "Ações" menu to Bloquear/Ativar and finally Excluir the test client.
