<template>
  <div ref="triggerRef" class="actions-menu-trigger" :data-test="testId ?? 'btn-acoes'" @click="toggle">
    {{ triggerLabel }}
    <span class="actions-menu-caret">▾</span>
  </div>
  <Teleport to="body">
    <div v-if="open" ref="menuRef" class="actions-menu-dropdown" :style="position">
      <div
        v-for="(item, i) in items"
        :key="i"
        class="actions-menu-item"
        :class="{ 'actions-menu-item-danger': item.danger }"
        :data-test="item.testId"
        @click="select(item)"
      >
        {{ item.label }}
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useDropdownPosition } from '@/composables/useDropdownPosition'

export interface ActionsMenuItem {
  label: string
  action: () => void
  danger?: boolean
  testId?: string
}

withDefaults(defineProps<{ items: ActionsMenuItem[]; triggerLabel?: string; testId?: string }>(), {
  triggerLabel: 'Ações',
})

const triggerRef = ref<HTMLElement | null>(null)
const menuRef = ref<HTMLElement | null>(null)
const { open, position, toggle, close } = useDropdownPosition(triggerRef, menuRef)

function select(item: ActionsMenuItem) {
  close()
  item.action()
}
</script>

<style scoped>
.actions-menu-trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  border-radius: 6px;
  padding: 0 10px;
  font-size: 12px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  cursor: pointer;
  user-select: none;
}

.actions-menu-caret {
  font-size: 9px;
  color: var(--pm-text-muted);
}

.actions-menu-dropdown {
  position: fixed;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  min-width: 130px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 28px rgba(0, 0, 0, 0.12);
  z-index: 9999;
  overflow: hidden;
}

.actions-menu-item {
  padding: 8px 14px;
  font-size: 12px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  cursor: pointer;
}

.actions-menu-item:hover {
  background: var(--pm-bg);
}

.actions-menu-item-danger {
  color: var(--pm-error);
}
</style>
