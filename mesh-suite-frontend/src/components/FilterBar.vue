<template>
  <div class="filter-bar">
    <div class="filter-bar-row">
      <div class="filter-bar-search">
        <span class="filter-bar-search-icon">🔍</span>
        <input
          class="filter-bar-search-input"
          :placeholder="searchPlaceholder"
          :value="search"
          data-test="filter-bar-search"
          @input="$emit('update:search', ($event.target as HTMLInputElement).value)"
        />
      </div>

      <div ref="moreRef" class="filter-bar-more-wrap">
        <div
          class="filter-bar-more-btn"
          :class="{ 'filter-bar-more-btn-open': isOpen }"
          data-test="filter-bar-more"
          @click="openMenu"
        >
          Mais filtros
          <span class="filter-bar-caret" :class="{ 'filter-bar-caret-open': isOpen }">▾</span>
        </div>

        <div v-if="step === 'menu'" class="filter-bar-panel filter-bar-panel-menu" data-test="filter-bar-menu">
          <div
            v-for="cat in categories"
            :key="cat"
            class="filter-bar-panel-item"
            :data-test="`filter-cat-${cat}`"
            @click="selectCategory(cat)"
          >
            {{ cat }}
          </div>
        </div>

        <div v-if="step === 'values' && isCustomCategory" class="filter-bar-panel filter-bar-panel-custom" data-test="filter-bar-custom">
          <slot name="custom-panel" :category="activeFilter" :apply="applyCustomValue" :cancel="close" />
        </div>

        <div v-else-if="step === 'values'" class="filter-bar-panel filter-bar-panel-values" data-test="filter-bar-values">
          <input v-model="query" class="filter-bar-values-search" placeholder="Buscar..." autofocus />
          <div class="filter-bar-values-list">
            <div
              v-for="val in filteredValues"
              :key="val"
              class="filter-bar-value-item"
              :data-test="`filter-value-${val}`"
              @click="toggleValue(val)"
            >
              <span class="filter-bar-checkbox" :class="{ 'filter-bar-checkbox-checked': pending.includes(val) }">
                <span v-if="pending.includes(val)">✓</span>
              </span>
              {{ val }}
            </div>
          </div>
          <div class="filter-bar-apply" data-test="filter-bar-apply" @click="apply">Aplicar</div>
        </div>
      </div>
    </div>

    <div v-if="chips.length > 0" class="filter-bar-chips">
      <div v-for="[key, vals] in chips" :key="key" class="filter-bar-chip">
        <strong>{{ key }}:</strong> {{ vals.join(', ') }}
        <span class="filter-bar-chip-remove" :data-test="`filter-chip-remove-${key}`" @click="removeChip(key)">×</span>
      </div>
      <span class="filter-bar-clear" data-test="filter-bar-clear" @click="clearAll">Limpar filtros</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { normalizarTexto } from '@/utils/texto'

const props = withDefaults(
  defineProps<{
    search: string
    searchPlaceholder?: string
    categories: string[]
    valueMap: Record<string, string[]>
    /** Categories rendered via the `custom-panel` slot instead of the default checkbox-value list. */
    customCategories?: string[]
  }>(),
  { searchPlaceholder: 'Pesquisar', customCategories: () => [] },
)

const emit = defineEmits<{
  'update:search': [value: string]
  'update:filters': [filters: Record<string, string[]>]
}>()

const step = ref<'menu' | 'values' | null>(null)
const activeFilter = ref<string | null>(null)
const pending = ref<string[]>([])
const applied = ref<Record<string, string[]>>({})
const query = ref('')
const moreRef = ref<HTMLElement | null>(null)

const isOpen = computed(() => step.value !== null)
const isCustomCategory = computed(() => !!activeFilter.value && props.customCategories.includes(activeFilter.value))
const chips = computed(() => Object.entries(applied.value).filter(([, v]) => v.length > 0))
const filteredValues = computed(() => {
  if (!activeFilter.value) {
    return []
  }
  const consulta = normalizarTexto(query.value)
  return (props.valueMap[activeFilter.value] ?? []).filter((v) => normalizarTexto(v).includes(consulta))
})

function close() {
  step.value = null
  activeFilter.value = null
  query.value = ''
  pending.value = []
}

