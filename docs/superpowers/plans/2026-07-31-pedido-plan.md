# Cadastro de Pedido — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Pedido domain (listagem + formulário único, criar/editar) on top of the existing Cliente/Fornecedor and Produto master-data domains, following the approved spec at `docs/superpowers/specs/2026-07-31-pedido-design.md`.

**Architecture:** Same layered pattern as `parceiro`/`produto`: JPA entities with RLS-backed `tenant_id` isolation, a `PedidoService` holding all business rules (papel validation, per-tenant sequential numbering, line-item snapshotting, linear status progression, totals), a thin `PedidoController`, and two Vue views (`PedidosListView.vue`, `PedidoFormView.vue`) reusing the AppShell/Teleport-dropdown/try-catch-error conventions already established.

**Tech Stack:** Spring Boot 3.4.5 / Java 21, Postgres 16 (Row-Level Security, Flyway), Vue 3 + TypeScript + Vite, Vitest + Vue Test Utils, JUnit 5 + Testcontainers + AssertJ.

## Global Constraints

These are project-wide requirements carried over from the Cliente and Produto slices — every task below implicitly includes them, and several were found as real bugs in those slices, so **do not repeat them here**.

- **RLS on every new table**: `ALTER TABLE … ENABLE ROW LEVEL SECURITY; … FORCE ROW LEVEL SECURITY;` plus a policy using `tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid`. Child tables with no own `tenant_id` column (`item_pedido`) use an `EXISTS` subquery against the parent row's own tenant-scoped policy instead — same pattern as `parceiro_contato`.
- **Tenant scoping is never explicit in queries.** `TenantContext` (ThreadLocal) + `TenantContextAspect` (`SET LOCAL app.tenant_id` on every `@Transactional` method) + `JwtAuthenticationFilter` do all the work. `tenantId` is only set explicitly when constructing a brand-new top-level entity (`Pedido`); child entities (`ItemPedido`) never get their own `tenantId`.
- **Service test setup must call both** the raw `SET LOCAL app.tenant_id` native query **and** `TenantContext.set(tenantId)`. Omitting `TenantContext.set(...)` silently breaks RLS-scoped service calls — this was a real bug in Cliente Task 2.
- **Cross-tenant MockMvc tests must call `entityManager.clear()`** at the point where the test switches from tenant A's cookie to tenant B's. Without it, Hibernate's first-level cache (shared across one `@Transactional` test method) returns tenant A's already-managed entity without re-issuing SQL, masking RLS behind a false 200 instead of the expected 404 — a real bug in Cliente Task 3.
- **Every domain gets its own scoped `@RestControllerAdvice(assignableTypes = XController.class)`** for `DataIntegrityViolationException`. Never add domain-worded exception handling to the shared `GlobalExceptionHandler` — a handler there is global, so Parceiro-worded (or Pedido-worded) text would leak onto unrelated domains' constraint violations. This was a real bug fixed during Cliente's final review. Domain-unique exception types (`PedidoNaoEncontradoException`, `PedidoValidacaoException`) are safe to register directly in the shared `GlobalExceptionHandler`, since no other domain throws them — same treatment as `ParceiroNaoEncontradoException`/`ProdutoNaoEncontradoException`.
- **Every list view's Ações dropdown menu uses `<Teleport to="body">` with `position: fixed`**, computed from the trigger button's `getBoundingClientRect()`, from the start. `position: absolute` inside a `.card { overflow: hidden }` silently clips the dropdown in real browsers — invisible to jsdom tests. This was a real bug in Cliente, pre-applied correctly in Produto, and must be pre-applied here too.
- **Mounting a Teleport-using view in tests** requires `global: { stubs: { teleport: true } } }` so the dropdown renders in place for `wrapper.find()` queries.
- **Numeric optional fields bound with `v-model.number` produce `''` (not `null`) when cleared.** Every payload-building function must normalize `''`/`undefined` right before the API call — to `null` for genuinely optional fields, to `0` where a numeric default applies. A regression test for this must actually drive an input through the empty-string state via `setValue('123')` then `setValue('')`, not just assert a field that was already `null`/`0` by default untouched — this exact gap was a real bug in Produto Task 5.
- **`vi.clearAllMocks()` in every view spec file's `beforeEach`**, to prevent one test's mock call bleeding into a later test's assertion — a real bonus bug found in Produto Task 5.
- **Every data-loading/mutating function in a view wraps its work in try/catch** and sets a user-facing `erro`/`erroGeral` ref. Never let a rejected promise propagate unhandled.
- **`valor_unitario` on each `ItemPedido` is captured at add-time** from `produto.precoVenda` and stored independently on the row — never re-derived from the live `Produto` on read, so later price changes never retroactively alter existing pedidos.
- **`numero` is generated exactly once per pedido**, via an atomic `UPDATE … RETURNING` against the tenant's single `pedido_contador` row, executed inside the same `@Transactional` method that creates the `Pedido`. Never assigned client-side or computed via `COUNT(*)`/`MAX(numero)+1` — both race under concurrent inserts.
- **Status only advances one step at a time**, in the fixed order `DIGITADO → EM_PREPARO → FATURADO`. The service compares enum ordinals and rejects (400) anything that isn't exactly `current.ordinal() + 1` — skipping or regressing is always rejected. Editing a Faturado pedido's other fields (client, items, etc.) remains allowed in this slice — deliberate, per spec §8 risk 1, not a bug to fix.
- **Design tokens only**: every new/changed `.vue` file uses `var(--pm-*)` tokens exclusively (the established `rgba(0,0,0,X)` box-shadow elevation exception still applies).

---

