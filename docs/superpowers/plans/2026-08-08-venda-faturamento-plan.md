# Venda (Faturamento de Pedido) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert a Pedido in status `EM_PREPARO` into a Venda (fiscal sales document) — new `Venda`/`ItemVenda` entities with a formal FK to `Pedido`, per-item tax calculation reusing the existing `FiscalCalculationService`, a "Faturar" action in the frontend, and a listing screen for Vendas.

**Architecture:** New backend module `com.meshsuite.venda` (controller/service/repository/domain/dto/exception), following the same layered-per-module package structure already used by `pedido`/`purchaseorder`/etc. `VendaService` depends on `pedido`'s repository/domain (one-directional: venda → pedido). `PedidoService.avancarStatus` is tightened to reject the `FATURADO` target so that status can only change via the new faturamento flow, guaranteeing every `FATURADO` Pedido has exactly one Venda. Frontend adds `src/api/vendas.ts`, a read-only `VendasListView.vue`, and swaps the existing "Avançar para Faturado" action in `PedidosListView.vue` for a "Faturar" action.

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / PostgreSQL 16 (Flyway, RLS) on the backend; Vue 3 + TypeScript + Vitest on the frontend. Same stack as every other slice already in this repo — no new dependencies.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-08-venda-faturamento-design.md` — read it before starting if anything below is ambiguous.
- 1 Pedido → 1 Venda: `venda.pedido_id` is `NOT NULL UNIQUE`, a real FK (not the legacy's loose reference).
- No stock/receivable side effects in this slice — Venda only persists the document itself. (Matches the current system: `PurchaseOrder` doesn't trigger `AccountsPayable`/`StockMovement` either.)
- Venda is immutable once created: no `PUT`/`DELETE` endpoints, no status field.
- Tax calc reuses `com.meshsuite.fiscal.service.FiscalCalculationService` exactly as it exists today (flat percentage rates from `FiscalRegistration`) — do not add ICMS-ST/MVA/IPI-redução granularity.
- Every new table gets `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + a tenant-isolation policy, mirroring `pedido`/`item_pedido` exactly (child tables with no `tenant_id` column use the `EXISTS` pattern against the parent).
- Every service method that's reachable via a controller gets `@RequiresPermission(module = Module.SALE, action = Action.*)`.
- Package layout per module: `controller/`, `service/`, `repository/`, `repository/specification/`, `domain/`, `domain/enums/`, `dto/`, `exception/` — same structure the rest of the backend already uses.
- Only touch of already-working code: `PedidoService.avancarStatus` gets one new guard clause (reject `FATURADO`). Nothing else in `pedido`/`produto`/`fiscal` changes.
- The `app_user` table is the current name of the users table (renamed from `usuario` in `V8__rename_usuario_to_user.sql`) — FKs to the user table must reference `app_user(id)`, not `usuario(id)`.

---

### Task 1: Migrations — `venda`/`item_venda`/`venda_contador` tables + `SALE` permission module

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V26__create_venda.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V27__add_sale_to_user_permission_module_check.sql`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/domain/enums/Module.java`

**Interfaces:**
- Produces: DB tables `venda`, `item_venda`, `venda_contador` (columns listed below); enum constant `Module.SALE`.

- [ ] **Step 1: Write the migration for the counter, `venda`, and `item_venda` tables**

```sql
-- mesh-suite-backend/src/main/resources/db/migration/V26__create_venda.sql
CREATE TABLE venda_contador (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    proximo_numero INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE venda_contador ENABLE ROW LEVEL SECURITY;
ALTER TABLE venda_contador FORCE ROW LEVEL SECURITY;

CREATE POLICY venda_contador_tenant_isolation ON venda_contador
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE venda (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    numero INTEGER NOT NULL,
    pedido_id UUID NOT NULL UNIQUE REFERENCES pedido(id),
    cliente_id UUID NOT NULL REFERENCES parceiro(id),
    vendedor_id UUID NOT NULL REFERENCES app_user(id),
    data_emissao DATE NOT NULL DEFAULT CURRENT_DATE,
    desconto NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_icms NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_ipi NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_pis NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_cofins NUMERIC(12,2) NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_venda_tenant_numero ON venda(tenant_id, numero);
CREATE INDEX idx_venda_tenant_id ON venda(tenant_id);
CREATE INDEX idx_venda_cliente_id ON venda(cliente_id);
CREATE INDEX idx_venda_vendedor_id ON venda(vendedor_id);

ALTER TABLE venda ENABLE ROW LEVEL SECURITY;
ALTER TABLE venda FORCE ROW LEVEL SECURITY;

CREATE POLICY venda_tenant_isolation ON venda
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE item_venda (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venda_id UUID NOT NULL REFERENCES venda(id) ON DELETE CASCADE,
    produto_id UUID NOT NULL REFERENCES produto(id),
    quantidade NUMERIC(12,3) NOT NULL,
    valor_unitario NUMERIC(12,2) NOT NULL,
    valor_total NUMERIC(12,2) NOT NULL,
    valor_icms NUMERIC(12,2) NOT NULL,
    valor_ipi NUMERIC(12,2) NOT NULL,
    valor_pis NUMERIC(12,2) NOT NULL,
    valor_cofins NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_item_venda_venda_id ON item_venda(venda_id);

ALTER TABLE item_venda ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_venda FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent venda
-- row's own RLS policy, matched by venda_id. Same pattern as item_pedido.
CREATE POLICY item_venda_tenant_isolation ON item_venda
    USING (EXISTS (
        SELECT 1 FROM venda v
        WHERE v.id = item_venda.venda_id
          AND v.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

- [ ] **Step 2: Write the migration adding `SALE` to the permission module check constraint**

```sql
-- mesh-suite-backend/src/main/resources/db/migration/V27__add_sale_to_user_permission_module_check.sql
ALTER TABLE user_permission DROP CONSTRAINT user_permission_module_check;

ALTER TABLE user_permission ADD CONSTRAINT user_permission_module_check
    CHECK (module IN ('CUSTOMER','PRODUCT','ORDER','USER','PURCHASE','STOCK','PAYABLE','SALE'));
```

- [ ] **Step 3: Add `SALE` to the `Module` enum**

```java
package com.meshsuite.auth.domain.enums;

public enum Module {
    CUSTOMER,
    PRODUCT,
    ORDER,
    USER,
    PURCHASE,
    STOCK,
    PAYABLE,
    SALE
}
```

- [ ] **Step 4: Verify the app boots with the new migrations**

Run: `cd mesh-suite-backend && mvn -q clean compile`
Expected: `BUILD SUCCESS`, no output on success. (Migrations are validated when the test suite boots Spring context in later tasks — Flyway will fail loudly if the SQL is malformed.)

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V26__create_venda.sql \
        mesh-suite-backend/src/main/resources/db/migration/V27__add_sale_to_user_permission_module_check.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/domain/enums/Module.java
git commit -m "feat(venda): add venda/item_venda/venda_contador tables and SALE permission module"
```

---

