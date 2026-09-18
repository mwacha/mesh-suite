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
          @input="onSearchInput"
          @keydown.down.prevent="moveHighlight(1)"
          @keydown.up.prevent="moveHighlight(-1)"
          @keydown.enter.prevent="selectHighlighted"
          @keydown.esc.prevent="close"
        />
        <div class="search-select-list">
          <div v-if="loading" class="search-select-empty" :data-test="testId && `${testId}-loading`">Carregando...</div>
          <div
            v-else-if="visibleItems.length === 0"
            class="search-select-empty"
            :class="{ 'search-select-empty-error': emptyIsError }"
          >
            {{ emptyMessage }}
          </div>
          <div
            v-for="(item, index) in visibleItems"
            :key="item.id"
            class="search-select-item"
            :class="{
              'search-select-item-active': item.id === modelValue,
              'search-select-item-highlighted': index === highlightedIndex,
            }"
            :data-test="testId && `${testId}-option-${item.id}`"
            @mouseenter="highlightedIndex = index"
            @click="select(item)"
          >
            <span class="search-select-item-label">
              <template v-for="(part, partIndex) in highlightParts(item.label)" :key="partIndex">
                <mark v-if="part.match" class="search-select-match">{{ part.text }}</mark>
                <template v-else>{{ part.text }}</template>
              </template>
            </span>
            <span v-if="item.sublabel" class="search-select-item-sublabel">{{ item.sublabel }}</span>
          </div>
        </div>
        <button
          v-if="canShowMore"
          type="button"
          class="search-select-more"
          :data-test="testId && `${testId}-more`"
          @click="showMore"
        >
          <span class="search-select-more-caret">▾</span> {{ moreLabel }}
        </button>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useDropdownPosition } from '@/composables/useDropdownPosition'
import { normalizarTexto } from '@/utils/texto'

export interface SearchSelectItem {
  id: string
  label: string
  sublabel?: string
}

const props = withDefaults(
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
    /** How many options to reveal at a time; the rest come in via "Exibir mais". */
    pageSize?: number
    /** Filter `items` in the component instead of relying on the parent to answer `@search`. */
    filterLocally?: boolean
    /**
     * Server-side paging: pass it and the component stops slicing `items`,
     * showing the "Exibir mais" button while true and emitting `load-more`
     * instead of revealing locally held options.
     */
    hasMore?: boolean
    loading?: boolean
    moreLabel?: string
  }>(),
  {
    placeholder: 'Selecione...',
    emptyMessage: 'Nenhum resultado',
    pageSize: 10,
    moreLabel: 'Exibir mais',
    // Vue casts an absent Boolean prop to `false`; this keeps it `undefined` so
    // "not passed" stays distinguishable from "passed as false" below.
    hasMore: undefined,
  },
)

const emit = defineEmits<{
  'update:modelValue': [id: string]
  select: [item: SearchSelectItem]
  search: [query: string]
  'load-more': []
  open: []
}>()

const triggerRef = ref<HTMLElement | null>(null)
const menuRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLInputElement | null>(null)
const query = ref('')
const visibleCount = ref(props.pageSize)
const highlightedIndex = ref(0)
// ~420px = busca + uma página cheia de 10 opções + rodapé "Exibir mais"; é o que
// decide se o painel abre para baixo ou para cima.
const { open, position, toggle, close } = useDropdownPosition(triggerRef, menuRef, 420, {
  matchTriggerWidth: true,
  minWidth: 260,
})

// `hasMore` left undefined means the parent hands over the whole option list and
// the component pages through it; passing it hands paging back to the parent.
const serverPaged = computed(() => props.hasMore !== undefined)

const filteredItems = computed(() => {
  const term = query.value.trim()
  if (!props.filterLocally || !term) {
    return props.items
  }
  const normalized = normalizarTexto(term)
  return props.items.filter((item) => normalizarTexto(item.label).includes(normalized))
})

const visibleItems = computed(() =>
  serverPaged.value ? filteredItems.value : filteredItems.value.slice(0, visibleCount.value),
)

const canShowMore = computed(() =>
  serverPaged.value ? !!props.hasMore : filteredItems.value.length > visibleCount.value,
)

watch(query, () => {
  visibleCount.value = props.pageSize
  highlightedIndex.value = 0
})

// NFD-stripping preserves length (an accented letter becomes base letter +
// combining mark, and only the mark is dropped), so offsets found in the
// normalized label still line up with the original one.
function highlightParts(label: string): { text: string; match: boolean }[] {
  const term = query.value.trim()
  if (!term) {
    return [{ text: label, match: false }]
  }
  const start = normalizarTexto(label).indexOf(normalizarTexto(term))
  if (start === -1) {
    return [{ text: label, match: false }]
  }
  const end = start + term.length
  return [
    { text: label.slice(0, start), match: false },
    { text: label.slice(start, end), match: true },
    { text: label.slice(end), match: false },
  ].filter((part) => part.text !== '')
}

function onTriggerClick() {
  const opening = !open.value
  toggle()
  if (opening) {
    query.value = ''
    visibleCount.value = props.pageSize
    highlightedIndex.value = 0
    emit('search', '')
    emit('open')
    nextTick(() => inputRef.value?.focus())
  }
}

function onSearchInput() {
  emit('search', query.value)
}

function showMore() {
  if (serverPaged.value) {
    emit('load-more')
    return
  }
  visibleCount.value += props.pageSize
}

function moveHighlight(step: number) {
  const total = visibleItems.value.length
  if (total === 0) {
    return
  }
  highlightedIndex.value = (highlightedIndex.value + step + total) % total
}

function selectHighlighted() {
  const item = visibleItems.value[highlightedIndex.value]
  if (item) {
    select(item)
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
  /* Fits a full page of 10 options (~33px each) without an inner scrollbar, so
     the page boundary is visible instead of looking like an endless list. Only
     once "Exibir mais" appends further pages does the list start scrolling. */
  max-height: 340px;
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

.search-select-item-highlighted {
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

.search-select-match {
  background: none;
  color: inherit;
  font-weight: 700;
}

.search-select-more {
  width: 100%;
  border: none;
  border-top: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  padding: 9px 12px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--pm-font);
  color: var(--pm-accent);
  text-align: left;
  cursor: pointer;
}

.search-select-more:hover {
  background: var(--pm-bg);
}

.search-select-more-caret {
  font-size: 10px;
  margin-right: 4px;
}
</style>
