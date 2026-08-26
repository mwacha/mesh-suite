# Perfis de Permissão Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the hardcoded frontend permission-profile matrix into a real, backend-managed `PermissionProfile` entity with a dedicated `/permissoes` screen (Perfis de Permissão + Usuários e Permissões tabs), matching wireframe `layout/wireframes/12 - Permissoes-v1.html`.

**Architecture:** New backend module `com.meshsuite.permissionprofile` (entity + child grant collection, service, controller — same shape as the existing `pricetable`/`paymentmethod` modules). `User` gains a new `permissionProfileId` FK *alongside* the existing `profile` enum (left untouched, default-only, no longer surfaced in the UI) to avoid touching the 24 test files across unrelated modules that construct a `User` with `.setProfile(Profile.X)` purely for test-caller setup. Frontend gets three new views (`PermissionsView` tab wrapper, `PermissionProfilesListView`, `PermissionProfileFormView`) and `UserFormView.vue` is rewritten to fetch profiles from the API instead of the local `DEFAULT_MATRIX`.

**Tech Stack:** Spring Boot 3.4 / Java 21 / PostgreSQL 16 (backend), Vue 3 / TypeScript / Vitest (frontend) — same stack as the rest of the repo.

**Spec:** `docs/superpowers/specs/2026-08-26-perfis-permissao-design.md`

## Global Constraints

- No `Action.CANCEL` — stays out of scope, per spec §1.
- `User.profile` (enum) is never removed or made `@NotNull`-violating; it just stops being read/written by the new UI. Nothing in the 24 unrelated test files (`sale`, `purchaseorder`, `salesorder`, `purchaseinvoice`, `category`, `product`, `colorway`, `partner`, `pricetable`, `municipality`, `stock`, `payable`, `paymentmethod` packages) may need to change.
- `PermissionProfile` grants reuse the existing `@Embeddable` class `com.meshsuite.user.domain.UserPermissionGrant` (module+action) — do not create a duplicate class.
- Permission gate for the new service/controller reuses `Module.USER` (no new `Module` enum value), same pattern as Category/Colorway reusing `Module.PRODUCT`.
- `PermissionProfilesListView`/`PermissionProfileFormView.vue` follow the existing app visual/CSS conventions (`.card`, `.grid-2`, `.field-label`, `.field-error`, `.error-geral`, `.actions`, `.btn-primary`/`.btn-secondary`, `.tabela`) — copy from `PriceTablesListView.vue`/`PriceTableFormView.vue`, do not invent new class names.
- TDD throughout: write the failing test, run it, confirm it fails for the right reason, implement, run again, confirm green. Use `./mvnw -Dtest=<ClassName> test` (no `-q`, so `BUILD SUCCESS`/`Tests run:` is visible) for backend, `npx vitest run <path>` for frontend.

---

### Task 1: PermissionProfile — migration + domain + repository

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V32__create_permission_profile.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/domain/PermissionProfile.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/repository/PermissionProfileRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/permissionprofile/repository/PermissionProfileRepositoryTest.java`

**Interfaces:**
- Produces: `PermissionProfile` (getters/setters: `id`, `tenantId`, `name`, `description`, `isSystem`/`setSystem`/`getSystem` — Lombok `@Getter`/`@Setter` on a `Boolean isSystem` field generates `getIsSystem()`/`setIsSystem()`, keep that naming), `getCreatedAt()`, `getGrants()` returning `Set<UserPermissionGrant>`. `PermissionProfileRepository extends JpaRepository<PermissionProfile, UUID>, JpaSpecificationExecutor<PermissionProfile>` with `boolean existsByName(String)`, `boolean existsByNameAndIdNot(String, UUID)`, `long countByTenantId(UUID)` (used by Task 2 to decide whether to seed).

- [ ] **Step 1: Write the failing repository test**

```java
package com.meshsuite.permissionprofile.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.permissionprofile.domain.PermissionProfile;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.UserPermissionGrant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PermissionProfileRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PermissionProfileRepository permissionProfileRepository;
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

    private PermissionProfile newProfile(UUID tenantId, String name) {
        PermissionProfile p = new PermissionProfile();
        p.setTenantId(tenantId);
        p.setName(name);
        return p;
    }

    @Test
    @Transactional
    void savesPermissionProfileWithGrants() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        PermissionProfile profile = newProfile(tenant.getId(), "Gerente");
        profile.getGrants().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        profile.getGrants().add(new UserPermissionGrant(Module.CUSTOMER, Action.EDIT));

        PermissionProfile saved = permissionProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        PermissionProfile reloaded = permissionProfileRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Gerente");
        assertThat(reloaded.getIsSystem()).isFalse();
        assertThat(reloaded.getGrants()).containsExactlyInAnyOrder(
                new UserPermissionGrant(Module.CUSTOMER, Action.VIEW),
                new UserPermissionGrant(Module.CUSTOMER, Action.EDIT));
    }

    @Test
    @Transactional
    void nameMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        permissionProfileRepository.saveAndFlush(newProfile(tenant.getId(), "Gerente"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> permissionProfileRepository.saveAndFlush(newProfile(tenant.getId(), "Gerente")));
    }

    @Test
    @Transactional
    void sameNameAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        permissionProfileRepository.saveAndFlush(newProfile(tenantA.getId(), "Gerente"));

        setTenantContext(tenantB.getId());
        PermissionProfile saved = permissionProfileRepository.saveAndFlush(newProfile(tenantB.getId(), "Gerente"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesProfileAndGrantsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        PermissionProfile profile = newProfile(tenant.getId(), "Gerente");
        profile.getGrants().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        permissionProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long profileCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM permission_profile")
                .getSingleResult()).longValue();
        Long grantCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM permission_profile_grant")
                .getSingleResult()).longValue();

        assertThat(profileCount).isZero();
        assertThat(grantCount).isZero();
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `cd mesh-suite-backend && ./mvnw -Dtest=PermissionProfileRepositoryTest test`
Expected: `COMPILATION ERROR` — `package com.meshsuite.permissionprofile.domain does not exist` / `cannot find symbol PermissionProfileRepository`.

- [ ] **Step 3: Create the migration**

```sql
CREATE TABLE permission_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    is_system BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_permission_profile_tenant_name ON permission_profile(tenant_id, name);
CREATE INDEX idx_permission_profile_tenant_id ON permission_profile(tenant_id);

ALTER TABLE permission_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE permission_profile FORCE ROW LEVEL SECURITY;

CREATE POLICY permission_profile_tenant_isolation ON permission_profile
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE permission_profile_grant (
    permission_profile_id UUID NOT NULL REFERENCES permission_profile(id) ON DELETE CASCADE,
    module VARCHAR(20) NOT NULL
        CHECK (module IN ('CUSTOMER','PRODUCT','ORDER','USER','PURCHASE','STOCK','PAYABLE','SALE','PURCHASE_INVOICE')),
    action VARCHAR(10) NOT NULL CHECK (action IN ('VIEW','CREATE','EDIT','DELETE')),
    PRIMARY KEY (permission_profile_id, module, action)
);

ALTER TABLE permission_profile_grant ENABLE ROW LEVEL SECURITY;
ALTER TABLE permission_profile_grant FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- permission_profile row's own RLS policy, matched by permission_profile_id.
-- Same pattern as user_permission/partner_role.
CREATE POLICY permission_profile_grant_tenant_isolation ON permission_profile_grant
    USING (EXISTS (
        SELECT 1 FROM permission_profile pp
        WHERE pp.id = permission_profile_grant.permission_profile_id
          AND pp.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
```

- [ ] **Step 4: Create the domain entity**

```java
package com.meshsuite.permissionprofile.domain;

import com.meshsuite.user.domain.UserPermissionGrant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "permission_profile")
@Getter
@Setter
public class PermissionProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "permission_profile_grant", joinColumns = @JoinColumn(name = "permission_profile_id"))
    private Set<UserPermissionGrant> grants = new HashSet<>();
}
```

- [ ] **Step 5: Create the repository**

```java
package com.meshsuite.permissionprofile.repository;

import com.meshsuite.permissionprofile.domain.PermissionProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PermissionProfileRepository
        extends JpaRepository<PermissionProfile, UUID>, JpaSpecificationExecutor<PermissionProfile> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
    long countByTenantId(UUID tenantId);
}
```

- [ ] **Step 6: Run the test to confirm it passes**

Run: `cd mesh-suite-backend && ./mvnw -Dtest=PermissionProfileRepositoryTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V32__create_permission_profile.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/domain/PermissionProfile.java \
        mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/repository/PermissionProfileRepository.java \
        mesh-suite-backend/src/test/java/com/meshsuite/permissionprofile/repository/PermissionProfileRepositoryTest.java
git commit -m "feat(permissions): add PermissionProfile entity, migration, repository"
```

---

### Task 2: PermissionProfile — DTOs + service (seed, CRUD, validation)

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/dto/PermissionProfileRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/dto/PermissionProfileResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/dto/PermissionProfileSummaryResponse.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/exception/PermissionProfileNotFoundException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/exception/DuplicatePermissionProfileNameException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/exception/PermissionProfileValidationException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/service/PermissionProfileService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/permissionprofile/service/PermissionProfileServiceTest.java`

**Interfaces:**
- Consumes: `PermissionProfileRepository` (Task 1), `com.meshsuite.user.dto.PermissionDto(Module module, Action action)` (existing — reused as-is for grants in/out), `com.meshsuite.user.repository.UserRepository` (needs a new derived method `countByPermissionProfileId(UUID)` — added in this task since the service depends on it for delete-in-use validation), `com.meshsuite.shared.context.TenantContext.get()` (existing static accessor).
- Produces: `PermissionProfileService` with `list(String search, Pageable pageable): Page<PermissionProfileSummaryResponse>`, `findById(UUID id): PermissionProfileResponse`, `create(UUID tenantId, PermissionProfileRequest request): PermissionProfileResponse`, `update(UUID id, PermissionProfileRequest request): PermissionProfileResponse`, `delete(UUID id): void`. `PermissionProfileRequest(String name, String description, List<PermissionDto> grants)`. `PermissionProfileResponse(UUID id, String name, String description, Boolean isSystem, Instant createdAt, List<PermissionDto> grants)`. `PermissionProfileSummaryResponse(UUID id, String name, String description, Boolean isSystem, Integer moduleCount, Long userCount)`.

This task's `delete()` implements only the `isSystem` check. `UserRepository.countByPermissionProfileId` and the "profile in use by a user" delete check don't exist yet — `User` has no link to `PermissionProfile` until Task 4, which adds that method and upgrades `delete()`/`toSummary()` accordingly (Task 4 Steps 8–9). This keeps every commit in this task buildable and green on its own.

- [ ] **Step 1: Write the failing service test**

```java
package com.meshsuite.permissionprofile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.exception.PermissionDeniedException;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.permissionprofile.exception.DuplicatePermissionProfileNameException;
import com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException;
import com.meshsuite.permissionprofile.exception.PermissionProfileValidationException;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.dto.PermissionDto;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
class PermissionProfileServiceTest extends AbstractIntegrationTest {

