# Cadastro Público de Tenant (Signup) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a visitor create a new tenant (empresa + primeiro usuário admin) through a public, unauthenticated signup form, gated by mandatory e-mail confirmation before the tenant can be used.

**Architecture:** A new `POST /api/auth/signup` endpoint creates `Tenant` (born `ativo=false`), `Company`, and `User` (role ADMIN, full permission grant) in one flow, then e-mails a confirmation link backed by a new `tenant_signup_token` table (same design as the existing `password_reset_token`). `POST /api/auth/confirm-signup` flips `tenant.ativo` to `true`, reusing the already-enforced "inactive tenant blocks login" rule — no new authorization logic needed. A resend-on-retry rule (CNPJ already registered but its tenant never confirmed) avoids a dead-end for anyone who abandons the form before confirming.

**Tech Stack:** Java 21, Spring Boot 3.3+, Spring Data JPA, Spring Security, PostgreSQL 16 (Row-Level Security), Flyway, JUnit 5 + Mockito + AssertJ + Testcontainers (backend); Vue 3 `<script setup>` + TypeScript, Vue Router, Pinia, Vitest + @vue/test-utils (frontend).

**Spec:** `docs/superpowers/specs/2026-09-16-cadastro-publico-tenant-design.md`

## Global Constraints

- Backend package root for all new code: `com.meshsuite.auth.*` (mirrors `PasswordResetService`/`PasswordResetToken`, not a new top-level domain).
- Every RLS-gated write/read must go through the existing `TenantContext` + `self.`-proxy + `@Transactional` pattern (`TenantContextAspect` only fires on a real Spring-proxied `@Transactional` call) — never call an RLS-gated repository method without either `TenantContext.set(...)` active or `app.bypass_tenant_check` explicitly set first.
- New public endpoints must be added to `SecurityConfig`'s `permitAll()` list — anything not listed there defaults to `authenticated()` and will 401.
- End-customer-visible strings (labels, error messages, e-mail copy) stay in Portuguese; new Java/TypeScript identifiers (classes, methods, variables) are in English, per project convention.
- No secrets or credentials committed to source — nothing in this plan introduces any (mail credentials/JWT secret are already environment-driven).
- Password rule: `@Size(min = 8)`, matching `ResetPasswordRequest`'s existing rule — do not invent a stricter policy.
- Frontend: run `npx vue-tsc -b` (not `--noEmit` alone — it misses real errors, see `mesh-suite-frontend`'s established gotcha) before considering any frontend task done.
- Backend: run the relevant Maven test class after every task; run the full `mvn test` suite only in the final verification task (it's slow — Testcontainers boots a real Postgres per class).

---

### Task 1: `tenant_signup_token` table, RLS lookup policy, domain, repository

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V45__create_tenant_signup_token.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V46__add_company_signup_lookup_policy.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/domain/TenantSignupToken.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/repository/TenantSignupTokenRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/repository/TenantSignupTokenRepositoryTest.java`

**Interfaces:**
- Produces: `TenantSignupToken` (getters/setters: `id: UUID`, `tenantId: UUID`, `tokenHash: String`, `expiraEm: Instant`, `usadoEm: Instant`, `criadoEm: Instant`); `TenantSignupTokenRepository.findByTokenHash(String): Optional<TenantSignupToken>`, `.save(TenantSignupToken): TenantSignupToken`. Consumed by Task 5.
- Produces: a new permissive RLS policy `company_signup_lookup` on `company` (`FOR SELECT USING (current_setting('app.bypass_tenant_check', true) = 'true')`), which Task 5's `findExistingSignup` relies on to read `company` before any tenant is known.

- [ ] **Step 1: Write the migration for the new token table**

```sql
-- mesh-suite-backend/src/main/resources/db/migration/V45__create_tenant_signup_token.sql
-- Mirrors password_reset_token exactly (see V4__create_password_reset_token.sql):
-- no RLS, because this is read before any tenant context exists in the session.
CREATE TABLE tenant_signup_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    token_hash VARCHAR(64) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_signup_token_tenant_id ON tenant_signup_token(tenant_id);
CREATE UNIQUE INDEX idx_tenant_signup_token_hash ON tenant_signup_token(token_hash);
```

- [ ] **Step 2: Write the migration for the company signup-lookup RLS policy**

```sql
-- mesh-suite-backend/src/main/resources/db/migration/V46__add_company_signup_lookup_policy.sql
-- Public signup needs to look up an existing Company by CNPJ *before* any tenant_id
-- is known (to detect "this CNPJ already signed up, resend the confirmation" vs.
-- "genuinely new"). company_tenant_isolation alone would hide every row in that
-- state (NULL app.tenant_id never matches). This adds a second PERMISSIVE policy
-- (Postgres ORs permissive policies for the same command), scoped to SELECT only,
-- gated by the same app.bypass_tenant_check flag AuthService.findAllByEmailForLogin
-- already uses for the equivalent app_user case (see app_user_login_lookup in
-- V3__create_usuario.sql).
CREATE POLICY company_signup_lookup ON company
    FOR SELECT
    USING (current_setting('app.bypass_tenant_check', true) = 'true');
```

- [ ] **Step 3: Write the `TenantSignupToken` entity**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/auth/domain/TenantSignupToken.java
package com.meshsuite.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_signup_token")
@Getter
@Setter
public class TenantSignupToken {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
```

- [ ] **Step 4: Write the repository interface**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/auth/repository/TenantSignupTokenRepository.java
package com.meshsuite.auth.repository;

import com.meshsuite.auth.domain.TenantSignupToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSignupTokenRepository extends JpaRepository<TenantSignupToken, UUID> {
    Optional<TenantSignupToken> findByTokenHash(String tokenHash);
}
```

- [ ] **Step 5: Write the failing repository test**

```java
// mesh-suite-backend/src/test/java/com/meshsuite/auth/repository/TenantSignupTokenRepositoryTest.java
package com.meshsuite.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.TenantSignupToken;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class TenantSignupTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    TenantSignupTokenRepository tokenRepository;

    @Test
    @Transactional
    void savesAndFindsByTokenHash() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora-signup");
        tenant.setNome("Aurora Signup");
        tenant.setAtivo(false);
        tenantRepository.saveAndFlush(tenant);

        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(tenant.getId());
        token.setTokenHash("abc123hash");
        token.setExpiraEm(Instant.now().plus(24, ChronoUnit.HOURS));

        tokenRepository.save(token);

        assertThat(tokenRepository.findByTokenHash("abc123hash")).isPresent();
        assertThat(tokenRepository.findByTokenHash("no-such-hash")).isEmpty();
    }
}
```

- [ ] **Step 6: Run it to confirm it passes (this test has no separate "should fail first" step — the migration and entity are created together, so there's no meaningful red state; run once to confirm everything wires up)**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=TenantSignupTokenRepositoryTest`
Expected: PASS. If it fails on Flyway checksum/order, confirm `V45`/`V46` are the next free version numbers (`V44` is the current latest — check `ls src/main/resources/db/migration/ | sort -V | tail -5` if unsure).

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V45__create_tenant_signup_token.sql \
        mesh-suite-backend/src/main/resources/db/migration/V46__add_company_signup_lookup_policy.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/domain/TenantSignupToken.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/repository/TenantSignupTokenRepository.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/repository/TenantSignupTokenRepositoryTest.java
git commit -m "feat(auth): add tenant_signup_token table and company signup-lookup RLS policy"
```

---

### Task 2: Repository method additions (`existsByCodigo`, `findByCnpj`, `findFirstByTenantIdAndRole`)

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/tenant/repository/TenantRepository.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/company/repository/CompanyRepository.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/repository/UserRepository.java`
- Modify (add test methods): `mesh-suite-backend/src/test/java/com/meshsuite/tenant/repository/TenantRepositoryTest.java`
- Modify (add test methods): `mesh-suite-backend/src/test/java/com/meshsuite/company/repository/CompanyRepositoryTest.java`
- Modify (add test methods): `mesh-suite-backend/src/test/java/com/meshsuite/user/repository/UserRepositoryTest.java`

**Interfaces:**
- Consumes: nothing new from Task 1.
- Produces: `TenantRepository.existsByCodigo(String): boolean`; `CompanyRepository.findByCnpj(String): Optional<Company>`; `UserRepository.findFirstByTenantIdAndRole(UUID, Role): Optional<User>`. All three consumed by Task 5's `TenantSignupService`.

