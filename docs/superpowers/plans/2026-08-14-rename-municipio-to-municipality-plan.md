# Rename Municipio → Municipality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename `Municipio` (Brazilian municipality/city IBGE reference data) to `Municipality`, in its own top-level `com.meshsuite.municipality` package, in English, without changing a single character of end-customer-visible text.

**Architecture:** The smallest sub-project in this initiative so far — no cross-module Java coupling exists (confirmed by full-codebase grep during design), and the module has no DTO/service/exception layers, only an entity, a repository, and a controller. One backend task covers the whole domain, one frontend task covers the API file plus its single consumer bridge (`ClientesListView.vue`, Partner's own screen — not renamed, just bridged), one migration task, one verification task.

**Tech Stack:** Spring Boot 3.4.5, Java 21, PostgreSQL 16, Flyway, Vue 3, TypeScript, Vitest.

## Global Constraints

- End-customer-visible text (frontend routes/labels, UI text, error messages) stays in Portuguese, unchanged, character-for-character.
- Backend REST endpoint paths ARE code, not user-visible text, and DO get translated: `/api/municipios` → `/api/municipalities`.
- The REST query parameter name `uf` stays Portuguese everywhere — in the controller's `@RequestParam`, the repository's named JPQL parameter, and the frontend param object key — matching the established `busca`/`ativo` convention. Only the entity FIELD (`uf`→`state`) is translated, not the wire-level query param name.
- `MunicipioController`'s method name (`listar`) stays Portuguese, matching the established convention that an entity's own already-Portuguese method names aren't retranslated.
- Component-local variable/function names in the bridge consumer (`cidades`, `carregarCidades`) stay unchanged — they belong to Partner's own module, not Municipality's field map.
- Field/name map:

  | Portuguese | English |
  |---|---|
  | `Municipio` | `Municipality` |
  | `MunicipioRepository` | `MunicipalityRepository` |
  | `MunicipioController` | `MunicipalityController` |
  | field `nome` | `name` |
  | field `uf` | `state` |
  | table `municipio` | `municipality` |
  | column `nome` | `name` |
  | column `uf` | `state` |

---

## Task 1: Migration

**Files:**
- Delete: `mesh-suite-backend/src/main/resources/db/migration/V19__create_municipio.sql`
- Create: `mesh-suite-backend/src/main/resources/db/migration/V19__create_municipality.sql`

**Interfaces:**
- Produces: table `municipality` (columns: `id BIGINT PRIMARY KEY`, `name VARCHAR(150) NOT NULL`, `state VARCHAR(2) NOT NULL`).

- [ ] **Step 1: Create the renamed migration file**

The current file has 3 parts: a 6-line comment header, a 15-line DDL block (`CREATE TABLE`/`CREATE INDEX` ×2), and one `INSERT INTO municipio (id, nome, uf) VALUES` statement followed by ~5,573 data-row tuples like `(1200013, 'Acrelândia', 'AC'),`.

Run this to produce the new file, which only touches the header/DDL/INSERT-statement line and leaves every data row byte-identical (they're positional — they don't reference column names):

```bash
sed \
  -e 's/CREATE TABLE municipio (/CREATE TABLE municipality (/' \
  -e 's/    nome VARCHAR(150) NOT NULL,/    name VARCHAR(150) NOT NULL,/' \
  -e 's/    uf VARCHAR(2) NOT NULL/    state VARCHAR(2) NOT NULL/' \
  -e 's/CREATE INDEX idx_municipio_uf ON municipio(uf);/CREATE INDEX idx_municipio_uf ON municipality(state);/' \
  -e 's/CREATE INDEX idx_municipio_nome ON municipio(nome);/CREATE INDEX idx_municipio_nome ON municipality(name);/' \
  -e 's/INSERT INTO municipio (id, nome, uf) VALUES/INSERT INTO municipality (id, name, state) VALUES/' \
  mesh-suite-backend/src/main/resources/db/migration/V19__create_municipio.sql \
  > mesh-suite-backend/src/main/resources/db/migration/V19__create_municipality.sql
```

Note: index names (`idx_municipio_uf`, `idx_municipio_nome`) stay unrenamed — only their target table/column changes — matching the established convention of leaving internal DB object names alone during table renames.

- [ ] **Step 2: Verify the rewrite is correct**

Run: `diff <(sed -n '16,5589p' mesh-suite-backend/src/main/resources/db/migration/V19__create_municipio.sql) <(sed -n '16,5589p' mesh-suite-backend/src/main/resources/db/migration/V19__create_municipality.sql)`

Expected: no output (empty diff) — confirms every data row from line 16 onward is byte-identical between old and new files. Also run: `head -15 mesh-suite-backend/src/main/resources/db/migration/V19__create_municipality.sql` and confirm it shows `CREATE TABLE municipality (`, `name VARCHAR(150) NOT NULL,`, `state VARCHAR(2) NOT NULL`, and the two `CREATE INDEX ... ON municipality(...)` lines with `state`/`name` as their targets.

- [ ] **Step 3: Delete the old file**

```bash
git rm mesh-suite-backend/src/main/resources/db/migration/V19__create_municipio.sql
```

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V19__create_municipality.sql
git commit -m "refactor(municipality): rename V19 migration municipio->municipality"
```

---

## Task 2: Backend domain, repository, controller, controller test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/municipality/domain/Municipality.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/municipality/repository/MunicipalityRepository.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/municipality/controller/MunicipalityController.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/municipality/controller/MunicipalityControllerTest.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/municipio/domain/Municipio.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/municipio/repository/MunicipioRepository.java`
- Delete: `mesh-suite-backend/src/main/java/com/meshsuite/municipio/controller/MunicipioController.java`
- Delete: `mesh-suite-backend/src/test/java/com/meshsuite/municipio/controller/MunicipioControllerTest.java`

**Interfaces:**
- Consumes: Task 1's `municipality` table.
- Produces: `com.meshsuite.municipality.domain.Municipality` (`getId(): Long`, `getName(): String`, `getState(): String`), `com.meshsuite.municipality.repository.MunicipalityRepository` with `findNamesByStateOptional(String state): List<String>`, REST endpoint `GET /api/municipalities`.

This is the whole backend module — no other file in the codebase references `Municipio`/`MunicipioRepository` (confirmed via full-codebase grep during design), so no relocate-test-restore or bridging is needed. This task alone makes the backend module compile.

- [ ] **Step 1: Create `Municipality.java`**

```java
package com.meshsuite.municipality.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Global IBGE municipality reference data (see V19__create_municipality.sql) --
 * not tenant-scoped, no RLS, same rows for every tenant.
 */
@Entity
@Table(name = "municipality")
public class Municipality {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2)
    private String state;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }
}
```

- [ ] **Step 2: Create `MunicipalityRepository.java`**

```java
package com.meshsuite.municipality.repository;

import com.meshsuite.municipality.domain.Municipality;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MunicipalityRepository extends JpaRepository<Municipality, Long> {

    @Query("SELECT DISTINCT m.name FROM Municipality m WHERE (:uf IS NULL OR m.state = :uf) ORDER BY m.name")
    List<String> findNamesByStateOptional(@Param("uf") String uf);
}
```

Note the JPQL property paths (`m.name`, `m.state`) are translated to match the renamed entity fields, but the named parameter itself stays `:uf`/`@Param("uf")` — per Global Constraints, the query-parameter name is not translated.

- [ ] **Step 3: Create `MunicipalityController.java`**

```java
package com.meshsuite.municipality.controller;

import com.meshsuite.municipality.repository.MunicipalityRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/municipalities")
public class MunicipalityController {

    private final MunicipalityRepository municipalityRepository;

    public MunicipalityController(MunicipalityRepository municipalityRepository) {
        this.municipalityRepository = municipalityRepository;
    }

    @GetMapping
    public List<String> listar(@RequestParam(required = false) String uf) {
        return municipalityRepository.findNamesByStateOptional(uf);
    }
}
```

Note the constructor parameter/field name is renamed to `municipalityRepository` to match the renamed type (standard treatment for any injected dependency in this initiative). Only the method name `listar` is kept exactly as it was — per the design's explicit convention that an entity's own already-Portuguese method names aren't retranslated, which applies to method names specifically, not field/parameter names.

- [ ] **Step 4: Delete the 3 old files**

```bash
git rm mesh-suite-backend/src/main/java/com/meshsuite/municipio/domain/Municipio.java \
       mesh-suite-backend/src/main/java/com/meshsuite/municipio/repository/MunicipioRepository.java \
       mesh-suite-backend/src/main/java/com/meshsuite/municipio/controller/MunicipioController.java
```

- [ ] **Step 5: Create `MunicipalityControllerTest.java`**

Translated from `MunicipioControllerTest.java` (deleted next step). Method name translations: `listsAllMunicipiosWhenNoUfIsGiven`→`listsAllMunicipalitiesWhenNoUfIsGiven`; `filtersMunicipiosByUf`→`filtersMunicipalitiesByUf`; `unauthenticatedRequestIsRejected` unchanged. The URL path changes to `/api/municipalities`; the `uf` query param name stays `uf`.

```java
package com.meshsuite.municipality.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.repository.UserRepository;
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
class MunicipalityControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String loginAndGetCookie() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("aurora");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Aurora Ltda");
        company.setCnpj("11222333000144");
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.ADMIN);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"marina@aurora.com.br\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    @Test
    void listsAllMunicipalitiesWhenNoUfIsGiven() throws Exception {
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, loginAndGetCookie());

        mockMvc.perform(get("/api/municipalities").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("São Paulo")))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("Rio de Janeiro")));
    }

    @Test
    void filtersMunicipalitiesByUf() throws Exception {
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, loginAndGetCookie());

        mockMvc.perform(get("/api/municipalities").cookie(cookie).param("uf", "AC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("Rio Branco")))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("São Paulo"))));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/municipalities"))
                .andExpect(status().isUnauthorized());
    }
}
```

```bash
git rm mesh-suite-backend/src/test/java/com/meshsuite/municipio/controller/MunicipioControllerTest.java
```

- [ ] **Step 6: Run the new controller test**

No relocate-test-restore is needed — this module has no other backend consumers, so the whole module should compile once this task's files are in place.

Run: `cd mesh-suite-backend && mvn -q test -Dtest=MunicipalityControllerTest`

Expected: `BUILD SUCCESS`, 3 tests run, 0 failures, 0 errors.

- [ ] **Step 7: Run the FULL backend test suite**

Run: `cd mesh-suite-backend && mvn -q clean test`

Expected: 0 failures. Errors should match the documented pre-existing flake exactly — 15 errors (3 `CompanyRepositoryTest` + 3 `AccountsPayableControllerTest` + 1 `AccountsPayableRepositoryTest` + 8 `AccountsPayableServiceTest`), confirmed identical after every prior sub-project's merge. If the error count or the specific failing classes differ from this signature, investigate before proceeding — do not assume it's the same flake without checking `target/surefire-reports/*.txt` for the exact class names.

Also confirm: `find mesh-suite-backend/src -type d -path "*com/meshsuite/municipio*"` returns nothing (no output) — the whole `com.meshsuite.municipio` tree is gone.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/municipality/ \
        mesh-suite-backend/src/test/java/com/meshsuite/municipality/
git commit -m "refactor(municipality): rename Municipio domain/repository/controller to English, route municipios->municipalities"
```

---

## Task 3: Frontend — `api/municipios.ts` → `api/municipalities.ts`, bridge `ClientesListView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/api/municipalities.ts`
- Create: `mesh-suite-frontend/src/api/__tests__/municipalities.spec.ts`
- Modify: `mesh-suite-frontend/src/views/ClientesListView.vue`
- Modify: `mesh-suite-frontend/src/views/__tests__/ClientesListView.spec.ts`
- Delete: `mesh-suite-frontend/src/api/municipios.ts`
- Delete: `mesh-suite-frontend/src/api/__tests__/municipios.spec.ts`

**Interfaces:**
- Produces: `ListMunicipalitiesParams`, `listMunicipalities(params: ListMunicipalitiesParams): Promise<string[]>`.

Municipality has no CRUD screens of its own — this task's only frontend view work is bridging its sole consumer, `ClientesListView.vue` (Partner's own screen, not renamed).

- [ ] **Step 1: Create `municipalities.ts`**

```typescript
import { apiClient } from './client'

export interface ListMunicipalitiesParams {
  uf?: string
}

export async function listMunicipalities(params: ListMunicipalitiesParams = {}): Promise<string[]> {
  const { data } = await apiClient.get<string[]>('/municipalities', { params })
  return data
}
```

(`uf` stays as the param name per Global Constraints.)

- [ ] **Step 2: Create `municipalities.spec.ts`**

```typescript
import { describe, it, expect, vi } from 'vitest'
import { apiClient } from '../client'
import { listMunicipalities } from '../municipalities'

vi.mock('../client', () => ({
  apiClient: { get: vi.fn() },
}))

describe('api/municipalities', () => {
  it('listMunicipalities calls GET /municipalities with query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: ['São Paulo', 'Campinas'] })
    const result = await listMunicipalities({ uf: 'SP' })
    expect(apiClient.get).toHaveBeenCalledWith('/municipalities', { params: { uf: 'SP' } })
    expect(result).toEqual(['São Paulo', 'Campinas'])
  })

  it('listMunicipalities works without params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] })
    await listMunicipalities()
    expect(apiClient.get).toHaveBeenCalledWith('/municipalities', { params: {} })
  })
})
```

- [ ] **Step 3: Delete the 2 old files**

```bash
git rm mesh-suite-frontend/src/api/municipios.ts \
       mesh-suite-frontend/src/api/__tests__/municipios.spec.ts
