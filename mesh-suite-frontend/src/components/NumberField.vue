<template>
  <div class="number-field">
    <label v-if="label" class="field-label">
      {{ label }}<span v-if="required" class="required-mark">*</span>
    </label>
    <input
      :value="modelValue ?? ''"
      type="number"
      :step="step"
      :min="min"
      :placeholder="placeholder"
      :disabled="disabled"
      :data-test="testId"
      :class="{ 'input-error': !!error }"
      @input="onInput"
    />
    <p v-if="error" class="field-error">⚠️ {{ error }}</p>
  </div>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue: number | null
    label?: string
    required?: boolean
    error?: string
    placeholder?: string
    step?: number | string
    min?: number | string
    disabled?: boolean
    testId?: string
  }>(),
  { step: 1 },
)

const emit = defineEmits<{
  'update:modelValue': [valor: number | null]
}>()

function onInput(event: Event) {
  const raw = (event.target as HTMLInputElement).value
  emit('update:modelValue', raw === '' ? null : Number(raw))
}
</script>

<style scoped>
.number-field {
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

input {
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

input:disabled {
  background: var(--pm-bg);
  color: var(--pm-text-muted);
  cursor: not-allowed;
}

.input-error {
  border-color: var(--pm-error);
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 4px 0 0;
}
</style>
