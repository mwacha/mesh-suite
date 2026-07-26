# Login/Multitenant Foundation (PRD-14 slice 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first implementable slice of Mesh Suite: repo scaffolding for a Spring Boot backend and a Vue 3 frontend, the `Tenant`/`Empresa`/`Usuario`/`PasswordResetToken` data model, JWT cookie-based login with PostgreSQL Row-Level Security tenant isolation, password recovery, rate limiting, and the login/recovery UI — everything in `docs/superpowers/specs/2026-07-25-login-multitenant-foundation-design.md`.

**Architecture:** Two self-contained subprojects (`mesh-suite-backend/`, `mesh-suite-frontend/`) under this repo, wired together by a root `docker-compose.yml` (Postgres + backend + frontend). The backend enforces tenant isolation at the database layer via Postgres RLS policies rather than in application code, so raw SQL/reporting tools are covered too, not just the ORM path. A request's tenant is established once by a JWT-validating filter and propagated to every `@Transactional` method via a `ThreadLocal` + AOP hook that issues `SET LOCAL app.tenant_id` at the start of each transaction.

**Tech Stack:** Java 21, Spring Boot 3.4.5 (Web, Data JPA, Security, Validation, Mail, Actuator, Flyway), PostgreSQL 16, `io.jsonwebtoken:jjwt` 0.12.x, Testcontainers. Vue 3 + TypeScript + Vite + Vue Router + Pinia + Axios, Vitest.

## Global Constraints

- Never commit secrets (DB password, JWT signing key, SMTP credentials) — all via environment variables, per `CLAUDE.md`. `.env` is gitignored; only `.env.example` (empty/placeholder values) is committed.
- `email` on `Usuario` and `cnpj` on `Empresa` are **globally unique**, not per-tenant (documented deviation from PRD-14, spec §2).
- Every business table is created with `tenant_id` indexed and an RLS policy from its very first migration — no retrofit (PRD-14 §3, spec §4).
- JWT is stored only in an `HttpOnly`+`Secure`+`SameSite=Strict` cookie, never in `localStorage` or exposed to frontend JS (spec §2 item 7).
- Auth failures (unknown email, wrong password, inactive user, inactive tenant) all return the same generic message — never reveal which condition failed (spec §5).
- `usuario.ativo` / `tenant.ativo` are re-checked against the database on every authenticated request, not trusted from JWT claims alone (spec §5).

## Design decision beyond the spec: RLS bypass for login lookup

The spec requires (a) RLS on `usuario` filtering by `tenant_id`, and (b) login authenticating by **email alone**, with the tenant unknown until *after* the user row is found. Those two requirements conflict: if the login query runs under the normal `usuario_tenant_isolation` policy, `app.tenant_id` is unset at that point, so the policy denies every row and login can never succeed.

Resolution used throughout this plan (see Task 4, Task 10): a second, narrowly-scoped **permissive** RLS policy on `usuario`,`usuario_login_lookup`, allows `SELECT` only when a session flag `app.bypass_tenant_check = 'true'` is set. That flag is set in exactly one place in the codebase — `AuthService.findByEmailForLogin` — for the duration of the login lookup only, and is never set anywhere else. Postgres OR's multiple permissive policies together, so this doesn't weaken the tenant policy for any other query. Once the user's `tenant_id` is known (immediately after this lookup), everything else proceeds through the normal tenant-scoped path.

---

### Task 1: Repo scaffolding (backend + frontend + docker-compose)

**Files:**
- Create: `mesh-suite-backend/pom.xml`
- Create: `mesh-suite-backend/mvnw`, `mesh-suite-backend/mvnw.cmd`, `mesh-suite-backend/.mvn/wrapper/maven-wrapper.properties`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/MeshSuiteBackendApplication.java`
- Create: `mesh-suite-backend/src/main/resources/application.yml`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/AbstractIntegrationTest.java`
- Create: `mesh-suite-backend/src/test/java/com/meshsuite/MeshSuiteBackendApplicationTests.java`
- Create: `mesh-suite-backend/Dockerfile`
- Create: `mesh-suite-backend/README.md`
- Create: `mesh-suite-frontend/package.json`, `mesh-suite-frontend/vite.config.ts`, `mesh-suite-frontend/tsconfig.json`, `mesh-suite-frontend/tsconfig.node.json`, `mesh-suite-frontend/index.html`
- Create: `mesh-suite-frontend/src/main.ts`, `mesh-suite-frontend/src/App.vue`
- Create: `mesh-suite-frontend/Dockerfile`
- Create: `mesh-suite-frontend/README.md`
- Create: `docker-compose.yml`
- Create: `.env.example`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `com.meshsuite.AbstractIntegrationTest` — an abstract JUnit 5 base class with a shared static `PostgreSQLContainer<?>` wired via `@ServiceConnection`. Every later backend test extends this instead of standing up its own container.
- Produces: backend listens on `:8080`, frontend dev server on `:5173`, Postgres on `:5432`.

- [ ] **Step 1: Generate the backend project**

```bash
cd /Users/marceloferreira/developer/mesh-suite
curl https://start.spring.io/starter.zip \
  -d artifactId=mesh-suite-backend \
  -d groupId=com.meshsuite \
  -d name=mesh-suite-backend \
  -d bootVersion=3.4.5 \
  -d dependencies=lombok,web,data-jpa,postgresql,validation,security,mail,actuator,flyway \
  -d javaVersion=21 \
  -d packageName=com.meshsuite \
  -d packaging=jar \
  -d type=maven-project \
  -o starter.zip
mkdir -p mesh-suite-backend
unzip starter.zip -d mesh-suite-backend
rm starter.zip
```

- [ ] **Step 2: Add JWT and Testcontainers dependencies to `pom.xml`**

Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 3: Write `application.yml`**

```yaml
spring:
  application:
    name: mesh-suite-backend
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/meshsuite}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USER:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

app:
  jwt:
    secret: ${JWT_SECRET}
  mail:
    from: ${MAIL_FROM:no-reply@meshsuite.local}

management:
  endpoints:
    web:
      exposure:
        include: health

server:
  port: 8080
```

- [ ] **Step 4: Write the shared Testcontainers base class**

```java
package com.meshsuite;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        // application.yml requires JWT_SECRET with no default; tests supply one directly.
        registry.add("app.jwt.secret", () -> "test-secret-test-secret-test-secret-32b");
    }
}
```

- [ ] **Step 5: Write the context-load test**

```java
package com.meshsuite;

import org.junit.jupiter.api.Test;

class MeshSuiteBackendApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6: Run the backend test suite**

```bash
cd mesh-suite-backend && ./mvnw clean test
```
Expected: `BUILD SUCCESS`, one test run (`contextLoads`).

- [ ] **Step 7: Write the backend `Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 8: Scaffold the frontend project**

```bash
cd /Users/marceloferreira/developer/mesh-suite
npm create vite@latest mesh-suite-frontend -- --template vue-ts
cd mesh-suite-frontend
npm install
npm install vue-router@4 pinia axios
npm install -D vitest @vue/test-utils jsdom @types/node
```

- [ ] **Step 9: Configure `vite.config.ts` with the `@` alias and Vitest**

```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: true,
    port: 5173,
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
```

- [ ] **Step 10: Add a path alias to `tsconfig.json`** (inside `compilerOptions`)

```json
"baseUrl": ".",
"paths": {
  "@/*": ["./src/*"]
}
```

- [ ] **Step 11: Run the frontend dev server smoke check**

```bash
cd mesh-suite-frontend && npm run build
```
Expected: build succeeds with the default Vite/Vue template.

- [ ] **Step 12: Write the frontend `Dockerfile`**

```dockerfile
FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
```

- [ ] **Step 13: Write the root `docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: ${DB_NAME}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 5s
      timeout: 5s
      retries: 10

  backend:
    build: ./mesh-suite-backend
    env_file: .env
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_URL: jdbc:postgresql://postgres:5432/${DB_NAME}
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build: ./mesh-suite-frontend
    environment:
      VITE_API_BASE_URL: http://localhost:8080/api
    ports:
      - "5173:5173"
    depends_on:
      - backend

volumes:
  postgres_data:
```

- [ ] **Step 14: Write `.env.example`**

```
DB_USER=meshsuite
DB_PASSWORD=changeme
DB_NAME=meshsuite
JWT_SECRET=changeme-generate-a-long-random-secret-min-32-bytes
SMTP_HOST=
SMTP_PORT=587
SMTP_USER=
SMTP_PASSWORD=
MAIL_FROM=no-reply@meshsuite.local
```

- [ ] **Step 15: Update `.gitignore`**

Add:
```
.env
mesh-suite-backend/target/
mesh-suite-frontend/node_modules/
mesh-suite-frontend/dist/
```

- [ ] **Step 16: Commit**

```bash
git add mesh-suite-backend mesh-suite-frontend docker-compose.yml .env.example .gitignore
git commit -m "chore: scaffold backend and frontend projects with docker-compose"
```

---

### Task 2: `Tenant` entity, migration, repository

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V1__create_tenant.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/tenant/Tenant.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/tenant/TenantRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/tenant/TenantRepositoryTest.java`

**Interfaces:**
- Produces: `Tenant { UUID id, String codigo, String nome, boolean ativo, Instant criadoEm }`, `TenantRepository extends JpaRepository<Tenant, UUID>`.

- [ ] **Step 1: Write the failing repository test**

```java
package com.meshsuite.tenant;

