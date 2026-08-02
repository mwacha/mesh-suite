# Cadastro de Usuário + Permissões Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the User (Usuário) domain — full CRUD with a module/action permission model, defaulted from a Profile and editable per user — and retrofit real permission enforcement onto the Customer (Parceiro), Product (Produto), Order (Pedido) and User services. Along the way, rename the existing `Usuario`/`Papel` entity/enum (and everything that references them by name) to `User`/`Role`, the first piece of code in this codebase written in English.

**Architecture:** Same layered pattern as every prior domain (entity + repository + service + controller + DTO, RLS via `tenant_id`), plus two new cross-cutting pieces: a `user_permission` child table holding per-user `(module, action)` grants, and a `@RequiresPermission` annotation + AOP aspect (mirroring the existing `TenantContextAspect`) that checks those grants before a service method runs.

**Tech Stack:** Spring Boot 3.4.5 / Java 21, Postgres 16 (Row-Level Security, Flyway), Vue 3 + TypeScript + Vite, Vitest + Vue Test Utils, JUnit 5 + Testcontainers + AssertJ.

## Global Constraints

Carried over from every prior plan in this codebase — still binding on every task below:

- **RLS on every new/touched table**: `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + a policy using `tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid`. The child table `user_permission` (no own `tenant_id`) uses an `EXISTS` subquery against `app_user`'s own tenant-scoped policy — same pattern as `parceiro_contato`/`item_pedido`.
- **Tenant scoping is never explicit in application queries** — enforced entirely via `TenantContext`/`TenantContextAspect`/`JwtAuthenticationFilter`. `tenantId` is only set explicitly when constructing a brand-new top-level entity.
- **Service test setup must call both** the raw `SET LOCAL app.tenant_id` native query **and** `TenantContext.set(tenantId)` — omitting `TenantContext.set(...)` silently breaks RLS-scoped service calls (a real bug from an earlier slice).
- **Cross-tenant MockMvc tests must call `entityManager.clear()`** at the tenant-switch point, or Hibernate's first-level cache masks RLS behind a false 200 instead of 404.
- **Every domain gets its own scoped `@RestControllerAdvice(assignableTypes = XController.class)`** for `DataIntegrityViolationException` — never add domain-worded exception handling to the shared `GlobalExceptionHandler`. Domain-unique exception types are safe to register directly in the shared handler.
- **Every list view's Ações dropdown uses `<Teleport to="body">` with `position: fixed`**, computed from the trigger button's `getBoundingClientRect()`, from the start.
- **Mounting a Teleport-using view in tests** requires `global: { stubs: { teleport: true } } }`.
- **Numeric optional fields bound with `v-model.number` produce `''` (not `null`) when cleared** — payload-building functions must normalize this right before the API call, and any regression test for it must genuinely drive the input through the empty-string state.
- **`vi.clearAllMocks()` in every view spec file's `beforeEach`.**
- **Every data-loading/mutating function in a view wraps its work in try/catch** and sets a user-facing `erro`/`erroGeral` ref.
- **Design tokens only** in `.vue` files (`var(--pm-*)`), no hardcoded hex.

**New constraints specific to this plan:**

- **Rename scope boundary** (do not extend beyond this without asking): the `Usuario` entity class, `Papel` enum, and `UsuarioRepository` interface — including **all of `Usuario`'s own fields** (`nome`→`name`, `senhaHash`→`passwordHash`, `papel`→`role`, `ativo`→`active`, `criadoEm`→`createdAt`, `ultimoAcesso`→`lastAccessAt`) and the DB table (`usuario`→`app_user`, columns renamed to match) — are renamed to English. `PasswordResetToken.usuarioId`/column `usuario_id` are renamed to `userId`/`user_id` too, since that field is set directly from a `User`'s id in code this plan already touches. **Nothing else is renamed**: `MeResponse`'s `nome`/`papel` fields, `LoginRequest`'s `senha` field, the `"papel"` JWT claim key, and every unrelated Portuguese identifier in `AuthContextService.Context`, `JwtService`, `JwtAuthenticationFilter`, `LoginRequest`/`ForgotPasswordRequest`/`ResetPasswordRequest` stay exactly as they are — renaming them isn't required to keep the codebase compiling after the `Usuario`→`User` rename, and is out of scope. `Parceiro`/`Produto`/`Pedido` and everything in them (fields, tables, API routes, frontend) are **not** touched by this plan at all — that is a separate future project.
- **New code in this plan is written in English** (classes, methods, variables, DB tables/columns). User-facing text (form labels, error messages) and frontend route paths/names stay in Portuguese, matching the rest of the app.
- **`user` is a reserved word in Postgres/SQL** — the renamed table is `app_user`, never `user`.
- **`@RequiresPermission` goes on service methods, never controller methods.** `TenantContextAspect` only issues `SET LOCAL app.tenant_id` inside `@Transactional` methods (the service layer). The new `PermissionAspect` must run at `@Order(2)`, strictly after `TenantContextAspect`'s `@Order(1)`, because the permission check queries `user_permission`, which has RLS via `EXISTS` against `app_user.tenant_id` — if the check ran before tenant context was set, it would find zero rows and deny everything unconditionally, regardless of what's actually granted.
- **`GET /api/users/sales-reps` (renamed from `/representantes`) is never gated by a permission check** — it's a support lookup for the Pedido form's vendor picker, not "viewing the Users module," and a Vendedor's default permission profile has no `USER` grants at all (see the matrix in Task 6).
- **The default permission matrix (Task 6) is computed only in the frontend**, purely to pre-check checkboxes for UX. The backend never recomputes it — it persists exactly whatever `(module, action)` list arrives in the request body, on both create and update.
- **No hard delete for `User`** — only activate/deactivate. `Pedido.vendedor_id REFERENCES app_user(id)` has no cascade; deleting a user who was ever a vendedor on a pedido would violate referential integrity.

---

### Task 1: Rename core entity — `Usuario`→`User`, `Papel`→`Role` (new `com.meshsuite.user` package)

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/User.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/Role.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserRepository.java`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V8__rename_usuario_to_user.sql`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/Usuario.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/Papel.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/UsuarioRepository.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/UsuarioController.java` (fully superseded by the new `UserController` built in Task 8 — no intermediate rename, it's rebuilt from scratch there)
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/dto/UsuarioSummaryResponse.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/usuario/UsuarioControllerTest.java` (rebuilt as part of Task 8's `UserControllerTest`)
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/user/UserRepositoryTest.java` (replaces `mesh-suite-backend/src/test/java/com/meshsuite/usuario/UsuarioRepositoryTest.java`, which this task deletes)
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/usuario/UsuarioRepositoryTest.java`

**Interfaces:**
- Produces: `User` entity (`id`, `tenantId`, `name`, `email`, `passwordHash`, `role: Role`, `active`, `createdAt`, `lastAccessAt`) — Tasks 6-7 add `phone`/`profile` to this same file. `Role` enum: `ADMINISTRATIVE, SALES_REP, PRODUCTION, OUTSOURCED, ADMIN`. `UserRepository extends JpaRepository<User, UUID>` with `Optional<User> findByEmail(String email)` and `List<User> findByRoleOrderByName(Role role)`.
- Consumes: nothing new — this is the foundational rename every later task builds on.

This task leaves the codebase **not compiling** until Tasks 2-3 finish the rename in the files that import `Usuario`/`Papel`/`UsuarioRepository` — that's expected for a rename of this size; the three rename tasks are meant to land as one uninterrupted sequence before any other task starts. Do not run the full test suite until Task 3 is done.

- [ ] **Step 1: Write the migration**

The existing table's `CHECK` constraint has no explicit name in `V3__create_usuario.sql`, so Postgres auto-named it `usuario_papel_check` (the default `<table>_<column>_check` pattern). Confirm this name first:

Run: `psql "$DB_URL" -c "\d usuario"` (or open the dev DB in any client) and confirm the constraint is named `usuario_papel_check` before running the migration in a real environment — if Flyway hasn't applied V1-V7 to a fresh DB yet, this step is moot and the name above is guaranteed correct since it's the only migration that ever created that constraint.

```sql
ALTER TABLE usuario RENAME TO app_user;
ALTER TABLE app_user RENAME COLUMN nome TO name;
ALTER TABLE app_user RENAME COLUMN senha_hash TO password_hash;
ALTER TABLE app_user RENAME COLUMN papel TO role;
ALTER TABLE app_user RENAME COLUMN ativo TO active;
ALTER TABLE app_user RENAME COLUMN criado_em TO created_at;
ALTER TABLE app_user RENAME COLUMN ultimo_acesso TO last_access_at;

ALTER TABLE app_user DROP CONSTRAINT usuario_papel_check;

UPDATE app_user SET role = CASE role
    WHEN 'ADMINISTRATIVO' THEN 'ADMINISTRATIVE'
    WHEN 'REPRESENTANTE' THEN 'SALES_REP'
    WHEN 'PRODUCAO' THEN 'PRODUCTION'
    WHEN 'TERCEIRIZADO' THEN 'OUTSOURCED'
    WHEN 'ADMINISTRADOR' THEN 'ADMIN'
END;

ALTER TABLE app_user ADD CONSTRAINT app_user_role_check
    CHECK (role IN ('ADMINISTRATIVE','SALES_REP','PRODUCTION','OUTSOURCED','ADMIN'));

ALTER INDEX idx_usuario_tenant_id RENAME TO idx_app_user_tenant_id;
ALTER INDEX idx_usuario_email RENAME TO idx_app_user_email;

ALTER POLICY usuario_tenant_isolation ON app_user RENAME TO app_user_tenant_isolation;
ALTER POLICY usuario_login_lookup ON app_user RENAME TO app_user_login_lookup;

-- password_reset_token.usuario_id -> user_id: this column's own FK target table
-- was renamed above, and PasswordResetService (Task 2) is being edited in this
-- same rename pass to call token.setUserId(...)/getUserId() instead of
-- setUsuarioId(...)/getUsuarioId() -- renaming the column now keeps the DB and
-- the Java field name in sync from the same commit.
ALTER TABLE password_reset_token RENAME COLUMN usuario_id TO user_id;
ALTER INDEX idx_password_reset_token_usuario_id RENAME TO idx_password_reset_token_user_id;
```

- [ ] **Step 2: Write `User.java`**

```java
package com.meshsuite.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_access_at")
    private Instant lastAccessAt;
}
```

- [ ] **Step 3: Write `Role.java`**

```java
package com.meshsuite.user;

public enum Role {
    ADMINISTRATIVE,
    SALES_REP,
    PRODUCTION,
    OUTSOURCED,
    ADMIN
}
```

- [ ] **Step 4: Write `UserRepository.java`**

```java
package com.meshsuite.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    List<User> findByRoleOrderByName(Role role);
}
```

- [ ] **Step 5: Delete the old `com.meshsuite.usuario` files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/usuario/Usuario.java \
       mesh-suite-backend/src/main/java/com/meshsuite/usuario/Papel.java \
       mesh-suite-backend/src/main/java/com/meshsuite/usuario/UsuarioRepository.java \
       mesh-suite-backend/src/main/java/com/meshsuite/usuario/UsuarioController.java \
       mesh-suite-backend/src/main/java/com/meshsuite/usuario/dto/UsuarioSummaryResponse.java \
       mesh-suite-backend/src/test/java/com/meshsuite/usuario/UsuarioControllerTest.java \
       mesh-suite-backend/src/test/java/com/meshsuite/usuario/UsuarioRepositoryTest.java
```

- [ ] **Step 6: Write `UserRepositoryTest.java`**

```java
package com.meshsuite.user;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    // app_user_tenant_isolation has no explicit WITH CHECK, so its USING expression
    // also gates INSERT: writing a row requires app.tenant_id to already equal that
    // row's tenant_id. app_user_login_lookup (bypass flag) is SELECT-only and
    // doesn't help here.
    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    @Test
    @Transactional
    void savesUserWithRole() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@confeccaoaurora.com.br");
        user.setPasswordHash("bcrypt-hash");
        user.setRole(Role.ADMIN);

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @Transactional
    void rejectsDuplicateEmailAcrossTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        User a = new User();
        a.setTenantId(tenantA.getId());
        a.setName("Marina");
        a.setEmail("marina@confeccaoaurora.com.br");
        a.setPasswordHash("hash");
        a.setRole(Role.ADMIN);
        userRepository.saveAndFlush(a);

        setTenantContext(tenantB.getId());
        User b = new User();
        b.setTenantId(tenantB.getId());
        b.setName("Marina Outra");
        b.setEmail("marina@confeccaoaurora.com.br");
        b.setPasswordHash("hash");
        b.setRole(Role.ADMIN);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(b));
    }

    @Test
    @Transactional
    void loginBypassPolicyAllowsEmailLookupWithoutTenantContext() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@confeccaoaurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);
        entityManager.clear();

        // RESET simulates no tenant context: without the bypass flag, RLS hides the row.
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
        Long withoutBypass = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM app_user WHERE email = 'marina@confeccaoaurora.com.br'")
                .getSingleResult()).longValue();
        assertThat(withoutBypass).isZero();

        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        Long withBypass = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM app_user WHERE email = 'marina@confeccaoaurora.com.br'")
                .getSingleResult()).longValue();
        assertThat(withBypass).isEqualTo(1L);
    }

    @Test
    @Transactional
    void findsByRoleOrderedByName() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User b = new User();
        b.setTenantId(tenant.getId());
        b.setName("Bruno");
        b.setEmail("bruno@aurora.com.br");
        b.setPasswordHash("hash");
        b.setRole(Role.SALES_REP);
        userRepository.saveAndFlush(b);

        User a = new User();
        a.setTenantId(tenant.getId());
        a.setName("Ana");
        a.setEmail("ana@aurora.com.br");
        a.setPasswordHash("hash");
        a.setRole(Role.SALES_REP);
        userRepository.saveAndFlush(a);

        User admin = new User();
        admin.setTenantId(tenant.getId());
        admin.setName("Carlos");
        admin.setEmail("carlos@aurora.com.br");
        admin.setPasswordHash("hash");
        admin.setRole(Role.ADMIN);
        userRepository.saveAndFlush(admin);

        var result = userRepository.findByRoleOrderByName(Role.SALES_REP);

        assertThat(result).extracting(User::getName).containsExactly("Ana", "Bruno");
    }
}
```

