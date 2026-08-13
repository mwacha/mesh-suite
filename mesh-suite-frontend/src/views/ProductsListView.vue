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
        <option value="ACTIVE">Ativo</option>
        <option value="INACTIVE">Inativo</option>
      </select>
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Produtos</span>
        <div v-if="resumo" class="table-card-stats">
          <StatPill :value="resumo.total" label="Total" color="dark" />
          <StatPill :value="resumo.active" label="Ativos" color="green" />
          <StatPill :value="resumo.inactive" label="Inativos" color="red" />
        </div>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Código</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort('name')">
            Produto
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'name' }">{{ sortIcon('name') }}</span>
          </div>
          <div class="table-grid-col">Marca</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-preco" @click="toggleSort('salePrice')">
            Preço de Venda
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'salePrice' }">{{ sortIcon('salePrice') }}</span>
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
          @click="editarProduto(produto.id)"
        >
          <div class="table-grid-cell">{{ produto.sku }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ produto.name }}</div>
          <div class="table-grid-cell">{{ produto.brand }}</div>
          <div class="table-grid-cell">{{ formatarPreco(produto.salePrice) }}</div>
          <div class="table-grid-cell">{{ produto.stockQuantity }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(produto.status)" :color="produto.status === 'ACTIVE' ? 'green' : 'red'" />
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
  listProducts,
  getProductSummary,
  updateProductStatus,
  deleteProduct,
  type ProductListItem,
  type ProductSummary,
  type Page as ApiPage,
  type ProductStatus,
} from '@/api/products'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<ProductListItem>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<ProductSummary | null>(null)
const sortField = ref<'name' | 'salePrice' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

const countLabel = computed(() => (resumo.value ? `${resumo.value.total} produtos cadastrados` : undefined))

function statusLabel(status: ProductStatus) {
  return { ACTIVE: 'Ativo', INACTIVE: 'Inativo' }[status]
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function sortIcon(field: 'name' | 'salePrice' | 'status') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'name' | 'salePrice' | 'status') {
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
    pagina.value = await listProducts({
      busca: filtros.busca || undefined,
      status: (filtros.status || undefined) as ProductStatus | undefined,
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
    resumo.value = await getProductSummary()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de produtos.'
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

function novoProduto() {
  router.push({ name: 'produtos-novo' })
}

function editarProduto(id: string) {
  router.push({ name: 'produtos-editar', params: { id } })
}

async function alternarStatus(produto: ProductListItem) {
  erro.value = ''
  const novoStatus = produto.status === 'INACTIVE' ? 'ACTIVE' : 'INACTIVE'
  try {
    await updateProductStatus(produto.id, novoStatus)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status.'
  }
}

async function excluir(produto: ProductListItem) {
  if (!confirm(`Excluir o produto "${produto.name}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deleteProduct(produto.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir o produto.'
  }
}

function acoesPara(produto: ProductListItem): ActionsMenuItem[] {
  return [
    { label: 'Editar', action: () => editarProduto(produto.id), testId: 'acao-editar' },
    {
      label: produto.status === 'INACTIVE' ? 'Ativar' : 'Inativar',
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