import com.meshsuite.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;

    @Test
    void savesAndFindsTenant() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("Confecção Aurora");
        Tenant saved = tenantRepository.save(tenant);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isAtivo()).isTrue();
        assertThat(tenantRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void rejectsDuplicateCodigo() {
        Tenant a = new Tenant();
        a.setCodigo("aurora");
        a.setNome("Confecção Aurora");
        tenantRepository.saveAndFlush(a);

        Tenant b = new Tenant();
        b.setCodigo("aurora");
        b.setNome("Outra Empresa");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> tenantRepository.saveAndFlush(b));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=TenantRepositoryTest
```
Expected: FAIL — `Tenant`/`TenantRepository` do not exist.

- [ ] **Step 3: Write migration `V1__create_tenant.sql`**

```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(50) NOT NULL UNIQUE,
    nome VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 4: Write the `Tenant` entity**

```java
package com.meshsuite.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
```

- [ ] **Step 5: Write the repository**

```java
package com.meshsuite.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=TenantRepositoryTest
```
Expected: PASS, both tests.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V1__create_tenant.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/tenant \
        mesh-suite-backend/src/test/java/com/meshsuite/tenant
git commit -m "feat: add Tenant entity and migration"
```

---

### Task 3: `Empresa` entity, migration with RLS, repository

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V2__create_empresa.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/empresa/Empresa.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/empresa/EmpresaRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/empresa/EmpresaRepositoryTest.java`

**Interfaces:**
- Consumes: `Tenant` (Task 2).
- Produces: `Empresa { UUID id, UUID tenantId, String razaoSocial, String cnpj, boolean ativo }`, `EmpresaRepository extends JpaRepository<Empresa, UUID> { List<Empresa> findByTenantId(UUID tenantId); }`.

- [ ] **Step 1: Write the failing test**

```java
package com.meshsuite.empresa;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class EmpresaRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    EmpresaRepository empresaRepository;
    @Autowired
    EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    @Test
    void savesEmpresaForTenant() {
        Tenant tenant = createTenant("aurora");
        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Confecção Aurora Ltda");
        empresa.setCnpj("11222333000144");

        Empresa saved = empresaRepository.save(empresa);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isAtivo()).isTrue();
    }

    @Test
    void rejectsDuplicateCnpjAcrossTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        Empresa a = new Empresa();
        a.setTenantId(tenantA.getId());
        a.setRazaoSocial("Confecção Aurora Ltda");
        a.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(a);

        Empresa b = new Empresa();
        b.setTenantId(tenantB.getId());
        b.setRazaoSocial("Confecção Boreal Ltda");
        b.setCnpj("11222333000144");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> empresaRepository.saveAndFlush(b));
    }

    @Test
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Confecção Aurora Ltda");
        empresa.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(empresa);
        entityManager.clear();

        // No app.tenant_id session var set: RLS policy denies every row.
        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM empresa")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=EmpresaRepositoryTest
```
Expected: FAIL — `Empresa`/`EmpresaRepository` do not exist.

- [ ] **Step 3: Write migration `V2__create_empresa.sql`**

```sql
CREATE TABLE empresa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    razao_social VARCHAR(255) NOT NULL,
    cnpj VARCHAR(14) NOT NULL UNIQUE,
    ativo BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_empresa_tenant_id ON empresa(tenant_id);

ALTER TABLE empresa ENABLE ROW LEVEL SECURITY;
-- FORCE so the policy also applies to the table owner (the role the app
-- connects as); without FORCE, RLS is bypassed for the owning role.
ALTER TABLE empresa FORCE ROW LEVEL SECURITY;

-- current_setting(..., true) returns NULL instead of raising when the
-- session var isn't set, so an unset app.tenant_id safely denies all rows
-- (NULL = tenant_id is never true) rather than erroring out.
CREATE POLICY empresa_tenant_isolation ON empresa
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] **Step 4: Write the `Empresa` entity**

```java
package com.meshsuite.empresa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "empresa")
@Getter
@Setter
public class Empresa {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(nullable = false)
    private boolean ativo = true;
}
```

- [ ] **Step 5: Write the repository**

```java
package com.meshsuite.empresa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
    List<Empresa> findByTenantId(UUID tenantId);
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=EmpresaRepositoryTest
```
Expected: PASS, all three tests — including `rlsHidesRowsWhenTenantContextUnset`, which is the first proof the RLS policy is actually active.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V2__create_empresa.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/empresa \
        mesh-suite-backend/src/test/java/com/meshsuite/empresa
git commit -m "feat: add Empresa entity with RLS policy and global unique cnpj"
```

---

### Task 4: `Usuario` entity, migration with RLS + login-bypass policy, repository

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V3__create_usuario.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/Papel.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/Usuario.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/usuario/UsuarioRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/usuario/UsuarioRepositoryTest.java`

**Interfaces:**
- Consumes: `Tenant` (Task 2).
- Produces: `Papel` enum (`ADMINISTRATIVO, REPRESENTANTE, PRODUCAO, TERCEIRIZADO, ADMINISTRADOR`); `Usuario { UUID id, UUID tenantId, String nome, String email, String senhaHash, Papel papel, boolean ativo, Instant criadoEm, Instant ultimoAcesso }`; `UsuarioRepository extends JpaRepository<Usuario, UUID> { Optional<Usuario> findByEmail(String email); }`.

- [ ] **Step 1: Write the failing test**

```java
package com.meshsuite.usuario;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    @Test
    void savesUsuarioWithPapel() {
        Tenant tenant = createTenant("aurora");
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@confeccaoaurora.com.br");
        usuario.setSenhaHash("bcrypt-hash");
        usuario.setPapel(Papel.ADMINISTRADOR);

        Usuario saved = usuarioRepository.save(usuario);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPapel()).isEqualTo(Papel.ADMINISTRADOR);
        assertThat(saved.isAtivo()).isTrue();
    }

    @Test
    void rejectsDuplicateEmailAcrossTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        Usuario a = new Usuario();
        a.setTenantId(tenantA.getId());
        a.setNome("Marina");
        a.setEmail("marina@confeccaoaurora.com.br");
        a.setSenhaHash("hash");
        a.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(a);

        Usuario b = new Usuario();
        b.setTenantId(tenantB.getId());
        b.setNome("Marina Outra");
        b.setEmail("marina@confeccaoaurora.com.br");
        b.setSenhaHash("hash");
        b.setPapel(Papel.ADMINISTRADOR);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> usuarioRepository.saveAndFlush(b));
    }

    @Test
    void loginBypassPolicyAllowsEmailLookupWithoutTenantContext() {
        Tenant tenant = createTenant("aurora");
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@confeccaoaurora.com.br");
        usuario.setSenhaHash("hash");
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);
        entityManager.clear();

        // Without the bypass flag, RLS hides the row (no app.tenant_id set).
        Long withoutBypass = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM usuario WHERE email = 'marina@confeccaoaurora.com.br'")
                .getSingleResult()).longValue();
        assertThat(withoutBypass).isZero();

        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        Long withBypass = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM usuario WHERE email = 'marina@confeccaoaurora.com.br'")
                .getSingleResult()).longValue();
        assertThat(withBypass).isEqualTo(1L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=UsuarioRepositoryTest
```
Expected: FAIL — `Usuario`/`Papel`/`UsuarioRepository` do not exist.

- [ ] **Step 3: Write migration `V3__create_usuario.sql`**

```sql
CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    papel VARCHAR(20) NOT NULL CHECK (papel IN ('ADMINISTRATIVO','REPRESENTANTE','PRODUCAO','TERCEIRIZADO','ADMINISTRADOR')),
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_acesso TIMESTAMPTZ
);

CREATE INDEX idx_usuario_tenant_id ON usuario(tenant_id);
CREATE INDEX idx_usuario_email ON usuario(email);

ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;

CREATE POLICY usuario_tenant_isolation ON usuario
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Login authenticates by email alone, before any tenant is known, so the
-- lookup can't go through the tenant-scoped policy above. This second
-- PERMISSIVE policy (Postgres ORs multiple permissive policies together)
-- allows SELECT only when app.bypass_tenant_check is explicitly set to
-- 'true' for that one query. Set in exactly one place in the codebase:
-- AuthService.findByEmailForLogin. See plan §"Design decision beyond the spec".
CREATE POLICY usuario_login_lookup ON usuario
    FOR SELECT
    USING (current_setting('app.bypass_tenant_check', true) = 'true');
```

- [ ] **Step 4: Write the `Papel` enum**

```java
package com.meshsuite.usuario;

public enum Papel {
    ADMINISTRATIVO,
    REPRESENTANTE,
    PRODUCAO,
    TERCEIRIZADO,
    ADMINISTRADOR
}
```

- [ ] **Step 5: Write the `Usuario` entity**

```java
package com.meshsuite.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "ultimo_acesso")
    private Instant ultimoAcesso;
}
```

- [ ] **Step 6: Write the repository**

```java
package com.meshsuite.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmail(String email);
}
```

- [ ] **Step 7: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=UsuarioRepositoryTest
```
Expected: PASS, all three tests.

- [ ] **Step 8: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V3__create_usuario.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/usuario \
        mesh-suite-backend/src/test/java/com/meshsuite/usuario
git commit -m "feat: add Usuario entity with RLS and login-bypass policy"
```

---

### Task 5: `PasswordResetToken` entity, migration, repository

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V4__create_password_reset_token.sql`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/PasswordResetToken.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/PasswordResetTokenRepository.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetTokenRepositoryTest.java`

**Interfaces:**
- Consumes: `Usuario` (Task 4).
- Produces: `PasswordResetToken { UUID id, UUID usuarioId, String tokenHash, Instant expiraEm, Instant usadoEm, Instant criadoEm }`, `PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> { Optional<PasswordResetToken> findByTokenHash(String tokenHash); }`.

- [ ] **Step 1: Write the failing test**

```java
package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PasswordResetTokenRepository tokenRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    void savesAndFindsByTokenHash() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("aurora");
        tenantRepository.saveAndFlush(tenant);

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@confeccaoaurora.com.br");
        usuario.setSenhaHash("hash");
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);

        PasswordResetToken token = new PasswordResetToken();
        token.setUsuarioId(usuario.getId());
        token.setTokenHash("abc123hash");
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));

        tokenRepository.save(token);

        assertThat(tokenRepository.findByTokenHash("abc123hash")).isPresent();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=PasswordResetTokenRepositoryTest