### Task 2: Domain entities, repositories, and RLS tests

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/Venda.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/ItemVenda.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/VendaContador.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/VendaRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/VendaContadorRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/specification/VendaSpecifications.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/venda/repository/VendaRepositoryTest.java`

**Interfaces:**
- Consumes: `com.meshsuite.pedido.domain.Pedido` (existing), `com.meshsuite.parceiro.domain.Parceiro` (existing), `com.meshsuite.user.domain.User` (existing), `com.meshsuite.produto.domain.Produto` (existing).
- Produces: `Venda` (getters/setters: `getId/setId`, `getTenantId/setTenantId`, `getNumero/setNumero`, `getPedido/setPedido`, `getCliente/setCliente`, `getVendedor/setVendedor`, `getDataEmissao/setDataEmissao`, `getDesconto/setDesconto`, `getSubtotal/setSubtotal`, `getTotal/setTotal`, `getValorIcms/setValorIcms`, `getValorIpi/setValorIpi`, `getValorPis/setValorPis`, `getValorCofins/setValorCofins`, `getCriadoEm`, `getItens()` returning `List<ItemVenda>`). `ItemVenda` (`getId/setId`, `getVenda/setVenda`, `getProduto/setProduto`, `getQuantidade/setQuantidade`, `getValorUnitario/setValorUnitario`, `getValorTotal/setValorTotal`, `getValorIcms/setValorIcms`, `getValorIpi/setValorIpi`, `getValorPis/setValorPis`, `getValorCofins/setValorCofins`). `VendaRepository extends JpaRepository<Venda, UUID>, JpaSpecificationExecutor<Venda>`. `VendaContadorRepository extends JpaRepository<VendaContador, UUID>`. `VendaSpecifications.comBusca(String busca): Specification<Venda>`.

- [ ] **Step 1: Write the failing RLS/cascade test**

```java
// mesh-suite-backend/src/test/java/com/meshsuite/venda/repository/VendaRepositoryTest.java
package com.meshsuite.venda.repository;

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
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import com.meshsuite.venda.domain.ItemVenda;
import com.meshsuite.venda.domain.Venda;
import com.meshsuite.venda.domain.VendaContador;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class VendaRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired VendaRepository vendaRepository;
    @Autowired VendaContadorRepository vendaContadorRepository;
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

    private User criarVendedor(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u);
    }

    private Produto criarProduto(UUID tenantId, String sku) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(new BigDecimal("59.90"));
        return produtoRepository.saveAndFlush(p);
    }

    private Pedido criarPedido(UUID tenantId, Parceiro cliente, User vendedor, int numero) {
        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setNumero(numero);
        pedido.setCliente(cliente);
        pedido.setVendedor(vendedor);
        return pedidoRepository.saveAndFlush(pedido);
    }

    private Venda novaVenda(UUID tenantId, Pedido pedido, Parceiro cliente, User vendedor, int numero) {
        Venda venda = new Venda();
        venda.setTenantId(tenantId);
        venda.setNumero(numero);
        venda.setPedido(pedido);
        venda.setCliente(cliente);
        venda.setVendedor(vendedor);
        return venda;
    }

    @Test
    @Transactional
    void savesVendaWithItensViaCascade() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Produto produto = criarProduto(tenant.getId(), "P0001");
        Pedido pedido = criarPedido(tenant.getId(), cliente, vendedor, 1);

        Venda venda = novaVenda(tenant.getId(), pedido, cliente, vendedor, 1);
        ItemVenda item = new ItemVenda();
        item.setVenda(venda);
        item.setProduto(produto);
        item.setQuantidade(new BigDecimal("2"));
        item.setValorUnitario(new BigDecimal("59.90"));
        item.setValorTotal(new BigDecimal("119.80"));
        item.setValorIcms(new BigDecimal("10.00"));
        item.setValorIpi(BigDecimal.ZERO);
        item.setValorPis(BigDecimal.ZERO);
        item.setValorCofins(BigDecimal.ZERO);
        venda.getItens().add(item);

        Venda saved = vendaRepository.saveAndFlush(venda);
        entityManager.clear();

        Venda reloaded = vendaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getItens()).hasSize(1);
        assertThat(reloaded.getItens().get(0).getValorIcms()).isEqualByComparingTo("10.00");
        assertThat(reloaded.getPedido().getId()).isEqualTo(pedido.getId());
    }

    @Test
    @Transactional
    void pedidoIdMustBeUniqueAcrossVendas() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Pedido pedido = criarPedido(tenant.getId(), cliente, vendedor, 1);

        vendaRepository.saveAndFlush(novaVenda(tenant.getId(), pedido, cliente, vendedor, 1));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> vendaRepository.saveAndFlush(novaVenda(tenant.getId(), pedido, cliente, vendedor, 2)));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Pedido pedido = criarPedido(tenant.getId(), cliente, vendedor, 1);
        vendaRepository.saveAndFlush(novaVenda(tenant.getId(), pedido, cliente, vendedor, 1));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM venda")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void itemVendaRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        Parceiro cliente = criarCliente(tenant.getId(), "11222333000144");
        User vendedor = criarVendedor(tenant.getId(), "marina@aurora.com.br");
        Produto produto = criarProduto(tenant.getId(), "P0001");
        Pedido pedido = criarPedido(tenant.getId(), cliente, vendedor, 1);

        Venda venda = novaVenda(tenant.getId(), pedido, cliente, vendedor, 1);
        ItemVenda item = new ItemVenda();
        item.setVenda(venda);
        item.setProduto(produto);
        item.setQuantidade(BigDecimal.ONE);
        item.setValorUnitario(new BigDecimal("59.90"));
        item.setValorTotal(new BigDecimal("59.90"));
        item.setValorIcms(BigDecimal.ZERO);
        item.setValorIpi(BigDecimal.ZERO);
        item.setValorPis(BigDecimal.ZERO);
        item.setValorCofins(BigDecimal.ZERO);
        venda.getItens().add(item);
        Venda saved = vendaRepository.saveAndFlush(venda);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM item_venda WHERE venda_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void vendaContadorRlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        VendaContador contador = new VendaContador();
        contador.setTenantId(tenant.getId());
        contador.setProximoNumero(1);
        vendaContadorRepository.saveAndFlush(contador);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM venda_contador")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile (entities/repos don't exist yet)**

Run: `cd mesh-suite-backend && mvn -q test-compile`
Expected: `COMPILATION ERROR` — `package com.meshsuite.venda.domain does not exist` (or similar).

- [ ] **Step 3: Create `Venda.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/Venda.java
package com.meshsuite.venda.domain;

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
@Table(name = "venda")
@Getter
@Setter
public class Venda {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Integer numero;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Parceiro cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private User vendedor;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao = LocalDate.now();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "valor_icms", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorIcms = BigDecimal.ZERO;

    @Column(name = "valor_ipi", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorIpi = BigDecimal.ZERO;

    @Column(name = "valor_pis", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPis = BigDecimal.ZERO;

    @Column(name = "valor_cofins", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorCofins = BigDecimal.ZERO;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemVenda> itens = new ArrayList<>();
}
```