- [ ] **Step 7: Confirm the expected compile failures**

Run: `cd mesh-suite-backend && ./mvnw compile 2>&1 | grep -c ERROR`
Expected: a non-zero count of errors, all in files that import `com.meshsuite.usuario.*` — this is expected and will be resolved by Tasks 2-3. Do not attempt to fix them in this task.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/user/ \
        mesh-suite-backend/src/main/resources/db/migration/V8__rename_usuario_to_user.sql \
        mesh-suite-backend/src/test/java/com/meshsuite/user/UserRepositoryTest.java
git commit -m "refactor(user): rename Usuario/Papel entity to User/Role (1/3 — core, codebase does not compile yet)"
```

---

### Task 2: Rename in the auth foundation (login, password reset, JWT, tenant provisioning)

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/AuthService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/AuthContextService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/AuthController.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/JwtAuthenticationFilter.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/TenantQueryService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/PasswordResetService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/PasswordResetToken.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/AuthControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/AuthControllerNoAmbientTransactionTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/JwtAuthenticationFilterTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetControllerNoAmbientTransactionTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/TenantIsolationTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetTokenRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetServiceTest.java`

**Interfaces:**
- Consumes (from Task 1): `com.meshsuite.user.User`, `Role`, `UserRepository`.
- Produces: `AuthService.LoginResult(User user, Tenant tenant, Empresa empresa)`; `AuthService.findByEmailForLogin(String): User`; `AuthService.findUserByIdBypassingTenant(UUID): User` (renamed from `findUsuarioByIdBypassingTenant`); `AuthContextService.userAndTenantActive(UUID tenantId, UUID userId): boolean` (renamed from `usuarioETenantAtivos`); `AuthContextService.userName(UUID userId): String` (renamed from `nomeDoUsuario`); `TenantQueryService.saveUser(UUID, String, String, Role)` (renamed from `saveUsuario`); `TenantQueryService.listUsers(): List<User>` (renamed from `listUsuarios`); `PasswordResetToken.getUserId()/setUserId(UUID)` (renamed from `getUsuarioId()/setUsuarioId`). `AuthContextService.Context` record, `MeResponse`, `JwtService`, `LoginRequest`/`ForgotPasswordRequest`/`ResetPasswordRequest`, and the `"papel"` JWT claim key are all **unchanged** — see the Global Constraints rename-boundary note.

- [ ] **Step 1: Update `AuthService.java`**

```java
package com.meshsuite.auth;

import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final EmpresaRepository empresaRepository;
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
                        EmpresaRepository empresaRepository, PasswordEncoder passwordEncoder,
                        EntityManager entityManager, @Lazy AuthService self) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.empresaRepository = empresaRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
        this.self = self;
    }

    public record LoginResult(User user, Tenant tenant, Empresa empresa) {
    }

    private record TenantAndEmpresa(Tenant tenant, Empresa empresa) {
    }

    // Runs before the caller's tenant is known -- needs the app_user_login_lookup
    // RLS policy (SET LOCAL app.bypass_tenant_check below).
    @Transactional(readOnly = true)
    public User findByEmailForLogin(String email) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        return userRepository.findByEmail(email).orElse(null);
    }

    // Used by PasswordResetService.confirmReset: a reset token identifies a user id
    // but not a tenant, so this lookup is also pre-tenant-context and needs the same
    // bypass. Reuses app_user_login_lookup -- that policy is unconditional on the
    // flag, not scoped to email lookups specifically.
    @Transactional(readOnly = true)
    public User findUserByIdBypassingTenant(UUID userId) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        return userRepository.findById(userId).orElse(null);
    }

    public LoginResult authenticate(String email, String senha) {
        User user = self.findByEmailForLogin(email);
        if (user == null || !passwordEncoder.matches(senha, user.getPasswordHash()) || !user.isActive()) {
            throw new AuthException();
        }

        TenantContext.set(user.getTenantId());
        try {
            TenantAndEmpresa loaded = self.loadTenantAndEmpresa(user.getTenantId());
            if (loaded == null || !loaded.tenant().isAtivo() || loaded.empresa() == null) {
                throw new AuthException();
            }

            self.registerAcesso(user.getId());
            return new LoginResult(user, loaded.tenant(), loaded.empresa());
        } finally {
            TenantContext.clear();
        }
    }

    // Consolidates the tenant+empresa lookups into one plain, hand-written
    // @Transactional method, proven to work with TenantContextAspect.
    @Transactional(readOnly = true)
    public TenantAndEmpresa loadTenantAndEmpresa(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return null;
        }
        List<Empresa> empresas = empresaRepository.findByTenantId(tenantId);
        Empresa empresa = empresas.isEmpty() ? null : empresas.get(0);
        return new TenantAndEmpresa(tenant, empresa);
    }

    @Transactional
    public void registerAcesso(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setLastAccessAt(Instant.now());
        userRepository.save(user);
    }
}
```

- [ ] **Step 2: Update `AuthContextService.java`**

```java
package com.meshsuite.auth;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthContextService {

    private final EntityManager entityManager;

    public AuthContextService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record Context(UUID usuarioId, UUID tenantId, String papel) {
    }

    // Callers must set TenantContext.set(tenantId) *before* invoking this method,
    // so TenantContextAspect can issue SET LOCAL before this query runs.
    @Transactional(readOnly = true)
    public boolean userAndTenantActive(UUID tenantId, UUID userId) {
        try {
            Object[] row = (Object[]) entityManager.createNativeQuery(
                            "SELECT u.active, t.ativo FROM app_user u JOIN tenant t ON t.id = u.tenant_id " +
                                    "WHERE u.id = :userId AND u.tenant_id = :tenantId")
                    .setParameter("userId", userId)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            return (boolean) row[0] && (boolean) row[1];
        } catch (NoResultException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public String userName(UUID userId) {
        return entityManager.createQuery(
                        "SELECT u.name FROM User u WHERE u.id = :id", String.class)
                .setParameter("id", userId)
                .getSingleResult();
    }
}
```

Note: `Context.usuarioId()`/`Context.papel()` are unchanged — see the rename-boundary constraint.

- [ ] **Step 3: Update `AuthController.java`**

Change only these three lines (everything else in the file is untouched):

```java
            String token = jwtService.generateToken(
                    result.user().getId(), result.tenant().getId(), result.empresa().getId(),
                    result.user().getRole().name(), request.manterConectado());
```

and:

```java
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthContextService.Context principal) {
        String nome = authContextService.userName(principal.usuarioId());
        return new MeResponse(nome, principal.papel());
    }
```

- [ ] **Step 4: Update `JwtAuthenticationFilter.java`**

Change only this one line:

```java
                    if (!authContextService.userAndTenantActive(tenantId, usuarioId)) {
```

- [ ] **Step 5: Update `TenantQueryService.java`**

```java
package com.meshsuite.auth;

import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantQueryService {

    private final EmpresaRepository empresaRepository;
    private final UserRepository userRepository;

    public TenantQueryService(EmpresaRepository empresaRepository, UserRepository userRepository) {
        this.empresaRepository = empresaRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void saveEmpresa(UUID tenantId, String razaoSocial, String cnpj) {
        Empresa empresa = new Empresa();
        empresa.setTenantId(tenantId);
        empresa.setRazaoSocial(razaoSocial);
        empresa.setCnpj(cnpj);
        empresaRepository.saveAndFlush(empresa);
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
    public List<Empresa> listEmpresas() {
        return empresaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }
}
```

- [ ] **Step 6: Update `PasswordResetService.java`**