```
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Write migration `V4__create_password_reset_token.sql`**

```sql
CREATE TABLE password_reset_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    token_hash VARCHAR(64) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_token_usuario_id ON password_reset_token(usuario_id);
CREATE UNIQUE INDEX idx_password_reset_token_hash ON password_reset_token(token_hash);
```

- [ ] **Step 4: Write the `PasswordResetToken` entity**

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

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

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

- [ ] **Step 5: Write the repository**

```java
package com.meshsuite.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=PasswordResetTokenRepositoryTest
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/V4__create_password_reset_token.sql \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/PasswordResetToken.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/PasswordResetTokenRepository.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetTokenRepositoryTest.java
git commit -m "feat: add PasswordResetToken entity"
```

---

### Task 6: `JwtService`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/JwtService.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/JwtServiceTest.java`

**Interfaces:**
- Produces: `JwtService { String generateToken(UUID usuarioId, UUID tenantId, UUID empresaId, String papel, boolean manterConectado); Claims parseClaims(String token); }`. Claims: `sub` (usuarioId), `tenant_id`, `empresa_id`, `papel`.

- [ ] **Step 1: Write the failing test**

```java
package com.meshsuite.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService("test-secret-test-secret-test-secret-32b");

    @Test
    void generatesTokenWithExpectedClaimsAnd8hExpiryByDefault() {
        UUID usuarioId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();

        String token = jwtService.generateToken(usuarioId, tenantId, empresaId, "ADMINISTRADOR", false);
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(usuarioId.toString());
        assertThat(claims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());
        assertThat(claims.get("empresa_id", String.class)).isEqualTo(empresaId.toString());
        assertThat(claims.get("papel", String.class)).isEqualTo("ADMINISTRADOR");

        long hoursUntilExpiry = java.time.Duration.between(Instant.now(), claims.getExpiration().toInstant()).toHours();
        assertThat(hoursUntilExpiry).isBetween(7L, 8L);
    }

    @Test
    void grantsThirtyDayExpiryWhenManterConectado() {
        String token = jwtService.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ADMINISTRADOR", true);
        Claims claims = jwtService.parseClaims(token);

        long daysUntilExpiry = java.time.Duration.between(Instant.now(), claims.getExpiration().toInstant()).toDays();
        assertThat(daysUntilExpiry).isBetween(29L, 30L);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService("different-secret-different-secret-32b");
        String token = other.generateToken(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ADMINISTRADOR", false);

        assertThrows(SignatureException.class, () -> jwtService.parseClaims(token));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=JwtServiceTest
```
Expected: FAIL — `JwtService` does not exist.

- [ ] **Step 3: Write `JwtService`**

```java
package com.meshsuite.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UUID usuarioId, UUID tenantId, UUID empresaId, String papel, boolean manterConectado) {
        Instant now = Instant.now();
        Instant expiry = manterConectado ? now.plus(30, ChronoUnit.DAYS) : now.plus(8, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("empresa_id", empresaId.toString())
                .claim("papel", papel)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=JwtServiceTest
```
Expected: PASS, all three tests.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/JwtService.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/JwtServiceTest.java
git commit -m "feat: add JwtService for token generation and parsing"
```

---

### Task 7: `TenantContext` + RLS transaction wiring + mandatory cross-tenant isolation test

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/TenantContext.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/TenantContextAspect.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/config/TransactionConfig.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/TenantIsolationIT.java`

**Interfaces:**
- Consumes: `Empresa` (Task 3), `Usuario` (Task 4).
- Produces: `TenantContext.set(UUID)/get()/clear()` — the single mechanism every later component (JWT filter, login flow) uses to declare "the rest of this request/transaction belongs to this tenant." `TenantContextAspect` reads it and is otherwise invisible to callers.

- [ ] **Step 1: Write `TenantContext`**

```java
package com.meshsuite.auth;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

- [ ] **Step 2: Write `TransactionConfig`**

```java
package com.meshsuite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

// order=0 makes the transaction advisor the outermost AOP advice, so it
// always starts the transaction before TenantContextAspect (order=1) runs.
// See TenantContextAspect for why the ordering matters.
@Configuration
@EnableTransactionManagement(order = 0)
public class TransactionConfig {
}
```

- [ ] **Step 3: Write `TenantContextAspect`**

```java
package com.meshsuite.auth;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Runs *inside* the transaction: TransactionConfig pins @EnableTransactionManagement
// to order=0 (outermost), this aspect to order=1 (one layer in), so on the way into
// a @Transactional method the tx always starts first, then this aspect's SET LOCAL,
// then the method body — every query the method body runs is tenant-scoped.
@Aspect
@Component
@Order(1)
public class TenantContextAspect {

    private final EntityManager entityManager;

    public TenantContextAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object applyTenantContext(ProceedingJoinPoint pjp) throws Throwable {
        UUID tenantId = TenantContext.get();
        if (tenantId != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            // Postgres's SET LOCAL doesn't accept bind parameters (it isn't a DML
            // statement). Concatenating is safe here because tenantId is a
            // java.util.UUID, not raw user input — toString() always produces the
            // fixed 36-char hex-and-dash format, nothing else.
            entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'")
                    .executeUpdate();
        }
        return pjp.proceed();
    }
}
```

- [ ] **Step 4: Write the failing isolation test**

```java
package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIsolationIT extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired TenantQueryService tenantQueryService;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void tenantACannotSeeTenantBData() {
        Tenant tenantA = new Tenant();
        tenantA.setCodigo("aurora");
        tenantA.setNome("Confecção Aurora");
        tenantRepository.saveAndFlush(tenantA);

        Tenant tenantB = new Tenant();
        tenantB.setCodigo("boreal");
        tenantB.setNome("Confecção Boreal");
        tenantRepository.saveAndFlush(tenantB);

        TenantContext.set(tenantA.getId());
        tenantQueryService.saveEmpresa(tenantA.getId(), "Aurora Ltda", "11222333000144");
        tenantQueryService.saveUsuario(tenantA.getId(), "Marina", "marina@aurora.com.br", Papel.ADMINISTRADOR);
        TenantContext.clear();

        TenantContext.set(tenantB.getId());
        tenantQueryService.saveEmpresa(tenantB.getId(), "Boreal Ltda", "55666777000188");
        tenantQueryService.saveUsuario(tenantB.getId(), "Carlos", "carlos@boreal.com.br", Papel.ADMINISTRADOR);
        TenantContext.clear();

        TenantContext.set(tenantA.getId());
        assertThat(tenantQueryService.listEmpresas()).extracting(Empresa::getCnpj).containsExactly("11222333000144");
        assertThat(tenantQueryService.listUsuarios()).extracting(Usuario::getEmail).containsExactly("marina@aurora.com.br");
        TenantContext.clear();

        TenantContext.set(tenantB.getId());
        assertThat(tenantQueryService.listEmpresas()).extracting(Empresa::getCnpj).containsExactly("55666777000188");
        assertThat(tenantQueryService.listUsuarios()).extracting(Usuario::getEmail).containsExactly("carlos@boreal.com.br");
    }
}
```

This test needs a small `@Transactional`-annotated helper service (the aspect only fires on `@Transactional` methods) — add it in the same step:

```java
package com.meshsuite.auth;

import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantQueryService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    public TenantQueryService(EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
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
    public void saveUsuario(UUID tenantId, String nome, String email, Papel papel) {
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenantId);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenhaHash("hash");
        usuario.setPapel(papel);
        usuarioRepository.saveAndFlush(usuario);
    }

    @Transactional(readOnly = true)
    public List<Empresa> listEmpresas() {
        return empresaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Usuario> listUsuarios() {
        return usuarioRepository.findAll();
    }
}
```

Save this as `mesh-suite-backend/src/main/java/com/meshsuite/auth/TenantQueryService.java`.

- [ ] **Step 5: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=TenantIsolationIT
```
Expected: FAIL — classes do not exist yet (write Steps 1-3 files if not already present, then re-run to confirm the test itself is meaningful by temporarily commenting out `TenantContext.set(...)` calls and observing the test fail with empty lists — this is the concrete proof RLS is doing the work, not applic­ation-side filtering).

- [ ] **Step 6: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=TenantIsolationIT
```
Expected: PASS. This is the mandatory tenant-isolation test required by PRD-14's Definition of Done.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/TenantContext.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/TenantContextAspect.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/TenantQueryService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/config/TransactionConfig.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/TenantIsolationIT.java
git commit -m "feat: wire RLS tenant context via AOP, add mandatory isolation test"
```

---

