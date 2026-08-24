<template>
  <div class="pill-field">
    <label v-if="label" class="field-label">{{ label }}</label>
    <div class="pills">
      <button
        v-for="opt in options"
        :key="opt.value"
        type="button"
        class="pill"
        :class="[opt.value === modelValue ? `pill--${opt.tone ?? 'ativo'}` : '']"
        @click="() => emit('update:modelValue', opt.value)"
      >
        <span class="dot"></span>
        {{ opt.label }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts" generic="T extends string">
defineProps<{
  modelValue: T
  options: { value: T; label: string; tone?: 'ativo' | 'inativo' }[]
  label?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [valor: T]
}>()
</script>

<style scoped>
.pill-field {
  margin-bottom: 10px;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

.pills {
  display: flex;
  gap: 8px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 18px;
  border: 2px solid var(--pm-border-light);
  border-radius: 20px;
  background: var(--pm-white);
  color: var(--pm-text-muted);
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--pm-border-light);
}

.pill--ativo {
  border-color: var(--pm-success);
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.pill--ativo .dot {
  background: var(--pm-success);
}

.pill--inativo {
  border-color: var(--pm-text-mid);
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.pill--inativo .dot {
  background: var(--pm-text-mid);
}
</style>
