<template>
  <section class="pm-card">
    <h2>Tipo de Produto</h2>
    <div class="pm-tipo-grid">
      <button
        v-for="opt in OPCOES"
        :key="opt.tipo"
        type="button"
        class="pm-tipo-card"
        :class="{ 'pm-tipo-card--ativo': opt.tipo === tipoAtual }"
        :disabled="opt.tipo === tipoAtual"
        @click="selecionar(opt.tipo)"
      >
        <span class="pm-tipo-icone">{{ opt.icone }}</span>
        <span class="pm-tipo-label">{{ opt.label }}</span>
        <span class="pm-tipo-descricao">{{ opt.descricao }}</span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

export type TipoProduto = 'SIMPLES' | 'KIT' | 'VARIACAO'

const props = defineProps<{ tipoAtual: TipoProduto }>()

const ROTAS: Record<TipoProduto, string> = {
  SIMPLES: 'produtos-novo-simples',
  KIT: 'produtos-novo-kit',
  VARIACAO: 'produtos-novo-variacao',
}

const OPCOES: { tipo: TipoProduto; icone: string; label: string; descricao: string }[] = [
  { tipo: 'SIMPLES', icone: '📦', label: 'Simples', descricao: 'Produto único sem variações' },
  { tipo: 'KIT', icone: '🎁', label: 'Kit', descricao: 'Conjunto de produtos' },
  { tipo: 'VARIACAO', icone: '👕', label: 'Com Variação', descricao: 'Tamanho, cor, etc.' },
]

const router = useRouter()

function selecionar(tipo: TipoProduto) {
  if (tipo === props.tipoAtual) return
  router.push({ name: ROTAS[tipo] })
}
</script>
