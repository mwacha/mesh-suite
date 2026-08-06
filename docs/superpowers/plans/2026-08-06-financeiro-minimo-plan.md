# Financeiro Mínimo (AccountsPayable) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add accounts-payable tracking — títulos created in batches by the future Compra sub-project, plus a simplified baixa/reversal (no caixa/conta bancária effect) with a real screen to drive it.

**Architecture:** New backend package `com.meshsuite.payable`, mirroring `PurchaseOrder`'s entity+counter+service+controller shape. `AccountsPayableService.createInstallments(...)` is a plain Java method with no HTTP caller yet (Compra will call it directly when built); `list`/`updateStatus` are the two externally-reachable, permission-gated operations, backing a new list-only Vue screen (no create form).

**Tech Stack:** Spring Boot 3.4.5/Java 21 backend, Vue 3 + TypeScript + Vite frontend, Postgres 16 RLS, Flyway migrations `V15`/`V16`.

## Global Constraints

- New backend package `com.meshsuite.payable` (English — new code). Table `accounts_payable`, migration `V15`. Migration `V16` widens the existing `user_permission_module_check` CHECK constraint to include `'PAYABLE'`.
- `AccountsPayable` fields: `id`, `tenantId`, `number` (own sequential number per tenant, via `AccountsPayableCounter` — same atomic `UPDATE ... RETURNING` pattern as `pedido_contador`/`purchase_order_counter`/`stock_movement`'s counters), `installmentNumber`/`totalInstallments` (parcela display, e.g. "2/3" — NOT a shared document number; each row gets its own `number`, installments are only grouped via `referenceId`), `supplier` (FK → `Parceiro`, must have `PapelParceiro.FORNECEDOR`), `amount`, `issueDate`, `dueDate`, `paymentDate` (nullable), `status` (`OPEN`/`PAID`), `referenceId` (nullable UUID — future Compra's id), `createdAt`.
- **No shared pagar/receber concept.** This plan builds only accounts payable — no `natureza` field, no accounts-receivable anything. A future receivables slice would be its own sub-project.
- **No conta bancária, no movimentação de caixa, no estorno-with-caixa-effect.** Baixa (`markAsPaid`, exposed as `updateStatus(id, PAID)`) only sets `status=PAID` and `paymentDate=LocalDate.now()` — no separate "valor pago" field, always quits the full `amount`. Reversal (`updateStatus(id, OPEN)`) clears `paymentDate` and returns to `OPEN` — unlike `PurchaseOrder`'s terminal `RECEIVED`/`CANCELLED` states, this reversal has no cap on how many times it can happen, since there's no external side effect (caixa/conta) to undo.
- `createInstallments(tenantId, supplierId, referenceId, installments: List<AccountsPayableInstallmentInput>)` carries no `@RequiresPermission` — internal service method, no direct HTTP caller, same role `StockService.adjustBalance` plays for Estoque. Only `list` (`VIEW`) and `updateStatus` (`EDIT`) are permission-gated: `@RequiresPermission(module = Module.PAYABLE, action = ...)`. New `Module.PAYABLE` enum value — named narrow (not `FINANCE`), since only accounts payable is being built.
- `PATCH /api/accounts-payable/{id}/status` takes only `{ "status": "OPEN" | "PAID" }` — same shape as `PurchaseOrderStatusRequest`. No `paymentDate` field in the request; the service always uses `LocalDate.now()` when transitioning to `PAID`, matching the screen's plain "Dar Baixa" button (no date picker).
- No write endpoint anywhere — `createInstallments` has no HTTP surface. No "Nova Conta a Pagar" button, no create form.
- Route: `GET /api/accounts-payable`, top-level, matching `/api/purchase-orders`'s and `/api/stock-movements`'s pattern.
- Frontend: `AccountsPayableListView.vue` (English file/type names, Portuguese local `<script setup>` variables and UI text — same convention as `PurchaseOrderFormView.vue`/`PurchaseOrdersListView.vue`). Route `/contas-a-pagar`, route name `contas-a-pagar`. **Reuses the existing, currently-inert "Pagamentos" sidebar entry** (`mesh-suite-frontend/src/components/AppSidebar.vue`, already has `{ icon: '💳', label: 'Pagamentos', route: null }`) by giving it a real route — do not add a new, separate sidebar entry.
- Unlike `Module.STOCK` (deliberately excluded from `UserFormView.vue`'s permission matrix, since Estoque has no screen), `Module.PAYABLE` **is** added to the matrix — this slice has a real screen and real user-facing actions (baixa/reversal), so an admin needs to be able to grant it.
- Spec: `docs/superpowers/specs/2026-08-06-financeiro-minimo-design.md`.

---

### Task 1: Backend — AccountsPayable domain model, migrations, and `AccountsPayableService`

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V15__create_accounts_payable.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V16__add_payable_to_user_permission_module_check.sql`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/Module.java` (add `PAYABLE`)
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableStatus.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayable.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableCounter.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableCounterRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableValidationException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/dto/AccountsPayableInstallmentInput.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/dto/AccountsPayableResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/dto/AccountsPayableStatusRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/payable/AccountsPayableRepositoryTest.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/payable/AccountsPayableServiceTest.java`

**Interfaces:**
- Consumes: `Parceiro`/`ParceiroRepository`/`PapelParceiro.FORNECEDOR` (existing, package `com.meshsuite.parceiro`).
- Produces: `AccountsPayableService.createInstallments(UUID tenantId, UUID supplierId, UUID referenceId, List<AccountsPayableInstallmentInput> installments): List<AccountsPayableResponse>`, `AccountsPayableService.list(AccountsPayableStatus status, Pageable pageable): Page<AccountsPayableResponse>`, `AccountsPayableService.updateStatus(UUID id, AccountsPayableStatus newStatus): AccountsPayableResponse`. Task 2 (`AccountsPayableController`) consumes `list` and `updateStatus` directly, with these exact signatures.

- [ ] **Step 1: Write the migrations**

`V15__create_accounts_payable.sql`:

```sql
CREATE TABLE accounts_payable_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE accounts_payable_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts_payable_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY accounts_payable_counter_tenant_isolation ON accounts_payable_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE accounts_payable (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    installment_number INTEGER NOT NULL,
    total_installments INTEGER NOT NULL,
    supplier_id UUID NOT NULL REFERENCES parceiro(id),
    amount NUMERIC(12,2) NOT NULL,
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date DATE NOT NULL,
    payment_date DATE,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PAID')),
    reference_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_accounts_payable_tenant_number ON accounts_payable(tenant_id, number);
CREATE INDEX idx_accounts_payable_tenant_id ON accounts_payable(tenant_id);
CREATE INDEX idx_accounts_payable_supplier_id ON accounts_payable(supplier_id);

ALTER TABLE accounts_payable ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts_payable FORCE ROW LEVEL SECURITY;

CREATE POLICY accounts_payable_tenant_isolation ON accounts_payable
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

`V16__add_payable_to_user_permission_module_check.sql`:

```sql
ALTER TABLE user_permission DROP CONSTRAINT user_permission_module_check;

ALTER TABLE user_permission ADD CONSTRAINT user_permission_module_check
    CHECK (module IN ('CUSTOMER','PRODUCT','ORDER','USER','PURCHASE','STOCK','PAYABLE'));
```

- [ ] **Step 2: Add `PAYABLE` to `Module.java`**

```java
package com.meshsuite.auth;

public enum Module {
    CUSTOMER,
    PRODUCT,
    ORDER,
    USER,
    PURCHASE,
    STOCK,
    PAYABLE
}
```

- [ ] **Step 3: Write `AccountsPayableStatus.java`**

```java
package com.meshsuite.payable;

public enum AccountsPayableStatus {
    OPEN,
    PAID
}
```

- [ ] **Step 4: Write `AccountsPayable.java`**

```java
package com.meshsuite.payable;

import com.meshsuite.parceiro.Parceiro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts_payable")
@Getter
@Setter
public class AccountsPayable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Integer number;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "total_installments", nullable = false)
    private Integer totalInstallments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Parceiro supplier;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate = LocalDate.now();

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountsPayableStatus status = AccountsPayableStatus.OPEN;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 5: Write `AccountsPayableCounter.java`**

```java
package com.meshsuite.payable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "accounts_payable_counter")
@Getter
@Setter
public class AccountsPayableCounter {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber = 1;
}
```

- [ ] **Step 6: Write the repositories**

`AccountsPayableRepository.java`:

```java
package com.meshsuite.payable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AccountsPayableRepository extends JpaRepository<AccountsPayable, UUID>, JpaSpecificationExecutor<AccountsPayable> {
}
```

`AccountsPayableCounterRepository.java`:

```java
package com.meshsuite.payable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountsPayableCounterRepository extends JpaRepository<AccountsPayableCounter, UUID> {
}
```

- [ ] **Step 7: Write the exceptions**

`AccountsPayableNotFoundException.java`:

```java
package com.meshsuite.payable;

public class AccountsPayableNotFoundException extends RuntimeException {
    public AccountsPayableNotFoundException() {
        super("Conta a pagar não encontrada");
    }
}
```

`AccountsPayableValidationException.java`:

```java
package com.meshsuite.payable;

public class AccountsPayableValidationException extends RuntimeException {
    public AccountsPayableValidationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 8: Write `AccountsPayableSpecifications.java`**

```java
package com.meshsuite.payable;

import org.springframework.data.jpa.domain.Specification;

public final class AccountsPayableSpecifications {

    private AccountsPayableSpecifications() {
    }

    public static Specification<AccountsPayable> withStatus(AccountsPayableStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
```

- [ ] **Step 9: Write the DTOs**

`dto/AccountsPayableInstallmentInput.java`:

```java
package com.meshsuite.payable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountsPayableInstallmentInput(BigDecimal amount, LocalDate dueDate) {
}
```

`dto/AccountsPayableResponse.java`:

```java
package com.meshsuite.payable.dto;

import com.meshsuite.payable.AccountsPayableStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountsPayableResponse(
        UUID id,
        Integer number,
        Integer installmentNumber,
        Integer totalInstallments,
        UUID supplierId,
        String supplierName,
        BigDecimal amount,
        LocalDate issueDate,
        LocalDate dueDate,
        LocalDate paymentDate,
        AccountsPayableStatus status,
        UUID referenceId,
        Instant createdAt) {
}
```

`dto/AccountsPayableStatusRequest.java`:

```java
package com.meshsuite.payable.dto;

import com.meshsuite.payable.AccountsPayableStatus;
import jakarta.validation.constraints.NotNull;

public record AccountsPayableStatusRequest(@NotNull AccountsPayableStatus status) {
}
```

- [ ] **Step 10: Write `AccountsPayableService.java`**

```java
package com.meshsuite.payable;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.payable.dto.*;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AccountsPayableService {

    private final AccountsPayableRepository accountsPayableRepository;
    private final ParceiroRepository parceiroRepository;
    private final EntityManager entityManager;

    public AccountsPayableService(AccountsPayableRepository accountsPayableRepository,
                                   ParceiroRepository parceiroRepository, EntityManager entityManager) {
        this.accountsPayableRepository = accountsPayableRepository;
        this.parceiroRepository = parceiroRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public List<AccountsPayableResponse> createInstallments(UUID tenantId, UUID supplierId, UUID referenceId,
                                                              List<AccountsPayableInstallmentInput> installments) {
        Parceiro supplier = findValidSupplier(supplierId);
        int total = installments.size();
        List<AccountsPayableResponse> result = new ArrayList<>();
        int installmentNumber = 1;
        for (AccountsPayableInstallmentInput input : installments) {
            AccountsPayable entry = new AccountsPayable();
            entry.setTenantId(tenantId);
            entry.setNumber(nextNumber(tenantId));
            entry.setInstallmentNumber(installmentNumber++);
            entry.setTotalInstallments(total);
            entry.setSupplier(supplier);
            entry.setAmount(input.amount());
            entry.setIssueDate(LocalDate.now());
            entry.setDueDate(input.dueDate());
            entry.setReferenceId(referenceId);
            result.add(toResponse(accountsPayableRepository.saveAndFlush(entry)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PAYABLE, action = Action.VIEW)
    public Page<AccountsPayableResponse> list(AccountsPayableStatus status, Pageable pageable) {
        Specification<AccountsPayable> spec = AccountsPayableSpecifications.withStatus(status);
        return accountsPayableRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional
    @RequiresPermission(module = Module.PAYABLE, action = Action.EDIT)
    public AccountsPayableResponse updateStatus(UUID id, AccountsPayableStatus newStatus) {
        AccountsPayable entry = findEntityById(id);
        if (newStatus == AccountsPayableStatus.PAID) {
            if (entry.getStatus() != AccountsPayableStatus.OPEN) {
                throw new AccountsPayableValidationException("Só é possível dar baixa em um título em aberto");
            }
            entry.setStatus(AccountsPayableStatus.PAID);
            entry.setPaymentDate(LocalDate.now());
        } else {
            if (entry.getStatus() != AccountsPayableStatus.PAID) {
                throw new AccountsPayableValidationException("Só é possível reverter a baixa de um título pago");
            }
            entry.setStatus(AccountsPayableStatus.OPEN);
            entry.setPaymentDate(null);
        }
        return toResponse(accountsPayableRepository.saveAndFlush(entry));
    }

    private AccountsPayable findEntityById(UUID id) {
        return accountsPayableRepository.findById(id).orElseThrow(AccountsPayableNotFoundException::new);
    }

    private Parceiro findValidSupplier(UUID supplierId) {
        Parceiro parceiro = parceiroRepository.findById(supplierId)
                .orElseThrow(() -> new AccountsPayableValidationException("Fornecedor não encontrado"));
        if (!parceiro.getPapeis().contains(PapelParceiro.FORNECEDOR)) {
            throw new AccountsPayableValidationException("O parceiro selecionado não tem o papel Fornecedor");
        }
        return parceiro;
    }

    // Atomic UPDATE ... RETURNING against the tenant's single
    // accounts_payable_counter row -- never COUNT(*)/MAX(number)+1. Same
    // pattern as PurchaseOrderService.nextNumber/PedidoService.proximoNumero.
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO accounts_payable_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE accounts_payable_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private AccountsPayableResponse toResponse(AccountsPayable e) {
        return new AccountsPayableResponse(e.getId(), e.getNumber(), e.getInstallmentNumber(), e.getTotalInstallments(),
                e.getSupplier().getId(), e.getSupplier().getNomeFantasia(), e.getAmount(), e.getIssueDate(),
                e.getDueDate(), e.getPaymentDate(), e.getStatus(), e.getReferenceId(), e.getCreatedAt());
    }
}
```

- [ ] **Step 11: Write the failing repository RLS test**

```java
package com.meshsuite.payable;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountsPayableRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired AccountsPayableRepository accountsPayableRepository;
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

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Parceiro fornecedor = new Parceiro();
        fornecedor.setTenantId(tenant.getId());
        fornecedor.setTipoPessoa(TipoPessoa.JURIDICA);
        fornecedor.setDocumento("11222333000144");
        fornecedor.setNomeFantasia("Tecidos Aurora");
        fornecedor.getPapeis().add(PapelParceiro.FORNECEDOR);
        fornecedor = parceiroRepository.saveAndFlush(fornecedor);

        AccountsPayable entry = new AccountsPayable();
        entry.setTenantId(tenant.getId());
        entry.setNumber(1);
        entry.setInstallmentNumber(1);
        entry.setTotalInstallments(1);
        entry.setSupplier(fornecedor);
        entry.setAmount(new BigDecimal("100.00"));
        entry.setDueDate(LocalDate.now().plusDays(30));
        accountsPayableRepository.saveAndFlush(entry);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM accounts_payable")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 12: Write the failing service test**

```java
package com.meshsuite.payable;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.payable.dto.AccountsPayableInstallmentInput;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class AccountsPayableServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired AccountsPayableService accountsPayableService;
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
        caller.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.EDIT));
        userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(caller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
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

    private UUID criarCliente(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p).getId();
    }

    @Test
    void createsInstallmentsWithSequentialNumberingAndCorrectParcela() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var installments = List.of(
                new AccountsPayableInstallmentInput(new BigDecimal("100.00"), LocalDate.now().plusDays(30)),
                new AccountsPayableInstallmentInput(new BigDecimal("100.00"), LocalDate.now().plusDays(60)),
                new AccountsPayableInstallmentInput(new BigDecimal("100.00"), LocalDate.now().plusDays(90)));

        var created = accountsPayableService.createInstallments(tenantId, supplierId, null, installments);

        assertThat(created).hasSize(3);
        assertThat(created.get(0).number()).isEqualTo(1);
        assertThat(created.get(1).number()).isEqualTo(2);
        assertThat(created.get(2).number()).isEqualTo(3);
        assertThat(created.get(0).installmentNumber()).isEqualTo(1);
        assertThat(created.get(0).totalInstallments()).isEqualTo(3);
        assertThat(created.get(2).installmentNumber()).isEqualTo(3);
        assertThat(created.get(0).status()).isEqualTo(AccountsPayableStatus.OPEN);
        assertThat(created.get(0).supplierName()).isEqualTo("Tecidos Aurora");
    }

    @Test
    void rejectsSupplierWithoutFornecedorPapel() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        var installments = List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now()));

        assertThrows(AccountsPayableValidationException.class,
                () -> accountsPayableService.createInstallments(tenantId, clienteId, null, installments));
    }

    @Test
    void marksAsPaidFromOpen() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));

        var paid = accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);

        assertThat(paid.status()).isEqualTo(AccountsPayableStatus.PAID);
        assertThat(paid.paymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void reversesFromPaid() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);

        var reverted = accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.OPEN);

        assertThat(reverted.status()).isEqualTo(AccountsPayableStatus.OPEN);
        assertThat(reverted.paymentDate()).isNull();
    }

    @Test
    void rejectsMarkingAsPaidTwice() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);

        assertThrows(AccountsPayableValidationException.class,
                () -> accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID));
    }

    @Test
    void rejectsReversingAnOpenEntry() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));

        assertThrows(AccountsPayableValidationException.class,
                () -> accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.OPEN));
    }

    @Test
    void reversalCanHappenMoreThanOnce() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));

        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.OPEN);
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);
        var revertedAgain = accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.OPEN);

        assertThat(revertedAgain.status()).isEqualTo(AccountsPayableStatus.OPEN);
    }

    @Test
    void listFiltersByStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now()),
                        new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);

        var openPage = accountsPayableService.list(AccountsPayableStatus.OPEN, PageRequest.of(0, 10));
        var paidPage = accountsPayableService.list(AccountsPayableStatus.PAID, PageRequest.of(0, 10));

        assertThat(openPage.getTotalElements()).isEqualTo(1);
        assertThat(paidPage.getTotalElements()).isEqualTo(1);
    }

    @Test
    void deniesListingWhenCallerLacksPayableViewPermission() {
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
        noPerms.setRole(Role.ADMINISTRATIVE);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(com.meshsuite.auth.PermissionDeniedException.class,
                () -> accountsPayableService.list(null, PageRequest.of(0, 10)));
    }
}
```

- [ ] **Step 13: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=AccountsPayableRepositoryTest,AccountsPayableServiceTest`
Expected: PASS (1/1 + 9/9).

