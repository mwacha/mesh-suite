<template>
  <div class="segmented-control" :class="[`segmented-control-${variant}`, { 'segmented-control-disabled': disabled }]">
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
      <span v-if="variant === 'status'" class="segmented-dot"></span>
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

/**
 * `status` reproduz o pill de status dos wireframes (contorno arredondado,
 * verde com bolinha quando selecionado) -- usado onde a tela alterna
 * Ativo/Inativo. `default` é o segmento azul usado nas demais escolhas.
 */
export type SegmentedVariant = 'default' | 'status'

withDefaults(
  defineProps<{
    modelValue: string
    options: SegmentedOption[]
    disabled?: boolean
    testId?: string
    variant?: SegmentedVariant
  }>(),
  { disabled: false, variant: 'default' },
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

.segmented-control-status {
  gap: 8px;
}

.segmented-control-status .segmented-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 2px solid var(--pm-border-light);
  border-radius: 999px;
  padding: 5px 18px;
  background: var(--pm-white);
  color: var(--pm-text-muted);
}

.segmented-control-status .segmented-option-active {
  border-color: var(--pm-success);
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.segmented-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--pm-border-light);
}

.segmented-control-status .segmented-option-active .segmented-dot {
  background: var(--pm-success);
}
</style>