- [ ] **Step 4: Create `ItemVenda.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/ItemVenda.java
package com.meshsuite.venda.domain;

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
@Table(name = "item_venda")
@Getter
@Setter
public class ItemVenda {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "valor_icms", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorIcms;

    @Column(name = "valor_ipi", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorIpi;

    @Column(name = "valor_pis", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPis;

    @Column(name = "valor_cofins", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorCofins;
}
```

- [ ] **Step 5: Create `VendaContador.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/domain/VendaContador.java
package com.meshsuite.venda.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "venda_contador")
@Getter
@Setter
public class VendaContador {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "proximo_numero", nullable = false)
    private Integer proximoNumero = 1;
}
```

- [ ] **Step 6: Create the repositories and specification**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/VendaRepository.java
package com.meshsuite.venda.repository;

import com.meshsuite.venda.domain.Venda;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VendaRepository extends JpaRepository<Venda, UUID>, JpaSpecificationExecutor<Venda> {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/VendaContadorRepository.java
package com.meshsuite.venda.repository;

import com.meshsuite.venda.domain.VendaContador;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendaContadorRepository extends JpaRepository<VendaContador, UUID> {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/repository/specification/VendaSpecifications.java
package com.meshsuite.venda.repository.specification;

import com.meshsuite.venda.domain.Venda;
import org.springframework.data.jpa.domain.Specification;

public final class VendaSpecifications {

    private VendaSpecifications() {
    }

    public static Specification<Venda> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        Integer numero = tryParseInt(busca.trim());
        return (root, query, cb) -> {
            var porCliente = cb.like(cb.lower(root.get("cliente").get("nomeFantasia")), termo);
            if (numero != null) {
                return cb.or(porCliente, cb.equal(root.get("numero"), numero));
            }
            return porCliente;
        };
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

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=VendaRepositoryTest`
Expected: `BUILD SUCCESS`, 5 tests passed. (Requires Docker for Testcontainers — if unavailable in your environment, this is the point where you must run it before continuing; every later task builds on this passing.)

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/venda/domain \
        mesh-suite-backend/src/main/java/com/meshsuite/venda/repository \
        mesh-suite-backend/src/test/java/com/meshsuite/venda/repository/VendaRepositoryTest.java
git commit -m "feat(venda): add Venda/ItemVenda/VendaContador entities and repositories"
```

---

### Task 3: DTOs and exceptions

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/VendaResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/ItemVendaResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/VendaSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaNaoEncontradaException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaValidacaoException.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `VendaResponse(UUID id, Integer numero, UUID pedidoId, Integer pedidoNumero, UUID clienteId, String clienteNome, UUID vendedorId, String vendedorNome, LocalDate dataEmissao, BigDecimal desconto, BigDecimal subtotal, BigDecimal total, BigDecimal valorIcms, BigDecimal valorIpi, BigDecimal valorPis, BigDecimal valorCofins, List<ItemVendaResponse> itens)`. `ItemVendaResponse(UUID produtoId, String produtoNome, BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal, BigDecimal valorIcms, BigDecimal valorIpi, BigDecimal valorPis, BigDecimal valorCofins)`. `VendaSummaryResponse(UUID id, Integer numero, String clienteNome, LocalDate dataEmissao, BigDecimal total)`. `VendaNaoEncontradaException` (no-arg constructor), `VendaValidacaoException(String mensagem)`.

- [ ] **Step 1: Create the DTOs**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/ItemVendaResponse.java
package com.meshsuite.venda.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemVendaResponse(
        UUID produtoId,
        String produtoNome,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        BigDecimal valorIcms,
        BigDecimal valorIpi,
        BigDecimal valorPis,
        BigDecimal valorCofins) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/VendaResponse.java
package com.meshsuite.venda.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VendaResponse(
        UUID id,
        Integer numero,
        UUID pedidoId,
        Integer pedidoNumero,
        UUID clienteId,
        String clienteNome,
        UUID vendedorId,
        String vendedorNome,
        LocalDate dataEmissao,
        BigDecimal desconto,
        BigDecimal subtotal,
        BigDecimal total,
        BigDecimal valorIcms,
        BigDecimal valorIpi,
        BigDecimal valorPis,
        BigDecimal valorCofins,
        List<ItemVendaResponse> itens) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/dto/VendaSummaryResponse.java
package com.meshsuite.venda.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VendaSummaryResponse(
        UUID id,
        Integer numero,
        String clienteNome,
        LocalDate dataEmissao,
        BigDecimal total) {
}
```

- [ ] **Step 2: Create the exceptions**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaNaoEncontradaException.java
package com.meshsuite.venda.exception;

public class VendaNaoEncontradaException extends RuntimeException {
    public VendaNaoEncontradaException() {
        super("Venda não encontrada");
    }
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaValidacaoException.java
package com.meshsuite.venda.exception;

public class VendaValidacaoException extends RuntimeException {
    public VendaValidacaoException(String mensagem) {
        super(mensagem);
    }
}
```

- [ ] **Step 3: Wire both exceptions into `GlobalExceptionHandler`**

In `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`, add these two methods at the end of the class, right before the closing `}` (same style as every other exception mapping in this file — fully-qualified inline type, no new import):

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

- [ ] **Step 4: Verify it compiles**

Run: `cd mesh-suite-backend && mvn -q clean compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/venda/dto \
        mesh-suite-backend/src/main/java/com/meshsuite/venda/exception \
        mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java
git commit -m "feat(venda): add Venda DTOs and exceptions"
```

---

### Task 4: Tighten `PedidoService.avancarStatus` to reject `FATURADO`

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/service/PedidoService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/service/PedidoServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/controller/PedidoControllerTest.java`

**Interfaces:**
- Consumes: `com.meshsuite.pedido.exception.PedidoValidacaoException` (existing).
- Produces: `PedidoService.avancarStatus(UUID id, StatusPedido novoStatus)` now throws `PedidoValidacaoException` immediately when `novoStatus == StatusPedido.FATURADO`, before any other check.

- [ ] **Step 1: Write the failing service test**

Add this test method to `PedidoServiceTest` (same file, same fixtures already defined — `setUpTenant`, `criarCliente`, `criarVendedor`, `criarProduto`, `request` are all already in the class from the existing tests):

```java
    @Test
    void rejeitaFaturarViaAvancarStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProduto(tenantId, "P0001", new BigDecimal("59.90"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("59.90")));
        var criado = pedidoService.criar(tenantId, request(clienteId, vendedorId, itens, BigDecimal.ZERO));
        pedidoService.avancarStatus(criado.id(), StatusPedido.EM_PREPARO);

        assertThrows(PedidoValidacaoException.class,
                () -> pedidoService.avancarStatus(criado.id(), StatusPedido.FATURADO));
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PedidoServiceTest#rejeitaFaturarViaAvancarStatus`
Expected: FAIL — the pedido successfully advances to `FATURADO` instead of throwing (no exception raised where one was expected).

- [ ] **Step 3: Add the guard clause**

In `mesh-suite-backend/src/main/java/com/meshsuite/pedido/service/PedidoService.java`, change `avancarStatus` from:

```java
    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.EDIT)
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
```

to:

```java
    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.EDIT)
    public PedidoResponse avancarStatus(UUID id, StatusPedido novoStatus) {
        if (novoStatus == StatusPedido.FATURADO) {
            throw new PedidoValidacaoException(
                    "Faturamento deve ser feito através do fluxo de Venda (POST /api/vendas/faturar/{pedidoId})");
        }
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
```

- [ ] **Step 4: Run the new test to verify it passes, then the whole `PedidoServiceTest` class to verify nothing else broke**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PedidoServiceTest`
Expected: `BUILD SUCCESS`, all tests pass (the existing `rejeitaPularEtapaDeStatus` test still passes — it goes `DIGITADO → FATURADO`, which now fails at the new guard instead of the ordinal check, but it only asserts the exception type, not the message).

- [ ] **Step 5: Add an HTTP-level regression test**

Add this test method to `PedidoControllerTest` (reuses `loginAndSetUp`, already in the class):

```java
    @Test
    void advancingToFaturadoViaStatusEndpointIsRejected() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoPayload(ctx)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/pedidos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/pedidos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FATURADO\"}"))
                .andExpect(status().isBadRequest());
    }