```java
package com.meshsuite.auth;

import com.meshsuite.mail.MailService;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    // Field injection (not constructor), specifically so PasswordResetServiceTest
    // can construct this class directly with mocks and assign `self` manually --
    // see the test. In production, Spring wires this via @Lazy to avoid a
    // circular-construction failure. Package-private (no `private`) so the test,
    // which lives in the same package, can assign it directly.
    @Autowired
    @Lazy
    PasswordResetService self;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
                                 AuthService authService, MailService mailService, PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
    }

    // User lookups pre-tenant-context always go through AuthService, the one
    // class that sets app.bypass_tenant_check.
    public boolean requestReset(String email) {
        User user = authService.findByEmailForLogin(email);
        if (user == null || !user.isActive()) {
            return false; // caller still returns 200 with the generic message
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(sha256(rawToken));
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
        tokenRepository.save(token); // PasswordResetToken has no RLS -- no tenant context needed here

        String resetLink = "https://app.meshsuite.local/redefinir-senha?token=" + rawToken;
        mailService.sendPasswordResetEmail(email, resetLink);
        return true;
    }

    public void confirmReset(String rawToken, String novaSenha) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(AuthException::new);

        if (token.getUsadoEm() != null || Instant.now().isAfter(token.getExpiraEm())) {
            throw new AuthException();
        }

        User user = authService.findUserByIdBypassingTenant(token.getUserId());
        if (user == null) {
            throw new AuthException();
        }

        // app_user has RLS: updating password_hash needs app.tenant_id set to this
        // row's tenant. The bypass lookup above told us which tenant; route the
        // write through `self.` so TenantContextAspect actually applies.
        TenantContext.set(user.getTenantId());
        try {
            self.updateSenhaAndMarkTokenUsed(user, novaSenha, token);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void updateSenhaAndMarkTokenUsed(User user, String novaSenha, PasswordResetToken token) {
        user.setPasswordHash(passwordEncoder.encode(novaSenha));
        userRepository.save(user);

        token.setUsadoEm(Instant.now());
        tokenRepository.save(token);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 7: Update `PasswordResetToken.java`**

```java
package com.meshsuite.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
@Getter
@Setter
public class PasswordResetToken {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

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

- [ ] **Step 8: Update the test files**

`AuthControllerTest.java` — replace the three `import com.meshsuite.usuario.*` lines with `import com.meshsuite.user.Role;` and `import com.meshsuite.user.User;` and `import com.meshsuite.user.UserRepository;`; replace `@Autowired UsuarioRepository usuarioRepository;` with `@Autowired UserRepository userRepository;`; in each of `seedTenantWithUsuario`, `seedTenantWithInactiveUsuario`, `seedInactiveTenantWithUsuario`, replace:

```java
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@aurora.com.br");
        usuario.setSenhaHash(passwordEncoder.encode(senhaPlano));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);
```

with (adjusting the literal name/email per each method exactly as before — only field/type/variable names change):

```java
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode(senhaPlano));
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);
```

(`seedTenantWithInactiveUsuario` additionally has `usuario.setAtivo(false);` → `user.setActive(false);` right before the save call.) `jsonPath("$.papel").value("ADMINISTRADOR")` in `validLoginSetsCookieAndAllowsMe` becomes `jsonPath("$.papel").value("ADMIN")` — the JSON key stays `papel` (unchanged, `MeResponse` field), but the value is now the new `Role` literal.

`AuthControllerNoAmbientTransactionTest.java` — same import/type swap; inside the `TransactionTemplate.executeWithoutResult` block, replace:

```java
            Usuario usuario = new Usuario();
            usuario.setTenantId(tenant.getId());
            usuario.setNome("No-Tx Usuario");
            usuario.setEmail(email);
            usuario.setSenhaHash(passwordEncoder.encode(senha));
            usuario.setPapel(Papel.ADMINISTRADOR);
            usuarioRepository.saveAndFlush(usuario);
```

with:

```java
            User user = new User();
            user.setTenantId(tenant.getId());
            user.setName("No-Tx User");
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(senha));
            user.setRole(Role.ADMIN);
            userRepository.saveAndFlush(user);
```

`JwtAuthenticationFilterTest.java` — same import/type swap; in `rejectsRequestForDeactivatedUser`, replace:

```java
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@aurora.com.br");
        usuario.setSenhaHash("hash");
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String token = jwtService.generateToken(usuario.getId(), tenant.getId(), java.util.UUID.randomUUID(), "ADMINISTRADOR", false);
```

with:

```java
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        user.setActive(false);
        userRepository.saveAndFlush(user);
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String token = jwtService.generateToken(user.getId(), tenant.getId(), java.util.UUID.randomUUID(), "ADMIN", false);
```

`PasswordResetControllerTest.java` — same import/type swap; in `forgotPasswordSendsEmailWhenUsuarioExistsAndActive`, replace:

```java
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@aurora.com.br");
        usuario.setSenhaHash(passwordEncoder.encode("senha123"));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);
```

with:

```java
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);
```

`PasswordResetControllerNoAmbientTransactionTest.java` — same import/type swap; `UUID[] usuarioId = new UUID[1];` stays as a local variable name (untouched, it's just a UUID holder — the field it later reads is renamed, see below); inside the `TransactionTemplate` block, replace:

```java
            Usuario usuario = new Usuario();
            usuario.setTenantId(tenant.getId());
            usuario.setNome("Reset No-Tx Usuario");
            usuario.setEmail(email);
            usuario.setSenhaHash(passwordEncoder.encode(senhaAntiga));
            usuario.setPapel(Papel.ADMINISTRADOR);
            usuarioRepository.saveAndFlush(usuario);
            usuarioId[0] = usuario.getId();
```

with:

```java
            User user = new User();
            user.setTenantId(tenant.getId());
            user.setName("Reset No-Tx User");
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(senhaAntiga));
            user.setRole(Role.ADMIN);
            userRepository.saveAndFlush(user);
            usuarioId[0] = user.getId();
```

and further down, replace:

```java
        Usuario reloaded = authService.findUsuarioByIdBypassingTenant(usuarioId[0]);
        assertThat(reloaded).isNotNull();
        assertThat(passwordEncoder.matches(senhaNova, reloaded.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches(senhaAntiga, reloaded.getSenhaHash())).isFalse();
```

with:

```java
        User reloaded = authService.findUserByIdBypassingTenant(usuarioId[0]);
        assertThat(reloaded).isNotNull();
        assertThat(passwordEncoder.matches(senhaNova, reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(senhaAntiga, reloaded.getPasswordHash())).isFalse();
```

`TenantIsolationTest.java` — replace the `import com.meshsuite.usuario.*` lines with `com.meshsuite.user.Role`/`User`/`UserRepository`; replace `@Autowired UsuarioRepository usuarioRepository;` with `@Autowired UserRepository userRepository;` (this field becomes unused by the test body itself — the fixture goes through `tenantQueryService` — but keep it, it was already unused-by-body-only before this rename too, autowired for potential direct use); replace:

```java
        tenantQueryService.saveUsuario(tenantA.getId(), "Marina", "marina@aurora.com.br", Papel.ADMINISTRADOR);
```

with:

```java
        tenantQueryService.saveUser(tenantA.getId(), "Marina", "marina@aurora.com.br", Role.ADMIN);
```

(and the matching `boreal`/Carlos line), and replace:

```java
        assertThat(tenantQueryService.listUsuarios()).extracting(Usuario::getEmail).containsExactly("marina@aurora.com.br");
```

with:

```java
        assertThat(tenantQueryService.listUsers()).extracting(User::getEmail).containsExactly("marina@aurora.com.br");
```

(and the matching `boreal`/Carlos assertion).

`PasswordResetTokenRepositoryTest.java` — replace the `import com.meshsuite.usuario.*` lines with `com.meshsuite.user.Role`/`User`/`UserRepository`; replace `@Autowired UsuarioRepository usuarioRepository;` with `@Autowired UserRepository userRepository;`; replace:

```java
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@confeccaoaurora.com.br");
        usuario.setSenhaHash("hash");
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);

        PasswordResetToken token = new PasswordResetToken();
        token.setUsuarioId(usuario.getId());
```

with:

```java
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@confeccaoaurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
```

`PasswordResetServiceTest.java` — replace the `import com.meshsuite.usuario.*` lines with `com.meshsuite.user.User`/`UserRepository`; replace `@Mock UsuarioRepository usuarioRepository;` with `@Mock UserRepository userRepository;`; in the constructor call inside `service()`, `usuarioRepository`→`userRepository`; replace:

```java
    void requestResetSendsEmailWhenUsuarioExists() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("marina@aurora.com.br");
        usuario.setAtivo(true);
        when(authService.findByEmailForLogin("marina@aurora.com.br")).thenReturn(usuario);
```

with:

```java
    void requestResetSendsEmailWhenUserExists() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("marina@aurora.com.br");
        user.setActive(true);
        when(authService.findByEmailForLogin("marina@aurora.com.br")).thenReturn(user);
```

and rename `requestResetDoesNothingSilentlyWhenUsuarioDoesNotExist` to `requestResetDoesNothingSilentlyWhenUserDoesNotExist` (body unchanged — it never constructs a `Usuario`).

- [ ] **Step 9: Confirm the remaining compile failures are only in Task 3's files**

Run: `cd mesh-suite-backend && ./mvnw compile test-compile 2>&1 | grep "ERROR.*\.java"`
Expected: every remaining error is in `com.meshsuite.pedido.*`, `com.meshsuite.parceiro.ParceiroControllerTest`, or `com.meshsuite.produto.ProdutoControllerTest` — all of Task 3's scope. If an error appears anywhere else, this task missed a reference; fix it before moving on.

- [ ] **Step 10: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/ mesh-suite-backend/src/test/java/com/meshsuite/auth/
git commit -m "refactor(user): rename Usuario/Papel to User/Role (2/3 — auth foundation, codebase does not compile yet)"
```

---

### Task 3: Finish the rename — Parceiro/Produto/Pedido fixtures, frontend `api/usuarios.ts`→`api/users.ts`

**Files:**
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroControllerTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoControllerTest.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoRepositoryTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoControllerTest.java`
- Create: `mesh-suite-frontend/src/api/users.ts`
- Delete: `mesh-suite-frontend/src/api/usuarios.ts`
- Modify: `mesh-suite-frontend/src/views/PedidoFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts`

**Interfaces:**
- Consumes (from Tasks 1-2): `com.meshsuite.user.User`, `Role`, `UserRepository`.
- Produces: `PedidoService.buscarVendedorValido(UUID): User` (was `Usuario`); frontend `SalesRep { id: string; name: string }` and `listSalesReps(): Promise<SalesRep[]>` in `src/api/users.ts`, hitting `GET /users/sales-reps` — this is the **final** endpoint path Task 8's `UserController` will serve (Task 8 does not need to touch these frontend files again).

This task is where the whole rename lands — after it, `./mvnw compile test-compile` must succeed and the full backend suite must pass (except any test that depends on `GET /api/usuarios/representantes` actually being live at runtime, which is expected to be broken until Task 8 rebuilds it as `/api/users/sales-reps` — no backend test in this codebase does a live HTTP call to that path outside `UsuarioControllerTest`, already deleted in Task 1).

- [ ] **Step 1: Update `ParceiroControllerTest.java`**

Replace the three `import com.meshsuite.usuario.*` lines with:

```java
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
```

Replace `@Autowired UsuarioRepository usuarioRepository;` with `@Autowired UserRepository userRepository;`. Inside `loginAndGetCookie`, replace:

```java
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode("senha123"));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);
```

with:

```java
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);
```

- [ ] **Step 2: Update `ProdutoControllerTest.java`**

Identical change to Step 1, applied to this file (same import swap, same field rename, same `loginAndGetCookie` body replacement).

- [ ] **Step 3: Update `PedidoService.java`**

Replace the three `com.meshsuite.usuario.*` imports:

```java
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
```

Replace the field, constructor param, and assignment:

```java
    private final UserRepository userRepository;
```

```java
    public PedidoService(PedidoRepository pedidoRepository, ParceiroRepository parceiroRepository,
                          UserRepository userRepository, ProdutoRepository produtoRepository,
                          EntityManager entityManager) {
        this.pedidoRepository = pedidoRepository;
        this.parceiroRepository = parceiroRepository;
        this.userRepository = userRepository;
        this.produtoRepository = produtoRepository;
        this.entityManager = entityManager;
    }
```

Replace every `Usuario vendedor` local variable declaration (in `criar`, `atualizar`, `aplicar`'s signature) with `User vendedor`. Replace `buscarVendedorValido`:

```java
    private User buscarVendedorValido(UUID vendedorId) {
        User user = userRepository.findById(vendedorId)
                .orElseThrow(() -> new PedidoValidacaoException("Vendedor não encontrado"));
        if (user.getRole() != Role.SALES_REP) {
            throw new PedidoValidacaoException("O usuário selecionado não tem o papel Representante");
        }
        return user;
    }
```

- [ ] **Step 4: Update `PedidoServiceTest.java`**

Replace the three `import com.meshsuite.usuario.*` lines with `com.meshsuite.user.Role`/`User`/`UserRepository`. Replace `@Autowired UsuarioRepository usuarioRepository;` with `@Autowired UserRepository userRepository;`. Replace `criarVendedor`/`criarAdministrativo`:

```java
    private UUID criarVendedor(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u).getId();
    }

    private UUID criarAdministrativo(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Carlos");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.ADMINISTRATIVE);
        return userRepository.saveAndFlush(u).getId();
    }
```

- [ ] **Step 5: Update `PedidoRepositoryTest.java`**

Replace the three `import com.meshsuite.usuario.*` lines with `com.meshsuite.user.Role`/`User`/`UserRepository`. Replace `@Autowired UsuarioRepository usuarioRepository;` with `@Autowired UserRepository userRepository;`. Replace `criarVendedor`:

```java
    private User criarVendedor(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.SALES_REP);
        return userRepository.saveAndFlush(u);
    }
```

Replace every `Usuario vendedor` parameter/variable type across the file (the `novoPedido(UUID tenantId, Parceiro cliente, Usuario vendedor, int numero)` signature and every call site's local `Usuario vendedor = criarVendedor(...)`) with `User vendedor`.

- [ ] **Step 6: Update `PedidoControllerTest.java`**

Replace the three `import com.meshsuite.usuario.*` lines with `com.meshsuite.user.Role`/`User`/`UserRepository`. Replace `@Autowired UsuarioRepository usuarioRepository;` with `@Autowired UserRepository userRepository;`. Inside `loginAndSetUp`, replace:

```java
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
```

with:

```java
        User userLogin = new User();
        userLogin.setTenantId(tenant.getId());
        userLogin.setName("Marina");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMIN);
        userRepository.saveAndFlush(userLogin);

        User vendedor = new User();
        vendedor.setTenantId(tenant.getId());
        vendedor.setName("Carla Vendedora");
        vendedor.setEmail("carla-" + codigo + "@" + codigo + ".com.br");
        vendedor.setPasswordHash("hash");
        vendedor.setRole(Role.SALES_REP);
        userRepository.saveAndFlush(vendedor);
```

- [ ] **Step 7: Write `mesh-suite-frontend/src/api/users.ts`**

```typescript
import { apiClient } from './client'

export interface SalesRep {
  id: string
  name: string
}

export async function listSalesReps(): Promise<SalesRep[]> {
  const { data } = await apiClient.get<SalesRep[]>('/users/sales-reps')
  return data
}
```

- [ ] **Step 8: Delete `mesh-suite-frontend/src/api/usuarios.ts`**

```bash
git rm mesh-suite-frontend/src/api/usuarios.ts
```

- [ ] **Step 9: Update `PedidoFormView.vue`**

Replace the import line:

```typescript
import { listSalesReps, type SalesRep } from '@/api/users'
```

Replace the ref declaration:

```typescript
const representantes = ref<SalesRep[]>([])
```

Replace the load call inside `onMounted`:

```typescript
    representantes.value = await listSalesReps()
```

Replace the vendedor `<select>` option's label binding in the template:

```html
            <option v-for="r in representantes" :key="r.id" :value="r.id">{{ r.name }}</option>
```

- [ ] **Step 10: Update `PedidoFormView.spec.ts`**

Replace the import and mock declaration:

```typescript
import * as usersApi from '@/api/users'
```

```typescript
vi.mock('@/api/users')
```

Replace the fixture:

```typescript
const salesRepBase = { id: 'v1', name: 'Carla Vendedora' }
```

Replace every use of `representanteBase` with `salesRepBase`, and every `usuariosApi.listarRepresentantes` with `usersApi.listSalesReps` (the mock setup in `beforeEach` and the assertion in the "loads the representantes list" test).

- [ ] **Step 11: Run the full backend suite**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS, every test passing. This is the first point since Task 1 where the backend compiles and all tests pass again — investigate and fix immediately if anything fails; do not move on with a red suite.

- [ ] **Step 12: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run && npx vue-tsc -b`
Expected: all tests passing, typecheck clean.

- [ ] **Step 13: Commit**

```bash
git add mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroControllerTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoControllerTest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/pedido/ \
        mesh-suite-frontend/src/api/users.ts \
        mesh-suite-frontend/src/views/PedidoFormView.vue \
        mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts
git commit -m "refactor(user): rename Usuario/Papel to User/Role (3/3 — Pedido, Parceiro/Produto test fixtures, frontend)"
```

---

### Task 4: Permission data model — `Module`, `Action`, `user_permission`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/Module.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/Action.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserPermissionGrant.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/User.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserRepository.java`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V9__create_user_permission.sql`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/user/UserPermissionRepositoryTest.java`

**Interfaces:**
- Produces: `Module` enum (`CUSTOMER, PRODUCT, ORDER, USER`); `Action` enum (`VIEW, CREATE, EDIT, DELETE`); `User.getPermissions(): Set<UserPermissionGrant>`; `UserRepository.hasPermission(UUID userId, Module module, Action action): boolean` — consumed by Task 5's `PermissionService`/`PermissionAspect`.
- Consumes: `User` (Task 1), `UserRepository` (Task 1).

`UserPermissionGrant` follows the exact pattern already established by `Parceiro.papeis` (`@ElementCollection` of an enum-bearing value into a child table with no own `tenant_id`) — except this child table needs **two** enum columns per row (module + action) instead of one, so it's an `@Embeddable` composite value inside the collection rather than a bare enum. No separate JPA entity or repository is needed for `user_permission` — same reason `parceiro_papel` never got one.

- [ ] **Step 1: Write `Module.java`**

```java
package com.meshsuite.auth;

public enum Module {
    CUSTOMER,
    PRODUCT,
    ORDER,
    USER
}
```

- [ ] **Step 2: Write `Action.java`**

```java
package com.meshsuite.auth;

public enum Action {
    VIEW,
    CREATE,
    EDIT,
    DELETE
}
```

- [ ] **Step 3: Write `UserPermissionGrant.java`**

```java
package com.meshsuite.user;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

@Embeddable
public class UserPermissionGrant {

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 20)
    private Module module;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10)
    private Action action;

    public UserPermissionGrant() {
    }

    public UserPermissionGrant(Module module, Action action) {
        this.module = module;
        this.action = action;
    }

    public Module getModule() {
        return module;
    }

    public void setModule(Module module) {
        this.module = module;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    // equals/hashCode required: this is the element type of a Set<UserPermissionGrant>
    // (User.permissions) -- without them, Set membership/dedup falls back to identity,
    // and Hibernate's dirty-checking for @ElementCollection compares elements by value.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPermissionGrant that)) return false;
        return module == that.module && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, action);
    }
}
```

- [ ] **Step 4: Add the `permissions` field to `User.java`**

Add this field and its two imports to the existing `User.java` from Task 1 (do not touch any other field):

```java
    import jakarta.persistence.CollectionTable;
    import jakarta.persistence.ElementCollection;
    import jakarta.persistence.FetchType;
    import jakarta.persistence.JoinColumn;
```

```java
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permission", joinColumns = @JoinColumn(name = "user_id"))
    private Set<UserPermissionGrant> permissions = new HashSet<>();
```

(add `import java.util.HashSet;` and `import java.util.Set;` too — the file's existing imports only have `java.time.Instant` and `java.util.UUID`.)

- [ ] **Step 5: Add `hasPermission` to `UserRepository.java`**

```java
package com.meshsuite.user;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    List<User> findByRoleOrderByName(Role role);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.permissions p " +
            "WHERE u.id = :userId AND p.module = :module AND p.action = :action")
    boolean hasPermission(@Param("userId") UUID userId, @Param("module") Module module, @Param("action") Action action);
}
```

- [ ] **Step 6: Write the migration**

```sql
CREATE TABLE user_permission (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    module VARCHAR(20) NOT NULL CHECK (module IN ('CUSTOMER','PRODUCT','ORDER','USER')),
    action VARCHAR(10) NOT NULL CHECK (action IN ('VIEW','CREATE','EDIT','DELETE')),
    PRIMARY KEY (user_id, module, action)
);

ALTER TABLE user_permission ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_permission FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent app_user
-- row's own RLS policy, matched by user_id. Same pattern as parceiro_papel.
CREATE POLICY user_permission_tenant_isolation ON user_permission
    USING (EXISTS (
        SELECT 1 FROM app_user u
        WHERE u.id = user_permission.user_id
          AND u.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

- [ ] **Step 7: Write the failing test**

```java
package com.meshsuite.user;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPermissionRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
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

    private User newUser(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.ADMIN);
        return u;
    }

    @Test
    @Transactional
    void savesAndReadsPermissionsViaElementCollection() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = newUser(tenant.getId(), "marina@aurora.com.br");
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.CREATE));
        User saved = userRepository.saveAndFlush(user);
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPermissions()).containsExactlyInAnyOrder(
                new UserPermissionGrant(Module.CUSTOMER, Action.VIEW),
                new UserPermissionGrant(Module.CUSTOMER, Action.CREATE));
    }

    @Test
    @Transactional
    void hasPermissionReturnsTrueOnlyForGrantedModuleAndAction() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = newUser(tenant.getId(), "marina@aurora.com.br");
        user.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.CREATE));
        User saved = userRepository.saveAndFlush(user);

        assertThat(userRepository.hasPermission(saved.getId(), Module.ORDER, Action.CREATE)).isTrue();
        assertThat(userRepository.hasPermission(saved.getId(), Module.ORDER, Action.DELETE)).isFalse();
        assertThat(userRepository.hasPermission(saved.getId(), Module.CUSTOMER, Action.CREATE)).isFalse();
    }

    @Test
    @Transactional
    void removingAGrantFromTheSetDeletesItsRow() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = newUser(tenant.getId(), "marina@aurora.com.br");
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.EDIT));
        User saved = userRepository.saveAndFlush(user);

        saved.getPermissions().clear();
        userRepository.saveAndFlush(saved);
        entityManager.clear();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM user_permission WHERE user_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void rlsHidesPermissionRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = newUser(tenant.getId(), "marina@aurora.com.br");
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
        User saved = userRepository.saveAndFlush(user);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM user_permission WHERE user_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 8: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=UserPermissionRepositoryTest`
Expected: PASS (4/4).

- [ ] **Step 9: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/Module.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/Action.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/UserPermissionGrant.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/User.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/UserRepository.java \
        mesh-suite-backend/src/main/resources/db/migration/V9__create_user_permission.sql \
        mesh-suite-backend/src/test/java/com/meshsuite/user/UserPermissionRepositoryTest.java
git commit -m "feat(user): add Module/Action permission model on User via element collection"
```