    @Autowired PermissionProfileService permissionProfileService;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
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
        caller.setName("Marina");
        caller.setEmail(codigo + "@aurora.com.br");
        caller.setPasswordHash("hash");
        caller.setRole(Role.ADMINISTRATIVE);
        caller.setProfile(Profile.ADMIN);
        caller.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.USER, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.USER, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.USER, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private com.meshsuite.permissionprofile.dto.PermissionProfileRequest request(String name, List<PermissionDto> grants) {
        return new com.meshsuite.permissionprofile.dto.PermissionProfileRequest(name, "Descrição de teste", grants);
    }

    @Test
    void createsAndRetrievesProfileWithGrants() {
        setUpTenant("aurora");

        var criado = permissionProfileService.create(TenantContext.get(),
                request("Financeiro", List.of(new PermissionDto(Module.PAYABLE, Action.VIEW))));

        var buscado = permissionProfileService.findById(criado.id());
        assertThat(buscado.name()).isEqualTo("Financeiro");
        assertThat(buscado.isSystem()).isFalse();
        assertThat(buscado.grants()).containsExactly(new PermissionDto(Module.PAYABLE, Action.VIEW));
    }

    @Test
    void listSeedsTheFourDefaultProfilesOnFirstCall() {
        setUpTenant("aurora");

        var pagina = permissionProfileService.list(null, PageRequest.of(0, 10));

        assertThat(pagina.getContent()).extracting("name")
                .containsExactlyInAnyOrder("Admin", "Gerente", "Vendedor", "Visualizador");
        assertThat(pagina.getContent()).allMatch(com.meshsuite.permissionprofile.dto.PermissionProfileSummaryResponse::isSystem);
    }

    @Test
    void listDoesNotReseedOnSecondCall() {
        setUpTenant("aurora");
        permissionProfileService.list(null, PageRequest.of(0, 10));

        var segunda = permissionProfileService.list(null, PageRequest.of(0, 10));

        assertThat(segunda.getTotalElements()).isEqualTo(4);
    }

    @Test
    void rejectsDuplicateNameOnCreate() {
        setUpTenant("aurora");
        permissionProfileService.create(TenantContext.get(), request("Financeiro", List.of()));

        assertThatThrownBy(() -> permissionProfileService.create(TenantContext.get(), request("Financeiro", List.of())))
                .isInstanceOf(DuplicatePermissionProfileNameException.class);
    }

    @Test
    void updateReplacesTheWholeGrantList() {
        setUpTenant("aurora");
        var criado = permissionProfileService.create(TenantContext.get(),
                request("Financeiro", List.of(new PermissionDto(Module.PAYABLE, Action.VIEW))));

        var atualizado = permissionProfileService.update(criado.id(),
                request("Financeiro", List.of(new PermissionDto(Module.PAYABLE, Action.EDIT), new PermissionDto(Module.SALE, Action.VIEW))));

        assertThat(atualizado.grants()).containsExactlyInAnyOrder(
                new PermissionDto(Module.PAYABLE, Action.EDIT), new PermissionDto(Module.SALE, Action.VIEW));
    }

    @Test
    void allowsEditingASystemProfilesGrants() {
        setUpTenant("aurora");
        permissionProfileService.list(null, PageRequest.of(0, 10));
        var admin = permissionProfileService.list(null, PageRequest.of(0, 10)).getContent().stream()
                .filter(p -> p.name().equals("Admin")).findFirst().orElseThrow();

        var atualizado = permissionProfileService.update(admin.id(),
                request("Admin", List.of(new PermissionDto(Module.CUSTOMER, Action.VIEW))));

        assertThat(atualizado.grants()).containsExactly(new PermissionDto(Module.CUSTOMER, Action.VIEW));
    }

    @Test
    void rejectsDeletingASystemProfile() {
        setUpTenant("aurora");
        var admin = permissionProfileService.list(null, PageRequest.of(0, 10)).getContent().stream()
                .filter(p -> p.name().equals("Admin")).findFirst().orElseThrow();

        assertThatThrownBy(() -> permissionProfileService.delete(admin.id()))
                .isInstanceOf(PermissionProfileValidationException.class);
    }

    @Test
    void deletesACustomProfile() {
        setUpTenant("aurora");
        var criado = permissionProfileService.create(TenantContext.get(), request("Financeiro", List.of()));

        permissionProfileService.delete(criado.id());

        assertThatThrownBy(() -> permissionProfileService.findById(criado.id()))
                .isInstanceOf(PermissionProfileNotFoundException.class);
    }