- [ ] **Step 1: Add the failing test for `TenantRepository.existsByCodigo`**

Add to `mesh-suite-backend/src/test/java/com/meshsuite/tenant/repository/TenantRepositoryTest.java`, inside the `TenantRepositoryTest` class body (after `rejectsDuplicateCodigo`):

```java
    @Test
    void existsByCodigoReflectsWhatWasSaved() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("boreal");
        tenant.setNome("Confecção Boreal");
        tenantRepository.saveAndFlush(tenant);

        assertThat(tenantRepository.existsByCodigo("boreal")).isTrue();
        assertThat(tenantRepository.existsByCodigo("nao-existe")).isFalse();
    }
```

- [ ] **Step 2: Run it to confirm it fails to compile (method doesn't exist yet)**

Run: `cd mesh-suite-backend && ./mvnw test-compile`
Expected: FAIL — `cannot find symbol: method existsByCodigo`

- [ ] **Step 3: Add the method to `TenantRepository`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/tenant/repository/TenantRepository.java
package com.meshsuite.tenant.repository;

import com.meshsuite.tenant.domain.Tenant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsByCodigo(String codigo);
}
```

- [ ] **Step 4: Run it to confirm it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=TenantRepositoryTest`
Expected: PASS (all methods in the class, including the new one).

- [ ] **Step 5: Add the failing test for `CompanyRepository.findByCnpj`**

Add to `mesh-suite-backend/src/test/java/com/meshsuite/company/repository/CompanyRepositoryTest.java`, inside the class body (it already has `createTenant`/`setTenantContext` helpers — reuse them):

```java
    @Test
    @Transactional
    void findByCnpjBypassesTenantScopingWhenBypassFlagIsSet() {
        Tenant tenant = createTenant("aurora-findcnpj");
        setTenantContext(tenant.getId());

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Aurora Ltda");
        company.setCnpj("99888777000111");
        companyRepository.saveAndFlush(company);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        // No tenant context and no bypass flag: company_tenant_isolation hides the row.
        assertThat(companyRepository.findByCnpj("99888777000111")).isEmpty();

        // company_signup_lookup (Task 1) is a second PERMISSIVE policy gated by this
        // flag, ORed with company_tenant_isolation for SELECT.
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        assertThat(companyRepository.findByCnpj("99888777000111")).isPresent();
        entityManager.createNativeQuery("RESET app.bypass_tenant_check").executeUpdate();
    }
```

- [ ] **Step 6: Run it to confirm it fails to compile**

Run: `cd mesh-suite-backend && ./mvnw test-compile`
Expected: FAIL — `cannot find symbol: method findByCnpj`

- [ ] **Step 7: Add the method to `CompanyRepository`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/company/repository/CompanyRepository.java
package com.meshsuite.company.repository;

import com.meshsuite.company.domain.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyRepository extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {
    List<Company> findByTenantId(UUID tenantId);
    boolean existsByCnpj(String cnpj);
    boolean existsByCnpjAndIdNot(String cnpj, UUID id);
    Optional<Company> findByCnpj(String cnpj);
    long countByActive(boolean active);
    long countByTenantId(UUID tenantId);
}
```

- [ ] **Step 8: Run it to confirm it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=CompanyRepositoryTest`
Expected: PASS.

- [ ] **Step 9: Add the failing test for `UserRepository.findFirstByTenantIdAndRole`**

Add to `mesh-suite-backend/src/test/java/com/meshsuite/user/repository/UserRepositoryTest.java`, inside the class body (it already has `createTenant`/`setTenantContext` helpers):

```java
    @Test
    @Transactional
    void findFirstByTenantIdAndRoleFindsTheAdmin() {
        Tenant tenant = createTenant("aurora-findadmin");
        setTenantContext(tenant.getId());

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora-findadmin.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findFirstByTenantIdAndRole(tenant.getId(), Role.ADMIN))
                .isPresent()
                .get().extracting(User::getEmail).isEqualTo("marina@aurora-findadmin.com.br");
        assertThat(userRepository.findFirstByTenantIdAndRole(tenant.getId(), Role.SALES_REP)).isEmpty();
    }
```

- [ ] **Step 10: Run it to confirm it fails to compile**

Run: `cd mesh-suite-backend && ./mvnw test-compile`
Expected: FAIL — `cannot find symbol: method findFirstByTenantIdAndRole`

- [ ] **Step 11: Add the method to `UserRepository`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/user/repository/UserRepository.java
package com.meshsuite.user.repository;

import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    List<User> findAllByEmail(String email);
    List<User> findByRoleOrderByName(Role role);
    Optional<User> findFirstByTenantIdAndRole(UUID tenantId, Role role);
    long countByActive(boolean active);
    long countByPermissionProfileId(UUID permissionProfileId);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.permissions p " +
            "WHERE u.id = :userId AND p.module = :module AND p.action = :action")
    boolean hasPermission(@Param("userId") UUID userId, @Param("module") Module module, @Param("action") Action action);

    @Query("SELECT DISTINCT u FROM User u JOIN u.permissions p " +
            "WHERE u.role IN :roles AND p.module = :module AND p.action = :action " +
            "ORDER BY u.name")
    List<User> findByRoleInAndPermission(@Param("roles") List<Role> roles,
                                          @Param("module") Module module,
                                          @Param("action") Action action);
}
```

- [ ] **Step 12: Run it to confirm it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=UserRepositoryTest`
Expected: PASS.

- [ ] **Step 13: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/tenant/repository/TenantRepository.java \
        mesh-suite-backend/src/main/java/com/meshsuite/company/repository/CompanyRepository.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/repository/UserRepository.java \
        mesh-suite-backend/src/test/java/com/meshsuite/tenant/repository/TenantRepositoryTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/company/repository/CompanyRepositoryTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/user/repository/UserRepositoryTest.java
git commit -m "feat(auth): add repository lookups needed by tenant signup"
```

---

### Task 3: `MailService.sendSignupConfirmationEmail`

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/mail/service/MailService.java`

**Interfaces:**
- Produces: `MailService.sendSignupConfirmationEmail(String to, String confirmLink): void`. Consumed by Task 5.

No dedicated unit test for this method — `MailService` has no existing test file (its only method, `sendPasswordResetEmail`, is verified indirectly through `PasswordResetControllerTest`'s `@MockBean MailService`, not a direct unit test). Task 6's controller integration test verifies this new method the same way. This keeps the same convention rather than introducing a new one for a two-line method.

- [ ] **Step 1: Add the method**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/mail/service/MailService.java
package com.meshsuite.mail.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Redefinição de senha — Mesh Suite");
        message.setText("Clique no link para redefinir sua senha: " + resetLink +
                "\n\nSe você não solicitou isso, ignore este e-mail.");
        mailSender.send(message);
    }

    public void sendSignupConfirmationEmail(String to, String confirmLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Confirme seu cadastro — Mesh Suite");
        message.setText("Clique no link para confirmar seu cadastro e ativar sua conta: " + confirmLink +
                "\n\nSe você não solicitou isso, ignore este e-mail.");
        mailSender.send(message);
    }
}
```

- [ ] **Step 2: Compile to confirm no breakage**

Run: `cd mesh-suite-backend && ./mvnw compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/mail/service/MailService.java
git commit -m "feat(auth): add signup confirmation email"
```

---

### Task 4: `SignupRequest` and `ConfirmSignupRequest` DTOs

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/SignupRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/ConfirmSignupRequest.java`

**Interfaces:**
- Produces: `SignupRequest` record with accessors `legalName()`, `cnpj()`, `tradeName()`, `stateRegistration()`, `municipalRegistration()`, `phone()`, `email()`, `website()`, `zipCode()`, `street()`, `number()`, `complement()`, `neighborhood()`, `city()`, `state()`, `adminName()`, `adminEmail()`, `senha()`. `ConfirmSignupRequest` record with accessor `token()`. Both consumed by Task 5 (service) and Task 6 (controller).

Records with only `jakarta.validation` annotations — no dedicated unit test (matches `CompanyRequest`/`ResetPasswordRequest`, neither of which has one); validated indirectly through Task 6's controller test (a request missing a `@NotBlank` field should 400).