- [ ] **Step 14: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V15__create_accounts_payable.sql \
        mesh-suite-backend/src/main/resources/db/migration/V16__add_payable_to_user_permission_module_check.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/Module.java \
        mesh-suite-backend/src/main/java/com/meshsuite/payable/ \
        mesh-suite-backend/src/test/java/com/meshsuite/payable/
git commit -m "feat(payable): add AccountsPayable ledger, Module.PAYABLE and AccountsPayableService"
```

---

### Task 2: Backend — `AccountsPayableController`, exception handling, integration tests

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableController.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/payable/AccountsPayableControllerTest.java`

**Interfaces:**
- Consumes: `AccountsPayableService.list(AccountsPayableStatus, Pageable): Page<AccountsPayableResponse>`, `AccountsPayableService.updateStatus(UUID, AccountsPayableStatus): AccountsPayableResponse`, `AccountsPayableService.createInstallments(...)` (Task 1, used directly by the test to seed data — there is no write endpoint to call instead).

- [ ] **Step 1: Write `AccountsPayableController.java`**

```java
package com.meshsuite.payable;

import com.meshsuite.payable.dto.AccountsPayableResponse;
import com.meshsuite.payable.dto.AccountsPayableStatusRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts-payable")
public class AccountsPayableController {

    private final AccountsPayableService accountsPayableService;

    public AccountsPayableController(AccountsPayableService accountsPayableService) {
        this.accountsPayableService = accountsPayableService;
    }

    @GetMapping
    public Page<AccountsPayableResponse> list(
            @RequestParam(required = false) AccountsPayableStatus status,
            @PageableDefault(size = 10, sort = "dueDate") Pageable pageable) {
        return accountsPayableService.list(status, pageable);
    }

    @PatchMapping("/{id}/status")
    public AccountsPayableResponse updateStatus(@PathVariable UUID id,
                                                 @Valid @RequestBody AccountsPayableStatusRequest request) {
        return accountsPayableService.updateStatus(id, request.status());
    }
}
```

