# Rename Parceiro → Partner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the `Parceiro` module (business-partner entity: customer/supplier/carrier) to `Partner` across Java code, the database schema, and the one frontend file that owns it directly — sub-project 3 of the "rename to English" initiative — while leaving every end-customer-visible string (error messages, UI labels, Vue Router paths) in Portuguese.

**Architecture:** Mechanical, byte-for-byte-mappable rename following the exact identifier map in `docs/superpowers/specs/2026-08-10-rename-parceiro-to-partner-design.md`. No business logic changes. Ordered bottom-up: DB schema and dependent migrations first, then domain/repository, then DTOs/exceptions/service, then controller, then the 12 files in other backend modules that consume `Parceiro` as a fixture/relation, then the two frontend files this module owns, then the 6 frontend files that merely call into it, then a full-suite verification pass.

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / PostgreSQL 16 / Flyway (backend); Vue 3 / TypeScript / Vitest (frontend).

## Global Constraints

- Full identifier map (classes, fields, enum values, DB columns): see `docs/superpowers/specs/2026-08-10-rename-parceiro-to-partner-design.md` sections 3. Every task below implements a slice of that map — treat the spec as the source of truth for any value not repeated here.
- End-customer-visible text (error message strings, Vue Router paths like `/clientes`, UI labels like "Clientes", "Ativo", "Em Risco", "Bloqueado", "Ativar", "Bloquear") stays in Portuguese, unchanged, in every task.
- Test method names DO get translated to English as part of this rename (matches the `CompanyRepositoryTest` precedent from the Empresa→Company sub-project) — this applies specifically to `ParceiroServiceTest.java`, whose 16 test method names are still fully Portuguese.
- The JSON error-envelope key `"mensagem"` used in every `Map.of("mensagem", ...)` across the codebase is NOT touched by this plan — it's a project-wide convention, out of scope (same reasoning as the `empresa_id` JWT claim key deferred in the Empresa sub-project).
- `mesh-suite-backend/src/main/resources/application.yml` has `jpa.hibernate.ddl-auto: validate` — Hibernate validates every `@Entity`'s columns against the live DB schema at Spring context boot. This means Task 1 (schema rename) alone leaves the app **unable to boot** until Task 2 (domain rename) lands — this is expected and matches the same shape used successfully in the Empresa→Company sub-project. Task 1's own verification is therefore compile/grep-based only; the first task that boots the full Spring context is Task 2, and it will fail loudly (not silently) if Task 1 missed a reference.
- Local Postgres must be reset (`docker compose down -v && docker compose up -d`, or equivalent) before running any test from Task 2 onward, because migration `V5` is edited in place rather than superseded by a new migration — same greenfield pattern used in both prior rename sub-projects.
- Known pre-existing, unrelated flake (confirmed on `main`, not caused by any rename sub-project): a full `mvn clean test` run shows 0 failures but 15 errors — 12 in `com.meshsuite.payable.*`, 3 in `CompanyRepositoryTest` — both caused by dev-seed data (`R__seed_dev_tenant.sql`, tenant codigo `aurora`/`boreal`) colliding with hardcoded fixture literals in those test classes when the whole suite shares one Postgres container. Do not attempt to fix this as part of this plan; Task 10's full-suite verification step must reproduce exactly this signature (or a variant explained by this plan's own changes) and treat it as passing.

---

### Task 1: Database schema — rename `parceiro`→`partner`, fix dependent FKs, rewrite seed data

**Files:**
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V5__create_parceiro.sql` → rename to `V5__create_partner.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V7__create_pedido.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V9__create_user_permission.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V11__create_purchase_order.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V15__create_accounts_payable.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql`
- Modify: `mesh-suite-backend/src/main/resources/db/seed/R__seed_dev_test_clientes.sql`

**Interfaces:**
- Produces: DB table `partner` (columns: `id`, `tenant_id`, `person_type`, `document`, `trade_name`, `legal_name`, `status`, `billing_emails`, `whatsapp`, `tax_indicator`, `state_registration`, `municipal_registration`, `suframa_registration`, `zip_code`, `street`, `number`, `neighborhood`, `complement`, `state`, `city`, `notes`, `created_at`), table `partner_role` (`partner_id`, `role`), table `partner_contact` (`id`, `partner_id`, `name`, `email`, `business_phone`, `mobile_phone`, `job_title`) — consumed by Task 2's `Partner`/`PartnerContact`/`PartnerRepository`.

- [ ] **Step 1: Rename and rewrite the V5 migration**

```bash
git mv mesh-suite-backend/src/main/resources/db/migration/V5__create_parceiro.sql \
       mesh-suite-backend/src/main/resources/db/migration/V5__create_partner.sql
```

Replace the entire content of `V5__create_partner.sql` with:

```sql
CREATE TABLE partner (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    person_type VARCHAR(20) NOT NULL CHECK (person_type IN ('INDIVIDUAL','LEGAL_ENTITY')),
    document VARCHAR(14) NOT NULL,
    trade_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255),
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','AT_RISK','BLOCKED')),
    billing_emails VARCHAR(500),
    whatsapp VARCHAR(20),
    tax_indicator VARCHAR(20) CHECK (tax_indicator IN ('NON_TAXPAYER','TAXPAYER','EXEMPT_TAXPAYER')),
    state_registration VARCHAR(20),
    municipal_registration VARCHAR(20),
    suframa_registration VARCHAR(20),
    zip_code VARCHAR(8),
    street VARCHAR(255),
    number VARCHAR(20),
    neighborhood VARCHAR(100),
    complement VARCHAR(100),
    state VARCHAR(2),
    city VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_partner_tenant_document ON partner(tenant_id, document);
CREATE INDEX idx_partner_tenant_id ON partner(tenant_id);

ALTER TABLE partner ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner FORCE ROW LEVEL SECURITY;