### Task 1: Backend — Pedido domain model (migration, entities, repositories)

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V7__create_pedido.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/StatusPedido.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/Pedido.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/ItemPedido.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoContador.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoContadorRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoRepositoryTest.java`

**Interfaces:**
- Produces: `Pedido` entity (`id`, `tenantId`, `numero: Integer`, `cliente: Parceiro`, `vendedor: Usuario`, `dataPedido: LocalDate`, `dataEntrega: LocalDate`, `status: StatusPedido`, `desconto/subtotal/total: BigDecimal`, `criadoEm: Instant`, `itens: List<ItemPedido>`); `ItemPedido` entity (`id`, `pedido: Pedido`, `produto: Produto`, `quantidade/valorUnitario/valorTotal: BigDecimal`); `PedidoContador` entity (`tenantId` as `@Id`, `proximoNumero: Integer`); `StatusPedido` enum with declared order `DIGITADO, EM_PREPARO, FATURADO` (ordinal comparison drives progression validation in Task 2); `PedidoRepository extends JpaRepository<Pedido, UUID>, JpaSpecificationExecutor<Pedido>` with `long countByStatus(StatusPedido status)`; `PedidoContadorRepository extends JpaRepository<PedidoContador, UUID>`.
- Consumes: `Parceiro` (`com.meshsuite.parceiro.Parceiro`), `Usuario` (`com.meshsuite.usuario.Usuario`), `Produto` (`com.meshsuite.produto.Produto`) — all already exist.

- [ ] **Step 1: Write the migration**

```sql
CREATE TABLE pedido_contador (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    proximo_numero INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE pedido_contador ENABLE ROW LEVEL SECURITY;
ALTER TABLE pedido_contador FORCE ROW LEVEL SECURITY;

CREATE POLICY pedido_contador_tenant_isolation ON pedido_contador
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE pedido (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    numero INTEGER NOT NULL,
    cliente_id UUID NOT NULL REFERENCES parceiro(id),
    vendedor_id UUID NOT NULL REFERENCES usuario(id),
    data_pedido DATE NOT NULL DEFAULT CURRENT_DATE,
    data_entrega DATE,
    status VARCHAR(10) NOT NULL DEFAULT 'DIGITADO' CHECK (status IN ('DIGITADO','EM_PREPARO','FATURADO')),
    desconto NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_pedido_tenant_numero ON pedido(tenant_id, numero);
CREATE INDEX idx_pedido_tenant_id ON pedido(tenant_id);
CREATE INDEX idx_pedido_cliente_id ON pedido(cliente_id);
CREATE INDEX idx_pedido_vendedor_id ON pedido(vendedor_id);

ALTER TABLE pedido ENABLE ROW LEVEL SECURITY;
ALTER TABLE pedido FORCE ROW LEVEL SECURITY;

CREATE POLICY pedido_tenant_isolation ON pedido
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE item_pedido (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pedido_id UUID NOT NULL REFERENCES pedido(id) ON DELETE CASCADE,
    produto_id UUID NOT NULL REFERENCES produto(id),
    quantidade NUMERIC(12,3) NOT NULL,
    valor_unitario NUMERIC(12,2) NOT NULL,
    valor_total NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_item_pedido_pedido_id ON item_pedido(pedido_id);

ALTER TABLE item_pedido ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_pedido FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent pedido
-- row's own RLS policy, matched by pedido_id. Same pattern as parceiro_contato.
CREATE POLICY item_pedido_tenant_isolation ON item_pedido
    USING (EXISTS (
        SELECT 1 FROM pedido p
        WHERE p.id = item_pedido.pedido_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

- [ ] **Step 2: Write `StatusPedido.java`**

```java
package com.meshsuite.pedido;

public enum StatusPedido {
    DIGITADO,
    EM_PREPARO,
    FATURADO
}
```

- [ ] **Step 3: Write `Pedido.java`**

```java
package com.meshsuite.pedido;

import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.usuario.Usuario;
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
@Table(name = "pedido")
@Getter
@Setter
public class Pedido {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Integer numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Parceiro cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Usuario vendedor;

    @Column(name = "data_pedido", nullable = false)
    private LocalDate dataPedido = LocalDate.now();

    @Column(name = "data_entrega")
    private LocalDate dataEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusPedido status = StatusPedido.DIGITADO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemPedido> itens = new ArrayList<>();
}
```

- [ ] **Step 4: Write `ItemPedido.java`**

```java
package com.meshsuite.pedido;

import com.meshsuite.produto.Produto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "item_pedido")
@Getter
@Setter
public class ItemPedido {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;
}
```

- [ ] **Step 5: Write `PedidoContador.java`**

```java
package com.meshsuite.pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "pedido_contador")
@Getter
@Setter
public class PedidoContador {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "proximo_numero", nullable = false)
    private Integer proximoNumero = 1;
}
```

- [ ] **Step 6: Write `PedidoRepository.java`**

```java
package com.meshsuite.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID>, JpaSpecificationExecutor<Pedido> {
    long countByStatus(StatusPedido status);
}
```

- [ ] **Step 7: Write `PedidoContadorRepository.java`**

```java
package com.meshsuite.pedido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PedidoContadorRepository extends JpaRepository<PedidoContador, UUID> {
}
```

- [ ] **Step 8: Write the failing repository/RLS test**

```java
package com.meshsuite.pedido;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PedidoContadorRepository pedidoContadorRepository;
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

    private Parceiro criarCliente(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p);
    }

    private Usuario criarVendedor(UUID tenantId, String email) {
        Usuario u = new Usuario();
        u.setTenantId(tenantId);
        u.setNome("Marina");
        u.setEmail(email);
        u.setSenhaHash("hash");
        u.setPapel(Papel.REPRESENTANTE);
        return usuarioRepository.saveAndFlush(u);
    }

    private Produto criarProduto(UUID tenantId, String sku) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("59.90"));
        return produtoRepository.saveAndFlush(p);
    }

    private Pedido novoPedido(UUID tenantId, Parceiro cliente, Usuario vendedor, int numero) {
        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setNumero(numero);
        pedido.setCliente(cliente);
        pedido.setVendedor(vendedor);
        return pedido;
    }

    @Test
    @Transactional
    void savesPedidoWithItensViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        Usuario vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Produto produto = criarProduto(tenant.getId(), "P0001");

        Pedido pedido = novoPedido(tenant.getId(), cliente, vendedor, 1);
        ItemPedido item = new ItemPedido();
        item.setPedido(pedido);
        item.setProduto(produto);
        item.setQuantidade(new BigDecimal("2"));
        item.setValorUnitario(new BigDecimal("59.90"));
        item.setValorTotal(new BigDecimal("119.80"));
        pedido.getItens().add(item);

        Pedido saved = pedidoRepository.saveAndFlush(pedido);
        entityManager.clear();

        Pedido reloaded = pedidoRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusPedido.DIGITADO);
        assertThat(reloaded.getItens()).hasSize(1);
        assertThat(reloaded.getItens().get(0).getValorTotal()).isEqualByComparingTo("119.80");
    }

    @Test
    @Transactional
    void removingAnItemFromTheListDeletesItViaOrphanRemoval() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        Usuario vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Produto produto = criarProduto(tenant.getId(), "P0001");

        Pedido pedido = novoPedido(tenant.getId(), cliente, vendedor, 1);
        ItemPedido item = new ItemPedido();
        item.setPedido(pedido);
        item.setProduto(produto);
        item.setQuantidade(BigDecimal.ONE);
        item.setValorUnitario(new BigDecimal("59.90"));
        item.setValorTotal(new BigDecimal("59.90"));
        pedido.getItens().add(item);
        Pedido saved = pedidoRepository.saveAndFlush(pedido);

        saved.getItens().clear();
        pedidoRepository.saveAndFlush(saved);
        entityManager.clear();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM item_pedido WHERE pedido_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void numeroMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        Usuario vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");

        pedidoRepository.saveAndFlush(novoPedido(tenant.getId(), cliente, vendedor, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> pedidoRepository.saveAndFlush(novoPedido(tenant.getId(), cliente, vendedor, 1)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        Usuario vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        pedidoRepository.saveAndFlush(novoPedido(tenant.getId(), cliente, vendedor, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM pedido")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void proximoNumeroIncrementsAtomicallyPerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        entityManager.createNativeQuery(
                "INSERT INTO pedido_contador (tenant_id, proximo_numero) VALUES (:tenantId, 1) " +
                        "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenant.getId())
                .executeUpdate();

        Object primeiro = entityManager.createNativeQuery(
                        "UPDATE pedido_contador SET proximo_numero = proximo_numero + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING proximo_numero - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();
        Object segundo = entityManager.createNativeQuery(
                        "UPDATE pedido_contador SET proximo_numero = proximo_numero + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING proximo_numero - 1")
                .setParameter("tenantId", tenant.getId())
                .getSingleResult();

        assertThat(((Number) primeiro).intValue()).isEqualTo(1);
        assertThat(((Number) segundo).intValue()).isEqualTo(2);
    }

    @Test
    @Transactional
    void pedidoContadorRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        PedidoContador contador = new PedidoContador();
        contador.setTenantId(tenant.getId());
        contador.setProximoNumero(1);
        pedidoContadorRepository.saveAndFlush(contador);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM pedido_contador")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=PedidoRepositoryTest`
Expected: PASS (6 tests, 0 failures). This requires Docker running for Testcontainers.

- [ ] **Step 10: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V7__create_pedido.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/pedido/ \
        mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoRepositoryTest.java
git commit -m "feat(pedido): add Pedido/ItemPedido/PedidoContador entities, migration and repositories"
```

---

### Task 2: Backend — DTOs, exceptions, specifications, PedidoService

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/ItemPedidoDto.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/ItemPedidoResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoResumoResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/dto/PedidoStatusRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoNaoEncontradoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoValidacaoException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoServiceTest.java`

**Interfaces:**
- Consumes (from Task 1): `Pedido`, `ItemPedido`, `PedidoContador`, `StatusPedido`, `PedidoRepository`. Also consumes existing `ParceiroRepository` (`findById`), `Parceiro.getPapeis(): Set<PapelParceiro>`, `UsuarioRepository` (`findById`), `Usuario.getPapel(): Papel`, `ProdutoRepository` (`findById`), `Produto.getNome()`/`getId()`.
- Produces: `PedidoService` with `listar(String busca, StatusPedido status, Pageable pageable): Page<PedidoSummaryResponse>`, `resumo(): PedidoResumoResponse`, `buscarPorId(UUID id): PedidoResponse`, `criar(UUID tenantId, PedidoRequest request): PedidoResponse`, `atualizar(UUID id, PedidoRequest request): PedidoResponse`, `avancarStatus(UUID id, StatusPedido novoStatus): PedidoResponse`, `excluir(UUID id): void`. Thrown exceptions: `PedidoNaoEncontradoException` (404), `PedidoValidacaoException` (400, message varies by cause — cliente sem papel, vendedor sem papel, progressão de status inválida).

- [ ] **Step 1: Write the DTOs**

`ItemPedidoDto.java`:
```java
package com.meshsuite.pedido.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemPedidoDto(
        @NotNull UUID produtoId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantidade,
        @NotNull @DecimalMin(value = "0.00") BigDecimal valorUnitario) {
}
```

`ItemPedidoResponse.java`:
```java
package com.meshsuite.pedido.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemPedidoResponse(
        UUID produtoId,
        String produtoNome,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal) {
}
```

`PedidoRequest.java`:
```java
package com.meshsuite.pedido.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PedidoRequest(
        @NotNull UUID clienteId,
        @NotNull UUID vendedorId,
        LocalDate dataPedido,
        LocalDate dataEntrega,
        BigDecimal desconto,
        @NotEmpty List<@Valid ItemPedidoDto> itens) {
}
```

`PedidoResponse.java`:
```java
package com.meshsuite.pedido.dto;

import com.meshsuite.pedido.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PedidoResponse(
        UUID id,
        Integer numero,
        UUID clienteId,
        String clienteNome,
        UUID vendedorId,
        String vendedorNome,
        LocalDate dataPedido,
        LocalDate dataEntrega,
        StatusPedido status,
        BigDecimal desconto,
        BigDecimal subtotal,
        BigDecimal total,
        List<ItemPedidoResponse> itens) {
}
```

`PedidoSummaryResponse.java`:
```java
package com.meshsuite.pedido.dto;

import com.meshsuite.pedido.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PedidoSummaryResponse(
        UUID id,
        Integer numero,
        String clienteNome,
        String vendedorNome,
        LocalDate dataPedido,
        BigDecimal total,
        StatusPedido status) {
}
```

`PedidoResumoResponse.java`:
```java
package com.meshsuite.pedido.dto;

public record PedidoResumoResponse(long total, long digitados, long emPreparo, long faturados) {
}
```

`PedidoStatusRequest.java`:
```java
package com.meshsuite.pedido.dto;

import com.meshsuite.pedido.StatusPedido;
import jakarta.validation.constraints.NotNull;

public record PedidoStatusRequest(@NotNull StatusPedido status) {
}
```

- [ ] **Step 2: Write the exceptions**

`PedidoNaoEncontradoException.java`:
```java
package com.meshsuite.pedido;

public class PedidoNaoEncontradoException extends RuntimeException {
    public PedidoNaoEncontradoException() {
        super("Pedido não encontrado");
    }
}
```

`PedidoValidacaoException.java`:
```java
package com.meshsuite.pedido;

public class PedidoValidacaoException extends RuntimeException {
    public PedidoValidacaoException(String mensagem) {
        super(mensagem);
    }
}
```

- [ ] **Step 3: Write `PedidoSpecifications.java`**

```java
package com.meshsuite.pedido;

import org.springframework.data.jpa.domain.Specification;

public final class PedidoSpecifications {

    private PedidoSpecifications() {
    }

    public static Specification<Pedido> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        Integer numero = tryParseInt(busca.trim());
        return (root, query, cb) -> {
            var porTexto = cb.or(
                    cb.like(cb.lower(root.get("cliente").get("nomeFantasia")), termo),
                    cb.like(cb.lower(root.get("vendedor").get("nome")), termo));
            if (numero != null) {
                return cb.or(porTexto, cb.equal(root.get("numero"), numero));
            }
            return porTexto;
        };
    }

    public static Specification<Pedido> comStatus(StatusPedido status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Integer tryParseInt(String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Write `PedidoService.java`**

```java
package com.meshsuite.pedido;

import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.pedido.dto.*;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ParceiroRepository parceiroRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProdutoRepository produtoRepository;
    private final EntityManager entityManager;

    public PedidoService(PedidoRepository pedidoRepository, ParceiroRepository parceiroRepository,
                          UsuarioRepository usuarioRepository, ProdutoRepository produtoRepository,
                          EntityManager entityManager) {
        this.pedidoRepository = pedidoRepository;
        this.parceiroRepository = parceiroRepository;
        this.usuarioRepository = usuarioRepository;
        this.produtoRepository = produtoRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<PedidoSummaryResponse> listar(String busca, StatusPedido status, Pageable pageable) {
        Specification<Pedido> spec = Specification.allOf(
                PedidoSpecifications.comBusca(busca),
                PedidoSpecifications.comStatus(status));
        return pedidoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public PedidoResumoResponse resumo() {
        long digitados = pedidoRepository.countByStatus(StatusPedido.DIGITADO);
        long emPreparo = pedidoRepository.countByStatus(StatusPedido.EM_PREPARO);
        long faturados = pedidoRepository.countByStatus(StatusPedido.FATURADO);
        return new PedidoResumoResponse(digitados + emPreparo + faturados, digitados, emPreparo, faturados);
    }

    @Transactional(readOnly = true)
    public PedidoResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    public PedidoResponse criar(UUID tenantId, PedidoRequest request) {
        Parceiro cliente = buscarClienteValido(request.clienteId());
        Usuario vendedor = buscarVendedorValido(request.vendedorId());

        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setNumero(proximoNumero(tenantId));
        aplicar(pedido, cliente, vendedor, request);
        return toResponse(pedidoRepository.saveAndFlush(pedido));
    }

    @Transactional
    public PedidoResponse atualizar(UUID id, PedidoRequest request) {
        Parceiro cliente = buscarClienteValido(request.clienteId());
        Usuario vendedor = buscarVendedorValido(request.vendedorId());

        Pedido pedido = buscarEntidadePorId(id);
        aplicar(pedido, cliente, vendedor, request);
        return toResponse(pedidoRepository.saveAndFlush(pedido));
    }

    @Transactional
    public PedidoResponse avancarStatus(UUID id, StatusPedido novoStatus) {
        Pedido pedido = buscarEntidadePorId(id);
        int atual = pedido.getStatus().ordinal();
        int alvo = novoStatus.ordinal();
        if (alvo != atual + 1) {
            throw new PedidoValidacaoException(
                    "Não é possível avançar de " + pedido.getStatus() + " para " + novoStatus);
        }
        pedido.setStatus(novoStatus);
        return toResponse(pedidoRepository.saveAndFlush(pedido));
    }

    @Transactional
    public void excluir(UUID id) {
        pedidoRepository.delete(buscarEntidadePorId(id));
    }

    private Pedido buscarEntidadePorId(UUID id) {
        return pedidoRepository.findById(id).orElseThrow(PedidoNaoEncontradoException::new);
    }

    private Parceiro buscarClienteValido(UUID clienteId) {
        Parceiro parceiro = parceiroRepository.findById(clienteId)
                .orElseThrow(() -> new PedidoValidacaoException("Cliente não encontrado"));
        if (!parceiro.getPapeis().contains(PapelParceiro.CLIENTE)) {
            throw new PedidoValidacaoException("O parceiro selecionado não tem o papel Cliente");
        }
        return parceiro;
    }

    private Usuario buscarVendedorValido(UUID vendedorId) {
        Usuario usuario = usuarioRepository.findById(vendedorId)
                .orElseThrow(() -> new PedidoValidacaoException("Vendedor não encontrado"));
        if (usuario.getPapel() != Papel.REPRESENTANTE) {
            throw new PedidoValidacaoException("O usuário selecionado não tem o papel Representante");
        }
        return usuario;
    }

    // Atomic UPDATE ... RETURNING against the tenant's single pedido_contador row --
    // never COUNT(*)/MAX(numero)+1, both of which race under concurrent inserts.
    // Runs inside this method's own @Transactional, so TenantContextAspect has
    // already issued SET LOCAL app.tenant_id before either native query below runs.
    private int proximoNumero(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO pedido_contador (tenant_id, proximo_numero) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object resultado = entityManager.createNativeQuery(
                        "UPDATE pedido_contador SET proximo_numero = proximo_numero + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING proximo_numero - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) resultado).intValue();
    }

    private void aplicar(Pedido pedido, Parceiro cliente, Usuario vendedor, PedidoRequest request) {
        pedido.setCliente(cliente);
        pedido.setVendedor(vendedor);
        pedido.setDataPedido(request.dataPedido() != null ? request.dataPedido() : LocalDate.now());
        pedido.setDataEntrega(request.dataEntrega());
        pedido.setDesconto(request.desconto() != null ? request.desconto() : BigDecimal.ZERO);

        pedido.getItens().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedidoDto dto : request.itens()) {
            Produto produto = produtoRepository.findById(dto.produtoId())
                    .orElseThrow(() -> new PedidoValidacaoException("Produto não encontrado"));
            ItemPedido item = new ItemPedido();
            item.setPedido(pedido);
            item.setProduto(produto);
            item.setQuantidade(dto.quantidade());
            item.setValorUnitario(dto.valorUnitario());
            BigDecimal valorTotalItem = dto.quantidade().multiply(dto.valorUnitario());
            item.setValorTotal(valorTotalItem);
            pedido.getItens().add(item);
            subtotal = subtotal.add(valorTotalItem);
        }
        pedido.setSubtotal(subtotal);
        pedido.setTotal(subtotal.subtract(pedido.getDesconto()));
    }

    private PedidoSummaryResponse toSummary(Pedido p) {
        return new PedidoSummaryResponse(p.getId(), p.getNumero(), p.getCliente().getNomeFantasia(),
                p.getVendedor().getNome(), p.getDataPedido(), p.getTotal(), p.getStatus());
    }

    private PedidoResponse toResponse(Pedido p) {
        List<ItemPedidoResponse> itens = p.getItens().stream()
                .map(i -> new ItemPedidoResponse(i.getProduto().getId(), i.getProduto().getNome(),
                        i.getQuantidade(), i.getValorUnitario(), i.getValorTotal()))
                .toList();
        return new PedidoResponse(p.getId(), p.getNumero(), p.getCliente().getId(), p.getCliente().getNomeFantasia(),
                p.getVendedor().getId(), p.getVendedor().getNome(), p.getDataPedido(), p.getDataEntrega(),
                p.getStatus(), p.getDesconto(), p.getSubtotal(), p.getTotal(), itens);
    }
}
```

- [ ] **Step 5: Write the failing service test**

```java
package com.meshsuite.pedido;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.pedido.dto.ItemPedidoDto;
import com.meshsuite.pedido.dto.PedidoRequest;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class PedidoServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoService pedidoService;
    @Autowired EntityManager entityManager;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());
        return tenant.getId();
    }

    private UUID criarCliente(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p).getId();
    }

    private UUID criarFornecedor(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Tecidos Aurora");
        p.getPapeis().add(PapelParceiro.FORNECEDOR);
        return parceiroRepository.saveAndFlush(p).getId();
    }

    private UUID criarVendedor(UUID tenantId, String email) {
        Usuario u = new Usuario();
        u.setTenantId(tenantId);
        u.setNome("Marina");
        u.setEmail(email);
        u.setSenhaHash("hash");
        u.setPapel(Papel.REPRESENTANTE);
        return usuarioRepository.saveAndFlush(u).getId();
    }

    private UUID criarAdministrativo(UUID tenantId, String email) {
        Usuario u = new Usuario();
        u.setTenantId(tenantId);
        u.setNome("Carlos");
        u.setEmail(email);
        u.setSenhaHash("hash");
        u.setPapel(Papel.ADMINISTRATIVO);
        return usuarioRepository.saveAndFlush(u).getId();
    }

    private UUID criarProduto(UUID tenantId, String sku, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(precoVenda);
        return produtoRepository.saveAndFlush(p).getId();
    }

    private PedidoRequest request(UUID clienteId, UUID vendedorId, List<ItemPedidoDto> itens, BigDecimal desconto) {
        return new PedidoRequest(clienteId, vendedorId, null, null, desconto, itens);
    }

    @Test
    void criaERecuperaPedidoComNumeroENoStatusInicial() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, new BigDecimal("2"), new BigDecimal("59.90")));

        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        assertThat(criado.numero()).isEqualTo(1);
        assertThat(criado.status()).isEqualTo(StatusPedido.DIGITADO);
        assertThat(criado.itens()).hasSize(1);

        var buscado = pedidoService.buscarPorId(criado.id());
        assertThat(buscado.clienteNome()).isEqualTo("Mercado Silva");
        assertThat(buscado.vendedorNome()).isEqualTo("Marina");
    }

    @Test
    void numeroIncrementaSequencialmentePorTenant() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));

        var primeiro = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));
        var segundo = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        assertThat(primeiro.numero()).isEqualTo(1);
        assertThat(segundo.numero()).isEqualTo(2);
    }

    @Test
    void numeracaoReiniciaEmTenantDiferente() {
        UUID tenantA = setUpTenant("aurora");
        UUID clienteA = criarCliente(tenantA, "11222333000144");
        UUID vendedorA = criarVendedor(tenantA, "marina@aurora.com.br");
        UUID produtoA = criarProduto(tenantA, "P0001", new BigDecimal("59.90"));
        pedidoService.criar(tenantA, request(clienteA, vendedorA,
                List.of(new ItemPedidoDto(produtoA, BigDecimal.ONE, new BigDecimal("59.90"))), BigDecimal.ZERO));

        UUID tenantB = setUpTenant("boreal");
        UUID clienteB = criarCliente(tenantB, "11222333000144");
        UUID vendedorB = criarVendedor(tenantB, "carla@boreal.com.br");
        UUID produtoB = criarProduto(tenantB, "P0001", new BigDecimal("39.90"));
        var criadoB = pedidoService.criar(tenantB, request(clienteB, vendedorB,
                List.of(new ItemPedidoDto(produtoB, BigDecimal.ONE, new BigDecimal("39.90"))), BigDecimal.ZERO));

        assertThat(criadoB.numero()).isEqualTo(1);
    }

    @Test
    void rejeitaClienteSemPapelCliente() {
        UUID tenantId = setUpTenant("aurora");
        UUID fornecedorId = criarFornecedor(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));

        assertThrows(PedidoValidacaoException.class,
                () -> pedidoService.criar(tenantId, request(fornecedorId, vendedorId, itens, BigDecimal.ZERO)));
    }

    @Test
    void rejeitaVendedorSemPapelRepresentante() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID administrativoId = criarAdministrativo(tenantId, "carlos@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));

        assertThrows(PedidoValidacaoException.class,
                () -> pedidoService.criar(tenantId, request(clienteId, administrativoId, itens, BigDecimal.ZERO)));
    }

    @Test
    void calculaSubtotalDescontoETotal() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(
                new ItemPedidoDto(produtoId, new BigDecimal("2"), new BigDecimal("59.90")),
                new ItemPedidoDto(produtoId, new BigDecimal("1"), new BigDecimal("20.00")));

        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, new BigDecimal("10.00")));

        assertThat(criado.subtotal()).isEqualByComparingTo("139.80");
        assertThat(criado.total()).isEqualByComparingTo("129.80");
    }

    @Test
    void valorUnitarioDoItemNaoMudaQuandoPrecoDoProdutoMudaDepois() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        Produto produto = produtoRepository.findById(produtoId).orElseThrow();
        produto.setPrecoVenda(new BigDecimal("99.90"));
        produtoRepository.saveAndFlush(produto);

        var buscado = pedidoService.buscarPorId(criado.id());
        assertThat(buscado.itens().get(0).valorUnitario()).isEqualByComparingTo("59.90");
    }

    @Test
    void avancaDeDigitadoParaEmPreparo() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        var avancado = pedidoService.avancarStatus(criado.id(), StatusPedido.EM_PREPARO);

        assertThat(avancado.status()).isEqualTo(StatusPedido.EM_PREPARO);
    }

    @Test
    void rejeitaPularEtapaDeStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        assertThrows(PedidoValidacaoException.class,
                () -> pedidoService.avancarStatus(criado.id(), StatusPedido.FATURADO));
    }

    @Test
    void rejeitaRetrocederStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));
        pedidoService.avancarStatus(criado.id(), StatusPedido.EM_PREPARO);

        assertThrows(PedidoValidacaoException.class,
                () -> pedidoService.avancarStatus(criado.id(), StatusPedido.DIGITADO));
    }

    @Test
    void resumoContaPorStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var a = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));
        pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));
        pedidoService.avancarStatus(a.id(), StatusPedido.EM_PREPARO);

        var resumo = pedidoService.resumo();

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.digitados()).isEqualTo(1);
        assertThat(resumo.emPreparo()).isEqualTo(1);
        assertThat(resumo.faturados()).isEqualTo(0);
    }

    @Test
    void listaComFiltroDeBuscaPorNumero() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        var pagina = pedidoService.listar(String.valueOf(criado.numero()), null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listaComFiltroDeBuscaPorCliente() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        var pagina = pedidoService.listar("mercado silva", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    @Test
    void excluiPedido() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));

        pedidoService.excluir(criado.id());

        assertThrows(PedidoNaoEncontradoException.class, () -> pedidoService.buscarPorId(criado.id()));
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=PedidoServiceTest`
Expected: PASS (14 tests, 0 failures).

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/pedido/
git add mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoServiceTest.java
git commit -m "feat(pedido): add PedidoService with papel validation, numbering, snapshots and status progression"
```

