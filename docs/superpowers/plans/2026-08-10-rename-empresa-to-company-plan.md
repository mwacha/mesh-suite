# Rename Empresa → Company Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the `empresa` module (Java package, DB table, and every cross-module reference) to English (`company`), keeping every user-visible route/label/message in Portuguese.

**Architecture:** Pure mechanical rename — no behavior changes. `Empresa` is a small module (one entity, one repository, no controller/service of its own) but is referenced by 22 files across the backend, mostly test fixtures that create an `Empresa` as part of a login setup. Each task moves/edits a cluster of files, renaming identifiers per the mapping below, keeping the build green at the end of each task.

**Tech Stack:** Same as the rest of the repo — no new dependencies.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-10-rename-empresa-to-company-design.md` — read it if anything below is ambiguous.
- No user-visible text changes: the sidebar label "Empresa", the topbar text "Empresa Principal", and every Portuguese error message stay exactly as they are. Only code identifiers change.
- `razaoSocial` → `legalName` everywhere (field, column, setter/getter, method parameters).
- `cnpj` stays `cnpj` — a Brazilian legal/fiscal acronym, same precedent as ICMS/IPI/PIS/COFINS in the prior Venda→Sale rename (never translate Brazilian legal acronyms).
- `ativo` (boolean field) → `active`; its Lombok-generated getter goes from `isAtivo()` to `isActive()` — but only for `Empresa`/`Company`'s OWN field. `Tenant.isAtivo()` (a different class, out of scope) must NOT be touched — watch for this specifically in `AuthService.java:101`, which calls `loaded.tenant().isAtivo()` (Tenant's field, leave alone) in the same method that also touches Empresa/Company.
- Any local variable/parameter name containing "empresa" as a naming component (e.g. `cnpjEmpresa`) is a code identifier and must become English too (`companyCnpj`) — this already happened organically in `SaleControllerTest.java` during the prior rename (it already uses `companyCnpj`, not `cnpjEmpresa`) — this plan brings every other file to that same convention.
- Migration `V2__create_empresa.sql` is edited in place and renamed to `V2__create_company.sql` (not a new `ALTER TABLE RENAME` migration) — this repo has no production data yet. Requires resetting the local Postgres volume (`docker-compose down -v` then back up) to reapply migrations from scratch.
- Every renamed file keeps the exact same test coverage it has today — only names change.

---

### Task 1: Migration rename

**Files:**
- Delete: `mesh-suite-backend/src/main/resources/db/migration/V2__create_empresa.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V2__create_company.sql`

**Interfaces:**
- Produces: DB table `company` (columns below), replacing `empresa`.

- [ ] **Step 1: `git mv` and rewrite the migration**

```bash
git mv mesh-suite-backend/src/main/resources/db/migration/V2__create_empresa.sql \
       mesh-suite-backend/src/main/resources/db/migration/V2__create_company.sql
