<template>
  <section class="collapsible-section">
    <div class="collapsible-header" data-test="collapsible-header" @click="open = !open">
      <h2>{{ title }}</h2>
      <span class="collapsible-caret" :class="{ 'collapsible-caret-open': open }">▾</span>
    </div>
    <div v-if="open" class="collapsible-body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{ title: string; defaultOpen?: boolean }>(), {
  defaultOpen: true,
})

const open = ref(props.defaultOpen)
</script>

<style scoped>
.collapsible-section {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  overflow: hidden;
}

.collapsible-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  cursor: pointer;
  user-select: none;
}

.collapsible-header h2 {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0;
  font-family: var(--pm-font);
}

.collapsible-caret {
  font-size: 11px;
  color: var(--pm-text-muted);
  transition: transform 0.2s;
}

.collapsible-caret-open {
  transform: rotate(180deg);
}

.collapsible-body {
  padding: 0 16px 16px;
  border-top: 1px solid var(--pm-border-light);
  padding-top: 14px;
}
</style>
