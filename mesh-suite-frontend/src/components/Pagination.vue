<template>
  <div class="pagination">
    <div class="pagination-size">
      <select
        class="pagination-size-select"
        data-test="pagination-size"
        :value="size"
        @change="emit('update:size', Number(($event.target as HTMLSelectElement).value))"
      >
        <option v-for="opt in sizeOptions" :key="opt" :value="opt">{{ opt }}</option>
      </select>
      <span class="pagination-size-label">Registros por página</span>
      <span class="pagination-range" data-test="pagination-range">{{ rangeLabel }}</span>
    </div>

    <div class="pagination-pages">
      <button
        type="button"
        class="pagination-btn"
        data-test="pagination-prev"
        :disabled="number === 0"
        @click="emit('update:page', number - 1)"
      >
        ‹
      </button>
      <template v-for="(p, i) in pages" :key="i">
        <span v-if="p === '...'" class="pagination-ellipsis">…</span>
        <button
          v-else
          type="button"
          class="pagination-btn"
          :class="{ 'pagination-btn-active': p === number }"
          :data-test="`pagination-page-${p + 1}`"
          @click="emit('update:page', p)"
        >
          {{ p + 1 }}
        </button>
      </template>
      <button
        type="button"
        class="pagination-btn"
        data-test="pagination-next"
        :disabled="number + 1 >= totalPages"
        @click="emit('update:page', number + 1)"
      >
        ›
      </button>
    </div>

    <div class="pagination-jump">
      <span class="pagination-jump-label">Ir para página</span>
      <input
        class="pagination-jump-input"
        type="number"
        min="1"
        :max="Math.max(totalPages, 1)"
        data-test="pagination-jump-input"
        :value="number + 1"
        @change="onJump"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const { number, totalPages, totalElements, size, sizeOptions = [10, 20, 50, 100] } = defineProps<{
  number: number
  totalPages: number
  totalElements: number
  size: number
  sizeOptions?: number[]
}>()

const emit = defineEmits<{
  'update:page': [page: number]
  'update:size': [size: number]
}>()

const rangeLabel = computed(() => {
  if (totalElements === 0) {
    return '0 registros'
  }
  const start = number * size + 1
  const end = Math.min((number + 1) * size, totalElements)
  return `${start}–${end} de ${totalElements}`
})

const pages = computed<(number | '...')[]>(() => {
  const total = Math.max(totalPages, 1)
  const current = number
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i)
  }
  const result: (number | '...')[] = [0]
  if (current > 2) {
    result.push('...')
  }
  for (let i = Math.max(1, current - 1); i <= Math.min(total - 2, current + 1); i++) {
    result.push(i)
  }
  if (current < total - 3) {
    result.push('...')
  }
  result.push(total - 1)
  return result
})

function onJump(event: Event) {
  const raw = Number((event.target as HTMLInputElement).value)
  if (!Number.isFinite(raw)) {
    return
  }
  const clamped = Math.min(Math.max(Math.trunc(raw), 1), Math.max(totalPages, 1))
  emit('update:page', clamped - 1)
}
</script>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  font-family: var(--pm-font);
  font-size: 12px;
  color: var(--pm-text-muted);
}

.pagination-size,
.pagination-jump {
  display: flex;
  align-items: center;
  gap: 7px;
}

.pagination-size-select,
.pagination-jump-input {
  height: 28px;
  border: 1px solid var(--pm-border-light);
  border-radius: 4px;
  background: var(--pm-white);
  color: var(--pm-text-dark);
  font-size: 12px;
  font-family: var(--pm-font);
  padding: 0 8px;
}

.pagination-jump-input {
  width: 48px;
  text-align: center;
}

.pagination-range {
  color: var(--pm-text-muted);
}

.pagination-pages {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pagination-btn {
  width: 26px;
  height: 26px;
  border: 1px solid var(--pm-border-light);
  border-radius: 4px;
  background: var(--pm-white);
  color: var(--pm-text-dark);
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pagination-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pagination-btn-active {
  background: var(--pm-accent);
  border-color: var(--pm-accent);
  color: var(--pm-white);
}

.pagination-ellipsis {
  width: 26px;
  text-align: center;
}
</style>