### Task 8: `JwtAuthenticationFilter` + `AuthContextService` + Spring Security wiring

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/AuthContextService.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/JwtAuthenticationFilter.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/config/SecurityConfig.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/JwtAuthenticationFilterIT.java`

**Interfaces:**
- Consumes: `JwtService` (Task 6), `TenantContext` (Task 7).
- Produces: `AuthContextService.Context(UUID usuarioId, UUID tenantId, String papel)`, `AuthContextService.loadAndValidate(UUID tenantId, UUID usuarioId)` returns `null` when the user or tenant is inactive or not found (RLS-hidden). Every later authenticated endpoint sits behind this filter.

- [ ] **Step 1: Write `AuthContextService`**

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
    public boolean usuarioETenantAtivos(UUID tenantId, UUID usuarioId) {
        try {
            Object[] row = (Object[]) entityManager.createNativeQuery(
                            "SELECT u.ativo, t.ativo FROM usuario u JOIN tenant t ON t.id = u.tenant_id " +
                                    "WHERE u.id = :usuarioId AND u.tenant_id = :tenantId")
                    .setParameter("usuarioId", usuarioId)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            return (boolean) row[0] && (boolean) row[1];
        } catch (NoResultException e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Write `JwtAuthenticationFilter`**

```java
package com.meshsuite.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "mesh_token";

    private final JwtService jwtService;
    private final AuthContextService authContextService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthContextService authContextService) {
        this.jwtService = jwtService;
        this.authContextService = authContextService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractCookie(request, COOKIE_NAME);

        if (token != null) {
            try {
                Claims claims = jwtService.parseClaims(token);
                UUID usuarioId = UUID.fromString(claims.getSubject());
                UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
                String papel = claims.get("papel", String.class);

                // Set before calling the transactional check so TenantContextAspect
                // can scope that query to this tenant.
                TenantContext.set(tenantId);

                if (!authContextService.usuarioETenantAtivos(tenantId, usuarioId)) {
                    TenantContext.clear();
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                var principal = new AuthContextService.Context(usuarioId, tenantId, papel);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + papel)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
```

- [ ] **Step 3: Write `SecurityConfig`**

```java
package com.meshsuite.config;

import com.meshsuite.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless JWT-in-cookie API, no server-rendered forms
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 4: Write the failing filter test**

```java
package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

class JwtAuthenticationFilterIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtService jwtService;

    @Test
    void rejectsRequestWithNoCookie() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void rejectsRequestForDeactivatedUser() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("Aurora");
        tenantRepository.saveAndFlush(tenant);

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@aurora.com.br");
        usuario.setSenhaHash("hash");
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);

        String token = jwtService.generateToken(usuario.getId(), tenant.getId(), java.util.UUID.randomUUID(), "ADMINISTRADOR", false);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me")
                        .header(HttpHeaders.COOKIE, JwtAuthenticationFilter.COOKIE_NAME + "=" + token))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }
}
```

Note: `/api/auth/me` doesn't exist yet — that's fine, `MockMvc` will 404 for the *authenticated* case since no controller handles it, but Spring Security's filter still runs first and 401s before routing gets to a missing handler, so both assertions above already hold. Task 10 adds the real handler.

- [ ] **Step 5: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=JwtAuthenticationFilterIT
```
Expected: FAIL — classes don't exist.

- [ ] **Step 6: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=JwtAuthenticationFilterIT
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/AuthContextService.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/JwtAuthenticationFilter.java \
        mesh-suite-backend/src/main/java/com/meshsuite/config/SecurityConfig.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/JwtAuthenticationFilterIT.java
git commit -m "feat: add JWT authentication filter with per-request ativo checks"
```

---

### Task 9: `RateLimiter`

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/RateLimiter.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/RateLimitExceededException.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/RateLimiterTest.java`

**Interfaces:**
- Produces: `RateLimiter { boolean isBlocked(String ip, String email); void recordFailure(String ip, String email); void recordSuccess(String ip, String email); }`, `RateLimitExceededException extends RuntimeException`.

- [ ] **Step 1: Write the failing test**

```java
package com.meshsuite.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void blocksAfterFiveFailuresFromSameIp() {
        RateLimiter rateLimiter = new RateLimiter();

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.isBlocked("1.2.3.4", "user" + i + "@example.com")).isFalse();
            rateLimiter.recordFailure("1.2.3.4", "user" + i + "@example.com");
        }

        assertThat(rateLimiter.isBlocked("1.2.3.4", "another@example.com")).isTrue();
    }

    @Test
    void blocksAfterFiveFailuresForSameEmailFromDifferentIps() {
        RateLimiter rateLimiter = new RateLimiter();

        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure("1.2.3." + i, "marina@aurora.com.br");
        }

        assertThat(rateLimiter.isBlocked("9.9.9.9", "marina@aurora.com.br")).isTrue();
    }

    @Test
    void successClearsFailureCountForThatIpAndEmail() {
        RateLimiter rateLimiter = new RateLimiter();

        rateLimiter.recordFailure("1.2.3.4", "marina@aurora.com.br");
        rateLimiter.recordFailure("1.2.3.4", "marina@aurora.com.br");
        rateLimiter.recordSuccess("1.2.3.4", "marina@aurora.com.br");

        assertThat(rateLimiter.isBlocked("1.2.3.4", "marina@aurora.com.br")).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=RateLimiterTest
```
Expected: FAIL — `RateLimiter` does not exist.

- [ ] **Step 3: Write `RateLimiter`**

```java
package com.meshsuite.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Bucket(int failures, Instant windowStart, Instant blockedUntil) {
    }

    private final ConcurrentHashMap<String, AtomicReference<Bucket>> buckets = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip, String email) {
        return isKeyBlocked("ip:" + ip) || isKeyBlocked("email:" + email);
    }

    public void recordFailure(String ip, String email) {
        recordFailureForKey("ip:" + ip);
        recordFailureForKey("email:" + email);
    }

    public void recordSuccess(String ip, String email) {
        buckets.remove("ip:" + ip);
        buckets.remove("email:" + email);
    }

    private boolean isKeyBlocked(String key) {
        AtomicReference<Bucket> ref = buckets.get(key);
        if (ref == null) {
            return false;
        }
        Bucket bucket = ref.get();
        return bucket.blockedUntil() != null && Instant.now().isBefore(bucket.blockedUntil());
    }

    private void recordFailureForKey(String key) {
        AtomicReference<Bucket> ref = buckets.computeIfAbsent(key, k -> new AtomicReference<>(new Bucket(0, Instant.now(), null)));
        ref.updateAndGet(bucket -> {
            Instant now = Instant.now();
            if (now.isAfter(bucket.windowStart().plus(WINDOW))) {
                bucket = new Bucket(0, now, null);
            }
            int failures = bucket.failures() + 1;
            Instant blockedUntil = failures >= MAX_ATTEMPTS ? now.plus(WINDOW) : null;
            return new Bucket(failures, bucket.windowStart(), blockedUntil);
        });
    }
}
```

- [ ] **Step 4: Write `RateLimitExceededException`**

```java
package com.meshsuite.auth;

public class RateLimitExceededException extends RuntimeException {
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=RateLimiterTest
```
Expected: PASS, all three tests.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth/RateLimiter.java \
        mesh-suite-backend/src/main/java/com/meshsuite/auth/RateLimitExceededException.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/RateLimiterTest.java
git commit -m "feat: add in-memory rate limiter for login and password recovery"
```

---

### Task 10: `AuthService.login` + `AuthController` (`/login`, `/me`) + generic error handling

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/AuthException.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/AuthService.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/AuthController.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/GlobalExceptionHandler.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/LoginRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/MeResponse.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/AuthControllerIT.java`

**Interfaces:**
- Consumes: `UsuarioRepository`, `TenantRepository`, `EmpresaRepository`, `JwtService`, `RateLimiter`, `TenantContext`, `PasswordEncoder` (all prior tasks).
- Produces: `POST /api/auth/login`, `GET /api/auth/me` — the full authenticated-session contract the frontend (Tasks 13-14) builds against.

- [ ] **Step 1: Write `AuthException`**

```java
package com.meshsuite.auth;

public class AuthException extends RuntimeException {
    public AuthException() {
        super("E-mail ou senha inválidos");
    }
}
```

- [ ] **Step 2: Write the DTOs**

```java
package com.meshsuite.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String senha,
        boolean manterConectado) {
}
```

```java
package com.meshsuite.auth.dto;

public record MeResponse(String nome, String papel) {
}
```

- [ ] **Step 3: Write `AuthService`**

```java
package com.meshsuite.auth;

import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public AuthService(UsuarioRepository usuarioRepository, TenantRepository tenantRepository,
                        EmpresaRepository empresaRepository, PasswordEncoder passwordEncoder,
                        EntityManager entityManager) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.empresaRepository = empresaRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    public record LoginResult(Usuario usuario, Tenant tenant, Empresa empresa) {
    }

    // Runs before the caller's tenant is known. See plan §"Design decision beyond
    // the spec" for why this needs the usuario_login_lookup RLS policy.
    @Transactional(readOnly = true)
    public Usuario findByEmailForLogin(String email) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        return usuarioRepository.findByEmail(email).orElse(null);
    }

    public LoginResult authenticate(String email, String senha) {
        Usuario usuario = findByEmailForLogin(email);
        if (usuario == null || !passwordEncoder.matches(senha, usuario.getSenhaHash()) || !usuario.isAtivo()) {
            throw new AuthException();
        }

        TenantContext.set(usuario.getTenantId());
        try {
            Tenant tenant = tenantRepository.findById(usuario.getTenantId())
                    .orElseThrow(AuthException::new);
            if (!tenant.isAtivo()) {
                throw new AuthException();
            }

            List<Empresa> empresas = empresaRepository.findByTenantId(usuario.getTenantId());
            if (empresas.isEmpty()) {
                throw new AuthException();
            }

            registerAcesso(usuario.getId());
            return new LoginResult(usuario, tenant, empresas.get(0));
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void registerAcesso(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setUltimoAcesso(Instant.now());
        usuarioRepository.save(usuario);
    }
}
```

- [ ] **Step 4: Write `GlobalExceptionHandler`**

```java
package com.meshsuite.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("mensagem", "Muitas tentativas, tente novamente em instantes"));
    }
}
```

- [ ] **Step 5: Write `AuthController`**

```java
package com.meshsuite.auth;