```

```sql
-- mesh-suite-backend/src/main/resources/db/migration/V2__create_company.sql
CREATE TABLE company (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    legal_name VARCHAR(255) NOT NULL,
    cnpj VARCHAR(14) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_company_tenant_id ON company(tenant_id);

ALTER TABLE company ENABLE ROW LEVEL SECURITY;
-- FORCE so the policy also applies to the table owner (the role the app
-- connects as); without FORCE, RLS is bypassed for the owning role.
ALTER TABLE company FORCE ROW LEVEL SECURITY;

-- current_setting(..., true) returns NULL instead of raising when the
-- session var isn't set, so an unset app.tenant_id safely denies all rows
-- (NULL = tenant_id is never true) rather than erroring out. NULLIF(...,'')
-- covers a second case: Postgres custom GUCs that were SET earlier in the
-- session and then RESET come back as an empty string, not NULL -- without
-- the NULLIF guard, ::uuid would raise "invalid input syntax for type uuid"
-- instead of denying the row.
CREATE POLICY company_tenant_isolation ON company
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

- [ ] **Step 2: Reset the local database so Flyway reapplies migrations from scratch**

Run: `docker-compose down -v && docker-compose up -d` (from the repo root). Local dev volume only, no production data. If you don't have the stack running locally, skip this — Testcontainers always starts fresh.

- [ ] **Step 3: Verify the file is at the right path**

Run: `ls mesh-suite-backend/src/main/resources/db/migration/ | grep -i company` (expect `V2__create_company.sql`) and `ls mesh-suite-backend/src/main/resources/db/migration/ | grep -i empresa` (expect nothing).

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/
git commit -m "refactor(company): rename V2 migration from empresa to company (table, columns, indexes, RLS policy)"
```

---

### Task 2: Domain, repository, and repository test rename

**Files:**
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/empresa/domain/Empresa.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/company/domain/Company.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/empresa/repository/EmpresaRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/company/repository/CompanyRepository.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/empresa/repository/EmpresaRepositoryTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/company/repository/CompanyRepositoryTest.java`

**Interfaces:**
- Produces: `Company` (`getId/setId`, `getTenantId/setTenantId`, `getLegalName/setLegalName`, `getCnpj/setCnpj`, `isActive/setActive`). `CompanyRepository extends JpaRepository<Company, UUID>` with `findByTenantId(UUID tenantId): List<Company>`.

- [ ] **Step 1: `git mv` and write the failing test**

```bash
mkdir -p mesh-suite-backend/src/main/java/com/meshsuite/company/domain \
         mesh-suite-backend/src/main/java/com/meshsuite/company/repository \
         mesh-suite-backend/src/test/java/com/meshsuite/company/repository

git mv mesh-suite-backend/src/main/java/com/meshsuite/empresa/domain/Empresa.java \
       mesh-suite-backend/src/main/java/com/meshsuite/company/domain/Company.java
git mv mesh-suite-backend/src/main/java/com/meshsuite/empresa/repository/EmpresaRepository.java \
       mesh-suite-backend/src/main/java/com/meshsuite/company/repository/CompanyRepository.java
git mv mesh-suite-backend/src/test/java/com/meshsuite/empresa/repository/EmpresaRepositoryTest.java \
       mesh-suite-backend/src/test/java/com/meshsuite/company/repository/CompanyRepositoryTest.java
```

```java
// mesh-suite-backend/src/test/java/com/meshsuite/company/repository/CompanyRepositoryTest.java
package com.meshsuite.company.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class CompanyRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        // Tenant has no RLS (it's the tenant-defining table), so this insert needs
        // no app.tenant_id session var.
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    // The company_tenant_isolation policy has no explicit WITH CHECK, so Postgres
    // reuses its USING expression for INSERT too: writing a row now requires
    // app.tenant_id to already equal that row's tenant_id, not just reading one.
    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    @Test
    @Transactional
    void savesCompanyForTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Confecção Aurora Ltda");
        company.setCnpj("11222333000144");

        Company saved = companyRepository.save(company);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @Transactional
    void rejectsDuplicateCnpjAcrossTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        Company a = new Company();
        a.setTenantId(tenantA.getId());
        a.setLegalName("Confecção Aurora Ltda");
        a.setCnpj("11222333000144");
        companyRepository.saveAndFlush(a);

        setTenantContext(tenantB.getId());
        Company b = new Company();
        b.setTenantId(tenantB.getId());
        b.setLegalName("Confecção Boreal Ltda");
        b.setCnpj("11222333000144");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> companyRepository.saveAndFlush(b));
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Confecção Aurora Ltda");
        company.setCnpj("11222333000144");
        companyRepository.saveAndFlush(company);
        entityManager.clear();

        // RESET reverts the SET LOCAL above (back to no value, since it was never set
        // at session level either), simulating a query with no tenant context — RLS
        // denies every row.
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM company")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

Run: `cd mesh-suite-backend && mvn -q test-compile`
Expected: `COMPILATION ERROR` — `Company`/`CompanyRepository` don't exist yet.

- [ ] **Step 3: Rewrite `Company.java` and `CompanyRepository.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/company/domain/Company.java
package com.meshsuite.company.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "company")
@Getter
@Setter
public class Company {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(nullable = false)
    private boolean active = true;
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/company/repository/CompanyRepository.java
package com.meshsuite.company.repository;

import com.meshsuite.company.domain.Company;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findByTenantId(UUID tenantId);
}
```

- [ ] **Step 4: Remove the now-empty old directories, run the test**

```bash
rmdir mesh-suite-backend/src/main/java/com/meshsuite/empresa/domain \
      mesh-suite-backend/src/main/java/com/meshsuite/empresa/repository \
      mesh-suite-backend/src/test/java/com/meshsuite/empresa/repository 2>/dev/null || true
