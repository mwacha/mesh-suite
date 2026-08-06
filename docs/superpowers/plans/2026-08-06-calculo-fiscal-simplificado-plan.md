# Cálculo Fiscal Simplificado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the backend a way to calculate ICMS/IPI/PIS/COFINS per item — a simplified subset of `PRD-11-fiscal-tributario.md` — so the future Compra (sub-project 5 of Compras) can register tax values per item without building the full NF-e/SPED domain.

**Architecture:** A standalone `FiscalRegistration` entity (RLS by tenant, same pattern as `purchase_order`/`stock_movement`/`accounts_payable`) holds tax rates by "natureza de operação". `Produto` gets an optional FK to it. `FiscalCalculationService` is a pure calculation service — no persistence of its own, no HTTP endpoint, no UI. It exists purely as a capability for the future Compra to call.

**Tech Stack:** Spring Boot 3.4.5 / Java 21, Spring Data JPA, PostgreSQL 16 (RLS), Flyway.

## Global Constraints

- Package for new code: `com.meshsuite.fiscal` (English naming, matching the `purchaseorder`/`stock`/`payable` convention already used in this initiative — no UI this round, so no Portuguese-local-variable convention applies here).
- RLS pattern: `fiscal_registration` gets its own `tenant_id` column, `ENABLE`+`FORCE ROW LEVEL SECURITY`, and a `USING`-only policy — exactly the pattern already used by `purchase_order`/`stock_movement`/`accounts_payable` (never `WITH CHECK`, per the codebase-wide convention).
- Calculation formula: base = `quantity × unitPrice`. Each of the 4 taxes = `base × (rate / 100)`, rounded to 2 decimal places with `RoundingMode.HALF_UP`. Implement the `/100` as `rate.movePointLeft(2)` (exact, no precision loss) rather than `BigDecimal.divide`, then round only the final multiplication result.
- No controller, no DTOs, no HTTP endpoint, no new `Module` permission value — there is no UI or external consumer in this slice.
- `Produto.fiscalRegistration` is a nullable FK — no migration backfill needed, no UI to set it. Tests set it directly via the repository, not through `ProdutoController`'s existing API (which is not modified in this plan).
- `FiscalCalculationServiceTest` is a plain JUnit5 unit test — it does NOT extend `AbstractIntegrationTest` and does NOT use Testcontainers, because `FiscalCalculationService.calculate(...)` takes an already-in-memory `FiscalRegistration` and performs no database access. This mirrors the existing precedent at `src/test/java/com/meshsuite/auth/RateLimiterTest.java` (plain `@Test`, no Spring context).
- `FiscalRegistrationRepositoryTest` uses tenant `codigo` values distinct from `"aurora"`/`"boreal"` (e.g. `"aurora-fiscal"`) — this is a deliberate deviation from the codebase-wide fixture convention, done specifically to reduce collision surface with a known, pre-existing, order-dependent test-infra bug (`DevSeedTest` permanently seeding a `codigo='aurora'` tenant into the shared Testcontainers database — documented during the Financeiro Mínimo sub-project). It is not a retroactive convention change for existing tests, only a new choice for this slice's own fixtures.

---

### Task 1: `FiscalRegistration` domain, `Produto` integration, `FiscalCalculationService`

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V17__create_fiscal_registration.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V18__add_fiscal_registration_to_produto.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/fiscal/FiscalRegistration.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/fiscal/FiscalRegistrationRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/fiscal/FiscalCalculationResult.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/fiscal/FiscalCalculationService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/Produto.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/fiscal/FiscalRegistrationRepositoryTest.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/fiscal/FiscalCalculationServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier Compras sub-projects — standalone. `Produto` (existing entity at `com.meshsuite.produto.Produto`) is modified in place.
- Produces: `FiscalRegistration` entity (fields: `id`, `tenantId`, `description`, `cfop`, `icmsCst`, `icmsRate`, `ipiRate`, `pisRate`, `cofinsRate`, `createdAt`); `FiscalRegistrationRepository extends JpaRepository<FiscalRegistration, UUID>`; `FiscalCalculationResult(BigDecimal icmsValue, BigDecimal ipiValue, BigDecimal pisValue, BigDecimal cofinsValue)` record; `FiscalCalculationService.calculate(FiscalRegistration registration, BigDecimal quantity, BigDecimal unitPrice): FiscalCalculationResult`; `Produto.fiscalRegistration` (nullable `FiscalRegistration` field). The future Compra (sub-project 5) will consume all of these directly.

