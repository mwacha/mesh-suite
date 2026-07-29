# PediMais Rebrand + New Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebrand the frontend from "Mesh Suite" to "PediMais" (name, logo, blue/navy palette) and introduce a reusable application shell (Sidebar + Topbar), based on the real design reference in `layout/`. Apply the new visual system to the four existing screens: Login, Esqueci senha, Redefinir senha, Dashboard.

**Architecture:** Shared CSS custom-property tokens (`src/styles/tokens.css`) drive every color used by new and updated components — no hex values duplicated across files. `AppSidebar.vue` + `AppTopbar.vue` compose into `AppShell.vue`, a reusable layout wrapper that `DashboardView.vue` uses today and future domain screens (Pedidos, Produtos, ...) will reuse directly. The three existing auth screens (Login/ForgotPassword/ResetPassword) keep their exact current structure and behavior — only their `<style>` blocks and a few text strings change.

**Tech Stack:** Vue 3 + TypeScript (existing project, no new runtime dependencies), Google Fonts (Inter, linked in `index.html` — matches how the reference itself loads it, no new npm package).

## Global Constraints

- User-facing only. Backend package names (`com.meshsuite.*`), directory names (`mesh-suite-backend/`, `mesh-suite-frontend/`), the database name, and the git repository name are unchanged — this plan touches only `mesh-suite-frontend/`.
- No backend changes. `/api/auth/me` keeps returning `{ nome, papel }` only — nothing in this plan reads or displays data the backend doesn't already provide (see spec §1, §7).
- Every color in new/touched styles comes from a `--pm-*` custom property defined in Task 1 — no new hardcoded hex values in component `<style>` blocks.
- Nav items other than Home are visually present but inert (no `@click` navigation, `cursor: not-allowed`) — their domains (Pedidos, Produtos, etc.) aren't implemented yet.
- "Sair" (logout) is client-side only this slice: clears the Pinia auth store and navigates to `/login`. No backend logout endpoint exists yet (spec §7, risk 2) — do not add one as part of this plan.

## Design refinement beyond the spec: card backgrounds are white, not dark

The spec's shorthand ("mesma estrutura já implementada... só a paleta muda") undersold one real structural difference: in the actual PediMais reference (`layout/PediMais Prototipo.html`, `LoginB`, and the real screenshots in `layout/scraps/`), only the login's **left branding panel** is dark (`#1e293b`) — the form card itself, and virtually every other screen's content area, is **white** with dark text. The current Mesh Suite `LoginView.vue`/`ForgotPasswordView.vue`/`ResetPasswordView.vue` make the card dark too (`#0e2530`, matching the old petroleo palette). This plan corrects that: cards become white (`var(--pm-white)`) with dark text (`var(--pm-text-dark)`), matching the actual reference exactly. Only `AppSidebar` and the login's left panel keep the dark `var(--pm-sidebar-bg)` background.

---

### Task 1: Design tokens, font, and page title

**Files:**
- Create: `mesh-suite-frontend/src/styles/tokens.css`
- Modify: `mesh-suite-frontend/src/main.ts`
- Modify: `mesh-suite-frontend/index.html`

**Interfaces:**
- Produces: a fixed set of `--pm-*` CSS custom properties on `:root`, available globally to every component from Task 2 onward. No component in this plan defines its own color hex values — every color reference is `var(--pm-...)`.

- [ ] **Step 1: Write `tokens.css`**

```css
:root {
  /* Dark surfaces (sidebar, login's left branding panel) */
  --pm-sidebar-bg: #1e293b;
  --pm-sidebar-border: #334155;
  --pm-text-light: #f1f5f9;

  /* Text on light surfaces */
  --pm-text-dark: #1e293b;
  --pm-text-mid: #475569;
  --pm-text-muted: #94a3b8;

  /* Light surfaces */
  --pm-border: #374151;
  --pm-border-light: #e2e8f0;
  --pm-bg: #f1f5f9;
  --pm-white: #ffffff;

  /* Accent (buttons, active nav item, links) */
  --pm-accent: #2563eb;
  --pm-accent-bg: #eff6ff;
  --pm-accent-text: #1d4ed8;

  /* Status */
  --pm-success: #15803d;
  --pm-success-bg: #dcfce7;
  --pm-warning: #d97706;
  --pm-warning-bg: #fef3c7;
  --pm-error: #dc2626;
  --pm-error-bg: #fee2e2;

  --pm-font: 'Inter', system-ui, sans-serif;
}
```