---

### Task 3: Backend — PedidoController, exception handling, integration tests

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoExceptionHandler.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoControllerTest.java`

**Interfaces:**
- Consumes (from Task 2): `PedidoService` (all methods listed there), `PedidoRequest`, `PedidoResponse`, `PedidoSummaryResponse`, `PedidoResumoResponse`, `PedidoStatusRequest`, `PedidoNaoEncontradoException`, `PedidoValidacaoException`.
- Produces: `PedidoController` mapped at `/api/pedidos` — `GET /`, `GET /resumo`, `GET /{id}`, `POST /`, `PUT /{id}`, `PATCH /{id}/status`, `DELETE /{id}` — same shape as `ParceiroController`/`ProdutoController`.

- [ ] **Step 1: Write `PedidoController.java`**

```java
package com.meshsuite.pedido;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.pedido.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public Page<PedidoSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusPedido status,
            @PageableDefault(size = 10, sort = "numero", direction = Sort.Direction.DESC) Pageable pageable) {
        return pedidoService.listar(busca, status, pageable);
    }

    @GetMapping("/resumo")
    public PedidoResumoResponse resumo() {
        return pedidoService.resumo();
    }

    @GetMapping("/{id}")
    public PedidoResponse buscarPorId(@PathVariable UUID id) {
        return pedidoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                 @Valid @RequestBody PedidoRequest request) {
        PedidoResponse response = pedidoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PedidoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody PedidoRequest request) {
        return pedidoService.atualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    public PedidoResponse atualizarStatus(@PathVariable UUID id, @Valid @RequestBody PedidoStatusRequest request) {
        return pedidoService.avancarStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        pedidoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Write `PedidoExceptionHandler.java`**

```java
package com.meshsuite.pedido;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = PedidoController.class)
public class PedidoExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Não foi possível salvar o pedido. Tente novamente."));
    }
}
```

- [ ] **Step 3: Register `PedidoNaoEncontradoException`/`PedidoValidacaoException` in `GlobalExceptionHandler`**

Add these two handlers to the existing `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`, right after the `SkuDuplicadoException` handler (these are domain-unique exception types, so — unlike `DataIntegrityViolationException` — registering them in the shared handler is safe, following the existing `ParceiroNaoEncontradoException`/`ProdutoNaoEncontradoException` precedent):

```java
    @ExceptionHandler(com.meshsuite.pedido.PedidoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handlePedidoNaoEncontrado(
            com.meshsuite.pedido.PedidoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pedido.PedidoValidacaoException.class)
    public ResponseEntity<Map<String, String>> handlePedidoValidacao(
            com.meshsuite.pedido.PedidoValidacaoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 4: Write the failing controller test**

```java
package com.meshsuite.pedido;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class PedidoControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, String clienteId, String vendedorId, String produtoId) {
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

        Usuario usuarioLogin = new Usuario();
        usuarioLogin.setTenantId(tenant.getId());
        usuarioLogin.setNome("Marina");
        usuarioLogin.setEmail(email);
        usuarioLogin.setSenhaHash(passwordEncoder.encode("senha123"));
        usuarioLogin.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuarioLogin);

        Usuario vendedor = new Usuario();
        vendedor.setTenantId(tenant.getId());
        vendedor.setNome("Carla Vendedora");
        vendedor.setEmail("carla-" + codigo + "@" + codigo + ".com.br");
        vendedor.setSenhaHash("hash");
        vendedor.setPapel(Papel.REPRESENTANTE);
        usuarioRepository.saveAndFlush(vendedor);

        Parceiro cliente = new Parceiro();
        cliente.setTenantId(tenant.getId());
        cliente.setTipoPessoa(TipoPessoa.JURIDICA);
        cliente.setDocumento(cnpjEmpresa.equals("11222333000144") ? "55666777000155" : "11222333000144");
        cliente.setNomeFantasia("Mercado Silva");
        cliente.getPapeis().add(PapelParceiro.CLIENTE);
        parceiroRepository.saveAndFlush(cliente);

        Produto produto = new Produto();
        produto.setTenantId(tenant.getId());
        produto.setNome("Camiseta Polo");
        produto.setSku("P0001-" + codigo);
        produto.setPrecoVenda(new BigDecimal("59.90"));
        produtoRepository.saveAndFlush(produto);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Contexto(token, cliente.getId().toString(), vendedor.getId().toString(), produto.getId().toString());
    }

    private String pedidoPayload(Contexto ctx) {
        return """
                {
                  "clienteId": "%s",
                  "vendedorId": "%s",
                  "desconto": 0,
                  "itens": [
                    { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 }
                  ]
                }
                """.formatted(ctx.clienteId(), ctx.vendedorId(), ctx.produtoId());
    }

    @Test
    void createsListsUpdatesAdvancesAndDeletesPedido() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoPayload(ctx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.status").value("DIGITADO"))
                .andExpect(jsonPath("$.total").value(119.90))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/pedidos").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numero").value(1));

        mockMvc.perform(put("/api/pedidos/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 10.00,
                                  "itens": [
                                    { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 }
                                  ]
                                }
                                """.formatted(ctx.clienteId(), ctx.vendedorId(), ctx.produtoId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(109.90));

        mockMvc.perform(patch("/api/pedidos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PREPARO"));

        mockMvc.perform(patch("/api/pedidos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DIGITADO\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/pedidos/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/pedidos/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyItensWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": []
                                }
                                """.formatted(ctx.clienteId(), ctx.vendedorId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsClienteWithoutClientePapelWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": [ { "produtoId": "%s", "quantidade": 1, "valorUnitario": 10.00 } ]
                                }
                                """.formatted(ctx.vendedorId(), ctx.vendedorId(), ctx.produtoId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsPedido() throws Exception {
        Contexto ctxA = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/pedidos").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoPayload(ctxA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        Contexto ctxB = loginAndSetUp("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/pedidos/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=PedidoControllerTest`
Expected: PASS (5 tests, 0 failures).

- [ ] **Step 6: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: PASS, all existing suites unaffected.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoExceptionHandler.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoControllerTest.java
git commit -m "feat(pedido): add PedidoController with CRUD, status-advance and RLS-safe integration tests"
```

---

### Task 4: Backend — Cliente/Vendedor picker extensions (Parceiro papel filter, Usuario representantes)

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroSpecifications.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroController.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroServiceTest.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/UsuarioRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/dto/UsuarioSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/UsuarioController.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/usuario/UsuarioControllerTest.java`

**Interfaces:**
- Produces: `ParceiroSpecifications.comPapel(PapelParceiro papel): Specification<Parceiro>`; `ParceiroService.listar(String busca, StatusParceiro status, TipoPessoa tipoDocumento, String uf, String cidade, PapelParceiro papel, Pageable pageable)` (signature gains a `papel` parameter as the second-to-last argument — every existing call site must be updated); `GET /api/parceiros?papel=CLIENTE`; `UsuarioRepository.findByPapelOrderByNome(Papel papel): List<Usuario>`; `UsuarioSummaryResponse(UUID id, String nome)`; `GET /api/usuarios/representantes` returning `List<UsuarioSummaryResponse>`.

- [ ] **Step 1: Add `comPapel` to `ParceiroSpecifications.java`**

Add this method to the existing file, after `comCidade`:

```java
    public static Specification<Parceiro> comPapel(PapelParceiro papel) {
        if (papel == null) {
            return null;
        }
        return (root, query, cb) -> cb.isMember(papel, root.get("papeis"));
    }
```

- [ ] **Step 2: Update `ParceiroService.listar` to accept and apply the `papel` filter**

In `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroService.java`, change:

```java
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
```

to:

```java
    @Transactional(readOnly = true)
    public Page<ParceiroSummaryResponse> listar(String busca, StatusParceiro status, TipoPessoa tipoDocumento,
                                                 String uf, String cidade, PapelParceiro papel, Pageable pageable) {
        Specification<Parceiro> spec = Specification.allOf(
                ParceiroSpecifications.comBusca(busca),
                ParceiroSpecifications.comStatus(status),
                ParceiroSpecifications.comTipoPessoa(tipoDocumento),
                ParceiroSpecifications.comUf(uf),
                ParceiroSpecifications.comCidade(cidade),
                ParceiroSpecifications.comPapel(papel));
        return parceiroRepository.findAll(spec, pageable).map(this::toSummary);
    }
```

- [ ] **Step 3: Update `ParceiroController.listar` to accept and pass through `papel`**

In `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroController.java`, change:

```java
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
```

to:

```java
    @GetMapping
    public Page<ParceiroSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusParceiro status,
            @RequestParam(required = false) TipoPessoa tipoDocumento,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) PapelParceiro papel,
            @PageableDefault(size = 10, sort = "nomeFantasia") Pageable pageable) {
        return parceiroService.listar(busca, status, tipoDocumento, uf, cidade, papel, pageable);
    }
```

- [ ] **Step 4: Update the existing `ParceiroServiceTest` call site and add a filter test**

In `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroServiceTest.java`, change the existing call:

```java
        var pagina = parceiroService.listar("silva", null, null, null, null, PageRequest.of(0, 10));
```

to:

```java
        var pagina = parceiroService.listar("silva", null, null, null, null, null, PageRequest.of(0, 10));
```

Then add this new test, after `listaComFiltroDeBusca`:

```java
    @Test
    void listaComFiltroDePapel() {
        setUpTenant("aurora");
        parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));
        parceiroService.criar(TenantContext.get(), request("55666777000155", Set.of(PapelParceiro.FORNECEDOR)));

        var pagina = parceiroService.listar(null, null, null, null, null, PapelParceiro.CLIENTE, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }
```

- [ ] **Step 5: Run the updated Parceiro tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=ParceiroServiceTest,ParceiroControllerTest`
Expected: PASS, all tests including the new `listaComFiltroDePapel`.

- [ ] **Step 6: Add `findByPapelOrderByNome` to `UsuarioRepository.java`**

Change the file to:

```java
package com.meshsuite.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmail(String email);
    List<Usuario> findByPapelOrderByNome(Papel papel);
}
```

- [ ] **Step 7: Write `UsuarioSummaryResponse.java`**

```java
package com.meshsuite.usuario.dto;

import java.util.UUID;

public record UsuarioSummaryResponse(UUID id, String nome) {
}
```

- [ ] **Step 8: Write `UsuarioController.java`**

```java
package com.meshsuite.usuario;

import com.meshsuite.usuario.dto.UsuarioSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    public UsuarioController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/representantes")
    public List<UsuarioSummaryResponse> representantes() {
        return usuarioRepository.findByPapelOrderByNome(Papel.REPRESENTANTE).stream()
                .map(u -> new UsuarioSummaryResponse(u.getId(), u.getNome()))
                .toList();
    }
}
```

- [ ] **Step 9: Write the failing controller test**

```java
package com.meshsuite.usuario;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UsuarioControllerTest extends AbstractIntegrationTest {

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

        Usuario admin = new Usuario();
        admin.setTenantId(tenant.getId());
        admin.setNome("Marina");
        admin.setEmail(email);
        admin.setSenhaHash(passwordEncoder.encode("senha123"));
        admin.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(admin);

        Usuario representante = new Usuario();
        representante.setTenantId(tenant.getId());
        representante.setNome("Carla Vendedora");
        representante.setEmail("carla-" + codigo + "@" + codigo + ".com.br");
        representante.setSenhaHash("hash");
        representante.setPapel(Papel.REPRESENTANTE);
        usuarioRepository.saveAndFlush(representante);

        Usuario producao = new Usuario();
        producao.setTenantId(tenant.getId());
        producao.setNome("Pedro Produção");
        producao.setEmail("pedro-" + codigo + "@" + codigo + ".com.br");
        producao.setSenhaHash("hash");
        producao.setPapel(Papel.PRODUCAO);
        usuarioRepository.saveAndFlush(producao);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    @Test
    void listsOnlyUsersWithRepresentantePapel() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/usuarios/representantes").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Carla Vendedora"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/usuarios/representantes"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 10: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=UsuarioControllerTest,ParceiroServiceTest,ParceiroControllerTest`
Expected: PASS.

- [ ] **Step 11: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: PASS, all suites (including Produto/Parceiro/Auth/Pedido) unaffected.

- [ ] **Step 12: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroSpecifications.java \
        mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroController.java \
        mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroServiceTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/usuario/
git add mesh-suite-backend/src/test/java/com/meshsuite/usuario/UsuarioControllerTest.java
git commit -m "feat(pedido): add parceiro papel filter and read-only usuario representantes endpoint"
```

---

### Task 5: Frontend — API layer + PedidoFormView.vue

**Files:**
- Create: `mesh-suite-frontend/src/api/pedidos.ts`
- Create: `mesh-suite-frontend/src/api/usuarios.ts`
- Modify: `mesh-suite-frontend/src/api/parceiros.ts`
- Create: `mesh-suite-frontend/src/views/PedidoFormView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts`

**Interfaces:**
- Consumes (from Task 3/4 backend): `GET/POST/PUT /api/pedidos`, `PATCH /api/pedidos/{id}/status`, `GET /api/pedidos/{id}`; `GET /api/parceiros?papel=CLIENTE`; `GET /api/usuarios/representantes`.
- Produces: `PedidoRequest`, `PedidoResponse`, `PedidoSummary`, `ItemPedidoRequest`, `ItemPedidoResponse`, `StatusPedido`, `PedidoResumo` types and `listarPedidos`, `buscarPedido`, `criarPedido`, `atualizarPedido`, `avancarStatusPedido`, `excluirPedido`, `buscarResumoPedidos` functions in `api/pedidos.ts` — consumed by Task 6's `PedidosListView.vue`. `UsuarioRepresentante` type and `listarRepresentantes()` function in `api/usuarios.ts`. Router gains routes named `pedidos-novo` and `pedidos-editar` pointing at `PedidoFormView`.

**Deliberate deviation from the spec's wording:** §5 of the spec describes the Vendedor field as "busca entre usuários com papel REPRESENTANTE" — the same typeahead style as Cliente. This task instead renders Vendedor as a native `<select>` populated once from `listarRepresentantes()`. Representantes are a small, bounded list (unlike the potentially large, paginated Cliente list), so a typeahead adds interaction cost with no benefit — same reasoning already applied to UF and Unidade de Medida elsewhere in this codebase. Cliente keeps the typeahead since its list is not bounded the same way.

- [ ] **Step 1: Write `api/pedidos.ts`**

```typescript
import { apiClient } from './client'

export type StatusPedido = 'DIGITADO' | 'EM_PREPARO' | 'FATURADO'

export interface ItemPedidoRequest {
  produtoId: string
  quantidade: number
  valorUnitario: number
}

export interface ItemPedidoResponse extends ItemPedidoRequest {
  produtoNome: string
  valorTotal: number
}

export interface PedidoRequest {
  clienteId: string
  vendedorId: string
  dataPedido: string
  dataEntrega: string | null
  desconto: number
  itens: ItemPedidoRequest[]
}

export interface PedidoResponse {
  id: string
  numero: number
  clienteId: string
  clienteNome: string
  vendedorId: string
  vendedorNome: string
  dataPedido: string
  dataEntrega: string | null
  status: StatusPedido
  desconto: number
  subtotal: number
  total: number
  itens: ItemPedidoResponse[]
}

export interface PedidoSummary {
  id: string
  numero: number
  clienteNome: string
  vendedorNome: string
  dataPedido: string
  total: number
  status: StatusPedido
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarPedidosParams {
  busca?: string
  status?: StatusPedido
  page?: number
  size?: number
}

export interface PedidoResumo {
  total: number
  digitados: number
  emPreparo: number
  faturados: number
}

export async function listarPedidos(params: ListarPedidosParams): Promise<Page<PedidoSummary>> {
  const { data } = await apiClient.get<Page<PedidoSummary>>('/pedidos', { params })
  return data
}

export async function buscarPedido(id: string): Promise<PedidoResponse> {
  const { data } = await apiClient.get<PedidoResponse>(`/pedidos/${id}`)
  return data
}

export async function criarPedido(payload: PedidoRequest): Promise<PedidoResponse> {
  const { data } = await apiClient.post<PedidoResponse>('/pedidos', payload)
  return data
}

export async function atualizarPedido(id: string, payload: PedidoRequest): Promise<PedidoResponse> {
  const { data } = await apiClient.put<PedidoResponse>(`/pedidos/${id}`, payload)
  return data
}

export async function avancarStatusPedido(id: string, status: StatusPedido): Promise<void> {
  await apiClient.patch(`/pedidos/${id}/status`, { status })
}

export async function excluirPedido(id: string): Promise<void> {
  await apiClient.delete(`/pedidos/${id}`)
}

export async function buscarResumoPedidos(): Promise<PedidoResumo> {
  const { data } = await apiClient.get<PedidoResumo>('/pedidos/resumo')
  return data
}
```

- [ ] **Step 2: Write `api/usuarios.ts`**

```typescript
import { apiClient } from './client'

export interface UsuarioRepresentante {
  id: string
  nome: string
}

export async function listarRepresentantes(): Promise<UsuarioRepresentante[]> {
  const { data } = await apiClient.get<UsuarioRepresentante[]>('/usuarios/representantes')
  return data
}
```

- [ ] **Step 3: Add the `papel` filter to `api/parceiros.ts`**

Add `papel?: PapelParceiro` to `ListarParceirosParams`:

```typescript
export interface ListarParceirosParams {
  busca?: string
  status?: StatusParceiro
  tipoDocumento?: TipoPessoa
  uf?: string
  cidade?: string
  papel?: PapelParceiro
  page?: number
  size?: number
}
```

(No other change needed — `listarParceiros` already forwards the whole `params` object to `apiClient.get`.)

- [ ] **Step 4: Write `PedidoFormView.vue`**

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Pedido' : 'Novo Pedido'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados do Pedido</h2>
        <div class="grid grid-2">
          <div class="busca-wrapper">
            <label class="field-label">Cliente *</label>
            <input
              v-model="clienteBusca"
              data-test="cliente-busca"
              placeholder="Buscar cliente..."
              autocomplete="off"
              @input="buscarClientes"
            />
            <p v-if="erros.clienteId" class="field-error">{{ erros.clienteId }}</p>
            <ul v-if="resultadosClientes.length" class="dropdown-busca" data-test="cliente-resultados">
              <li v-for="c in resultadosClientes" :key="c.id" @click="selecionarCliente(c)">{{ c.nomeFantasia }}</li>
            </ul>
          </div>
          <div>
            <label class="field-label">Vendedor *</label>
            <select v-model="form.vendedorId" data-test="vendedor">
              <option value="">Selecione...</option>
              <option v-for="r in representantes" :key="r.id" :value="r.id">{{ r.nome }}</option>
            </select>
            <p v-if="erros.vendedorId" class="field-error">{{ erros.vendedorId }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Data do Pedido</label>
            <input v-model="form.dataPedido" type="date" data-test="data-pedido" />
          </div>
          <div>
            <label class="field-label">Previsão de Entrega</label>
            <input v-model="form.dataEntrega" type="date" data-test="data-entrega" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Itens</h2>
        <div class="item-adicionar">
          <div class="busca-wrapper item-produto-busca">
            <input
              v-model="produtoBusca"
              placeholder="Buscar produto por nome ou SKU..."
              data-test="produto-busca"
              autocomplete="off"
              @input="buscarProdutos"
            />
            <ul v-if="resultadosProdutos.length" class="dropdown-busca" data-test="produto-resultados">
              <li v-for="p in resultadosProdutos" :key="p.id" @click="selecionarProduto(p)">{{ p.nome }} ({{ p.sku }})</li>
            </ul>
          </div>
          <input
            v-model.number="itemForm.quantidade"
            type="number"
            step="0.001"
            min="0.001"
            placeholder="Qtd."
            data-test="item-quantidade"
          />
          <input
            v-model.number="itemForm.valorUnitario"
            type="number"
            step="0.01"
            min="0"
            placeholder="Valor unit."
            data-test="item-valor-unitario"
          />
          <button type="button" class="btn-secondary" data-test="item-adicionar" @click="adicionarItem">+ Adicionar</button>
        </div>
        <p v-if="erros.itens" class="field-error">{{ erros.itens }}</p>

        <table v-if="form.itens.length" class="tabela-itens">
          <thead>
            <tr>
              <th>Produto</th>
              <th>Qtd.</th>
              <th>Valor Unit.</th>
              <th>Total</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in form.itens" :key="index">
              <td>{{ item.produtoNome }}</td>
              <td>{{ item.quantidade }}</td>
              <td>{{ formatarPreco(item.valorUnitario) }}</td>
              <td>{{ formatarPreco(item.quantidade * item.valorUnitario) }}</td>
              <td><button type="button" class="btn-remover" data-test="item-remover" @click="removerItem(index)">✕</button></td>
            </tr>
          </tbody>
        </table>

        <div class="totais">
          <div><span>Subtotal</span><span>{{ formatarPreco(subtotal) }}</span></div>
          <div>
            <span>Desconto</span>
            <input v-model.number="form.desconto" type="number" step="0.01" min="0" data-test="desconto" />
          </div>
          <div class="total-final"><span>Total</span><span>{{ formatarPreco(total) }}</span></div>
        </div>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Pedido</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { buscarPedido, criarPedido, atualizarPedido, type PedidoRequest, type ItemPedidoRequest } from '@/api/pedidos'
import { listarParceiros, type ParceiroSummary } from '@/api/parceiros'
import { listarRepresentantes, type UsuarioRepresentante } from '@/api/usuarios'
import { listarProdutos, type ProdutoSummary } from '@/api/produtos'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

interface ItemForm extends ItemPedidoRequest {
  produtoNome: string
}

interface FormState {
  clienteId: string
  vendedorId: string
  dataPedido: string
  dataEntrega: string
  desconto: number
  itens: ItemForm[]
}

function novoFormulario(): FormState {
  return {
    clienteId: '',
    vendedorId: '',
    dataPedido: new Date().toISOString().slice(0, 10),
    dataEntrega: '',
    desconto: 0,
    itens: [],
  }
}

const form = reactive<FormState>(novoFormulario())
const erros = reactive<{ clienteId?: string; vendedorId?: string; itens?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

const clienteBusca = ref('')
const resultadosClientes = ref<ParceiroSummary[]>([])
const representantes = ref<UsuarioRepresentante[]>([])

const produtoBusca = ref('')
const resultadosProdutos = ref<ProdutoSummary[]>([])
const itemForm = reactive({ produtoId: '', produtoNome: '', quantidade: 1, valorUnitario: 0 })

const subtotal = computed(() => form.itens.reduce((soma, item) => soma + item.quantidade * item.valorUnitario, 0))
const total = computed(() => subtotal.value - (Number(form.desconto) || 0))

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

async function buscarClientes() {
  if (!clienteBusca.value.trim()) {
    resultadosClientes.value = []
    return
  }
  try {
    const pagina = await listarParceiros({ busca: clienteBusca.value, papel: 'CLIENTE', size: 5 })
    resultadosClientes.value = pagina.content
  } catch {
    resultadosClientes.value = []
  }
}

function selecionarCliente(cliente: ParceiroSummary) {
  form.clienteId = cliente.id
  clienteBusca.value = cliente.nomeFantasia
  resultadosClientes.value = []
}

async function buscarProdutos() {
  if (!produtoBusca.value.trim()) {
    resultadosProdutos.value = []
    return
  }
  try {
    const pagina = await listarProdutos({ busca: produtoBusca.value, size: 5 })
    resultadosProdutos.value = pagina.content
  } catch {
    resultadosProdutos.value = []
  }
}

function selecionarProduto(produto: ProdutoSummary) {
  itemForm.produtoId = produto.id
  itemForm.produtoNome = produto.nome
  itemForm.valorUnitario = produto.precoVenda
  produtoBusca.value = produto.nome
  resultadosProdutos.value = []
}

function adicionarItem() {
  const quantidade = Number(itemForm.quantidade) || 0
  if (!itemForm.produtoId || quantidade <= 0) {
    return
  }
  form.itens.push({
    produtoId: itemForm.produtoId,
    produtoNome: itemForm.produtoNome,
    quantidade,
    // Normalized here for the same reason paraPayload() normalizes on submit:
    // v-model.number on a blank input yields '' (not 0), and that would flow
    // straight into form.itens and later into the request payload untouched.
    valorUnitario: Number(itemForm.valorUnitario) || 0,
  })
  itemForm.produtoId = ''
  itemForm.produtoNome = ''
  itemForm.quantidade = 1
  itemForm.valorUnitario = 0
  produtoBusca.value = ''
}

function removerItem(index: number) {
  form.itens.splice(index, 1)
}

onMounted(async () => {
  try {
    representantes.value = await listarRepresentantes()
  } catch {
    erroGeral.value = 'Não foi possível carregar a lista de vendedores.'
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const pedido = await buscarPedido(id)
      form.clienteId = pedido.clienteId
      clienteBusca.value = pedido.clienteNome
      form.vendedorId = pedido.vendedorId
      form.dataPedido = pedido.dataPedido
      form.dataEntrega = pedido.dataEntrega ?? ''
      form.desconto = pedido.desconto
      form.itens = pedido.itens.map((item) => ({
        produtoId: item.produtoId,
        produtoNome: item.produtoNome,
        quantidade: item.quantidade,
        valorUnitario: item.valorUnitario,
      }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do pedido.'
    }
  }
})

function validar(): boolean {
  erros.clienteId = form.clienteId ? undefined : 'Selecione um cliente'
  erros.vendedorId = form.vendedorId ? undefined : 'Selecione um vendedor'
  erros.itens = form.itens.length > 0 ? undefined : 'Adicione ao menos um item'
  return !erros.clienteId && !erros.vendedorId && !erros.itens
}

function paraPayload(): PedidoRequest {
  return {
    clienteId: form.clienteId,
    vendedorId: form.vendedorId,
    dataPedido: form.dataPedido,
    dataEntrega: form.dataEntrega || null,
    desconto: Number(form.desconto) || 0,
    itens: form.itens.map(({ produtoId, quantidade, valorUnitario }) => ({ produtoId, quantidade, valorUnitario })),
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
      await atualizarPedido(id, payload)
    } else {
      await criarPedido(payload)
    }
    router.push({ name: 'pedidos' })
  } catch (err: any) {
    if (err?.response?.status === 400) {
      erroGeral.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      erroGeral.value = 'Não foi possível salvar. Tente novamente em instantes.'
    }
  } finally {
    salvando.value = false
  }
}

function cancelar() {
  router.push({ name: 'pedidos' })
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

.busca-wrapper {
  position: relative;
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

.item-adicionar {
  display: grid;
  grid-template-columns: 1fr 100px 120px auto;
  gap: 8px;
  align-items: start;
  margin-bottom: 10px;
}

.item-produto-busca {
  min-width: 0;
}

.tabela-itens {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-bottom: 12px;
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

.totais {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 260px;
  margin-left: auto;
  font-size: 13px;
  color: var(--pm-text-dark);
}

.totais > div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.totais input {
  width: 100px;
  text-align: right;
}

.total-final {
  font-weight: 700;
  font-size: 14px;
  border-top: 1px solid var(--pm-border-light);
  padding-top: 6px;
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

- [ ] **Step 5: Add the `pedidos-novo`/`pedidos-editar` routes**

In `mesh-suite-frontend/src/router/index.ts`, add the import:

```typescript
import PedidoFormView from '@/views/PedidoFormView.vue'
```

and add these two routes after the `produtos-editar` route:

```typescript
    { path: '/pedidos/novo', name: 'pedidos-novo', component: PedidoFormView },
    { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: PedidoFormView },
```

(The `pedidos` list route is added in Task 6 — until then, `router.push({ name: 'pedidos' })` inside this view resolves to an as-yet-undefined route name, which is fine at this stage: Task 6 defines it before either task's tests need it to resolve for real navigation. This view's own tests below stub that route directly.)

- [ ] **Step 6: Write the failing view test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PedidoFormView from '@/views/PedidoFormView.vue'
import * as pedidosApi from '@/api/pedidos'
import * as parceirosApi from '@/api/parceiros'
import * as usuariosApi from '@/api/usuarios'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/pedidos')
vi.mock('@/api/parceiros')
vi.mock('@/api/usuarios')
vi.mock('@/api/produtos')

function mountWithRouter(path = '/pedidos/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/pedidos', name: 'pedidos', component: { template: '<div />' } },
      { path: '/pedidos/novo', name: 'pedidos-novo', component: PedidoFormView },
      { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: PedidoFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PedidoFormView, { global: { plugins: [router] } }),
  }))
}

const clienteBase = {
  id: 'c1', nomeFantasia: 'Mercado Silva', razaoSocial: 'Mercado Silva Ltda',
  documento: '11222333000144', cidade: 'São Paulo', uf: 'SP', whatsapp: '', status: 'ATIVO' as const,
}

const representanteBase = { id: 'v1', nome: 'Carla Vendedora' }

const produtoBase = {
  id: 'p1', nome: 'Camiseta Polo', sku: 'P0001', marca: 'Marca Alpha',
  precoVenda: 59.9, quantidadeEstoque: 10, status: 'ATIVO' as const,
}

describe('PedidoFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usuariosApi.listarRepresentantes).mockResolvedValue([representanteBase])
    vi.mocked(parceirosApi.listarParceiros).mockResolvedValue({
      content: [clienteBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
  })

  it('shows required-field errors when cliente/vendedor/itens are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione um cliente')
    expect(wrapper.text()).toContain('Selecione um vendedor')
    expect(wrapper.text()).toContain('Adicione ao menos um item')
    expect(pedidosApi.criarPedido).not.toHaveBeenCalled()
  })

  it('loads the representantes list for the vendedor select', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(usuariosApi.listarRepresentantes).toHaveBeenCalled()
    expect(wrapper.find('[data-test="vendedor"]').text()).toContain('Carla Vendedora')
  })

  it('searches and selects a cliente via the busca dropdown', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cliente-busca"]').setValue('silva')
    await flushPromises()

    expect(parceirosApi.listarParceiros).toHaveBeenCalledWith(
      expect.objectContaining({ busca: 'silva', papel: 'CLIENTE' }),
    )
    await wrapper.find('[data-test="cliente-resultados"] li').trigger('click')

    expect((wrapper.find('[data-test="cliente-busca"]').element as HTMLInputElement).value).toBe('Mercado Silva')
  })

  it('searches for a produto, adds it as an item and computes totals live', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="produto-busca"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('2')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).toContain('R$ 119,80')

    await wrapper.find('[data-test="item-remover"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Camiseta Polo')
  })

  it('sends a numeric (not empty-string) valorUnitario for a manually cleared-then-refilled item field', async () => {
    vi.mocked(pedidosApi.criarPedido).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cliente-busca"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="cliente-resultados"] li').trigger('click')
    await wrapper.find('[data-test="vendedor"]').setValue('v1')

    await wrapper.find('[data-test="produto-busca"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    // Simulate the auto-filled valor unitário being manually cleared and refilled --
    // v-model.number drives the underlying value to '' (empty string) when cleared,
    // the exact state that must be normalized before it lands in form.itens/payload.
    await wrapper.find('[data-test="item-valor-unitario"]').setValue('')
    await wrapper.find('[data-test="item-valor-unitario"]').setValue('75.00')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(pedidosApi.criarPedido).mock.calls[0][0]
    expect(payload.itens[0].valorUnitario).toBe(75)
    expect(typeof payload.itens[0].valorUnitario).toBe('number')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(pedidosApi.criarPedido).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cliente-busca"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="cliente-resultados"] li').trigger('click')
    await wrapper.find('[data-test="vendedor"]').setValue('v1')

    await wrapper.find('[data-test="produto-busca"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(pedidosApi.criarPedido).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('pedidos')
  })

  it('loads existing pedido data in edit mode', async () => {
    vi.mocked(pedidosApi.buscarPedido).mockResolvedValue({
      id: 'ped-1', numero: 3, clienteId: 'c1', clienteNome: 'Mercado Silva', vendedorId: 'v1',
      vendedorNome: 'Carla Vendedora', dataPedido: '2026-07-31', dataEntrega: null, status: 'DIGITADO',
      desconto: 0, subtotal: 119.8, total: 119.8,
      itens: [{ produtoId: 'p1', produtoNome: 'Camiseta Polo', quantidade: 2, valorUnitario: 59.9, valorTotal: 119.8 }],
    } as any)

    const { wrapper } = await mountWithRouter('/pedidos/ped-1/editar')
    await flushPromises()

    expect(pedidosApi.buscarPedido).toHaveBeenCalledWith('ped-1')
    expect((wrapper.find('[data-test="cliente-busca"]').element as HTMLInputElement).value).toBe('Mercado Silva')
    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('shows an error message when loading pedido data fails in edit mode', async () => {
    vi.mocked(pedidosApi.buscarPedido).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/pedidos/ped-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do pedido.')
  })
})
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PedidoFormView.spec.ts`
Expected: PASS (8 tests, 0 failures).

- [ ] **Step 8: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: PASS, all existing suites unaffected.

- [ ] **Step 9: Commit**

```bash
git add mesh-suite-frontend/src/api/pedidos.ts \
        mesh-suite-frontend/src/api/usuarios.ts \
        mesh-suite-frontend/src/api/parceiros.ts \
        mesh-suite-frontend/src/views/PedidoFormView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts
git commit -m "feat(pedido): add pedidos/usuarios API layer and PedidoFormView with cliente/vendedor/produto pickers"
```

---

### Task 6: Frontend — PedidosListView.vue, routing and sidebar activation

**Files:**
- Create: `mesh-suite-frontend/src/views/PedidosListView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/PedidosListView.spec.ts`

**Interfaces:**
- Consumes (from Task 5): `listarPedidos`, `buscarResumoPedidos`, `avancarStatusPedido`, `excluirPedido`, `PedidoSummary`, `PedidoResumo`, `StatusPedido` from `@/api/pedidos`. Also consumes the `pedidos-novo`/`pedidos-editar` route names already added in Task 5.
- Produces: the `pedidos` route (list), and flips the sidebar's "Pedidos" nav item from inert to active.

- [ ] **Step 1: Write `PedidosListView.vue`**

```vue
<template>
  <AppShell title="Pedidos">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº, cliente ou vendedor..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="DIGITADO">Digitado</option>
        <option value="EM_PREPARO">Em Preparo</option>
        <option value="FATURADO">Faturado</option>
      </select>
      <button type="button" class="btn-primary" data-test="novo-pedido" @click="novoPedido">+ Novo Pedido</button>
    </div>

    <div v-if="resumo" class="resumo">
      <span class="resumo-item">{{ resumo.total }} Total</span>
      <span class="resumo-item resumo-digitado">{{ resumo.digitados }} Digitados</span>
      <span class="resumo-item resumo-em-preparo">{{ resumo.emPreparo }} Em Preparo</span>
      <span class="resumo-item resumo-faturado">{{ resumo.faturados }} Faturados</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nº</th>
            <th>Cliente</th>
            <th>Vendedor</th>
            <th>Data</th>
            <th>Total</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="pedido in pagina.content" :key="pedido.id">
            <td>{{ pedido.numero }}</td>
            <td>{{ pedido.clienteNome }}</td>
            <td>{{ pedido.vendedorNome }}</td>
            <td>{{ formatarData(pedido.dataPedido) }}</td>
            <td>{{ formatarPreco(pedido.total) }}</td>
            <td><span class="badge" :class="`badge-${pedido.status}`">{{ statusLabel(pedido.status) }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(pedido.id, $event)"
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
        v-if="pedidoAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarPedido(pedidoAcoesAtual.id)">Editar</div>
        <div v-if="rotuloAvancar(pedidoAcoesAtual.status)" data-test="acao-avancar" @click="avancar(pedidoAcoesAtual)">
          {{ rotuloAvancar(pedidoAcoesAtual.status) }}
        </div>
        <div class="acao-excluir" data-test="acao-excluir" @click="excluir(pedidoAcoesAtual)">Excluir</div>
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
  listarPedidos,
  buscarResumoPedidos,
  avancarStatusPedido,
  excluirPedido,
  type PedidoSummary,
  type PedidoResumo,
  type Page as ApiPage,
  type StatusPedido,
} from '@/api/pedidos'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<PedidoSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<PedidoResumo | null>(null)
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const pedidoAcoesAtual = computed(() =>
  pagina.value.content.find((p) => p.id === acoesAbertas.value) ?? null,
)

const PROXIMO_STATUS: Record<StatusPedido, StatusPedido | null> = {
  DIGITADO: 'EM_PREPARO',
  EM_PREPARO: 'FATURADO',
  FATURADO: null,
}

const STATUS_LABEL: Record<StatusPedido, string> = {
  DIGITADO: 'Digitado',
  EM_PREPARO: 'Em Preparo',
  FATURADO: 'Faturado',
}

function statusLabel(status: StatusPedido) {
  return STATUS_LABEL[status]
}

function rotuloAvancar(status: StatusPedido) {
  const proximo = PROXIMO_STATUS[status]
  return proximo ? `Avançar para ${statusLabel(proximo)}` : null
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listarPedidos({
      busca: filtros.busca || undefined,
      status: (filtros.status || undefined) as StatusPedido | undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de pedidos.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await buscarResumoPedidos()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de pedidos.'
  }
}

function novoPedido() {
  router.push({ name: 'pedidos-novo' })
}

function editarPedido(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'pedidos-editar', params: { id } })
}

function toggleAcoes(id: string, event: MouseEvent) {
  if (acoesAbertas.value === id) {
    acoesAbertas.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  posicaoDropdown.value = {
    top: `${rect.bottom + 4}px`,
    left: `${rect.right - 160}px`,
  }
  acoesAbertas.value = id
}

async function avancar(pedido: PedidoSummary) {
  acoesAbertas.value = null
  const proximo = PROXIMO_STATUS[pedido.status]
  if (!proximo) {
    return
  }
  erro.value = ''
  try {
    await avancarStatusPedido(pedido.id, proximo)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível avançar o status do pedido.'
  }
}

async function excluir(pedido: PedidoSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir o pedido nº ${pedido.numero}?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirPedido(pedido.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir o pedido.'
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

.resumo-digitado {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.resumo-em-preparo {
  background: var(--pm-warning-bg, var(--pm-bg));
  color: var(--pm-warning, var(--pm-text-mid));
}

.resumo-faturado {
  background: var(--pm-success-bg);
  color: var(--pm-success);
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

.badge-DIGITADO {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.badge-EM_PREPARO {
  background: var(--pm-warning-bg, var(--pm-bg));
  color: var(--pm-warning, var(--pm-text-mid));
}

.badge-FATURADO {
  background: var(--pm-success-bg);
  color: var(--pm-success);
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
  min-width: 160px;
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

Note: `var(--pm-warning-bg, var(--pm-bg))` and `var(--pm-warning, var(--pm-text-mid))` use CSS custom-property fallbacks, since no `--pm-warning*` token currently exists in the design system and adding one is out of scope for this slice — this keeps the "Em Preparo" badge visually distinct (falls back to the neutral `--pm-bg`/`--pm-text-mid` pairing) without inventing an unapproved token.

- [ ] **Step 2: Add the `pedidos` list route**

In `mesh-suite-frontend/src/router/index.ts`, add the import:

```typescript
import PedidosListView from '@/views/PedidosListView.vue'
```

and add this route right before the `pedidos-novo` route added in Task 5:

```typescript
    { path: '/pedidos', name: 'pedidos', component: PedidosListView },
```

- [ ] **Step 3: Activate the "Pedidos" sidebar item**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, change:

```typescript
  { icon: '📋', label: 'Pedidos', route: null },
```

to:

```typescript
  { icon: '📋', label: 'Pedidos', route: '/pedidos' },
```

- [ ] **Step 4: Write the failing view test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PedidosListView from '@/views/PedidosListView.vue'
import * as pedidosApi from '@/api/pedidos'

vi.mock('@/api/pedidos')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/pedidos', name: 'pedidos', component: PedidosListView },
      { path: '/pedidos/novo', name: 'pedidos-novo', component: { template: '<div />' } },
      { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/pedidos')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(PedidosListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const pedidoDigitado = {
  id: 'ped1', numero: 1, clienteNome: 'Mercado Silva', vendedorNome: 'Carla Vendedora',
  dataPedido: '2026-07-31', total: 119.8, status: 'DIGITADO' as const,
}

const pedidoFaturado = {
  id: 'ped2', numero: 2, clienteNome: 'Padaria Aurora', vendedorNome: 'Carla Vendedora',
  dataPedido: '2026-07-30', total: 59.9, status: 'FATURADO' as const,
}

describe('PedidosListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [pedidoDigitado], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(pedidosApi.buscarResumoPedidos).mockResolvedValue({
      total: 1, digitados: 1, emPreparo: 0, faturados: 0,
    })
  })

  it('loads and displays the pedido list on mount', async () => {
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

    expect(pedidosApi.listarPedidos).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('navigates to the create form when "+ Novo Pedido" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-pedido"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-novo')
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-editar')
    expect(router.currentRoute.value.params.id).toBe('ped1')
  })

  it('advances the status via the "Avançar para Em Preparo" Ações item', async () => {
    vi.mocked(pedidosApi.avancarStatusPedido).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    expect(wrapper.find('[data-test="acao-avancar"]').text()).toBe('Avançar para Em Preparo')
    await wrapper.find('[data-test="acao-avancar"]').trigger('click')
    await flushPromises()

    expect(pedidosApi.avancarStatusPedido).toHaveBeenCalledWith('ped1', 'EM_PREPARO')
  })

  it('hides the "Avançar" item once a pedido is already Faturado', async () => {
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [pedidoFaturado], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')

    expect(wrapper.find('[data-test="acao-avancar"]').exists()).toBe(false)
  })

  it('excludes a pedido via the Ações menu after confirming', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    vi.mocked(pedidosApi.excluirPedido).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(pedidosApi.excluirPedido).toHaveBeenCalledWith('ped1')
  })

  it('shows an error message when loading the pedido list fails', async () => {
    vi.mocked(pedidosApi.listarPedidos).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de pedidos.')
  })
})
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PedidosListView.spec.ts`
Expected: PASS (8 tests, 0 failures).

- [ ] **Step 6: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: PASS, all existing suites (including `AppSidebar.spec.ts` if it exists, and `PedidoFormView.spec.ts` from Task 5) unaffected.

- [ ] **Step 7: Manually verify in the browser**

Start the backend (`cd mesh-suite-backend && ./mvnw spring-boot:run`, using the same env vars as `devup.sh`) and frontend (`cd mesh-suite-frontend && npm run dev`), log in, click "Pedidos" in the sidebar, create a pedido with a real cliente/vendedor/produto, confirm it appears in the list with the right total, advance its status via the Ações menu, and confirm the badge/label update.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-frontend/src/views/PedidosListView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/views/__tests__/PedidosListView.spec.ts
git commit -m "feat(pedido): add PedidosListView with status-advance action, activate Pedidos sidebar nav"
```
