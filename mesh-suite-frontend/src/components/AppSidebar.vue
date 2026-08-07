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
        class="nav-item"
        :class="{ 'nav-item-active': isActive(topItem) }"
        :data-test="`nav-${topItem.label}`"
        :title="collapsed ? topItem.label : undefined"
        @click="go(topItem)"
      >
        <span class="nav-icon">{{ topItem.icon }}</span>
        <span v-if="!collapsed" class="nav-label">{{ topItem.label }}</span>
      </div>

      <div v-for="group in navGroups" :key="group.key" class="nav-group">
        <div
          v-if="!collapsed"
          class="nav-group-title"
          :data-test="`group-${group.key}`"
          @click="toggleGroup(group.key)"
        >
          <span>{{ group.title }}</span>
          <span class="nav-group-caret">{{ expandedGroups.has(group.key) ? '▼' : '▶' }}</span>
        </div>

        <template v-if="collapsed || expandedGroups.has(group.key)">
          <div
            v-for="item in group.items"
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
        </template>
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

interface NavGroup {
  key: string
  title: string
  items: NavItem[]
}

const topItem: NavItem = { icon: '🏠', label: 'Home', route: '/' }

const navGroups: NavGroup[] = [
  {
    key: 'vendas',
    title: 'VENDAS',
    items: [
      { icon: '📋', label: 'Pedidos', route: '/pedidos' },
      { icon: '💰', label: 'Tab. Preços', route: null },
      { icon: '💳', label: 'Pagamentos', route: '/contas-a-pagar' },
    ],
  },
  {
    key: 'compras',
    title: 'COMPRAS',
    items: [{ icon: '📥', label: 'Compras', route: '/compras' }],
  },
  {
    key: 'catalogo',
    title: 'CATÁLOGO',
    items: [
      { icon: '📦', label: 'Produtos', route: '/produtos' },
      { icon: '🗂', label: 'Categorias', route: null },
      { icon: '🏷', label: 'Marcas', route: null },
      { icon: '🎨', label: 'Cores / Estampas', route: null },
    ],
  },
  {
    key: 'cadastros',
    title: 'CADASTROS',
    items: [
      { icon: '👥', label: 'Clientes', route: '/clientes' },
      { icon: '🏭', label: 'Fornecedores', route: null },
      { icon: '🚚', label: 'Transportadoras', route: null },
    ],
  },
  {
    key: 'configuracoes',
    title: 'CONFIGURAÇÕES',
    items: [
      { icon: '🏢', label: 'Empresa', route: null },
      { icon: '👤', label: 'Usuários', route: '/usuarios' },
      { icon: '🔒', label: 'Permissões', route: null },
    ],
  },
]

const collapsed = ref(false)
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const initial = computed(() => (authStore.usuario?.nome?.[0] ?? '?').toUpperCase())

function isActive(item: NavItem) {
  if (item.route === null) {
    return false
  }
  return item.route === '/' ? route.path === '/' : route.path.startsWith(item.route)
}

function activeGroupKey(): string | null {
  return navGroups.find((group) => group.items.some((item) => isActive(item)))?.key ?? null
}

const initialGroup = activeGroupKey()
const expandedGroups = ref<Set<string>>(new Set(initialGroup ? [initialGroup] : []))

function toggleGroup(key: string) {
  const wasOpen = expandedGroups.value.has(key)
  expandedGroups.value = wasOpen ? new Set() : new Set([key])
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
  overflow-y: auto;
}

.nav-group {
  margin-top: 4px;
}

.nav-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px 4px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--pm-text-muted);
  cursor: pointer;
}

.nav-group-caret {
  font-size: 8px;
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