```

- [ ] **Step 6: Run it**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=PedidoControllerTest`
Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/pedido/service/PedidoService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/pedido/service/PedidoServiceTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/pedido/controller/PedidoControllerTest.java
git commit -m "fix(pedido): reject advancing status to FATURADO outside the Venda faturamento flow"
```

---

### Task 5: `VendaService` + `VendaServiceTest`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/service/VendaService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/venda/service/VendaServiceTest.java`

**Interfaces:**
- Consumes: `VendaRepository`, `com.meshsuite.pedido.repository.PedidoRepository`, `com.meshsuite.fiscal.service.FiscalCalculationService.calculate(FiscalRegistration, BigDecimal quantidade, BigDecimal valorUnitario): FiscalCalculationResult`, `EntityManager`. `VendaResponse`/`VendaSummaryResponse`/`ItemVendaResponse` (Task 3). `VendaNaoEncontradaException`/`VendaValidacaoException` (Task 3). `PedidoNaoEncontradoException` (existing, reused as-is — no new "pedido not found" type for this module).
- Produces: `VendaService.faturar(UUID pedidoId): VendaResponse`, `VendaService.listar(String busca, Pageable pageable): Page<VendaSummaryResponse>`, `VendaService.buscarPorId(UUID id): VendaResponse`.

- [ ] **Step 1: Write the failing service tests**

