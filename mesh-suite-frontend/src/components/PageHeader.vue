<template>
  <div class="page-header">
    <div>
      <div class="page-header-title">{{ title }}</div>
      <div v-if="count" class="page-header-count">{{ count }}</div>
    </div>
    <div class="page-header-actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string; count?: string }>()
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
  font-family: var(--pm-font);
}

.page-header-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.page-header-count {
  font-size: 12px;
  color: var(--pm-text-muted);
}

.page-header-actions {
  display: flex;
  gap: 8px;
}
</style>