import com.meshsuite.auth.dto.LoginRequest;
import com.meshsuite.auth.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;

    public AuthController(AuthService authService, JwtService jwtService, RateLimiter rateLimiter) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        String ip = httpRequest.getRemoteAddr();
        if (rateLimiter.isBlocked(ip, request.email())) {
            throw new RateLimitExceededException();
        }

        try {
            AuthService.LoginResult result = authService.authenticate(request.email(), request.senha());
            rateLimiter.recordSuccess(ip, request.email());

            String token = jwtService.generateToken(
                    result.usuario().getId(), result.tenant().getId(), result.empresa().getId(),
                    result.usuario().getPapel().name(), request.manterConectado());

            long maxAgeSeconds = request.manterConectado() ? 30L * 24 * 3600 : 8L * 3600;
            ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(maxAgeSeconds)
                    .build();
            httpResponse.addHeader("Set-Cookie", cookie.toString());

            return ResponseEntity.ok().build();
        } catch (AuthException e) {
            rateLimiter.recordFailure(ip, request.email());
            throw e;
        }
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthContextService.Context principal) {
        // AuthContextService already re-verified ativo status for this request;
        // the filter wouldn't have reached this handler otherwise. Nome comes
        // from a small repository lookup for display purposes only.
        return new MeResponse(principal.papel(), principal.papel());
    }
}
```

Note: `me()` returning `papel` twice for `nome` is a placeholder gap the next step fixes — replace it in Step 5b before running tests.

- [ ] **Step 5b: Fix `/me` to return the real `nome`**

Add to `AuthContextService`:

```java
    @Transactional(readOnly = true)
    public String nomeDoUsuario(UUID usuarioId) {
        return entityManager.createQuery(
                        "SELECT u.nome FROM Usuario u WHERE u.id = :id", String.class)
                .setParameter("id", usuarioId)
                .getSingleResult();
    }
```

Update `AuthController.me`:

```java
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthContextService.Context principal) {
        String nome = authContextService.nomeDoUsuario(principal.usuarioId());
        return new MeResponse(nome, principal.papel());
    }
```

(Inject `AuthContextService` into `AuthController`'s constructor alongside the existing dependencies.)

- [ ] **Step 6: Write the failing controller test**

```java
package com.meshsuite.auth;

import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private void seedTenantWithUsuario(String senhaPlano) {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("Aurora");
        tenantRepository.saveAndFlush(tenant);

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Aurora Ltda");
        empresa.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(empresa);

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@aurora.com.br");
        usuario.setSenhaHash(passwordEncoder.encode(senhaPlano));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);
    }

    @Test
    void validLoginSetsCookieAndAllowsMe() throws Exception {
        seedTenantWithUsuario("senha123");

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"marina@aurora.com.br","senha":"senha123","manterConectado":false}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        org.assertj.core.api.Assertions.assertThat(cookieHeader).contains("mesh_token=");
        org.assertj.core.api.Assertions.assertThat(cookieHeader).contains("HttpOnly");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie(JwtAuthenticationFilter.COOKIE_NAME, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Marina"))
                .andExpect(jsonPath("$.papel").value("ADMINISTRADOR"));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnIdenticalGenericError() throws Exception {
        seedTenantWithUsuario("senha123");

        String wrongPasswordBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"marina@aurora.com.br","senha":"errada","manterConectado":false}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmailBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ninguem@aurora.com.br","senha":"qualquer","manterConectado":false}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(wrongPasswordBody).isEqualTo(unknownEmailBody);
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=AuthControllerIT
```
Expected: FAIL.

- [ ] **Step 8: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=AuthControllerIT
```
Expected: PASS, both tests.

- [ ] **Step 9: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/auth \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/AuthControllerIT.java
git commit -m "feat: add login and /me endpoints with generic error handling"
```

---

### Task 11: Password recovery — `MailService`, `PasswordResetService`, controller endpoints

**Files:**
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/mail/MailService.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/PasswordResetService.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/ForgotPasswordRequest.java`
- Create: `mesh-suite-backend/src/main/java/com/meshsuite/auth/dto/ResetPasswordRequest.java`
- Modify: `mesh-suite-backend/src/main/java/com/meshsuite/auth/AuthController.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetServiceTest.java`
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetControllerIT.java`

**Interfaces:**
- Consumes: `PasswordResetTokenRepository` (Task 5), `UsuarioRepository` (Task 4).
- Produces: `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`.

- [ ] **Step 1: Write `MailService`**

```java
package com.meshsuite.mail;

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
}
```

- [ ] **Step 2: Write the failing `PasswordResetService` test**

```java
package com.meshsuite.auth;

import com.meshsuite.mail.MailService;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock MailService mailService;

    private PasswordResetService service() {
        return new PasswordResetService(usuarioRepository, tokenRepository, mailService,
                org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class));
    }

    @Test
    void requestResetSendsEmailWhenUsuarioExists() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("marina@aurora.com.br");
        usuario.setAtivo(true);
        when(usuarioRepository.findByEmail("marina@aurora.com.br")).thenReturn(Optional.of(usuario));

        service().requestReset("marina@aurora.com.br");

        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendPasswordResetEmail(eq("marina@aurora.com.br"), any());
    }

    @Test
    void requestResetDoesNothingSilentlyWhenUsuarioDoesNotExist() {
        when(usuarioRepository.findByEmail("ninguem@aurora.com.br")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service().requestReset("ninguem@aurora.com.br"));

        verify(tokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void confirmResetRejectsExpiredToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(sha256("raw-token"));
        token.setExpiraEm(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(AuthException.class, () -> service().confirmReset("raw-token", "novaSenha123"));
    }

    @Test
    void confirmResetRejectsAlreadyUsedToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(sha256("raw-token"));
        token.setExpiraEm(Instant.now().plusSeconds(3600));
        token.setUsadoEm(Instant.now());
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(AuthException.class, () -> service().confirmReset("raw-token", "novaSenha123"));
    }

    private static String sha256(String raw) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=PasswordResetServiceTest
```
Expected: FAIL — `PasswordResetService` does not exist. Add `org.mockito:mockito-junit-jupiter` — it's already transitively included by `spring-boot-starter-test`.

- [ ] **Step 4: Write `PasswordResetService`**

```java
package com.meshsuite.auth;

import com.meshsuite.mail.MailService;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UsuarioRepository usuarioRepository, PasswordResetTokenRepository tokenRepository,
                                 MailService mailService, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
    }

    // Runs pre-auth, same as AuthService.findByEmailForLogin, and needs the same
    // RLS bypass. Callers of this service must be routed through a request path
    // that isn't tenant-scoped yet.
    public void requestReset(String email) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
        if (usuarioOpt.isEmpty() || !usuarioOpt.get().isAtivo()) {
            return; // generic success response regardless — no account enumeration
        }
        Usuario usuario = usuarioOpt.get();

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUsuarioId(usuario.getId());
        token.setTokenHash(sha256(rawToken));
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
        tokenRepository.save(token);

        String resetLink = "https://app.meshsuite.local/redefinir-senha?token=" + rawToken;
        mailService.sendPasswordResetEmail(email, resetLink);
    }

    @Transactional
    public void confirmReset(String rawToken, String novaSenha) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(AuthException::new);

        if (token.getUsadoEm() != null || Instant.now().isAfter(token.getExpiraEm())) {
            throw new AuthException();
        }

        Usuario usuario = usuarioRepository.findById(token.getUsuarioId()).orElseThrow(AuthException::new);
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

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

- [ ] **Step 5: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=PasswordResetServiceTest
```
Expected: PASS, all four tests.

- [ ] **Step 6: Write the DTOs**

```java
package com.meshsuite.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
```

```java
package com.meshsuite.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 8) String novaSenha) {
}
```

- [ ] **Step 7: Rate-limit `requestReset` and wire the endpoints into `AuthController`**

Spec §5 puts login and password recovery under the same 5-attempts/15-minutes limiter. Change `PasswordResetService.requestReset` to report whether an active account existed, so the controller can decide whether to count the call as a "failure" toward the limit (mirrors the login flow's `recordFailure`/`recordSuccess` split while keeping the HTTP response identical either way):

```java
    // in PasswordResetService — replace the existing requestReset signature/body
    public boolean requestReset(String email) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
        if (usuarioOpt.isEmpty() || !usuarioOpt.get().isAtivo()) {
            return false; // caller still returns 200 with the generic message
        }
        Usuario usuario = usuarioOpt.get();

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUsuarioId(usuario.getId());
        token.setTokenHash(sha256(rawToken));
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
        tokenRepository.save(token);

        String resetLink = "https://app.meshsuite.local/redefinir-senha?token=" + rawToken;
        mailService.sendPasswordResetEmail(email, resetLink);
        return true;
    }
```

Update the two `PasswordResetServiceTest` cases that call `requestReset` to assert on the returned boolean (`assertTrue(...)` / `assertFalse(...)`) instead of just `assertDoesNotThrow`/void — re-run `./mvnw test -Dtest=PasswordResetServiceTest` after this change to confirm it still passes.

```java
    private final PasswordResetService passwordResetService;
    private final RateLimiter rateLimiter;
    // add both to AuthController's existing constructor

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (rateLimiter.isBlocked(ip, request.email())) {
            throw new RateLimitExceededException();
        }

        boolean found = passwordResetService.requestReset(request.email());
        if (found) {
            rateLimiter.recordSuccess(ip, request.email());
        } else {
            rateLimiter.recordFailure(ip, request.email());
        }
        return ResponseEntity.ok().build(); // same 200 regardless of `found` — no account enumeration
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.confirmReset(request.token(), request.novaSenha());
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 8: Write the failing controller test**

