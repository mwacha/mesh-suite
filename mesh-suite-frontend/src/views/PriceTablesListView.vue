<template>
  <AppShell title="Tabelas de Preço">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Tabelas de Preço" :count="countLabel">
      <button type="button" class="btn-primary" data-test="nova-tabela" @click="novaTabela">+ Nova Tabela</button>
    </PageHeader>

    <FilterBar
      :search="filtros.busca"
      search-placeholder="Buscar tabela por nome..."
      :categories="['Status']"
      :value-map="{ Status: ['Ativo', 'Inativo'] }"
      @update:search="onBuscaChange"
      @update:filters="onFiltrosChange"
    />

    <ListCard title="Lista de Tabelas de Preço" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort">
            Nome da Tabela
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortDir }">{{ sortIcon() }}</span>
          </div>
          <div class="table-grid-col">Método de Ajuste</div>
          <div class="table-grid-col">Início</div>
          <div class="table-grid-col">Término</div>
          <div class="table-grid-col">Status</div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="tabela in pagina.content"
          :key="tabela.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${tabela.id}`"
          @click="editarTabela(tabela.id)"
        >
          <div class="table-grid-cell table-grid-cell-nome">{{ tabela.name }}</div>
          <div class="table-grid-cell">{{ resumoMetodoAjuste(tabela) }}</div>
          <div class="table-grid-cell">{{ formatarData(tabela.effectiveStartDate) }}</div>
          <div class="table-grid-cell">{{ tabela.effectiveEndDate ? formatarData(tabela.effectiveEndDate) : '—' }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="tabela.active ? 'Ativo' : 'Inativo'" :color="tabela.active ? 'green' : 'red'" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(tabela)" />
          </div>
        </div>
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
  listPriceTables,
  getPriceTableCounts,
  updatePriceTableStatus,
  deletePriceTable,
  type PriceTableSummary,
  type PriceTableCounts,
  type Page as ApiPage,
} from '@/api/priceTables'

const router = useRouter()

const filtros = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const sortDir = ref<'asc' | 'desc' | null>(null)
const pagina = ref<ApiPage<PriceTableSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<PriceTableCounts | null>(null)
const erro = ref('')

const countLabel = computed(() => (counts.value ? `${counts.value.total} tabelas cadastradas` : undefined))
const statsCard = computed<ListCardStat[]>(() =>
  counts.value
    ? [
        { value: counts.value.total, label: 'Total', color: 'dark' },
        { value: counts.value.active, label: 'Ativas', color: 'green' },
        { value: counts.value.inactive, label: 'Inativas', color: 'red' },
      ]
    : [],
)

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function resumoMetodoAjuste(tabela: PriceTableSummary) {
  if (tabela.adjustmentMethod === 'MANUAL') {
    return 'Manual'
  }
  const operacao = tabela.adjustmentOperation === 'SUBTRACT' ? 'Subtrair' : 'Somar'
  const valor = tabela.adjustmentValueType === 'PERCENTAGE'
    ? `${tabela.adjustmentValue ?? 0}%`
    : (tabela.adjustmentValue ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
  return `Automático · ${operacao} ${valor}`
}

function sortIcon() {
  if (!sortDir.value) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort() {
  sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  carregar(0)
}

function labelsFor(categoria: string): string[] {
  return filtrosAvancados.value[categoria] ?? []
}

async function carregar(page: number) {
  erro.value = ''
  const statusLabels = labelsFor('Status')
  const ativo = statusLabels.length === 1 ? statusLabels[0] === 'Ativo' : undefined
  try {
    pagina.value = await listPriceTables({
      busca: filtros.busca || undefined,
      ativo,
      sort: sortDir.value ? `name,${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de tabelas de preço.'
  }
}

async function carregarContagens() {
  try {
    counts.value = await getPriceTableCounts()
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

function novaTabela() {
  router.push({ name: 'tabelas-preco-novo' })
}

function editarTabela(id: string) {
  router.push({ name: 'tabelas-preco-editar', params: { id } })
}

async function alternarStatus(tabela: PriceTableSummary) {
  erro.value = ''
  try {
    await updatePriceTableStatus(tabela.id, !tabela.active)
    await Promise.all([carregar(pagina.value.number), carregarContagens()])
  } catch {
    erro.value = 'Não foi possível atualizar o status.'
  }
}

async function excluir(tabela: PriceTableSummary) {
  if (!confirm(`Excluir a tabela de preço "${tabela.name}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deletePriceTable(tabela.id)
    await Promise.all([carregar(pagina.value.number), carregarContagens()])
  } catch {
    erro.value = 'Não foi possível excluir a tabela de preço.'
  }
}

function acoesPara(tabela: PriceTableSummary): ActionsMenuItem[] {
  return [
    { label: 'Editar', action: () => editarTabela(tabela.id), testId: 'acao-editar' },
    { label: tabela.active ? 'Inativar' : 'Ativar', action: () => alternarStatus(tabela), testId: 'acao-status' },
    { label: 'Excluir', action: () => excluir(tabela), danger: true, testId: 'acao-excluir' },
  ]
}

onMounted(() => {
  carregar(0)
  carregarContagens()
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
  grid-template-columns: 1fr 180px 100px 110px 90px 80px;
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
  font-weight: 600;
}
</style>