CREATE POLICY partner_tenant_isolation ON partner
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE partner_role (
    partner_id UUID NOT NULL REFERENCES partner(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','SUPPLIER','CARRIER')),
    PRIMARY KEY (partner_id, role)
);

ALTER TABLE partner_role ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner_role FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- partner row's own RLS policy, matched by partner_id. This keeps the
-- Hibernate @ElementCollection mapping on Partner.roles simple (just
-- partner_id + role, nothing extra for the app to populate on insert).
CREATE POLICY partner_role_tenant_isolation ON partner_role
    USING (EXISTS (
        SELECT 1 FROM partner p
        WHERE p.id = partner_role.partner_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));


CREATE TABLE partner_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id UUID NOT NULL REFERENCES partner(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    business_phone VARCHAR(20),
    mobile_phone VARCHAR(20),
    job_title VARCHAR(100)
);

CREATE INDEX idx_partner_contact_partner_id ON partner_contact(partner_id);

ALTER TABLE partner_contact ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner_contact FORCE ROW LEVEL SECURITY;

CREATE POLICY partner_contact_tenant_isolation ON partner_contact
    USING (EXISTS (
        SELECT 1 FROM partner p
        WHERE p.id = partner_contact.partner_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

- [ ] **Step 2: Fix the FK reference and comment in `V7__create_pedido.sql`**

Change (around line 17):
```sql
    cliente_id UUID NOT NULL REFERENCES parceiro(id),
```
to:
```sql
    cliente_id UUID NOT NULL REFERENCES partner(id),
```

Change the comment (around line 55, inside the `item_pedido` RLS section):
```sql
-- row's own RLS policy, matched by pedido_id. Same pattern as parceiro_contato.
```
to:
```sql
-- row's own RLS policy, matched by pedido_id. Same pattern as partner_contact.
```

- [ ] **Step 3: Fix the comment in `V9__create_user_permission.sql`**

Change (around line 12):
```sql
-- row's own RLS policy, matched by user_id. Same pattern as parceiro_papel.
```
to:
```sql
-- row's own RLS policy, matched by user_id. Same pattern as partner_role.
```

- [ ] **Step 4: Fix the FK reference and comment in `V11__create_purchase_order.sql`**

Change (around line 17):
```sql
    supplier_id UUID NOT NULL REFERENCES parceiro(id),
```
to:
```sql
    supplier_id UUID NOT NULL REFERENCES partner(id),
```

Change the comment (around line 56):
```sql
-- pattern as item_pedido/parceiro_contato.
```
to:
```sql
-- pattern as item_pedido/partner_contact.
```

- [ ] **Step 5: Fix the FK reference in `V15__create_accounts_payable.sql`**

Change (around line 19):
```sql
    supplier_id UUID NOT NULL REFERENCES parceiro(id),
```
to:
```sql
    supplier_id UUID NOT NULL REFERENCES partner(id),
```

- [ ] **Step 6: Fix the FK reference in `V26__create_sale.sql`**

Change (around line 19):
```sql
    customer_id UUID NOT NULL REFERENCES parceiro(id),
```
to:
```sql
    customer_id UUID NOT NULL REFERENCES partner(id),
```

- [ ] **Step 7: Rewrite the seed script `R__seed_dev_test_clientes.sql`**

Replace the entire file content with:

```sql
-- Bulk test data for the Clientes list screen -- enough rows/variety (UF,
-- cidade, status, tipo de documento) to exercise pagination (numbered pages,
-- page-size selector) and the "Mais filtros" multi-select panel. Dev/test
-- profile only (see application.yml's flyway.locations override).
--
-- Deterministic documentos in the 900... range (unlikely to collide with
-- other seed/test data) + ON CONFLICT DO NOTHING on the tenant+document
-- unique index make this safe to re-run when Flyway detects a checksum
-- change on this repeatable migration.
SET LOCAL app.tenant_id = '11111111-1111-1111-1111-111111111111';

-- Two separate statements (not one writable-CTE chaining both inserts):
-- a single statement shares one snapshot across all its parts, so the
-- partner_role RLS policy's cross-table EXISTS check can't see partner
-- rows inserted earlier in that same statement. Splitting them lets the
-- second statement start a fresh snapshot (read committed) that does.
WITH origem AS (
    SELECT
        n,
        (ARRAY['SP','SP','SP','RJ','RJ','MG','MG','PR','RS','BA','PE','CE','DF','SC','GO'])[1 + (n % 15)] AS state,
        (ARRAY['São Paulo','Campinas','Santos','Rio de Janeiro','Niterói','Belo Horizonte','Bicas',
               'Curitiba','Porto Alegre','Salvador','Recife','Fortaleza','Brasília','Florianópolis','Goiânia']
        )[1 + (n % 15)] AS city,
        CASE WHEN n % 2 = 0 THEN 'LEGAL_ENTITY' ELSE 'INDIVIDUAL' END AS person_type,
        CASE
            WHEN n % 5 = 0 THEN 'BLOCKED'
            WHEN n % 5 = 1 THEN 'AT_RISK'
            ELSE 'ACTIVE'
        END AS status
    FROM generate_series(1, 62) AS n
)
INSERT INTO partner (
    tenant_id, person_type, document, trade_name, legal_name, status,
    whatsapp, state, city
)
SELECT
    '11111111-1111-1111-1111-111111111111',
    o.person_type,
    CASE WHEN o.person_type = 'LEGAL_ENTITY'
        THEN lpad((90000000000000 + o.n)::text, 14, '0')
        ELSE lpad((90000000000 + o.n)::text, 11, '0')
    END,
    'Cliente Teste ' || lpad(o.n::text, 3, '0'),
    CASE WHEN o.person_type = 'LEGAL_ENTITY' THEN 'Cliente Teste ' || lpad(o.n::text, 3, '0') || ' Ltda' END,
    o.status,
    '(11) 99' || lpad(o.n::text, 3, '0') || '-' || lpad((o.n * 37 % 10000)::text, 4, '0'),
    o.state,
    o.city
FROM origem o
ON CONFLICT (tenant_id, document) DO NOTHING;

INSERT INTO partner_role (partner_id, role)
SELECT id, 'CUSTOMER'
FROM partner
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
  AND trade_name LIKE 'Cliente Teste %'
ON CONFLICT DO NOTHING;
```

Note: the business-data text `'Cliente Teste 001'` etc. stays in Portuguese — it's sample content shown in a dev-only screen, not a code identifier.

- [ ] **Step 8: Verify no `parceiro`/`Parceiro`-shaped SQL references remain**

Run:
```bash
grep -rn "parceiro" mesh-suite-backend/src/main/resources
```
Expected: no output (all six files above are the complete set from the design spec's audit).

Run:
```bash
cd mesh-suite-backend && mvn -q compile
```
Expected: `BUILD SUCCESS` (migrations are resources, not compiled — this just confirms nothing else broke). Do NOT run `mvn test` yet — Hibernate's `ddl-auto: validate` will fail every test until Task 2 renames the `Parceiro`/`ParceiroContato` entities to match this new schema. That is expected; Task 2 is the first task that boots the full Spring context.

- [ ] **Step 9: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V5__create_partner.sql \
        mesh-suite-backend/src/main/resources/db/migration/V7__create_pedido.sql \
        mesh-suite-backend/src/main/resources/db/migration/V9__create_user_permission.sql \
        mesh-suite-backend/src/main/resources/db/migration/V11__create_purchase_order.sql \
        mesh-suite-backend/src/main/resources/db/migration/V15__create_accounts_payable.sql \
        mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql \
        mesh-suite-backend/src/main/resources/db/seed/R__seed_dev_test_clientes.sql
git commit -m "refactor(partner): rename V5 migration parceiro->partner, fix dependent FKs, rewrite seed data"
```

---

### Task 2: Domain, enums, repository, repository test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/Partner.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/PartnerContact.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/enums/PartnerRole.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/enums/PartnerStatus.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/enums/PersonType.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/enums/TaxIndicator.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/repository/PartnerRepository.java`
- Delete (via `git mv` to the Create paths below): `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/domain/Parceiro.java`, `ParceiroContato.java`, the 4 files under `parceiro/domain/enums/`, `parceiro/repository/ParceiroRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/partner/repository/PartnerRepositoryTest.java` (via `git mv` from `parceiro/repository/ParceiroRepositoryTest.java`)

**Interfaces:**
- Produces: `Partner` (fields: `id`, `tenantId`, `personType: PersonType`, `document`, `tradeName`, `legalName`, `status: PartnerStatus`, `roles: Set<PartnerRole>`, `billingEmails`, `whatsapp`, `taxIndicator: TaxIndicator`, `stateRegistration`, `municipalRegistration`, `suframaRegistration`, `zipCode`, `street`, `number`, `neighborhood`, `complement`, `state`, `city`, `notes`, `createdAt`, `contacts: List<PartnerContact>`); `PartnerContact` (fields: `id`, `partner: Partner`, `name`, `email`, `businessPhone`, `mobilePhone`, `jobTitle`); `PartnerRepository` (`findByTenantId` does not exist on this repository — do not add it; only the 4 methods below); `PartnerRole {CUSTOMER, SUPPLIER, CARRIER}`; `PartnerStatus {ACTIVE, AT_RISK, BLOCKED}`; `PersonType {INDIVIDUAL, LEGAL_ENTITY}`; `TaxIndicator {NON_TAXPAYER, TAXPAYER, EXEMPT_TAXPAYER}` — all consumed by Task 3 (DTOs/service) and Task 5's bridge patches.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/domain/Parceiro.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/domain/ParceiroContato.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/domain/enums/PapelParceiro.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/domain/enums/StatusParceiro.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/domain/enums/TipoPessoa.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/domain/enums/IndicadorIe.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/repository/ParceiroRepository.java
git rm mesh-suite-backend/src/test/java/com/meshsuite/parceiro/repository/ParceiroRepositoryTest.java
```

(`git rm` here, not `git mv`, because every file's content changes non-trivially — package, class name, field names — so a literal move-then-edit is clearer than trying to preserve a rename detection that Git likely wouldn't recognize as a pure rename anyway.)

- [ ] **Step 2: Create the four enums**

`mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/enums/PartnerRole.java`:
```java
package com.meshsuite.partner.domain.enums;

public enum PartnerRole {
    CUSTOMER,
    SUPPLIER,
    CARRIER
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/enums/PartnerStatus.java`:
```java
package com.meshsuite.partner.domain.enums;

public enum PartnerStatus {
    ACTIVE,
    AT_RISK,
    BLOCKED
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/enums/PersonType.java`:
```java
package com.meshsuite.partner.domain.enums;

public enum PersonType {
    INDIVIDUAL,
    LEGAL_ENTITY
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/partner/domain/enums/TaxIndicator.java`:
```java
package com.meshsuite.partner.domain.enums;

public enum TaxIndicator {
    NON_TAXPAYER,
    TAXPAYER,
    EXEMPT_TAXPAYER
}
```

- [ ] **Step 3: Create `Partner.java`**

```java
package com.meshsuite.partner.domain;

import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.domain.enums.TaxIndicator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "partner")
@Getter
@Setter
public class Partner {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 20)
    private PersonType personType;

    @Column(nullable = false, length = 14)
    private String document;

    @Column(name = "trade_name", nullable = false)
    private String tradeName;

    @Column(name = "legal_name")
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PartnerStatus status = PartnerStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "partner_role", joinColumns = @JoinColumn(name = "partner_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<PartnerRole> roles = new HashSet<>();

    @Column(name = "billing_emails", length = 500)
    private String billingEmails;

    @Column(length = 20)
    private String whatsapp;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_indicator", length = 20)
    private TaxIndicator taxIndicator;

    @Column(name = "state_registration", length = 20)
    private String stateRegistration;

    @Column(name = "municipal_registration", length = 20)
    private String municipalRegistration;

    @Column(name = "suframa_registration", length = 20)
    private String suframaRegistration;

    @Column(length = 8)
    private String zipCode;

    private String street;

    @Column(length = 20)
    private String number;

    @Column(length = 100)
    private String neighborhood;

    @Column(length = 100)
    private String complement;

    @Column(length = 2)
    private String state;

    @Column(length = 100)
    private String city;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "partner", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PartnerContact> contacts = new ArrayList<>();
}
```

- [ ] **Step 4: Create `PartnerContact.java`**

```java
package com.meshsuite.partner.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "partner_contact")
@Getter
@Setter
public class PartnerContact {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(nullable = false)
    private String name;

    private String email;

    @Column(name = "business_phone", length = 20)
    private String businessPhone;

    @Column(name = "mobile_phone", length = 20)
    private String mobilePhone;

    @Column(length = 100)
    private String jobTitle;
}
```

- [ ] **Step 5: Create `PartnerRepository.java`**

```java
package com.meshsuite.partner.repository;

import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PartnerRepository extends JpaRepository<Partner, UUID>, JpaSpecificationExecutor<Partner> {
    boolean existsByDocument(String document);
    boolean existsByDocumentAndIdNot(String document, UUID id);
    long countByStatus(PartnerStatus status);
    long countByStatusAndRolesContaining(PartnerStatus status, PartnerRole role);
}
```

- [ ] **Step 6: Create `PartnerRepositoryTest.java`**

```java
package com.meshsuite.partner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.PartnerContact;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PartnerRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
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

    private Partner newPartner(UUID tenantId, String document) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(document);
        p.setTradeName("Mercado Silva");
        p.setLegalName("Mercado Silva Ltda");
        p.setRoles(Set.of(PartnerRole.CUSTOMER));
        return p;
    }

    @Test
    @Transactional
    void savesPartnerWithRolesAndContacts() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Partner partner = newPartner(tenant.getId(), "11222333000144");
        PartnerContact contact = new PartnerContact();
        contact.setPartner(partner);
        contact.setName("Ana Souza");
        contact.setJobTitle("Financeiro");
        partner.getContacts().add(contact);

        Partner saved = partnerRepository.saveAndFlush(partner);
        entityManager.clear();

        Partner reloaded = partnerRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRoles()).containsExactly(PartnerRole.CUSTOMER);
        assertThat(reloaded.getContacts()).hasSize(1);
        assertThat(reloaded.getContacts().get(0).getName()).isEqualTo("Ana Souza");
        assertThat(reloaded.getStatus()).isEqualTo(PartnerStatus.ACTIVE);
    }

    @Test
    @Transactional
    void documentMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        partnerRepository.saveAndFlush(newPartner(tenant.getId(), "11222333000144"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> partnerRepository.saveAndFlush(newPartner(tenant.getId(), "11222333000144")));
    }

    @Test
    @Transactional
    void sameDocumentAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        partnerRepository.saveAndFlush(newPartner(tenantA.getId(), "11222333000144"));

        setTenantContext(tenantB.getId());
        Partner saved = partnerRepository.saveAndFlush(newPartner(tenantB.getId(), "11222333000144"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesPartnerAndChildRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Partner partner = newPartner(tenant.getId(), "11222333000144");
        PartnerContact contact = new PartnerContact();
        contact.setPartner(partner);
        contact.setName("Ana Souza");
        partner.getContacts().add(contact);
        partnerRepository.saveAndFlush(partner);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long partnerCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM partner")
                .getSingleResult()).longValue();
        Long roleCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM partner_role")
                .getSingleResult()).longValue();
        Long contactCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM partner_contact")
                .getSingleResult()).longValue();

        assertThat(partnerCount).isZero();
        assertThat(roleCount).isZero();
        assertThat(contactCount).isZero();
    }
}
```

- [ ] **Step 7: Reset the local database, then verify**

```bash
docker compose down -v && docker compose up -d
```
(Or the project's equivalent reset command — whatever was used for the Empresa→Company sub-project's Task 2.)

**Important — whole-module compile blast radius:** at this point, ~19 other files across `parceiro` itself (none left), `payable`, `pedido`, `purchaseorder`, `sale` (main + test sources) still reference the now-deleted `com.meshsuite.parceiro.*` classes, so `mvn test-compile` on the whole module will fail. Use the relocate-test-restore technique (established in the Venda→Sale and Empresa→Company sub-projects): temporarily move the following 12 out-of-scope test files out of `src/` to a temp directory (preserving relative paths), run this task's own test in isolation, then move them back exactly and verify `git status --short` is clean of anything unexpected:
```
mesh-suite-backend/src/test/java/com/meshsuite/payable/controller/AccountsPayableControllerTest.java
mesh-suite-backend/src/test/java/com/meshsuite/payable/repository/AccountsPayableRepositoryTest.java
mesh-suite-backend/src/test/java/com/meshsuite/payable/service/AccountsPayableServiceTest.java
mesh-suite-backend/src/test/java/com/meshsuite/pedido/controller/PedidoControllerTest.java
mesh-suite-backend/src/test/java/com/meshsuite/pedido/repository/PedidoRepositoryTest.java
mesh-suite-backend/src/test/java/com/meshsuite/pedido/service/PedidoServiceTest.java
mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/controller/PurchaseOrderControllerTest.java
mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/repository/PurchaseOrderRepositoryTest.java
mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/service/PurchaseOrderServiceTest.java
mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java
mesh-suite-backend/src/test/java/com/meshsuite/sale/repository/SaleRepositoryTest.java
mesh-suite-backend/src/test/java/com/meshsuite/sale/service/SaleServiceTest.java
```
The main-source files (`payable/domain/AccountsPayable.java`, `payable/service/AccountsPayableService.java`, `pedido/domain/Pedido.java`, `pedido/service/PedidoService.java`, `purchaseorder/domain/PurchaseOrder.java`, `purchaseorder/service/PurchaseOrderService.java`, `sale/domain/Sale.java`) also reference `Parceiro` but are NOT relocatable (deleting them would break other things) — they need a one-line compile-bridge (`import com.meshsuite.parceiro.domain.Parceiro;` → `import com.meshsuite.partner.domain.Partner;`, and the field's declared type only) applied directly, in this same task, without touching their field names (`cliente`, `supplier`, `customer` stay). This bridge is reserved for Task 5 in the plan text, but Task 2 cannot compile without it — apply the type-only bridge now as part of Task 2's own verification, and Task 5 will find it already done (note this explicitly in your task report so Task 5's dispatch isn't surprised).

Run:
```bash
cd mesh-suite-backend && mvn -q test -Dtest=PartnerRepositoryTest
```
Expected: `BUILD SUCCESS`, 4/4 tests pass. This is the first task in this plan that boots the full Spring context — a passing run here proves Task 1's schema (including the 5 FK fixes) is fully correct.

Restore the 12 relocated files, then verify:
```bash
git status --short
```
Expected: only the files this task actually changed (Task 1's commit is already in history; this step should show only Task 2's new/deleted files plus the type-only bridge edits on the 7 main-source consumer files).

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/partner \
        mesh-suite-backend/src/test/java/com/meshsuite/partner/repository/PartnerRepositoryTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/parceiro \
        mesh-suite-backend/src/test/java/com/meshsuite/parceiro/repository/ParceiroRepositoryTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/payable/domain/AccountsPayable.java \
        mesh-suite-backend/src/main/java/com/meshsuite/payable/service/AccountsPayableService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/pedido/domain/Pedido.java \
        mesh-suite-backend/src/main/java/com/meshsuite/pedido/service/PedidoService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/domain/PurchaseOrder.java \
        mesh-suite-backend/src/main/java/com/meshsuite/purchaseorder/service/PurchaseOrderService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/sale/domain/Sale.java
git commit -m "refactor(partner): rename Parceiro/ParceiroContato domain and repository layer to English, bridge-patch type-only consumers"
```