```java
// mesh-suite-backend/src/test/java/com/meshsuite/venda/service/VendaServiceTest.java
package com.meshsuite.venda.service;

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
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import com.meshsuite.venda.dto.VendaResponse;
import com.meshsuite.venda.exception.VendaValidacaoException;
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
class VendaServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PedidoService pedidoService;
    @Autowired VendaService vendaService;
    @Autowired EntityManager entityManager;

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

    private UUID criarCliente(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p).getId();
    }

    private UUID criarVendedor(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u).getId();
    }

    private FiscalRegistration criarCadastroFiscal(UUID tenantId) {
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

    private UUID criarProdutoComCadastroFiscal(UUID tenantId, String sku, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Polo");
        p.setSku(sku);
        p.setPrecoVenda(precoVenda);
        p.setFiscalRegistration(criarCadastroFiscal(tenantId));
        return produtoRepository.saveAndFlush(p).getId();
    }

    private UUID criarProdutoSemCadastroFiscal(UUID tenantId, String sku, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setTenantId(tenantId);
        p.setNome("Camiseta Sem Fiscal");
        p.setSku(sku);
        p.setPrecoVenda(precoVenda);
        return produtoRepository.saveAndFlush(p).getId();
    }

    private UUID criarPedidoEmPreparo(UUID tenantId, UUID clienteId, UUID vendedorId, UUID produtoId,
                                       BigDecimal quantidade, BigDecimal valorUnitario) {
        var itens = List.of(new ItemPedidoDto(produtoId, quantidade, valorUnitario));
        var request = new PedidoRequest(clienteId, vendedorId, null, null, BigDecimal.ZERO, itens);
        var pedido = pedidoService.criar(tenantId, request);
        pedidoService.avancarStatus(pedido.id(), StatusPedido.EM_PREPARO);
        return pedido.id();
    }

    @Test
    void faturaPedidoEmPreparoCopiandoItensECalculandoTributos() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        UUID pedidoId = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId,
                new BigDecimal("10"), new BigDecimal("50.00"));

        VendaResponse venda = vendaService.faturar(pedidoId);

        assertThat(venda.numero()).isEqualTo(1);
        assertThat(venda.pedidoId()).isEqualTo(pedidoId);
        assertThat(venda.total()).isEqualByComparingTo("500.00");
        assertThat(venda.itens()).hasSize(1);
        assertThat(venda.itens().get(0).valorIcms()).isEqualByComparingTo("90.00");
        assertThat(venda.valorIcms()).isEqualByComparingTo("90.00");

        Pedido pedidoAtualizado = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedidoAtualizado.getStatus()).isEqualTo(StatusPedido.FATURADO);
    }

    @Test
    void numeroIncrementaSequencialmentePorTenant() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        UUID pedido1 = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));
        UUID pedido2 = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));

        VendaResponse primeira = vendaService.faturar(pedido1);
        VendaResponse segunda = vendaService.faturar(pedido2);

        assertThat(primeira.numero()).isEqualTo(1);
        assertThat(segunda.numero()).isEqualTo(2);
    }

    @Test
    void rejeitaFaturarPedidoQueNaoEstaEmPreparo() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        var itens = List.of(new ItemPedidoDto(produtoId, BigDecimal.ONE, new BigDecimal("50.00")));
        var pedido = pedidoService.criar(tenantId,
                new PedidoRequest(clienteId, vendedorId, null, null, BigDecimal.ZERO, itens));

        assertThrows(VendaValidacaoException.class, () -> vendaService.faturar(pedido.id()));
    }

    @Test
    void rejeitaFaturarQuandoProdutoNaoTemCadastroFiscal() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoSemCadastroFiscal(tenantId, "P0002", new BigDecimal("50.00"));
        UUID pedidoId = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));

        assertThrows(VendaValidacaoException.class, () -> vendaService.faturar(pedidoId));
    }

    @Test
    void faturarDuasVezesOMesmoPedidoFalhaNaSegundaTentativa() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        UUID pedidoId = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));

        vendaService.faturar(pedidoId);

        // Second call sees the pedido already FATURADO (not EM_PREPARO), so it's
        // rejected by the same status guard as rejeitaFaturarPedidoQueNaoEstaEmPreparo.
        assertThrows(VendaValidacaoException.class, () -> vendaService.faturar(pedidoId));
    }

    @Test
    void listaEBuscaVendaPorId() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        UUID vendedorId = criarVendedor(tenantId, "marina@aurora.com.br");
        UUID produtoId = criarProdutoComCadastroFiscal(tenantId, "P0001", new BigDecimal("50.00"));
        UUID pedidoId = criarPedidoEmPreparo(tenantId, clienteId, vendedorId, produtoId, BigDecimal.ONE, new BigDecimal("50.00"));
        VendaResponse criada = vendaService.faturar(pedidoId);

        var pagina = vendaService.listar(null, PageRequest.of(0, 10));
        var buscada = vendaService.buscarPorId(criada.id());

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(buscada.clienteNome()).isEqualTo("Mercado Silva");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail to compile**

Run: `cd mesh-suite-backend && mvn -q test-compile`
Expected: `COMPILATION ERROR` — `VendaService` does not exist.

- [ ] **Step 3: Implement `VendaService`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/service/VendaService.java
package com.meshsuite.venda.service;

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
import com.meshsuite.venda.domain.ItemVenda;
import com.meshsuite.venda.domain.Venda;
import com.meshsuite.venda.dto.ItemVendaResponse;
import com.meshsuite.venda.dto.VendaResponse;
import com.meshsuite.venda.dto.VendaSummaryResponse;
import com.meshsuite.venda.exception.VendaNaoEncontradaException;
import com.meshsuite.venda.exception.VendaValidacaoException;
import com.meshsuite.venda.repository.VendaRepository;
import com.meshsuite.venda.repository.specification.VendaSpecifications;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendaService {

    private final VendaRepository vendaRepository;
    private final PedidoRepository pedidoRepository;
    private final FiscalCalculationService fiscalCalculationService;
    private final EntityManager entityManager;

    public VendaService(VendaRepository vendaRepository, PedidoRepository pedidoRepository,
                         FiscalCalculationService fiscalCalculationService, EntityManager entityManager) {
        this.vendaRepository = vendaRepository;
        this.pedidoRepository = pedidoRepository;
        this.fiscalCalculationService = fiscalCalculationService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public Page<VendaSummaryResponse> listar(String busca, Pageable pageable) {
        Specification<Venda> spec = Specification.where(VendaSpecifications.comBusca(busca));
        return vendaRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public VendaResponse buscarPorId(UUID id) {
        return toResponse(vendaRepository.findById(id).orElseThrow(VendaNaoEncontradaException::new));
    }

    @Transactional
    @RequiresPermission(module = Module.SALE, action = Action.CREATE)
    public VendaResponse faturar(UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(PedidoNaoEncontradoException::new);
        if (pedido.getStatus() != StatusPedido.EM_PREPARO) {
            throw new VendaValidacaoException(
                    "Só é possível faturar um pedido em preparo. Status atual: " + pedido.getStatus());
        }

        Venda venda = new Venda();
        venda.setTenantId(pedido.getTenantId());
        venda.setNumero(proximoNumero(pedido.getTenantId()));
        venda.setPedido(pedido);
        venda.setCliente(pedido.getCliente());
        venda.setVendedor(pedido.getVendedor());
        venda.setDesconto(pedido.getDesconto());
        venda.setSubtotal(pedido.getSubtotal());
        venda.setTotal(pedido.getTotal());

        BigDecimal totalIcms = BigDecimal.ZERO;
        BigDecimal totalIpi = BigDecimal.ZERO;
        BigDecimal totalPis = BigDecimal.ZERO;
        BigDecimal totalCofins = BigDecimal.ZERO;

        for (ItemPedido itemPedido : pedido.getItens()) {
            Produto produto = itemPedido.getProduto();
            if (produto.getFiscalRegistration() == null) {
                throw new VendaValidacaoException(
                        "O produto " + produto.getNome() + " não possui cadastro fiscal aplicado");
            }
            FiscalCalculationResult calculo = fiscalCalculationService.calculate(
                    produto.getFiscalRegistration(), itemPedido.getQuantidade(), itemPedido.getValorUnitario());

            ItemVenda itemVenda = new ItemVenda();
            itemVenda.setVenda(venda);
            itemVenda.setProduto(produto);
            itemVenda.setQuantidade(itemPedido.getQuantidade());
            itemVenda.setValorUnitario(itemPedido.getValorUnitario());
            itemVenda.setValorTotal(itemPedido.getValorTotal());
            itemVenda.setValorIcms(calculo.icmsValue());
            itemVenda.setValorIpi(calculo.ipiValue());
            itemVenda.setValorPis(calculo.pisValue());
            itemVenda.setValorCofins(calculo.cofinsValue());
            venda.getItens().add(itemVenda);

            totalIcms = totalIcms.add(calculo.icmsValue());
            totalIpi = totalIpi.add(calculo.ipiValue());
            totalPis = totalPis.add(calculo.pisValue());
            totalCofins = totalCofins.add(calculo.cofinsValue());
        }

        venda.setValorIcms(totalIcms);
        venda.setValorIpi(totalIpi);
        venda.setValorPis(totalPis);
        venda.setValorCofins(totalCofins);

        Venda salva = vendaRepository.saveAndFlush(venda);

        pedido.setStatus(StatusPedido.FATURADO);
        pedidoRepository.saveAndFlush(pedido);

        return toResponse(salva);
    }

    // Atomic UPDATE ... RETURNING against the tenant's single venda_contador row --
    // never COUNT(*)/MAX(numero)+1, both of which race under concurrent inserts.
    // Runs inside this method's own @Transactional, so TenantContextAspect has
    // already issued SET LOCAL app.tenant_id before either native query below runs.
    private int proximoNumero(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO venda_contador (tenant_id, proximo_numero) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object resultado = entityManager.createNativeQuery(
                        "UPDATE venda_contador SET proximo_numero = proximo_numero + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING proximo_numero - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) resultado).intValue();
    }

    private VendaSummaryResponse toSummary(Venda v) {
        return new VendaSummaryResponse(v.getId(), v.getNumero(), v.getCliente().getNomeFantasia(),
                v.getDataEmissao(), v.getTotal());
    }

    private VendaResponse toResponse(Venda v) {
        List<ItemVendaResponse> itens = v.getItens().stream()
                .map(i -> new ItemVendaResponse(i.getProduto().getId(), i.getProduto().getNome(),
                        i.getQuantidade(), i.getValorUnitario(), i.getValorTotal(),
                        i.getValorIcms(), i.getValorIpi(), i.getValorPis(), i.getValorCofins()))
                .toList();
        return new VendaResponse(v.getId(), v.getNumero(), v.getPedido().getId(), v.getPedido().getNumero(),
                v.getCliente().getId(), v.getCliente().getNomeFantasia(),
                v.getVendedor().getId(), v.getVendedor().getName(),
                v.getDataEmissao(), v.getDesconto(), v.getSubtotal(), v.getTotal(),
                v.getValorIcms(), v.getValorIpi(), v.getValorPis(), v.getValorCofins(), itens);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=VendaServiceTest`
Expected: `BUILD SUCCESS`, 6 tests passed.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/venda/service/VendaService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/venda/service/VendaServiceTest.java
git commit -m "feat(venda): add VendaService.faturar/listar/buscarPorId"
```

---

### Task 6: `VendaController` + `VendaControllerTest`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/controller/VendaController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/venda/controller/VendaControllerTest.java`