---

### Task 5: `@RequiresPermission` annotation + `PermissionAspect` + wiring

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/RequiresPermission.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/PermissionDeniedException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/PermissionService.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/PermissionAspect.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/PermissionAspectTest.java`

**Interfaces:**
- Consumes (from Task 4): `Module`, `Action`, `UserRepository.hasPermission(UUID, Module, Action): boolean`. From the already-existing auth foundation: `AuthContextService.Context` (`usuarioId()` field, unchanged), `TenantContext`, `TenantContextAspect` (`@Order(1)`).
- Produces: `@RequiresPermission(module = ..., action = ...)` — a method-level annotation for `@Transactional` service methods; `PermissionDeniedException` → 403; `PermissionService.hasPermission(UUID, Module, Action): boolean` (thin wrapper Tasks 6-11 can also call directly if ever needed outside the aspect). Consumed by every task from here on that adds enforcement (Tasks 9, 10, 11) and by Task 7's `UserService`.

- [ ] **Step 1: Write `RequiresPermission.java`**

```java
package com.meshsuite.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {
    Module module();
    Action action();
}
```

- [ ] **Step 2: Write `PermissionDeniedException.java`**

```java
package com.meshsuite.auth;

public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException() {
        super("Você não tem permissão para executar esta ação");
    }
}
```

- [ ] **Step 3: Write `PermissionService.java`**

```java
package com.meshsuite.user;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PermissionService {

    private final UserRepository userRepository;

    public PermissionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean hasPermission(UUID userId, Module module, Action action) {
        return userRepository.hasPermission(userId, module, action);
    }
}
```

- [ ] **Step 4: Write `PermissionAspect.java`**

```java
package com.meshsuite.auth;

import com.meshsuite.user.PermissionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// Runs after TenantContextAspect (@Order(1)): the permission check below queries
// user_permission, which has RLS via EXISTS against app_user.tenant_id. If this
// ran before SET LOCAL app.tenant_id, the query would find zero rows and deny
// every request unconditionally, regardless of what's actually granted -- see the
// Global Constraints note on @RequiresPermission ordering.
@Aspect
@Component
@Order(2)
public class PermissionAspect {