```

- [ ] **Step 4: Bridge `ClientesListView.vue`**

Change line 132 from:
```typescript
import { listarMunicipios } from '@/api/municipios'
```
to:
```typescript
import { listMunicipalities } from '@/api/municipalities'
```

Change line 244 (inside `carregarCidades()`) from:
```typescript
    cidades.value = await listarMunicipios({ uf })
```
to:
```typescript
    cidades.value = await listMunicipalities({ uf })
```

Nothing else in this file changes — `cidades`, `carregarCidades`, `ufsSelecionadas`, and every other local identifier stay exactly as they are (Partner's own naming, out of scope for this rename).

- [ ] **Step 5: Bridge `ClientesListView.spec.ts`**

Change line 7 from:
```typescript
import * as municipiosApi from '@/api/municipios'
```
to:
```typescript
import * as municipiosApi from '@/api/municipalities'
```

(Keep the local alias `municipiosApi` unchanged — matches this initiative's established minimal-bridge convention.)

Change line 10 from:
```typescript
vi.mock('@/api/municipios')
```
to:
```typescript
vi.mock('@/api/municipalities')
```

Change line 45 from `vi.mocked(municipiosApi.listarMunicipios).mockResolvedValue(['São Paulo'])` to `vi.mocked(municipiosApi.listMunicipalities).mockResolvedValue(['São Paulo'])`.

Change line 69 from `expect(municipiosApi.listarMunicipios).toHaveBeenCalledWith({ uf: undefined })` to `expect(municipiosApi.listMunicipalities).toHaveBeenCalledWith({ uf: undefined })`.

Change line 80 from `vi.mocked(municipiosApi.listarMunicipios).mockResolvedValue(['Rio de Janeiro'])` to `vi.mocked(municipiosApi.listMunicipalities).mockResolvedValue(['Rio de Janeiro'])`.

Change line 88 from `expect(municipiosApi.listarMunicipios).toHaveBeenLastCalledWith({ uf: 'RJ' })` to `expect(municipiosApi.listMunicipalities).toHaveBeenLastCalledWith({ uf: 'RJ' })`.

Nothing else in this file changes.

- [ ] **Step 6: Run the affected tests**

Run: `cd mesh-suite-frontend && npx vitest run src/api/__tests__/municipalities.spec.ts src/views/__tests__/ClientesListView.spec.ts`

Expected: all tests pass — 2 tests in `municipalities.spec.ts`, and every test in `ClientesListView.spec.ts` passing exactly as before (pure rename, no test logic changes).

- [ ] **Step 7: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run --run`