**Interfaces:**
- Consumes: `VendaService.faturar/listar/buscarPorId` (Task 5).
- Produces: `POST /api/vendas/faturar/{pedidoId}` (201, `VendaResponse`), `GET /api/vendas` (200, `Page<VendaSummaryResponse>`), `GET /api/vendas/{id}` (200, `VendaResponse`).

- [ ] **Step 1: Write the failing controller test**

```java
// mesh-suite-backend/src/test/java/com/meshsuite/venda/controller/VendaControllerTest.java
package com.meshsuite.venda.controller;

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
class VendaControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
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

        User vendedor = new User();
        vendedor.setTenantId(tenant.getId());
        vendedor.setName("Carla Vendedora");
        vendedor.setEmail("carla-" + codigo + "@" + codigo + ".com.br");
        vendedor.setPasswordHash("hash");
        vendedor.setRole(Role.SALES_REP);
        vendedor.setProfile(Profile.SALES);
        userRepository.saveAndFlush(vendedor);

        Parceiro cliente = new Parceiro();
        cliente.setTenantId(tenant.getId());
        cliente.setTipoPessoa(TipoPessoa.JURIDICA);
        cliente.setDocumento(cnpjEmpresa.equals("11222333000144") ? "55666777000155" : "11222333000144");
        cliente.setNomeFantasia("Mercado Silva");
        cliente.getPapeis().add(PapelParceiro.CLIENTE);
        parceiroRepository.saveAndFlush(cliente);

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

        Produto produto = new Produto();
        produto.setTenantId(tenant.getId());
        produto.setNome("Camiseta Polo");
        produto.setSku("P0001-" + codigo);
        produto.setPrecoVenda(new BigDecimal("59.90"));
        produto.setFiscalRegistration(registration);
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

    private String criarPedidoEmPreparo(Contexto ctx, Cookie cookie) throws Exception {
        String created = mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": [ { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 } ]
                                }
                                """.formatted(ctx.clienteId(), ctx.vendedorId(), ctx.produtoId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String pedidoId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/pedidos/" + pedidoId + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isOk());

        return pedidoId;
    }

    @Test
    void faturaListsAndFindsVenda() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        String pedidoId = criarPedidoEmPreparo(ctx, cookie);

        String created = mockMvc.perform(post("/api/vendas/faturar/" + pedidoId).cookie(cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.pedidoId").value(pedidoId))
                .andExpect(jsonPath("$.total").value(119.80))
                .andReturn().getResponse().getContentAsString();
        String vendaId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/vendas").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numero").value(1));

        mockMvc.perform(get("/api/vendas/" + vendaId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNome").value("Mercado Silva"));

        mockMvc.perform(get("/api/pedidos/" + pedidoId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FATURADO"));
    }

    @Test
    void faturingAPedidoStillInDigitadoIsBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
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
                                """.formatted(ctx.clienteId(), ctx.vendedorId(), ctx.produtoId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String pedidoId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/vendas/faturar/" + pedidoId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void faturingWithoutSalePermissionIsForbidden() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao-venda");
        tenant.setNome("sem-permissao-venda");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

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

        mockMvc.perform(get("/api/vendas").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

Run: `cd mesh-suite-backend && mvn -q test-compile`
Expected: `COMPILATION ERROR` — `VendaController` does not exist.

- [ ] **Step 3: Implement `VendaController`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/controller/VendaController.java
package com.meshsuite.venda.controller;

import com.meshsuite.venda.dto.VendaResponse;
import com.meshsuite.venda.dto.VendaSummaryResponse;
import com.meshsuite.venda.service.VendaService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vendas")
public class VendaController {

    private final VendaService vendaService;

    public VendaController(VendaService vendaService) {
        this.vendaService = vendaService;
    }

    @GetMapping
    public Page<VendaSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 10, sort = "numero", direction = Sort.Direction.DESC) Pageable pageable) {
        return vendaService.listar(busca, pageable);
    }

    @GetMapping("/{id}")
    public VendaResponse buscarPorId(@PathVariable UUID id) {
        return vendaService.buscarPorId(id);
    }

    @PostMapping("/faturar/{pedidoId}")
    public ResponseEntity<VendaResponse> faturar(@PathVariable UUID pedidoId) {
        VendaResponse response = vendaService.faturar(pedidoId);
        return ResponseEntity.status(201).body(response);
    }
}
```

- [ ] **Step 4: Implement `VendaExceptionHandler`** (protects against the race where two concurrent `faturar` calls both pass the `EM_PREPARO` check before either commits, hitting `venda.pedido_id`'s `UNIQUE` constraint)

```java
// mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaExceptionHandler.java
package com.meshsuite.venda.exception;

import com.meshsuite.venda.controller.VendaController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = VendaController.class)
public class VendaExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Não foi possível faturar o pedido. Tente novamente."));
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && mvn -q test -Dtest=VendaControllerTest`
Expected: `BUILD SUCCESS`, 3 tests passed.

- [ ] **Step 6: Run the full backend suite to confirm no regressions**

Run: `cd mesh-suite-backend && mvn -q clean test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/venda/controller/VendaController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/venda/exception/VendaExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/venda/controller/VendaControllerTest.java
git commit -m "feat(venda): add VendaController with faturar/listar/buscarPorId endpoints"
```

---

### Task 7: Frontend — `src/api/vendas.ts`

**Files:**
- Create: `mesh-suite-frontend/src/api/vendas.ts`

**Interfaces:**
- Consumes: `apiClient` from `./client` (existing, same as every other `src/api/*.ts` file).
- Produces: `VendaResponse`, `ItemVendaResponse`, `VendaSummary`, `Page<T>` types; `listarVendas(params: ListarVendasParams): Promise<Page<VendaSummary>>`, `buscarVenda(id: string): Promise<VendaResponse>`, `faturarPedido(pedidoId: string): Promise<VendaResponse>`.

- [ ] **Step 1: Create the API module**

```typescript
// mesh-suite-frontend/src/api/vendas.ts
import { apiClient } from './client'

export interface ItemVendaResponse {
  produtoId: string
  produtoNome: string
  quantidade: number
  valorUnitario: number
  valorTotal: number
  valorIcms: number
  valorIpi: number
  valorPis: number
  valorCofins: number
}

export interface VendaResponse {
  id: string
  numero: number
  pedidoId: string
  pedidoNumero: number
  clienteId: string
  clienteNome: string
  vendedorId: string
  vendedorNome: string
  dataEmissao: string
  desconto: number
  subtotal: number
  total: number
  valorIcms: number
  valorIpi: number
  valorPis: number
  valorCofins: number
  itens: ItemVendaResponse[]
}

export interface VendaSummary {
  id: string
  numero: number
  clienteNome: string
  dataEmissao: string
  total: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarVendasParams {
  busca?: string
  page?: number
  size?: number
  sort?: string
}

export async function listarVendas(params: ListarVendasParams): Promise<Page<VendaSummary>> {
  const { data } = await apiClient.get<Page<VendaSummary>>('/vendas', { params })
  return data
}

export async function buscarVenda(id: string): Promise<VendaResponse> {
  const { data } = await apiClient.get<VendaResponse>(`/vendas/${id}`)
  return data
}

export async function faturarPedido(pedidoId: string): Promise<VendaResponse> {
  const { data } = await apiClient.post<VendaResponse>(`/vendas/faturar/${pedidoId}`)
  return data
}
```

- [ ] **Step 2: Verify it type-checks**

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit`
Expected: no errors mentioning `src/api/vendas.ts`.

- [ ] **Step 3: Commit**

```bash
git add mesh-suite-frontend/src/api/vendas.ts
git commit -m "feat(venda): add vendas API client module"
```

---

### Task 8: Frontend — `VendasListView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/VendasListView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/VendasListView.spec.ts`

**Interfaces:**
- Consumes: `listarVendas` (Task 7), `AppShell`, `StatusBadge`, `Pagination` components (existing, same imports as `AccountsPayableListView.vue`).
- Produces: `VendasListView` component, mounted standalone (no route registered yet — that's Task 9).

- [ ] **Step 1: Write the failing component test**

```typescript
// mesh-suite-frontend/src/views/__tests__/VendasListView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import VendasListView from '@/views/VendasListView.vue'
import * as vendasApi from '@/api/vendas'

vi.mock('@/api/vendas')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/vendas', name: 'vendas', component: VendasListView }],
  })
  router.push('/vendas')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(VendasListView, { global: { plugins: [router] } }),
  }))
}

