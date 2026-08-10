<template>
  <header class="topbar">
    <div class="breadcrumb">{{ title }}</div>

    <div class="topbar-right">
      <div class="company-badge">Empresa Principal</div>

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

.company-badge {
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
