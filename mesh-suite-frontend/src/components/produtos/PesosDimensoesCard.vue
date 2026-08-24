<template>
  <section class="pm-card">
    <h2>Pesos &amp; Dimensões</h2>
    <div class="pm-grid pm-grid-2">
      <NumberField v-model="peso" label="Peso (kg)" step="0.001" :min="0" />
      <NumberField v-model="comprimento" label="Comprimento (cm)" step="0.01" :min="0" />
      <NumberField v-model="largura" label="Largura (cm)" step="0.01" :min="0" />
      <NumberField v-model="altura" label="Altura (cm)" step="0.01" :min="0" />
    </div>
  </section>
</template>

<script setup lang="ts">
import NumberField from '@/components/NumberField.vue'

const peso = defineModel<number | null>('peso', { required: true })
const comprimento = defineModel<number | null>('comprimento', { required: true })
const largura = defineModel<number | null>('largura', { required: true })
const altura = defineModel<number | null>('altura', { required: true })
</script>