---

### Task 3: DTOs, exceptions, specifications, service, service test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerContactDto.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerListItemResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerStatusRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/exception/DuplicateDocumentException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/exception/PartnerNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/exception/PartnerValidationException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/repository/specification/PartnerSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/service/PartnerService.java`
- Delete: the 6 files under `parceiro/dto/`, 3 files under `parceiro/exception/` (NOT `ParceiroExceptionHandler.java` — that one is Task 4's), `parceiro/repository/specification/ParceiroSpecifications.java`, `parceiro/service/ParceiroService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/partner/service/PartnerServiceTest.java` (mirrors `parceiro/service/ParceiroServiceTest.java`, see Step 8)

**Interfaces:**
- Consumes: `Partner`, `PartnerContact`, `PartnerRepository`, `PartnerRole`, `PartnerStatus`, `PersonType`, `TaxIndicator` (Task 2).
- Produces: `PartnerService` with methods `list(String search, List<PartnerStatus> status, List<PersonType> personType, String document, List<String> state, List<String> city, PartnerRole role, Pageable pageable): Page<PartnerListItemResponse>`, `summary(PartnerRole role): PartnerSummaryResponse`, `findById(UUID id): PartnerResponse`, `create(UUID tenantId, PartnerRequest request): PartnerResponse`, `update(UUID id, PartnerRequest request): PartnerResponse`, `updateStatus(UUID id, PartnerStatus newStatus): PartnerResponse`, `delete(UUID id): void` — all consumed by Task 4's `PartnerController`.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroContatoDto.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroRequest.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroResponse.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroResumoResponse.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroStatusRequest.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/dto/ParceiroSummaryResponse.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/exception/DocumentoDuplicadoException.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/exception/ParceiroNaoEncontradoException.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/exception/ParceiroValidacaoException.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/repository/specification/ParceiroSpecifications.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/service/ParceiroService.java
git rm mesh-suite-backend/src/test/java/com/meshsuite/parceiro/service/ParceiroServiceTest.java
```

- [ ] **Step 2: Create `PartnerRequest.java`**

```java
package com.meshsuite.partner.dto;

import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.domain.enums.TaxIndicator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record PartnerRequest(
        @NotNull PersonType personType,
        @NotBlank String document,
        @NotBlank String tradeName,
        String legalName,
        @NotEmpty Set<PartnerRole> roles,
        String billingEmails,
        String whatsapp,
        TaxIndicator taxIndicator,
        String stateRegistration,
        String municipalRegistration,
        String suframaRegistration,
        String zipCode,
        String street,
        String number,
        String neighborhood,
        String complement,
        String state,
        String city,
        String notes,
        List<PartnerContactDto> contacts) {
}
```

- [ ] **Step 3: Create `PartnerResponse.java`**

```java
package com.meshsuite.partner.dto;

import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.domain.enums.TaxIndicator;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PartnerResponse(
        UUID id,
        PersonType personType,
        String document,
        String tradeName,
        String legalName,
        PartnerStatus status,
        Set<PartnerRole> roles,
        String billingEmails,
        String whatsapp,
        TaxIndicator taxIndicator,
        String stateRegistration,
        String municipalRegistration,
        String suframaRegistration,
        String zipCode,
        String street,
        String number,
        String neighborhood,
        String complement,
        String state,
        String city,
        String notes,
        List<PartnerContactDto> contacts) {
}
```

- [ ] **Step 4: Create the remaining DTOs**

`mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerContactDto.java`:
```java
package com.meshsuite.partner.dto;

public record PartnerContactDto(
        String name,
        String email,
        String businessPhone,
        String mobilePhone,
        String jobTitle) {
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerSummaryResponse.java`:
```java
package com.meshsuite.partner.dto;

public record PartnerSummaryResponse(long total, long active, long atRisk, long blocked) {
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerListItemResponse.java`:
```java
package com.meshsuite.partner.dto;

import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;

import java.util.UUID;

public record PartnerListItemResponse(
        UUID id,
        String tradeName,
        String legalName,
        String document,
        PersonType personType,
        String city,
        String state,
        String whatsapp,
        PartnerStatus status) {
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/partner/dto/PartnerStatusRequest.java`:
```java
package com.meshsuite.partner.dto;

import com.meshsuite.partner.domain.enums.PartnerStatus;
import jakarta.validation.constraints.NotNull;

public record PartnerStatusRequest(@NotNull PartnerStatus status) {
}
```

- [ ] **Step 5: Create the three exceptions**

`mesh-suite-backend/src/main/java/com/meshsuite/partner/exception/DuplicateDocumentException.java`:
```java
package com.meshsuite.partner.exception;

public class DuplicateDocumentException extends RuntimeException {
    public DuplicateDocumentException() {
        super("Já existe um parceiro cadastrado com este documento");
    }
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/partner/exception/PartnerNotFoundException.java`:
```java
package com.meshsuite.partner.exception;

public class PartnerNotFoundException extends RuntimeException {
    public PartnerNotFoundException() {
        super("Parceiro não encontrado");
    }
}
```

`mesh-suite-backend/src/main/java/com/meshsuite/partner/exception/PartnerValidationException.java`:
```java
package com.meshsuite.partner.exception;

public class PartnerValidationException extends RuntimeException {
    public PartnerValidationException(String message) {
        super(message);
    }
}
```

Note: the exception messages (`"Já existe um parceiro cadastrado com este documento"`, `"Parceiro não encontrado"`) stay in Portuguese — they're HTTP error responses shown to the end user.

- [ ] **Step 6: Create `PartnerSpecifications.java`**

```java
package com.meshsuite.partner.repository.specification;

import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class PartnerSpecifications {

    private PartnerSpecifications() {
    }

    public static Specification<Partner> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("tradeName")), term),
                cb.like(cb.lower(root.get("legalName")), term));
    }

    public static Specification<Partner> withStatus(List<PartnerStatus> status) {
        if (status == null || status.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("status").in(status);
    }

    public static Specification<Partner> withPersonType(List<PersonType> personType) {
        if (personType == null || personType.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("personType").in(personType);
    }

    public static Specification<Partner> withState(List<String> state) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("state").in(state);
    }

    public static Specification<Partner> withCity(List<String> city) {
        if (city == null || city.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("city").in(city);
    }

    public static Specification<Partner> withDocument(String document) {
        if (document == null || document.isBlank()) {
            return null;
        }
        String digits = document.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return null;
        }
        String term = "%" + digits + "%";
        return (root, query, cb) -> cb.like(root.get("document"), term);
    }

    public static Specification<Partner> withRole(PartnerRole role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> cb.isMember(role, root.get("roles"));
    }
}
```

- [ ] **Step 7: Create `PartnerService.java`**

```java
package com.meshsuite.partner.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.PartnerContact;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.dto.*;
import com.meshsuite.partner.exception.DuplicateDocumentException;
import com.meshsuite.partner.exception.PartnerNotFoundException;
import com.meshsuite.partner.exception.PartnerValidationException;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.partner.repository.specification.PartnerSpecifications;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerService {

    private final PartnerRepository partnerRepository;

    public PartnerService(PartnerRepository partnerRepository) {
        this.partnerRepository = partnerRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public Page<PartnerListItemResponse> list(String search, List<PartnerStatus> status, List<PersonType> personType,
                                               String document, List<String> state, List<String> city,
                                               PartnerRole role, Pageable pageable) {
        Specification<Partner> spec = Specification.allOf(
                PartnerSpecifications.withSearch(search),
                PartnerSpecifications.withStatus(status),
                PartnerSpecifications.withPersonType(personType),
                PartnerSpecifications.withDocument(document),
                PartnerSpecifications.withState(state),
                PartnerSpecifications.withCity(city),
                PartnerSpecifications.withRole(role));
        return partnerRepository.findAll(spec, pageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public PartnerSummaryResponse summary(PartnerRole role) {
        long active = countByStatus(PartnerStatus.ACTIVE, role);
        long atRisk = countByStatus(PartnerStatus.AT_RISK, role);
        long blocked = countByStatus(PartnerStatus.BLOCKED, role);
        return new PartnerSummaryResponse(active + atRisk + blocked, active, atRisk, blocked);
    }

    private long countByStatus(PartnerStatus status, PartnerRole role) {
        return role == null
                ? partnerRepository.countByStatus(status)
                : partnerRepository.countByStatusAndRolesContaining(status, role);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public PartnerResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.CREATE)
    public PartnerResponse create(UUID tenantId, PartnerRequest request) {
        validate(request, null);

        Partner partner = new Partner();
        partner.setTenantId(tenantId);
        apply(partner, request);
        return toResponse(partnerRepository.saveAndFlush(partner));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.EDIT)
    public PartnerResponse update(UUID id, PartnerRequest request) {
        validate(request, id);

        Partner partner = findEntityById(id);
        apply(partner, request);
        return toResponse(partnerRepository.saveAndFlush(partner));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.EDIT)
    public PartnerResponse updateStatus(UUID id, PartnerStatus newStatus) {
        if (newStatus != PartnerStatus.ACTIVE && newStatus != PartnerStatus.BLOCKED) {
            throw new PartnerValidationException("Só é possível definir o status como ATIVO ou BLOQUEADO manualmente");
        }
        Partner partner = findEntityById(id);
        partner.setStatus(newStatus);
        return toResponse(partnerRepository.saveAndFlush(partner));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.DELETE)
    public void delete(UUID id) {
        partnerRepository.delete(findEntityById(id));
    }

    private Partner findEntityById(UUID id) {
        return partnerRepository.findById(id).orElseThrow(PartnerNotFoundException::new);
    }

    private void validate(PartnerRequest request, UUID currentId) {
        boolean noActiveRole = request.roles().stream()
                .noneMatch(r -> r == PartnerRole.CUSTOMER || r == PartnerRole.SUPPLIER);
        if (noActiveRole) {
            throw new PartnerValidationException("Selecione ao menos o papel Cliente ou Fornecedor");
        }

        String document = normalizeDocument(request.document());
        int expectedLength = request.personType() == PersonType.INDIVIDUAL ? 11 : 14;
        if (document.length() != expectedLength) {
            throw new PartnerValidationException(
                    request.personType() == PersonType.INDIVIDUAL
                            ? "CPF deve ter 11 dígitos"
                            : "CNPJ deve ter 14 dígitos");
        }

        boolean duplicate = currentId == null
                ? partnerRepository.existsByDocument(document)
                : partnerRepository.existsByDocumentAndIdNot(document, currentId);
        if (duplicate) {
            throw new DuplicateDocumentException();
        }
    }

    // Aceita CNPJ/CPF digitados ou colados com a máscara usual (pontos, barra,
    // hífen) -- só os dígitos são validados e persistidos.
    private static String normalizeDocument(String document) {
        return document.replaceAll("\\D", "");
    }

    private void apply(Partner partner, PartnerRequest request) {
        partner.setPersonType(request.personType());
        partner.setDocument(normalizeDocument(request.document()));
        partner.setTradeName(request.tradeName());
        partner.setLegalName(request.legalName());
        partner.setRoles(new HashSet<>(request.roles()));
        partner.setBillingEmails(request.billingEmails());
        partner.setWhatsapp(request.whatsapp());
        partner.setTaxIndicator(request.taxIndicator());
        partner.setStateRegistration(request.stateRegistration());
        partner.setMunicipalRegistration(request.municipalRegistration());
        partner.setSuframaRegistration(request.suframaRegistration());
        partner.setZipCode(request.zipCode());
        partner.setStreet(request.street());
        partner.setNumber(request.number());
        partner.setNeighborhood(request.neighborhood());
        partner.setComplement(request.complement());
        partner.setState(request.state());
        partner.setCity(request.city());
        partner.setNotes(request.notes());

        partner.getContacts().clear();
        List<PartnerContactDto> contacts = request.contacts() == null ? List.of() : request.contacts();
        for (PartnerContactDto dto : contacts) {
            PartnerContact contact = new PartnerContact();
            contact.setPartner(partner);
            contact.setName(dto.name());
            contact.setEmail(dto.email());
            contact.setBusinessPhone(dto.businessPhone());
            contact.setMobilePhone(dto.mobilePhone());
            contact.setJobTitle(dto.jobTitle());
            partner.getContacts().add(contact);
        }
    }

    private PartnerListItemResponse toListItem(Partner p) {
        return new PartnerListItemResponse(
                p.getId(), p.getTradeName(), p.getLegalName(), p.getDocument(), p.getPersonType(),
                p.getCity(), p.getState(), p.getWhatsapp(), p.getStatus());
    }

    private PartnerResponse toResponse(Partner p) {
        List<PartnerContactDto> contacts = p.getContacts().stream()
                .map(c -> new PartnerContactDto(c.getName(), c.getEmail(), c.getBusinessPhone(),
                        c.getMobilePhone(), c.getJobTitle()))
                .toList();
        return new PartnerResponse(
                p.getId(), p.getPersonType(), p.getDocument(), p.getTradeName(), p.getLegalName(),
                p.getStatus(), p.getRoles(), p.getBillingEmails(), p.getWhatsapp(), p.getTaxIndicator(),
                p.getStateRegistration(), p.getMunicipalRegistration(), p.getSuframaRegistration(), p.getZipCode(),
                p.getStreet(), p.getNumber(), p.getNeighborhood(), p.getComplement(), p.getState(), p.getCity(),
                p.getNotes(), contacts);
    }
}
```

- [ ] **Step 8: Create `PartnerServiceTest.java` by mirroring `ParceiroServiceTest.java`**

Read `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/service/ParceiroServiceTest.java` (331 lines, already deleted in Step 1 — read it from git history: `git show HEAD:mesh-suite-backend/src/test/java/com/meshsuite/parceiro/service/ParceiroServiceTest.java`) and create `mesh-suite-backend/src/test/java/com/meshsuite/partner/service/PartnerServiceTest.java` with the exact same test cases (same assertions, same fixture data, same edge cases — nothing added or removed), applying this substitution table throughout:

Package/imports: `com.meshsuite.parceiro.service` → `com.meshsuite.partner.service`; every `com.meshsuite.parceiro.*` import → the matching `com.meshsuite.partner.*` path per Task 2/3's new package layout.

Types: `Parceiro→Partner`, `ParceiroContato→PartnerContact`, `ParceiroContatoDto→PartnerContactDto`, `ParceiroRequest→PartnerRequest`, `ParceiroResponse→PartnerResponse`, `ParceiroService→PartnerService`, `ParceiroRepository→PartnerRepository`, `PapelParceiro→PartnerRole`, `StatusParceiro→PartnerStatus`, `TipoPessoa→PersonType`.

Enum values: `PapelParceiro.CLIENTE→PartnerRole.CUSTOMER`, `PapelParceiro.FORNECEDOR→PartnerRole.SUPPLIER`, `PapelParceiro.TRANSPORTADORA→PartnerRole.CARRIER`, `StatusParceiro.ATIVO→PartnerStatus.ACTIVE`, `StatusParceiro.EM_RISCO→PartnerStatus.AT_RISK`, `StatusParceiro.BLOQUEADO→PartnerStatus.BLOCKED`, `TipoPessoa.JURIDICA→PersonType.LEGAL_ENTITY`, `TipoPessoa.FISICA→PersonType.INDIVIDUAL`.

Fields/methods used in fixtures and assertions: `.papeis()→.roles()`, `.documento()→.document()`, `.status()` (unchanged), `parceiroService→partnerService`, `parceiroService.listar(...)→partnerService.list(...)`, `.resumo(...)→.summary(...)`, `.criar(...)→.create(...)`, `.atualizar(...)→.update(...)`, `.atualizarStatus(...)→.updateStatus(...)`, `.excluir(...)→.delete(...)`.

Test method names (translate all 16, matching the design spec's list — these are the ONLY method-name changes; the test bodies/assertions/fixture values otherwise stay byte-identical to the original modulo the type substitutions above):
- `criaERecuperaParceiro` → `createsAndRetrievesPartner`
- `aceitaCnpjComMascaraEArmazenaSomenteDigitos` → `acceptsCnpjWithMaskAndStoresOnlyDigits`
- `rejeitaParceiroSemPapelClienteOuFornecedor` → `rejectsPartnerWithoutCustomerOrSupplierRole`
- `rejeitaDocumentoDuplicadoNoMesmoTenant` → `rejectsDuplicateDocumentInSameTenant`
- `rejeitaAtualizacaoDeStatusParaEmRisco` → `rejectsStatusUpdateToAtRisk`
- `atualizaStatusParaBloqueado` → `updatesStatusToBlocked`
- `resumoContaPorStatus` → `summaryCountsByStatus`
- `resumoContaSomenteOPapelInformado` → `summaryCountsOnlyTheGivenRole`
- `listaComFiltroDeBusca` → `listsWithSearchFilter`
- `listaComFiltroDeDocumentoParcialIgnorandoMascara` → `listsWithPartialDocumentFilterIgnoringMask`
- `listaComFiltroDePapel` → `listsWithRoleFilter`
- `listaComFiltroDeStatusMultiplo` → `listsWithMultiValueStatusFilter`
- `listaComFiltroDeUfMultiplo` → `listsWithMultiValueStateFilter`
- `excluiParceiro` → `deletesPartner`
- `atualizaParceiroComSucesso` → `updatesPartnerSuccessfully`
- `atualizaParceiroMantendoOProprioDocumento` → `updatesPartnerKeepingItsOwnDocument`

Field-name-shaped literals used only as free-text business content (e.g. `"Mercado Silva"`, `"Cliente antigo"`, `"Comércio Rio"`, email/phone strings, CNPJ/CPF digit strings) stay exactly as they are — only identifiers and enum-backed values change, never business data.

- [ ] **Step 9: Verify**

Use the relocate-test-restore technique again for the same 12 out-of-scope consumer test files listed in Task 2 Step 7 (they still reference `Parceiro`/`ParceiroRepository`, which no longer exist — Task 6/7 fix them).

Run:
```bash
cd mesh-suite-backend && mvn -q test -Dtest=PartnerServiceTest
```
Expected: `BUILD SUCCESS`, same test count as the original `ParceiroServiceTest` (16 tests), all passing.

Restore the 12 relocated files; verify `git status --short` shows only this task's intended changes.

- [ ] **Step 10: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/partner/dto \
        mesh-suite-backend/src/main/java/com/meshsuite/partner/exception \
        mesh-suite-backend/src/main/java/com/meshsuite/partner/repository/specification \
        mesh-suite-backend/src/main/java/com/meshsuite/partner/service \
        mesh-suite-backend/src/test/java/com/meshsuite/partner/service/PartnerServiceTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/parceiro \
        mesh-suite-backend/src/test/java/com/meshsuite/parceiro/service/ParceiroServiceTest.java
git commit -m "refactor(partner): rename Parceiro DTOs, exceptions, specifications, and service layer to English"
```

---

### Task 4: Controller, exception handler, controller test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/controller/PartnerController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/partner/exception/PartnerExceptionHandler.java`
- Delete: `parceiro/controller/ParceiroController.java`, `parceiro/exception/ParceiroExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/partner/controller/PartnerControllerTest.java` (mirrors `parceiro/controller/ParceiroControllerTest.java`, see Step 4)

**Interfaces:**
- Consumes: `PartnerService`, all its DTOs (Task 3).
- Produces: `PartnerController` at `/api/partners` with routes `GET /api/partners`, `GET /api/partners/summary`, `GET /api/partners/{id}`, `POST /api/partners`, `PUT /api/partners/{id}`, `PATCH /api/partners/{id}/status`, `DELETE /api/partners/{id}` — the `/resumo` sub-path becomes `/summary`.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/controller/ParceiroController.java
git rm mesh-suite-backend/src/main/java/com/meshsuite/parceiro/exception/ParceiroExceptionHandler.java
git rm mesh-suite-backend/src/test/java/com/meshsuite/parceiro/controller/ParceiroControllerTest.java
```

- [ ] **Step 2: Create `PartnerController.java`**

```java
package com.meshsuite.partner.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.dto.*;
import com.meshsuite.partner.service.PartnerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partners")
public class PartnerController {

    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @GetMapping
    public Page<PartnerListItemResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) List<PartnerStatus> status,
            @RequestParam(required = false) List<PersonType> tipoDocumento,
            @RequestParam(required = false) String documento,
            @RequestParam(required = false) List<String> uf,
            @RequestParam(required = false) List<String> cidade,
            @RequestParam(required = false) PartnerRole papel,
            @PageableDefault(size = 10, sort = "tradeName") Pageable pageable) {
        return partnerService.list(busca, status, tipoDocumento, documento, uf, cidade, papel, pageable);
    }

    @GetMapping("/summary")
    public PartnerSummaryResponse summary(@RequestParam(required = false) PartnerRole papel) {
        return partnerService.summary(papel);
    }

    @GetMapping("/{id}")
    public PartnerResponse findById(@PathVariable UUID id) {
        return partnerService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PartnerResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                   @Valid @RequestBody PartnerRequest request) {
        PartnerResponse response = partnerService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PartnerResponse update(@PathVariable UUID id, @Valid @RequestBody PartnerRequest request) {
        return partnerService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public PartnerResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody PartnerStatusRequest request) {
        return partnerService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        partnerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

Note: request-parameter names (`busca`, `documento`, `uf`, `cidade`, `papel`, `tipoDocumento`) stay as-is — they're query-string parameter names, which are part of the wire API contract read by `mesh-suite-frontend/src/api/parceiros.ts` (renamed to `partners.ts` in Task 8, at which point these same names carry over unchanged — this plan does not translate query-parameter names, only path segments, matching how `/api/sales`'s own params were handled in the Venda→Sale sub-project). `@PageableDefault(sort = "tradeName")` above already reflects the fix from the original `sort = "nomeFantasia"` — the sort key must match the entity's actual field name (`Partner.tradeName`), not the DTO's old Portuguese name.

- [ ] **Step 3: Create `PartnerExceptionHandler.java`**

```java
package com.meshsuite.partner.exception;

import com.meshsuite.partner.controller.PartnerController;
import com.meshsuite.partner.service.PartnerService;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PartnerController.class)
public class PartnerExceptionHandler {

    // Fallback for a race condition slipping past PartnerService's pre-check
    // (two concurrent requests for the same new document) -- the DB's
    // UNIQUE(tenant_id, document) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um parceiro cadastrado com este documento"));
    }
}
```

- [ ] **Step 4: Create `PartnerControllerTest.java` by mirroring `ParceiroControllerTest.java`**

Read `git show HEAD:mesh-suite-backend/src/test/java/com/meshsuite/parceiro/controller/ParceiroControllerTest.java` (303 lines) and create `mesh-suite-backend/src/test/java/com/meshsuite/partner/controller/PartnerControllerTest.java` with the exact same test cases, applying:

- All the type/enum-value substitutions from Task 3 Step 8's table.
- URL paths: every `"/api/parceiros"` (and `"/api/parceiros/{id}"`, `"/api/parceiros/{id}/status"`) → `"/api/partners"` (and the matching sub-paths); `"/api/parceiros/resumo"` → `"/api/partners/summary"`.
- Test method names — this file's 8 method names are already mostly English (per the design spec's audit); only the domain noun changes: `createsListsUpdatesAndDeletesParceiro→createsListsUpdatesAndDeletesPartner`, `listsWithDocumentoFilterIgnoringMaskAndMatchingPartially→listsWithDocumentFilterIgnoringMaskAndMatchingPartially`, `listsWithMultiValueStatusFilter` (unchanged), `rejectsDuplicateDocumentoWithConflict→rejectsDuplicateDocumentWithConflict`, `rejectsStatusUpdateToEmRisco→rejectsStatusUpdateToAtRisk`, `tenantACannotAccessTenantBsParceiro→tenantACannotAccessTenantBsPartner`, `unauthenticatedRequestIsRejected` (unchanged), `listingWithoutCustomerViewPermissionIsForbidden` (unchanged).
- Any JSON body field names asserted against (e.g. `.andExpect(jsonPath("$.razaoSocial")...)`) — apply the field map from the design spec (e.g. `razaoSocial→legalName`, `nomeFantasia→tradeName`, `papeis→roles`, `documento→document`).

- [ ] **Step 5: Verify**

Use the relocate-test-restore technique for the same 12 out-of-scope files (Task 2 Step 7's list).

Run:
```bash
cd mesh-suite-backend && mvn -q test -Dtest=PartnerControllerTest
```
Expected: `BUILD SUCCESS`, same test count as the original (8 tests), all passing.

Restore the 12 relocated files; verify `git status --short` is clean of anything unexpected.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/partner/controller \
        mesh-suite-backend/src/main/java/com/meshsuite/partner/exception/PartnerExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/partner/controller/PartnerControllerTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/parceiro \
        mesh-suite-backend/src/test/java/com/meshsuite/parceiro/controller/ParceiroControllerTest.java
git commit -m "refactor(partner): rename Parceiro controller and exception handler to English, route parceiros->partners"
```

Note: after this task, `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/` should be completely empty of files (only empty directories, if any, may remain — remove them if `git status` shows them as untracked empty dirs, though Git does not track empty directories so this is usually a no-op).

---

### Task 5: Bridge — `shared/handler/GlobalExceptionHandler.java`

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `PartnerNotFoundException`, `DuplicateDocumentException`, `PartnerValidationException` (Task 3).

**Note:** the type-only bridge patches on `Pedido.java`/`PedidoService.java`, `AccountsPayable.java`/`AccountsPayableService.java`, `PurchaseOrder.java`/`PurchaseOrderService.java`, `Sale.java` were already applied and committed as part of Task 2 Step 7 (they were unavoidable to make Task 2's own test compile) — do not redo them here; if `git log` shows those 7 files already reference `Partner`, this task's scope is only `GlobalExceptionHandler.java`.

- [ ] **Step 1: Rename the three handler methods and their exception types**

In `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`, change:
```java
    @ExceptionHandler(com.meshsuite.parceiro.exception.ParceiroNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleParceiroNaoEncontrado(
            com.meshsuite.parceiro.exception.ParceiroNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.parceiro.exception.DocumentoDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleDocumentoDuplicado(
            com.meshsuite.parceiro.exception.DocumentoDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.parceiro.exception.ParceiroValidacaoException.class)
    public ResponseEntity<Map<String, String>> handleParceiroValidacao(
            com.meshsuite.parceiro.exception.ParceiroValidacaoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```
to:
```java
    @ExceptionHandler(com.meshsuite.partner.exception.PartnerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePartnerNotFound(
            com.meshsuite.partner.exception.PartnerNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.partner.exception.DuplicateDocumentException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateDocument(
            com.meshsuite.partner.exception.DuplicateDocumentException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.partner.exception.PartnerValidationException.class)
    public ResponseEntity<Map<String, String>> handlePartnerValidation(
            com.meshsuite.partner.exception.PartnerValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

The `"mensagem"` key and `e.getMessage()` (which carries the Portuguese error text from Task 3's exception classes) stay unchanged.

- [ ] **Step 2: Verify**

Use the relocate-test-restore technique for the same 12 out-of-scope files.

Run:
```bash
cd mesh-suite-backend && mvn -q compile
```
Expected: `BUILD SUCCESS`.

Restore the 12 relocated files; verify `git status --short` is clean of anything unexpected.

- [ ] **Step 3: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java
git commit -m "refactor(partner): rename GlobalExceptionHandler's Parceiro handlers to English"
```

---

### Task 6: Cross-module test batch 1 — payable, pedido

**Files:**
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/payable/controller/AccountsPayableControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/payable/repository/AccountsPayableRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/payable/service/AccountsPayableServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/controller/PedidoControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/repository/PedidoRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/service/PedidoServiceTest.java`

**Interfaces:**
- Consumes: `Partner`, `PartnerRepository`, `PartnerRole` (Task 2).

For every file in this task, apply the same mechanical edit:

1. Change imports:
```java
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
```
to:
```java
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
```
(Import order/grouping may already differ slightly per file — preserve each file's existing import block order, just swap the identifiers.)

2. Change the autowired field: `@Autowired ParceiroRepository parceiroRepository;` → `@Autowired PartnerRepository partnerRepository;` (and every subsequent use of `parceiroRepository` → `partnerRepository`).

3. Change every `Parceiro` variable declaration/usage to `Partner`, `.setTipoPessoa(TipoPessoa.JURIDICA)` → `.setPersonType(PersonType.LEGAL_ENTITY)`, `.setDocumento(...)` → `.setDocument(...)`, `.setNomeFantasia(...)` → `.setTradeName(...)`, `.getPapeis().add(PapelParceiro.X)` → `.getRoles().add(PartnerRole.Y)` (with `CLIENTE→CUSTOMER`, `FORNECEDOR→SUPPLIER`).

**Per-file specifics:**

- [ ] **`AccountsPayableControllerTest.java`**: fixture block (around line 77-83):
```java
        Parceiro fornecedor = new Parceiro();
        fornecedor.setTenantId(tenant.getId());
        fornecedor.setTipoPessoa(TipoPessoa.JURIDICA);
        fornecedor.setDocumento(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        fornecedor.setNomeFantasia("Tecidos Aurora");
        fornecedor.getPapeis().add(PapelParceiro.FORNECEDOR);
        parceiroRepository.saveAndFlush(fornecedor);
```
becomes:
```java
        Partner fornecedor = new Partner();
        fornecedor.setTenantId(tenant.getId());
        fornecedor.setPersonType(PersonType.LEGAL_ENTITY);
        fornecedor.setDocument(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        fornecedor.setTradeName("Tecidos Aurora");
        fornecedor.getRoles().add(PartnerRole.SUPPLIER);
        partnerRepository.saveAndFlush(fornecedor);
```
(the local variable name `fornecedor` stays — it's not a type or field name, just a local identifier, out of this plan's mechanical-rename scope, same as how `cliente`/`supplier`/`customer` field names were left alone in Task 2's bridge patches).

- [ ] **`AccountsPayableRepositoryTest.java`**: same shape, variable `fornecedor`, one block around line 45-51.

- [ ] **`AccountsPayableServiceTest.java`**: two helper methods creating a `Parceiro p` each (around lines 80-86 and 90-96) — one with `PapelParceiro.FORNECEDOR`, one with `PapelParceiro.CLIENTE`. Both become `Partner p` / `PartnerRole.SUPPLIER` / `PartnerRole.CUSTOMER` respectively.

- [ ] **`PedidoControllerTest.java`**: fixture block around line 87-93, variable `cliente`:
```java
        Parceiro cliente = new Parceiro();
        ...
        cliente.getPapeis().add(PapelParceiro.CLIENTE);
        parceiroRepository.saveAndFlush(cliente);
```
becomes `Partner cliente = new Partner(); ... cliente.getRoles().add(PartnerRole.CUSTOMER); partnerRepository.saveAndFlush(cliente);` (variable name `cliente` stays).

- [ ] **`PedidoRepositoryTest.java`**: private helper `criarCliente(UUID tenantId, String documento)` returns `Parceiro` — the method NAME (`criarCliente`) is a private test helper, not part of the design spec's rename map, and stays as-is (it's local to this file, Portuguese, and out of this plan's declared scope — this module's own Pedido→Order rename is a future sub-project). Only its return type (`Parceiro→Partner`) and body (`new Parceiro()`, `.getPapeis().add(PapelParceiro.CLIENTE)`, `parceiroRepository.saveAndFlush(p)`) change. Every call site (`Parceiro cliente = criarCliente(...)`) updates its declared type to `Partner`. The method `novoPedido(UUID tenantId, Parceiro cliente, User vendedor, int numero)` updates its parameter type to `Partner cliente` (parameter name unchanged).

- [ ] **`PedidoServiceTest.java`**: two helper methods (around lines 86-92 and 96-102) mirroring `AccountsPayableServiceTest.java`'s shape — one `PapelParceiro.CLIENTE`, one `PapelParceiro.FORNECEDOR`.

- [ ] **Step: Run the affected test suites**

Run: `cd mesh-suite-backend && mvn -q test -Dtest='AccountsPayableControllerTest,AccountsPayableRepositoryTest,AccountsPayableServiceTest,PedidoControllerTest,PedidoRepositoryTest,PedidoServiceTest'`
Expected: `BUILD SUCCESS`, all tests in these 6 classes pass. (No relocate-test-restore needed for THIS run since it doesn't need the whole module to compile beyond what Task 5 already fixed plus the 6 files this task touches — but the 6 files in Task 7's scope, `purchaseorder`/`sale`, still reference the deleted `Parceiro` classes, so use the relocate-test-restore technique for those 6 files: move them out, run the command above, move them back, verify `git status --short` is clean.)

Files to relocate for this step:
```
mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/controller/PurchaseOrderControllerTest.java
mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/repository/PurchaseOrderRepositoryTest.java
mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/service/PurchaseOrderServiceTest.java
mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java
mesh-suite-backend/src/test/java/com/meshsuite/sale/repository/SaleRepositoryTest.java
mesh-suite-backend/src/test/java/com/meshsuite/sale/service/SaleServiceTest.java
```

- [ ] **Step: Commit**

```bash
git add mesh-suite-backend/src/test/java/com/meshsuite/payable mesh-suite-backend/src/test/java/com/meshsuite/pedido
git commit -m "refactor(partner): rename Parceiro references in payable and pedido test fixtures"
```

---

### Task 7: Cross-module test batch 2 — purchaseorder, sale

**Files:**
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/controller/PurchaseOrderControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/repository/PurchaseOrderRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/service/PurchaseOrderServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/repository/SaleRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/service/SaleServiceTest.java`

**Interfaces:**
- Consumes: `Partner`, `PartnerRepository`, `PartnerRole` (Task 2).

Apply the same 3-part mechanical edit described in Task 6 to all 6 files below.

**Per-file specifics:**

- [ ] **`PurchaseOrderControllerTest.java`**: fixture block around line 78-84, variable `supplier`:
```java
        Parceiro supplier = new Parceiro();
        ...
        supplier.getPapeis().add(PapelParceiro.FORNECEDOR);
        parceiroRepository.saveAndFlush(supplier);
```
becomes `Partner supplier = new Partner(); ... supplier.getRoles().add(PartnerRole.SUPPLIER); partnerRepository.saveAndFlush(supplier);`.

- [ ] **`PurchaseOrderRepositoryTest.java`**: private helper `criarFornecedor(UUID tenantId, String documento)` returns `Parceiro` (method name stays, same reasoning as `PedidoRepositoryTest.java`'s `criarCliente` in Task 6 — it's a local test helper, out of this plan's declared rename scope). Return type → `Partner`, body updates `PapelParceiro.FORNECEDOR→PartnerRole.SUPPLIER`. The method `novaOrdem(UUID tenantId, Parceiro supplier, User buyer, int number)` updates its parameter type to `Partner supplier`.

- [ ] **`PurchaseOrderServiceTest.java`**: two helper methods (around lines 87-93 and 97-103) — one `PapelParceiro.FORNECEDOR`, one `PapelParceiro.CLIENTE`.

- [ ] **`SaleControllerTest.java`**: fixture block around line 90-96, variable `customer`:
```java
        Parceiro customer = new Parceiro();
        ...
        customer.getPapeis().add(PapelParceiro.CLIENTE);
        parceiroRepository.saveAndFlush(customer);
```
becomes `Partner customer = new Partner(); ... customer.getRoles().add(PartnerRole.CUSTOMER); partnerRepository.saveAndFlush(customer);`. Note: this file already uses `companyCnpj` as a parameter name from the Empresa→Company sub-project — no parameter renames needed here, only the `Parceiro`/`PapelParceiro` substitutions.

- [ ] **`SaleRepositoryTest.java`**: private helper `createCustomer(UUID tenantId, String document)` returns `Parceiro` (this method name is ALREADY English — matches the design spec's observation that this file's fixtures predate this rename in English style). Return type → `Partner`, body updates `PapelParceiro.CLIENTE→PartnerRole.CUSTOMER`. Methods `createOrder(UUID tenantId, Parceiro customer, User salesperson, int number)` and `newSale(UUID tenantId, Pedido order, Parceiro customer, User salesperson, int number)` update their `Parceiro customer` parameter to `Partner customer`.

- [ ] **`SaleServiceTest.java`**: one helper method (around lines 101-107) with `PapelParceiro.CLIENTE`.

- [ ] **Step: Run the affected test suites**

Run: `cd mesh-suite-backend && mvn -q test -Dtest='PurchaseOrderControllerTest,PurchaseOrderRepositoryTest,PurchaseOrderServiceTest,SaleControllerTest,SaleRepositoryTest,SaleServiceTest'`
Expected: `BUILD SUCCESS`, all tests pass. No relocate-test-restore needed — after this task, no file anywhere in the backend should still reference `com.meshsuite.parceiro.*`.

- [ ] **Step: Verify the whole backend module now compiles with zero remaining `Parceiro` references**

Run:
```bash
grep -rn "com\.meshsuite\.parceiro\|\bParceiro\b\|\bParceiroRepository\b" mesh-suite-backend/src --include="*.java"
```
Expected: no output.

Run:
```bash
cd mesh-suite-backend && mvn -q test-compile
```
Expected: `BUILD SUCCESS` (whole module, no relocation needed).

- [ ] **Step: Commit**

```bash
git add mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder mesh-suite-backend/src/test/java/com/meshsuite/sale
git commit -m "refactor(partner): rename Parceiro references in purchaseorder and sale test fixtures"
```

---

### Task 8: Frontend `api/parceiros.ts` → `partners.ts`

**Files:**
- Create: `mesh-suite-frontend/src/api/partners.ts`
- Create: `mesh-suite-frontend/src/api/__tests__/partners.spec.ts`
- Delete: `mesh-suite-frontend/src/api/parceiros.ts`, `mesh-suite-frontend/src/api/__tests__/parceiros.spec.ts`

**Interfaces:**
- Produces: types `PersonType`, `PartnerRole`, `PartnerStatus`, `TaxIndicator`, `PartnerContact`, `PartnerRequest`, `PartnerResponse`, `PartnerListItem`, `ListPartnersParams`, `PartnerSummary`; functions `listPartners`, `getPartner`, `createPartner`, `updatePartner`, `updatePartnerStatus`, `deletePartner`, `getPartnerSummary` — consumed by Task 9's 6 bridge files.

- [ ] **Step 1: Delete the old files**

```bash
git rm mesh-suite-frontend/src/api/parceiros.ts
git rm mesh-suite-frontend/src/api/__tests__/parceiros.spec.ts
```

- [ ] **Step 2: Create `partners.ts`**

```typescript
import { apiClient } from './client'
import type { Page } from './types'

export type PersonType = 'INDIVIDUAL' | 'LEGAL_ENTITY'
export type PartnerRole = 'CUSTOMER' | 'SUPPLIER' | 'CARRIER'
export type PartnerStatus = 'ACTIVE' | 'AT_RISK' | 'BLOCKED'
export type TaxIndicator = 'NON_TAXPAYER' | 'TAXPAYER' | 'EXEMPT_TAXPAYER'

export interface PartnerContact {
  name: string
  email: string
  businessPhone: string
  mobilePhone: string
  jobTitle: string
}

export interface PartnerRequest {
  personType: PersonType
  document: string
  tradeName: string
  legalName: string
  roles: PartnerRole[]
  billingEmails: string
  whatsapp: string
  taxIndicator: TaxIndicator | null
  stateRegistration: string
  municipalRegistration: string
  suframaRegistration: string
  zipCode: string
  street: string
  number: string
  neighborhood: string
  complement: string
  state: string
  city: string
  notes: string
  contacts: PartnerContact[]
}

export interface PartnerResponse extends PartnerRequest {
  id: string
  status: PartnerStatus
}

export interface PartnerListItem {
  id: string
  tradeName: string
  legalName: string
  document: string
  personType: PersonType
  city: string
  state: string
  whatsapp: string
  status: PartnerStatus
}

export interface ListPartnersParams {
  busca?: string
  status?: PartnerStatus[]
  tipoDocumento?: PersonType[]
  documento?: string
  uf?: string[]
  cidade?: string[]
  papel?: PartnerRole
  page?: number
  size?: number
  sort?: string
}

export interface PartnerSummary {
  total: number
  active: number
  atRisk: number
  blocked: number
}

export async function listPartners(params: ListPartnersParams): Promise<Page<PartnerListItem>> {
  const { data } = await apiClient.get<Page<PartnerListItem>>('/partners', { params })
  return data
}

export async function getPartner(id: string): Promise<PartnerResponse> {
  const { data } = await apiClient.get<PartnerResponse>(`/partners/${id}`)
  return data
}

export async function createPartner(payload: PartnerRequest): Promise<PartnerResponse> {
  const { data } = await apiClient.post<PartnerResponse>('/partners', payload)
  return data
}

export async function updatePartner(id: string, payload: PartnerRequest): Promise<PartnerResponse> {
  const { data } = await apiClient.put<PartnerResponse>(`/partners/${id}`, payload)
  return data
}

export async function updatePartnerStatus(id: string, status: PartnerStatus): Promise<void> {
  await apiClient.patch(`/partners/${id}/status`, { status })
}

export async function deletePartner(id: string): Promise<void> {
  await apiClient.delete(`/partners/${id}`)
}

export async function getPartnerSummary(papel?: PartnerRole): Promise<PartnerSummary> {
  const { data } = await apiClient.get<PartnerSummary>('/partners/summary', { params: { papel } })
  return data
}
```

Note: `ListPartnersParams`' own field names (`busca`, `tipoDocumento`, `documento`, `uf`, `cidade`, `papel`) stay exactly as they are — they're serialized as query-string parameter names and must match `PartnerController`'s `@RequestParam` names from Task 4 verbatim, which this plan does not rename (see Task 4 Step 2's note). Do not translate them here even though it looks inconsistent with the rest of the file — that inconsistency already exists in the current codebase (e.g. `SaleControllerTest` already mixes `companyCnpj` with untranslated params) and is out of this plan's scope to fix.

- [ ] **Step 3: Create `partners.spec.ts` by mirroring `parceiros.spec.ts`**

Read `git show HEAD:mesh-suite-frontend/src/api/__tests__/parceiros.spec.ts` and recreate it at `mesh-suite-frontend/src/api/__tests__/partners.spec.ts` with the same test cases, updating: the import path (`'../parceiros'→'../partners'`), every function/type name per Step 2's map, and the two literal API paths/status values already known from the design spec's audit: `'/parceiros/abc-123/status'→'/partners/abc-123/status'`, `'/parceiros/resumo'→'/partners/summary'`, `'CLIENTE'→'CUSTOMER'`, `'BLOQUEADO'→'BLOCKED'`.

- [ ] **Step 4: Verify**

Run: `cd mesh-suite-frontend && npx vitest run partners`
Expected: all tests in `partners.spec.ts` pass.

Note: the rest of the frontend suite will NOT pass yet — `ClientesListView.vue`, `ClienteFormView.vue`, `ClienteDetailView.vue`, `DashboardView.vue`, `PedidoFormView.vue`, `PurchaseOrderFormView.vue` (and their specs) still import from the now-deleted `@/api/parceiros` — Task 9 fixes them. Do not run the full frontend suite yet.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/api/partners.ts mesh-suite-frontend/src/api/__tests__/partners.spec.ts \
        mesh-suite-frontend/src/api/parceiros.ts mesh-suite-frontend/src/api/__tests__/parceiros.spec.ts
git commit -m "refactor(partner): rename frontend api/parceiros.ts to partners.ts"
```

---

### Task 9: Frontend bridge — Cliente/Dashboard/Pedido/PurchaseOrder views

**Files:**
- Modify: `mesh-suite-frontend/src/views/ClientesListView.vue` (+ `__tests__/ClientesListView.spec.ts`)
- Modify: `mesh-suite-frontend/src/views/ClienteFormView.vue` (+ `__tests__/ClienteFormView.spec.ts`)
- Modify: `mesh-suite-frontend/src/views/ClienteDetailView.vue` (+ `__tests__/ClienteDetailView.spec.ts`)
- Modify: `mesh-suite-frontend/src/views/DashboardView.vue` (+ `__tests__/DashboardView.spec.ts`)
- Modify: `mesh-suite-frontend/src/views/PedidoFormView.vue` (+ `__tests__/PedidoFormView.spec.ts`)
- Modify: `mesh-suite-frontend/src/views/PurchaseOrderFormView.vue` (+ `__tests__/PurchaseOrderFormView.spec.ts`)

**Interfaces:**
- Consumes: everything exported by `partners.ts` (Task 8).

None of these 6 view files or their filenames are renamed in this task — only their imports, type names, and internal literal values that flow through the API contract. Visible Portuguese text (labels, the "Clientes" title, status labels "Ativo"/"Em Risco"/"Bloqueado", button labels "Ativar"/"Bloquear") is NOT touched anywhere in this task.

- [ ] **`ClientesListView.vue`**: change the import block
```typescript
import {
  listarParceiros,
  buscarResumoParceiros,
  atualizarStatusParceiro,
  excluirParceiro,
  type ParceiroSummary,
  type ParceiroResumo,
  type StatusParceiro,
} from '@/api/parceiros'
```
to:
```typescript
import {
  listPartners,
  getPartnerSummary,
  updatePartnerStatus,
  deletePartner,
  type PartnerListItem,
  type PartnerSummary,
  type PartnerStatus,
} from '@/api/partners'
```
Then update every call site: `listarParceiros(...)→listPartners(...)`, `buscarResumoParceiros('CLIENTE')→getPartnerSummary('CUSTOMER')`, `atualizarStatusParceiro(...)→updatePartnerStatus(...)`, `excluirParceiro(...)→deletePartner(...)`. Update type annotations: `ParceiroSummary→PartnerListItem`, `ParceiroResumo→PartnerSummary`, `StatusParceiro→PartnerStatus`. The literal `Record<string, StatusParceiro>` map:
```typescript
const STATUS_LABELS: Record<string, StatusParceiro> = { Ativo: 'ATIVO', 'Em Risco': 'EM_RISCO', Bloqueado: 'BLOQUEADO' }
```
becomes:
```typescript
const STATUS_LABELS: Record<string, PartnerStatus> = { Ativo: 'ACTIVE', 'Em Risco': 'AT_RISK', Bloqueado: 'BLOCKED' }
```
(the map's KEYS — `Ativo`, `Em Risco`, `Bloqueado` — are the Portuguese display labels and stay unchanged; only the VALUES, which are API enum values, translate). Every other literal `'ATIVO'`/`'EM_RISCO'`/`'BLOQUEADO'`/`'CLIENTE'` used as a comparison or API argument (in `statusLabel`, `statusColor`, the `papel: 'CLIENTE'` filter param, `alternarStatus`'s `parceiro.status === 'BLOQUEADO' ? 'ATIVO' : 'BLOQUEADO'`) updates to the matching English value. The local variable name `parceiro` (used throughout as a loop/parameter variable, e.g. `v-for="parceiro in pagina.content"`, `function acoesPara(parceiro: PartnerListItem)`) stays unchanged — it's a local identifier, not a type or API field, same reasoning as `cliente`/`fornecedor`/`supplier` elsewhere in this plan.

- [ ] **`ClienteFormView.vue`**: change the import
```typescript
import {
  buscarParceiro,
  criarParceiro,
  atualizarParceiro,
  type ParceiroRequest,
  type PapelParceiro,
} from '@/api/parceiros'
```
to:
```typescript
import {
  getPartner,
  createPartner,
  updatePartner,
  type PartnerRequest,
  type PartnerRole,
} from '@/api/partners'
```
Update call sites: `buscarParceiro(id)→getPartner(id)`, `atualizarParceiro(id, form)→updatePartner(id, form)`, `criarParceiro(form)→createPartner(form)`. Update `function novoFormulario(): ParceiroRequest` → `: PartnerRequest`, `reactive<ParceiroRequest>(...)→reactive<PartnerRequest>(...)`, `function togglePapel(papel: PapelParceiro)→function togglePapel(papel: PartnerRole)`. Update the two checkbox bindings:
```html
<input type="checkbox" :checked="form.papeis.includes('CLIENTE')" @change="togglePapel('CLIENTE')" />
<input type="checkbox" :checked="form.papeis.includes('FORNECEDOR')" @change="togglePapel('FORNECEDOR')" />
```
to:
```html
<input type="checkbox" :checked="form.roles.includes('CUSTOMER')" @change="togglePapel('CUSTOMER')" />
<input type="checkbox" :checked="form.roles.includes('FORNECEDOR')" @change="togglePapel('SUPPLIER')" />
```
(note: `form.papeis→form.roles` since `PartnerRequest.roles` is the renamed field). Update `papeis: ['CLIENTE']` (default form state) → `roles: ['CUSTOMER']`. Update the validation line `form.papeis.some((p) => p === 'CLIENTE' || p === 'FORNECEDOR')` → `form.roles.some((p) => p === 'CUSTOMER' || p === 'SUPPLIER')`. Every other field reference on `form.*` (e.g. `form.razaoSocial`, `form.nomeFantasia`, `form.documento`, `form.logradouro`, `form.numero`, `form.bairro`, `form.complemento`, `form.uf`, `form.cidade`, `form.cep`, `form.observacao`, `form.emailsCobranca`, `form.indicadorIe`, `form.inscricaoEstadual`, `form.inscricaoMunicipal`, `form.inscricaoSuframa`, `form.contatos`, and nested `contato.telefoneComercial`/`contato.telefoneCelular`/`contato.cargo`) must be updated to match `PartnerRequest`'s renamed fields from Task 8 (`legalName`, `tradeName`, `document`, `street`, `number`, `neighborhood`, `complement`, `state`, `city`, `zipCode`, `notes`, `billingEmails`, `taxIndicator`, `stateRegistration`, `municipalRegistration`, `suframaRegistration`, `contacts`, `businessPhone`/`mobilePhone`/`jobTitle`) — grep the file for `form\.` and `contato\.` to find every occurrence; there is no shortcut list here because this view binds nearly every field of the form. The error message literal `'Já existe um parceiro cadastrado com este documento.'` stays in Portuguese (visible to the user).

- [ ] **`ClienteDetailView.vue`**: change the import
```typescript
import { buscarParceiro, listarParceiros, type ParceiroResponse, type ParceiroSummary } from '@/api/parceiros'
```
to:
```typescript
import { getPartner, listPartners, type PartnerResponse, type PartnerListItem } from '@/api/partners'
```
Update call sites (`buscarParceiro(id)→getPartner(id)`, `listarParceiros({...})→listPartners({...})`) and type annotations (`ref<ParceiroResponse | null>→ref<PartnerResponse | null>`, `ref<ParceiroSummary[]>→ref<PartnerListItem[]>`). All template bindings reading `parceiro.nomeFantasia`, `parceiro.razaoSocial`, `parceiro.documento`, `parceiro.inscricaoEstadual`, `parceiro.logradouro`, `parceiro.numero`, `parceiro.bairro`, `parceiro.cidade`, `parceiro.uf`, `parceiro.cep`, `parceiro.contatos` update to the renamed fields (`tradeName`, `legalName`, `document`, `stateRegistration`, `street`, `number`, `neighborhood`, `city`, `state`, `zipCode`, `contacts`). The local variable/ref names `parceiro`, `parceiroId`, `carregarParceiro`, `listaRail` stay as local identifiers (not types or API fields) — only their assigned/declared TYPES change.

- [ ] **`DashboardView.vue`**: change the import
```typescript
import { buscarResumoParceiros, type ParceiroResumo } from '@/api/parceiros'
```
to:
```typescript
import { getPartnerSummary, type PartnerSummary } from '@/api/partners'
```
Update: `ref<ParceiroResumo | null>→ref<PartnerSummary | null>`, `buscarResumoParceiros()→getPartnerSummary()`, and the field read `parceiroResumo.value.ativos→parceiroResumo.value.active` (Task 3's `PartnerSummaryResponse.active` field). The local variable name `parceiroResumo`/`parceiroR` stays. The visible label `'Clientes Ativos'` stays unchanged.

- [ ] **`PedidoFormView.vue`**: change the import
```typescript
import { listarParceiros, type ParceiroSummary } from '@/api/parceiros'
```
to:
```typescript
import { listPartners, type PartnerListItem } from '@/api/partners'
```
Update: `ref<ParceiroSummary[]>→ref<PartnerListItem[]>`, `listarParceiros({ busca: clienteBusca.value, papel: 'CLIENTE', size: 5 })→listPartners({ busca: clienteBusca.value, papel: 'CUSTOMER', size: 5 })`, `function selecionarCliente(cliente: ParceiroSummary)→function selecionarCliente(cliente: PartnerListItem)`.

- [ ] **`PurchaseOrderFormView.vue`**: change the import
```typescript
import { listarParceiros, type ParceiroSummary } from '@/api/parceiros'
```
to:
```typescript
import { listPartners, type PartnerListItem } from '@/api/partners'
```
Update: `ref<ParceiroSummary[]>→ref<PartnerListItem[]>`, `listarParceiros({ busca: fornecedorBusca.value, papel: 'FORNECEDOR', size: 5 })→listPartners({ busca: fornecedorBusca.value, papel: 'SUPPLIER', size: 5 })`, `function selecionarFornecedor(fornecedor: ParceiroSummary)→function selecionarFornecedor(fornecedor: PartnerListItem)`.

- [ ] **Step: Update the 6 corresponding spec files**

For each of `ClientesListView.spec.ts`, `ClienteFormView.spec.ts`, `ClienteDetailView.spec.ts`, `DashboardView.spec.ts`, `PedidoFormView.spec.ts`, `PurchaseOrderFormView.spec.ts`: update the mocked module path (`vi.mock('@/api/parceiros', ...)→vi.mock('@/api/partners', ...)`), every mocked/asserted function and type name per the maps above, and every literal enum value/API path used in assertions (e.g. `expect(parceirosApi.listarParceiros).toHaveBeenLastCalledWith(expect.objectContaining({ papel: 'CLIENTE' }))→expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ papel: 'CUSTOMER' }))`, `status: ['ATIVO', 'BLOQUEADO']→status: ['ACTIVE', 'BLOCKED']`, fixture objects with `razaoSocial`/`status: 'ATIVO'`/`papeis: ['CLIENTE']` → `legalName`/`status: 'ACTIVE'`/`roles: ['CUSTOMER']`). Test case structure and assertions on VISIBLE rendered text (e.g. asserting the DOM shows "Ativo") do not change — only the underlying data/API layer they mock and feed in.

- [ ] **Step: Verify**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all test files pass (44 files, same count as before this sub-project started — no tests added or removed).

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`
(Always use the `-p tsconfig.app.json` flag — plain `npx vue-tsc --noEmit` silently reports 0 errors regardless of real breakage in this project.)
Expected: 0 errors.

- [ ] **Step: Commit**

```bash
git add mesh-suite-frontend/src/views/ClientesListView.vue mesh-suite-frontend/src/views/ClienteFormView.vue \
        mesh-suite-frontend/src/views/ClienteDetailView.vue mesh-suite-frontend/src/views/DashboardView.vue \
        mesh-suite-frontend/src/views/PedidoFormView.vue mesh-suite-frontend/src/views/PurchaseOrderFormView.vue \
        mesh-suite-frontend/src/views/__tests__/ClientesListView.spec.ts mesh-suite-frontend/src/views/__tests__/ClienteFormView.spec.ts \
        mesh-suite-frontend/src/views/__tests__/ClienteDetailView.spec.ts mesh-suite-frontend/src/views/__tests__/DashboardView.spec.ts \
        mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts mesh-suite-frontend/src/views/__tests__/PurchaseOrderFormView.spec.ts
git commit -m "refactor(partner): update Cliente/Dashboard/Pedido/PurchaseOrder views to consume the renamed partners API"
```

---

### Task 10: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Confirm no `parceiro`/`Parceiro` code traces remain anywhere (backend + frontend), Portuguese business data and UI text excepted**

Run:
```bash
grep -ril "com\.meshsuite\.parceiro\|\bParceiro\b\|ParceiroRepository\|PapelParceiro\|StatusParceiro" mesh-suite-backend/src --include="*.java"
```
Expected: no output.

Run:
```bash
grep -rl "parceiro" mesh-suite-backend/src/main/resources
```
Expected: no output.

Run:
```bash
grep -ril "parceiro\|ParceiroSummary\|ParceiroRequest\|ParceiroResponse" mesh-suite-frontend/src --include="*.ts" --include="*.vue"
```
Expected: no output.

Run:
```bash
grep -rln "'Outra Empresa'" mesh-suite-backend/src/test/java/com/meshsuite/tenant/repository/TenantRepositoryTest.java
```
Expected: exactly this one file — confirms the pre-existing, unrelated business-data literal from the Empresa sub-project is still untouched (a sanity check that this task's broad greps aren't accidentally over-matching unrelated content).

- [ ] **Step 2: Confirm exactly one V5 migration file**

Run: `ls mesh-suite-backend/src/main/resources/db/migration/ | grep V5__`
Expected: exactly one line, `V5__create_partner.sql`.

- [ ] **Step 3: Run the full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`
Expected: `BUILD SUCCESS` except the pre-existing, unrelated test-isolation flake — 0 failures, 15 errors, 12 in `com.meshsuite.payable.*` and 3 in `CompanyRepositoryTest` (see Global Constraints — this exact signature was confirmed pre-existing on `main` before this sub-project began; it is not caused by this plan). If the error count, module, or class names differ from this signature, stop and investigate — it likely means a rename in this plan is incomplete somewhere the earlier greps didn't catch (e.g. a fixture literal string that doesn't contain the literal substring "parceiro").

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
