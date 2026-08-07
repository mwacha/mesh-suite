<template>
  <div class="stat-pill" :class="`stat-pill-${color}`">
    <span class="stat-pill-value">{{ value }}</span>
    <span class="stat-pill-label">{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
export type StatPillColor = 'dark' | 'green' | 'amber' | 'red' | 'blue'

withDefaults(defineProps<{ value: string | number; label: string; color?: StatPillColor }>(), {
  color: 'dark',
})
</script>

<style scoped>
.stat-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--pm-border-light);
  border-radius: 5px;
  padding: 5px 12px;
  font-family: var(--pm-font);
}

.stat-pill-value {
  font-size: 16px;
  font-weight: 700;
}

.stat-pill-label {
  font-size: 12px;
  opacity: 0.85;
}

.stat-pill-dark {
  background: var(--pm-bg);
  color: var(--pm-text-dark);
}

.stat-pill-green {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.stat-pill-amber {
  background: var(--pm-warning-bg);
  color: var(--pm-warning);
}

.stat-pill-red {
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.stat-pill-blue {
  background: var(--pm-accent-bg);
  color: var(--pm-accent-text);
}
</style>
