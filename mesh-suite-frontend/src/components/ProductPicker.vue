<template>
  <div class="product-picker">
    <label v-if="label" class="field-label">{{ label }}</label>

    <div
      ref="triggerRef"
      class="product-picker-trigger"
      :class="{ 'product-picker-trigger-open': open }"
      :data-test="testId"
      @click="onTriggerClick"
    >
      <span :class="{ 'product-picker-placeholder': !selectedLabel }">{{ selectedLabel || placeholder }}</span>
      <span class="product-picker-caret" :class="{ 'product-picker-caret-open': open }">▾</span>
    </div>

    <Teleport to="body">
      <div v-if="open" ref="menuRef" class="product-picker-panel" :style="position">
        <div class="product-picker-search-row">
          <input
            ref="inputRef"
            v-model="query"
            class="product-picker-search"
            placeholder="Buscar por SKU ou nome..."
            autocomplete="off"
            :data-test="testId && `${testId}-input`"
            @input="$emit('search', query)"
          />
        </div>

        <div class="product-picker-filters">
          <div class="product-picker-filters-row">
            <div
              class="product-picker-filters-btn"
              :class="{ 'product-picker-filters-btn-open': filterStep !== null }"
              :data-test="testId && `${testId}-filters`"
              @click="toggleFilterMenu"
            >
              Filtros
              <span class="product-picker-caret" :class="{ 'product-picker-caret-open': filterStep !== null }">▾</span>
            </div>

            <div
              v-for="chip in activeChips"
              :key="`${chip.category}:${chip.value}`"
              class="product-picker-chip"
              :data-test="testId && `${testId}-chip-${chip.value}`"
            >
              {{ chip.category }}: {{ chip.value }}
              <span class="product-picker-chip-remove" @click="removeChip(chip)">×</span>
            </div>

            <span
              v-if="activeChips.length > 0"
              class="product-picker-clear"
              :data-test="testId && `${testId}-clear-filters`"
              @click="clearFilters"
            >
              Limpar
            </span>
          </div>

          <div v-if="filterStep === 'menu'" class="product-picker-filter-menu">
            <div
              v-for="cat in categories"
              :key="cat.name"
              class="product-picker-filter-cat"
              :data-test="testId && `${testId}-filter-cat-${cat.name}`"
              @click="openCategory(cat)"
            >
              <span>{{ cat.name }}</span>
              <span class="product-picker-filter-cat-right">
                <span v-if="cat.applied.length > 0" class="product-picker-filter-count">{{ cat.applied.length }}</span>
                <span class="product-picker-filter-chevron">›</span>
              </span>
            </div>
          </div>

          <div v-else-if="filterStep" class="product-picker-filter-values">
            <div class="product-picker-filter-values-head">
              <span class="product-picker-filter-back" @click="filterStep = 'menu'">‹</span>
              <span class="product-picker-filter-title">{{ filterStep }}</span>
            </div>
            <input
              v-model="valueQuery"
              class="product-picker-filter-search"
              :placeholder="`Buscar ${filterStep.toLowerCase()}...`"
              autocomplete="off"
              @input="valuePage = 0"
            />
            <div class="product-picker-filter-list">
              <div v-if="pagedValues.length === 0" class="product-picker-filter-none">Nenhuma opção</div>
              <div
                v-for="val in pagedValues"
                :key="val"
                class="product-picker-filter-option"
                :data-test="testId && `${testId}-filter-value-${val}`"
                @click="togglePending(val)"
              >
                <span class="product-picker-checkbox" :class="{ 'product-picker-checkbox-checked': pending.includes(val) }">
                  <span v-if="pending.includes(val)">✓</span>
                </span>
                {{ val }}
              </div>
            </div>
            <div v-if="valueTotalPages > 1" class="product-picker-filter-pager">
              <span class="product-picker-pager-btn" @click="valuePage = Math.max(0, valuePage - 1)">‹</span>
              <span class="product-picker-pager-label">{{ valuePage + 1 }} / {{ valueTotalPages }}</span>
              <span class="product-picker-pager-btn" @click="valuePage = Math.min(valueTotalPages - 1, valuePage + 1)">›</span>
            </div>
            <div class="product-picker-filter-actions">
              <span class="product-picker-filter-clear" @click="pending = []">Limpar</span>
              <span
                class="product-picker-filter-apply"
                :data-test="testId && `${testId}-filter-apply`"
                @click="applyCategory"
              >
                Aplicar
              </span>
            </div>
          </div>
        </div>

        <div class="product-picker-list">
          <div
            v-if="visibleItems.length === 0"
            class="product-picker-empty"
            :class="{ 'product-picker-empty-error': emptyIsError }"
          >
            {{ emptyMessage }}
          </div>
          <div
            v-for="item in visibleItems"
            :key="item.id"
            class="product-picker-row"
            :data-test="testId && `${testId}-option-${item.id}`"
            @click="select(item)"
          >
            <span class="product-picker-sku">{{ item.sku }}</span>
            <span class="product-picker-name">{{ item.name }}</span>
            <StatusBadge :label="TYPE_LABEL[item.type]" :color="TYPE_COLOR[item.type]" />
            <span class="product-picker-price">{{ formatPrice(item.salePrice) }}</span>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import StatusBadge, { type StatusBadgeColor } from '@/components/StatusBadge.vue'