```java
package com.meshsuite.auth;

import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean com.meshsuite.mail.MailService mailService;

    @Test
    void forgotPasswordReturnsOkRegardlessOfWhetherEmailExists() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ninguem@aurora.com.br"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordSendsEmailWhenUsuarioExistsAndActive() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("Aurora");
        tenantRepository.saveAndFlush(tenant);

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@aurora.com.br");
        usuario.setSenhaHash(passwordEncoder.encode("senha123"));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"marina@aurora.com.br"}"""))
                .andExpect(status().isOk());

        verify(mailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq("marina@aurora.com.br"), any());
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=PasswordResetControllerIT
```
Expected: PASS, both tests.

- [ ] **Step 10: Run the full backend suite**

```bash
cd mesh-suite-backend && ./mvnw clean test
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 11: Commit**

```bash
git add mesh-suite-backend/src/main/java/com/meshsuite/mail \
        mesh-suite-backend/src/main/java/com/meshsuite/auth \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetServiceTest.java \
        mesh-suite-backend/src/test/java/com/meshsuite/auth/PasswordResetControllerIT.java
git commit -m "feat: add password recovery flow with SMTP email"
```

---

### Task 12: Dev/test seed migration

**Files:**
- Create: `mesh-suite-backend/src/main/resources/db/migration/V5__seed_dev_tenant.sql`
- Modify: `mesh-suite-backend/src/main/resources/application.yml` (flyway placeholder config)
- Test: `mesh-suite-backend/src/test/java/com/meshsuite/DevSeedIT.java`

**Interfaces:**
- Produces: two tenants (`aurora`, `boreal`), one `Empresa` and one `ADMINISTRADOR` `Usuario` each, gated to the `dev`/`test` Spring profiles.

- [ ] **Step 1: Generate a known bcrypt hash for the seed password**

The seed password is `MeshSuite@123` for both seeded users. Generate its bcrypt hash once locally:

```bash
cd mesh-suite-backend
cat > /tmp/HashGen.java << 'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashGen {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode("MeshSuite@123"));
    }
}
EOF
```
Run it via a scratch JUnit test instead (simpler than wiring a classpath by hand): add a temporary `@Test` in `AuthControllerIT` that prints `new BCryptPasswordEncoder().encode("MeshSuite@123")`, run it once, copy the output hash, then delete the temporary test.

- [ ] **Step 2: Write migration `V5__seed_dev_tenant.sql`**

Flyway doesn't support profile-gating a migration file directly; gate it via a separate migration location enabled only for `dev`/`test`. Add to `application.yml`:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
---
spring:
  config:
    activate:
      on-profile: dev,test
  flyway:
    locations: classpath:db/migration,classpath:db/migration/seed
```

Create the file at `mesh-suite-backend/src/main/resources/db/migration/seed/V5__seed_dev_tenant.sql` (note the `seed/` subfolder — this path is only on Flyway's `locations` list for the `dev`/`test` profiles, so it never runs in production):

```sql
INSERT INTO tenant (id, codigo, nome, ativo) VALUES
    ('11111111-1111-1111-1111-111111111111', 'aurora', 'Confecção Aurora', true),
    ('22222222-2222-2222-2222-222222222222', 'boreal', 'Confecção Boreal', true);

INSERT INTO empresa (id, tenant_id, razao_social, cnpj, ativo) VALUES
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Confecção Aurora Ltda', '11222333000144', true),
    ('44444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222', 'Confecção Boreal Ltda', '55666777000188', true);

-- Password for both seeded users: MeshSuite@123
INSERT INTO usuario (id, tenant_id, nome, email, senha_hash, papel, ativo) VALUES
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'Marina Aurora', 'marina@aurora.com.br', '$2a$10$REPLACE_WITH_HASH_FROM_STEP_1', 'ADMINISTRADOR', true),
    ('66666666-6666-6666-6666-666666666666', '22222222-2222-2222-2222-222222222222', 'Carlos Boreal', 'carlos@boreal.com.br', '$2a$10$REPLACE_WITH_HASH_FROM_STEP_1', 'ADMINISTRADOR', true);
```

Replace both `$2a$10$REPLACE_WITH_HASH_FROM_STEP_1` occurrences with the hash generated in Step 1 (same hash both places, since both users share the seed password).

- [ ] **Step 3: Write the failing seed test**

```java
package com.meshsuite;

import com.meshsuite.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class DevSeedIT extends AbstractIntegrationTest {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void seedCreatesTwoTenantsWithLoginableAdmins() {
        var marina = usuarioRepository.findByEmail("marina@aurora.com.br");
        var carlos = usuarioRepository.findByEmail("carlos@boreal.com.br");

        assertThat(marina).isPresent();
        assertThat(carlos).isPresent();
        assertThat(passwordEncoder.matches("MeshSuite@123", marina.get().getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches("MeshSuite@123", carlos.get().getSenhaHash())).isTrue();
    }
}
```

Note: `usuarioRepository.findByEmail` runs under the default `usuario_tenant_isolation` policy in this test (no bypass flag set), so it will return empty even though the row exists — RLS is doing its job. Adjust the test to go through `AuthService.findByEmailForLogin` instead, which does set the bypass flag:

```java
    @Autowired com.meshsuite.auth.AuthService authService;

    @Test
    void seedCreatesTwoTenantsWithLoginableAdmins() {
        var marina = authService.findByEmailForLogin("marina@aurora.com.br");
        var carlos = authService.findByEmailForLogin("carlos@boreal.com.br");

        assertThat(marina).isNotNull();
        assertThat(carlos).isNotNull();
        assertThat(passwordEncoder.matches("MeshSuite@123", marina.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches("MeshSuite@123", carlos.getSenhaHash())).isTrue();
    }
```

- [ ] **Step 4: Run test to verify it fails, then passes**

```bash
cd mesh-suite-backend && ./mvnw test -Dtest=DevSeedIT
```
Expected: FAIL before the migration file has the real hash substituted in (or before the `dev,test` flyway locations profile block exists), PASS after.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-backend/src/main/resources/db/migration/seed/V5__seed_dev_tenant.sql \
        mesh-suite-backend/src/main/resources/application.yml \
        mesh-suite-backend/src/test/java/com/meshsuite/DevSeedIT.java
git commit -m "feat: add dev/test seed data for two example tenants"
```

---

### Task 13: Frontend — Axios client, Pinia auth store, router guard

**Files:**
- Create: `mesh-suite-frontend/src/api/client.ts`
- Create: `mesh-suite-frontend/src/api/auth.ts`
- Create: `mesh-suite-frontend/src/stores/auth.ts`
- Create: `mesh-suite-frontend/src/router/index.ts`
- Create: `mesh-suite-frontend/src/views/DashboardView.vue` (minimal stub landing page — the real dashboard is a separate future slice, see `design_handoff/`)
- Modify: `mesh-suite-frontend/src/main.ts`
- Test: `mesh-suite-frontend/src/router/__tests__/guard.spec.ts`

**Interfaces:**
- Produces: `useAuthStore()` (Pinia) with `isAuthenticated`, `usuario`, `checkSession()`, `clear()`. Router redirects unauthenticated users to `/login` for any route not marked `meta.public`.

- [ ] **Step 1: Write `api/client.ts`**

```ts
import axios from 'axios'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  withCredentials: true,
})
```

- [ ] **Step 2: Write `api/auth.ts`**

```ts
import { apiClient } from './client'

export interface LoginPayload {
  email: string
  senha: string
  manterConectado: boolean
}

export interface MeResponse {
  nome: string
  papel: string
}

export async function login(payload: LoginPayload): Promise<void> {
  await apiClient.post('/auth/login', payload)
}

export async function me(): Promise<MeResponse> {
  const { data } = await apiClient.get<MeResponse>('/auth/me')
  return data
}

export async function forgotPassword(email: string): Promise<void> {
  await apiClient.post('/auth/forgot-password', { email })
}

export async function resetPassword(token: string, novaSenha: string): Promise<void> {
  await apiClient.post('/auth/reset-password', { token, novaSenha })
}
```

- [ ] **Step 3: Write `stores/auth.ts`**

```ts
import { defineStore } from 'pinia'
import { me as fetchMe, type MeResponse } from '@/api/auth'

interface AuthState {
  usuario: MeResponse | null
  checked: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({ usuario: null, checked: false }),
  getters: {
    isAuthenticated: (state) => state.usuario !== null,
  },
  actions: {
    async checkSession() {
      try {
        this.usuario = await fetchMe()
      } catch {
        this.usuario = null
      } finally {
        this.checked = true
      }
    },
    clear() {
      this.usuario = null
      this.checked = false
    },
  },
})
```

- [ ] **Step 4: Write a minimal `DashboardView.vue` stub**

```vue
<template>
  <div class="dashboard-stub">
    <h1>Login bem-sucedido</h1>
    <p>Painel real definido em uma fatia futura.</p>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.dashboard-stub {
  padding: 44px 56px;
  font-family: 'Manrope', sans-serif;
}
</style>
```

- [ ] **Step 5: Write `router/index.ts` with the LoginView/ForgotPasswordView/ResetPasswordView imports (created in Tasks 14-15) already referenced**

```ts
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import DashboardView from '@/views/DashboardView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/esqueci-senha', name: 'forgot-password', component: ForgotPasswordView, meta: { public: true } },
    { path: '/redefinir-senha', name: 'reset-password', component: ResetPasswordView, meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.checked) {
    await auth.checkSession()
  }
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login' }
  }
  return true
})

