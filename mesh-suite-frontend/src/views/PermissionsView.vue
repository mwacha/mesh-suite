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