```

Run: `cd mesh-suite-backend && mvn -q test -Dtest=CompanyRepositoryTest`
Expected: `BUILD SUCCESS`, 3 tests passed. (This also validates Task 1's migration for the first time.) Note: `mvn compile` alone will still fail at this point — `AuthService.java`/`TenantQueryService.java` (Task 3) still reference the deleted `Empresa`/`EmpresaRepository`. That's expected; running the single test with `-Dtest` still requires the whole module to compile, so if this step fails to compile, check the error is confined to `AuthService.java`/`TenantQueryService.java` referencing `com.meshsuite.empresa.*` — if so, you'll need to do a minimal compile-bridge on those two files (update their imports and internal usage to the new `Company`/`CompanyRepository`/`getLegalName`/`isActive` API, WITHOUT renaming their own methods like `saveEmpresa`/`listEmpresas` yet — that full rename is Task 3's job). If the error is anything else, stop and report BLOCKED.

- [ ] **Step 5: Commit**

```bash
git add -A mesh-suite-backend/src/main/java/com/meshsuite/empresa mesh-suite-backend/src/main/java/com/meshsuite/company \
           mesh-suite-backend/src/test/java/com/meshsuite/empresa mesh-suite-backend/src/test/java/com/meshsuite/company
git commit -m "refactor(company): rename Empresa/EmpresaRepository domain and repository layer to English"
```

---

### Task 3: `auth` module cleanup — `AuthService`, `TenantQueryService`, and their dependent tests

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/service/AuthService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/service/TenantQueryService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/controller/AuthControllerNoAmbientTransactionTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/controller/AuthControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/controller/PasswordResetControllerNoAmbientTransactionTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/TenantIsolationTest.java`

**Interfaces:**
- Consumes: `Company`, `CompanyRepository` (Task 2).
- Produces: `TenantQueryService.saveCompany(UUID tenantId, String legalName, String cnpj)`, `TenantQueryService.listCompanies(): List<Company>`. `AuthService.LoginResult(User user, Tenant tenant, Company company)`, `AuthService.loadTenantAndCompany(UUID tenantId)`.

- [ ] **Step 1: Rewrite `TenantQueryService.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/auth/service/TenantQueryService.java
package com.meshsuite.auth.service;

import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantQueryService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public TenantQueryService(CompanyRepository companyRepository, UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void saveCompany(UUID tenantId, String legalName, String cnpj) {
        Company company = new Company();
        company.setTenantId(tenantId);
        company.setLegalName(legalName);
        company.setCnpj(cnpj);
        companyRepository.saveAndFlush(company);
    }

    @Transactional
    public void saveUser(UUID tenantId, String name, String email, Role role) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        userRepository.saveAndFlush(user);
    }

    @Transactional(readOnly = true)
    public List<Company> listCompanies() {
        return companyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }
}
```

- [ ] **Step 2: Rewrite `AuthService.java`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/auth/service/AuthService.java
package com.meshsuite.auth.service;