- [ ] **Step 2: Import it in `main.ts`**

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import './styles/tokens.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

- [ ] **Step 3: Load Inter and update the page title in `index.html`**

```html
<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link
      href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"
      rel="stylesheet"
    />
    <title>PediMais</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

(`lang="en"` → `lang="pt-BR"` too, matching the rest of the app's Portuguese content — a pre-existing inconsistency worth fixing while this file is already being touched.)

- [ ] **Step 4: Verify the build picks up the new files**

```bash
cd mesh-suite-frontend && npm run build && grep -q "pm-accent" dist/assets/*.css && echo "tokens present in build output"
```
Expected: build succeeds, `tokens present in build output` prints.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/styles/tokens.css mesh-suite-frontend/src/main.ts mesh-suite-frontend/index.html
git commit -m "feat: add PediMais design tokens, load Inter, update page title"
```

---

### Task 2: `AppSidebar.vue`

**Files:**
- Create: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Test: `mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts`

**Interfaces:**
- Consumes: `useAuthStore()` (`usuario.nome`, `usuario.papel`), `useRoute()`/`useRouter()` from vue-router.
- Produces: a self-contained sidebar with no props — `AppShell` (Task 4) renders it directly, `<AppSidebar />`.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AppSidebar from '@/components/AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div />' } },
      { path: '/outra', name: 'outra', component: { template: '<div />' } },
    ],
  })
  const wrapper = mount(AppSidebar, { global: { plugins: [router] } })
  return { router, wrapper }
}

describe('AppSidebar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('navigates to / when Home is clicked', async () => {
    const { router, wrapper } = mountWithRouter()
    await router.push('/outra')
    await router.isReady()

    await wrapper.find('[data-test="nav-Home"]').trigger('click')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('does not navigate when an inert item (Pedidos) is clicked', async () => {
    const { router, wrapper } = mountWithRouter()
    await router.push('/outra')
    await router.isReady()

    await wrapper.find('[data-test="nav-Pedidos"]').trigger('click')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/outra')
  })

  it('toggles collapsed state, hiding the brand name and nav labels', async () => {
    const { wrapper } = mountWithRouter()
    expect(wrapper.text()).toContain('PediMais')
    expect(wrapper.text()).toContain('Home')

    await wrapper.find('[data-test="collapse-toggle"]').trigger('click')

    expect(wrapper.text()).not.toContain('PediMais')
    expect(wrapper.text()).not.toContain('Home')
  })

  it("shows the logged-in user's name and role in the footer", async () => {
    const { wrapper } = mountWithRouter()
    const authStore = useAuthStore()
    authStore.usuario = { nome: 'Marina Aurora', papel: 'ADMINISTRADOR' }
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Marina Aurora')
    expect(wrapper.text()).toContain('ADMINISTRADOR')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-frontend && npx vitest run src/components/__tests__/AppSidebar.spec.ts
```
Expected: FAIL — `AppSidebar.vue` does not exist.

- [ ] **Step 3: Write `AppSidebar.vue`**

```vue
<template>
  <aside class="sidebar" :class="{ collapsed }">
    <div class="sidebar-header">
      <div class="brand">
        <span class="logo-mark">P</span>
        <span v-if="!collapsed" class="brand-name">PediMais</span>
      </div>
      <button
        type="button"
        class="collapse-toggle"
        data-test="collapse-toggle"
        :title="collapsed ? 'Expandir menu' : 'Recolher menu'"
        @click="collapsed = !collapsed"
      >
        {{ collapsed ? '▶' : '◀' }}
      </button>
    </div>

    <nav class="nav-list">
      <div
        v-for="item in navItems"
        :key="item.label"
        class="nav-item"
        :class="{ 'nav-item-active': isActive(item), 'nav-item-inert': !item.route }"
        :data-test="`nav-${item.label}`"
        :title="collapsed ? item.label : undefined"
        @click="go(item)"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
      </div>
    </nav>

    <div class="sidebar-footer">
      <div class="avatar">{{ initial }}</div>
      <div v-if="!collapsed" class="user-info">
        <div class="user-name">{{ authStore.usuario?.nome }}</div>
        <div class="user-role">{{ authStore.usuario?.papel }}</div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

interface NavItem {
  icon: string
  label: string
  route: string | null
}

const navItems: NavItem[] = [
  { icon: '🏠', label: 'Home', route: '/' },
  { icon: '👥', label: 'Clientes', route: null },
  { icon: '🏢', label: 'Empresa', route: null },
  { icon: '🏷', label: 'Marcas', route: null },
  { icon: '💳', label: 'Pagamentos', route: null },
  { icon: '📋', label: 'Pedidos', route: null },
  { icon: '🔒', label: 'Permissões', route: null },
  { icon: '📦', label: 'Produtos', route: null },
  { icon: '💰', label: 'Tab. Preços', route: null },
  { icon: '👤', label: 'Usuários', route: null },
]

const collapsed = ref(false)
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const initial = computed(() => (authStore.usuario?.nome?.[0] ?? '?').toUpperCase())

function isActive(item: NavItem) {
  return item.route !== null && route.path === item.route
}

function go(item: NavItem) {
  if (item.route) {
    router.push(item.route)
  }
}
</script>

<style scoped>
.sidebar {
  width: 200px;
  background: var(--pm-sidebar-bg);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid var(--pm-sidebar-border);
  font-family: var(--pm-font);
  transition: width 0.18s;
}

.sidebar.collapsed {
  width: 48px;
}

.sidebar-header {
  padding: 12px 10px;
  border-bottom: 1px solid var(--pm-sidebar-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 50px;
}

.sidebar.collapsed .sidebar-header {
  justify-content: center;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.logo-mark {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  background: var(--pm-accent);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--pm-white);
  font-weight: 700;
  font-size: 13px;
}

.brand-name {
  color: var(--pm-text-light);
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
}

.collapse-toggle {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  background: var(--pm-sidebar-border);
  border: none;
  border-radius: 4px;
  color: var(--pm-text-muted);
  font-size: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sidebar.collapsed .collapse-toggle {
  margin-left: 0;
}

.nav-list {
  flex: 1;
  padding: 6px 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  margin: 1px 5px;
  border-radius: 4px;
  color: var(--pm-text-muted);
  font-size: 12px;
  cursor: pointer;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
}

.nav-item-active {
  background: var(--pm-accent);
  color: var(--pm-white);
}

.nav-item-inert {
  cursor: not-allowed;
}

.nav-icon {
  font-size: 15px;
  flex-shrink: 0;
}

.sidebar-footer {
  border-top: 1px solid var(--pm-sidebar-border);
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar.collapsed .sidebar-footer {
  justify-content: center;
  padding: 8px 0;
}

.avatar {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  background: var(--pm-sidebar-border);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--pm-white);
  font-size: 12px;
}

.user-info {
  overflow: hidden;
}

.user-name {
  color: var(--pm-text-light);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  color: var(--pm-text-muted);
  font-size: 10px;
  white-space: nowrap;
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd mesh-suite-frontend && npx vitest run src/components/__tests__/AppSidebar.spec.ts
```
Expected: PASS, all four tests.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/components/AppSidebar.vue mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts
git commit -m "feat: add AppSidebar with PediMais nav and inert future-domain items"
```

---

### Task 3: `AppTopbar.vue`

**Files:**
- Create: `mesh-suite-frontend/src/components/AppTopbar.vue`
- Test: `mesh-suite-frontend/src/components/__tests__/AppTopbar.spec.ts`

**Interfaces:**
- Consumes: `useAuthStore()` (`usuario.nome`, `clear()` — already exists, see `mesh-suite-frontend/src/stores/auth.ts:24-27`), `useRouter()`.
- Produces: `AppTopbar` with one required prop `title: string`. `AppShell` (Task 4) renders `<AppTopbar :title="title" />`.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AppTopbar from '@/components/AppTopbar.vue'
import { useAuthStore } from '@/stores/auth'

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
  const wrapper = mount(AppTopbar, { props: { title: 'Dashboard' }, global: { plugins: [router] } })
  return { router, wrapper }
}

describe('AppTopbar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders the page title', () => {
    const { wrapper } = mountWithRouter()
    expect(wrapper.text()).toContain('Dashboard')
  })

  it('opens and closes the user menu on avatar click', async () => {
    const { wrapper } = mountWithRouter()
    expect(wrapper.find('[data-test="user-dropdown"]').exists()).toBe(false)

    await wrapper.find('[data-test="avatar-button"]').trigger('click')
    expect(wrapper.find('[data-test="user-dropdown"]').exists()).toBe(true)

    await wrapper.find('[data-test="avatar-button"]').trigger('click')
    expect(wrapper.find('[data-test="user-dropdown"]').exists()).toBe(false)
  })

  it('logging out clears the auth store and navigates to /login', async () => {
    const { router, wrapper } = mountWithRouter()
    const authStore = useAuthStore()
    authStore.usuario = { nome: 'Marina Aurora', papel: 'ADMINISTRADOR' }
    authStore.checked = true

    await wrapper.find('[data-test="avatar-button"]').trigger('click')
    await wrapper.find('[data-test="logout"]').trigger('click')
    await router.isReady()

    expect(authStore.usuario).toBeNull()
    expect(router.currentRoute.value.name).toBe('login')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-frontend && npx vitest run src/components/__tests__/AppTopbar.spec.ts
```
Expected: FAIL — `AppTopbar.vue` does not exist.

- [ ] **Step 3: Write `AppTopbar.vue`**

```vue
<template>
  <header class="topbar">
    <div class="breadcrumb">{{ title }}</div>

    <div class="topbar-right">
      <div class="empresa-badge">Empresa Principal</div>

      <button type="button" class="icon-button" title="Notificações (em breve)">🔔</button>

      <div class="user-menu">
        <button
          type="button"
          class="avatar-button"
          data-test="avatar-button"
          @click="menuOpen = !menuOpen"
        >
          {{ initial }}
        </button>
        <div v-if="menuOpen" class="user-dropdown" data-test="user-dropdown">
          <div class="dropdown-item-inert">Meu Perfil</div>
          <div class="dropdown-item-inert">Configurações</div>
          <div class="dropdown-item" data-test="logout" @click="onLogout">Sair</div>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

defineProps<{ title: string }>()

const menuOpen = ref(false)
const router = useRouter()
const authStore = useAuthStore()

const initial = computed(() => (authStore.usuario?.nome?.[0] ?? '?').toUpperCase())

function onLogout() {
  menuOpen.value = false
  authStore.clear()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.topbar {
  height: 56px;
  flex-shrink: 0;
  background: var(--pm-white);
  border-bottom: 1px solid var(--pm-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  font-family: var(--pm-font);
}

.breadcrumb {
  font-size: 15px;
  font-weight: 600;
  color: var(--pm-text-dark);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.empresa-badge {
  font-size: 13px;
  color: var(--pm-text-mid);
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  padding: 5px 10px;
}

.icon-button {
  background: none;
  border: none;
  font-size: 16px;
  cursor: not-allowed;
  padding: 4px;
}

.user-menu {
  position: relative;
}

.avatar-button {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: var(--pm-accent);
  color: var(--pm-white);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 180px;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.12);
  padding: 4px;
  z-index: 10;
}

.dropdown-item-inert,
.dropdown-item {
  padding: 8px 10px;
  font-size: 13px;
  border-radius: 5px;
  color: var(--pm-text-dark);
}

.dropdown-item-inert {
  cursor: not-allowed;
  color: var(--pm-text-muted);
}

.dropdown-item {
  cursor: pointer;
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd mesh-suite-frontend && npx vitest run src/components/__tests__/AppTopbar.spec.ts
```
Expected: PASS, all three tests.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/components/AppTopbar.vue mesh-suite-frontend/src/components/__tests__/AppTopbar.spec.ts
git commit -m "feat: add AppTopbar with user menu and client-side logout"
```

---

### Task 4: `AppShell.vue`

**Files:**
- Create: `mesh-suite-frontend/src/components/AppShell.vue`
- Test: `mesh-suite-frontend/src/components/__tests__/AppShell.spec.ts`

**Interfaces:**
- Consumes: `AppSidebar` (Task 2), `AppTopbar` (Task 3).
- Produces: `AppShell` with a required prop `title: string` and a default `<slot>` for page content. `DashboardView.vue` (Task 5) uses `<AppShell title="Dashboard"><!-- content --></AppShell>`.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AppShell from '@/components/AppShell.vue'

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/', name: 'dashboard', component: { template: '<div />' } }],
  })
  return mount(AppShell, {
    props: { title: 'Dashboard' },
    slots: { default: '<p data-test="slot-content">Conteúdo da página</p>' },
    global: { plugins: [router] },
  })
}

describe('AppShell', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders the slot content', () => {
    const wrapper = mountWithRouter()
    expect(wrapper.find('[data-test="slot-content"]').text()).toBe('Conteúdo da página')
  })

  it('renders the sidebar brand and the topbar title', () => {
    const wrapper = mountWithRouter()
    expect(wrapper.text()).toContain('PediMais')
    expect(wrapper.text()).toContain('Dashboard')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-frontend && npx vitest run src/components/__tests__/AppShell.spec.ts
```
Expected: FAIL — `AppShell.vue` does not exist.

- [ ] **Step 3: Write `AppShell.vue`**

```vue
<template>
  <div class="app-shell">
    <AppSidebar />
    <div class="app-shell-main">
      <AppTopbar :title="title" />
      <main class="app-shell-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'

defineProps<{ title: string }>()
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  font-family: var(--pm-font);
}

.app-shell-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app-shell-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  background: var(--pm-bg);
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd mesh-suite-frontend && npx vitest run src/components/__tests__/AppShell.spec.ts
```
Expected: PASS, both tests.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/components/AppShell.vue mesh-suite-frontend/src/components/__tests__/AppShell.spec.ts
git commit -m "feat: add AppShell composing AppSidebar and AppTopbar"
```

---

### Task 5: `DashboardView.vue`

**Files:**
- Modify: `mesh-suite-frontend/src/views/DashboardView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/DashboardView.spec.ts`

**Interfaces:**
- Consumes: `AppShell` (Task 4), `useAuthStore()`.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import { useAuthStore } from '@/stores/auth'

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/', name: 'dashboard', component: DashboardView }],
  })
  return { router, wrapper: mount(DashboardView, { global: { plugins: [router] } }) }
}

describe('DashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('greets the logged-in user by name', () => {
    const authStore = useAuthStore()
    authStore.usuario = { nome: 'Marina Aurora', papel: 'ADMINISTRADOR' }

    const { wrapper } = mountWithRouter()

    expect(wrapper.text()).toContain('Marina Aurora')
  })

  it('renders inside the app shell (sidebar and topbar present)', () => {
    const { wrapper } = mountWithRouter()

    expect(wrapper.text()).toContain('PediMais')
    expect(wrapper.text()).toContain('Dashboard')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd mesh-suite-frontend && npx vitest run src/views/__tests__/DashboardView.spec.ts
```
Expected: FAIL — current `DashboardView.vue` has no `AppShell`, no user greeting.

- [ ] **Step 3: Rewrite `DashboardView.vue`**

```vue
<template>
  <AppShell title="Dashboard">
    <h1>Bem-vindo, {{ authStore.usuario?.nome }} 👋</h1>
    <p class="subtitle">Painel real definido em uma fatia futura.</p>
  </AppShell>
</template>

<script setup lang="ts">
import AppShell from '@/components/AppShell.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
</script>

<style scoped>
h1 {
  font-family: var(--pm-font);
  font-size: 24px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0 0 8px;
}

.subtitle {
  font-family: var(--pm-font);
  color: var(--pm-text-mid);
  font-size: 14px;
  margin: 0;
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd mesh-suite-frontend && npx vitest run src/views/__tests__/DashboardView.spec.ts
```
Expected: PASS, both tests.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/DashboardView.vue mesh-suite-frontend/src/views/__tests__/DashboardView.spec.ts
git commit -m "feat: use AppShell in DashboardView, greet the logged-in user"
```

---

### Task 6: `LoginView.vue` re-skin

**Files:**
- Modify: `mesh-suite-frontend/src/views/LoginView.vue`

**Interfaces:**
- None new — `<script setup>` logic (form fields, submit handler, error handling) is unchanged. Only the `<template>`'s logo mark and two text strings, and the entire `<style scoped>` block, change.

- [ ] **Step 1: Update the template's logo mark and copy**

In the `<template>`, change:

```html
        <span class="logo-mark" />
        <span class="logo-text">Mesh Suite</span>
      </div>
      <p class="tagline">O ERP completo para confecções.</p>
```

to:

```html
        <span class="logo-mark">P</span>
        <span class="logo-text">PediMais</span>
      </div>
      <p class="tagline">Gestão inteligente de pedidos para o seu negócio.</p>
```

And change:

```html
        <p class="subtitle">Acesse o painel do seu Mesh Suite</p>
```

to:

```html
        <p class="subtitle">Acesse o painel do seu PediMais</p>
```

- [ ] **Step 2: Replace the entire `<style scoped>` block**

```css
<style scoped>
.login-page {
  display: flex;
  width: 100vw;
  height: 100vh;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.login-brand {
  width: 40%;
  min-width: 320px;
  background: var(--pm-sidebar-bg);
  color: var(--pm-text-light);
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
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  background: var(--pm-accent);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--pm-white);
  font-weight: 700;
  font-size: 13px;
}

.logo-text {
  font-family: var(--pm-font);
  font-weight: 700;
  font-size: 18px;
}

.tagline {
  color: var(--pm-text-muted);
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
  background: var(--pm-white);
  border-radius: 12px;
  padding: 40px;
  width: 380px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
  color: var(--pm-text-dark);
  font-family: var(--pm-font);
}

.login-card h1 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
}

.subtitle {
  color: var(--pm-text-mid);
  font-size: 14px;
  margin: 0 0 24px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--pm-text-mid);
  margin: 16px 0 6px;
}

input[type='email'],
input[type='password'],
input[type='text'] {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 10px 14px;
  color: var(--pm-text-dark);
  font-size: 14px;
}

input[type='email']::placeholder,
input[type='password']::placeholder,
input[type='text']::placeholder {
  color: #9ca3af;
}

.password-field {
  position: relative;
}

.password-field input {
  padding-right: 64px;
}

.toggle-senha {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--pm-accent);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px;
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
  color: var(--pm-text-dark);
}

.checkbox-label input[type='checkbox'] {
  accent-color: var(--pm-accent);
  width: 16px;
  height: 16px;
}

.link {
  color: var(--pm-accent);
  text-decoration: none;
}

.link-inert {
  color: var(--pm-accent);
  cursor: not-allowed;
}

.error {
  color: var(--pm-error);
  font-size: 14px;
  margin-top: 16px;
}

.submit-button {
  width: 100%;
  margin-top: 24px;
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
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
  color: var(--pm-text-mid);
}
</style>
```

- [ ] **Step 3: Run the existing test suite — confirm no regression**

```bash
cd mesh-suite-frontend && npx vitest run src/views/__tests__/LoginView.spec.ts
```
Expected: PASS, all existing tests unchanged (they assert on behavior — form submission, error messages — not color, per the plan's pre-check of this file).

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-frontend/src/views/LoginView.vue
git commit -m "feat: re-skin LoginView with the PediMais palette and branding"
```

---

### Task 7: `ForgotPasswordView.vue` + `ResetPasswordView.vue` re-skin

**Files:**
- Modify: `mesh-suite-frontend/src/views/ForgotPasswordView.vue`
- Modify: `mesh-suite-frontend/src/views/ResetPasswordView.vue`

**Interfaces:**
- None new — `<script setup>` logic unchanged in both files. Only `<style scoped>` changes.

- [ ] **Step 1: Replace `ForgotPasswordView.vue`'s `<style scoped>` block**

```css
<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.auth-card {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 12px;
  padding: 40px;
  width: 380px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--pm-text-mid);
  margin: 16px 0 6px;
}

input {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 10px 14px;
  color: var(--pm-text-dark);
}

.submit-button {
  width: 100%;
  margin-top: 24px;
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
  padding: 12px;
  font-weight: 700;
  cursor: pointer;
}

.success {
  color: var(--pm-success);
}
</style>
```

- [ ] **Step 2: Replace `ResetPasswordView.vue`'s `<style scoped>` block**

```css
<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.auth-card {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 12px;
  padding: 40px;
  width: 380px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--pm-text-mid);
  margin: 16px 0 6px;
}

input {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 10px 14px;
  color: var(--pm-text-dark);
}

.submit-button {
  width: 100%;
  margin-top: 24px;
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
  padding: 12px;
  font-weight: 700;
  cursor: pointer;
}

.error {
  color: var(--pm-error);
  margin-top: 16px;
}

.success {
  color: var(--pm-success);
  margin-top: 16px;
}
</style>
```

- [ ] **Step 3: Run the existing test suites — confirm no regression**

```bash
cd mesh-suite-frontend && npx vitest run src/views/__tests__/ForgotPasswordView.spec.ts src/views/__tests__/ResetPasswordView.spec.ts
```
Expected: PASS, all existing tests unchanged.

- [ ] **Step 4: Commit**

```bash
git add mesh-suite-frontend/src/views/ForgotPasswordView.vue mesh-suite-frontend/src/views/ResetPasswordView.vue
git commit -m "feat: re-skin password recovery screens with the PediMais palette"
```

---

## Final verification

- [ ] Run `cd mesh-suite-frontend && npx vitest run` — full suite passes (existing tests + the new component tests from Tasks 2, 3, 4, 5).
- [ ] Run `cd mesh-suite-frontend && npm run build` — production build succeeds.
- [ ] Run the app (`devup.sh` or `npm run dev`), log in with the seeded dev credentials, and confirm visually: login screen shows the dark panel + white card + blue accents + "PediMais" branding; after login, the Dashboard shows the sidebar (Home active, other items inert) and topbar (title, avatar menu); clicking "Sair" logs out and returns to `/login`.
