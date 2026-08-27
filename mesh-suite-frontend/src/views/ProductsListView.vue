<template>
  <AppShell title="Produtos">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Produtos" :count="countLabel">
      <button type="button" class="btn-primary" data-test="novo-produto" @click="novoProduto">+ Novo Produto</button>
    </PageHeader>

    <FilterBar
      :search="filtros.busca"
      search-placeholder="Buscar produto por nome ou SKU..."
      :categories="['Status', 'Tipo']"
      :value-map="{ Status: ['Ativo', 'Inativo'], Tipo: ['Simples', 'Kit', 'Variação'] }"
      @update:search="onBuscaChange"
      @update:filters="onFiltrosChange"
    />

    <ListCard title="Lista de Produtos" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Código</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort('name')">
            Produto
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'name' }">{{ sortIcon('name') }}</span>
          </div>
          <div class="table-grid-col">Marca</div>
          <div class="table-grid-col">Tipo</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-preco" @click="toggleSort('salePrice')">
            Preço
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'salePrice' }">{{ sortIcon('salePrice') }}</span>
          </div>
          <div class="table-grid-col">Estoque</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-status" @click="toggleSort('status')">
            Status
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'status' }">{{ sortIcon('status') }}</span>
          </div>
          <div class="table-grid-col"></div>
        </div>

        <template v-for="produto in pagina.content" :key="produto.id">
          <div
            class="table-grid-row table-grid-row-clickable"
            :data-test="`row-${produto.id}`"
            @click="editarProduto(produto)"
          >
            <div class="table-grid-cell">{{ produto.sku }}</div>
            <div class="table-grid-cell table-grid-cell-nome">
              <span
                v-if="produto.type === 'VARIATION_PARENT'"
                class="expand-toggle"
                :class="{ 'expand-toggle-open': isExpanded(produto.id) }"
                :data-test="`expandir-${produto.id}`"
                @click.stop="toggleExpanded(produto.id)"
              >
                <svg width="8" height="8" viewBox="0 0 8 8" fill="none">
                  <path d="M2 1l4 3-4 3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <span v-else class="expand-toggle-spacer"></span>
              {{ produto.name }}
            </div>
            <div class="table-grid-cell">{{ produto.brandName }}</div>
            <div class="table-grid-cell">{{ tipoLabel(produto.type) }}</div>
            <div class="table-grid-cell">{{ formatarPreco(produto.salePrice) }}</div>
            <div class="table-grid-cell">{{ produto.stockQuantity }}</div>
            <div class="table-grid-cell">
              <StatusBadge :label="statusLabel(produto.status)" :color="produto.status === 'ACTIVE' ? 'green' : 'red'" />
            </div>
            <div class="table-grid-cell" @click.stop>
              <ActionsMenu :items="acoesPara(produto)" :test-id="`btn-acoes-${produto.id}`" />
            </div>
          </div>

          <template v-if="produto.type === 'VARIATION_PARENT' && isExpanded(produto.id)">
            <div
              v-for="filho in produto.children"
              :key="filho.id"
              class="table-grid-row table-grid-row-child table-grid-row-clickable"
              :data-test="`row-filho-${filho.id}`"
              @click="editarProduto(produto)"
            >
              <div class="table-grid-cell">{{ filho.sku }}</div>
              <div class="table-grid-cell table-grid-cell-nome">— {{ filho.name }}</div>
              <div class="table-grid-cell"></div>
              <div class="table-grid-cell">
                <StatusBadge label="Variação" color="amber" />
              </div>
              <div class="table-grid-cell">{{ formatarPreco(filho.salePrice) }}</div>
              <div class="table-grid-cell">{{ filho.stockQuantity }}</div>
              <div class="table-grid-cell">
                <StatusBadge :label="statusLabel(produto.status)" :color="produto.status === 'ACTIVE' ? 'green' : 'red'" />
              </div>
              <div class="table-grid-cell" @click.stop>
                <ActionsMenu :items="acoesParaFilho(produto)" :test-id="`btn-acoes-${filho.id}`" />
              </div>
            </div>
          </template>
        </template>
      </div>
    </ListCard>

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
import FilterBar from '@/components/FilterBar.vue'
import ListCard, { type ListCardStat } from '@/components/ListCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import {
  listAllProducts,
  getAllProductsSummary,
  updateProductStatus,
  deleteProduct,
  type ProductAllListItem,
  type ProductType,
  type Page as ApiPage,
  type ProductStatus,
  type ProductSummary,
} from '@/api/products'

const router = useRouter()

