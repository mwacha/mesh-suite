<template>
  <div class="money-field">
    <label v-if="label" class="field-label">
      {{ label }}<span v-if="required" class="required-mark">*</span>
    </label>
    <div class="money-input" :class="{ 'money-input-error': !!error }">
      <input
        :value="displayValue"
        type="text"
        inputmode="numeric"
        class="money-input-native"
        :placeholder="placeholder ?? zeroDisplay"
        :data-test="testId"
        @input="onInput"
        @blur="$emit('blur')"
      />
    </div>
    <p v-if="error" class="field-error">⚠️ {{ error }}</p>
  </div>
</template>

<script setup lang="ts">
// Reusable masked money input: digits typed fill in from the right (like a
// calculator display), formatted live as pt-BR currency (thousands ".",
// decimals ","). `decimalPlaces` controls how many digits count as the
// fractional part -- 2 for R$ (the default), but callers can pass a
// different value for anything else priced with more/fewer decimals.
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: number | null
    decimalPlaces?: number
    label?: string
    required?: boolean
    error?: string
    placeholder?: string
    testId?: string
  }>(),
  { decimalPlaces: 2 },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  blur: []
}>()

function format(value: number): string {
  return value.toLocaleString('pt-BR', {
    minimumFractionDigits: props.decimalPlaces,
    maximumFractionDigits: props.decimalPlaces,
  })
}

const zeroDisplay = format(0)

const displayValue = ref(props.modelValue != null ? format(props.modelValue) : '')

// Keeps the display in sync when modelValue changes from the OUTSIDE (e.g.
// loading existing data into the form after mount) -- onInput below already
// keeps it in sync for the user's own typing, so this only fires for
// external assignments to avoid fighting the cursor mid-edit.
watch(
  () => props.modelValue,
  (value) => {
    const next = value != null ? format(value) : ''
    if (next !== displayValue.value) {
      displayValue.value = next
    }
  },
)

function onInput(event: Event) {
  const digits = (event.target as HTMLInputElement).value.replace(/\D/g, '')
  if (digits === '') {
    displayValue.value = ''
    emit('update:modelValue', null)
    return
  }
  const numeric = parseInt(digits, 10) / 10 ** props.decimalPlaces
  displayValue.value = format(numeric)
  emit('update:modelValue', numeric)
}
</script>

<style scoped>
.money-field {
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

.money-input {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
}

.money-input-error {
  border-color: var(--pm-error);
}

.money-input-native {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  padding: 0;
  background: transparent;
  color: var(--pm-text-dark);
  font-size: 13px;
  font-family: var(--pm-font);
  text-align: right;
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 4px 0 0;
}
</style>
