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
