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