import com.meshsuite.auth.aspect.TenantContextAspect;
import com.meshsuite.auth.controller.AuthController;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final AuthService self;

    // Self-injection: `authenticate()` is called externally (from AuthController,
    // a different bean) so it goes through this class's real Spring proxy -- but
    // calling `this.findByEmailForLogin(...)` etc. from inside it would be a plain
    // Java self-invocation, which bypasses that proxy entirely. @Transactional (and
    // TenantContextAspect, which relies on @Transactional's own proxying) would
    // have no effect on such a call outside of an already-active transaction --
    // which is exactly the case for a real login request (AuthController isn't
    // @Transactional, and application.yml disables open-in-view, so nothing
    // pre-opens one). A @Lazy self-reference lets internal calls go through
    // `self.` instead, routing them through the real proxy so @Transactional
    // actually applies. @Lazy avoids a circular-construction failure (Spring can't
    // otherwise build a bean that depends on itself).
    public AuthService(UserRepository userRepository, TenantRepository tenantRepository,
                        CompanyRepository companyRepository, PasswordEncoder passwordEncoder,
                        EntityManager entityManager, @Lazy AuthService self) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
        this.self = self;
    }

    public record LoginResult(User user, Tenant tenant, Company company) {
    }

    private record TenantAndCompany(Tenant tenant, Company company) {
    }

    // Runs before the caller's tenant is known -- needs the app_user_login_lookup
    // RLS policy (SET LOCAL app.bypass_tenant_check below).
    @Transactional(readOnly = true)
    public User findByEmailForLogin(String email) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        User user = userRepository.findByEmail(email).orElse(null);
        // In production this method's @Transactional always starts a fresh physical
        // transaction (see the self-injection note above), so SET LOCAL naturally
        // expires at commit and this RESET is a no-op. It matters when this method
        // runs inside an already-active, longer-lived transaction -- e.g. a
        // @Transactional integration test that shares one physical transaction across
        // several "requests" -- where, without this, the bypass flag would otherwise
        // leak into every later query on app_user for the rest of that transaction
        // and silently defeat its tenant-isolation RLS policy.
        entityManager.createNativeQuery("RESET app.bypass_tenant_check").executeUpdate();
        return user;
    }

    // Used by PasswordResetService.confirmReset: a reset token identifies a user id
    // but not a tenant, so this lookup is also pre-tenant-context and needs the same
    // bypass. Reuses app_user_login_lookup -- that policy is unconditional on the
    // flag, not scoped to email lookups specifically.
    @Transactional(readOnly = true)
    public User findUserByIdBypassingTenant(UUID userId) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        User user = userRepository.findById(userId).orElse(null);
        // See the matching comment in findByEmailForLogin above.
        entityManager.createNativeQuery("RESET app.bypass_tenant_check").executeUpdate();
        return user;
    }

    public LoginResult authenticate(String email, String senha) {
        User user = self.findByEmailForLogin(email);
        if (user == null || !passwordEncoder.matches(senha, user.getPasswordHash()) || !user.isActive()) {
            throw new AuthException();
        }

        TenantContext.set(user.getTenantId());
        try {
            TenantAndCompany loaded = self.loadTenantAndCompany(user.getTenantId());
            if (loaded == null || !loaded.tenant().isAtivo() || loaded.company() == null) {
                throw new AuthException();
            }

            self.registerAcesso(user.getId());
            return new LoginResult(user, loaded.tenant(), loaded.company());
        } finally {
            TenantContext.clear();
        }
    }

    // Consolidates the tenant+company lookups into one plain, hand-written
    // @Transactional method, proven to work with TenantContextAspect.
    @Transactional(readOnly = true)
    public TenantAndCompany loadTenantAndCompany(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return null;
        }
        List<Company> companies = companyRepository.findByTenantId(tenantId);
        Company company = companies.isEmpty() ? null : companies.get(0);
        return new TenantAndCompany(tenant, company);
    }

    @Transactional
    public void registerAcesso(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setLastAccessAt(Instant.now());
        userRepository.save(user);
    }
}
```

Note: `loaded.tenant().isAtivo()` is UNCHANGED — that's `Tenant`'s own field (a different, not-yet-renamed module), not `Company`'s. Do not touch it.

- [ ] **Step 3: Update the 4 dependent test files**

In `mesh-suite-backend/src/test/java/com/meshsuite/auth/controller/AuthControllerNoAmbientTransactionTest.java`, change:
```java
import com.meshsuite.empresa.domain.Empresa;
import com.meshsuite.empresa.repository.EmpresaRepository;
```
to:
```java
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
```
Change `@Autowired EmpresaRepository empresaRepository;` to `@Autowired CompanyRepository companyRepository;`. Change:
```java
            Empresa empresa = new Empresa();
            empresa.setTenantId(tenant.getId());
            empresa.setRazaoSocial("No-Tx Empresa " + suffix);
            empresa.setCnpj(cnpj);
            empresaRepository.saveAndFlush(empresa);