const TIPO_LABEL: Record<ProductType, string> = {
  PRODUCT: 'Simples',
  PRODUCT_KIT: 'Kit',
  VARIATION_PARENT: 'Variação',
  VARIATION_CHILD: 'Variante',
}
const TIPO_TO_ROUTE_TYPE: Record<string, ProductType> = {
  Simples: 'PRODUCT',
  Kit: 'PRODUCT_KIT',
  Variação: 'VARIATION_PARENT',
}
const TIPO_EDIT_ROUTE: Record<ProductType, string> = {
  PRODUCT: 'produtos-editar',
  PRODUCT_KIT: 'produtos-editar-kit',
  VARIATION_PARENT: 'produtos-editar-variacao',
  VARIATION_CHILD: 'produtos-editar',
}

const filtros = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const pagina = ref<ApiPage<ProductAllListItem>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const sortField = ref<'name' | 'salePrice' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')
const expandedIds = ref<Set<string>>(new Set())
const resumo = ref<ProductSummary | null>(null)

const countLabel = computed(() => `${pagina.value.totalElements} produtos cadastrados`)
const statsCard = computed<ListCardStat[]>(() =>
  resumo.value
    ? [
        { value: resumo.value.total, label: 'Total', color: 'dark' },
        { value: resumo.value.active, label: 'Ativos', color: 'green' },
        { value: resumo.value.inactive, label: 'Inativos', color: 'red' },
      ]
    : [],
)

function tipoLabel(type: ProductType) {
  return TIPO_LABEL[type]
}

function statusLabel(status: ProductStatus) {
  return { ACTIVE: 'Ativo', INACTIVE: 'Inativo' }[status]
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function isExpanded(id: string) {
  return expandedIds.value.has(id)
}

function toggleExpanded(id: string) {
  const next = new Set(expandedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedIds.value = next
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

function labelsFor(categoria: string): string[] {
  return filtrosAvancados.value[categoria] ?? []
}

async function carregar(page: number) {
  erro.value = ''
  const statusLabels = labelsFor('Status')
  const status = statusLabels.length === 1 ? (statusLabels[0] === 'Ativo' ? 'ACTIVE' : 'INACTIVE') : undefined
  const tipoLabels = labelsFor('Tipo')
  const type = tipoLabels.length === 1 ? TIPO_TO_ROUTE_TYPE[tipoLabels[0]] : undefined
  try {
    pagina.value = await listAllProducts({
      search: filtros.busca || undefined,
      status,
      type,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de produtos.'
  }
}

async function carregarResumo() {
  try {
    resumo.value = await getAllProductsSummary()
  } catch {
    // Pills de contagem são um complemento -- uma falha aqui não deve bloquear a listagem.
  }
}

function onBuscaChange(valor: string) {
  filtros.busca = valor
  carregar(0)
}

function onFiltrosChange(filtrosNovos: Record<string, string[]>) {
  filtrosAvancados.value = filtrosNovos
  carregar(0)
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

function novoProduto() {
  router.push({ name: 'produtos-novo' })
}

function editarProduto(produto: ProductAllListItem) {
  router.push({ name: TIPO_EDIT_ROUTE[produto.type], params: { id: produto.id } })
}

async function alternarStatus(produto: ProductAllListItem) {
  erro.value = ''
  const novoStatus = produto.status === 'INACTIVE' ? 'ACTIVE' : 'INACTIVE'
  try {
    await updateProductStatus(produto.id, novoStatus)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status.'
  }
}

async function excluir(produto: ProductAllListItem) {
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

function acoesPara(produto: ProductAllListItem): ActionsMenuItem[] {
  return [
    { label: 'Editar', action: () => editarProduto(produto), testId: 'acao-editar' },
    {
      label: produto.status === 'INACTIVE' ? 'Ativar' : 'Inativar',
      action: () => alternarStatus(produto),
      testId: 'acao-status',
    },
    { label: 'Excluir', action: () => excluir(produto), danger: true, testId: 'acao-excluir' },
  ]
}

// A variante (VARIATION_CHILD) nunca é um recurso próprio no backend -- só é
// gerenciada através do formulário de Variação do pai (ver
// ProductTypeStrategyResolver), então a única ação sensata aqui é abrir esse
// formulário, e não repetir Ativar/Inativar/Excluir como se a variante fosse
// editável isoladamente.
function acoesParaFilho(pai: ProductAllListItem): ActionsMenuItem[] {
  return [{ label: 'Editar', action: () => editarProduto(pai), testId: 'acao-editar' }]
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

.table-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 110px 1fr 130px 90px 110px 90px 90px 90px;
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

.table-grid-row-child {
  background: var(--pm-bg);
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

.expand-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  margin-right: 6px;
  border: 1.5px solid var(--pm-border-light);
  border-radius: 4px;
  background: var(--pm-white);
  color: var(--pm-text-muted);
  cursor: pointer;
}

.expand-toggle svg {
  transition: transform 0.15s;
}

.expand-toggle-open {
  background: var(--pm-accent-bg);
  color: var(--pm-accent);
}

.expand-toggle-open svg {
  transform: rotate(90deg);
}

.expand-toggle-spacer {
  display: inline-block;
  width: 18px;
  margin-right: 6px;
}
</style>