    @Test
    void deniesCreateWhenCallerLacksUserCreatePermission() {
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

        assertThatThrownBy(() -> permissionProfileService.create(TenantContext.get(), request("Financeiro", List.of())))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `cd mesh-suite-backend && ./mvnw -Dtest=PermissionProfileServiceTest test`
Expected: `COMPILATION ERROR` — DTOs, exceptions, and `PermissionProfileService` don't exist yet.

- [ ] **Step 3: Create the DTOs**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/dto/PermissionProfileRequest.java
package com.meshsuite.permissionprofile.dto;

import com.meshsuite.user.dto.PermissionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PermissionProfileRequest(
        @NotBlank String name,
        String description,
        @NotNull List<@Valid PermissionDto> grants) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/dto/PermissionProfileResponse.java
package com.meshsuite.permissionprofile.dto;

import com.meshsuite.user.dto.PermissionDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PermissionProfileResponse(
        UUID id,
        String name,
        String description,
        Boolean isSystem,
        Instant createdAt,
        List<PermissionDto> grants) {
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/dto/PermissionProfileSummaryResponse.java
package com.meshsuite.permissionprofile.dto;

import java.util.UUID;

public record PermissionProfileSummaryResponse(
        UUID id,
        String name,
        String description,
        Boolean isSystem,
        Integer moduleCount,
        Long userCount) {
}
```

- [ ] **Step 4: Create the exceptions**

```java
// mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/exception/PermissionProfileNotFoundException.java
package com.meshsuite.permissionprofile.exception;

public class PermissionProfileNotFoundException extends RuntimeException {
    public PermissionProfileNotFoundException() {
        super("Perfil de permissão não encontrado");
    }
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/exception/DuplicatePermissionProfileNameException.java
package com.meshsuite.permissionprofile.exception;

public class DuplicatePermissionProfileNameException extends RuntimeException {
    public DuplicatePermissionProfileNameException() {
        super("Já existe um perfil de permissão cadastrado com este nome");
    }
}
```

```java
// mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/exception/PermissionProfileValidationException.java
package com.meshsuite.permissionprofile.exception;

public class PermissionProfileValidationException extends RuntimeException {
    public PermissionProfileValidationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Create the service**

```java
package com.meshsuite.permissionprofile.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.permissionprofile.domain.PermissionProfile;
import com.meshsuite.permissionprofile.dto.PermissionProfileRequest;
import com.meshsuite.permissionprofile.dto.PermissionProfileResponse;
import com.meshsuite.permissionprofile.dto.PermissionProfileSummaryResponse;
import com.meshsuite.permissionprofile.exception.DuplicatePermissionProfileNameException;
import com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException;
import com.meshsuite.permissionprofile.exception.PermissionProfileValidationException;
import com.meshsuite.permissionprofile.repository.PermissionProfileRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.dto.PermissionDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionProfileService {

    private final PermissionProfileRepository permissionProfileRepository;

    public PermissionProfileService(PermissionProfileRepository permissionProfileRepository) {
        this.permissionProfileRepository = permissionProfileRepository;
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public Page<PermissionProfileSummaryResponse> list(String search, Pageable pageable) {
        ensureDefaultsSeeded();
        Specification<PermissionProfile> spec = search == null || search.isBlank()
                ? null
                : (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
        return permissionProfileRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public PermissionProfileResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.CREATE)
    public PermissionProfileResponse create(UUID tenantId, PermissionProfileRequest request) {
        validateName(request.name(), null);

        PermissionProfile profile = new PermissionProfile();
        profile.setTenantId(tenantId);
        apply(profile, request);
        return toResponse(permissionProfileRepository.saveAndFlush(profile));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.EDIT)
    public PermissionProfileResponse update(UUID id, PermissionProfileRequest request) {
        validateName(request.name(), id);

        PermissionProfile profile = findEntityById(id);
        apply(profile, request);
        return toResponse(permissionProfileRepository.saveAndFlush(profile));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.DELETE)
    public void delete(UUID id) {
        PermissionProfile profile = findEntityById(id);
        if (profile.getIsSystem()) {
            throw new PermissionProfileValidationException(
                    "Não é possível excluir um perfil padrão do sistema");
        }
        permissionProfileRepository.delete(profile);
    }

    private PermissionProfile findEntityById(UUID id) {
        return permissionProfileRepository.findById(id).orElseThrow(PermissionProfileNotFoundException::new);
    }

    private void validateName(String name, UUID currentId) {
        boolean duplicate = currentId == null
                ? permissionProfileRepository.existsByName(name)
                : permissionProfileRepository.existsByNameAndIdNot(name, currentId);
        if (duplicate) {
            throw new DuplicatePermissionProfileNameException();
        }
    }

    private void apply(PermissionProfile profile, PermissionProfileRequest request) {
        profile.setName(request.name());
        profile.setDescription(request.description());
        profile.getGrants().clear();
        for (PermissionDto dto : request.grants()) {
            profile.getGrants().add(new UserPermissionGrant(dto.module(), dto.action()));
        }
    }

    // Seeds the 4 system defaults the first time a tenant lists its profiles --
    // there is no tenant-registration flow yet to hook this into (see spec §8.1).
    // The DB's UNIQUE(tenant_id, name) makes a concurrent double-seed harmless:
    // the loser's insert throws DataIntegrityViolationException, surfaced as a
    // 409 by the same handler duplicate names already get.
    private void ensureDefaultsSeeded() {
        UUID tenantId = TenantContext.get();
        if (permissionProfileRepository.countByTenantId(tenantId) > 0) {
            return;
        }
        for (Map.Entry<String, List<UserPermissionGrant>> entry : defaultMatrix().entrySet()) {
            PermissionProfile profile = new PermissionProfile();
            profile.setTenantId(tenantId);
            profile.setName(entry.getKey());
            profile.setIsSystem(true);
            profile.getGrants().addAll(entry.getValue());
            permissionProfileRepository.saveAndFlush(profile);
        }
    }

    // Business-judgment default matrix, ported from the old frontend-only
    // DEFAULT_MATRIX (UserFormView.vue), extended with STOCK -- see spec §3.
    private static Map<String, List<UserPermissionGrant>> defaultMatrix() {
        return Map.of(
                "Admin", List.of(
                        g(Module.CUSTOMER, Action.VIEW), g(Module.CUSTOMER, Action.CREATE), g(Module.CUSTOMER, Action.EDIT), g(Module.CUSTOMER, Action.DELETE),
                        g(Module.PRODUCT, Action.VIEW), g(Module.PRODUCT, Action.CREATE), g(Module.PRODUCT, Action.EDIT), g(Module.PRODUCT, Action.DELETE),
                        g(Module.ORDER, Action.VIEW), g(Module.ORDER, Action.CREATE), g(Module.ORDER, Action.EDIT), g(Module.ORDER, Action.DELETE),
                        g(Module.USER, Action.VIEW), g(Module.USER, Action.CREATE), g(Module.USER, Action.EDIT),
                        g(Module.PURCHASE, Action.VIEW), g(Module.PURCHASE, Action.CREATE), g(Module.PURCHASE, Action.EDIT), g(Module.PURCHASE, Action.DELETE),
                        g(Module.STOCK, Action.VIEW), g(Module.STOCK, Action.CREATE), g(Module.STOCK, Action.EDIT), g(Module.STOCK, Action.DELETE),
                        g(Module.PAYABLE, Action.VIEW), g(Module.PAYABLE, Action.EDIT),
                        g(Module.SALE, Action.VIEW), g(Module.SALE, Action.CREATE),
                        g(Module.PURCHASE_INVOICE, Action.VIEW), g(Module.PURCHASE_INVOICE, Action.CREATE)),
                "Gerente", List.of(
                        g(Module.CUSTOMER, Action.VIEW), g(Module.CUSTOMER, Action.CREATE), g(Module.CUSTOMER, Action.EDIT),
                        g(Module.PRODUCT, Action.VIEW), g(Module.PRODUCT, Action.CREATE), g(Module.PRODUCT, Action.EDIT),
                        g(Module.ORDER, Action.VIEW), g(Module.ORDER, Action.CREATE), g(Module.ORDER, Action.EDIT),
                        g(Module.PURCHASE, Action.VIEW), g(Module.PURCHASE, Action.CREATE), g(Module.PURCHASE, Action.EDIT),
                        g(Module.STOCK, Action.VIEW),
                        g(Module.PAYABLE, Action.VIEW), g(Module.PAYABLE, Action.EDIT),
                        g(Module.SALE, Action.VIEW), g(Module.SALE, Action.CREATE),
                        g(Module.PURCHASE_INVOICE, Action.VIEW), g(Module.PURCHASE_INVOICE, Action.CREATE),
                        g(Module.USER, Action.VIEW)),
                "Vendedor", List.of(
                        g(Module.CUSTOMER, Action.VIEW), g(Module.CUSTOMER, Action.CREATE), g(Module.CUSTOMER, Action.EDIT),
                        g(Module.PRODUCT, Action.VIEW),
                        g(Module.ORDER, Action.VIEW), g(Module.ORDER, Action.CREATE), g(Module.ORDER, Action.EDIT),
                        g(Module.SALE, Action.VIEW), g(Module.SALE, Action.CREATE)),
                "Visualizador", List.of(
                        g(Module.CUSTOMER, Action.VIEW),
                        g(Module.PRODUCT, Action.VIEW),
                        g(Module.ORDER, Action.VIEW),
                        g(Module.PURCHASE, Action.VIEW),
                        g(Module.STOCK, Action.VIEW),
                        g(Module.PAYABLE, Action.VIEW),
                        g(Module.SALE, Action.VIEW),
                        g(Module.PURCHASE_INVOICE, Action.VIEW)));
    }

    private static UserPermissionGrant g(Module module, Action action) {
        return new UserPermissionGrant(module, action);
    }

    private PermissionProfileSummaryResponse toSummary(PermissionProfile p) {
        long moduleCount = p.getGrants().stream().map(UserPermissionGrant::getModule).distinct().count();
        return new PermissionProfileSummaryResponse(p.getId(), p.getName(), p.getDescription(), p.getIsSystem(),
                (int) moduleCount, 0L);
    }

    private PermissionProfileResponse toResponse(PermissionProfile p) {
        List<PermissionDto> grants = p.getGrants().stream()
                .map(g -> new PermissionDto(g.getModule(), g.getAction()))
                .toList();
        return new PermissionProfileResponse(p.getId(), p.getName(), p.getDescription(), p.getIsSystem(),
                p.getCreatedAt(), grants);
    }
}
```

Note: `toSummary`'s `userCount` is hardcoded to `0L` in this task — Task 4 replaces it with a real `userRepository.countByPermissionProfileId(p.getId())` call once that repository method and the `User` FK exist. Leaving it at `0L` here keeps this task's test suite (which never asserts on `userCount`) green without a forward reference to Task 4.

- [ ] **Step 6: Run the test to confirm it passes**

Run: `cd mesh-suite-backend && ./mvnw -Dtest=PermissionProfileServiceTest test`
Expected: `Tests run: 9, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/dto/ \
        mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/exception/ \
        mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/service/PermissionProfileService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/permissionprofile/service/PermissionProfileServiceTest.java
git commit -m "feat(permissions): add PermissionProfile service with default-profile seeding"
```

---

### Task 3: PermissionProfile — controller + exception wiring

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/controller/PermissionProfileController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/exception/PermissionProfileExceptionHandler.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/permissionprofile/controller/PermissionProfileControllerTest.java`

**Interfaces:**
- Consumes: `PermissionProfileService` (Task 2).
- Produces: REST endpoints under `/api/permission-profiles` — `GET` (list, param `busca`), `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`.

- [ ] **Step 1: Write the failing controller test**

```java
package com.meshsuite.permissionprofile.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PermissionProfileControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String loginAndGetCookie(String codigo, String email, String companyCnpj) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName(codigo + " Ltda");
        company.setCnpj(companyCnpj);
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.ADMIN);
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.CREATE));
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.EDIT));
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.DELETE));
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String profilePayload(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Perfil de teste",
                  "grants": [{"module": "CUSTOMER", "action": "VIEW"}]
                }
                """.formatted(name);
    }

    @Test
    void createsListsUpdatesAndDeletesProfile() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/permission-profiles").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload("Financeiro")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Financeiro"))
                .andExpect(jsonPath("$.isSystem").value(false))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/permission-profiles").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5)); // 4 seeded defaults + Financeiro

        mockMvc.perform(put("/api/permission-profiles/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Financeiro Atualizado",
                                  "description": "",
                                  "grants": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Financeiro Atualizado"));

        mockMvc.perform(delete("/api/permission-profiles/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/permission-profiles/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDeletingASystemProfileWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String listBody = mockMvc.perform(get("/api/permission-profiles").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String adminId = com.jayway.jsonpath.JsonPath.read(listBody, "$.content[?(@.name=='Admin')].id[0]");

        mockMvc.perform(delete("/api/permission-profiles/" + adminId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateNameWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/permission-profiles").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload("Financeiro")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/permission-profiles").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload("Financeiro")))
                .andExpect(status().isConflict());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/permission-profiles"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd mesh-suite-backend && ./mvnw -Dtest=PermissionProfileControllerTest test`
Expected: 404s (no controller mapped yet) or compile error if `PermissionProfileController` doesn't exist — confirm the failure is "route not found", not a typo.

- [ ] **Step 3: Create the controller**

```java
package com.meshsuite.permissionprofile.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.permissionprofile.dto.PermissionProfileRequest;
import com.meshsuite.permissionprofile.dto.PermissionProfileResponse;
import com.meshsuite.permissionprofile.dto.PermissionProfileSummaryResponse;
import com.meshsuite.permissionprofile.service.PermissionProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/permission-profiles")
public class PermissionProfileController {

    private final PermissionProfileService permissionProfileService;

    public PermissionProfileController(PermissionProfileService permissionProfileService) {
        this.permissionProfileService = permissionProfileService;
    }

    @GetMapping
    public Page<PermissionProfileSummaryResponse> list(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return permissionProfileService.list(busca, pageable);
    }

    @GetMapping("/{id}")
    public PermissionProfileResponse findById(@PathVariable UUID id) {
        return permissionProfileService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PermissionProfileResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                              @Valid @RequestBody PermissionProfileRequest request) {
        PermissionProfileResponse response = permissionProfileService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PermissionProfileResponse update(@PathVariable UUID id, @Valid @RequestBody PermissionProfileRequest request) {
        return permissionProfileService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        permissionProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Create the per-controller exception handler**

```java
package com.meshsuite.permissionprofile.exception;

import com.meshsuite.permissionprofile.controller.PermissionProfileController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PermissionProfileController.class)
public class PermissionProfileExceptionHandler {

    // Fallback for a race condition slipping past PermissionProfileService's
    // pre-check (two concurrent requests for the same new name, or the
    // default-seed race documented in PermissionProfileService) -- the DB's
    // UNIQUE(tenant_id, name) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um perfil de permissão cadastrado com este nome"));
    }
}
```

- [ ] **Step 5: Register the domain exceptions in `GlobalExceptionHandler`**

Modify `mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java` — add these three handlers right before the closing `}` of the class (after the `PurchaseInvoiceValidationException` handler added by the payment-methods work):

```java
    @ExceptionHandler(com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePermissionProfileNotFound(
            com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.permissionprofile.exception.DuplicatePermissionProfileNameException.class)
    public ResponseEntity<Map<String, String>> handleDuplicatePermissionProfileName(
            com.meshsuite.permissionprofile.exception.DuplicatePermissionProfileNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.permissionprofile.exception.PermissionProfileValidationException.class)
    public ResponseEntity<Map<String, String>> handlePermissionProfileValidation(
            com.meshsuite.permissionprofile.exception.PermissionProfileValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
```

- [ ] **Step 6: Run the test to confirm it passes**

Run: `cd mesh-suite-backend && ./mvnw -Dtest=PermissionProfileControllerTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/controller/ \
        mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/exception/PermissionProfileExceptionHandler.java \
        mesh-suite-backend/src/main/java/com/meshsuite/shared/handler/GlobalExceptionHandler.java \
        mesh-suite-backend/src/test/java/com/meshsuite/permissionprofile/controller/PermissionProfileControllerTest.java
git commit -m "feat(permissions): add PermissionProfile controller and error handling"
```

---

### Task 4: Link `User` to `PermissionProfile`

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V33__add_permission_profile_to_user.sql`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/domain/User.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserRequest.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserResponse.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserListItemResponse.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/service/UserService.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/user/repository/UserRepository.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/service/PermissionProfileService.java` (real `userCount`, delete-in-use check)
- Modify: `mesh-suite-backend/src/test/java/com/meshsuite/user/service/UserServiceTest.java` (one new field on the existing `request()` helper — append `null`)
- Test (new cases): `mesh-suite-backend/src/test/java/com/meshsuite/user/service/UserServiceTest.java`, `mesh-suite-backend/src/test/java/com/meshsuite/permissionprofile/service/PermissionProfileServiceTest.java`

**Interfaces:**
- Consumes: `PermissionProfileRepository` (Task 1).
- Produces: `User.getPermissionProfile(): PermissionProfile` / `setPermissionProfile(PermissionProfile)`. `UserRequest` gains `UUID permissionProfileId` as its **last** field (append, don't insert in the middle — the record has exactly one positional-constructor call site to touch, see Step 6). `UserResponse`/`UserListItemResponse` gain `permissionProfileId`/`permissionProfileName`.

- [ ] **Step 1: Write the new failing test cases in `UserServiceTest`**

Add these two tests at the end of the `UserServiceTest` class (before the final closing `}`):

```java
    @Test
    void linksPermissionProfileToUser() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        com.meshsuite.permissionprofile.domain.PermissionProfile perfil =
                new com.meshsuite.permissionprofile.domain.PermissionProfile();
        perfil.setTenantId(tenantId);
        perfil.setName("Financeiro");
        permissionProfileRepository.saveAndFlush(perfil);

        var criado = userService.create(tenantId,
                requestWithProfile("marina@aurora.com.br", "senha1234", "senha1234", List.of(), perfil.getId()));

        assertThat(criado.permissionProfileId()).isEqualTo(perfil.getId());
        assertThat(criado.permissionProfileName()).isEqualTo("Financeiro");
    }

    @Test
    void createsUserWithoutPermissionProfile() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        assertThat(criado.permissionProfileId()).isNull();
        assertThat(criado.permissionProfileName()).isNull();
    }
```

Add the autowired repository and the new request helper near the top of the class (right after the existing `request(...)` helper):

```java
    @Autowired com.meshsuite.permissionprofile.repository.PermissionProfileRepository permissionProfileRepository;

    private UserRequest requestWithProfile(String email, String password, String confirmPassword,
                                            List<PermissionDto> permissions, UUID permissionProfileId) {
        return new UserRequest("Marina", email, "(11) 99999-9999", Role.ADMINISTRATIVE, Profile.ADMIN, true,
                password, confirmPassword, permissions, permissionProfileId);
    }
```

And update the existing `request(...)` helper (the one already in the file) to append `null`:

```java
    private UserRequest request(String email, String password, String confirmPassword, List<PermissionDto> permissions) {
        return new UserRequest("Marina", email, "(11) 99999-9999", Role.ADMINISTRATIVE, Profile.ADMIN, true,
                password, confirmPassword, permissions, null);
    }
```

- [ ] **Step 2: Add the new test case to `PermissionProfileServiceTest`**

Add `@Autowired com.meshsuite.permissionprofile.repository.PermissionProfileRepository permissionProfileRepository;` to the test class's field list (next to the existing `@Autowired` fields), then add this test:

```java
    @Test
    void rejectsDeletingAProfileThatIsInUseByAUser() {
        UUID tenantId = setUpTenant("aurora");
        var criado = permissionProfileService.create(TenantContext.get(), request("Financeiro", List.of()));
        var perfilEntity = permissionProfileRepository.findById(criado.id()).orElseThrow();

        User linked = new User();
        linked.setTenantId(tenantId);
        linked.setName("Usuário Vinculado");
        linked.setEmail("vinculado@aurora.com.br");
        linked.setPasswordHash("hash");
        linked.setRole(Role.ADMINISTRATIVE);
        linked.setPermissionProfile(perfilEntity);
        userRepository.saveAndFlush(linked);

        assertThatThrownBy(() -> permissionProfileService.delete(criado.id()))
                .isInstanceOf(PermissionProfileValidationException.class);
    }
```

- [ ] **Step 3: Run both test files to confirm they fail to compile**

Run: `cd mesh-suite-backend && ./mvnw -Dtest=UserServiceTest,PermissionProfileServiceTest test`
Expected: `COMPILATION ERROR` — `User` has no `setPermissionProfile`, `UserRequest`/`UserResponse` constructors have the wrong arity.

- [ ] **Step 4: Create the migration**

```sql
ALTER TABLE app_user ADD COLUMN permission_profile_id UUID REFERENCES permission_profile(id);
```

- [ ] **Step 5: Add the field to `User`**

Modify `mesh-suite-backend/src/main/java/com/meshsuite/user/domain/User.java` — add the import and field:

```java
import com.meshsuite.permissionprofile.domain.PermissionProfile;
```

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_profile_id")
    private PermissionProfile permissionProfile;
```

(Insert this field right after the existing `profile` field, before `permissions`.)

- [ ] **Step 6: Update `UserRequest`/`UserResponse`/`UserListItemResponse`**

`UserRequest.java` — remove `@NotNull` from `profile`, add `permissionProfileId` as the last parameter:

```java
package com.meshsuite.user.dto;

import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        @NotNull Role role,
        Profile profile,
        boolean active,
        String password,
        String confirmPassword,
        List<@Valid PermissionDto> permissions,
        UUID permissionProfileId) {
}
```

`UserResponse.java` — append `permissionProfileId`/`permissionProfileName`:

```java
package com.meshsuite.user.dto;

import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;

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
        List<PermissionDto> permissions,
        UUID permissionProfileId,
        String permissionProfileName) {
}
```

`UserListItemResponse.java` — append the same two fields:

```java
package com.meshsuite.user.dto;

import com.meshsuite.user.domain.enums.Profile;

import java.util.UUID;

public record UserListItemResponse(
        UUID id,
        String name,
        String email,
        Profile profile,
        boolean active,
        UUID permissionProfileId,
        String permissionProfileName) {
}
```

- [ ] **Step 7: Update `UserService`**

Modify `mesh-suite-backend/src/main/java/com/meshsuite/user/service/UserService.java`:

Add the import and a new constructor dependency:

```java
import com.meshsuite.permissionprofile.domain.PermissionProfile;
import com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException;
import com.meshsuite.permissionprofile.repository.PermissionProfileRepository;
```

```java
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionProfileRepository permissionProfileRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        PermissionProfileRepository permissionProfileRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionProfileRepository = permissionProfileRepository;
    }
```

Change `applyRequest` — only overwrite `profile` when provided, and resolve `permissionProfile`:

```java
    private void applyRequest(User user, UserRequest request) {
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        if (request.profile() != null) {
            user.setProfile(request.profile());
        }
        user.setActive(request.active());
        if (request.permissionProfileId() != null) {
            PermissionProfile permissionProfile = permissionProfileRepository.findById(request.permissionProfileId())
                    .orElseThrow(PermissionProfileNotFoundException::new);
            user.setPermissionProfile(permissionProfile);
        } else {
            user.setPermissionProfile(null);
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        user.getPermissions().clear();
        List<PermissionDto> permissions = request.permissions() == null ? List.of() : request.permissions();
        for (PermissionDto dto : permissions) {
            user.getPermissions().add(new UserPermissionGrant(dto.module(), dto.action()));
        }
    }
```

Change `toListItem`/`toResponse`:

```java
    private UserListItemResponse toListItem(User u) {
        PermissionProfile pp = u.getPermissionProfile();
        return new UserListItemResponse(u.getId(), u.getName(), u.getEmail(), u.getProfile(), u.isActive(),
                pp == null ? null : pp.getId(), pp == null ? null : pp.getName());
    }

    private UserResponse toResponse(User u) {
        List<PermissionDto> permissions = u.getPermissions().stream()
                .map(p -> new PermissionDto(p.getModule(), p.getAction()))
                .toList();
        PermissionProfile pp = u.getPermissionProfile();
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getProfile(),
                u.isActive(), permissions, pp == null ? null : pp.getId(), pp == null ? null : pp.getName());
    }
```

- [ ] **Step 8: Add `countByPermissionProfileId` to `UserRepository`**

Modify `mesh-suite-backend/src/main/java/com/meshsuite/user/repository/UserRepository.java` — add after `countByActive`:

```java
    long countByPermissionProfileId(UUID permissionProfileId);
```

- [ ] **Step 9: Wire the real `userCount` and the in-use delete check into `PermissionProfileService`**

Modify `mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/service/PermissionProfileService.java`:

Add the dependency:

```java
import com.meshsuite.user.repository.UserRepository;
```

```java
    private final PermissionProfileRepository permissionProfileRepository;
    private final UserRepository userRepository;

    public PermissionProfileService(PermissionProfileRepository permissionProfileRepository, UserRepository userRepository) {
        this.permissionProfileRepository = permissionProfileRepository;
        this.userRepository = userRepository;
    }
```

Replace `delete`:

```java
    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.DELETE)
    public void delete(UUID id) {
        PermissionProfile profile = findEntityById(id);
        if (profile.getIsSystem()) {
            throw new PermissionProfileValidationException(
                    "Não é possível excluir um perfil padrão do sistema");
        }
        long linked = userRepository.countByPermissionProfileId(id);
        if (linked > 0) {
            throw new PermissionProfileValidationException(
                    "Não é possível excluir: " + linked + " usuário(s) usam este perfil");
        }
        permissionProfileRepository.delete(profile);
    }
```

Replace `toSummary`:

```java
    private PermissionProfileSummaryResponse toSummary(PermissionProfile p) {
        long moduleCount = p.getGrants().stream().map(UserPermissionGrant::getModule).distinct().count();
        long userCount = userRepository.countByPermissionProfileId(p.getId());
        return new PermissionProfileSummaryResponse(p.getId(), p.getName(), p.getDescription(), p.getIsSystem(),
                (int) moduleCount, userCount);
    }
```

- [ ] **Step 10: Run both test files to confirm they pass**

Run: `cd mesh-suite-backend && ./mvnw -Dtest=UserServiceTest,PermissionProfileServiceTest test`
Expected: both `BUILD SUCCESS`.

- [ ] **Step 11: Run the full backend suite to confirm no regressions in the 24 unrelated test files**

Run: `cd mesh-suite-backend && ./mvnw test 2>&1 | tail -60`
Expected: the only failures (if any) are the 15 pre-existing, unrelated `tenant_codigo_key`/`company_cnpj_key` flaky failures already documented as pre-existing on `main` (see the payment-methods work in this same session) — confirm by count, not by re-verifying each one individually. If the failure count or the specific failing classes differ from that known baseline, stop and investigate before continuing — that would mean this task introduced a real regression.

- [ ] **Step 12: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V33__add_permission_profile_to_user.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/user/domain/User.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserRequest.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/dto/UserListItemResponse.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/service/UserService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/user/repository/UserRepository.java \
        mesh-suite-backend/src/main/java/com/meshsuite/permissionprofile/service/PermissionProfileService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/user/service/UserServiceTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/permissionprofile/service/PermissionProfileServiceTest.java
git commit -m "feat(permissions): link User to PermissionProfile alongside the legacy Profile enum"
```

---

### Task 5: Frontend — `api/permissionProfiles.ts` + `PermissionProfilesListView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/api/permissionProfiles.ts`
- Create: `mesh-suite-frontend/src/views/PermissionProfilesListView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/PermissionProfilesListView.spec.ts`

**Interfaces:**
- Produces: `listPermissionProfiles(params: { busca?: string; page?: number; size?: number }): Promise<Page<PermissionProfileSummary>>`, `getPermissionProfile(id): Promise<PermissionProfileResponse>`, `createPermissionProfile`, `updatePermissionProfile`, `deletePermissionProfile`. Types: `PermissionGrant { module: ModuleName; action: ActionName }`, `PermissionProfileSummary { id, name, description, isSystem, moduleCount, userCount }`, `PermissionProfileResponse { id, name, description, isSystem, createdAt, grants: PermissionGrant[] }`, `PermissionProfileRequest { name, description, grants: PermissionGrant[] }`.

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PermissionProfilesListView from '@/views/PermissionProfilesListView.vue'
import * as perfisApi from '@/api/permissionProfiles'

vi.mock('@/api/permissionProfiles')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/permissoes', name: 'permissoes', component: PermissionProfilesListView },
      { path: '/permissoes/perfis/novo', name: 'permissoes-perfis-novo', component: { template: '<div />' } },
      { path: '/permissoes/perfis/:id/editar', name: 'permissoes-perfis-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/permissoes')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PermissionProfilesListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const perfilExemplo = {
  id: 'pp-1', name: 'Gerente', description: 'Gestão operacional', isSystem: true, moduleCount: 7, userCount: 5,
}

describe('PermissionProfilesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the profile list', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [perfilExemplo], totalElements: 1, totalPages: 1, number: 0, size: 20,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Gerente')
    expect(wrapper.text()).toContain('7 de 9 módulos')
    expect(wrapper.text()).toContain('5')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de perfis de permissão.')
  })

  it('navigates to the new-profile route when the button is clicked', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-perfil"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('permissoes-perfis-novo')
  })

  it('deletes a custom profile after confirmation and reloads the list', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [{ ...perfilExemplo, isSystem: false }], totalElements: 1, totalPages: 1, number: 0, size: 20,
    })
    vi.mocked(perfisApi.deletePermissionProfile).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(perfisApi.deletePermissionProfile).toHaveBeenCalledWith('pp-1')
  })

  it('shows the backend error message when deleting a system profile fails', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [perfilExemplo], totalElements: 1, totalPages: 1, number: 0, size: 20,
    })
    vi.mocked(perfisApi.deletePermissionProfile).mockRejectedValue({
      response: { data: { mensagem: 'Não é possível excluir um perfil padrão do sistema' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não é possível excluir um perfil padrão do sistema')
  })
})
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PermissionProfilesListView.spec.ts`
Expected: `Failed to resolve import "@/views/PermissionProfilesListView.vue"`.

- [ ] **Step 3: Create the API client**

```typescript
import { apiClient } from './client'

export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE' | 'STOCK' | 'PAYABLE' | 'SALE' | 'PURCHASE_INVOICE'
export type ActionName = 'VIEW' | 'CREATE' | 'EDIT' | 'DELETE'

export interface PermissionGrant {
  module: ModuleName
  action: ActionName
}

export interface PermissionProfileRequest {
  name: string
  description: string
  grants: PermissionGrant[]
}

export interface PermissionProfileResponse {
  id: string
  name: string
  description: string | null
  isSystem: boolean
  createdAt: string
  grants: PermissionGrant[]
}

export interface PermissionProfileSummary {
  id: string
  name: string
  description: string | null
  isSystem: boolean
  moduleCount: number
  userCount: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListPermissionProfilesParams {
  busca?: string
  page?: number
  size?: number
}

export async function listPermissionProfiles(params: ListPermissionProfilesParams): Promise<Page<PermissionProfileSummary>> {
  const { data } = await apiClient.get<Page<PermissionProfileSummary>>('/permission-profiles', { params })
  return data
}

export async function getPermissionProfile(id: string): Promise<PermissionProfileResponse> {
  const { data } = await apiClient.get<PermissionProfileResponse>(`/permission-profiles/${id}`)
  return data
}

export async function createPermissionProfile(payload: PermissionProfileRequest): Promise<PermissionProfileResponse> {
  const { data } = await apiClient.post<PermissionProfileResponse>('/permission-profiles', payload)
  return data
}

export async function updatePermissionProfile(id: string, payload: PermissionProfileRequest): Promise<PermissionProfileResponse> {
  const { data } = await apiClient.put<PermissionProfileResponse>(`/permission-profiles/${id}`, payload)
  return data
}

export async function deletePermissionProfile(id: string): Promise<void> {
  await apiClient.delete(`/permission-profiles/${id}`)
}
```

- [ ] **Step 4: Create the list view**

Base this file directly on `mesh-suite-frontend/src/views/PriceTablesListView.vue` (read it first) — same `.toolbar`/`.card`/`.tabela`/`.badge`/`.dropdown-acoes`/`.paginacao` CSS classes and Teleport-based actions dropdown pattern, but:
- No status filter (profiles don't have `active`/`inactive`).
- Columns: Nome, Descrição, Módulos (`{{ perfil.moduleCount }} de 9 módulos`), Usuários (`{{ perfil.userCount }}`), Ações.
- The Ações dropdown only shows "Excluir" when `!perfil.isSystem` (system profiles show "Editar" only — deletion is blocked server-side anyway, but hiding the option for system profiles avoids a pointless round-trip for the common case).
- `excluir()` catches the backend error and shows `err?.response?.data?.mensagem` (matches the "system profile" test case above), falling back to a generic message.

This view has no `<AppShell>` wrapper — unlike every other List view in the app, it's never mounted on its own route; it only ever renders inside `PermissionsView.vue`'s single `AppShell` (Task 7). Wrapping it in a second `AppShell` here would render the sidebar and topbar twice.

```vue
<template>
  <div class="perfis-permissao">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar perfil por nome..."
        data-test="busca"
        @input="carregar(0)"
      />
      <button type="button" class="btn-primary" data-test="novo-perfil" @click="novoPerfil">+ Novo Perfil</button>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Perfil</th>
            <th>Descrição</th>
            <th>Módulos</th>
            <th>Usuários</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="perfil in pagina.content" :key="perfil.id">
            <td>{{ perfil.name }}</td>
            <td>{{ perfil.description || '—' }}</td>
            <td>{{ perfil.moduleCount }} de 9 módulos</td>
            <td>{{ perfil.userCount }}</td>
            <td class="acoes">
              <button type="button" class="btn-acoes" data-test="btn-acoes" @click="toggleAcoes(perfil.id, $event)">
                Ações
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!pagina.content.length" class="empty-state">Nenhum perfil de permissão para exibir.</p>
    </section>

    <Teleport to="body">
      <div v-if="perfilAcoesAtual" class="dropdown-acoes" :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }">
        <div data-test="acao-editar" @click="editarPerfil(perfilAcoesAtual.id)">Editar</div>
        <div v-if="!perfilAcoesAtual.isSystem" data-test="acao-excluir" class="acao-excluir" @click="excluir(perfilAcoesAtual)">Excluir</div>
      </div>
    </Teleport>

    <div class="paginacao">
      <button type="button" :disabled="pagina.number === 0" @click="carregar(pagina.number - 1)">‹</button>
      <span>Página {{ pagina.number + 1 }} de {{ Math.max(pagina.totalPages, 1) }}</span>
      <button type="button" :disabled="pagina.number + 1 >= pagina.totalPages" @click="carregar(pagina.number + 1)">›</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  listPermissionProfiles,
  deletePermissionProfile,
  type PermissionProfileSummary,
  type Page as ApiPage,
} from '@/api/permissionProfiles'

const router = useRouter()

const filtros = reactive({ busca: '' })
const pagina = ref<ApiPage<PermissionProfileSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const perfilAcoesAtual = computed(() =>
  pagina.value.content.find((p) => p.id === acoesAbertas.value) ?? null,
)

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listPermissionProfiles({
      busca: filtros.busca || undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de perfis de permissão.'
  }
}

function novoPerfil() {
  router.push({ name: 'permissoes-perfis-novo' })
}

function editarPerfil(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'permissoes-perfis-editar', params: { id } })
}

function toggleAcoes(id: string, event: MouseEvent) {
  if (acoesAbertas.value === id) {
    acoesAbertas.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  posicaoDropdown.value = { top: `${rect.bottom + 4}px`, left: `${rect.right - 120}px` }
  acoesAbertas.value = id
}

async function excluir(perfil: PermissionProfileSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir o perfil "${perfil.name}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deletePermissionProfile(perfil.id)
    await carregar(pagina.value.number)
  } catch (err: any) {
    erro.value = err?.response?.data?.mensagem ?? 'Não foi possível excluir o perfil de permissão.'
  }
}

onMounted(() => {
  carregar(0)
})
</script>

<style scoped>
.error-geral { color: var(--pm-error); font-size: 14px; margin: 0 0 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; font-family: var(--pm-font); }
.busca { flex: 1; }
.toolbar input {
  border: 1px solid var(--pm-border-light); border-radius: 8px; padding: 8px 10px;
  font-size: 13px; font-family: var(--pm-font); color: var(--pm-text-dark); background: var(--pm-white);
}
.btn-primary {
  background: var(--pm-accent); color: var(--pm-white); border: none; border-radius: 8px;
  padding: 8px 16px; font-size: 13px; font-weight: 600; cursor: pointer; white-space: nowrap;
}
.card { background: var(--pm-white); border: 1px solid var(--pm-border-light); border-radius: 12px; overflow: hidden; margin-bottom: 12px; }
.tabela { width: 100%; border-collapse: collapse; font-size: 13px; font-family: var(--pm-font); }
.tabela th {
  text-align: left; font-size: 11px; font-weight: 600; text-transform: uppercase;
  color: var(--pm-text-mid); background: var(--pm-bg); padding: 8px 12px;
}
.tabela td { padding: 8px 12px; border-top: 1px solid var(--pm-border-light); color: var(--pm-text-dark); }
.empty-state { padding: 16px; color: var(--pm-text-mid); font-size: 13px; margin: 0; }
.btn-acoes { border: 1px solid var(--pm-border-light); background: var(--pm-white); border-radius: 6px; padding: 4px 10px; font-size: 12px; cursor: pointer; }
.dropdown-acoes {
  position: fixed; background: var(--pm-white); border: 1px solid var(--pm-border-light); border-radius: 6px;
  min-width: 120px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08), 0 8px 28px rgba(0, 0, 0, 0.12); z-index: 10;
}
.dropdown-acoes div { padding: 8px 12px; font-size: 12px; cursor: pointer; color: var(--pm-text-dark); }
.acao-excluir { color: var(--pm-error); }
.paginacao { display: flex; align-items: center; justify-content: center; gap: 12px; font-size: 13px; color: var(--pm-text-mid); }
.paginacao button { border: 1px solid var(--pm-border-light); background: var(--pm-white); border-radius: 6px; width: 28px; height: 28px; cursor: pointer; }
.paginacao button:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
```

- [ ] **Step 5: Run the test to confirm it passes**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PermissionProfilesListView.spec.ts`
Expected: `Test Files 1 passed`, `Tests 5 passed`.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/api/permissionProfiles.ts \
        mesh-suite-frontend/src/views/PermissionProfilesListView.vue \
        mesh-suite-frontend/src/views/__tests__/PermissionProfilesListView.spec.ts
git commit -m "feat(permissions): add PermissionProfilesListView"
```

---

### Task 6: Frontend — `PermissionProfileFormView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/PermissionProfileFormView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/PermissionProfileFormView.spec.ts`

**Interfaces:**
- Consumes: `api/permissionProfiles.ts` (Task 5) — `getPermissionProfile`, `createPermissionProfile`, `updatePermissionProfile`.

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PermissionProfileFormView from '@/views/PermissionProfileFormView.vue'
import * as perfisApi from '@/api/permissionProfiles'

vi.mock('@/api/permissionProfiles')

function mountWithRouter(path = '/permissoes/perfis/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/permissoes', name: 'permissoes', component: { template: '<div />' } },
      { path: '/permissoes/perfis/novo', name: 'permissoes-perfis-novo', component: PermissionProfileFormView },
      { path: '/permissoes/perfis/:id/editar', name: 'permissoes-perfis-editar', component: PermissionProfileFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PermissionProfileFormView, { global: { plugins: [router] } }),
  }))
}

describe('PermissionProfileFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows a required-field error when nome is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(perfisApi.createPermissionProfile).not.toHaveBeenCalled()
  })

  it('renders all 9 modules with a checkbox per action', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Estoque')
    expect(wrapper.find('[data-test="perm-STOCK-VIEW"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="perm-PURCHASE_INVOICE-DELETE"]').exists()).toBe(true)
  })

  it('creates a profile with the checked grants and navigates to the list', async () => {
    vi.mocked(perfisApi.createPermissionProfile).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Financeiro')
    await wrapper.find('[data-test="perm-PAYABLE-VIEW"]').setValue(true)
    await wrapper.find('[data-test="perm-PAYABLE-EDIT"]').setValue(true)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(perfisApi.createPermissionProfile).toHaveBeenCalledWith({
      name: 'Financeiro',
      description: '',
      grants: [{ module: 'PAYABLE', action: 'VIEW' }, { module: 'PAYABLE', action: 'EDIT' }],
    })
    expect(router.currentRoute.value.name).toBe('permissoes')
  })

  it('shows a conflict message when the name already exists', async () => {
    vi.mocked(perfisApi.createPermissionProfile).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Financeiro')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um perfil de permissão cadastrado com este nome.')
  })

  it('loads existing data and pre-checks the right boxes in edit mode', async () => {
    vi.mocked(perfisApi.getPermissionProfile).mockResolvedValue({
      id: 'pp-1', name: 'Gerente', description: 'Gestão', isSystem: true, createdAt: '2026-01-01T00:00:00Z',
      grants: [{ module: 'CUSTOMER', action: 'VIEW' }, { module: 'STOCK', action: 'VIEW' }],
    })
    const { wrapper } = await mountWithRouter('/permissoes/perfis/pp-1/editar')
    await flushPromises()

    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Gerente')
    expect((wrapper.find('[data-test="perm-CUSTOMER-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-STOCK-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-DELETE"]').element as HTMLInputElement).checked).toBe(false)
  })
})
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PermissionProfileFormView.spec.ts`
Expected: `Failed to resolve import "@/views/PermissionProfileFormView.vue"`.

- [ ] **Step 3: Create the form view**

Base this on `UserFormView.vue`'s "Permissões por Módulo" table (`MODULES`/`ACTIONS`/`isChecked`/`togglePermission`) — same 4×N grid pattern, but as the whole page instead of a collapsible sub-section, and with `STOCK` added to the module list:

```vue
<template>
  <AppShell :title="modoEdicao ? 'Editar Perfil de Permissão' : 'Novo Perfil de Permissão'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados do Perfil</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Nome do Perfil *</label>
            <input v-model="form.name" data-test="nome" placeholder="Ex: Supervisor de Vendas" />
            <p v-if="erros.name" class="field-error">{{ erros.name }}</p>
          </div>
          <div>
            <label class="field-label">Descrição</label>
            <input v-model="form.description" data-test="descricao" placeholder="Descreva as responsabilidades deste perfil..." />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Permissões por Módulo</h2>
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
                  @change="toggleGrant(m, a)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Perfil</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getPermissionProfile,
  createPermissionProfile,
  updatePermissionProfile,
  type PermissionProfileRequest,
  type PermissionGrant,
  type ModuleName,
  type ActionName,
} from '@/api/permissionProfiles'

const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER', 'PURCHASE', 'STOCK', 'PAYABLE', 'SALE', 'PURCHASE_INVOICE']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
  PURCHASE: 'Compras',
  STOCK: 'Estoque',
  PAYABLE: 'Contas a Pagar',
  SALE: 'Vendas',
  PURCHASE_INVOICE: 'Notas de Entrada',
}
const ACTIONS: ActionName[] = ['VIEW', 'CREATE', 'EDIT', 'DELETE']
const ACTION_LABELS: Record<ActionName, string> = {
  VIEW: 'Visualizar',
  CREATE: 'Criar',
  EDIT: 'Editar',
  DELETE: 'Excluir',
}

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): PermissionProfileRequest {
  return { name: '', description: '', grants: [] }
}

const form = reactive<PermissionProfileRequest>(novoFormulario())
const erros = reactive<{ name?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

function isChecked(module: ModuleName, action: ActionName) {
  return form.grants.some((g) => g.module === module && g.action === action)
}

function toggleGrant(module: ModuleName, action: ActionName) {
  const index = form.grants.findIndex((g) => g.module === module && g.action === action)
  if (index >= 0) {
    form.grants.splice(index, 1)
  } else {
    form.grants.push({ module, action })
  }
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const perfil = await getPermissionProfile(id)
      form.name = perfil.name
      form.description = perfil.description ?? ''
      form.grants = [...perfil.grants]
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do perfil.'
    }
  }
})

function validar(): boolean {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
  return !erros.name
}

async function salvar() {
  erroGeral.value = ''
  if (!validar()) {
    return
  }
  salvando.value = true
  try {
    const id = route.params.id
    const payload: PermissionProfileRequest = { name: form.name, description: form.description, grants: form.grants }
    if (typeof id === 'string') {
      await updatePermissionProfile(id, payload)
    } else {
      await createPermissionProfile(payload)
    }
    router.push({ name: 'permissoes' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um perfil de permissão cadastrado com este nome.'
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
  router.push({ name: 'permissoes' })
}
</script>

<style scoped>
.form { display: flex; flex-direction: column; gap: 12px; font-family: var(--pm-font); }
.card { background: var(--pm-white); border: 1px solid var(--pm-border-light); border-radius: 12px; padding: 16px; }
.card h2 { font-size: 14px; font-weight: 700; color: var(--pm-text-dark); margin: 0 0 12px; }
.grid { display: grid; gap: 0 14px; margin-bottom: 10px; }
.grid-2 { grid-template-columns: 1fr 1fr; }
.field-label { display: block; font-size: 12px; color: var(--pm-text-mid); margin-bottom: 4px; }
input {
  width: 100%; box-sizing: border-box; background: var(--pm-white); border: 1px solid var(--pm-border-light);
  border-radius: 8px; padding: 8px 10px; color: var(--pm-text-dark); font-size: 13px; font-family: var(--pm-font);
}
.field-error { color: var(--pm-error); font-size: 12px; margin: 4px 0 0; }
.error-geral { color: var(--pm-error); font-size: 14px; }
.tabela-permissoes { width: 100%; border-collapse: collapse; font-size: 12px; margin-top: 4px; }
.tabela-permissoes th, .tabela-permissoes td { text-align: center; padding: 6px 8px; border-top: 1px solid var(--pm-border-light); }
.tabela-permissoes td:first-child { text-align: left; font-weight: 600; color: var(--pm-text-dark); }
.actions { display: flex; justify-content: flex-end; gap: 8px; }
.btn-primary, .btn-secondary { border-radius: 8px; padding: 10px 20px; font-size: 13px; font-weight: 600; font-family: var(--pm-font); cursor: pointer; }
.btn-primary { background: var(--pm-accent); color: var(--pm-white); border: none; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-secondary { background: var(--pm-white); color: var(--pm-text-dark); border: 1px solid var(--pm-border-light); }
</style>
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PermissionProfileFormView.spec.ts`
Expected: `Test Files 1 passed`, `Tests 5 passed`.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/PermissionProfileFormView.vue \
        mesh-suite-frontend/src/views/__tests__/PermissionProfileFormView.spec.ts
git commit -m "feat(permissions): add PermissionProfileFormView"
```

---

### Task 7: Frontend — `PermissionsView.vue` tab wrapper + routes + sidebar

**Files:**
- Create: `mesh-suite-frontend/src/views/PermissionsView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/PermissionsView.spec.ts`
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`

**Interfaces:**
- Consumes: `PermissionProfilesListView.vue` (Task 5, no `AppShell` of its own — see that task's note).

`AppShell.vue` (`mesh-suite-frontend/src/components/AppShell.vue`) renders the full page chrome — sidebar + topbar — around its slot, not just a title bar. Nesting a second `AppShell` inside it would render two sidebars and two topbars. `UsersListView.vue` (existing, standalone route `/usuarios`) already wraps itself in its own `AppShell` and must keep doing so for its own route — so it **cannot** be mounted a second time inside `PermissionsView.vue`'s `AppShell` without duplicating chrome, and this plan does not modify `UsersListView.vue`'s structure to fix that (out of scope, see spec §1). Resolution: `PermissionsView.vue` renders `PermissionProfilesListView` inline for the "Perfis de Permissão" tab (that view has no `AppShell` of its own, by design), and the "Usuários e Permissões" tab is a real navigation to the existing `/usuarios` route rather than an in-place render — clicking it takes the user to the actual `UsersListView.vue` page (same sidebar/topbar chrome, so it still reads as a tab to the user, just via a route change instead of client-side state).

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PermissionsView from '@/views/PermissionsView.vue'
import * as perfisApi from '@/api/permissionProfiles'

vi.mock('@/api/permissionProfiles')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/permissoes', name: 'permissoes', component: PermissionsView },
      { path: '/usuarios', name: 'usuarios', component: { template: '<div />' } },
    ],
  })
  router.push('/permissoes')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PermissionsView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

describe('PermissionsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
    })
  })

  it('shows Perfis de Permissão by default', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.find('[data-test="tab-perfis"]').classes()).toContain('tab-ativa')
    expect(perfisApi.listPermissionProfiles).toHaveBeenCalled()
  })

  it('navigates to /usuarios when the Usuários e Permissões tab is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="tab-usuarios"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('usuarios')
  })
})
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PermissionsView.spec.ts`
Expected: `Failed to resolve import "@/views/PermissionsView.vue"`.

- [ ] **Step 3: Create the tab wrapper**

```vue
<template>
  <AppShell title="Permissões de Acesso">
    <div class="tabs">
      <button type="button" class="tab tab-ativa" data-test="tab-perfis">
        Perfis de Permissão
      </button>
      <button type="button" class="tab" data-test="tab-usuarios" @click="irParaUsuarios">
        Usuários e Permissões
      </button>
    </div>

    <PermissionProfilesListView />
  </AppShell>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PermissionProfilesListView from '@/views/PermissionProfilesListView.vue'

const router = useRouter()

function irParaUsuarios() {
  router.push({ name: 'usuarios' })
}
</script>

<style scoped>
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
  font-family: var(--pm-font);
}

.tab-ativa {
  color: var(--pm-accent);
  border-bottom-color: var(--pm-accent);
  font-weight: 600;
}
</style>
```

`tab-perfis` is always `tab-ativa` — this view IS the "Perfis de Permissão" tab; there is no client-side state to switch, since the other tab is a real route. This means `UsersListView.vue` itself never renders the shared tab bar when reached directly at `/usuarios` (a documented, accepted gap — see spec §1's "sem alterações" decision).

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/PermissionsView.spec.ts`
Expected: `Test Files 1 passed`, `Tests 2 passed`.

- [ ] **Step 5: Wire the routes**

Modify `mesh-suite-frontend/src/router/index.ts` — add the imports near the other view imports:

```typescript
import PermissionsView from '@/views/PermissionsView.vue'
import PermissionProfileFormView from '@/views/PermissionProfileFormView.vue'
```

Add the routes near the `/usuarios` routes:

```typescript
    { path: '/permissoes', name: 'permissoes', component: PermissionsView },
    { path: '/permissoes/perfis/novo', name: 'permissoes-perfis-novo', component: PermissionProfileFormView },
    { path: '/permissoes/perfis/:id/editar', name: 'permissoes-perfis-editar', component: PermissionProfileFormView },
```

- [ ] **Step 6: Point the sidebar at the new route**

Modify `mesh-suite-frontend/src/components/AppSidebar.vue` — find the `CONFIGURAÇÕES` group's `Permissões` entry (`{ icon: '🔒', label: 'Permissões', route: null }`) and change `route: null` to `route: '/permissoes'`.

- [ ] **Step 7: Run the full frontend suite to confirm no regressions**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all test files pass, including `AppSidebar.spec.ts` (check it doesn't assert `route === null` for the Permissões item specifically — if it does, update that one assertion to `/permissoes`, following the same pattern the Fornecedores sidebar activation used).

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-frontend/src/views/PermissionsView.vue \
        mesh-suite-frontend/src/views/__tests__/PermissionsView.spec.ts \
        mesh-suite-frontend/src/router/index.ts \
        mesh-suite-frontend/src/components/AppSidebar.vue
git commit -m "feat(permissions): add Permissões tab screen, wire routes and sidebar"
```

---

### Task 8: Frontend — rewrite `UserFormView.vue` to use dynamic profiles

**Files:**
- Modify: `mesh-suite-frontend/src/api/users.ts`
- Modify: `mesh-suite-frontend/src/views/UserFormView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts`
- Modify: `mesh-suite-frontend/src/views/UsersListView.vue` (mechanical fix — removing `Profile` from `api/users.ts` breaks its compile; see Step 1)
- Modify: `mesh-suite-frontend/src/views/__tests__/UsersListView.spec.ts` (drop the now-removed `profile` fixture field)

**Interfaces:**
- Consumes: `api/permissionProfiles.ts` (Task 5) — `listPermissionProfiles`, `getPermissionProfile`.

- [ ] **Step 1: Update `api/users.ts` types**

Modify `mesh-suite-frontend/src/api/users.ts`:

```typescript
export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE' | 'STOCK' | 'PAYABLE' | 'SALE' | 'PURCHASE_INVOICE'
```

(adds `'STOCK'` to the existing union — was missing before this plan).

```typescript
export interface UserRequest {
  name: string
  email: string
  phone: string
  role: Role
  active: boolean
  password: string
  confirmPassword: string
  permissions: Permission[]
  permissionProfileId: string | null
}

export interface UserResponse {
  id: string
  name: string
  email: string
  phone: string
  role: Role
  active: boolean
  permissions: Permission[]
  permissionProfileId: string | null
  permissionProfileName: string | null
}

export interface UserListItem {
  id: string
  name: string
  email: string
  active: boolean
  permissionProfileId: string | null
  permissionProfileName: string | null
}
```

Note this **removes** `profile: Profile` from all three interfaces and drops the now-unused `Profile` type export. Also remove the `profile?: Profile` field from `ListUsersParams`.

This is a breaking type change for `UsersListView.vue`, which currently imports `type Profile` and reads `user.profile` for its badge column and filter select (see the design spec's §8 risk note about this being a documented, accepted gap for users created after this change). Fix it in the same step, since the file won't compile otherwise.

Modify `mesh-suite-frontend/src/views/UsersListView.vue` — remove the profile filter `<select>` (the whole block, between the "busca" input and the "Status" `<select>`):

```html
      <select v-model="filtros.profile" @change="carregar(0)">
        <option value="">Perfil</option>
        <option value="ADMIN">Admin</option>
        <option value="MANAGER">Gerente</option>
        <option value="SALES">Vendedor</option>
        <option value="VIEWER">Visualizador</option>
      </select>
```

Change the "Perfil" column header from sortable to plain (no more sorting by a field that's no longer meaningful):

```html
          <div class="table-grid-col">Perfil</div>
```

(replaces the existing `<div class="table-grid-col table-grid-col-sortable" data-test="col-perfil" @click="toggleSort('profile')">Perfil<span ...>...</span></div>` block).

Change the badge cell:

```html
          <div class="table-grid-cell">
            <StatusBadge :label="user.permissionProfileName ?? '—'" color="blue" />
          </div>
```

(replaces `<StatusBadge :label="PROFILE_LABELS[user.profile]" color="blue" />`).

In the `<script setup>` block: remove `type Profile` from the `@/api/users` import list; delete the `PROFILE_LABELS` constant entirely; change `sortField`'s type and the `sortIcon`/`toggleSort` parameter types from `'name' | 'profile' | null` / `'name' | 'profile'` to `'name' | null` / `'name'`; change `filtros` from `reactive({ busca: '', profile: '', active: '' })` to `reactive({ busca: '', active: '' })`; and remove the `profile: (filtros.profile || undefined) as Profile | undefined,` line from the `listUsers({...})` call inside `carregar()`.

Modify `mesh-suite-frontend/src/views/__tests__/UsersListView.spec.ts` — line 26's fixture has `profile: 'SALES' as const,`; delete that field (it's not read by the component anymore and the type no longer has it):

```typescript
const userBase = { id: 'u1', name: 'Carla Vendedora', email: 'carla@aurora.com.br', active: true }
```

- [ ] **Step 2: Update the existing `UserFormView.spec.ts` tests to use dynamic profile IDs**

Replace the whole file:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import UserFormView from '@/views/UserFormView.vue'
import * as usersApi from '@/api/users'
import * as perfisApi from '@/api/permissionProfiles'

vi.mock('@/api/users')
vi.mock('@/api/permissionProfiles')

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

const perfilAdmin = {
  id: 'pp-admin', name: 'Admin', description: '', isSystem: true, moduleCount: 9, userCount: 1,
}
const perfilVendedor = {
  id: 'pp-vendedor', name: 'Vendedor', description: '', isSystem: true, moduleCount: 4, userCount: 5,
}

function mockPerfis() {
  vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
    content: [perfilAdmin, perfilVendedor], totalElements: 2, totalPages: 1, number: 0, size: 100,
  })
  vi.mocked(perfisApi.getPermissionProfile).mockImplementation(async (id: string) => {
    if (id === 'pp-admin') {
      return {
        id: 'pp-admin', name: 'Admin', description: '', isSystem: true, createdAt: '2026-01-01T00:00:00Z',
        grants: [
          { module: 'PURCHASE', action: 'VIEW' }, { module: 'PURCHASE', action: 'CREATE' },
          { module: 'PAYABLE', action: 'VIEW' }, { module: 'PAYABLE', action: 'EDIT' },
        ],
      }
    }
    return {
      id: 'pp-vendedor', name: 'Vendedor', description: '', isSystem: true, createdAt: '2026-01-01T00:00:00Z',
      grants: [{ module: 'CUSTOMER', action: 'VIEW' }],
    }
  })
}

describe('UserFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockPerfis()
  })

  it('shows required-field errors when name/email/role/profile/password are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(usersApi.createUser).not.toHaveBeenCalled()
  })

  it('rejects mismatched password confirmation', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('outraSenha1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('As senhas não coincidem')
  })

  it('lists the profiles fetched from the API in the Perfil de Acesso select', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Admin')
    expect(wrapper.text()).toContain('Vendedor')
  })

  it('pre-checks the permission grid from the selected profile', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="profile"]').trigger('change')
    await flushPromises()

    expect((wrapper.find('[data-test="perm-CUSTOMER-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-CREATE"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('includes Compras and Contas a Pagar in the grid, pre-checked for the Admin profile', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="profile"]').setValue('pp-admin')
    await wrapper.find('[data-test="profile"]').trigger('change')
    await flushPromises()

    expect(wrapper.text()).toContain('Compras')
    expect(wrapper.text()).toContain('Contas a Pagar')
    expect((wrapper.find('[data-test="perm-PURCHASE-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PAYABLE-EDIT"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PAYABLE-CREATE"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('submits the form with the chosen permissionProfileId and navigates to the list on success', async () => {
    vi.mocked(usersApi.createUser).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="profile"]').trigger('change')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await flushPromises()
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(usersApi.createUser).toHaveBeenCalledWith(expect.objectContaining({ permissionProfileId: 'pp-vendedor' }))
    expect(router.currentRoute.value.name).toBe('usuarios')
  })

  it('shows a conflict message on duplicate e-mail (409)', async () => {
    vi.mocked(usersApi.createUser).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um usuário cadastrado com este e-mail')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(usersApi.createUser).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing user data in edit mode with blank password fields and the right profile selected', async () => {
    vi.mocked(usersApi.getUser).mockResolvedValue({
      id: 'u1', name: 'Carla', email: 'carla@aurora.com.br', phone: '(11) 98888-7777',
      role: 'SALES_REP', active: true,
      permissions: [{ module: 'ORDER', action: 'VIEW' }],
      permissionProfileId: 'pp-vendedor', permissionProfileName: 'Vendedor',
    } as any)

    const { wrapper } = await mountWithRouter('/usuarios/u1/editar')
    await flushPromises()

    expect(usersApi.getUser).toHaveBeenCalledWith('u1')
    expect((wrapper.find('[data-test="name"]').element as HTMLInputElement).value).toBe('Carla')
    expect((wrapper.find('[data-test="password"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.find('[data-test="profile"]').element as HTMLInputElement).value).toBe('pp-vendedor')
  })

  it('shows an error message when loading user data fails in edit mode', async () => {
    vi.mocked(usersApi.getUser).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/usuarios/u1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do usuário.')
  })
})
```

- [ ] **Step 3: Run it to confirm the current implementation fails these new assertions**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/UserFormView.spec.ts`
Expected: multiple failures — the `profile` select still renders enum options (`SALES`/`ADMIN`/`VIEWER`), not the mocked `pp-admin`/`pp-vendedor` IDs; `createUser` is called with the old `profile` field shape.

- [ ] **Step 4: Rewrite `UserFormView.vue`**

Replace the `<script setup>` block's profile-related code. Full replacement of the relevant sections (keep the rest of the file — `<template>`, styles, non-profile logic — unchanged except the "Permissões por Módulo" `<details>` section's hint text and the `<select data-test="profile">` options, shown below):

Template changes — replace:

```html
          <div>
            <label class="field-label">Perfil de Acesso *</label>
            <select v-model="form.profile" data-test="profile" @change="applyDefaultPermissions">
              <option value="">Selecione...</option>
              <option v-for="p in PROFILES" :key="p" :value="p">{{ PROFILE_LABELS[p] }}</option>
            </select>
            <p v-if="erros.profile" class="field-error">{{ erros.profile }}</p>
          </div>
```

with:

```html
          <div>
            <label class="field-label">Perfil de Acesso *</label>
            <select v-model="form.permissionProfileId" data-test="profile" @change="applyProfilePermissions">
              <option value="">Selecione...</option>
              <option v-for="p in perfis" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
            <p v-if="erros.profile" class="field-error">{{ erros.profile }}</p>
          </div>
```

and update the hint text right above the permission table from "As permissões são herdadas do perfil selecionado." (unchanged wording is fine — still true).

Script changes — replace the whole `<script setup>` section with:

```typescript
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getUser,
  createUser,
  updateUser,
  type UserRequest,
  type Role,
  type ModuleName,
  type ActionName,
  type Permission,
} from '@/api/users'
import { listPermissionProfiles, getPermissionProfile, type PermissionProfileSummary } from '@/api/permissionProfiles'

const ROLES: Role[] = ['ADMINISTRATIVE', 'SALES_REP', 'PRODUCTION', 'OUTSOURCED', 'ADMIN']
const ROLE_LABELS: Record<Role, string> = {
  ADMINISTRATIVE: 'Administrativo',
  SALES_REP: 'Representante',
  PRODUCTION: 'Produção',
  OUTSOURCED: 'Terceirizado',
  ADMIN: 'Administrador',
}
const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER', 'PURCHASE', 'STOCK', 'PAYABLE', 'SALE', 'PURCHASE_INVOICE']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
  PURCHASE: 'Compras',
  STOCK: 'Estoque',
  PAYABLE: 'Contas a Pagar',
  SALE: 'Vendas',
  PURCHASE_INVOICE: 'Notas de Entrada',
}
const ACTIONS: ActionName[] = ['VIEW', 'CREATE', 'EDIT', 'DELETE']
const ACTION_LABELS: Record<ActionName, string> = {
  VIEW: 'Visualizar',
  CREATE: 'Criar',
  EDIT: 'Editar',
  DELETE: 'Excluir',
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
  permissionProfileId: string
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
    permissionProfileId: '',
    active: true,
    password: '',
    confirmPassword: '',
    permissions: [],
  }
}

const form = reactive<FormState>(novoFormulario())
const perfis = ref<PermissionProfileSummary[]>([])
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

async function applyProfilePermissions() {
  if (!form.permissionProfileId) {
    return
  }
  try {
    const perfil = await getPermissionProfile(form.permissionProfileId)
    form.permissions = [...perfil.grants]
  } catch {
    erroGeral.value = 'Não foi possível carregar as permissões padrão deste perfil.'
  }
}

onMounted(async () => {
  try {
    const pagina = await listPermissionProfiles({ size: 100 })
    perfis.value = pagina.content
  } catch {
    perfis.value = []
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const user = await getUser(id)
      form.name = user.name
      form.email = user.email
      form.phone = user.phone ?? ''
      form.role = user.role
      form.permissionProfileId = user.permissionProfileId ?? ''
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
  erros.profile = form.permissionProfileId ? undefined : 'Campo obrigatório'
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
    active: form.active,
    password: form.password,
    confirmPassword: form.confirmPassword,
    permissions: form.permissions,
    permissionProfileId: form.permissionProfileId || null,
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
```

Delete the `DEFAULT_MATRIX` constant entirely — it's fully replaced by the backend-driven `applyProfilePermissions`.

- [ ] **Step 5: Run the test to confirm it passes**

Run: `cd mesh-suite-frontend && npx vitest run src/views/__tests__/UserFormView.spec.ts`
Expected: `Test Files 1 passed`, `Tests 10 passed`.

- [ ] **Step 6: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all test files pass. Pay special attention to `UsersListView.spec.ts` (Step 1's fixture/profile-filter fixes) and `AppSidebar.spec.ts` (Task 7's route change).

- [ ] **Step 7: Type-check**

Run: `cd mesh-suite-frontend && ./node_modules/.bin/vue-tsc -b --noEmit`
Expected: no new errors beyond the two pre-existing, unrelated ones already known from the payment-methods work (`FornecedoresListView.spec.ts`, `FornecedorFormView.spec.ts` — unused-variable warnings, nothing to do with this feature).

- [ ] **Step 8: Manual smoke check**

Start the frontend dev server only if the user has already started the backend and asks for this check — per this session's standing instruction, do not auto-start dev servers. If both are already running, open `/permissoes` in a browser and confirm: "Perfis de Permissão" shows the 4 seeded defaults on first load, clicking "Usuários e Permissões" navigates to `/usuarios`, "+ Novo Perfil" → save → shows up in the list, editing a system profile's grants persists, deleting a system profile shows the backend's blocking message, and `/usuarios/novo`'s "Perfil de Acesso" select populates from the same 4 profiles with live pre-checking.

- [ ] **Step 9: Commit**

```bash
git add mesh-suite-frontend/src/api/users.ts \
        mesh-suite-frontend/src/views/UserFormView.vue \
        mesh-suite-frontend/src/views/__tests__/UserFormView.spec.ts \
        mesh-suite-frontend/src/views/UsersListView.vue \
        mesh-suite-frontend/src/views/__tests__/UsersListView.spec.ts
git commit -m "feat(permissions): switch UserFormView to dynamic backend-driven profiles"
```

---

## Final Verification

- [ ] Run the full backend suite once more from a clean state: `cd mesh-suite-backend && ./mvnw test 2>&1 | tail -80` — confirm failure count matches the known 15 pre-existing baseline, nothing new.
- [ ] Run the full frontend suite once more: `cd mesh-suite-frontend && npx vitest run` — all green.
- [ ] Run the frontend type-check once more: `cd mesh-suite-frontend && ./node_modules/.bin/vue-tsc -b --noEmit` — only the two known pre-existing errors.
- [ ] `git log --oneline -9` shows the 8 commits from this plan in order, each with a passing test suite at that point in history (no squashing needed, but don't skip verifying).