```
to:
```java
            Company company = new Company();
            company.setTenantId(tenant.getId());
            company.setLegalName("No-Tx Company " + suffix);
            company.setCnpj(cnpj);
            companyRepository.saveAndFlush(company);
```

In `mesh-suite-backend/src/test/java/com/meshsuite/auth/controller/AuthControllerTest.java`, change the same two import lines and the field line as above. Change all THREE occurrences of the block (they appear at three call sites with different literal values — keep each literal exactly as it is, only rename identifiers):
```java
        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Aurora Ltda");
        empresa.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(empresa);
```
to:
```java
        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Aurora Ltda");
        company.setCnpj("11222333000144");
        companyRepository.saveAndFlush(company);
```
(same pattern for the `"Aurora Ltda"`/`"11222333000155"` block and the `"Boreal Ltda"`/`"55666777000155"` block — only the identifiers change, the literal string/cnpj arguments stay exactly as they are in each of the three occurrences).

In `mesh-suite-backend/src/test/java/com/meshsuite/auth/controller/PasswordResetControllerNoAmbientTransactionTest.java`, change the same two import lines and field line. Change:
```java
            Empresa empresa = new Empresa();
            empresa.setTenantId(tenant.getId());
            empresa.setRazaoSocial("Reset No-Tx Empresa " + suffix);
            empresa.setCnpj(cnpj);
            empresaRepository.saveAndFlush(empresa);
```
to:
```java
            Company company = new Company();
            company.setTenantId(tenant.getId());
            company.setLegalName("Reset No-Tx Company " + suffix);
            company.setCnpj(cnpj);
            companyRepository.saveAndFlush(company);
```

In `mesh-suite-backend/src/test/java/com/meshsuite/auth/TenantIsolationTest.java`, change the same two import lines and field line. Change:
```java
        tenantQueryService.saveEmpresa(tenantA.getId(), "Aurora Ltda", "11222333000144");
```
to:
```java
        tenantQueryService.saveCompany(tenantA.getId(), "Aurora Ltda", "11222333000144");
```
Change:
```java
        tenantQueryService.saveEmpresa(tenantB.getId(), "Boreal Ltda", "55666777000188");
```
to:
```java
        tenantQueryService.saveCompany(tenantB.getId(), "Boreal Ltda", "55666777000188");
```
Change:
```java
        assertThat(tenantQueryService.listEmpresas()).extracting(Empresa::getCnpj).containsExactly("11222333000144");
```
to:
```java
        assertThat(tenantQueryService.listCompanies()).extracting(Company::getCnpj).containsExactly("11222333000144");
```
Change:
```java
        assertThat(tenantQueryService.listEmpresas()).extracting(Empresa::getCnpj).containsExactly("55666777000188");
```
to:
```java
        assertThat(tenantQueryService.listCompanies()).extracting(Company::getCnpj).containsExactly("55666777000188");
```

- [ ] **Step 4: Run the auth test suite**

Run: `cd mesh-suite-backend && mvn -q test -Dtest='com.meshsuite.auth.**'`
Expected: `BUILD SUCCESS`, all auth tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A mesh-suite-backend/src/main/java/com/meshsuite/auth/service/AuthService.java \
           mesh-suite-backend/src/main/java/com/meshsuite/auth/service/TenantQueryService.java \
           mesh-suite-backend/src/test/java/com/meshsuite/auth
git commit -m "refactor(company): rename Empresa references in auth module (saveEmpresa->saveCompany, listEmpresas->listCompanies, TenantAndEmpresa->TenantAndCompany)"
```

---

### Task 4: Cross-module test batch 1 — municipio, parceiro, payable, pedido, categoria, cor-estampa