    private final PermissionService permissionService;

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint pjp, RequiresPermission requiresPermission) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthContextService.Context principal = (AuthContextService.Context) auth.getPrincipal();
        if (!permissionService.hasPermission(principal.usuarioId(), requiresPermission.module(), requiresPermission.action())) {
            throw new PermissionDeniedException();
        }
        return pjp.proceed();
    }
}
```

- [ ] **Step 5: Register `PermissionDeniedException` in `GlobalExceptionHandler`**

Add this handler to the existing file (`PermissionDeniedException` is in the same `com.meshsuite.auth` package as `GlobalExceptionHandler`, so no fully-qualified reference is needed, unlike the domain exceptions below it):

```java
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<Map<String, String>> handlePermissionDenied(PermissionDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 6: Write the failing test**

```java
package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class PermissionAspectTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;
    @Autowired ProbeService probeService;

    // Minimal throwaway service, annotated exactly like a real domain service will
    // be from Task 9 onward -- exists only so this test can exercise
    // PermissionAspect's real AOP wiring (ordering with TenantContextAspect,
    // RLS-scoped permission lookup) before any production service actually uses
    // @RequiresPermission yet. Picked up by the normal component scan since it's a
    // @Service in the com.meshsuite.auth package.
    @Service
    static class ProbeService {
        @Transactional(readOnly = true)
        @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
        public String probe() {
            return "ok";
        }
    }

    private void authenticateAs(UUID userId) {
        var principal = new AuthContextService.Context(userId, null, "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void allowsWhenPermissionGranted() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("aurora");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        User saved = userRepository.saveAndFlush(user);

        TenantContext.set(tenant.getId());
        authenticateAs(saved.getId());
        try {
            assertThat(probeService.probe()).isEqualTo("ok");
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deniesWhenPermissionNotGranted() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("boreal");
        tenant.setNome("boreal");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Carlos");
        user.setEmail("carlos@boreal.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        User saved = userRepository.saveAndFlush(user);

        TenantContext.set(tenant.getId());
        authenticateAs(saved.getId());
        try {
            assertThrows(PermissionDeniedException.class, () -> probeService.probe());
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=PermissionAspectTest`
Expected: PASS (2/2). If `allowsWhenPermissionGranted` fails with a denial, check the aspect's `@Order(2)` — it means the permission check ran before tenant context was applied.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/RequiresPermission.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/PermissionDeniedException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/PermissionAspect.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/PermissionService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/PermissionAspectTest.java
git commit -m "feat(auth): add @RequiresPermission annotation and PermissionAspect (403 on denial)"
```

---

### Task 6: `Profile` enum, `User.phone`/`profile` fields, DTOs, exceptions, specifications

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/Profile.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/User.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/EmailAlreadyExistsException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserSpecifications.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/PermissionDto.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserListItemResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserCountsResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserStatusRequest.java`

**Interfaces:**
- Consumes (from Tasks 1, 4): `User`, `Role`, `UserRepository`, `Module`, `Action`, `UserPermissionGrant`.
- Produces: `Profile` enum (`ADMIN, MANAGER, SALES, VIEWER`); `User.getPhone()/setPhone(String)`, `User.getProfile()/setProfile(Profile)`; `UserRepository` gains `JpaSpecificationExecutor<User>` and `long countByActive(boolean)`; `UserSpecifications.withSearch/withProfile/withActive`; the 6 DTOs, consumed by Task 7's `UserService`.

- [ ] **Step 1: Write `Profile.java`**

```java
package com.meshsuite.user;

public enum Profile {
    ADMIN,
    MANAGER,
    SALES,
    VIEWER
}
```

- [ ] **Step 2: Add `phone`/`profile` to `User.java`**

Add these two fields and one import (`import jakarta.persistence.Column;` is already present via the `jakarta.persistence.*` wildcard import from Task 1 — no new import needed for `phone`; `profile`'s `@Enumerated`/`@Column` annotations are already imported the same way):

```java
    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Profile profile;
```

- [ ] **Step 3: Extend `UserRepository.java`**

```java
package com.meshsuite.user;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    List<User> findByRoleOrderByName(Role role);
    long countByActive(boolean active);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.permissions p " +
            "WHERE u.id = :userId AND p.module = :module AND p.action = :action")
    boolean hasPermission(@Param("userId") UUID userId, @Param("module") Module module, @Param("action") Action action);
}
```

- [ ] **Step 4: Write the exceptions**

```java
package com.meshsuite.user;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("Usuário não encontrado");
    }
}
```

```java
package com.meshsuite.user;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("Já existe um usuário cadastrado com este e-mail");
    }
}
```

- [ ] **Step 5: Write `UserSpecifications.java`**

```java
package com.meshsuite.user;

import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), term),
                cb.like(cb.lower(root.get("email")), term));
    }

    public static Specification<User> withProfile(Profile profile) {
        if (profile == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("profile"), profile);
    }

    public static Specification<User> withActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
```

- [ ] **Step 6: Write the DTOs**

`dto/PermissionDto.java`:

```java
package com.meshsuite.user.dto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import jakarta.validation.constraints.NotNull;

public record PermissionDto(@NotNull Module module, @NotNull Action action) {
}
```

`dto/UserRequest.java`:

```java
package com.meshsuite.user.dto;

import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        @NotNull Role role,
        @NotNull Profile profile,
        boolean active,
        String password,
        String confirmPassword,
        List<@Valid PermissionDto> permissions) {
}
```

`dto/UserResponse.java`:

```java
package com.meshsuite.user.dto;

import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        Role role,
        Profile profile,
        boolean active,
        List<PermissionDto> permissions) {
}
```

`dto/UserListItemResponse.java`:

```java
package com.meshsuite.user.dto;

import com.meshsuite.user.Profile;

import java.util.UUID;

public record UserListItemResponse(
        UUID id,
        String name,
        String email,
        Profile profile,
        boolean active) {
}
```

`dto/UserCountsResponse.java`:

```java
package com.meshsuite.user.dto;

public record UserCountsResponse(long total, long active, long inactive) {
}
```

`dto/UserStatusRequest.java`:

```java
package com.meshsuite.user.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull Boolean active) {
}
```

- [ ] **Step 7: Confirm it compiles**

Run: `cd mesh-suite-backend && ./mvnw compile`
Expected: BUILD SUCCESS. There is no runnable test in this task — it's pure structure Task 7 builds on; `UserPermissionRepositoryTest` (Task 4) and every other previously-passing test must still be green.

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS, no regressions.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/user/
git commit -m "feat(user): add Profile enum, phone field, DTOs, exceptions and specifications"
```

---

### Task 7: `UserService` — CRUD, email uniqueness, password rules, permission persistence

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserValidationException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/user/UserServiceTest.java`

**Interfaces:**
- Consumes (from Tasks 1, 4, 5, 6): `User`, `Role`, `Profile`, `UserPermissionGrant`, `UserRepository`, `Module`, `Action`, `@RequiresPermission`, `PermissionDeniedException`, all 6 DTOs, `UserNotFoundException`, `EmailAlreadyExistsException`.
- Produces: `UserService.list(String, Profile, Boolean, Pageable): Page<UserListItemResponse>`, `.counts(): UserCountsResponse`, `.findById(UUID): UserResponse`, `.create(UUID tenantId, UserRequest): UserResponse`, `.update(UUID, UserRequest): UserResponse`, `.updateStatus(UUID, boolean): UserResponse` — consumed by Task 8's `UserController`.

- [ ] **Step 1: Write `UserValidationException.java`**

```java
package com.meshsuite.user;

public class UserValidationException extends RuntimeException {
    public UserValidationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Write `UserService.java`**

```java
package com.meshsuite.user;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.user.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public Page<UserListItemResponse> list(String search, Profile profile, Boolean active, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.withSearch(search),
                UserSpecifications.withProfile(profile),
                UserSpecifications.withActive(active));
        return userRepository.findAll(spec, pageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public UserCountsResponse counts() {
        long active = userRepository.countByActive(true);
        long inactive = userRepository.countByActive(false);
        return new UserCountsResponse(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public UserResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.CREATE)
    public UserResponse create(UUID tenantId, UserRequest request) {
        validate(request, null);

        User user = new User();
        user.setTenantId(tenantId);
        applyRequest(user, request);
        return toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.EDIT)
    public UserResponse update(UUID id, UserRequest request) {
        validate(request, id);

        User user = findEntityById(id);
        applyRequest(user, request);
        return toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.EDIT)
    public UserResponse updateStatus(UUID id, boolean active) {
        User user = findEntityById(id);
        user.setActive(active);
        return toResponse(userRepository.saveAndFlush(user));
    }

    private User findEntityById(UUID id) {
        return userRepository.findById(id).orElseThrow(UserNotFoundException::new);
    }

    private void validate(UserRequest request, UUID currentId) {
        boolean duplicate = userRepository.findByEmail(request.email())
                .filter(u -> currentId == null || !u.getId().equals(currentId))
                .isPresent();
        if (duplicate) {
            throw new EmailAlreadyExistsException();
        }

        boolean creating = currentId == null;
        String password = request.password();
        if (creating && (password == null || password.isBlank())) {
            throw new UserValidationException("Senha é obrigatória");
        }
        if (password != null && !password.isBlank()) {
            if (!password.equals(request.confirmPassword())) {
                throw new UserValidationException("As senhas não coincidem");
            }
            if (!PASSWORD_PATTERN.matcher(password).matches()) {
                throw new UserValidationException("A senha deve ter no mínimo 8 caracteres, com letras e números");
            }
        }
    }

    private void applyRequest(User user, UserRequest request) {
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setProfile(request.profile());
        user.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        user.getPermissions().clear();
        List<PermissionDto> permissions = request.permissions() == null ? List.of() : request.permissions();
        for (PermissionDto dto : permissions) {
            user.getPermissions().add(new UserPermissionGrant(dto.module(), dto.action()));
        }
    }

    private UserListItemResponse toListItem(User u) {
        return new UserListItemResponse(u.getId(), u.getName(), u.getEmail(), u.getProfile(), u.isActive());
    }

    private UserResponse toResponse(User u) {
        List<PermissionDto> permissions = u.getPermissions().stream()
                .map(p -> new PermissionDto(p.getModule(), p.getAction()))
                .toList();
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getProfile(),
                u.isActive(), permissions);
    }
}
```

- [ ] **Step 3: Write the failing test**

Every `UserService` method carries `@RequiresPermission`, so every test in this file needs an authenticated principal with the right grant — set one up in a shared helper, and use a second, permission-less principal only in the dedicated denial test.

```java
package com.meshsuite.user;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.PermissionDeniedException;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.dto.PermissionDto;
import com.meshsuite.user.dto.UserRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class UserServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserService userService;
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
        return tenant.getId();
    }

    private void authenticateAsFullAdmin(UUID tenantId) {
        User admin = new User();
        admin.setTenantId(tenantId);
        admin.setName("Admin Caller");
        admin.setEmail("admin-caller-" + UUID.randomUUID() + "@aurora.com.br");
        admin.setPasswordHash("hash");
        admin.setRole(Role.ADMINISTRATIVE);
        admin.setProfile(Profile.ADMIN);
        admin.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
        admin.getPermissions().add(new UserPermissionGrant(Module.USER, Action.CREATE));
        admin.getPermissions().add(new UserPermissionGrant(Module.USER, Action.EDIT));
        User saved = userRepository.saveAndFlush(admin);
        authenticateAs(saved.getId());
    }

    private void authenticateAsNoPermissions(UUID tenantId) {
        User noPerms = new User();
        noPerms.setTenantId(tenantId);
        noPerms.setName("No Permissions Caller");
        noPerms.setEmail("no-perms-" + UUID.randomUUID() + "@aurora.com.br");
        noPerms.setPasswordHash("hash");
        noPerms.setRole(Role.SALES_REP);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);
        authenticateAs(saved.getId());
    }

    private void authenticateAs(UUID userId) {
        var principal = new AuthContextService.Context(userId, null, "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private UserRequest request(String email, String password, String confirmPassword, List<PermissionDto> permissions) {
        return new UserRequest("Marina", email, "(11) 99999-9999", Role.ADMINISTRATIVE, Profile.ADMIN, true,
                password, confirmPassword, permissions);
    }

    @Test
    void createsAndRetrievesUser() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        assertThat(criado.name()).isEqualTo("Marina");
        assertThat(criado.active()).isTrue();

        var buscado = userService.findById(criado.id());
        assertThat(buscado.email()).isEqualTo("marina@aurora.com.br");
    }

    @Test
    void rejectsDuplicateEmailInSameTenant() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        assertThrows(EmailAlreadyExistsException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", "outraSenha1", "outraSenha1", List.of())));
    }

    @Test
    void rejectsCreateWithoutPassword() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        assertThrows(UserValidationException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", null, null, List.of())));
    }

    @Test
    void rejectsMismatchedPasswordConfirmation() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        assertThrows(UserValidationException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "outraSenha1", List.of())));
    }

    @Test
    void rejectsPasswordWithoutDigits() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        assertThrows(UserValidationException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", "somenteletras", "somenteletras", List.of())));
    }

    @Test
    void updateWithBlankPasswordKeepsExistingHash() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));
        String originalHash = userRepository.findById(criado.id()).orElseThrow().getPasswordHash();

        userService.update(criado.id(), request("marina@aurora.com.br", "", "", List.of()));

        assertThat(userRepository.findById(criado.id()).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
    }

    @Test
    void updateReplacesThePermissionList() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234",
                List.of(new PermissionDto(Module.CUSTOMER, Action.VIEW))));

        var atualizado = userService.update(criado.id(), request("marina@aurora.com.br", "", "",
                List.of(new PermissionDto(Module.PRODUCT, Action.EDIT), new PermissionDto(Module.ORDER, Action.VIEW))));

        assertThat(atualizado.permissions()).containsExactlyInAnyOrder(
                new PermissionDto(Module.PRODUCT, Action.EDIT), new PermissionDto(Module.ORDER, Action.VIEW));
    }

    @Test
    void updateStatusTogglesActive() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        var atualizado = userService.updateStatus(criado.id(), false);

        assertThat(atualizado.active()).isFalse();
    }

    @Test
    void countsByActiveStatus() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        var a = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));
        userService.create(tenantId, request("carlos@aurora.com.br", "senha1234", "senha1234", List.of()));
        userService.updateStatus(a.id(), false);

        // Includes the admin-caller fixture itself, created active in authenticateAsFullAdmin.
        var counts = userService.counts();

        assertThat(counts.total()).isEqualTo(3);
        assertThat(counts.active()).isEqualTo(2);
        assertThat(counts.inactive()).isEqualTo(1);
    }

    @Test
    void listsWithSearchFilter() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        var pagina = userService.list("marina", null, null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).email()).isEqualTo("marina@aurora.com.br");
    }

    @Test
    void deniesCreateWhenCallerLacksPermission() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsNoPermissions(tenantId);

        assertThrows(PermissionDeniedException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of())));
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=UserServiceTest`
Expected: PASS (11/11).

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/user/UserValidationException.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/UserService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/user/UserServiceTest.java
git commit -m "feat(user): add UserService — CRUD, email uniqueness, password rules, permission persistence"
```

---

### Task 8: `UserController`, scoped exception handler, `/sales-reps`, integration tests

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/SalesRepResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/user/UserExceptionHandler.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/user/UserControllerTest.java`

**Interfaces:**
- Consumes (from Tasks 6-7): `UserService` (all 6 methods), all DTOs, `UserRepository.findByRoleOrderByName`.
- Produces: `UserController` at `/api/users` — `GET /`, `GET /counts`, `GET /sales-reps`, `GET /{id}`, `POST /`, `PUT /{id}`, `PATCH /{id}/status`. `GET /sales-reps` bypasses `UserService` entirely (calls `UserRepository` directly, same as the old deleted `UsuarioController`) — this is what keeps it un-gated by `@RequiresPermission`, per the Global Constraints note.

- [ ] **Step 1: Write `dto/SalesRepResponse.java`**

```java
package com.meshsuite.user.dto;

import java.util.UUID;