function openMenu() {
  if (step.value === 'menu') {
    close()
    return
  }
  step.value = 'menu'
  activeFilter.value = null
}

function selectCategory(cat: string) {
  activeFilter.value = cat
  pending.value = applied.value[cat] ? [...applied.value[cat]] : []
  step.value = 'values'
  query.value = ''
}

function toggleValue(val: string) {
  pending.value = pending.value.includes(val) ? pending.value.filter((v) => v !== val) : [...pending.value, val]
}

function apply() {
  if (!activeFilter.value) {
    return
  }
  const next = { ...applied.value }
  if (pending.value.length > 0) {
    next[activeFilter.value] = pending.value
  } else {
    delete next[activeFilter.value]
  }
  applied.value = next
  emit('update:filters', next)
  close()
}

function applyCustomValue(value: string | null) {
  if (!activeFilter.value) {
    return
  }
  const next = { ...applied.value }
  if (value) {
    next[activeFilter.value] = [value]
  } else {
    delete next[activeFilter.value]
  }
  applied.value = next
  emit('update:filters', next)
  close()
}

function removeChip(key: string) {
  const next = { ...applied.value }
  delete next[key]
  applied.value = next
  emit('update:filters', next)
}

function clearAll() {
  applied.value = {}
  emit('update:filters', {})
}

function handleClickOutside(event: MouseEvent) {
  if (moreRef.value && !moreRef.value.contains(event.target as Node)) {
    close()
  }
}

onMounted(() => document.addEventListener('mousedown', handleClickOutside))
onBeforeUnmount(() => document.removeEventListener('mousedown', handleClickOutside))
</script>

<style scoped>
.filter-bar {
  font-family: var(--pm-font);
  margin-bottom: 12px;
}

.filter-bar-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.filter-bar-search {
  display: flex;
  align-items: center;
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  background: var(--pm-white);
  height: 36px;
  flex: 1;
  max-width: 360px;
  padding: 0 10px;
  gap: 6px;
}

.filter-bar-search-icon {
  font-size: 13px;
  color: var(--pm-text-muted);
}

.filter-bar-search-input {
  border: none;
  outline: none;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  flex: 1;
  background: transparent;
}

.filter-bar-more-wrap {
  position: relative;
}

.filter-bar-more-btn {
  height: 36px;
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  background: var(--pm-white);
  padding: 0 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--pm-text-dark);
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
}

.filter-bar-more-btn-open {
  background: var(--pm-accent);
  border-color: var(--pm-accent);
  color: var(--pm-white);
}

.filter-bar-caret {
  font-size: 10px;
  transition: transform 0.15s;
}

.filter-bar-caret-open {
  transform: rotate(180deg);
}

.filter-bar-panel {
  position: absolute;
  top: 42px;
  right: 0;
  z-index: 40;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.1);
}

.filter-bar-panel-menu {
  min-width: 200px;
}

.filter-bar-panel-item {
  padding: 10px 16px;
  font-size: 13px;
  color: var(--pm-text-dark);
  cursor: pointer;
}

.filter-bar-panel-item:hover {
  background: var(--pm-bg);
}

.filter-bar-panel-values,
.filter-bar-panel-custom {
  width: 260px;
}

.filter-bar-values-search {
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

.filter-bar-values-list {
  max-height: 200px;
  overflow-y: auto;
}

.filter-bar-value-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--pm-text-dark);
  cursor: pointer;
}

.filter-bar-value-item:hover {
  background: var(--pm-bg);
}

.filter-bar-checkbox {
  width: 15px;
  height: 15px;
  border: 1px solid var(--pm-border-light);
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: var(--pm-white);
  flex-shrink: 0;
}

.filter-bar-checkbox-checked {
  background: var(--pm-accent);
  border-color: var(--pm-accent);
}

.filter-bar-apply {
  margin: 8px;
  background: var(--pm-accent);
  color: var(--pm-white);
  border-radius: 6px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.filter-bar-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.filter-bar-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--pm-border-light);
  border-radius: 20px;
  padding: 4px 12px;
  font-size: 12px;
  color: var(--pm-text-dark);
  background: var(--pm-white);
}

.filter-bar-chip-remove {
  color: var(--pm-text-muted);
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
}

.filter-bar-clear {
  color: var(--pm-accent);
  font-size: 12px;
  cursor: pointer;
}
</style>
