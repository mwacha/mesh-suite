<template>
  <div class="search-select">
    <label v-if="label" class="field-label">
      {{ label }}<span v-if="required" class="required-mark">*</span>
    </label>
    <div
      ref="triggerRef"
      class="search-select-trigger"
      :class="{ 'search-select-trigger-open': open, 'search-select-trigger-error': !!error }"
      :data-test="testId"
      @click="onTriggerClick"
    >
      <span :class="{ 'search-select-placeholder': !selectedLabel }">{{ selectedLabel || placeholder }}</span>
      <span class="search-select-caret" :class="{ 'search-select-caret-open': open }">▾</span>
    </div>
    <p v-if="error" class="field-error">⚠️ {{ error }}</p>

    <Teleport to="body">
      <div v-if="open" ref="menuRef" class="search-select-panel" :style="position">
        <input
          ref="inputRef"
          v-model="query"
          class="search-select-search"
          placeholder="Buscar..."
          autocomplete="off"
          :data-test="testId && `${testId}-input`"
          @input="$emit('search', query)"
        />
        <div class="search-select-list">
          <div v-if="items.length === 0" class="search-select-empty" :class="{ 'search-select-empty-error': emptyIsError }">
            {{ emptyMessage }}
          </div>
          <div
            v-for="item in items"
            :key="item.id"
            class="search-select-item"
            :class="{ 'search-select-item-active': item.id === modelValue }"
            :data-test="testId && `${testId}-option-${item.id}`"
            @click="select(item)"
          >
            <span class="search-select-item-label">{{ item.label }}</span>
            <span v-if="item.sublabel" class="search-select-item-sublabel">{{ item.sublabel }}</span>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useDropdownPosition } from '@/composables/useDropdownPosition'

export interface SearchSelectItem {
  id: string
  label: string
  sublabel?: string
}

withDefaults(
  defineProps<{
    modelValue: string | null
    items: SearchSelectItem[]
    selectedLabel?: string
    label?: string
    placeholder?: string
    required?: boolean
    error?: string
    testId?: string
    emptyMessage?: string
    /** Renders `emptyMessage` as a failure (a lookup that errored) rather than a genuine no-match. */
    emptyIsError?: boolean
  }>(),
  { placeholder: 'Selecione...', emptyMessage: 'Nenhum resultado' },
)

const emit = defineEmits<{
  'update:modelValue': [id: string]
  select: [item: SearchSelectItem]
  search: [query: string]
}>()

const triggerRef = ref<HTMLElement | null>(null)
const menuRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLInputElement | null>(null)
const query = ref('')
const { open, position, toggle, close } = useDropdownPosition(triggerRef, menuRef, 220, {
  matchTriggerWidth: true,
  minWidth: 260,
})

function onTriggerClick() {
  const opening = !open.value
  toggle()
  if (opening) {
    query.value = ''
    emit('search', '')
    nextTick(() => inputRef.value?.focus())
  }
}

function select(item: SearchSelectItem) {
  emit('update:modelValue', item.id)
  emit('select', item)
  close()
}
</script>

<style scoped>
.search-select {
  margin-bottom: 10px;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

.required-mark {
  color: var(--pm-error);
  margin-left: 2px;
}

.search-select-trigger {
  height: 34px;
  box-sizing: border-box;
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  background: var(--pm-white);
  padding: 0 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  cursor: pointer;
  user-select: none;
}

.search-select-trigger-open {
  border-color: var(--pm-accent);
}

.search-select-trigger-error {
  border-color: var(--pm-error);
}

.search-select-placeholder {
  color: var(--pm-placeholder);
}

.search-select-caret {
  font-size: 10px;
  color: var(--pm-text-muted);
  transition: transform 0.15s;
  flex-shrink: 0;
}

.search-select-caret-open {
  transform: rotate(180deg);
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 4px 0 0;
}

.search-select-panel {
  position: fixed;
  z-index: 9999;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 28px rgba(0, 0, 0, 0.12);
  font-family: var(--pm-font);
  overflow: hidden;
}

.search-select-search {
  width: 100%;
  box-sizing: border-box;
  border: none;
  border-bottom: 1px solid var(--pm-border-light);
  padding: 8px 12px;
  font-size: 13px;
  font-family: var(--pm-font);
  outline: none;
  color: var(--pm-text-dark);
}

.search-select-list {
  max-height: 220px;
  overflow-y: auto;
}

.search-select-empty {
  padding: 10px 14px;
  font-size: 13px;
  color: var(--pm-placeholder);
}

.search-select-empty-error {
  color: var(--pm-error);
}

.search-select-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--pm-text-dark);
  cursor: pointer;
  border-bottom: 1px solid var(--pm-bg);
}

.search-select-item:last-child {
  border-bottom: none;
}

.search-select-item:hover {
  background: var(--pm-bg);
}

.search-select-item-active {
  background: var(--pm-accent-bg);
  color: var(--pm-accent-text);
}

.search-select-item-sublabel {
  font-size: 12px;
  color: var(--pm-text-muted);
  flex-shrink: 0;
}
</style>
