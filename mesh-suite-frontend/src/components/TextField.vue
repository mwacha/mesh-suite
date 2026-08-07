<template>
  <div class="text-field">
    <label v-if="label" class="field-label">
      {{ label }}<span v-if="required" class="required-mark">*</span>
    </label>
    <input
      :value="displayValue"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :data-test="testId"
      :class="{ 'input-error': !!error }"
      @input="onInput"
      @blur="$emit('blur')"
    />
    <p v-if="error" class="field-error">⚠️ {{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue: string
  label?: string
  required?: boolean
  error?: string
  placeholder?: string
  maxlength?: number
  testId?: string
  mask?: (valor: string) => string
}>()

const emit = defineEmits<{
  'update:modelValue': [valor: string]
  blur: []
}>()

const displayValue = computed(() => (props.mask ? props.mask(props.modelValue) : props.modelValue))

function onInput(event: Event) {
  const raw = (event.target as HTMLInputElement).value
  emit('update:modelValue', props.mask ? props.mask(raw) : raw)
}
</script>

<style scoped>
.text-field {
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

.input-error {
  border-color: var(--pm-error);
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 4px 0 0;
}
</style>