**Files:**
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/municipio/controller/MunicipioControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/controller/ParceiroControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/payable/controller/AccountsPayableControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/controller/PedidoControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CategoriaControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CorEstampaControllerTest.java`

**Interfaces:**
- Consumes: `Company`, `CompanyRepository` (Task 2).

For every file in this task, apply the same 4-part mechanical edit:

1. Change the two import lines:
```java
import com.meshsuite.empresa.domain.Empresa;
import com.meshsuite.empresa.repository.EmpresaRepository;
```
to:
```java
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
```

2. Change the autowired field:
```java
    @Autowired EmpresaRepository empresaRepository;
```
to:
```java
    @Autowired CompanyRepository companyRepository;
```

3. Wherever a method parameter is named `cnpjEmpresa`, rename it (and every use of it in that method's body) to `companyCnpj`.

4. Every occurrence of this 5-line block:
```java
        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial(<ARG1>);
        empresa.setCnpj(<ARG2>);
        empresaRepository.saveAndFlush(empresa);
```
becomes:
```java
        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName(<ARG1>);
        company.setCnpj(<ARG2>);
        companyRepository.saveAndFlush(company);
```
keeping `<ARG1>`/`<ARG2>` exactly as they are in each occurrence (after the `cnpjEmpresa`→`companyCnpj` rename from step 3, if that variable is used as `<ARG2>`).

**Per-file specifics** (exact `<ARG1>`/`<ARG2>` values and any other `cnpjEmpresa` usage, so there's no ambiguity):

- [ ] **`MunicipioControllerTest.java`**: no `cnpjEmpresa` parameter (skip step 3). One block: `empresa.setRazaoSocial("Aurora Ltda"); empresa.setCnpj("11222333000144");`.

- [ ] **`ParceiroControllerTest.java`**: two methods have a `cnpjEmpresa` parameter — `loginAndGetCookie(String codigo, String email, String cnpjEmpresa)` and `loginWithoutCustomerPermission(String codigo, String email, String cnpjEmpresa)`; rename the parameter to `companyCnpj` in both signatures. Both methods have one block each: `empresa.setRazaoSocial(codigo + " Ltda"); empresa.setCnpj(cnpjEmpresa);` → after rename, `company.setLegalName(codigo + " Ltda"); company.setCnpj(companyCnpj);`. Also update the comment at (approximately) line 279, which reads `// ParceiroRepositoryTest/EmpresaRepositoryTest/UsuarioRepositoryTest.` — change `EmpresaRepositoryTest` to `CompanyRepositoryTest` in that comment text.

- [ ] **`AccountsPayableControllerTest.java`**: `loginAndSetUp(String codigo, String email, String cnpjEmpresa)` — rename parameter to `companyCnpj`. Two blocks: first uses `empresa.setRazaoSocial(codigo + " Ltda"); empresa.setCnpj(cnpjEmpresa);`, second uses `empresa.setRazaoSocial("sem-permissao Ltda"); empresa.setCnpj("11222333000144");` (this second one has no `cnpjEmpresa` reference, leave its literal as-is). There's also an unrelated line `fornecedor.setDocumento(cnpjEmpresa.equals("11222333000144") ? "55666777000155" : "11222333000144");` — update `cnpjEmpresa` to `companyCnpj` there too (it's the same renamed parameter, referenced later in the method).

- [ ] **`PedidoControllerTest.java`**: two methods with `cnpjEmpresa` parameter — `loginAndSetUp(String codigo, String email, String cnpjEmpresa)` and `loginWithoutOrderPermission(String codigo, String email, String cnpjEmpresa)`; rename both to `companyCnpj`. Both have one block using `codigo + " Ltda"` / `cnpjEmpresa`. Also an unrelated line `cliente.setDocumento(cnpjEmpresa.equals("11222333000144") ? "55666777000155" : "11222333000144");` in the first method — update `cnpjEmpresa`→`companyCnpj` there too.

- [ ] **`CategoriaControllerTest.java`**: two methods with `cnpjEmpresa` parameter — `loginAndGetCookie(String codigo, String email, String cnpjEmpresa)` and `loginWithoutProductPermission(String codigo, String email, String cnpjEmpresa)`; rename both to `companyCnpj`. Both have one block using `codigo + " Ltda"` / `cnpjEmpresa`.