- [ ] **Step 1: Write the migration creating `fiscal_registration`**

```sql
CREATE TABLE fiscal_registration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    description VARCHAR(255) NOT NULL,
    cfop VARCHAR(10),
    icms_cst VARCHAR(10),
    icms_rate NUMERIC(5,2) NOT NULL,
    ipi_rate NUMERIC(5,2) NOT NULL,
    pis_rate NUMERIC(5,2) NOT NULL,
    cofins_rate NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fiscal_registration_tenant_id ON fiscal_registration(tenant_id);

ALTER TABLE fiscal_registration ENABLE ROW LEVEL SECURITY;
ALTER TABLE fiscal_registration FORCE ROW LEVEL SECURITY;

CREATE POLICY fiscal_registration_tenant_isolation ON fiscal_registration
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

Save as `mesh-suite-backend/src/main/resources/db/migration/V17__create_fiscal_registration.sql`.

- [ ] **Step 2: Write the migration adding the FK column to `produto`**

```sql
ALTER TABLE produto ADD COLUMN fiscal_registration_id UUID REFERENCES fiscal_registration(id);
```

Save as `mesh-suite-backend/src/main/resources/db/migration/V18__add_fiscal_registration_to_produto.sql`.

- [ ] **Step 3: Write `FiscalRegistration.java`**

```java
package com.meshsuite.fiscal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fiscal_registration")
@Getter
@Setter
public class FiscalRegistration {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String description;

    @Column(length = 10)
    private String cfop;

    @Column(name = "icms_cst", length = 10)
    private String icmsCst;