import { useDropdownPosition } from '@/composables/useDropdownPosition'
import { normalizarTexto } from '@/utils/texto'
import type { SellableProductItem, ProductType } from '@/api/products'

const props = withDefaults(
  defineProps<{
    items: SellableProductItem[]
    label?: string
    placeholder?: string
    selectedLabel?: string
    testId?: string
    emptyMessage?: string
    emptyIsError?: boolean
  }>(),
  { placeholder: 'Buscar produto por SKU ou nome...', emptyMessage: 'Nenhum resultado' },
)

const emit = defineEmits<{
  search: [query: string]
  select: [item: SellableProductItem]
}>()

// A VARIATION_PARENT is never offered here (the endpoint excludes it), so the
// badge only ever has to describe the three orderable kinds.
const TYPE_LABEL: Record<ProductType, string> = {
  PRODUCT: 'Simples',
  PRODUCT_KIT: 'Kit',
  VARIATION_CHILD: 'Variação',
  VARIATION_PARENT: 'Variação',
}
const TYPE_COLOR: Record<ProductType, StatusBadgeColor> = {
  PRODUCT: 'gray',
  PRODUCT_KIT: 'blue',
  VARIATION_CHILD: 'amber',
  VARIATION_PARENT: 'amber',
}

const VALUES_PER_PAGE = 6

const triggerRef = ref<HTMLElement | null>(null)
const menuRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLInputElement | null>(null)
const query = ref('')

const selectedSizes = ref<string[]>([])
const selectedColors = ref<string[]>([])
const filterStep = ref<'menu' | 'Tamanho' | 'Cor' | null>(null)
const pending = ref<string[]>([])
const valueQuery = ref('')
const valuePage = ref(0)

const { open, position, toggle, close } = useDropdownPosition(triggerRef, menuRef, 300, {
  matchTriggerWidth: true,
  minWidth: 420,
})

