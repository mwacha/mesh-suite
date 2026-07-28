# mesh-suite-backend

Spring Boot 3.4.5 / Java 21 backend for Mesh Suite — a greenfield multitenant ERP. This module currently
provides the application skeleton (no domain entities yet; those arrive in later tasks of the
login/multitenant-foundation plan).

## Stack

- Java 21, Spring Boot 3.4.5
- Spring Web, Spring Data JPA, Spring Security, Spring Mail, Spring Boot Actuator
- Flyway (Postgres) for schema migrations
- jjwt for JWT signing/verification
- Testcontainers (Postgres) for integration tests

## Prerequisites

- Java 21
- Docker (required to run integration tests — they start a real Postgres container via Testcontainers)

## Configuration

All configuration is via environment variables (see `application.yml`). No secrets are ever committed;
see the root `.env.example` for the full list. At minimum, running the app requires:

- `DB_USER`, `DB_PASSWORD` — Postgres credentials (`DB_URL` defaults to `jdbc:postgresql://localhost:5433/meshsuite` — matches the host port `docker-compose.yml` maps Postgres to, chosen to avoid colliding with other local projects' default-5432 Postgres instances)
- `JWT_SECRET` — signing secret for JWTs (no default; must be set)

## Running tests

```bash
./mvnw clean test
```

Every integration test extends `com.meshsuite.AbstractIntegrationTest`, which starts one shared
Postgres Testcontainer (`postgres:16-alpine`) for the whole test run via `@ServiceConnection`, and
supplies a test-only `app.jwt.secret` so `JWT_SECRET` does not need to be set in the test environment.

## Running locally

```bash
DB_USER=meshsuite DB_PASSWORD=changeme JWT_SECRET=change-this-to-a-long-random-secret ./mvnw spring-boot:run
```

The app listens on `:8081` by default (not 8080, to avoid colliding with other local projects — override with `SERVER_PORT`); health check is exposed at `/actuator/health`.

## Docker

Build and run via the root `docker-compose.yml` (see repo root README / `.env.example`), or standalone:

```bash
docker build -t mesh-suite-backend .
docker run -p 8081:8081 -e SERVER_PORT=8081 --env-file ../.env mesh-suite-backend
```