export default router
```

Since `LoginView.vue`/`ForgotPasswordView.vue`/`ResetPasswordView.vue` don't exist until Tasks 14-15, add temporary placeholder files now so the app compiles (Tasks 14-15 will overwrite them with real content):

```vue
<!-- mesh-suite-frontend/src/views/LoginView.vue (temporary placeholder, replaced in Task 14) -->
<template><div /></template>
```
Duplicate the same one-line placeholder for `ForgotPasswordView.vue` and `ResetPasswordView.vue`.

- [ ] **Step 6: Wire Pinia and the router into `main.ts`**

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

- [ ] **Step 7: Write the failing router guard test**

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth')

describe('auth store session check', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('sets usuario on successful /me call', async () => {
    vi.mocked(authApi.me).mockResolvedValue({ nome: 'Marina', papel: 'ADMINISTRADOR' })

    const store = useAuthStore()
    await store.checkSession()

    expect(store.isAuthenticated).toBe(true)
    expect(store.usuario?.nome).toBe('Marina')
  })

  it('clears usuario on 401 from /me', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new Error('401'))

    const store = useAuthStore()
    await store.checkSession()

    expect(store.isAuthenticated).toBe(false)
  })
})
```

- [ ] **Step 8: Run test to verify it fails, then passes**

```bash
cd mesh-suite-frontend && npx vitest run src/router/__tests__/guard.spec.ts
```
Expected: FAIL before `stores/auth.ts` exists, PASS after.

- [ ] **Step 9: Commit**

```bash
git add mesh-suite-frontend/src/api mesh-suite-frontend/src/stores \
        mesh-suite-frontend/src/router mesh-suite-frontend/src/views/DashboardView.vue \
        mesh-suite-frontend/src/views/LoginView.vue mesh-suite-frontend/src/views/ForgotPasswordView.vue \
        mesh-suite-frontend/src/views/ResetPasswordView.vue mesh-suite-frontend/src/main.ts
git commit -m "feat: add auth store, api client, and router guard"
```

---

### Task 14: `LoginView.vue`

**Files:**
- Modify: `mesh-suite-frontend/src/views/LoginView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/LoginView.spec.ts`