function formatPrice(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** Rows left after the Tamanho/Cor chips are applied. Text search is served by the API. */
const visibleItems = computed(() =>
  props.items.filter(
    (p) =>
      (selectedSizes.value.length === 0 || (p.size !== null && selectedSizes.value.includes(p.size))) &&
      (selectedColors.value.length === 0 || (p.colorwayName !== null && selectedColors.value.includes(p.colorwayName))),
  ),
)

// Each axis offers the values still reachable given the OTHER axis' selection, so the
// two filters narrow each other instead of offering combinations that match nothing.
const sizeOptions = computed(() =>
  unique(
    props.items
      .filter((p) => selectedColors.value.length === 0 || (p.colorwayName !== null && selectedColors.value.includes(p.colorwayName)))
      .map((p) => p.size),
  ),
)
const colorOptions = computed(() =>
  unique(
    props.items
      .filter((p) => selectedSizes.value.length === 0 || (p.size !== null && selectedSizes.value.includes(p.size)))
      .map((p) => p.colorwayName),
  ),
)

function unique(values: (string | null)[]): string[] {
  return [...new Set(values.filter((v): v is string => !!v))].sort((a, b) => a.localeCompare(b, 'pt-BR'))
}

interface Category {
  name: 'Tamanho' | 'Cor'
  values: string[]
  applied: string[]
  set: (v: string[]) => void
}

const categories = computed<Category[]>(() => [
  { name: 'Tamanho', values: sizeOptions.value, applied: selectedSizes.value, set: (v) => (selectedSizes.value = v) },
  { name: 'Cor', values: colorOptions.value, applied: selectedColors.value, set: (v) => (selectedColors.value = v) },
])

const activeCategory = computed(() =>
  filterStep.value && filterStep.value !== 'menu' ? categories.value.find((c) => c.name === filterStep.value) : undefined,
)

const filteredValues = computed(() => {
  const consulta = normalizarTexto(valueQuery.value)
  return (activeCategory.value?.values ?? []).filter((v) => normalizarTexto(v).includes(consulta))
})
const valueTotalPages = computed(() => Math.max(1, Math.ceil(filteredValues.value.length / VALUES_PER_PAGE)))
const pagedValues = computed(() =>
  filteredValues.value.slice(valuePage.value * VALUES_PER_PAGE, (valuePage.value + 1) * VALUES_PER_PAGE),
)

const activeChips = computed(() => [
  ...selectedSizes.value.map((value) => ({ category: 'Tamanho' as const, value })),
  ...selectedColors.value.map((value) => ({ category: 'Cor' as const, value })),
])

function onTriggerClick() {
  const opening = !open.value
  toggle()
  if (opening) {
    query.value = ''
    filterStep.value = null
    emit('search', '')
    nextTick(() => inputRef.value?.focus())
  }
}

function toggleFilterMenu() {
  filterStep.value = filterStep.value === null ? 'menu' : null
}

function openCategory(cat: Category) {
  pending.value = [...cat.applied]
  valueQuery.value = ''
  valuePage.value = 0
  filterStep.value = cat.name
}

function togglePending(value: string) {
  pending.value = pending.value.includes(value)
    ? pending.value.filter((v) => v !== value)
    : [...pending.value, value]
}

function applyCategory() {
  activeCategory.value?.set([...pending.value])
  filterStep.value = null
}

function removeChip(chip: { category: 'Tamanho' | 'Cor'; value: string }) {
  if (chip.category === 'Tamanho') {
    selectedSizes.value = selectedSizes.value.filter((v) => v !== chip.value)
  } else {
    selectedColors.value = selectedColors.value.filter((v) => v !== chip.value)
  }
}

function clearFilters() {
  selectedSizes.value = []
  selectedColors.value = []
}

function select(item: SellableProductItem) {
  emit('select', item)
  close()
}

// Values that vanish from an axis (because the query changed) must not keep filtering
// the list -- otherwise the picker can get stuck showing nothing with no visible cause.
watch(
  () => props.items,
  () => {
    selectedSizes.value = selectedSizes.value.filter((v) => sizeOptions.value.includes(v))
    selectedColors.value = selectedColors.value.filter((v) => colorOptions.value.includes(v))
  },
)
</script>

<style scoped>
.product-picker {
  min-width: 0;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

.product-picker-trigger {
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

.product-picker-trigger-open {
  border-color: var(--pm-accent);
}

.product-picker-placeholder {
  color: var(--pm-placeholder);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-picker-caret {
  font-size: 10px;
  color: var(--pm-text-muted);
  transition: transform 0.15s;
  flex-shrink: 0;
}

.product-picker-caret-open {
  transform: rotate(180deg);
}

.product-picker-panel {
  position: fixed;
  z-index: 9999;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 28px rgba(0, 0, 0, 0.12);
  font-family: var(--pm-font);
}

.product-picker-search-row {
  padding: 8px 8px 4px;
}

.product-picker-search,
.product-picker-filter-search {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  padding: 7px 10px;
  font-size: 13px;
  font-family: var(--pm-font);
  outline: none;
  color: var(--pm-text-dark);
}

.product-picker-filters {
  position: relative;
  padding: 4px 10px 8px;
  border-bottom: 1px solid var(--pm-border-light);
}

.product-picker-filters-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.product-picker-filters-btn {
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  background: var(--pm-white);
  color: var(--pm-text-dark);
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  cursor: pointer;
  user-select: none;
}

.product-picker-filters-btn-open {
  background: var(--pm-accent);
  border-color: var(--pm-accent);
  color: var(--pm-white);
}

.product-picker-filters-btn-open .product-picker-caret {
  color: var(--pm-white);
}

.product-picker-chip {
  height: 24px;
  padding: 0 6px 0 9px;
  border-radius: 12px;
  background: var(--pm-accent-bg);
  border: 1px solid var(--pm-accent-bg);
  color: var(--pm-accent-text);
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
}

.product-picker-chip-remove {
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
}

.product-picker-clear {
  font-size: 11px;
  color: var(--pm-text-muted);
  cursor: pointer;
  text-decoration: underline;
}

.product-picker-filter-menu,
.product-picker-filter-values {
  position: absolute;
  top: 34px;
  left: 10px;
  z-index: 50;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.product-picker-filter-menu {
  min-width: 170px;
}

.product-picker-filter-values {
  width: 210px;
}

.product-picker-filter-cat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--pm-text-dark);
  cursor: pointer;
  border-bottom: 1px solid var(--pm-bg);
}

.product-picker-filter-cat:last-child {
  border-bottom: none;
}

.product-picker-filter-cat:hover {
  background: var(--pm-bg);
}

.product-picker-filter-cat-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.product-picker-filter-count {
  font-size: 10px;
  font-weight: 700;
  background: var(--pm-accent);
  color: var(--pm-white);
  border-radius: 8px;
  padding: 0 6px;
}

.product-picker-filter-chevron {
  color: var(--pm-text-muted);
}

.product-picker-filter-values-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border-bottom: 1px solid var(--pm-bg);
}

.product-picker-filter-back {
  cursor: pointer;
  font-size: 15px;
  color: var(--pm-text-muted);
  line-height: 1;
}

.product-picker-filter-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.product-picker-filter-search {
  width: calc(100% - 16px);
  margin: 6px 8px 4px;
  padding: 5px 9px;
  font-size: 12px;
}

.product-picker-filter-list {
  min-height: 96px;
  padding: 2px 0;
}

.product-picker-filter-none {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--pm-placeholder);
}

.product-picker-filter-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--pm-text-dark);
  cursor: pointer;
}