- [ ] **Step 1: Write `SignupRequest`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/SignupRequest.java
package com.meshsuite.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank String legalName,
        @NotBlank @Size(min = 14, max = 14) String cnpj,
        String tradeName,
        String stateRegistration,
        String municipalRegistration,
        String phone,
        String email,
        String website,
        String zipCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        @NotBlank String adminName,
        @NotBlank @Email String adminEmail,
        @NotBlank @Size(min = 8) String senha) {
}
```

- [ ] **Step 2: Write `ConfirmSignupRequest`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/ConfirmSignupRequest.java
package com.meshsuite.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmSignupRequest(@NotBlank String token) {
}
```

- [ ] **Step 3: Compile to confirm no breakage**

Run: `cd mesh-suite-backend && ./mvnw compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/SignupRequest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/ConfirmSignupRequest.java
git commit -m "feat(auth): add signup request DTOs"
```

---

### Task 5: `TenantSignupService`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/service/TenantSignupService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/service/TenantSignupServiceTest.java`

**Interfaces:**
- Consumes: `TenantRepository.existsByCodigo/findById/save/saveAndFlush` (Task 2 + existing), `CompanyRepository.findByCnpj/save` (Task 2 + existing), `UserRepository.findFirstByTenantIdAndRole/save` (Task 2 + existing), `TenantSignupTokenRepository.findByTokenHash/save` (Task 1), `MailService.sendSignupConfirmationEmail` (Task 3), `SignupRequest`/`ConfirmSignupRequest` (Task 4), `TenantContext.set/clear` (existing, `com.meshsuite.shared.context.TenantContext`), `AuthException` (existing, `com.meshsuite.auth.exception.AuthException`), `DuplicateCnpjException` (existing, `com.meshsuite.company.exception.DuplicateCnpjException`).
- Produces: `TenantSignupService.signup(SignupRequest): void` (throws `DuplicateCnpjException` when the CNPJ belongs to an already-confirmed tenant); `TenantSignupService.confirmSignup(String rawToken): void` (throws `AuthException` when the token is unknown, expired, or already used). Both consumed by Task 6.

- [ ] **Step 1: Write the failing unit tests**

```java
// mesh-suite-backend/src/test/java/com/meshsuite/auth/service/TenantSignupServiceTest.java
package com.meshsuite.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.meshsuite.auth.domain.TenantSignupToken;
import com.meshsuite.auth.dto.SignupRequest;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.auth.repository.TenantSignupTokenRepository;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.exception.DuplicateCnpjException;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.mail.service.MailService;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TenantSignupServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock CompanyRepository companyRepository;
    @Mock UserRepository userRepository;
    @Mock TenantSignupTokenRepository tokenRepository;
    @Mock MailService mailService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EntityManager entityManager;
    @Mock Query query;

    @BeforeEach
    void stubEntityManager() {
        // findExistingSignup always calls entityManager.createNativeQuery(...).executeUpdate()
        // (SET LOCAL / RESET on app.bypass_tenant_check) before any business logic runs. An
        // unstubbed Mockito mock returns null for an unstubbed method returning an object type,
        // so without this, .executeUpdate() NPEs on every signup() test. lenient() avoids a
        // strict-stubbing UnnecessaryStubbingException on the confirmSignup tests, which never
        // touch entityManager at all.
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    private TenantSignupService service() {
        TenantSignupService svc = new TenantSignupService(tenantRepository, companyRepository, userRepository,
                tokenRepository, mailService, passwordEncoder, entityManager);
        // Same rationale as PasswordResetServiceTest: plain Mockito test, no Spring
        // proxy, so `self` is pointed back at the same instance to simulate it.
        svc.self = svc;
        return svc;
    }

    private SignupRequest request(String cnpj) {
        return new SignupRequest("Confecção Aurora Ltda", cnpj, "Aurora", null, null, null, null, null,
                null, null, null, null, null, null, null,
                "Marina", "marina@aurora.com.br", "senha1234");
    }

    @Test
    void signupCreatesTenantCompanyAdminAndSendsConfirmation() {
        when(companyRepository.findByCnpj("11222333000144")).thenReturn(Optional.empty());
        when(tenantRepository.existsByCodigo(any())).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("hashed");
        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        service().signup(request("11222333000144"));

        verify(companyRepository).save(argThat(c -> c.getCnpj().equals("11222333000144")
                && c.getLegalName().equals("Confecção Aurora Ltda")));
        verify(userRepository).save(argThat(u -> u.getEmail().equals("marina@aurora.com.br")
                && u.getRole() == Role.ADMIN
                && u.getPasswordHash().equals("hashed")
                // USER+DELETE is deliberately excluded -- see the seed's own comment
                // (there is no hard delete for User) -- 35 grants, not 36.
                && u.getPermissions().size() == 35));
        verify(tokenRepository).save(any(TenantSignupToken.class));
        verify(mailService).sendSignupConfirmationEmail(eq("marina@aurora.com.br"), any());
    }

    @Test
    void signupThrowsDuplicateCnpjWhenExistingTenantIsAlreadyConfirmed() {
        UUID tenantId = UUID.randomUUID();
        Company existing = new Company();
        existing.setTenantId(tenantId);
        existing.setCnpj("11222333000144");
        when(companyRepository.findByCnpj("11222333000144")).thenReturn(Optional.of(existing));
        Tenant confirmedTenant = new Tenant();
        confirmedTenant.setId(tenantId);
        confirmedTenant.setAtivo(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(confirmedTenant));

        assertThrows(DuplicateCnpjException.class, () -> service().signup(request("11222333000144")));
        verify(companyRepository, never()).save(any());
        verify(mailService, never()).sendSignupConfirmationEmail(any(), any());
    }

    @Test
    void signupResendsConfirmationWithoutDuplicatingWhenExistingTenantIsNotYetConfirmed() {
        UUID tenantId = UUID.randomUUID();
        Company existing = new Company();
        existing.setTenantId(tenantId);
        existing.setCnpj("11222333000144");
        when(companyRepository.findByCnpj("11222333000144")).thenReturn(Optional.of(existing));
        Tenant unconfirmedTenant = new Tenant();
        unconfirmedTenant.setId(tenantId);
        unconfirmedTenant.setAtivo(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(unconfirmedTenant));
        User existingAdmin = new User();
        existingAdmin.setEmail("marina@aurora.com.br");
        when(userRepository.findFirstByTenantIdAndRole(tenantId, Role.ADMIN)).thenReturn(Optional.of(existingAdmin));

        service().signup(request("11222333000144"));

        verify(companyRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(tokenRepository).save(any(TenantSignupToken.class));
        verify(mailService).sendSignupConfirmationEmail(eq("marina@aurora.com.br"), any());
    }

    @Test
    void signupGeneratesSuffixedCodigoOnCollision() {
        when(companyRepository.findByCnpj(any())).thenReturn(Optional.empty());
        when(tenantRepository.existsByCodigo("confeccao-aurora-ltda")).thenReturn(true);
        when(tenantRepository.existsByCodigo("confeccao-aurora-ltda-2")).thenReturn(false);
        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        SignupRequest req = new SignupRequest("Confecção Aurora Ltda", "11222333000144", null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                "Marina", "marina@aurora.com.br", "senha1234");
        service().signup(req);

        verify(tenantRepository).saveAndFlush(argThat(t -> t.getCodigo().equals("confeccao-aurora-ltda-2")));
    }

    @Test
    void confirmSignupActivatesTenantAndMarksTokenUsed() {
        UUID tenantId = UUID.randomUUID();
        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(tenantId);
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setAtivo(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        service().confirmSignup("raw-token");

        verify(tenantRepository).save(argThat(Tenant::isAtivo));
        verify(tokenRepository).save(argThat(t -> t.getUsadoEm() != null));
    }

    @Test
    void confirmSignupThrowsAuthExceptionWhenTokenUnknown() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(AuthException.class, () -> service().confirmSignup("bogus"));
    }

    @Test
    void confirmSignupThrowsAuthExceptionWhenTokenExpired() {
        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(UUID.randomUUID());
        token.setExpiraEm(Instant.now().minus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(AuthException.class, () -> service().confirmSignup("expired"));
    }

    @Test
    void confirmSignupThrowsAuthExceptionWhenTokenAlreadyUsed() {
        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(UUID.randomUUID());
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
        token.setUsadoEm(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(AuthException.class, () -> service().confirmSignup("used"));
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail (class doesn't exist yet)**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=TenantSignupServiceTest`
Expected: FAIL — compile error, `TenantSignupService` doesn't exist.

