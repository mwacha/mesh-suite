<template>
  <span class="status-badge" :class="`status-badge-${color}`">{{ label }}</span>
</template>

<script setup lang="ts">
export type StatusBadgeColor = 'green' | 'amber' | 'red' | 'gray' | 'blue'

withDefaults(defineProps<{ label: string; color?: StatusBadgeColor }>(), {
  color: 'gray',
})
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  font-family: var(--pm-font);
  white-space: nowrap;
}

.status-badge-green {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.status-badge-amber {
  background: var(--pm-warning-bg);
  color: var(--pm-warning);
}

.status-badge-red {
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.status-badge-blue {
  background: var(--pm-accent-bg);
  color: var(--pm-accent-text);
}

.status-badge-gray {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}
</style>
