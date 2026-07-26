# mesh-suite-frontend

Vue 3 / TypeScript / Vite frontend for Mesh Suite — a greenfield multitenant ERP. This module currently
provides the application skeleton (no login UI yet; that arrives in later tasks of the
login/multitenant-foundation plan).

## Stack

- Vue 3, TypeScript, Vite
- vue-router 4, Pinia, Axios
- Vitest + @vue/test-utils for unit tests (jsdom environment)

## Prerequisites

- Node.js 22+ and npm

## Path alias

`@` resolves to `./src` (configured in `vite.config.ts` and `tsconfig.app.json`), so imports can use
`@/components/...` instead of relative paths.

## Configuration

The API base URL is provided at runtime via the `VITE_API_BASE_URL` environment variable (see the
root `.env.example` / `docker-compose.yml`); no secrets belong in this project.

## Development

```bash
npm install
npm run dev
```

Dev server listens on `:5173`.

## Build

```bash
npm run build
```

Type-checks with `vue-tsc` then produces a production build in `dist/`.

## Tests

```bash
npm run test
```

## Docker

Build and run via the root `docker-compose.yml`, or standalone:

```bash
docker build -t mesh-suite-frontend .
docker run -p 5173:5173 mesh-suite-frontend
```