public record SalesRepResponse(UUID id, String name) {
}
```

- [ ] **Step 2: Write `UserController.java`**

```java
package com.meshsuite.user;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.user.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Page<UserListItemResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Profile profile,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return userService.list(search, profile, active, pageable);
    }

    @GetMapping("/counts")
    public UserCountsResponse counts() {
        return userService.counts();
    }

    // Deliberately bypasses UserService/@RequiresPermission -- support lookup for
    // the Pedido form's vendor picker, not "viewing the Users module". See the
    // Global Constraints note.
    @GetMapping("/sales-reps")
    public List<SalesRepResponse> salesReps() {
        return userRepository.findByRoleOrderByName(Role.SALES_REP).stream()
                .map(u -> new SalesRepResponse(u.getId(), u.getName()))
                .toList();
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                @Valid @RequestBody UserRequest request) {
        UserResponse response = userService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request) {
        return userService.updateStatus(id, request.active());
    }
}
```

- [ ] **Step 3: Write `UserExceptionHandler.java`**

```java
package com.meshsuite.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = UserController.class)
public class UserExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um usuário cadastrado com este e-mail"));
    }
}
```

- [ ] **Step 4: Register the domain-unique exceptions in `GlobalExceptionHandler`**

Add these three handlers to the existing file, after the `PermissionDeniedException` handler added in Task 5:

```java
    @ExceptionHandler(com.meshsuite.user.UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(
            com.meshsuite.user.UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.user.EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyExists(
            com.meshsuite.user.EmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.user.UserValidationException.class)
    public ResponseEntity<Map<String, String>> handleUserValidation(
            com.meshsuite.user.UserValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 5: Write the failing test**

```java
package com.meshsuite.user;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.auth.Module;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class UserControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String loginAndGetCookie(String codigo, String email, String cnpjEmpresa, boolean grantUserPermissions) throws Exception {
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
        user.setRole(Role.ADMINISTRATIVE);
        user.setProfile(Profile.ADMIN);
        if (grantUserPermissions) {
            user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
            user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.CREATE));
            user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.EDIT));
        }
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String userPayload(String email) {
        return """
                {
                  "name": "Carla Vendedora",
                  "email": "%s",
                  "phone": "(11) 98888-7777",
                  "role": "SALES_REP",
                  "profile": "SALES",
                  "active": true,
                  "password": "senha1234",
                  "confirmPassword": "senha1234",
                  "permissions": [ { "module": "ORDER", "action": "VIEW" } ]
                }
                """.formatted(email);
    }

    @Test
    void createsListsUpdatesAndTogglesStatus() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", true);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Carla Vendedora"))
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/users").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='carla@aurora.com.br')]").exists());

        mockMvc.perform(get("/api/users/counts").cookie(cookie))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/users/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Carla Vendedora Sênior",
                                  "email": "carla@aurora.com.br",
                                  "phone": "(11) 98888-7777",
                                  "role": "SALES_REP",
                                  "profile": "SALES",
                                  "active": true,
                                  "password": "",
                                  "confirmPassword": "",
                                  "permissions": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carla Vendedora Sênior"));

        mockMvc.perform(patch("/api/users/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void rejectsDuplicateEmailWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", true);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingNameWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", true);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "sem-nome@aurora.com.br",
                                  "role": "SALES_REP",
                                  "profile": "SALES",
                                  "active": true,
                                  "password": "senha1234",
                                  "confirmPassword": "senha1234",
                                  "permissions": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesCreateWhenCallerLacksUserPermission() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", false);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isForbidden());
    }

    @Test
    void salesRepsEndpointWorksEvenWithoutUserPermission() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", false);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/users/sales-reps").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void tenantACannotAccessTenantBsUser() throws Exception {
        String tokenA = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", true);
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/users").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal", "carlos@boreal.com.br", "55666777000155", true);
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/users/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=UserControllerTest`
Expected: PASS (7/7).

- [ ] **Step 7: Run the full backend suite**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS, no regressions anywhere (Parceiro/Produto/Pedido/Auth/User all green).

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/user/dto/SalesRepResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/UserController.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/UserExceptionHandler.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/user/UserControllerTest.java
git commit -m "feat(user): add UserController with CRUD, sales-reps lookup and RLS-safe integration tests"
```

---

### Task 9: Enforce permission on `ParceiroService` (Cliente)

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/parceiro/ParceiroControllerTest.java`

**Interfaces:**
- Consumes (from Tasks 4-5): `Module.CUSTOMER`, `Action`, `@RequiresPermission`. From Task 1: `User`, `Role`, `UserRepository`. From Task 4: `UserPermissionGrant`.
- Produces: every `ParceiroService` method now requires a permission grant; callers without it get `PermissionDeniedException` → 403.

Adding `@RequiresPermission` to every method means **every existing test that calls `ParceiroService` directly or through `MockMvc` now needs an authenticated principal with the matching grant**, or it fails with a `ClassCastException`/403 that has nothing to do with what that test is actually checking. This task's real work is as much in the two test files' shared setup helpers as in the service itself.

- [ ] **Step 1: Add `@RequiresPermission` to every `ParceiroService` method**

Add this import block to the existing file:

```java
import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
```

Add one `@RequiresPermission` line to each of the 7 existing methods, directly below its `@Transactional`:

```java
    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public Page<ParceiroSummaryResponse> listar(...
```
```java
    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public ParceiroResumoResponse resumo() {
```
```java
    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public ParceiroResponse buscarPorId(UUID id) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.CREATE)
    public ParceiroResponse criar(UUID tenantId, ParceiroRequest request) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.EDIT)
    public ParceiroResponse atualizar(UUID id, ParceiroRequest request) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.EDIT)
    public ParceiroResponse atualizarStatus(UUID id, StatusParceiro novoStatus) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.DELETE)
    public void excluir(UUID id) {
```

(Everything else in the file — the method bodies, `validar`, `aplicar`, `toSummary`, `toResponse` — is unchanged.)

- [ ] **Step 2: Update `ParceiroServiceTest.java`'s shared setup**

Add these imports:

```java
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
```

Add the field: `@Autowired UserRepository userRepository;`

Replace `setUpTenant` and the existing `@AfterEach`:

```java
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
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }
```

This one change fixes every existing test in the file — they all call `setUpTenant(...)` first, and now get an authenticated, fully-permissioned caller for free.

- [ ] **Step 3: Add one denial test to `ParceiroServiceTest.java`**

```java
    @Test
    void deniesListingWhenCallerLacksCustomerViewPermission() {
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
        noPerms.setRole(Role.SALES_REP);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(com.meshsuite.auth.PermissionDeniedException.class,
                () -> parceiroService.listar(null, null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }
```

- [ ] **Step 4: Update `ParceiroControllerTest.java`'s login helper**

Add these imports:

```java
import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.user.Profile;
import com.meshsuite.user.UserPermissionGrant;
```

Inside `loginAndGetCookie`, right after `user.setRole(Role.ADMIN);` and before `userRepository.saveAndFlush(user);`, add:

```java
        user.setProfile(Profile.ADMIN);
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.CREATE));
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.EDIT));
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.DELETE));
```

- [ ] **Step 5: Add one 403 test to `ParceiroControllerTest.java`**

This test needs its own login helper variant that grants no `CUSTOMER` permission — add it alongside the existing `loginAndGetCookie`:

```java
    private String loginWithoutCustomerPermission(String codigo, String email, String cnpjEmpresa) throws Exception {
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

    @Test
    void listingWithoutCustomerViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutCustomerPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/parceiros").cookie(cookie))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 6: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=ParceiroServiceTest,ParceiroControllerTest`
Expected: PASS, every previously-existing test still green plus the two new denial tests.

- [ ] **Step 7: Run the full backend suite**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS — this also confirms `ProdutoControllerTest`/`PedidoControllerTest`/`PedidoServiceTest` (which construct `Parceiro`s indirectly through `ParceiroRepository`, not `ParceiroService`) are unaffected, since RLS/repository-level access never goes through `@RequiresPermission`.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/parceiro/ParceiroService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/parceiro/
git commit -m "feat(parceiro): enforce CUSTOMER module permission on every ParceiroService method"
```

---

### Task 10: Enforce permission on `ProdutoService` (Produto)

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/produto/ProdutoControllerTest.java`

**Interfaces:**
- Consumes (from Tasks 4-5): `Module.PRODUCT`, `Action`, `@RequiresPermission`. From Task 1: `User`, `Role`, `UserRepository`. From Task 4: `UserPermissionGrant`.
- Produces: every `ProdutoService` method now requires a permission grant.

Same shape as Task 9, applied to the Produto domain — `ProdutoService`'s methods are named identically to `ParceiroService`'s (`listar`, `resumo`, `buscarPorId`, `criar`, `atualizar`, `atualizarStatus`, `excluir`), and `ProdutoServiceTest`/`ProdutoControllerTest` follow the exact same `setUpTenant`/`loginAndGetCookie` shape as their Parceiro counterparts.

- [ ] **Step 1: Add `@RequiresPermission` to every `ProdutoService` method**

Add the same import block as Task 9:

```java
import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
```

Add one `@RequiresPermission` line to each of the 7 existing methods, directly below its `@Transactional` — identical pattern to Task 9, substituting `Module.PRODUCT`:

```java
    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<ProdutoSummaryResponse> listar(String busca, StatusProduto status, Pageable pageable) {
```
```java
    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProdutoResumoResponse resumo() {
```
```java
    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProdutoResponse buscarPorId(UUID id) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public ProdutoResponse criar(UUID tenantId, ProdutoRequest request) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProdutoResponse atualizar(UUID id, ProdutoRequest request) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProdutoResponse atualizarStatus(UUID id, StatusProduto novoStatus) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
```

- [ ] **Step 2: Update `ProdutoServiceTest.java`'s shared setup**

Same change as Task 9 Step 2, applied to this file — add the same import block (`com.meshsuite.auth.Action`, `AuthContextService`, `Module`, `com.meshsuite.user.Profile/Role/User/UserPermissionGrant/UserRepository`, `UsernamePasswordAuthenticationToken`, `SecurityContextHolder`, `java.util.List`), add `@Autowired UserRepository userRepository;`, and replace `setUpTenant`/`@AfterEach` with:

```java
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
```

- [ ] **Step 3: Add one denial test to `ProdutoServiceTest.java`**

```java
    @Test
    void deniesListingWhenCallerLacksProductViewPermission() {
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
        noPerms.setRole(Role.SALES_REP);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(com.meshsuite.auth.PermissionDeniedException.class,
                () -> produtoService.listar(null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }
```

- [ ] **Step 4: Update `ProdutoControllerTest.java`'s login helper**

Same change as Task 9 Step 4 — add the same imports, and inside `loginAndGetCookie`, right after `user.setRole(Role.ADMIN);`, add:

```java
        user.setProfile(Profile.ADMIN);
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.CREATE));
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.EDIT));
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.DELETE));
```

- [ ] **Step 5: Add one 403 test to `ProdutoControllerTest.java`**

Same shape as Task 9 Step 5 — add a `loginWithoutProductPermission` helper (identical body to `loginWithoutCustomerPermission`, just renamed) and:

```java
    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/produtos").cookie(cookie))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 6: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=ProdutoServiceTest,ProdutoControllerTest`
Expected: PASS, every previously-existing test still green plus the two new denial tests.

- [ ] **Step 7: Run the full backend suite**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/produto/ProdutoService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/produto/
git commit -m "feat(produto): enforce PRODUCT module permission on every ProdutoService method"
```

---

### Task 11: Enforce permission on `PedidoService` (Pedido)

**Files:**
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoService.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoServiceTest.java`
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoControllerTest.java`

**Interfaces:**
- Consumes (from Tasks 4-5): `Module.ORDER`, `Action`, `@RequiresPermission`.
- Produces: every `PedidoService` method now requires a permission grant.

Same shape as Tasks 9-10, with two differences: `PedidoService`'s status-advance method is called `avancarStatus`, not `atualizarStatus`, and both `PedidoServiceTest` and `PedidoControllerTest` already have a `UserRepository userRepository` field (renamed from `UsuarioRepository` in Task 3) — do not re-add it.

- [ ] **Step 1: Add `@RequiresPermission` to every `PedidoService` method**

Add the same import block as Tasks 9-10:

```java
import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
```

Add one `@RequiresPermission` line to each of the 6 existing methods, directly below its `@Transactional`, substituting `Module.ORDER`:

```java
    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public Page<PedidoSummaryResponse> listar(String busca, StatusPedido status, Pageable pageable) {
```
```java
    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public PedidoResumoResponse resumo() {
```
```java
    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public PedidoResponse buscarPorId(UUID id) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.CREATE)
    public PedidoResponse criar(UUID tenantId, PedidoRequest request) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.EDIT)
    public PedidoResponse atualizar(UUID id, PedidoRequest request) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.EDIT)
    public PedidoResponse avancarStatus(UUID id, StatusPedido novoStatus) {
```
```java
    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.DELETE)
    public void excluir(UUID id) {
```

- [ ] **Step 2: Update `PedidoServiceTest.java`'s shared setup**

Add these imports (note: `Role`/`User`/`UserRepository` are already imported from Task 3's rename — only add what's new):

```java
import com.meshsuite.auth.Action;
import com.meshsuite.auth.AuthContextService;
import com.meshsuite.auth.Module;
import com.meshsuite.user.Profile;
import com.meshsuite.user.UserPermissionGrant;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
```

Replace `setUpTenant` and the existing `@AfterEach` (this file's `@AfterEach` already exists from before this plan — just add the `SecurityContextHolder.clearContext();` line to it):

```java
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
        caller.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }
```

(add `import java.util.List;` if the file doesn't already have one — it doesn't, per its current import block.)

- [ ] **Step 3: Add one denial test to `PedidoServiceTest.java`**

```java
    @Test
    void deniesListingWhenCallerLacksOrderViewPermission() {
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
        noPerms.setRole(Role.SALES_REP);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(com.meshsuite.auth.PermissionDeniedException.class,
                () -> pedidoService.listar(null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }
```

- [ ] **Step 4: Update `PedidoControllerTest.java`'s login helper**

Add these imports:

```java
import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.user.Profile;
import com.meshsuite.user.UserPermissionGrant;
```

Inside `loginAndSetUp`, right after `userLogin.setRole(Role.ADMIN);` and before `userRepository.saveAndFlush(userLogin);` — grant the permission to the **login** user only, not `vendedor` (a plain data fixture who never authenticates):

```java
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.CREATE));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.EDIT));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.DELETE));
```

Also give `vendedor` a `Profile` (required, `@NotNull` at the JPA column level from Task 6) even though it grants no permissions — add `vendedor.setProfile(Profile.SALES);` right after `vendedor.setRole(Role.SALES_REP);`.

- [ ] **Step 5: Add one 403 test to `PedidoControllerTest.java`**

```java
    private String loginWithoutOrderPermission(String codigo, String email, String cnpjEmpresa) throws Exception {
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

    @Test
    void listingWithoutOrderViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutOrderPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/pedidos").cookie(cookie))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 6: Run the tests**

Run: `cd mesh-suite-backend && ./mvnw test -Dtest=PedidoServiceTest,PedidoControllerTest`
Expected: PASS, every previously-existing test still green plus the two new denial tests.

- [ ] **Step 7: Run the full backend suite**

Run: `cd mesh-suite-backend && ./mvnw test`
Expected: BUILD SUCCESS — every domain's tests green (Parceiro, Produto, Pedido, Auth, User).

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/pedido/PedidoService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoServiceTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/pedido/PedidoControllerTest.java
git commit -m "feat(pedido): enforce ORDER module permission on every PedidoService method"
```

---

### Task 12: Frontend API layer + `UserFormView.vue`

**Files:**
- Modify: `mesh-suite-frontend/src/api/users.ts`
- Create: `mesh-suite-frontend/src/views/UserFormView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts`

**Interfaces:**
- Consumes (from Task 8): `GET/POST/PUT /api/users`, `PATCH /api/users/{id}/status`, `GET /api/users/{id}`.
- Produces: `UserRequest`, `UserResponse`, `UserListItem`, `Role`, `Profile`, `Permission` types and `listUsers`, `getUserCounts`, `getUser`, `createUser`, `updateUser`, `updateUserStatus` functions in `api/users.ts` (added alongside the existing `SalesRep`/`listSalesReps` from Task 3) — consumed by Task 13's `UsersListView.vue`. Router gains routes named `usuarios-novo` and `usuarios-editar`.

`UserFormView.vue` includes the "friendly 403 message" handling from the start (same reasoning as every earlier domain: pre-apply what Task 14 will retroactively add to Cliente/Produto/Pedido, rather than building it wrong here and fixing it later).

- [ ] **Step 1: Extend `api/users.ts`**

Add these types and functions to the existing file (keep `SalesRep`/`listSalesReps` exactly as they are):

```typescript
export type Role = 'ADMINISTRATIVE' | 'SALES_REP' | 'PRODUCTION' | 'OUTSOURCED' | 'ADMIN'
export type Profile = 'ADMIN' | 'MANAGER' | 'SALES' | 'VIEWER'
export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER'
export type ActionName = 'VIEW' | 'CREATE' | 'EDIT' | 'DELETE'

export interface Permission {
  module: ModuleName
  action: ActionName
}

export interface UserRequest {
  name: string
  email: string
  phone: string
  role: Role
  profile: Profile
  active: boolean
  password: string
  confirmPassword: string
  permissions: Permission[]
}

export interface UserResponse {
  id: string
  name: string
  email: string
  phone: string
  role: Role
  profile: Profile
  active: boolean
  permissions: Permission[]
}

export interface UserListItem {
  id: string
  name: string
  email: string
  profile: Profile
  active: boolean
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListUsersParams {
  search?: string
  profile?: Profile
  active?: boolean
  page?: number
  size?: number
}

export interface UserCounts {
  total: number
  active: number
  inactive: number
}

export async function listUsers(params: ListUsersParams): Promise<Page<UserListItem>> {
  const { data } = await apiClient.get<Page<UserListItem>>('/users', { params })
  return data
}

export async function getUserCounts(): Promise<UserCounts> {
  const { data } = await apiClient.get<UserCounts>('/users/counts')
  return data
}

export async function getUser(id: string): Promise<UserResponse> {
  const { data } = await apiClient.get<UserResponse>(`/users/${id}`)
  return data
}

export async function createUser(payload: UserRequest): Promise<UserResponse> {
  const { data } = await apiClient.post<UserResponse>('/users', payload)
  return data
}

export async function updateUser(id: string, payload: UserRequest): Promise<UserResponse> {
  const { data } = await apiClient.put<UserResponse>(`/users/${id}`, payload)
  return data
}

export async function updateUserStatus(id: string, active: boolean): Promise<void> {
  await apiClient.patch(`/users/${id}/status`, { active })
}
```

- [ ] **Step 2: Write `UserFormView.vue`**

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Usuário' : 'Novo Usuário'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados do Usuário</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Nome completo *</label>
            <input v-model="form.name" data-test="name" />
            <p v-if="erros.name" class="field-error">{{ erros.name }}</p>
          </div>
          <div>
            <label class="field-label">E-mail *</label>
            <input v-model="form.email" data-test="email" />
            <p v-if="erros.email" class="field-error">{{ erros.email }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Telefone</label>
            <input v-model="form.phone" placeholder="(11) 99999-9999" />
          </div>
          <div>
            <label class="field-label">Papel *</label>
            <select v-model="form.role" data-test="role">
              <option value="">Selecione...</option>
              <option v-for="r in ROLES" :key="r" :value="r">{{ ROLE_LABELS[r] }}</option>
            </select>
            <p v-if="erros.role" class="field-error">{{ erros.role }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Perfil de Acesso *</label>
            <select v-model="form.profile" data-test="profile" @change="applyDefaultPermissions">
              <option value="">Selecione...</option>
              <option v-for="p in PROFILES" :key="p" :value="p">{{ PROFILE_LABELS[p] }}</option>
            </select>
            <p v-if="erros.profile" class="field-error">{{ erros.profile }}</p>
          </div>
          <div>
            <label class="field-label">Status</label>
            <select v-model="form.active">
              <option :value="true">Ativo</option>
              <option :value="false">Inativo</option>
            </select>
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Acesso ao Sistema</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Senha{{ modoEdicao ? '' : ' *' }}</label>
            <input v-model="form.password" type="password" data-test="password" />
          </div>
          <div>
            <label class="field-label">Confirmar Senha{{ modoEdicao ? '' : ' *' }}</label>
            <input v-model="form.confirmPassword" type="password" data-test="confirm-password" />
          </div>
        </div>
        <p v-if="erros.password" class="field-error">{{ erros.password }}</p>
        <p v-if="erros.confirmPassword" class="field-error">{{ erros.confirmPassword }}</p>
        <p class="field-hint">Mínimo 8 caracteres, com letras e números. Deixe em branco para manter a senha atual.</p>
      </section>

      <details class="card">
        <summary>Permissões por Módulo</summary>
        <p class="field-hint">As permissões são herdadas do perfil selecionado. Você pode personalizar abaixo.</p>
        <table class="tabela-permissoes">
          <thead>
            <tr>
              <th></th>
              <th v-for="a in ACTIONS" :key="a">{{ ACTION_LABELS[a] }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in MODULES" :key="m">
              <td>{{ MODULE_LABELS[m] }}</td>
              <td v-for="a in ACTIONS" :key="a">
                <input
                  type="checkbox"
                  :checked="isChecked(m, a)"
                  :data-test="`perm-${m}-${a}`"
                  @change="togglePermission(m, a)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </details>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Usuário</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getUser,
  createUser,
  updateUser,
  type UserRequest,
  type Role,
  type Profile,
  type ModuleName,
  type ActionName,
  type Permission,
} from '@/api/users'

const ROLES: Role[] = ['ADMINISTRATIVE', 'SALES_REP', 'PRODUCTION', 'OUTSOURCED', 'ADMIN']
const ROLE_LABELS: Record<Role, string> = {
  ADMINISTRATIVE: 'Administrativo',
  SALES_REP: 'Representante',
  PRODUCTION: 'Produção',
  OUTSOURCED: 'Terceirizado',
  ADMIN: 'Administrador',
}
const PROFILES: Profile[] = ['ADMIN', 'MANAGER', 'SALES', 'VIEWER']
const PROFILE_LABELS: Record<Profile, string> = {
  ADMIN: 'Admin',
  MANAGER: 'Gerente',
  SALES: 'Vendedor',
  VIEWER: 'Visualizador',
}
const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
}
const ACTIONS: ActionName[] = ['VIEW', 'CREATE', 'EDIT', 'DELETE']
const ACTION_LABELS: Record<ActionName, string> = {
  VIEW: 'Visualizar',
  CREATE: 'Criar',
  EDIT: 'Editar',
  DELETE: 'Excluir',
}

// Business-judgment default matrix, not confirmed by PRD or prototype -- see the
// plan's Task 6 note. Only used to pre-check the grid on Perfil selection; the
// backend never recomputes this, it persists whatever is checked at submit time.
const DEFAULT_MATRIX: Record<Profile, Permission[]> = {
  ADMIN: [
    ...MODULES.flatMap((m) => ACTIONS.filter((a) => !(m === 'USER' && a === 'DELETE')).map((a) => ({ module: m, action: a }))),
  ],
  MANAGER: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' }, { module: 'PRODUCT', action: 'CREATE' }, { module: 'PRODUCT', action: 'EDIT' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
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
  ],
}

const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

interface FormState {
  name: string
  email: string
  phone: string
  role: Role | ''
  profile: Profile | ''
  active: boolean
  password: string
  confirmPassword: string
  permissions: Permission[]
}

function novoFormulario(): FormState {
  return {
    name: '',
    email: '',
    phone: '',
    role: '',
    profile: '',
    active: true,
    password: '',
    confirmPassword: '',
    permissions: [],
  }
}

const form = reactive<FormState>(novoFormulario())
const erros = reactive<{ name?: string; email?: string; role?: string; profile?: string; password?: string; confirmPassword?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

function isChecked(module: ModuleName, action: ActionName) {
  return form.permissions.some((p) => p.module === module && p.action === action)
}

function togglePermission(module: ModuleName, action: ActionName) {
  const index = form.permissions.findIndex((p) => p.module === module && p.action === action)
  if (index >= 0) {
    form.permissions.splice(index, 1)
  } else {
    form.permissions.push({ module, action })
  }
}

function applyDefaultPermissions() {
  if (form.profile) {
    form.permissions = [...DEFAULT_MATRIX[form.profile]]
  }
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const user = await getUser(id)
      form.name = user.name
      form.email = user.email
      form.phone = user.phone ?? ''
      form.role = user.role
      form.profile = user.profile
      form.active = user.active
      form.permissions = [...user.permissions]
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do usuário.'
    }
  }
})

function validar(): boolean {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
  erros.email = form.email.trim() ? undefined : 'Campo obrigatório'
  erros.role = form.role ? undefined : 'Campo obrigatório'
  erros.profile = form.profile ? undefined : 'Campo obrigatório'
  erros.password = !modoEdicao.value && !form.password ? 'Campo obrigatório' : undefined
  if (form.password) {
    if (form.password !== form.confirmPassword) {
      erros.confirmPassword = 'As senhas não coincidem'
    } else if (!PASSWORD_PATTERN.test(form.password)) {
      erros.confirmPassword = 'Mínimo 8 caracteres, com letras e números'
    } else {
      erros.confirmPassword = undefined
    }
  } else {
    erros.confirmPassword = undefined
  }
  return !erros.name && !erros.email && !erros.role && !erros.profile && !erros.password && !erros.confirmPassword
}

function paraPayload(): UserRequest {
  return {
    name: form.name,
    email: form.email,
    phone: form.phone,
    role: form.role as Role,
    profile: form.profile as Profile,
    active: form.active,
    password: form.password,
    confirmPassword: form.confirmPassword,
    permissions: form.permissions,
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
      await updateUser(id, payload)
    } else {
      await createUser(payload)
    }
    router.push({ name: 'usuarios' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um usuário cadastrado com este e-mail.'
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
  router.push({ name: 'usuarios' })
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

details.card summary {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  cursor: pointer;
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

.field-hint {
  font-size: 11px;
  color: var(--pm-text-mid);
  margin: 4px 0 0;
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

.tabela-permissoes {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  margin-top: 10px;
}

.tabela-permissoes th,
.tabela-permissoes td {
  text-align: center;
  padding: 6px 8px;
  border-top: 1px solid var(--pm-border-light);
}

.tabela-permissoes td:first-child {
  text-align: left;
  font-weight: 600;
  color: var(--pm-text-dark);
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

- [ ] **Step 3: Add the `usuarios-novo`/`usuarios-editar` routes**

In `mesh-suite-frontend/src/router/index.ts`, add the import:

```typescript
import UserFormView from '@/views/UserFormView.vue'
```

and add these two routes after the `pedidos-editar` route:

```typescript
    { path: '/usuarios/novo', name: 'usuarios-novo', component: UserFormView },
    { path: '/usuarios/:id/editar', name: 'usuarios-editar', component: UserFormView },
```

(The `usuarios` list route is added in Task 13, same pattern as every earlier domain's form-before-list task split.)

- [ ] **Step 4: Write the failing test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import UserFormView from '@/views/UserFormView.vue'
import * as usersApi from '@/api/users'

vi.mock('@/api/users')

function mountWithRouter(path = '/usuarios/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/usuarios', name: 'usuarios', component: { template: '<div />' } },
      { path: '/usuarios/novo', name: 'usuarios-novo', component: UserFormView },
      { path: '/usuarios/:id/editar', name: 'usuarios-editar', component: UserFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(UserFormView, { global: { plugins: [router] } }),
  }))
}

describe('UserFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field errors when name/email/role/profile/password are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(usersApi.createUser).not.toHaveBeenCalled()
  })

  it('rejects mismatched password confirmation', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('SALES')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('outraSenha1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('As senhas não coincidem')
  })

  it('pre-checks the permission grid from the default matrix when a Perfil is selected', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="profile"]').setValue('VIEWER')
    await wrapper.find('[data-test="profile"]').trigger('change')
    await flushPromises()

    expect((wrapper.find('[data-test="perm-CUSTOMER-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-CREATE"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(usersApi.createUser).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('SALES')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(usersApi.createUser).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('usuarios')
  })

  it('shows a conflict message on duplicate e-mail (409)', async () => {
    vi.mocked(usersApi.createUser).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('SALES')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um usuário cadastrado com este e-mail')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(usersApi.createUser).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('SALES')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing user data in edit mode with blank password fields', async () => {
    vi.mocked(usersApi.getUser).mockResolvedValue({
      id: 'u1', name: 'Carla', email: 'carla@aurora.com.br', phone: '(11) 98888-7777',
      role: 'SALES_REP', profile: 'SALES', active: true,
      permissions: [{ module: 'ORDER', action: 'VIEW' }],
    } as any)

    const { wrapper } = await mountWithRouter('/usuarios/u1/editar')
    await flushPromises()

    expect(usersApi.getUser).toHaveBeenCalledWith('u1')
    expect((wrapper.find('[data-test="name"]').element as HTMLInputElement).value).toBe('Carla')
    expect((wrapper.find('[data-test="password"]').element as HTMLInputElement).value).toBe('')
  })

  it('shows an error message when loading user data fails in edit mode', async () => {
    vi.mocked(usersApi.getUser).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/usuarios/u1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do usuário.')
  })
})
```

- [ ] **Step 5: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/UserFormView.spec.ts`
Expected: PASS (8/8).

- [ ] **Step 6: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run && npx vue-tsc -b`
Expected: all tests passing, typecheck clean.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/api/users.ts \
        mesh-suite-frontend/src/views/UserFormView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts
git commit -m "feat(user): add full CRUD API layer and UserFormView with permission grid"
```

---

### Task 13: `UsersListView.vue`, routing, sidebar activation

**Files:**
- Create: `mesh-suite-frontend/src/views/UsersListView.vue`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/UsersListView.spec.ts`

**Interfaces:**
- Consumes (from Task 12): `listUsers`, `getUserCounts`, `updateUserStatus`, `UserListItem`, `UserCounts`, `Profile` from `@/api/users`. Also consumes the `usuarios-novo`/`usuarios-editar` route names already added in Task 12.
- Produces: the `usuarios` route (list); flips the sidebar's "Usuários" nav item from inert to active.

- [ ] **Step 1: Write `UsersListView.vue`**

```vue
<template>
  <AppShell title="Usuários">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar usuário por nome ou e-mail..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.profile" @change="carregar(0)">
        <option value="">Perfil</option>
        <option value="ADMIN">Admin</option>
        <option value="MANAGER">Gerente</option>
        <option value="SALES">Vendedor</option>
        <option value="VIEWER">Visualizador</option>
      </select>
      <select v-model="filtros.active" @change="carregar(0)">
        <option value="">Status</option>
        <option value="true">Ativo</option>
        <option value="false">Inativo</option>
      </select>
      <button type="button" class="btn-primary" data-test="novo-usuario" @click="novoUsuario">+ Novo Usuário</button>
    </div>

    <div v-if="counts" class="resumo">
      <span class="resumo-item">{{ counts.total }} Total</span>
      <span class="resumo-item resumo-ativo">{{ counts.active }} Ativos</span>
      <span class="resumo-item resumo-inativo">{{ counts.inactive }} Inativos</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nome</th>
            <th>E-mail</th>
            <th>Perfil</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in pagina.content" :key="user.id">
            <td>{{ user.name }}</td>
            <td>{{ user.email }}</td>
            <td><span class="badge-perfil">{{ PROFILE_LABELS[user.profile] }}</span></td>
            <td><span class="badge" :class="user.active ? 'badge-ATIVO' : 'badge-INATIVO'">{{ user.active ? 'Ativo' : 'Inativo' }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(user.id, $event)"
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
        v-if="userAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarUsuario(userAcoesAtual.id)">Editar</div>
        <div data-test="acao-status" @click="alternarStatus(userAcoesAtual)">
          {{ userAcoesAtual.active ? 'Inativar' : 'Ativar' }}
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
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  listUsers,
  getUserCounts,
  updateUserStatus,
  type UserListItem,
  type UserCounts,
  type Page as ApiPage,
  type Profile,
} from '@/api/users'

const PROFILE_LABELS: Record<Profile, string> = {
  ADMIN: 'Admin',
  MANAGER: 'Gerente',
  SALES: 'Vendedor',
  VIEWER: 'Visualizador',
}

const router = useRouter()

const filtros = reactive({ busca: '', profile: '', active: '' })
const pagina = ref<ApiPage<UserListItem>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<UserCounts | null>(null)
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const userAcoesAtual = computed(() =>
  pagina.value.content.find((u) => u.id === acoesAbertas.value) ?? null,
)

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listUsers({
      search: filtros.busca || undefined,
      profile: (filtros.profile || undefined) as Profile | undefined,
      active: filtros.active === '' ? undefined : filtros.active === 'true',
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de usuários.'
  }
}

async function carregarCounts() {
  erro.value = ''
  try {
    counts.value = await getUserCounts()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de usuários.'
  }
}

function novoUsuario() {
  router.push({ name: 'usuarios-novo' })
}

function editarUsuario(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'usuarios-editar', params: { id } })
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

async function alternarStatus(user: UserListItem) {
  acoesAbertas.value = null
  erro.value = ''
  try {
    await updateUserStatus(user.id, !user.active)
    await Promise.all([carregar(pagina.value.number), carregarCounts()])
  } catch (err: any) {
    erro.value = err?.response?.status === 403
      ? 'Você não tem permissão para executar esta ação.'
      : 'Não foi possível atualizar o status.'
  }
}

onMounted(() => {
  carregar(0)
  carregarCounts()
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

.resumo-ativo {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.resumo-inativo {
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

.badge-perfil {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: var(--pm-bg);
  color: var(--pm-text-mid);
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

- [ ] **Step 2: Add the `usuarios` list route**

In `mesh-suite-frontend/src/router/index.ts`, add the import:

```typescript
import UsersListView from '@/views/UsersListView.vue'
```

and add this route right before the `usuarios-novo` route added in Task 12:

```typescript
    { path: '/usuarios', name: 'usuarios', component: UsersListView },
```

- [ ] **Step 3: Activate the "Usuários" sidebar item**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, change:

```typescript
  { icon: '👤', label: 'Usuários', route: null },
```

to:

```typescript
  { icon: '👤', label: 'Usuários', route: '/usuarios' },
```

- [ ] **Step 4: Write the failing test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import UsersListView from '@/views/UsersListView.vue'
import * as usersApi from '@/api/users'

vi.mock('@/api/users')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/usuarios', name: 'usuarios', component: UsersListView },
      { path: '/usuarios/novo', name: 'usuarios-novo', component: { template: '<div />' } },
      { path: '/usuarios/:id/editar', name: 'usuarios-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/usuarios')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(UsersListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const userBase = { id: 'u1', name: 'Carla Vendedora', email: 'carla@aurora.com.br', profile: 'SALES' as const, active: true }

describe('UsersListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usersApi.listUsers).mockResolvedValue({
      content: [userBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(usersApi.getUserCounts).mockResolvedValue({ total: 1, active: 1, inactive: 0 })
  })

  it('loads and displays the user list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Carla Vendedora')
    expect(wrapper.text()).toContain('1 Total')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('carla')
    await flushPromises()

    expect(usersApi.listUsers).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'carla' }))
  })

  it('navigates to the create form when "+ Novo Usuário" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-usuario"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('usuarios-novo')
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('usuarios-editar')
    expect(router.currentRoute.value.params.id).toBe('u1')
  })

  it('has no exclusion item in the Ações menu', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')

    expect(wrapper.find('[data-test="acao-excluir"]').exists()).toBe(false)
  })

  it('toggles a user status via the Ações menu', async () => {
    vi.mocked(usersApi.updateUserStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-status"]').trigger('click')
    await flushPromises()

    expect(usersApi.updateUserStatus).toHaveBeenCalledWith('u1', false)
  })

  it('shows an error message when loading the user list fails', async () => {
    vi.mocked(usersApi.listUsers).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de usuários.')
  })
})
```

- [ ] **Step 5: Run the tests**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/UsersListView.spec.ts`
Expected: PASS (7/7).

- [ ] **Step 6: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run && npx vue-tsc -b`
Expected: all tests passing, typecheck clean.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/views/UsersListView.vue \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/views/__tests__/UsersListView.spec.ts
git commit -m "feat(user): add UsersListView, activate Usuários sidebar nav"
```

---

### Task 14: Retrofit friendly 403 handling onto Cliente/Produto/Pedido's save actions

**Files:**
- Modify: `mesh-suite-frontend/src/views/ClienteFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/ClienteFormView.spec.ts`
- Modify: `mesh-suite-frontend/src/views/ProdutoFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts`
- Modify: `mesh-suite-frontend/src/views/PedidoFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts`

**Interfaces:**
- Consumes: nothing new — this task only touches each form's existing `salvar()` catch chain.

`UserFormView.vue` (Task 12) already has this 403 branch, since it was written after the backend enforcement existed. This task retrofits the same one-branch addition onto the three form views that predate the enforcement work — Cliente/Produto/Pedido — the same "pre-apply learned fix, then retrofit the earlier domains" rhythm already used for the Teleport-dropdown fix during the Produto slice.

This task is deliberately scoped to the three **form views'** save actions (`criar`/`atualizar`, where a permission denial is most consequential to a user's workflow). The three **list views'** status-toggle/delete actions are not touched here — that's a smaller, lower-value gap, noted as a deferred minor rather than expanding this already-large plan further.

- [ ] **Step 1: Add the 403 branch to `ProdutoFormView.vue`**

Locate the `salvar()` function's catch block:

```typescript
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um produto cadastrado com este SKU.'
    } else if (err?.response?.status === 400) {
      erroGeral.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      erroGeral.value = 'Não foi possível salvar. Tente novamente em instantes.'
    }
  } finally {
```

Insert a 403 branch between the 409 and 400 branches:

```typescript
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um produto cadastrado com este SKU.'
    } else if (err?.response?.status === 403) {
      erroGeral.value = 'Você não tem permissão para executar esta ação.'
    } else if (err?.response?.status === 400) {
      erroGeral.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      erroGeral.value = 'Não foi possível salvar. Tente novamente em instantes.'
    }
  } finally {
```

- [ ] **Step 2: Add a regression test to `ProdutoFormView.spec.ts`**

Add this test alongside the existing "shows a conflict message on duplicate SKU (409)" test:

```typescript
  it('shows a permission-denied message on 403', async () => {
    vi.mocked(produtosApi.criarProduto).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })
```

- [ ] **Step 3: Add the 403 branch to `PedidoFormView.vue`**

Locate the `salvar()` function's catch block:

```typescript
  } catch (err: any) {
    if (err?.response?.status === 400) {
      erroGeral.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      erroGeral.value = 'Não foi possível salvar. Tente novamente em instantes.'
    }
  } finally {
```

Insert a 403 branch before the 400 branch:

```typescript
  } catch (err: any) {
    if (err?.response?.status === 403) {
      erroGeral.value = 'Você não tem permissão para executar esta ação.'
    } else if (err?.response?.status === 400) {
      erroGeral.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      erroGeral.value = 'Não foi possível salvar. Tente novamente em instantes.'
    }
  } finally {
```

- [ ] **Step 4: Add a regression test to `PedidoFormView.spec.ts`**

Add this test alongside the existing conflict/validation-message tests, reusing the same cliente/vendedor/item setup as the "submits the form and navigates to the list on success" test:

```typescript
  it('shows a permission-denied message on 403', async () => {
    vi.mocked(pedidosApi.criarPedido).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()
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

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })
```

- [ ] **Step 5: Add the 403 branch to `ClienteFormView.vue`**

Locate the `salvar()` function's catch block — it follows the same `if (409) {...} else if (400) {...} else {...}` (or `if (409) {...} else {...}`) shape as `ProdutoFormView.vue`'s did before Step 1, adapted for Cliente's own 409 message (document duplicate, not SKU duplicate). Insert a `else if (err?.response?.status === 403) { erroGeral.value = 'Você não tem permissão para executar esta ação.' }` branch into that chain, positioned before the final generic `else`, following the exact same insertion shape as Steps 1 and 3. If the existing chain has no 400 branch (only 409 and a generic else, since Cliente's business-rule violations may not use a distinct 400 case the same way), add the 403 branch directly before the generic `else`.

- [ ] **Step 6: Add a regression test to `ClienteFormView.spec.ts`**

Add a test following the exact same shape as Step 2/4's, adapted to Cliente's existing test setup (mocking `criarParceiro` to reject with `{ response: { status: 403 } }`, filling in Cliente's required fields, submitting, asserting `wrapper.text()` contains `'Você não tem permissão para executar esta ação'`) — mirror whichever existing "shows a conflict message" test is already in that spec file for the exact required-field-filling sequence to reuse.

- [ ] **Step 7: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run && npx vue-tsc -b`
Expected: all tests passing (including the 3 new 403 regression tests), typecheck clean.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-frontend/src/views/ClienteFormView.vue \
        mesh-suite-frontend/src/views/__tests__/ClienteFormView.spec.ts \
        mesh-suite-frontend/src/views/ProdutoFormView.vue \
        mesh-suite-frontend/src/views/__tests__/ProdutoFormView.spec.ts \
        mesh-suite-frontend/src/views/PedidoFormView.vue \
        mesh-suite-frontend/src/views/__tests__/PedidoFormView.spec.ts
git commit -m "feat(frontend): show a friendly message on 403 in Cliente/Produto/Pedido save actions"
```

**Note for the final whole-branch review:** the three list views' (`ClientesListView`, `ProdutosListView`, `PedidosListView`) status-toggle/delete actions still show their generic error message on a 403, rather than the friendly permission-denied one — deliberately deferred, documented here rather than silently left out.