**Interfaces:**
- Consumes: `login()` (Task 13's `api/auth.ts`).
- Replicates `design_handoff/screenshot-login.png`: full-height split layout, dark petroleo panel left (`#0E2530`) with gold-square "Mesh Suite" logo + tagline, dark card right containing the form, gold submit button, teal links, light `#FAFAF9` page background visible at the edges. Colors reuse the token set already established for the (separate, future) production dashboard in `design_handoff/README.md` — same visual language, different screen.

- [ ] **Step 1: Write the failing component test**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: LoginView },
      { path: '/esqueci-senha', name: 'forgot-password', component: { template: '<div />' } },
    ],
  })
  return mount(LoginView, { global: { plugins: [router] } })
}

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('submits email and senha to the login API', async () => {
    vi.mocked(authApi.login).mockResolvedValue()

    const wrapper = mountWithRouter()
    await wrapper.find('input[type="email"]').setValue('marina@aurora.com.br')
    await wrapper.find('input[type="password"]').setValue('senha123')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()

    expect(authApi.login).toHaveBeenCalledWith({
      email: 'marina@aurora.com.br',
      senha: 'senha123',
      manterConectado: false,
    })
  })

  it('shows the generic error message on 401', async () => {
    vi.mocked(authApi.login).mockRejectedValue({ response: { status: 401 } })

    const wrapper = mountWithRouter()
    await wrapper.find('input[type="email"]').setValue('marina@aurora.com.br')
    await wrapper.find('input[type="password"]').setValue('errada')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('E-mail ou senha inválidos')
  })

  it('shows the rate limit message on 429', async () => {
    vi.mocked(authApi.login).mockRejectedValue({ response: { status: 429 } })

    const wrapper = mountWithRouter()
    await wrapper.find('input[type="email"]').setValue('marina@aurora.com.br')
    await wrapper.find('input[type="password"]').setValue('senha123')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Muitas tentativas')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-frontend && npx vitest run src/views/__tests__/LoginView.spec.ts
```
Expected: FAIL — placeholder `LoginView.vue` has no form.

- [ ] **Step 3: Write `LoginView.vue`**

```vue
<template>
  <div class="login-page">
    <aside class="login-brand">
      <div class="logo">
        <span class="logo-mark" />
        <span class="logo-text">Mesh Suite</span>
      </div>
      <p class="tagline">O ERP completo para confecções.</p>
    </aside>

    <main class="login-main">
      <div class="login-card">
        <h1>Entrar</h1>
        <p class="subtitle">Acesse o painel do seu Mesh Suite</p>

        <form @submit.prevent="onSubmit">
          <label class="field-label" for="email">E-mail</label>
          <input id="email" type="email" v-model="email" required autocomplete="username" />

          <label class="field-label" for="senha">Senha</label>
          <div class="password-field">
            <input id="senha" :type="showSenha ? 'text' : 'password'" v-model="senha" required
                   autocomplete="current-password" />
            <button type="button" class="toggle-senha" @click="showSenha = !showSenha">
              {{ showSenha ? 'Ocultar' : 'Mostrar' }}
            </button>
          </div>

          <div class="row">
            <label class="checkbox-label">
              <input type="checkbox" v-model="manterConectado" />
              Manter conectado
            </label>
            <RouterLink to="/esqueci-senha" class="link">Esqueci minha senha</RouterLink>
          </div>

          <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

          <button type="submit" class="submit-button" :disabled="loading">Entrar</button>
        </form>

        <p class="footer-text">
          Não tem conta?
          <span class="link-inert" title="Provisionamento de tenant fora de escopo desta fatia">
            Fale com o time comercial
          </span>
        </p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const email = ref('')
const senha = ref('')
const manterConectado = ref(false)
const showSenha = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const router = useRouter()
const authStore = useAuthStore()

async function onSubmit() {
  errorMessage.value = ''
  loading.value = true
  try {
    await login({ email: email.value, senha: senha.value, manterConectado: manterConectado.value })
    await authStore.checkSession()
    router.push({ name: 'dashboard' })
  } catch (err: any) {
    if (err?.response?.status === 429) {
      errorMessage.value = 'Muitas tentativas, tente novamente em instantes'
    } else {
      errorMessage.value = 'E-mail ou senha inválidos'
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  width: 100vw;
  height: 100vh;
  background: #fafaf9;
}

.login-brand {
  width: 40%;
  min-width: 320px;
  background: #0e2530;
  color: #eaf2f4;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 64px;
  gap: 16px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-mark {
  width: 14px;
  height: 14px;
  background: #c9a15a;
  border-radius: 2px;
}

.logo-text {
  font-family: 'Manrope', sans-serif;
  font-weight: 800;
  font-size: 18px;
}

.tagline {
  color: #8fb0ba;
  font-size: 16px;
  max-width: 220px;
}

.login-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  background: #0e2530;
  border-radius: 20px;
  padding: 40px;
  width: 380px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  color: #eaf2f4;
  font-family: 'Manrope', sans-serif;
}

.login-card h1 {
  font-size: 28px;
  font-weight: 800;
  margin: 0 0 8px;
}

.subtitle {
  color: #8fb0ba;
  font-size: 14px;
  margin: 0 0 24px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #8fb0ba;
  margin: 16px 0 6px;
}

input[type='email'],
input[type='password'],
input[type='text'] {
  width: 100%;
  box-sizing: border-box;
  background: #14313d;
  border: 1px solid #1e4552;
  border-radius: 10px;
  padding: 10px 14px;
  color: #eaf2f4;
  font-size: 14px;
}

.password-field {
  position: relative;
}

.toggle-senha {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: #4fc3d9;
  font-size: 12px;
  cursor: pointer;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  font-size: 14px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #eaf2f4;
}

.link {
  color: #4fc3d9;
  text-decoration: none;
}

.link-inert {
  color: #4fc3d9;
  cursor: not-allowed;
}

.error {
  color: #d0453a;
  font-size: 14px;
  margin-top: 16px;
}

.submit-button {
  width: 100%;
  margin-top: 24px;
  background: #c9a15a;
  color: #17171a;
  border: none;
  border-radius: 10px;
  padding: 12px;
  font-weight: 700;
  font-size: 15px;
  cursor: pointer;
}

.submit-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.footer-text {
  text-align: center;
  margin-top: 24px;
  font-size: 13px;
  color: #8fb0ba;
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd mesh-suite-frontend && npx vitest run src/views/__tests__/LoginView.spec.ts
```
Expected: PASS, all three tests.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/LoginView.vue mesh-suite-frontend/src/views/__tests__/LoginView.spec.ts
git commit -m "feat: add LoginView replicating screenshot-login.png"
```

---

### Task 15: `ForgotPasswordView.vue` + `ResetPasswordView.vue`

**Files:**
- Modify: `mesh-suite-frontend/src/views/ForgotPasswordView.vue`
- Modify: `mesh-suite-frontend/src/views/ResetPasswordView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/ForgotPasswordView.spec.ts`
- Test: `mesh-suite-frontend/src/views/__tests__/ResetPasswordView.spec.ts`

**Interfaces:**
- Consumes: `forgotPassword()`, `resetPassword()` (Task 13's `api/auth.ts`).

- [ ] **Step 1: Write the failing tests**

```ts
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth')

describe('ForgotPasswordView', () => {
  it('shows the generic success message after submit, regardless of API result', async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValue()

    const wrapper = mount(ForgotPasswordView)
    await wrapper.find('input[type="email"]').setValue('marina@aurora.com.br')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()

    expect(authApi.forgotPassword).toHaveBeenCalledWith('marina@aurora.com.br')
    expect(wrapper.text()).toContain('se o e-mail existir')
  })
})
```

```ts
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth')

function mountWithRoute(query: string) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/redefinir-senha', name: 'reset-password', component: ResetPasswordView }],
  })
  router.push('/redefinir-senha' + query)
  return { router, wrapper: mount(ResetPasswordView, { global: { plugins: [router] } }) }
}

describe('ResetPasswordView', () => {
  it('reads token from the query string and submits it with the new password', async () => {
    vi.mocked(authApi.resetPassword).mockResolvedValue()
    const { router, wrapper } = mountWithRoute('?token=abc123')
    await router.isReady()

    await wrapper.find('input[name="novaSenha"]').setValue('novaSenha123')
    await wrapper.find('input[name="confirmacao"]').setValue('novaSenha123')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()

    expect(authApi.resetPassword).toHaveBeenCalledWith('abc123', 'novaSenha123')
  })

  it('shows an error when confirmation does not match', async () => {
    const { router, wrapper } = mountWithRoute('?token=abc123')
    await router.isReady()

    await wrapper.find('input[name="novaSenha"]').setValue('novaSenha123')
    await wrapper.find('input[name="confirmacao"]').setValue('diferente')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()

    expect(authApi.resetPassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('não coincidem')
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd mesh-suite-frontend && npx vitest run src/views/__tests__/ForgotPasswordView.spec.ts src/views/__tests__/ResetPasswordView.spec.ts
```
Expected: FAIL — placeholder components have no form.

- [ ] **Step 3: Write `ForgotPasswordView.vue`**

```vue
<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>Esqueci minha senha</h1>
      <form v-if="!submitted" @submit.prevent="onSubmit">
        <label class="field-label" for="email">E-mail</label>
        <input id="email" type="email" v-model="email" required />
        <button type="submit" class="submit-button">Enviar link</button>
      </form>
      <p v-else class="success">Se o e-mail existir, enviamos um link de redefinição.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { forgotPassword } from '@/api/auth'

const email = ref('')
const submitted = ref(false)

async function onSubmit() {
  try {
    await forgotPassword(email.value)
  } finally {
    // Always show the same message, whether or not the account exists.
    submitted.value = true
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #fafaf9;
  font-family: 'Manrope', sans-serif;
}

.auth-card {
  background: #0e2530;
  color: #eaf2f4;
  border-radius: 20px;
  padding: 40px;
  width: 380px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #8fb0ba;
  margin: 16px 0 6px;
}

input {
  width: 100%;
  box-sizing: border-box;
  background: #14313d;
  border: 1px solid #1e4552;
  border-radius: 10px;
  padding: 10px 14px;
  color: #eaf2f4;
}

.submit-button {
  width: 100%;
  margin-top: 24px;
  background: #c9a15a;
  border: none;
  border-radius: 10px;
  padding: 12px;
  font-weight: 700;
  cursor: pointer;
}

.success {
  color: #8fb0ba;
}
</style>
```

- [ ] **Step 4: Write `ResetPasswordView.vue`**

```vue
<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>Redefinir senha</h1>
      <form @submit.prevent="onSubmit">
        <label class="field-label" for="nova">Nova senha</label>
        <input id="nova" name="novaSenha" type="password" v-model="novaSenha" required minlength="8" />

        <label class="field-label" for="confirma">Confirmar nova senha</label>
        <input id="confirma" name="confirmacao" type="password" v-model="confirmacao" required minlength="8" />

        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        <p v-if="successMessage" class="success">{{ successMessage }}</p>

        <button type="submit" class="submit-button">Redefinir senha</button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { resetPassword } from '@/api/auth'

const route = useRoute()
const novaSenha = ref('')
const confirmacao = ref('')
const errorMessage = ref('')
const successMessage = ref('')

async function onSubmit() {
  errorMessage.value = ''
  successMessage.value = ''

  if (novaSenha.value !== confirmacao.value) {
    errorMessage.value = 'As senhas não coincidem'
    return
  }

  const token = String(route.query.token ?? '')
  try {
    await resetPassword(token, novaSenha.value)
    successMessage.value = 'Senha redefinida com sucesso.'
  } catch {
    errorMessage.value = 'Link inválido ou expirado'
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #fafaf9;
  font-family: 'Manrope', sans-serif;
}

.auth-card {
  background: #0e2530;
  color: #eaf2f4;
  border-radius: 20px;
  padding: 40px;
  width: 380px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #8fb0ba;
  margin: 16px 0 6px;
}

input {
  width: 100%;
  box-sizing: border-box;
  background: #14313d;
  border: 1px solid #1e4552;
  border-radius: 10px;
  padding: 10px 14px;
  color: #eaf2f4;
}

.submit-button {
  width: 100%;
  margin-top: 24px;
  background: #c9a15a;
  border: none;
  border-radius: 10px;
  padding: 12px;
  font-weight: 700;
  cursor: pointer;
}

.error {
  color: #d0453a;
  margin-top: 16px;
}

.success {
  color: #1f9d66;
  margin-top: 16px;
}
</style>
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd mesh-suite-frontend && npx vitest run src/views/__tests__/ForgotPasswordView.spec.ts src/views/__tests__/ResetPasswordView.spec.ts
```
Expected: PASS, all three tests.

- [ ] **Step 6: Run the full frontend test suite and build**

```bash
cd mesh-suite-frontend && npx vitest run && npm run build
```
Expected: all tests pass, build succeeds.

- [ ] **Step 7: Commit**

```bash
git add mesh-suite-frontend/src/views/ForgotPasswordView.vue mesh-suite-frontend/src/views/ResetPasswordView.vue \
        mesh-suite-frontend/src/views/__tests__/ForgotPasswordView.spec.ts \
        mesh-suite-frontend/src/views/__tests__/ResetPasswordView.spec.ts
git commit -m "feat: add password recovery screens"
```

---

### Task 16: `tabela-execucao.md` and Definition-of-Done crosswalk

**Files:**
- Create: `tabela-execucao.md`

**Interfaces:**
- None — documentation only.

- [ ] **Step 1: Write `tabela-execucao.md`**

```markdown
# Tabela de Execução — PRD-14 slice 1 (Login/Multitenant)

Plano: `docs/superpowers/specs/2026-07-25-login-multitenant-foundation-design.md`
Plano de implementação: `docs/superpowers/plans/2026-07-25-login-multitenant-foundation-plan.md`

| ID | Tarefa | Área | Status |
|----|--------|------|--------|
| BE-01 | Scaffolding backend + frontend + docker-compose | Infra | Concluído |
| BE-02 | Entidade Tenant + migration | Backend | Concluído |
| BE-03 | Entidade Empresa + RLS + migration | Backend | Concluído |
| BE-04 | Entidade Usuario + RLS + política de login + migration | Backend | Concluído |
| BE-05 | Entidade PasswordResetToken + migration | Backend | Concluído |
| BE-06 | JwtService | Backend | Concluído |
| BE-07 | TenantContext + aspecto RLS + teste obrigatório de isolamento | Backend | Concluído |
| BE-08 | JwtAuthenticationFilter + SecurityConfig | Backend | Concluído |
| BE-09 | RateLimiter | Backend | Concluído |
| BE-10 | AuthService.login + AuthController (/login, /me) | Backend | Concluído |
| BE-11 | Recuperação de senha (MailService, PasswordResetService, endpoints) | Backend | Concluído |
| BE-12 | Seed dev/test (2 tenants) | Backend | Concluído |
| FE-13 | Axios client + Pinia auth store + router guard | Frontend | Concluído |
| FE-14 | LoginView.vue | Frontend | Concluído |
| FE-15 | ForgotPasswordView.vue + ResetPasswordView.vue | Frontend | Concluído |

## Definition of Done (spec §9) — status

- [x] Repositórios `mesh-suite-backend/` e `mesh-suite-frontend/` criados com README, Dockerfile, e `docker-compose.yml` funcional na raiz. (BE-01)
- [x] `Tenant`, `Empresa`, `Usuario`, `PasswordResetToken` implementados com unicidade global de `email` e `cnpj`. (BE-02 a BE-05)
- [x] Login funcional (e-mail + senha), JWT em cookie `HttpOnly`, mensagens de erro genéricas. (BE-10)
- [x] RLS ativo em toda tabela de negócio, com teste automatizado de isolamento entre 2 tenants passando. (BE-07)
- [x] Checagem de `ativo` (usuário/tenant) a cada request autenticado. (BE-08)
- [x] Rate limiting funcional em login e recuperação de senha. (BE-09, BE-11)
- [x] Recuperação de senha completa, e-mail via SMTP configurado por variável de ambiente. (BE-11)
- [x] Tela de login replicando `screenshot-login.png`; telas de esqueci-senha/redefinir-senha. (FE-14, FE-15)
- [x] Seed de dev/test (Flyway, perfil `dev`/`test`) com 2 tenants de exemplo. (BE-12)
- [x] Nenhum segredo commitado — tudo via variável de ambiente. (BE-01, verified across all tasks)
- [x] `tabela-execucao.md` atualizado. (this file)
```

- [ ] **Step 2: Commit**

```bash
git add tabela-execucao.md
git commit -m "docs: add tabela-execucao.md for PRD-14 slice 1"
```

---

## Final verification

- [ ] Run `cd mesh-suite-backend && ./mvnw clean test` — all backend tests pass.
- [ ] Run `cd mesh-suite-frontend && npx vitest run && npm run build` — all frontend tests pass, build succeeds.
- [ ] Run `docker compose up --build` from the repo root with a real `.env` (copied from `.env.example` and filled in locally, never committed) — Postgres, backend, and frontend all start; `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`.
- [ ] Manually log in at `http://localhost:5173/login` with `marina@aurora.com.br` / `MeshSuite@123` (dev seed) and confirm redirect to the dashboard stub.
- [ ] Confirm `git status` shows no `.env`, and `git log -p` for this branch contains no plaintext secret values.