.product-picker-filter-option:hover {
  background: var(--pm-bg);
}

.product-picker-checkbox {
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

.product-picker-checkbox-checked {
  background: var(--pm-accent);
  border-color: var(--pm-accent);
}

.product-picker-filter-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 10px;
  border-top: 1px solid var(--pm-bg);
}

.product-picker-pager-btn {
  width: 22px;
  height: 22px;
  border: 1px solid var(--pm-border-light);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--pm-text-dark);
  cursor: pointer;
}

.product-picker-pager-label {
  font-size: 11px;
  color: var(--pm-text-muted);
}

.product-picker-filter-actions {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-top: 1px solid var(--pm-bg);
}

.product-picker-filter-clear,
.product-picker-filter-apply {
  flex: 1;
  text-align: center;
  padding: 5px 0;
  font-size: 12px;
  cursor: pointer;
}

.product-picker-filter-clear {
  color: var(--pm-text-muted);
}

.product-picker-filter-apply {
  font-weight: 600;
  color: var(--pm-white);
  background: var(--pm-accent);
  border-radius: 6px;
}

.product-picker-list {
  max-height: 260px;
  overflow-y: auto;
}

.product-picker-empty {
  padding: 10px 14px;
  font-size: 13px;
  color: var(--pm-placeholder);
}

.product-picker-empty-error {
  color: var(--pm-error);
}

.product-picker-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--pm-bg);
  cursor: pointer;
}

.product-picker-row:last-child {
  border-bottom: none;
}

.product-picker-row:hover {
  background: var(--pm-bg);
}

/* Wider than the wireframe's 78px: real variation SKUs (2408-44-VERMELHA) run far
   longer than its sample data, and truncating the SKU defeats searching by it. */
.product-picker-sku {
  font-size: 11px;
  color: var(--pm-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  width: 140px;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-picker-name {
  font-size: 13px;
  color: var(--pm-text-dark);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-picker-price {
  font-size: 12px;
  color: var(--pm-text-mid);
  width: 82px;
  text-align: right;
  flex-shrink: 0;
}
</style>
