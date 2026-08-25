<template>
  <Teleport to="body">
    <div class="slide-over" data-test="slide-over">
      <div class="slide-over-backdrop" @click="$emit('close')"></div>
      <div class="slide-over-panel" :style="{ width }">
        <div class="slide-over-header">
          <span class="slide-over-title">{{ title }}</span>
          <button type="button" class="slide-over-close" data-test="slide-over-close" @click="$emit('close')">×</button>
        </div>
        <div class="slide-over-body"><slot /></div>
        <div v-if="$slots.footer" class="slide-over-footer"><slot name="footer" /></div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title: string; width?: string }>(), { width: '900px' })
defineEmits<{ close: [] }>()
</script>

<style scoped>
.slide-over {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: stretch;
  justify-content: flex-end;
}

.slide-over-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
}

.slide-over-panel {
  position: relative;
  max-width: 100%;
  height: 100%;
  background: var(--pm-bg);
  display: flex;
  flex-direction: column;
  box-shadow: -6px 0 32px rgba(0, 0, 0, 0.18);
  font-family: var(--pm-font);
}

.slide-over-header {
  padding: 16px 20px;
  background: var(--pm-white);
  border-bottom: 2px solid var(--pm-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.slide-over-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.slide-over-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 18px;
  color: var(--pm-text-muted);
  background: var(--pm-bg);
}

.slide-over-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.slide-over-footer {
  padding: 12px 20px;
  background: var(--pm-white);
  border-top: 2px solid var(--pm-border-light);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}
</style>