Expected: all test files pass (44 files total — same count as before this sub-project, since Task 3 renames 1 file 1-for-1 and bridges 2 others without adding/removing test files).

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: 0 errors. (Do not omit `-p tsconfig.app.json` — bare `vue-tsc --noEmit` silently reports 0 errors regardless of real breakage in this project.)

Also confirm: `find mesh-suite-frontend/src -iname "*municipio*"` returns nothing.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-frontend/src/api/municipalities.ts \
        mesh-suite-frontend/src/api/__tests__/municipalities.spec.ts \
        mesh-suite-frontend/src/views/ClientesListView.vue \
        mesh-suite-frontend/src/views/__tests__/ClientesListView.spec.ts
git commit -m "refactor(municipality): rename frontend api/municipios.ts to municipalities.ts, bridge ClientesListView"
```

---

## Task 4: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Full backend suite**

Run: `cd mesh-suite-backend && mvn -q clean test`

Expected: 0 failures. Errors matching the documented pre-existing flake exactly (15 errors: 3 `CompanyRepositoryTest` + 3 `AccountsPayableControllerTest` + 1 `AccountsPayableRepositoryTest` + 8 `AccountsPayableServiceTest`) — confirm via `find target/surefire-reports -name "*.txt" | xargs grep -h "^Tests run:" | awk -F'[ ,]+' '{tests+=$3; failures+=$5; errors+=$7} END {print tests, failures, errors}'` and cross-check the specific failing class names.

- [ ] **Step 2: Full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run --run`

Expected: all test files pass, 0 failures.

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit -p tsconfig.app.json`

Expected: 0 errors.

- [ ] **Step 3: `com.meshsuite.municipio` no longer exists**

Run: `find mesh-suite-backend/src -type d -path "*com/meshsuite/municipio*"`

Expected: no output.

- [ ] **Step 4: Broad grep audit for missed `Municipio`/`municipio` identifiers**

Run: `grep -rln "Municipio\b" mesh-suite-backend/src --include="*.java"`

Expected: no output.

Run: `grep -rln "municipios\b\|Municipio\b\|listarMunicipios\|ListarMunicipiosParams" mesh-suite-frontend/src --include="*.ts" --include="*.vue"`

Expected: no output. (Portuguese prose text like the "Cidade" label or city-name data is expected to remain — only bare identifiers matter here.)

- [ ] **Step 5: Confirm no leftover verification-technique artifacts**

Run: `git status --short`

Expected: clean.

Report PLAN COMPLETE once all 5 verification steps pass.