const venda = {
  id: 'v1', numero: 1, clienteNome: 'Mercado Silva', dataEmissao: '2026-08-08', total: 119.8,
}

describe('VendasListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(vendasApi.listarVendas).mockResolvedValue({
      content: [venda], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the venda list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('silva')
    await flushPromises()

    expect(vendasApi.listarVendas).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('shows an empty state when there are no vendas', async () => {
    vi.mocked(vendasApi.listarVendas).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma venda para exibir.')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(vendasApi.listarVendas).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de vendas.')
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run VendasListView`
Expected: FAIL — `Failed to resolve import "@/views/VendasListView.vue"`.

- [ ] **Step 3: Implement `VendasListView.vue`**

```vue
<!-- mesh-suite-frontend/src/views/VendasListView.vue -->
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
          <div class="table-grid-col table-grid-col-sortable" data-test="col-cliente" @click="toggleSort('clienteNome')">
            Cliente
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'clienteNome' }">{{ sortIcon('clienteNome') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-data" @click="toggleSort('dataEmissao')">
            Data de Emissão
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'dataEmissao' }">{{ sortIcon('dataEmissao') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-total" @click="toggleSort('total')">
            Total
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'total' }">{{ sortIcon('total') }}</span>
          </div>
        </div>

        <div v-for="venda in pagina.content" :key="venda.id" class="table-grid-row" :data-test="`row-${venda.id}`">
          <div class="table-grid-cell">{{ venda.numero }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ venda.clienteNome }}</div>
          <div class="table-grid-cell">{{ formatarData(venda.dataEmissao) }}</div>
          <div class="table-grid-cell">{{ formatarPreco(venda.total) }}</div>
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
import { listarVendas, type VendaSummary, type Page as ApiPage } from '@/api/vendas'