    @Column(name = "icms_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal icmsRate;

    @Column(name = "ipi_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal ipiRate;

    @Column(name = "pis_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal pisRate;

    @Column(name = "cofins_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cofinsRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 4: Write `FiscalRegistrationRepository.java`**

```java
package com.meshsuite.fiscal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FiscalRegistrationRepository extends JpaRepository<FiscalRegistration, UUID> {
}
```

- [ ] **Step 5: Modify `Produto.java` to add the optional `fiscalRegistration` field**

In `mesh-suite-backend/src/main/java/com/meshsuite/produto/Produto.java`, add this import alongside the existing ones:

```java
import com.meshsuite.fiscal.FiscalRegistration;
```

Then add this field right after the existing `criadoEm` field (before the closing brace of the class):

```java

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_registration_id")
    private FiscalRegistration fiscalRegistration;
```

`jakarta.persistence.*` is already imported via wildcard in this file, so `ManyToOne`/`JoinColumn`/`FetchType` need no new imports.

- [ ] **Step 6: Write `FiscalRegistrationRepositoryTest.java` (RLS isolation)**

```java
package com.meshsuite.fiscal;

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

class FiscalRegistrationRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
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
        Tenant tenant = createTenant("aurora-fiscal");
        setTenantContext(tenant.getId());

        FiscalRegistration registration = new FiscalRegistration();
        registration.setTenantId(tenant.getId());
        registration.setDescription("Compra dentro do estado");
        registration.setCfop("1102");
        registration.setIcmsCst("000");
        registration.setIcmsRate(new BigDecimal("18.00"));
        registration.setIpiRate(new BigDecimal("5.00"));
        registration.setPisRate(new BigDecimal("1.65"));
        registration.setCofinsRate(new BigDecimal("7.60"));
        fiscalRegistrationRepository.saveAndFlush(registration);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM fiscal_registration")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 7: Run the RLS test to verify it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=FiscalRegistrationRepositoryTest`
Expected: PASS (1/1).

- [ ] **Step 8: Write the failing `FiscalCalculationServiceTest.java`**

```java
package com.meshsuite.fiscal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FiscalCalculationServiceTest {

    private final FiscalCalculationService service = new FiscalCalculationService();

    private FiscalRegistration registration(String icms, String ipi, String pis, String cofins) {
        FiscalRegistration registration = new FiscalRegistration();
        registration.setIcmsRate(new BigDecimal(icms));
        registration.setIpiRate(new BigDecimal(ipi));
        registration.setPisRate(new BigDecimal(pis));
        registration.setCofinsRate(new BigDecimal(cofins));
        return registration;
    }

    @Test
    void calculatesEachTaxAsPercentOfQuantityTimesUnitPrice() {
        FiscalRegistration registration = registration("18.00", "5.00", "1.65", "7.60");

        FiscalCalculationResult result = service.calculate(registration, new BigDecimal("10"), new BigDecimal("50.00"));

        assertThat(result.icmsValue()).isEqualByComparingTo("90.00");
        assertThat(result.ipiValue()).isEqualByComparingTo("25.00");
        assertThat(result.pisValue()).isEqualByComparingTo("8.25");
        assertThat(result.cofinsValue()).isEqualByComparingTo("38.00");
    }

    @Test
    void zeroRateProducesZeroTax() {
        FiscalRegistration registration = registration("0.00", "0.00", "0.00", "0.00");

        FiscalCalculationResult result = service.calculate(registration, new BigDecimal("10"), new BigDecimal("50.00"));

        assertThat(result.icmsValue()).isEqualByComparingTo("0.00");
        assertThat(result.ipiValue()).isEqualByComparingTo("0.00");
        assertThat(result.pisValue()).isEqualByComparingTo("0.00");
        assertThat(result.cofinsValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void roundsHalfUpToTwoDecimalPlaces() {
        FiscalRegistration registration = registration("33.33", "0.00", "0.00", "0.00");

        FiscalCalculationResult result = service.calculate(registration, new BigDecimal("1"), new BigDecimal("10.00"));

        // base = 10.00; 10.00 * 0.3333 = 3.3330 -> rounds HALF_UP to 3.33
        assertThat(result.icmsValue()).isEqualByComparingTo("3.33");
    }
}
```

- [ ] **Step 9: Run the test to verify it fails**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=FiscalCalculationServiceTest`
Expected: FAIL to compile — `FiscalCalculationService` does not exist yet.

- [ ] **Step 10: Write `FiscalCalculationService.java`**

```java
package com.meshsuite.fiscal;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FiscalCalculationService {

    public FiscalCalculationResult calculate(FiscalRegistration registration, BigDecimal quantity, BigDecimal unitPrice) {
        BigDecimal base = quantity.multiply(unitPrice);
        return new FiscalCalculationResult(
                applyRate(base, registration.getIcmsRate()),
                applyRate(base, registration.getIpiRate()),
                applyRate(base, registration.getPisRate()),
                applyRate(base, registration.getCofinsRate())
        );
    }

    private BigDecimal applyRate(BigDecimal base, BigDecimal ratePercent) {
        return base.multiply(ratePercent.movePointLeft(2)).setScale(2, RoundingMode.HALF_UP);
    }
}
```

Also create `FiscalCalculationResult.java`:

```java
package com.meshsuite.fiscal;

import java.math.BigDecimal;

public record FiscalCalculationResult(
        BigDecimal icmsValue,
        BigDecimal ipiValue,
        BigDecimal pisValue,
        BigDecimal cofinsValue
) {
}
```

- [ ] **Step 11: Run the test to verify it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=FiscalCalculationServiceTest`
Expected: PASS (3/3).

- [ ] **Step 12: Run the full backend suite to check for regressions**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: no NEW failures introduced by this task's diff. A known, pre-existing, order-dependent failure in `com.meshsuite.payable.*` (documented during the Financeiro Mínimo sub-project — `DevSeedTest`/`tenant_codigo_key` collision) may still appear; that is not caused by this task and is not a regression to chase here.

- [ ] **Step 13: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V17__create_fiscal_registration.sql \
        mesh-suite-backend/src/main/resources/db/migration/V18__add_fiscal_registration_to_produto.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/fiscal/FiscalRegistration.java \
        mesh-suite-backend/src/main/java/com/meshsuite/fiscal/FiscalRegistrationRepository.java \
        mesh-suite-backend/src/main/java/com/meshsuite/fiscal/FiscalCalculationResult.java \
        mesh-suite-backend/src/main/java/com/meshsuite/fiscal/FiscalCalculationService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/produto/Produto.java \
        mesh-suite-backend/src/test/java/com/meshsuite/fiscal/FiscalRegistrationRepositoryTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/fiscal/FiscalCalculationServiceTest.java
git commit -m "feat(fiscal): add FiscalRegistration, Produto link, and FiscalCalculationService"
```
