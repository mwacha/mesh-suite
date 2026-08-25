<template>
  <div class="segmented-control" :class="{ 'segmented-control-disabled': disabled }">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="segmented-option"
      :class="{ 'segmented-option-active': opt.value === modelValue }"
      :disabled="disabled || opt.disabled"
      :data-test="testId ? `${testId}-${opt.value}` : undefined"
      @click="$emit('update:modelValue', opt.value)"
    >
      {{ opt.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
export interface SegmentedOption {
  value: string
  label: string
  disabled?: boolean
}

withDefaults(
  defineProps<{ modelValue: string; options: SegmentedOption[]; disabled?: boolean; testId?: string }>(),
  { disabled: false },
)
defineEmits<{ 'update:modelValue': [valor: string] }>()
</script>

<style scoped>
.segmented-control {
  display: flex;
  gap: 6px;
}

.segmented-control-disabled {
  opacity: 0.45;
}

.segmented-option {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.segmented-option:disabled {
  cursor: not-allowed;
}

.segmented-option-active {
  background: var(--pm-accent);
  color: var(--pm-white);
  border-color: var(--pm-accent);
}
</style>