- [ ] **`CorEstampaControllerTest.java`**: same shape as `CategoriaControllerTest.java` — two methods (`loginAndGetCookie`, `loginWithoutProductPermission`), both with `cnpjEmpresa`→`companyCnpj`, one block each using `codigo + " Ltda"` / `cnpjEmpresa`.

- [ ] **Step: Run the affected test suites**

Run: `cd mesh-suite-backend && mvn -q test -Dtest='MunicipioControllerTest,ParceiroControllerTest,AccountsPayableControllerTest,PedidoControllerTest,CategoriaControllerTest,CorEstampaControllerTest'`
Expected: `BUILD SUCCESS`, all tests in these 6 classes pass.

- [ ] **Step: Commit**

```bash
git add mesh-suite-backend/src/test/java/com/meshsuite/municipio mesh-suite-backend/src/test/java/com/meshsuite/parceiro \
        mesh-suite-backend/src/test/java/com/meshsuite/payable mesh-suite-backend/src/test/java/com/meshsuite/pedido \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CategoriaControllerTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/CorEstampaControllerTest.java
git commit -m "refactor(company): rename Empresa references in municipio/parceiro/payable/pedido/categoria/cor-estampa controller tests"
```

---

### Task 5: Cross-module test batch 2 — produto, tabela-preco, purchase-order, sale, stock, user + TenantRepositoryTest comment + frontend CSS class

**Files:**
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/ProdutoControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/TabelaPrecoControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder/controller/PurchaseOrderControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/sale/controller/SaleControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/stock/controller/StockMovementControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/user/controller/UserControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/tenant/repository/TenantRepositoryTest.java`
- Modify: `mesh-suite-frontend/src/components/AppTopbar.vue`

**Interfaces:**
- Consumes: `Company`, `CompanyRepository` (Task 2).

Apply the same 4-part mechanical edit described in Task 4 to the first 6 files below. **Per-file specifics:**

- [ ] **`ProdutoControllerTest.java`**: two methods with `cnpjEmpresa` parameter — `loginAndGetCookie(String codigo, String email, String cnpjEmpresa)` and `loginWithoutProductPermission(String codigo, String email, String cnpjEmpresa)`; rename both to `companyCnpj`. Both have one block using `codigo + " Ltda"` / `cnpjEmpresa`.

- [ ] **`TabelaPrecoControllerTest.java`**: two methods with `cnpjEmpresa` parameter — `loginAndSetUp(String codigo, String email, String cnpjEmpresa)` and `loginWithoutProductPermission(String codigo, String email, String cnpjEmpresa)`; rename both to `companyCnpj`. Both have one block using `codigo + " Ltda"` / `cnpjEmpresa`.

- [ ] **`PurchaseOrderControllerTest.java`**: two methods with `cnpjEmpresa` parameter — `loginAndSetUp(String codigo, String email, String cnpjEmpresa)` and `loginWithoutPurchasePermission(String codigo, String email, String cnpjEmpresa)`; rename both to `companyCnpj`. Both have one block using `codigo + " Ltda"` / `cnpjEmpresa`. Also an unrelated line `supplier.setDocumento(cnpjEmpresa.equals("11222333000144") ? "55666777000155" : "11222333000144");` in the first method — update `cnpjEmpresa`→`companyCnpj` there too.

- [ ] **`SaleControllerTest.java`**: this file already uses `companyCnpj` as its parameter name (from the prior Venda→Sale rename) — **skip step 3 entirely, do not touch the parameter name.** Only apply steps 1, 2, and 4. Two blocks: `empresa.setRazaoSocial(code + " Ltda"); empresa.setCnpj(companyCnpj);` → `company.setLegalName(code + " Ltda"); company.setCnpj(companyCnpj);`, and `empresa.setRazaoSocial("sem-permissao-venda Ltda"); empresa.setCnpj("99888777000166");` → `company.setLegalName("sem-permissao-venda Ltda"); company.setCnpj("99888777000166");`.

- [ ] **`StockMovementControllerTest.java`**: `loginAndSetUp(String codigo, String email, String cnpjEmpresa)` — rename parameter to `companyCnpj`. Two blocks: first uses `codigo + " Ltda"` / `cnpjEmpresa`, second uses `"sem-permissao Ltda"` / `"11222333000144"` (no `cnpjEmpresa` reference in the second, leave literals as-is).

- [ ] **`UserControllerTest.java`**: `loginAndGetCookie(String codigo, String email, String cnpjEmpresa, boolean grantUserPermissions)` — rename the `cnpjEmpresa` parameter to `companyCnpj`. One block using `codigo + " Ltda"` / `cnpjEmpresa`.

- [ ] **`TenantRepositoryTest.java`**: this file does NOT use the `Empresa`/`EmpresaRepository` classes at all — its only match is the unrelated line `b.setNome("Outra Empresa");`, where `"Outra Empresa"` is a **string literal value** (a Tenant's `nome` field, used as sample test data, not a reference to the `Empresa` class). **Do not touch this line** — it's business data, not a code identifier.

- [ ] **`AppTopbar.vue`**: change the CSS class name (a code identifier, not visible text) in two places — the template's `<div class="empresa-badge">Empresa Principal</div>` becomes `<div class="company-badge">Empresa Principal</div>` (class renamed, visible text "Empresa Principal" stays exactly as-is), and the `<style scoped>` block's `.empresa-badge { ... }` selector becomes `.company-badge { ... }` (its declarations inside stay unchanged).

- [ ] **Step: Run the affected backend test suites**

Run: `cd mesh-suite-backend && mvn -q test -Dtest='ProdutoControllerTest,TabelaPrecoControllerTest,PurchaseOrderControllerTest,SaleControllerTest,StockMovementControllerTest,UserControllerTest,TenantRepositoryTest'`
Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step: Run the frontend suite for `AppTopbar`**

Run: `cd mesh-suite-frontend && npx vitest run AppTopbar` (if no dedicated spec file exists for `AppTopbar.vue`, run the full suite instead: `npx vitest run`). Expected: all tests pass — the class-name rename has no visible-DOM-text impact, so nothing should break.

- [ ] **Step: Commit**

```bash
git add mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/ProdutoControllerTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/controller/TabelaPrecoControllerTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/purchaseorder \
        mesh-suite-backend/src/test/java/com/meshsuite/sale \
        mesh-suite-backend/src/test/java/com/meshsuite/stock \
        mesh-suite-backend/src/test/java/com/meshsuite/user \
        mesh-suite-backend/src/test/java/com/meshsuite/tenant \
        mesh-suite-frontend/src/components/AppTopbar.vue
