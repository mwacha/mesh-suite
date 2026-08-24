<template>
  <div class="select-field">
    <label v-if="label" class="field-label">
      {{ label }}<span v-if="required" class="required-mark">*</span>
    </label>
    <select :value="modelValue" :disabled="disabled" :data-test="testId" @change="onChange">
      <option v-for="opt in options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </select>
    <p v-if="error" class="field-error">⚠️ {{ error }}</p>
  </div>
</template>

<script setup lang="ts" generic="T extends string">
const props = defineProps<{
  modelValue: T
  options: { value: T; label: string }[]
  label?: string
  required?: boolean
  error?: string
  disabled?: boolean
  testId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [valor: T]
}>()

function onChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLSelectElement).value as T)
}
</script>

<style scoped>
.select-field {
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

select {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  color: var(--pm-text-dark);
  font-size: 13px;
  font-family: var(--pm-font);
}

select:disabled {
  background: var(--pm-bg);
  color: var(--pm-text-muted);
  cursor: not-allowed;
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 4px 0 0;
}
</style>
