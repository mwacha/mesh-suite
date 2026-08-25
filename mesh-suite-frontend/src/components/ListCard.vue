<template>
  <section class="list-card">
    <div class="list-card-header">
      <span class="list-card-title">{{ title }}</span>
      <div v-if="stats && stats.length > 0" class="list-card-stats">
        <StatPill v-for="stat in stats" :key="stat.label" :value="stat.value" :label="stat.label" :color="stat.color" />
      </div>
    </div>
    <slot />
  </section>
</template>

<script setup lang="ts">
import StatPill, { type StatPillColor } from './StatPill.vue'

export interface ListCardStat {
  value: string | number
  label: string
  color?: StatPillColor
}

defineProps<{ title: string; stats?: ListCardStat[] }>()
</script>

<style scoped>
.list-card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 12px;
}

.list-card-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--pm-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: var(--pm-font);
}

.list-card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.list-card-stats {
  display: flex;
  gap: 8px;
}
</style>