const filtros = reactive({ busca: '' })
const pagina = ref<ApiPage<VendaSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const sortField = ref<'clienteNome' | 'dataEmissao' | 'total' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function sortIcon(field: 'clienteNome' | 'dataEmissao' | 'total') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'clienteNome' | 'dataEmissao' | 'total') {
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
    pagina.value = await listarVendas({
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

Run: `cd mesh-suite-frontend && npx vitest run VendasListView`
Expected: 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/VendasListView.vue mesh-suite-frontend/src/views/__tests__/VendasListView.spec.ts
git commit -m "feat(venda): add VendasListView"
```

---

### Task 9: Frontend — router + sidebar entry

**Files:**
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Modify: `mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts`

**Interfaces:**
- Consumes: `VendasListView` (Task 8).
- Produces: route `{ path: '/vendas', name: 'vendas', component: VendasListView }`; sidebar nav item `Vendas` under the `vendas` group.

- [ ] **Step 1: Write the failing sidebar test**

Add this test to `AppSidebar.spec.ts` (same file already testing `nav-Pedidos` — follow that test's exact shape):

```typescript
  it('navigates to /vendas when Vendas is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nav-Vendas"]').trigger('click')

    expect(router.currentRoute.value.path).toBe('/vendas')
  })
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run AppSidebar`
Expected: FAIL — `[data-test="nav-Vendas"]` not found.

- [ ] **Step 3: Register the route**

In `mesh-suite-frontend/src/router/index.ts`, add the import next to `PedidosListView`:

```typescript
import VendasListView from '@/views/VendasListView.vue'
```

and add the route next to the `pedidos` routes:

```typescript
    { path: '/vendas', name: 'vendas', component: VendasListView },
```

- [ ] **Step 4: Add the sidebar item**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, change the `vendas` group's `items` array from:

```typescript
    items: [
      { icon: '📋', label: 'Pedidos', route: '/pedidos' },
      { icon: '💰', label: 'Tab. Preços', route: '/tabelas-preco' },
      { icon: '💳', label: 'Pagamentos', route: '/contas-a-pagar' },
    ],
```

to:

```typescript
    items: [
      { icon: '📋', label: 'Pedidos', route: '/pedidos' },
      { icon: '🧾', label: 'Vendas', route: '/vendas' },
      { icon: '💰', label: 'Tab. Preços', route: '/tabelas-preco' },
      { icon: '💳', label: 'Pagamentos', route: '/contas-a-pagar' },
    ],
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd mesh-suite-frontend && npx vitest run AppSidebar`
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/router/index.ts mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts
git commit -m "feat(venda): register /vendas route and sidebar entry"
```

---

### Task 10: Frontend — "Faturar" action on `PedidosListView.vue`

**Files:**
- Modify: `mesh-suite-frontend/src/views/PedidosListView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/PedidosListView.spec.ts`

**Interfaces:**
- Consumes: `faturarPedido` from `@/api/vendas` (Task 7).
- Produces: for a pedido whose `PROXIMO_STATUS` target is `FATURADO`, the Ações menu now shows a "Faturar" item calling `faturarPedido` instead of `avancarStatusPedido`.

- [ ] **Step 1: Write the failing test**

Add this fixture and test to `PedidosListView.spec.ts` (same file — `pedidoDigitado`/`pedidoFaturado` fixtures and `mountWithRouter` already exist there):

```typescript
const pedidoEmPreparo = {
  id: 'ped3', numero: 3, clienteNome: 'Confecções Bela Vista', vendedorNome: 'Carla Vendedora',
  dataPedido: '2026-08-01', total: 200.0, status: 'EM_PREPARO' as const,
}
```

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

(Note: this test spies on the real `@/api/vendas` module rather than `vi.mock`-ing it wholesale, since the spec file doesn't otherwise touch that module — importing it directly for `vi.spyOn` avoids adding a blanket top-level mock that could affect unrelated tests in the same file.)

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run PedidosListView`
Expected: FAIL — `[data-test="acao-faturar"]` not found (the item is still labeled "Avançar para Faturado" with `testId: 'acao-avancar'`).

- [ ] **Step 3: Update `PedidosListView.vue`**

Add the import next to the other `@/api/pedidos` import:

```typescript
import { faturarPedido } from '@/api/vendas'
```

Replace the `avancar` function and `acoesPara` function:

```typescript
async function avancar(pedido: PedidoSummary) {
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

async function faturar(pedido: PedidoSummary) {
  erro.value = ''
  try {
    await faturarPedido(pedido.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível faturar o pedido.'
  }
}

function acoesPara(pedido: PedidoSummary): ActionsMenuItem[] {
  const itens: ActionsMenuItem[] = [
    { label: 'Editar', action: () => editarPedido(pedido.id), testId: 'acao-editar' },
  ]
  const proximo = PROXIMO_STATUS[pedido.status]
  if (proximo === 'FATURADO') {
    itens.push({ label: 'Faturar', action: () => faturar(pedido), testId: 'acao-faturar' })
  } else if (proximo) {
    itens.push({ label: rotuloAvancar(pedido.status)!, action: () => avancar(pedido), testId: 'acao-avancar' })
  }
  itens.push({ label: 'Excluir', action: () => excluir(pedido), danger: true, testId: 'acao-excluir' })
  return itens
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run PedidosListView`
Expected: all tests pass, including the pre-existing `advances the status via the "Avançar para Em Preparo" Ações item` test (that one's pedido is `DIGITADO`, so `proximo === 'EM_PREPARO'`, which still goes through the `else if` branch unchanged) and `hides the "Avançar" item once a pedido is already Faturado` (that pedido's `proximo` is `null`, so neither branch fires — still correctly hidden).

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/PedidosListView.vue mesh-suite-frontend/src/views/__tests__/PedidosListView.spec.ts
git commit -m "feat(venda): replace Faturado status-advance action with Faturar in PedidosListView"
```

---

### Task 11: `SALE` permission wiring in the frontend

**Files:**
- Modify: `mesh-suite-frontend/src/api/users.ts`
- Modify: `mesh-suite-frontend/src/views/UserFormView.vue`

**Interfaces:**
- Consumes: nothing new.
- Produces: `ModuleName` now includes `'SALE'`; the permission grid in `UserFormView.vue` renders a `SALE` row; `DEFAULT_MATRIX` grants `SALE:VIEW`/`SALE:CREATE` by default to `ADMIN`, `MANAGER`, and `SALES` profiles, and `SALE:VIEW` to `VIEWER`.

- [ ] **Step 1: Add `'SALE'` to `ModuleName`**

In `mesh-suite-frontend/src/api/users.ts`, change:

```typescript
export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE' | 'PAYABLE'
```

to:

```typescript
export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE' | 'PAYABLE' | 'SALE'
```

- [ ] **Step 2: Add `SALE` to `MODULES`/`MODULE_LABELS`/`DEFAULT_MATRIX` in `UserFormView.vue`**

Change:

```typescript
const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER', 'PURCHASE', 'PAYABLE']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
  PURCHASE: 'Compras',
  PAYABLE: 'Contas a Pagar',
}
```

to:

```typescript
const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER', 'PURCHASE', 'PAYABLE', 'SALE']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
  PURCHASE: 'Compras',
  PAYABLE: 'Contas a Pagar',
  SALE: 'Vendas',
}
```

Change the `DEFAULT_MATRIX` (only `ADMIN`'s exclusion filter, and the `MANAGER`/`SALES`/`VIEWER` explicit lists, change):

```typescript
const DEFAULT_MATRIX: Record<Profile, Permission[]> = {
  ADMIN: [
    ...MODULES.flatMap((m) => ACTIONS.filter((a) =>
      !(m === 'USER' && a === 'DELETE') && !(m === 'PAYABLE' && (a === 'CREATE' || a === 'DELETE'))
        && !(m === 'SALE' && (a === 'EDIT' || a === 'DELETE')),
    ).map((a) => ({ module: m, action: a }))),
  ],
  MANAGER: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' }, { module: 'PRODUCT', action: 'CREATE' }, { module: 'PRODUCT', action: 'EDIT' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
    { module: 'PURCHASE', action: 'VIEW' }, { module: 'PURCHASE', action: 'CREATE' }, { module: 'PURCHASE', action: 'EDIT' },
    { module: 'PAYABLE', action: 'VIEW' }, { module: 'PAYABLE', action: 'EDIT' },
    { module: 'SALE', action: 'VIEW' }, { module: 'SALE', action: 'CREATE' },
    { module: 'USER', action: 'VIEW' },
  ],
  SALES: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
    { module: 'SALE', action: 'VIEW' }, { module: 'SALE', action: 'CREATE' },
  ],
  VIEWER: [
    { module: 'CUSTOMER', action: 'VIEW' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' },
    { module: 'PURCHASE', action: 'VIEW' },
    { module: 'PAYABLE', action: 'VIEW' },
    { module: 'SALE', action: 'VIEW' },
  ],
}
```

- [ ] **Step 3: Run the existing `UserFormView` test suite to confirm nothing broke**

Run: `cd mesh-suite-frontend && npx vitest run UserFormView`
Expected: all existing tests still pass (they assert specific `perm-{MODULE}-{ACTION}` checkboxes by `data-test`, none of which reference `SALE`, so adding a new row doesn't affect them).

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-frontend/src/api/users.ts mesh-suite-frontend/src/views/UserFormView.vue
git commit -m "feat(venda): add SALE to the permission module list and default matrix"
```

---

### Task 12: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`
Expected: `BUILD SUCCESS`. (Requires Docker for Testcontainers.)

- [ ] **Step 2: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all tests pass.

- [ ] **Step 3: Type-check the frontend**

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit`
Expected: no errors.

- [ ] **Step 4: Update `ORDEM-EXECUCAO.md`**

In `prd/ORDEM-EXECUCAO.md`, update row 4 (Vendas) to mark it done, mirroring how row 2 (Cadastro Comercial) already reads "**Concluído**":

Change:
```
| 4 | Vendas | `PRD-12-vendas.md` | Documento fiscal de saída (Venda), emitido a partir do Pedido faturado. Mesmo PRD do item 3; implementado em sequência dentro dele. |
```
to:
```
| 4 | Vendas | `PRD-12-vendas.md` | **Concluído** (faturamento de Pedido em Venda, 1:1, cálculo fiscal simplificado por item). Documento fiscal de saída (Venda), emitido a partir do Pedido faturado. Mesmo PRD do item 3; implementado em sequência dentro dele. Baixa de estoque e título a receber automáticos ficam para quando Estoque/Financeiro tiverem esse gancho desenhado — ver riscos na spec. |
```

- [ ] **Step 5: Commit**

```bash
git add prd/ORDEM-EXECUCAO.md
git commit -m "docs: mark Vendas as concluído in ORDEM-EXECUCAO.md"
```