- [ ] **Step 2: Register `AccountsPayableNotFoundException`/`AccountsPayableValidationException` in `GlobalExceptionHandler`**

Append these two handlers to the end of the class, right after the existing `handleStockValidation` method, before the final closing brace:

```java
    @ExceptionHandler(com.meshsuite.payable.AccountsPayableNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAccountsPayableNotFound(
            com.meshsuite.payable.AccountsPayableNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.payable.AccountsPayableValidationException.class)
    public ResponseEntity<Map<String, String>> handleAccountsPayableValidation(
            com.meshsuite.payable.AccountsPayableValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 3: Write the failing controller test**

```java
package com.meshsuite.payable;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.auth.Module;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.payable.dto.AccountsPayableInstallmentInput;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class AccountsPayableControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired AccountsPayableService accountsPayableService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, UUID tenantId, UUID supplierId) {
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
        userLogin.setName("Carlos");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMIN);
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.EDIT));
        userRepository.saveAndFlush(userLogin);

        Parceiro fornecedor = new Parceiro();
        fornecedor.setTenantId(tenant.getId());
        fornecedor.setTipoPessoa(TipoPessoa.JURIDICA);
        fornecedor.setDocumento(cnpjEmpresa.equals("11222333000144") ? "55666777000155" : "11222333000144");
        fornecedor.setNomeFantasia("Tecidos Aurora");
        fornecedor.getPapeis().add(PapelParceiro.FORNECEDOR);
        parceiroRepository.saveAndFlush(fornecedor);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Contexto(token, tenant.getId(), fornecedor.getId());
    }

    @Test
    void listsAndUpdatesStatusOfAccountsPayable() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        var created = accountsPayableService.createInstallments(ctx.tenantId(), ctx.supplierId(), null,
                List.of(new AccountsPayableInstallmentInput(new BigDecimal("50.00"), LocalDate.now().plusDays(30))));
        String id = created.get(0).id().toString();

        mockMvc.perform(get("/api/accounts-payable").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].amount").value(50.00))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));

        mockMvc.perform(patch("/api/accounts-payable/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(patch("/api/accounts-payable/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/accounts-payable/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/accounts-payable"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutPayableViewPermissionIsForbidden() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao");
        tenant.setNome("sem-permissao");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("sem-permissao Ltda");
        empresa.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(empresa);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Sem Permissão");
        user.setEmail("sem-permissao@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.VIEWER);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sem-permissao@aurora.com.br\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/accounts-payable").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=AccountsPayableControllerTest`
Expected: PASS (3/3).

- [ ] **Step 5: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS, no regressions anywhere.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/payable/AccountsPayableController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/payable/AccountsPayableControllerTest.java
git commit -m "feat(payable): add AccountsPayableController and status endpoint"
```

---

### Task 3: Frontend — API layer, `AccountsPayableListView.vue`, routing and sidebar

**Files:**
- Create: `mesh-suite-frontend/src/api/accountsPayable.ts`
- Create: `mesh-suite-frontend/src/views/AccountsPayableListView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/AccountsPayableListView.spec.ts`

**Interfaces:**
- Consumes: `GET /api/accounts-payable`, `PATCH /api/accounts-payable/{id}/status` (Task 2).
- Produces: `listAccountsPayable`, `updateAccountsPayableStatus` in `@/api/accountsPayable`, and the `/contas-a-pagar` route. Task 4 does not depend on anything from this task.

- [ ] **Step 1: Write `api/accountsPayable.ts`**

```ts
import { apiClient } from './client'

export type AccountsPayableStatus = 'OPEN' | 'PAID'

export interface AccountsPayable {
  id: string
  number: number
  installmentNumber: number
  totalInstallments: number
  supplierId: string
  supplierName: string
  amount: number
  issueDate: string
  dueDate: string
  paymentDate: string | null
  status: AccountsPayableStatus
  referenceId: string | null
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListAccountsPayableParams {
  status?: AccountsPayableStatus
  page?: number
  size?: number
}

export async function listAccountsPayable(params: ListAccountsPayableParams): Promise<Page<AccountsPayable>> {
  const { data } = await apiClient.get<Page<AccountsPayable>>('/accounts-payable', { params })
  return data
}

export async function updateAccountsPayableStatus(id: string, status: AccountsPayableStatus): Promise<void> {
  await apiClient.patch(`/accounts-payable/${id}/status`, { status })
}
```

- [ ] **Step 2: Write `AccountsPayableListView.vue`**

```vue
<template>
  <AppShell title="Contas a Pagar">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="OPEN">Em Aberto</option>
        <option value="PAID">Paga</option>
      </select>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nº</th>
            <th>Parcela</th>
            <th>Fornecedor</th>
            <th>Vencimento</th>
            <th>Valor</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="titulo in pagina.content" :key="titulo.id">
            <td>{{ titulo.number }}</td>
            <td>{{ titulo.installmentNumber }}/{{ titulo.totalInstallments }}</td>
            <td>{{ titulo.supplierName }}</td>
            <td>{{ formatarData(titulo.dueDate) }}</td>
            <td>{{ formatarPreco(titulo.amount) }}</td>
            <td><span class="badge" :class="`badge-${titulo.status}`">{{ statusLabel(titulo.status) }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(titulo.id, $event)"
              >
                Ações
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma conta a pagar para exibir.</p>
    </section>

    <Teleport to="body">
      <div
        v-if="tituloAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div v-if="tituloAcoesAtual.status === 'OPEN'" data-test="acao-baixa" @click="darBaixa(tituloAcoesAtual)">
          Dar Baixa
        </div>
        <div v-if="tituloAcoesAtual.status === 'PAID'" data-test="acao-reverter" @click="reverterBaixa(tituloAcoesAtual)">
          Reverter Baixa
        </div>
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
import AppShell from '@/components/AppShell.vue'
import {
  listAccountsPayable,
  updateAccountsPayableStatus,
  type AccountsPayable,
  type AccountsPayableStatus,
  type Page as ApiPage,
} from '@/api/accountsPayable'

const filtros = reactive({ status: '' })
const pagina = ref<ApiPage<AccountsPayable>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const tituloAcoesAtual = computed(() =>
  pagina.value.content.find((t) => t.id === acoesAbertas.value) ?? null,
)

const STATUS_LABEL: Record<AccountsPayableStatus, string> = {
  OPEN: 'Em Aberto',
  PAID: 'Paga',
}

function statusLabel(status: AccountsPayableStatus) {
  return STATUS_LABEL[status]
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
    pagina.value = await listAccountsPayable({
      status: (filtros.status || undefined) as AccountsPayableStatus | undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de contas a pagar.'
  }
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

async function darBaixa(titulo: AccountsPayable) {
  acoesAbertas.value = null
  erro.value = ''
  try {
    await updateAccountsPayableStatus(titulo.id, 'PAID')
    await carregar(pagina.value.number)
  } catch {
    erro.value = 'Não foi possível dar baixa na conta a pagar.'
  }
}

async function reverterBaixa(titulo: AccountsPayable) {
  acoesAbertas.value = null
  erro.value = ''
  try {
    await updateAccountsPayableStatus(titulo.id, 'OPEN')
    await carregar(pagina.value.number)
  } catch {
    erro.value = 'Não foi possível reverter a baixa da conta a pagar.'
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

.toolbar select {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  background: var(--pm-white);
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

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}

.badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.badge-OPEN {
  background: var(--pm-warning-bg, var(--pm-bg));
  color: var(--pm-warning, var(--pm-text-mid));
}

.badge-PAID {
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

- [ ] **Step 3: Add the `contas-a-pagar` route**

In `mesh-suite-frontend/src/router/index.ts`, add the import near the other view imports:

```ts
import AccountsPayableListView from '@/views/AccountsPayableListView.vue'
```

Add to the `routes` array, after the `compras-editar` route:

```ts
    { path: '/contas-a-pagar', name: 'contas-a-pagar', component: AccountsPayableListView },
```

- [ ] **Step 4: Activate the existing "Pagamentos" sidebar item**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, the `navItems` array already has an inert entry for this screen — change only its `route` field, nothing else:

```ts
const navItems: NavItem[] = [
  { icon: '🏠', label: 'Home', route: '/' },
  { icon: '👥', label: 'Clientes', route: '/clientes' },
  { icon: '📥', label: 'Compras', route: '/compras' },
  { icon: '🏢', label: 'Empresa', route: null },
  { icon: '🏷', label: 'Marcas', route: null },
  { icon: '💳', label: 'Pagamentos', route: '/contas-a-pagar' },
  { icon: '📋', label: 'Pedidos', route: '/pedidos' },
  { icon: '🔒', label: 'Permissões', route: null },
  { icon: '📦', label: 'Produtos', route: '/produtos' },
  { icon: '💰', label: 'Tab. Preços', route: null },
  { icon: '👤', label: 'Usuários', route: '/usuarios' },
]
```

Do not rename the "Pagamentos" label and do not add a new, separate entry — this is the existing placeholder gaining a real route.

- [ ] **Step 5: Write the failing view test**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AccountsPayableListView from '@/views/AccountsPayableListView.vue'
import * as accountsPayableApi from '@/api/accountsPayable'

vi.mock('@/api/accountsPayable')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/contas-a-pagar', name: 'contas-a-pagar', component: AccountsPayableListView },
    ],
  })
  router.push('/contas-a-pagar')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(AccountsPayableListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const tituloAberto = {
  id: 'ap1', number: 1, installmentNumber: 1, totalInstallments: 3, supplierId: 'f1',
  supplierName: 'Tecidos Aurora', amount: 50.0, issueDate: '2026-08-06', dueDate: '2026-09-05',
  paymentDate: null, status: 'OPEN' as const, referenceId: null, createdAt: '2026-08-06T10:00:00Z',
}

const tituloPago = {
  id: 'ap2', number: 2, installmentNumber: 2, totalInstallments: 3, supplierId: 'f1',
  supplierName: 'Tecidos Aurora', amount: 50.0, issueDate: '2026-08-06', dueDate: '2026-10-05',
  paymentDate: '2026-08-10', status: 'PAID' as const, referenceId: null, createdAt: '2026-08-06T10:00:00Z',
}

describe('AccountsPayableListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(accountsPayableApi.listAccountsPayable).mockResolvedValue({
      content: [tituloAberto], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the accounts payable list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('1/3')
  })

  it('re-fetches with the status filter when it changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('select').setValue('PAID')
    await flushPromises()

    expect(accountsPayableApi.listAccountsPayable).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'PAID' }))
  })

  it('gives baixa via the Ações menu', async () => {
    vi.mocked(accountsPayableApi.updateAccountsPayableStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-baixa"]').trigger('click')
    await flushPromises()

    expect(accountsPayableApi.updateAccountsPayableStatus).toHaveBeenCalledWith('ap1', 'PAID')
  })

  it('reverses a baixa via the Ações menu', async () => {
    vi.mocked(accountsPayableApi.listAccountsPayable).mockResolvedValue({
      content: [tituloPago], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(accountsPayableApi.updateAccountsPayableStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-reverter"]').trigger('click')
    await flushPromises()

    expect(accountsPayableApi.updateAccountsPayableStatus).toHaveBeenCalledWith('ap2', 'OPEN')
  })

  it('hides the baixa action for an already-paid entry and the reversal action for an open one', async () => {
    vi.mocked(accountsPayableApi.listAccountsPayable).mockResolvedValue({
      content: [tituloPago], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')

    expect(wrapper.find('[data-test="acao-baixa"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="acao-reverter"]').exists()).toBe(true)
  })

  it('shows an empty state when there are no accounts payable', async () => {
    vi.mocked(accountsPayableApi.listAccountsPayable).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma conta a pagar para exibir.')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(accountsPayableApi.listAccountsPayable).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de contas a pagar.')
  })
})
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/AccountsPayableListView.spec.ts`
Expected: PASS (7/7).

- [ ] **Step 7: Run the full frontend suite to check for regressions**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all test files pass, no regressions. In particular, `AppSidebar.spec.ts`'s existing tests must still pass — none of them assert on the "Pagamentos" item's previous `route: null` state, so giving it a real route should not break anything, but confirm this by reading the test file if any failure appears here.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-frontend/src/api/accountsPayable.ts \
        mesh-suite-frontend/src/views/AccountsPayableListView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/views/__tests__/AccountsPayableListView.spec.ts
git commit -m "feat(payable): add AccountsPayableListView, activate Pagamentos sidebar nav"
```

---

### Task 4: Frontend — Add `PAYABLE` to the permission system

**Files:**
- Modify: `mesh-suite-frontend/src/api/users.ts`
- Modify: `mesh-suite-frontend/src/views/UserFormView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts`

**Interfaces:**
- Consumes: `Module.PAYABLE` (backend, Task 1) — the frontend's `ModuleName` union must include `'PAYABLE'` to type-check permission payloads sent to `POST/PUT /api/users`.

- [ ] **Step 1: Add `'PAYABLE'` to `ModuleName` in `api/users.ts`**

```ts
export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE' | 'PAYABLE'
```

- [ ] **Step 2: Add `PAYABLE` to `UserFormView.vue`'s `MODULES`, `MODULE_LABELS` and `DEFAULT_MATRIX`**

```ts
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

`AccountsPayable` only implements `VIEW`/`EDIT` on the backend (no `CREATE`/`DELETE` endpoint exists at all — creation is internal-only, there is no delete). `DEFAULT_MATRIX.ADMIN`'s existing `flatMap` filter already has a precedent for excluding an action that doesn't apply to a specific module (`!(m === 'USER' && a === 'DELETE')`) — extend it to also exclude `PAYABLE`'s `CREATE` and `DELETE`:

```ts
const DEFAULT_MATRIX: Record<Profile, Permission[]> = {
  ADMIN: [
    ...MODULES.flatMap((m) => ACTIONS.filter((a) =>
      !(m === 'USER' && a === 'DELETE') && !(m === 'PAYABLE' && (a === 'CREATE' || a === 'DELETE')),
    ).map((a) => ({ module: m, action: a }))),
  ],
  MANAGER: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' }, { module: 'PRODUCT', action: 'CREATE' }, { module: 'PRODUCT', action: 'EDIT' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
    { module: 'PURCHASE', action: 'VIEW' }, { module: 'PURCHASE', action: 'CREATE' }, { module: 'PURCHASE', action: 'EDIT' },
    { module: 'PAYABLE', action: 'VIEW' }, { module: 'PAYABLE', action: 'EDIT' },
    { module: 'USER', action: 'VIEW' },
  ],
  SALES: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
  ],
  VIEWER: [
    { module: 'CUSTOMER', action: 'VIEW' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' },
    { module: 'PURCHASE', action: 'VIEW' },
    { module: 'PAYABLE', action: 'VIEW' },
  ],
}
```

`SALES` deliberately gets no `PAYABLE` grants — a sales rep has no business role in accounts payable.

- [ ] **Step 3: Add a permission-grid regression test**

Add this test to `mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts`, alongside the existing `PURCHASE`-in-the-grid test added for Ordem de Compra (same file, same `mountWithRouter` helper, same `data-test="profile"` selector):

```ts
  it('includes Contas a Pagar in the permission grid, pre-checked for Admin but without Create/Delete', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="profile"]').setValue('ADMIN')
    await wrapper.find('[data-test="profile"]').trigger('change')

    expect(wrapper.text()).toContain('Contas a Pagar')
    expect((wrapper.find('[data-test="perm-PAYABLE-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PAYABLE-EDIT"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PAYABLE-CREATE"]').element as HTMLInputElement).checked).toBe(false)
    expect((wrapper.find('[data-test="perm-PAYABLE-DELETE"]').element as HTMLInputElement).checked).toBe(false)
  })
```

If the existing test file's helper is not named `mountWithRouter` or the profile select does not carry `data-test="profile"`, read the existing file first and match its actual helper name and selectors instead of introducing new ones.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/UserFormView.spec.ts`
Expected: PASS, including the new test.

- [ ] **Step 5: Run the full frontend suite and the full backend suite to check for regressions**

Run: `cd mesh-suite-frontend && npx vitest run`
Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS / all tests pass on both, no regressions anywhere.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/api/users.ts \
        mesh-suite-frontend/src/views/UserFormView.vue \
        mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts
git commit -m "feat(payable): add PAYABLE module to the permission grid and default matrix"
```
