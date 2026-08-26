<template>
  <div class="tipo-produto">
    <div
      v-for="opt in OPTIONS"
      :key="opt.value"
      class="tipo-tile"
      :class="{ 'tipo-tile-active': opt.value === modelValue, 'tipo-tile-disabled': disabled }"
      :data-test="`tipo-produto-${opt.value}`"
      @click="!disabled && $emit('update:modelValue', opt.value)"
    >
      <div class="tipo-tile-icon">{{ opt.icon }}</div>
      <div class="tipo-tile-label">{{ opt.label }}</div>
      <div class="tipo-tile-desc">{{ opt.description }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ProductType } from '@/api/products'

withDefaults(defineProps<{ modelValue: ProductType; disabled?: boolean }>(), { disabled: false })
defineEmits<{ 'update:modelValue': [tipo: ProductType] }>()

const OPTIONS: { value: ProductType; icon: string; label: string; description: string }[] = [
  { value: 'PRODUCT', icon: '📦', label: 'Simples', description: 'Produto único sem variações' },
  { value: 'PRODUCT_KIT', icon: '🎁', label: 'Kit', description: 'Conjunto de produtos' },
  { value: 'VARIATION_PARENT', icon: '👕', label: 'Com Variação', description: 'Tamanho, cor, etc.' },
]
</script>

<style scoped>
.tipo-produto {
  display: flex;
  gap: 10px;
}

.tipo-tile {
  flex: 1;
  border: 2px solid var(--pm-border-light);
  border-radius: 7px;
  padding: 12px;
  cursor: pointer;
  background: var(--pm-white);
}

.tipo-tile-active {
  border-color: var(--pm-accent);
  background: var(--pm-accent-bg);
}

.tipo-tile-disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.tipo-tile-icon {
  font-size: 22px;
  margin-bottom: 4px;
}

.tipo-tile-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--pm-text-dark);
  font-family: var(--pm-font);
}

.tipo-tile-active .tipo-tile-label {
  color: var(--pm-accent-text);
}

.tipo-tile-desc {
  font-size: 11px;
  color: var(--pm-text-muted);
  font-family: var(--pm-font);
}
</style>
