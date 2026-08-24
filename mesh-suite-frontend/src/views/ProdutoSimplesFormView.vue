<template>
  <AppShell :title="modoEdicao ? 'Editar Produto' : 'Novo Produto'">
    <form class="pm-form" @submit.prevent="salvar">
      <TipoProdutoSelector v-if="!modoEdicao" tipo-atual="SIMPLES" />

      <section class="pm-card">
        <h2>Informações Gerais</h2>
        <TextField v-model="form.nome" label="Nome do Produto" required :error="erros.nome" test-id="nome" />
        <div class="pm-grid pm-grid-2">
          <TextField v-model="form.sku" label="Código SKU" required :error="erros.sku" test-id="sku" />
          <TextField v-model="form.codigoBarras" label="Código de Barra (EAN/GTIN)" placeholder="7891234567890" />
          <MarcaCategoriaFields v-model:marca="form.marca" v-model:categoria="form.categoria" />
          <NumberField
            v-model="form.precoVenda"
            label="Preço de Venda"
            required
            :error="erros.precoVenda"
            step="0.01"
            :min="0"
            test-id="preco-venda"
          />
          <NumberField v-model="form.precoCusto" label="Preço de Custo" step="0.01" :min="0" test-id="preco-custo" />
        </div>
        <StatusPillGroup v-model="form.status" label="Status" :options="STATUS_OPCOES" />
        <TextField v-model="form.descricao" label="Descrição" placeholder="Descreva o produto..." />
      </section>

      <div class="pm-grid-cards">
        <EstoqueCard
          v-model:quantidade-estoque="form.quantidadeEstoque"
          v-model:unidade-medida="form.unidadeMedida"
          v-model:estoque-minimo="form.estoqueMinimo"
          v-model:estoque-maximo="form.estoqueMaximo"
        />
        <PesosDimensoesCard
          v-model:peso="form.peso"
          v-model:comprimento="form.comprimento"
          v-model:largura="form.largura"
          v-model:altura="form.altura"
        />
      </div>

      <p v-if="erroGeral" class="pm-error-geral">{{ erroGeral }}</p>

      <div class="pm-actions">
        <button type="button" class="pm-btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="pm-btn-primary" :disabled="salvando">Salvar Produto</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import AppShell from '@/components/AppShell.vue'
import TextField from '@/components/TextField.vue'
import NumberField from '@/components/NumberField.vue'
import StatusPillGroup from '@/components/StatusPillGroup.vue'
import TipoProdutoSelector from '@/components/produtos/TipoProdutoSelector.vue'
import MarcaCategoriaFields from '@/components/produtos/MarcaCategoriaFields.vue'
import EstoqueCard from '@/components/produtos/EstoqueCard.vue'
import PesosDimensoesCard from '@/components/produtos/PesosDimensoesCard.vue'
import { useProdutoSimplesController } from '@/composables/produtos/useProdutoSimplesController'

const STATUS_OPCOES = [
  { value: 'ATIVO', label: 'Ativo', tone: 'ativo' as const },
  { value: 'INATIVO', label: 'Inativo', tone: 'inativo' as const },
]

const { modoEdicao, form, erros, erroGeral, salvando, salvar, cancelar } = useProdutoSimplesController()
</script>