git commit -m "refactor(company): rename Empresa references in produto/tabela-preco/purchase-order/sale/stock/user controller tests and AppTopbar CSS class"
```

---

### Task 6: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Confirm no `empresa`/`Empresa` code traces remain (Portuguese UI text and unrelated string literals containing the word are fine)**

Run: `grep -ril "com\.meshsuite\.empresa\|\bEmpresa\b\|EmpresaRepository\|cnpjEmpresa" mesh-suite-backend/src --include="*.java"`
Expected: no output.

Run: `grep -rl "empresa-badge" mesh-suite-frontend/src`
Expected: no output.

- [ ] **Step 2: Run the full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`
Expected: `BUILD SUCCESS` except the pre-existing, unrelated `payable` module test-isolation flake (0 failures, exactly 12 errors, all in `com.meshsuite.payable.*` — same known signature documented in the prior Venda→Sale rename plan, unrelated to this change). If the error count or module differs from that signature, stop and investigate.

- [ ] **Step 3: Run the full frontend suite and type-check**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all tests pass.

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`
(Note: plain `npx vue-tsc --noEmit` without `-p` silently reports 0 errors regardless of real breakage in this project — always use the `-p tsconfig.app.json` flag.)
Expected: no errors.

- [ ] **Step 4: Confirm exactly one V2 migration file**

Run: `ls mesh-suite-backend/src/main/resources/db/migration/ | grep V2__`
Expected: exactly one line, `V2__create_company.sql`.

- [ ] **Step 5: Commit (only if Steps 1-4 required fixes) or confirm nothing to commit**

```bash
git status --short
```

If clean, no commit needed — this task is verification-only. If any fixes were required, commit them with a message describing exactly what was missed.
