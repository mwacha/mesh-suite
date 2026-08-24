<template>
  <AppShell title="Produtos">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Produtos" :count="countLabel">
      <button type="button" class="btn-primary" data-test="novo-produto" @click="novoProduto">+ Novo Produto</button>
    </PageHeader>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar produto por nome ou SKU..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="ATIVO">Ativo</option>
        <option value="INATIVO">Inativo</option>
      </select>
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Produtos</span>
        <div v-if="resumo" class="table-card-stats">
          <StatPill :value="resumo.total" label="Total" color="dark" />
          <StatPill :value="resumo.ativos" label="Ativos" color="green" />
          <StatPill :value="resumo.inativos" label="Inativos" color="red" />
        </div>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Código</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort('nome')">
            Produto
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'nome' }">{{ sortIcon('nome') }}</span>
          </div>
          <div class="table-grid-col">Marca</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-preco" @click="toggleSort('precoVenda')">
            Preço de Venda
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'precoVenda' }">{{ sortIcon('precoVenda') }}</span>
          </div>
          <div class="table-grid-col">Estoque</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-status" @click="toggleSort('status')">
            Status
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'status' }">{{ sortIcon('status') }}</span>
          </div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="produto in pagina.content"
          :key="produto.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${produto.id}`"
          @click="editarProduto(produto)"
        >
          <div class="table-grid-cell">{{ produto.sku }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ produto.nome }}</div>
          <div class="table-grid-cell">{{ produto.marca }}</div>
          <div class="table-grid-cell">{{ formatarPreco(produto.precoVenda) }}</div>
          <div class="table-grid-cell">{{ produto.quantidadeEstoque }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(produto.status)" :color="produto.status === 'ATIVO' ? 'green' : 'red'" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(produto)" :test-id="`btn-acoes-${produto.id}`" />
          </div>
        </div>
      </div>
    </section>

    <Pagination
      :number="pagina.number"
      :total-pages="pagina.totalPages"
      :total-elements="pagina.totalElements"
      :size="pagina.size"
      @update:page="carregar"
      @update:size="onSizeChange"
    />
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import StatPill from '@/components/StatPill.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import {
  listarProdutos,
  buscarResumoProdutos,
  atualizarStatusProduto,
  excluirProduto,
  type ProdutoSummary,
  type ProdutoResumo,
  type Page as ApiPage,
  type StatusProduto,
} from '@/api/produtos'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<ProdutoSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<ProdutoResumo | null>(null)
const sortField = ref<'nome' | 'precoVenda' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

const countLabel = computed(() => (resumo.value ? `${resumo.value.total} produtos cadastrados` : undefined))

function statusLabel(status: StatusProduto) {
  return { ATIVO: 'Ativo', INATIVO: 'Inativo' }[status]
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function sortIcon(field: 'nome' | 'precoVenda' | 'status') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'nome' | 'precoVenda' | 'status') {
  if (sortField.value === field) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortDir.value = 'asc'
  }
  carregar(0)
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listarProdutos({
      busca: filtros.busca || undefined,
      status: (filtros.status || undefined) as StatusProduto | undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de produtos.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await buscarResumoProdutos()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de produtos.'
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

function novoProduto() {
  router.push({ name: 'produtos-novo-simples' })
}

// Produto Simples and Kit still share the generic edit route/form (Kit editing
// isn't implemented yet either -- out of scope here); only Variação has its
// own type-specific edit screen so far.
function editarProduto(produto: ProdutoSummary) {
  const name = produto.tipo === 'VARIATION_PARENT' ? 'produtos-editar-variacao' : 'produtos-editar'
  router.push({ name, params: { id: produto.id } })
}

async function alternarStatus(produto: ProdutoSummary) {
  erro.value = ''
  const novoStatus = produto.status === 'INATIVO' ? 'ATIVO' : 'INATIVO'
  try {
    await atualizarStatusProduto(produto.id, novoStatus)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status.'
  }
}

async function excluir(produto: ProdutoSummary) {
  if (!confirm(`Excluir o produto "${produto.nome}"?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirProduto(produto.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir o produto.'
  }
}

function acoesPara(produto: ProdutoSummary): ActionsMenuItem[] {
  return [
    { label: 'Editar', action: () => editarProduto(produto), testId: 'acao-editar' },
    {
      label: produto.status === 'INATIVO' ? 'Ativar' : 'Inativar',
      action: () => alternarStatus(produto),
      testId: 'acao-status',
    },
    { label: 'Excluir', action: () => excluir(produto), danger: true, testId: 'acao-excluir' },
  ]
}

onMounted(() => {
  carregar(0)
  carregarResumo()
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  font-family: var(--pm-font);
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  font-family: var(--pm-font);
}

.busca {
  flex: 1;
}

.toolbar input,
.toolbar select {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  background: var(--pm-white);
}

.table-card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 12px;
}

.table-card-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--pm-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: var(--pm-font);
}

.table-card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.table-card-stats {
  display: flex;
  gap: 8px;
}

.table-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 100px 1fr 140px 130px 90px 100px 90px;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.table-grid-header {
  background: var(--pm-bg);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  padding: 12px;
}

.table-grid-col-sortable {
  cursor: pointer;
  white-space: nowrap;
}

.table-grid-sort-icon {
  font-size: 9px;
  color: var(--pm-text-muted);
  margin-left: 2px;
}

.table-grid-sort-icon-active {
  color: var(--pm-accent);
}

.table-grid-row {
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.table-grid-row-clickable {
  cursor: pointer;
  transition: background-color 0.1s;
}

.table-grid-row-clickable:hover {
  background: var(--pm-bg);
}

.table-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
