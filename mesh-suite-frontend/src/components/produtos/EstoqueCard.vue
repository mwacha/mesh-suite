<template>
  <section class="pm-card">
    <h2>Estoque</h2>
    <div class="pm-grid pm-grid-2">
      <NumberField v-model="quantidadeEstoque" label="Qtd. em Estoque" :min="0" />
      <SelectField v-if="mostrarUnidade" v-model="unidadeMedida" label="Unidade de Medida" :options="OPCOES_UNIDADE" />
      <NumberField v-model="estoqueMinimo" label="Estoque Mínimo" :min="0" />
      <NumberField v-model="estoqueMaximo" label="Estoque Máximo" :min="0" />
    </div>
  </section>
</template>

<script setup lang="ts">
import NumberField from '@/components/NumberField.vue'
import SelectField from '@/components/SelectField.vue'
import type { UnidadeMedida } from '@/api/produtos'

withDefaults(defineProps<{ mostrarUnidade?: boolean }>(), { mostrarUnidade: true })

const UNIDADES: UnidadeMedida[] = ['UN', 'KG', 'G', 'L', 'ML', 'MT', 'CM', 'CX', 'PC', 'PAR', 'DZ']
const OPCOES_UNIDADE = UNIDADES.map((u) => ({ value: u, label: u }))

const quantidadeEstoque = defineModel<number>('quantidadeEstoque', { required: true })
const unidadeMedida = defineModel<UnidadeMedida>('unidadeMedida', { required: true })
const estoqueMinimo = defineModel<number | null>('estoqueMinimo', { required: true })
const estoqueMaximo = defineModel<number | null>('estoqueMaximo', { required: true })
</script>