- [ ] **Step 3: Write `TenantSignupService`**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/auth/service/TenantSignupService.java
package com.meshsuite.auth.service;

import com.meshsuite.auth.domain.TenantSignupToken;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.dto.SignupRequest;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.auth.repository.TenantSignupTokenRepository;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.exception.DuplicateCnpjException;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.mail.service.MailService;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSignupService {

    // Every Module x Action except USER+DELETE, which doesn't exist as an
    // operation (there is no hard delete for User) -- matches the ADMIN grant
    // R__seed_dev_tenant.sql gives its seeded users.
    private static final List<Module> ALL_MODULES = List.of(
            Module.CUSTOMER, Module.PRODUCT, Module.ORDER, Module.USER, Module.PURCHASE,
            Module.STOCK, Module.PAYABLE, Module.SALE, Module.PURCHASE_INVOICE);
    private static final List<Action> ALL_ACTIONS = List.of(Action.VIEW, Action.CREATE, Action.EDIT, Action.DELETE);

    private final TenantRepository tenantRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final TenantSignupTokenRepository tokenRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final SecureRandom secureRandom = new SecureRandom();

    // Field injection (not constructor), same reason as PasswordResetService.self:
    // lets TenantSignupServiceTest construct this class directly with mocks and
    // assign `self` manually. In production Spring wires this via @Lazy to avoid a
    // circular-construction failure. Package-private so the test (same package) can
    // assign it directly.
    @Autowired
    @Lazy
    TenantSignupService self;

    public TenantSignupService(TenantRepository tenantRepository, CompanyRepository companyRepository,
                                UserRepository userRepository, TenantSignupTokenRepository tokenRepository,
                                MailService mailService, PasswordEncoder passwordEncoder,
                                EntityManager entityManager) {
        this.tenantRepository = tenantRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    public void signup(SignupRequest request) {
        ExistingSignup existing = self.findExistingSignup(request.cnpj());
        if (existing != null) {
            if (existing.tenantAtivo()) {
                throw new DuplicateCnpjException();
            }
            issueTokenAndSendEmail(existing.tenantId(), existing.adminEmail());
            return;
        }

        Tenant tenant = new Tenant();
        tenant.setCodigo(generateUniqueCodigo(request.legalName(), request.tradeName()));
        tenant.setNome(request.legalName());
        tenant.setAtivo(false);
        tenantRepository.saveAndFlush(tenant);

        TenantContext.set(tenant.getId());
        try {
            self.createCompanyAndAdmin(tenant.getId(), request);
        } finally {
            TenantContext.clear();
        }

        issueTokenAndSendEmail(tenant.getId(), request.adminEmail());
    }

    public void confirmSignup(String rawToken) {
        TenantSignupToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(AuthException::new);
        if (token.getUsadoEm() != null || Instant.now().isAfter(token.getExpiraEm())) {
            throw new AuthException();
        }
        Tenant tenant = tenantRepository.findById(token.getTenantId()).orElseThrow(AuthException::new);
        tenant.setAtivo(true);
        tenantRepository.save(tenant);

        token.setUsadoEm(Instant.now());
        tokenRepository.save(token);
    }

    private record ExistingSignup(UUID tenantId, boolean tenantAtivo, String adminEmail) {
    }

    // Runs before any tenant is known -- same shape as AuthService.findAllByEmailForLogin:
    // SET LOCAL app.bypass_tenant_check so company_signup_lookup (Task 1) and the
    // existing app_user_login_lookup policy both let this read through, then RESET
    // so the flag doesn't leak into any later query on the same connection.
    @Transactional(readOnly = true)
    ExistingSignup findExistingSignup(String cnpj) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        Optional<Company> company = companyRepository.findByCnpj(cnpj);
        if (company.isEmpty()) {
            entityManager.createNativeQuery("RESET app.bypass_tenant_check").executeUpdate();
            return null;
        }
        UUID tenantId = company.get().getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(DuplicateCnpjException::new);
        String adminEmail = null;
        if (!tenant.isAtivo()) {
            adminEmail = userRepository.findFirstByTenantIdAndRole(tenantId, Role.ADMIN)
                    .map(User::getEmail)
                    .orElseThrow(DuplicateCnpjException::new);
        }
        entityManager.createNativeQuery("RESET app.bypass_tenant_check").executeUpdate();
        return new ExistingSignup(tenantId, tenant.isAtivo(), adminEmail);
    }

    @Transactional
    void createCompanyAndAdmin(UUID tenantId, SignupRequest request) {
        Company company = new Company();
        company.setTenantId(tenantId);
        company.setLegalName(request.legalName());
        company.setCnpj(request.cnpj());
        company.setTradeName(request.tradeName());
        company.setStateRegistration(request.stateRegistration());
        company.setMunicipalRegistration(request.municipalRegistration());
        company.setPhone(request.phone());
        company.setEmail(request.email());
        company.setWebsite(request.website());
        company.setZipCode(request.zipCode());
        company.setStreet(request.street());
        company.setNumber(request.number());
        company.setComplement(request.complement());
        company.setNeighborhood(request.neighborhood());
        company.setCity(request.city());
        company.setState(request.state());
        companyRepository.save(company);

        User user = new User();
        user.setTenantId(tenantId);
        user.setName(request.adminName());
        user.setEmail(request.adminEmail());
        user.setPasswordHash(passwordEncoder.encode(request.senha()));
        user.setRole(Role.ADMIN);
        for (Module module : ALL_MODULES) {
            for (Action action : ALL_ACTIONS) {
                if (module == Module.USER && action == Action.DELETE) {
                    continue;
                }
                user.getPermissions().add(new UserPermissionGrant(module, action));
            }
        }
        userRepository.save(user);
    }

    private void issueTokenAndSendEmail(UUID tenantId, String adminEmail) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(tenantId);
        token.setTokenHash(sha256(rawToken));
        token.setExpiraEm(Instant.now().plus(24, ChronoUnit.HOURS));
        tokenRepository.save(token);

        String confirmLink = "https://app.meshsuite.local/confirmar-cadastro?token=" + rawToken;
        mailService.sendSignupConfirmationEmail(adminEmail, confirmLink);
    }

    private String generateUniqueCodigo(String legalName, String tradeName) {
        String base = slugify(tradeName != null && !tradeName.isBlank() ? tradeName : legalName);
        String candidate = base;
        int suffix = 2;
        while (tenantRepository.existsByCodigo(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = "tenant";
        }
        // Leaves room for a "-NN" collision suffix under the 50-char column limit.
        return slug.length() > 45 ? slug.substring(0, 45) : slug;
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=TenantSignupServiceTest`
Expected: PASS, all 8 tests.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/service/TenantSignupService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/service/TenantSignupServiceTest.java
git commit -m "feat(auth): add TenantSignupService"
```

---

### Task 6: `AuthController` endpoints, `SecurityConfig`, integration test

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/controller/AuthController.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/config/SecurityConfig.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/controller/TenantSignupControllerTest.java`

**Interfaces:**
- Consumes: `TenantSignupService.signup/confirmSignup` (Task 5), `SignupRequest`/`ConfirmSignupRequest` (Task 4), `RateLimiter.isBlocked/recordFailure/recordSuccess` (existing, already a field on `AuthController`), `DuplicateCnpjException`/`RateLimitExceededException` (existing, already handled by `GlobalExceptionHandler` — no changes needed there).
- Produces: `POST /api/auth/signup` (202 on success, 409 via `DuplicateCnpjException`, 429 via `RateLimitExceededException`, 400 on validation failure); `POST /api/auth/confirm-signup` (200 on success, 401 via `AuthException`). Consumed by Task 7 (frontend `api/auth.ts`).

- [ ] **Step 1: Write the failing integration test**

```java
// mesh-suite-backend/src/test/java/com/meshsuite/auth/controller/TenantSignupControllerTest.java
package com.meshsuite.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.TenantSignupToken;
import com.meshsuite.auth.repository.TenantSignupTokenRepository;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TenantSignupControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired TenantSignupTokenRepository tokenRepository;
    @Autowired EntityManager entityManager;
    @MockBean com.meshsuite.mail.service.MailService mailService;

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void signupCreatesInactiveTenantAndSendsConfirmationEmail() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddr("10.0.0.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Confecção Aurora Ltda","cnpj":"11222333000144",
                                 "adminName":"Marina","adminEmail":"marina@aurora.com.br","senha":"senha1234"}"""))
                .andExpect(status().isAccepted());

        List<Company> companies = companyRepository.findAll();
        assertThat(companies).anyMatch(c -> c.getCnpj().equals("11222333000144"));
        Tenant tenant = tenantRepository.findById(
                companies.stream().filter(c -> c.getCnpj().equals("11222333000144")).findFirst().get().getTenantId())
                .orElseThrow();
        assertThat(tenant.isAtivo()).isFalse();

        verify(mailService).sendSignupConfirmationEmail(eq("marina@aurora.com.br"), any());
    }

    @Test
    void confirmSignupActivatesTheTenant() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora-confirm");
        tenant.setNome("Aurora Confirm");
        tenant.setAtivo(false);
        tenantRepository.saveAndFlush(tenant);

        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(tenant.getId());
        token.setTokenHash(sha256("raw-confirm-token"));
        token.setExpiraEm(Instant.now().plus(24, ChronoUnit.HOURS));
        tokenRepository.saveAndFlush(token);

        mockMvc.perform(post("/api/auth/confirm-signup")
                        .with(remoteAddr("10.0.0.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"raw-confirm-token"}"""))
                .andExpect(status().isOk());

        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void confirmSignupWithUnknownTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/confirm-signup")
                        .with(remoteAddr("10.0.0.3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"nao-existe"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupWithCnpjOfAlreadyConfirmedTenantReturns409() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora-confirmed");
        tenant.setNome("Aurora Confirmed");
        tenant.setAtivo(true);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Aurora Ltda");
        company.setCnpj("22333444000155");
        companyRepository.saveAndFlush(company);
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddr("10.0.0.4"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Outra Ltda","cnpj":"22333444000155",
                                 "adminName":"Carlos","adminEmail":"carlos@outra.com.br","senha":"senha1234"}"""))
                .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=TenantSignupControllerTest`
Expected: FAIL — 404 on both endpoints (routes don't exist yet).

- [ ] **Step 3: Add the endpoints to `AuthController`**

Modify `mesh-suite-backend/src/main/java/com/meshsuite/auth/controller/AuthController.java`:

Add two imports near the existing ones:
```java
import com.meshsuite.auth.dto.ConfirmSignupRequest;
import com.meshsuite.auth.dto.SignupRequest;
import com.meshsuite.auth.service.TenantSignupService;
```

Add a field and constructor parameter (extend the existing constructor, keep every current parameter):
```java
    private final PasswordResetService passwordResetService;
    private final TenantSignupService tenantSignupService;
    private final boolean cookieSecure;

    public AuthController(AuthService authService, JwtService jwtService, RateLimiter rateLimiter,
                           AuthContextService authContextService, PasswordResetService passwordResetService,
                           TenantSignupService tenantSignupService,
                           @Value("${app.cookie-secure}") boolean cookieSecure) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.authContextService = authContextService;
        this.passwordResetService = passwordResetService;
        this.tenantSignupService = tenantSignupService;
        this.cookieSecure = cookieSecure;
    }
```

Add two endpoint methods (anywhere among the other `@PostMapping` methods, e.g. after `me()`):
```java
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (rateLimiter.isBlocked(ip, request.adminEmail())) {
            throw new RateLimitExceededException();
        }
        try {
            tenantSignupService.signup(request);
            rateLimiter.recordSuccess(ip, request.adminEmail());
            return ResponseEntity.accepted().build();
        } catch (com.meshsuite.company.exception.DuplicateCnpjException e) {
            rateLimiter.recordFailure(ip, request.adminEmail());
            throw e;
        }
    }

    @PostMapping("/confirm-signup")
    public ResponseEntity<Void> confirmSignup(@Valid @RequestBody ConfirmSignupRequest request) {
        tenantSignupService.confirmSignup(request.token());
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 4: Add the new paths to `SecurityConfig`'s permit list**

Modify `mesh-suite-backend/src/main/java/com/meshsuite/config/SecurityConfig.java`:

```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/select-account", "/api/auth/forgot-password",
                                "/api/auth/reset-password", "/api/auth/signup", "/api/auth/confirm-signup",
                                "/actuator/health").permitAll()
                        .anyRequest().authenticated())
```

- [ ] **Step 5: Run the test to confirm it passes**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=TenantSignupControllerTest`
Expected: PASS, all 4 tests.

- [ ] **Step 6: Run the full auth package's tests to confirm nothing else broke**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest="com.meshsuite.auth.**"`
Expected: PASS (this re-adds a constructor parameter to `AuthController`, so `AuthControllerTest`/`AuthControllerNoAmbientTransactionTest`/`PasswordResetControllerTest` must still pass — they construct the controller through Spring's context, not `new AuthController(...)` directly, so the extra parameter is auto-wired and shouldn't need any test change).

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/controller/AuthController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/config/SecurityConfig.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/controller/TenantSignupControllerTest.java
git commit -m "feat(auth): add public signup and confirm-signup endpoints"
```

---

### Task 7: Frontend `api/auth.ts` additions

**Files:**
- Modify: `mesh-suite-frontend/src/api/auth.ts`

**Interfaces:**
- Produces: `SignupPayload` interface (fields mirror the backend's `SignupRequest`, camelCase, all optional company fields typed `string`); `signup(payload: SignupPayload): Promise<void>`; `confirmSignup(token: string): Promise<void>`. Consumed by Task 8 and Task 9.

No dedicated test file for this task — matches the existing convention (`api/companies.ts`, `api/partners.ts`, etc. have no direct unit tests; they're exercised through the component specs that call them, which is exactly what Task 8/9 do).

- [ ] **Step 1: Add the payload type and two functions**

Modify `mesh-suite-frontend/src/api/auth.ts` — append to the end of the file (keep every existing export unchanged):

```typescript
export interface SignupPayload {
  legalName: string
  cnpj: string
  tradeName: string
  stateRegistration: string
  municipalRegistration: string
  phone: string
  email: string
  website: string
  zipCode: string
  street: string
  number: string
  complement: string
  neighborhood: string
  city: string
  state: string
  adminName: string
  adminEmail: string
  senha: string
}

export async function signup(payload: SignupPayload): Promise<void> {
  await apiClient.post('/auth/signup', payload)
}

export async function confirmSignup(token: string): Promise<void> {
  await apiClient.post('/auth/confirm-signup', { token })
}
```

- [ ] **Step 2: Type-check**

Run: `cd mesh-suite-frontend && npx vue-tsc -b`
Expected: no new errors.

- [ ] **Step 3: Commit**

```bash
git add mesh-suite-frontend/src/api/auth.ts
git commit -m "feat(auth): add signup/confirmSignup API client functions"
```

---

### Task 8: `SignupView.vue` + route

**Files:**
- Create: `mesh-suite-frontend/src/views/SignupView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/SignupView.spec.ts`

**Interfaces:**
- Consumes: `signup` + `SignupPayload` from `@/api/auth` (Task 7); `TextField.vue`, `CollapsibleSection.vue`, `FormActions.vue` (existing, unchanged); `maskCnpj`/`maskTelefone`/`maskCep` from `@/utils/masks` (existing); `emailValido`/`cepValido` from `@/utils/validacao` (existing); `buscarEnderecoPorCep` from `@/api/cep` (existing).
- Produces: route `{ path: '/cadastro', name: 'signup', component: SignupView, meta: { public: true } }`. Consumed by Task 10 (the login page's new link points here).

- [ ] **Step 1: Write the failing component test**

```typescript
// mesh-suite-frontend/src/views/__tests__/SignupView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import SignupView from '@/views/SignupView.vue'
import * as authApi from '@/api/auth'
import * as cepApi from '@/api/cep'

vi.mock('@/api/auth', async (importOriginal) => {
  const original = await importOriginal<typeof authApi>()
  return { ...original, signup: vi.fn() }
})

vi.mock('@/api/cep', async (importOriginal) => {
  const original = await importOriginal<typeof cepApi>()
  return { ...original, buscarEnderecoPorCep: vi.fn() }
})

function mountWithRouter(path = '/cadastro') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/cadastro', name: 'signup', component: SignupView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(SignupView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

describe('SignupView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows required-field errors when legalName, cnpj, adminName, adminEmail and senha are blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(authApi.signup).not.toHaveBeenCalled()
  })

  it('shows an error when senha and confirmarSenha do not match', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('[data-test="admin-name"]').setValue('Marina')
    await wrapper.find('[data-test="admin-email"]').setValue('marina@aurora.com.br')
    await wrapper.find('[data-test="senha"]').setValue('senha1234')
    await wrapper.find('[data-test="confirmar-senha"]').setValue('outrasenha')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('As senhas não coincidem')
    expect(authApi.signup).not.toHaveBeenCalled()
  })

  it('submits the form and shows the confirmation message on success', async () => {
    vi.mocked(authApi.signup).mockResolvedValue(undefined)
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('[data-test="admin-name"]').setValue('Marina')
    await wrapper.find('[data-test="admin-email"]').setValue('marina@aurora.com.br')
    await wrapper.find('[data-test="senha"]').setValue('senha1234')
    await wrapper.find('[data-test="confirmar-senha"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(authApi.signup).toHaveBeenCalledWith(
      expect.objectContaining({
        legalName: 'Confecção Aurora Ltda',
        cnpj: '11222333000144',
        adminName: 'Marina',
        adminEmail: 'marina@aurora.com.br',
        senha: 'senha1234',
      }),
    )
    expect(wrapper.text()).toContain('Verifique seu e-mail')
  })

  it('shows a specific message on 409 (CNPJ already confirmed)', async () => {
    vi.mocked(authApi.signup).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('[data-test="admin-name"]').setValue('Marina')
    await wrapper.find('[data-test="admin-email"]').setValue('marina@aurora.com.br')
    await wrapper.find('[data-test="senha"]').setValue('senha1234')
    await wrapper.find('[data-test="confirmar-senha"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma conta confirmada com este CNPJ')
  })

  it('shows a rate-limit message on 429', async () => {
    vi.mocked(authApi.signup).mockRejectedValue({ response: { status: 429 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('[data-test="admin-name"]').setValue('Marina')
    await wrapper.find('[data-test="admin-email"]').setValue('marina@aurora.com.br')
    await wrapper.find('[data-test="senha"]').setValue('senha1234')
    await wrapper.find('[data-test="confirmar-senha"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Muitas tentativas')
  })
})
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/SignupView.spec.ts`
Expected: FAIL — `SignupView.vue` doesn't exist.

- [ ] **Step 3: Write `SignupView.vue`**

```vue
<!-- mesh-suite-frontend/src/views/SignupView.vue -->
<template>
  <div class="signup-page">
    <div class="signup-card" v-if="!submitted">
      <h1>Criar conta</h1>
      <p class="subtitle">Cadastre sua empresa e comece a usar o Mesh Suite</p>

      <form class="form" @submit.prevent="save">
        <section class="card">
          <h2>Identificação</h2>
          <div class="grid grid-2">
            <TextField
              v-model="form.legalName"
              label="Razão Social"
              required
              placeholder="Ex: Mercado Silva Ltda"
              :error="errors.legalName"
              test-id="legal-name"
              @blur="validateLegalName"
            />
            <TextField v-model="form.tradeName" label="Nome Fantasia" placeholder="Ex: Mercado Silva" test-id="trade-name" />
          </div>
          <div class="grid grid-3-even">
            <TextField
              v-model="form.cnpj"
              label="CNPJ"
              required
              :mask="maskCnpj"
              :maxlength="18"
              placeholder="00.000.000/0000-00"
              :error="errors.cnpj"
              test-id="cnpj"
              @blur="validateCnpj"
            />
            <TextField v-model="form.stateRegistration" label="Inscrição Estadual" placeholder="000.000.000.000" />
            <TextField v-model="form.municipalRegistration" label="Inscrição Municipal" placeholder="000000" />
          </div>
        </section>

        <CollapsibleSection title="Contato">
          <div class="grid grid-3-even">
            <TextField v-model="form.phone" label="Telefone" placeholder="(11) 3000-0000" :mask="maskTelefone" :maxlength="15" />
            <TextField
              v-model="form.email"
              label="E-mail Comercial"
              placeholder="contato@empresa.com.br"
              :error="errors.email"
              test-id="company-email"
              @blur="validateCompanyEmail"
            />
            <TextField v-model="form.website" label="Site" placeholder="www.empresa.com.br" />
          </div>
        </CollapsibleSection>

        <CollapsibleSection title="Endereço">
          <div class="grid grid-cep">
            <div>
              <label class="field-label">CEP</label>
              <div class="input-action">
                <TextField
                  v-model="form.zipCode"
                  :mask="maskCep"
                  :maxlength="9"
                  :error="errors.zipCode"
                  test-id="zip-code"
                  @blur="validateZipCode"
                />
                <button type="button" data-test="search-cep" @click="searchCep">Buscar dados</button>
              </div>
              <p v-if="cepError" class="field-error">{{ cepError }}</p>
            </div>
            <TextField v-model="form.street" label="Logradouro" placeholder="Rua, Av., Alameda..." test-id="street" />
            <TextField v-model="form.number" label="Número" placeholder="123" />
          </div>
          <div class="grid grid-4">
            <TextField v-model="form.neighborhood" label="Bairro" placeholder="Ex: Centro" />
            <TextField v-model="form.city" label="Cidade" placeholder="Ex: São Paulo" test-id="city" />
            <div>
              <label class="field-label">UF</label>
              <select v-model="form.state" data-test="state">
                <option value="">UF</option>
                <option v-for="uf in UFS" :key="uf" :value="uf">{{ uf }}</option>
              </select>
            </div>
            <TextField v-model="form.complement" label="Complemento" placeholder="Sala, Andar, Bloco..." />
          </div>
        </CollapsibleSection>

        <section class="card">
          <h2>Acesso</h2>
          <div class="grid grid-2">
            <TextField
              v-model="form.adminName"
              label="Seu nome"
              required
              :error="errors.adminName"
              test-id="admin-name"
              @blur="validateAdminName"
            />
            <TextField
              v-model="form.adminEmail"
              label="Seu e-mail"
              required
              :error="errors.adminEmail"
              test-id="admin-email"
              @blur="validateAdminEmail"
            />
          </div>
          <div class="grid grid-2">
            <div>
              <label class="field-label" for="senha">Senha</label>
              <input id="senha" data-test="senha" type="password" v-model="form.senha" required minlength="8" />
            </div>
            <div>
              <label class="field-label" for="confirmar-senha">Confirmar senha</label>
              <input
                id="confirmar-senha"
                data-test="confirmar-senha"
                type="password"
                v-model="confirmarSenha"
                required
                minlength="8"
              />
            </div>
          </div>
        </section>

        <p v-if="generalError" class="error-general">{{ generalError }}</p>

        <FormActions :saving="saving" save-label="Criar conta" @cancel="cancel" />
      </form>
    </div>

    <div class="signup-card" v-else>
      <h1>Verifique seu e-mail</h1>
      <p class="subtitle">
        Enviamos um link de confirmação para {{ form.adminEmail }}. Clique nele para ativar sua conta.
      </p>
      <RouterLink to="/login" class="link">Voltar para o login</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import TextField from '@/components/TextField.vue'
import CollapsibleSection from '@/components/CollapsibleSection.vue'
import FormActions from '@/components/FormActions.vue'
import { signup, type SignupPayload } from '@/api/auth'
import { buscarEnderecoPorCep } from '@/api/cep'
import { maskCnpj, maskTelefone, maskCep } from '@/utils/masks'
import { emailValido, cepValido } from '@/utils/validacao'

const UFS = ['AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI',
  'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO']

const router = useRouter()

function emptyForm(): SignupPayload {
  return {
    legalName: '', cnpj: '', tradeName: '', stateRegistration: '', municipalRegistration: '',
    phone: '', email: '', website: '', zipCode: '', street: '', number: '', complement: '',
    neighborhood: '', city: '', state: '', adminName: '', adminEmail: '', senha: '',
  }
}

const form = reactive<SignupPayload>(emptyForm())
const confirmarSenha = ref('')
const errors = reactive<{
  legalName?: string; cnpj?: string; email?: string; zipCode?: string
  adminName?: string; adminEmail?: string
}>({})
const cepError = ref('')
const generalError = ref('')
const saving = ref(false)
const submitted = ref(false)

async function searchCep() {
  cepError.value = ''
  const address = await buscarEnderecoPorCep(form.zipCode ?? '')
  if (!address) {
    cepError.value = 'CEP não encontrado — preencha o endereço manualmente'
    return
  }
  form.street = address.logradouro
  form.neighborhood = address.bairro
  form.city = address.localidade
  form.state = address.uf
}

function validateLegalName() {
  errors.legalName = form.legalName.trim() ? undefined : 'Campo obrigatório'
}

function validateCnpj() {
  const digits = form.cnpj.replace(/\D/g, '')
  if (!digits) {
    errors.cnpj = 'Campo obrigatório'
  } else if (digits.length !== 14) {
    errors.cnpj = 'Informe um CNPJ válido'
  } else {
    errors.cnpj = undefined
  }
}

function validateCompanyEmail() {
  errors.email = !form.email || emailValido(form.email) ? undefined : 'E-mail inválido'
}

function validateZipCode() {
  errors.zipCode = !form.zipCode || cepValido(form.zipCode) ? undefined : 'CEP inválido'
}

function validateAdminName() {
  errors.adminName = form.adminName.trim() ? undefined : 'Campo obrigatório'
}

function validateAdminEmail() {
  if (!form.adminEmail.trim()) {
    errors.adminEmail = 'Campo obrigatório'
  } else if (!emailValido(form.adminEmail)) {
    errors.adminEmail = 'E-mail inválido'
  } else {
    errors.adminEmail = undefined
  }
}

function validate(): boolean {
  validateLegalName()
  validateCnpj()
  validateCompanyEmail()
  validateZipCode()
  validateAdminName()
  validateAdminEmail()
  return !errors.legalName && !errors.cnpj && !errors.email && !errors.zipCode
    && !errors.adminName && !errors.adminEmail
}

async function save() {
  generalError.value = ''
  if (!validate()) {
    return
  }
  if (!form.senha || form.senha.length < 8) {
    generalError.value = 'A senha precisa ter no mínimo 8 caracteres'
    return
  }
  if (form.senha !== confirmarSenha.value) {
    generalError.value = 'As senhas não coincidem'
    return
  }

  saving.value = true
  try {
    await signup({ ...form, cnpj: form.cnpj.replace(/\D/g, '') })
    submitted.value = true
  } catch (err: any) {
    if (err?.response?.status === 409) {
      generalError.value = 'Já existe uma conta confirmada com este CNPJ. Faça login.'
    } else if (err?.response?.status === 429) {
      generalError.value = 'Muitas tentativas, tente novamente em instantes'
    } else if (err?.response?.status === 400) {
      generalError.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      generalError.value = 'Não foi possível concluir o cadastro. Tente novamente em instantes.'
    }
  } finally {
    saving.value = false
  }
}

function cancel() {
  router.push({ name: 'login' })
}
</script>

<style scoped>
.signup-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40px 24px;
  box-sizing: border-box;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.signup-card {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 12px;
  padding: 40px;
  width: 100%;
  max-width: 720px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
}

.signup-card h1 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
}

.subtitle {
  color: var(--pm-text-mid);
  font-size: 14px;
  margin: 0 0 24px;
}

.link {
  color: var(--pm-accent);
  text-decoration: none;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px;
}

.card h2 {
  font-size: 13px;
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

.grid-3-even {
  grid-template-columns: 1fr 1fr 1fr;
}

.grid-4 {
  grid-template-columns: repeat(4, 1fr);
}

.grid-cep {
  grid-template-columns: 160px 1fr 100px;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

select,
input[type='password'] {
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

.input-action {
  display: flex;
  gap: 6px;
  align-items: flex-start;
}

.input-action :deep(.text-field) {
  flex: 1;
}

.input-action button {
  height: 36px;
  flex-shrink: 0;
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

.error-general {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0;
}
</style>
```

- [ ] **Step 4: Add the route**

Modify `mesh-suite-frontend/src/router/index.ts` — add the import near the other view imports:

```typescript
import SignupView from '@/views/SignupView.vue'
```

Add the route entry next to `/login` (inside the `routes` array):

```typescript
    { path: '/cadastro', name: 'signup', component: SignupView, meta: { public: true } },
```

- [ ] **Step 5: Run the test to confirm it passes**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/SignupView.spec.ts`
Expected: PASS, all 5 tests.

- [ ] **Step 6: Type-check**

Run: `cd mesh-suite-frontend && npx vue-tsc -b`
Expected: no new errors.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/views/SignupView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/__tests__/SignupView.spec.ts
git commit -m "feat(auth): add public signup form"
```

---

### Task 9: `ConfirmSignupView.vue` + route

**Files:**
- Create: `mesh-suite-frontend/src/views/ConfirmSignupView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/ConfirmSignupView.spec.ts`

**Interfaces:**
- Consumes: `confirmSignup` from `@/api/auth` (Task 7).
- Produces: route `{ path: '/confirmar-cadastro', name: 'confirm-signup', component: ConfirmSignupView, meta: { public: true } }`. Nothing else depends on this component.

- [ ] **Step 1: Write the failing component test**

```typescript
// mesh-suite-frontend/src/views/__tests__/ConfirmSignupView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import ConfirmSignupView from '@/views/ConfirmSignupView.vue'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', async (importOriginal) => {
  const original = await importOriginal<typeof authApi>()
  return { ...original, confirmSignup: vi.fn() }
})

function mountWithRouter(path: string) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/cadastro', name: 'signup', component: { template: '<div />' } },
      { path: '/confirmar-cadastro', name: 'confirm-signup', component: ConfirmSignupView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ConfirmSignupView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

describe('ConfirmSignupView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('confirms the token from the query string on mount and shows success', async () => {
    vi.mocked(authApi.confirmSignup).mockResolvedValue(undefined)
    const { wrapper } = await mountWithRouter('/confirmar-cadastro?token=abc123')
    await flushPromises()

    expect(authApi.confirmSignup).toHaveBeenCalledWith('abc123')
    expect(wrapper.text()).toContain('confirmada com sucesso')
  })

  it('shows an invalid/expired message on 401', async () => {
    vi.mocked(authApi.confirmSignup).mockRejectedValue({ response: { status: 401 } })
    const { wrapper } = await mountWithRouter('/confirmar-cadastro?token=expirado')
    await flushPromises()

    expect(wrapper.text()).toContain('Link inválido ou expirado')
  })

  it('shows a generic connection error on network failure', async () => {
    vi.mocked(authApi.confirmSignup).mockRejectedValue(new Error('network'))
    const { wrapper } = await mountWithRouter('/confirmar-cadastro?token=abc123')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível conectar')
  })
})
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/ConfirmSignupView.spec.ts`
Expected: FAIL — `ConfirmSignupView.vue` doesn't exist.

- [ ] **Step 3: Write `ConfirmSignupView.vue`**

```vue
<!-- mesh-suite-frontend/src/views/ConfirmSignupView.vue -->
<template>
  <div class="confirm-page">
    <div class="confirm-card">
      <h1>Confirmação de cadastro</h1>
      <p v-if="loading" class="subtitle">Confirmando...</p>
      <template v-else>
        <p v-if="successMessage" class="success">{{ successMessage }}</p>
        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        <RouterLink v-if="successMessage" to="/login" class="link">Ir para o login</RouterLink>
        <RouterLink v-else to="/cadastro" class="link">Cadastrar novamente</RouterLink>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { confirmSignup } from '@/api/auth'

const route = useRoute()
const loading = ref(true)
const successMessage = ref('')
const errorMessage = ref('')

onMounted(async () => {
  const token = String(route.query.token ?? '')
  try {
    await confirmSignup(token)
    successMessage.value = 'Sua conta foi confirmada com sucesso. Já pode fazer login.'
  } catch (err: any) {
    if (err?.response?.status === 401) {
      // The backend maps an unknown, already-used, or expired token to 401
      // (see TenantSignupService.confirmSignup) -- that's the only case where
      // "invalid or expired link" is an accurate message.
      errorMessage.value = 'Link inválido ou expirado. Preencha o cadastro novamente com o mesmo CNPJ para receber um novo link.'
    } else {
      errorMessage.value = 'Não foi possível conectar. Tente novamente em instantes.'
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.confirm-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.confirm-card {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 12px;
  padding: 40px;
  width: 380px;
  text-align: center;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
}

.subtitle {
  color: var(--pm-text-mid);
  font-size: 14px;
}

.success {
  color: var(--pm-success);
  margin-bottom: 16px;
}

.error {
  color: var(--pm-error);
  margin-bottom: 16px;
}

.link {
  color: var(--pm-accent);
  text-decoration: none;
}
</style>
```

- [ ] **Step 4: Add the route**

Modify `mesh-suite-frontend/src/router/index.ts` — add the import near `SignupView`:

```typescript
import ConfirmSignupView from '@/views/ConfirmSignupView.vue'
```

Add the route entry next to `/cadastro`:

```typescript
    { path: '/confirmar-cadastro', name: 'confirm-signup', component: ConfirmSignupView, meta: { public: true } },
```

- [ ] **Step 5: Run the test to confirm it passes**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/ConfirmSignupView.spec.ts`
Expected: PASS, all 3 tests.

- [ ] **Step 6: Type-check**

Run: `cd mesh-suite-frontend && npx vue-tsc -b`
Expected: no new errors.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/views/ConfirmSignupView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/__tests__/ConfirmSignupView.spec.ts
git commit -m "feat(auth): add signup confirmation page"
```

---

### Task 10: "Criar conta" link on `LoginView.vue`

**Files:**
- Modify: `mesh-suite-frontend/src/views/LoginView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/LoginView.spec.ts`

**Interfaces:**
- Consumes: route `signup` (Task 8, already registered in the router by the time this runs).
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Add the failing test**

Check the current `mesh-suite-frontend/src/views/__tests__/LoginView.spec.ts` for its existing `mountWithRouter` helper and route list first — it needs a `/cadastro` route added to its router instance for `RouterLink` to resolve. Add this test inside the existing `describe('LoginView', ...)` block:

```typescript
  it('links to the public signup page', async () => {
    const { wrapper } = await mountWithRouter()

    const link = wrapper.find('a[href="/cadastro"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('Criar conta')
  })
```

If the file's `mountWithRouter` router instance doesn't already include a `/cadastro` route, add one (a stub component is fine, matching how other unrelated destinations are stubbed in that same router list):

```typescript
      { path: '/cadastro', name: 'signup', component: { template: '<div />' } },
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/LoginView.spec.ts`
Expected: FAIL — no `a[href="/cadastro"]` found (the current markup is an inert `<span>`).

- [ ] **Step 3: Replace the inert link in `LoginView.vue`**

Modify `mesh-suite-frontend/src/views/LoginView.vue` — replace:

```html
          <p class="footer-text">
            Não tem conta?
            <span class="link-inert" title="Provisionamento de tenant fora de escopo desta fatia">
              Fale com o time comercial
            </span>
          </p>
```

with:

```html
          <p class="footer-text">
            Não tem conta?
            <RouterLink to="/cadastro" class="link">Criar conta</RouterLink>
          </p>
```

The `.link-inert` CSS class becomes unused after this change — leave it in `<style scoped>` only if anything else in the file still references it; it doesn't (grep the file to confirm), so also delete the `.link-inert` rule from the `<style>` block:

```css
.link-inert {
  color: var(--pm-accent);
  cursor: not-allowed;
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/LoginView.spec.ts`
Expected: PASS, including every pre-existing test in the file (no other test in it referenced the old inert span, per the earlier grep during design — confirm this is still true by reading the full diff of test output).

- [ ] **Step 5: Type-check**

Run: `cd mesh-suite-frontend && npx vue-tsc -b`
Expected: no new errors.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/views/LoginView.vue mesh-suite-frontend/src/views/__tests__/LoginView.spec.ts
git commit -m "feat(auth): link login page to public signup"
```

---

### Task 11: PRD-14 update

**Files:**
- Modify: `prd/PRD-14-login-multitenant.md`

**Interfaces:** none (documentation only).

- [ ] **Step 1: Remove the out-of-scope line**

In the "Fora de escopo (explicitamente)" section, delete the line:

```
- Autoatendimento/signup público de novo tenant.
```

- [ ] **Step 2: Add a new flow section**

Immediately after the existing "Fluxo — Provisionar novo Tenant (operação interna)" section, add:

```markdown
### Fluxo — Cadastro público de Tenant (autoatendimento)

1. Visitante preenche um formulário público com os dados da `Empresa` (mesmos campos do cadastro interno: razão social, CNPJ, dados fiscais básicos, contato, endereço) e os dados do primeiro `Usuario` administrador (nome, e-mail, senha).
2. Sistema cria o `Tenant` (código gerado automaticamente a partir do nome da empresa), a `Empresa` e o `Usuario` administrador — mas o `Tenant` nasce **inativo**.
3. Sistema envia um e-mail de confirmação com link de validade de 24h.
4. Enquanto o `Tenant` estiver inativo, nenhum dos seus usuários consegue logar (regra 8, já existente).
5. Ao confirmar o e-mail, o `Tenant` é ativado e o usuário pode fazer login normalmente.
6. Se o CNPJ informado já pertence a um `Tenant` ainda não confirmado (cadastro anterior abandonado), o sistema reenvia a confirmação para esse cadastro em vez de criar um novo — evita tanto duplicidade quanto um usuário ficar permanentemente bloqueado por ter perdido o e-mail original.
7. Rate limiting por IP e por e-mail do administrador, reaproveitando o mesmo mecanismo do login.
```

- [ ] **Step 3: Add the new business rule**

In "5. Regras de negócio", add a new numbered rule after the existing rule 8:

```markdown
9. **Cadastro público de tenant exige confirmação de e-mail antes de qualquer login ser permitido.** O tenant nasce inativo e só é ativado pela confirmação — não existe outro mecanismo de ativação.
```

- [ ] **Step 4: Verify the edits**

Run: `grep -n "Autoatendimento/signup\|Cadastro público de Tenant\|Cadastro público de tenant exige" prd/PRD-14-login-multitenant.md`
Expected: the "Fora de escopo" line is gone; the new flow section header and the new rule both appear.

- [ ] **Step 5: Commit**

```bash
git add prd/PRD-14-login-multitenant.md
git commit -m "docs(prd): document public tenant signup, no longer out of scope"
```

---

### Task 12: Full verification

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Run the full backend test suite**

Run: `cd mesh-suite-backend && ./mvnw clean test`
Expected: BUILD SUCCESS. The pre-existing `payable`/`CompanyRepositoryTest` dev-seed test-isolation flake (documented in project memory, unrelated to this feature) may still show a handful of failures on `main` — confirm any failure you see is one of those already-known ones (check by running the same command on a clean `main` checkout if in doubt), not something this feature introduced.

- [ ] **Step 2: Run the frontend type-check and full test suite**

Run: `cd mesh-suite-frontend && npx vue-tsc -b && npx vitest run`
Expected: `vue-tsc -b` reports no errors; all Vitest test files pass, including the 3 new/modified ones from Tasks 8–10.

- [ ] **Step 3: Run the frontend production build**

Run: `cd mesh-suite-frontend && npm run build`
Expected: BUILD SUCCESS — this is the same command that caught the pre-existing bugs during the DigitalOcean deploy earlier; running it here catches anything analogous introduced by this feature before it ever reaches a deploy.

- [ ] **Step 4: Manually confirm the end-to-end flow against a local backend, if one is running**

If a local `mesh-suite-backend`/`mesh-suite-frontend` dev stack is already up (do not start one yourself — see project convention: don't auto-start dev servers), exercise the flow once:
1. Open `/cadastro`, fill the form with a fresh CNPJ, submit.
2. Confirm the "verifique seu e-mail" message appears.
3. Check the backend log (or a local mail catcher, if configured) for the confirmation link, or query `tenant_signup_token` directly for the token's raw value is not recoverable (it's hashed) — instead confirm via `SELECT ativo FROM tenant WHERE codigo = '<generated-slug>'` that it's `false`.
4. Hit `/api/auth/confirm-signup` with the token from the email/log, or navigate to `/confirmar-cadastro?token=...`.
5. Confirm `tenant.ativo` flips to `true` and login with the new admin's credentials succeeds.

If no local stack is running, skip this step and say so explicitly rather than claiming it was verified.

- [ ] **Step 5: Final commit (only if Step 4 required any fix)**

If manual verification in Step 4 surfaced a bug, fix it, re-run the relevant automated test from whichever task owns that file, and commit the fix with a message describing what was wrong — do not silently fold a fix into an earlier task's commit.
