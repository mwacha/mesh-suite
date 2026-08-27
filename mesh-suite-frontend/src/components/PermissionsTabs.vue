<template>
  <div class="perm-tabs">
    <div
      class="perm-tab"
      :class="{ 'tab-ativa': active === 'perfis' }"
      data-test="tab-perfis"
      @click="$router.push({ name: 'permissoes' })"
    >
      Perfis de Permissão
    </div>
    <div
      class="perm-tab"
      :class="{ 'tab-ativa': active === 'usuarios' }"
      data-test="tab-usuarios"
      @click="$router.push({ name: 'permissoes-usuarios' })"
    >
      Usuários e Permissões
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ active: 'perfis' | 'usuarios' }>()
</script>

<style scoped>
.perm-tabs {
  display: flex;
  gap: 2px;
  border-bottom: 2px solid var(--pm-border-light);
  margin-bottom: 16px;
  font-family: var(--pm-font);
}

.perm-tab {
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 500;
  color: var(--pm-text-mid);
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
  cursor: pointer;
  white-space: nowrap;
}

.perm-tab.tab-ativa {
  font-weight: 700;
  color: var(--pm-accent);
  border-bottom-color: var(--pm-accent);
}
</style>
