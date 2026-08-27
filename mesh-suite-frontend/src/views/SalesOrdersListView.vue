<template>
  <AppShell title="Pedidos">
    <p v-if="error" class="error-geral">{{ error }}</p>

    <PageHeader title="Pedidos" :count="countLabel">
      <button type="button" class="btn-secondary" data-test="export-orders" @click="exportCsv">Exportar</button>
      <button type="button" class="btn-primary" data-test="new-order" @click="newOrder">+ Novo Pedido</button>
    </PageHeader>

    <FilterBar
      :search="filters.busca"
      search-placeholder="Buscar por nº, cliente ou vendedor..."
      :categories="categories"
      :value-map="valueMap"
      @update:search="onBuscaChange"
      @update:filters="onFiltersChange"
    />

    <ListCard title="Lista de Pedidos" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nº</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-customer" @click="toggleSort('customerName')">
            Cliente
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'customerName' }">{{ sortIcon('customerName') }}</span>
          </div>
          <div class="table-grid-col">Vendedor</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-date" @click="toggleSort('orderDate')">
            Data
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'orderDate' }">{{ sortIcon('orderDate') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-total" @click="toggleSort('total')">
            Total
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'total' }">{{ sortIcon('total') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-status" @click="toggleSort('status')">
            Status
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'status' }">{{ sortIcon('status') }}</span>
          </div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="order in page.content"
          :key="order.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${order.id}`"
          @click="editOrder(order.id)"
        >
          <div class="table-grid-cell">{{ order.number }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ order.customerName }}</div>
          <div class="table-grid-cell">{{ order.salespersonName }}</div>
          <div class="table-grid-cell">{{ formatDate(order.orderDate) }}</div>
          <div class="table-grid-cell">{{ formatPrice(order.total) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(order.status)" :color="statusColor(order.status)" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="actionsFor(order)" :test-id="`btn-acoes-${order.id}`" />
          </div>
        </div>
      </div>
    </ListCard>

    <Pagination
      :number="page.number"
      :total-pages="page.totalPages"
      :total-elements="page.totalElements"
      :size="page.size"
      @update:page="load"
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
import StatusBadge, { type StatusBadgeColor } from '@/components/StatusBadge.vue'
import ListCard, { type ListCardStat } from '@/components/ListCard.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import {
  listSalesOrders,
  getSalesOrderCounts,
  advanceSalesOrderStatus,
  deleteSalesOrder,
  type SalesOrderSummary,
  type SalesOrderCounts,
  type Page as ApiPage,
  type SalesOrderStatus,
} from '@/api/salesOrders'
import { issueSale } from '@/api/sales'
import { listSalesReps, type SalesRep } from '@/api/users'

const router = useRouter()

const STATUS_LABELS: Record<string, SalesOrderStatus> = {
  Digitado: 'DRAFT',
  'Em Preparo': 'IN_PREPARATION',
  Faturado: 'INVOICED',
}

const categories = ['Status', 'Vendedor']
const salesReps = ref<SalesRep[]>([])
const valueMap = computed(() => ({
  Status: Object.keys(STATUS_LABELS),
  Vendedor: salesReps.value.map((r) => r.name),
}))

const filters = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const page = ref<ApiPage<SalesOrderSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<SalesOrderCounts | null>(null)
const sortField = ref<'customerName' | 'orderDate' | 'total' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const error = ref('')

const countLabel = computed(() => (counts.value ? `${counts.value.total} pedidos cadastrados` : undefined))
const statsCard = computed<ListCardStat[]>(() =>
  counts.value
    ? [
        { value: counts.value.total, label: 'Total', color: 'dark' },
        { value: counts.value.draft, label: 'Digitados', color: 'dark' },
        { value: counts.value.inPreparation, label: 'Em Preparo', color: 'amber' },
        { value: counts.value.invoiced, label: 'Faturados', color: 'green' },
      ]
    : [],
)

const NEXT_STATUS: Record<SalesOrderStatus, SalesOrderStatus | null> = {
  DRAFT: 'IN_PREPARATION',
  IN_PREPARATION: 'INVOICED',
  INVOICED: null,
}

const STATUS_LABEL: Record<SalesOrderStatus, string> = {
  DRAFT: 'Digitado',
  IN_PREPARATION: 'Em Preparo',
  INVOICED: 'Faturado',
}

function statusLabel(status: SalesOrderStatus) {
  return STATUS_LABEL[status]
}

function statusColor(status: SalesOrderStatus): StatusBadgeColor {
  return { DRAFT: 'gray', IN_PREPARATION: 'amber', INVOICED: 'green' }[status] as StatusBadgeColor
}

function advanceLabel(status: SalesOrderStatus) {
  const next = NEXT_STATUS[status]
  return next ? `Avançar para ${statusLabel(next)}` : null
}

function formatPrice(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatDate(date: string) {
  const [year, month, day] = date.split('-')
  return `${day}/${month}/${year}`
}

function sortIcon(field: 'customerName' | 'orderDate' | 'total' | 'status') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'customerName' | 'orderDate' | 'total' | 'status') {
  if (sortField.value === field) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortDir.value = 'asc'
  }
  load(0)
}

function labelsFor(category: string): string[] {
  return filtrosAvancados.value[category] ?? []
}

async function load(pageNumber: number) {
  error.value = ''
  const selectedStatus = labelsFor('Status')[0]
  const selectedSalesperson = salesReps.value.find((r) => r.name === labelsFor('Vendedor')[0])
  try {
    page.value = await listSalesOrders({
      busca: filters.busca || undefined,
      status: selectedStatus ? STATUS_LABELS[selectedStatus] : undefined,
      salespersonId: selectedSalesperson?.id,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page: pageNumber,
      size: page.value.size,
    })
  } catch {
    error.value = 'Não foi possível carregar a lista de pedidos.'
  }
}

async function loadCounts() {
  error.value = ''
  try {
    counts.value = await getSalesOrderCounts()
  } catch {
    error.value = 'Não foi possível carregar o resumo de pedidos.'
  }
}

async function loadSalesReps() {
  try {
    salesReps.value = await listSalesReps()
  } catch {
    salesReps.value = []
  }
}

function onBuscaChange(value: string) {
  filters.busca = value
  load(0)
}

function onFiltersChange(newFilters: Record<string, string[]>) {
  filtrosAvancados.value = newFilters
  load(0)
}

function onSizeChange(newSize: number) {
  page.value.size = newSize
  load(0)
}

function newOrder() {
  router.push({ name: 'pedidos-novo' })
}

function editOrder(id: string) {
  router.push({ name: 'pedidos-editar', params: { id } })
}

async function advance(order: SalesOrderSummary) {
  const next = NEXT_STATUS[order.status]
  if (!next) {
    return
  }
  error.value = ''
  try {
    await advanceSalesOrderStatus(order.id, next)
    await Promise.all([load(page.value.number), loadCounts()])
  } catch {
    error.value = 'Não foi possível avançar o status do pedido.'
  }
}

async function issue(order: SalesOrderSummary) {
  error.value = ''
  try {
    await issueSale(order.id)
    await Promise.all([load(page.value.number), loadCounts()])
  } catch {
    error.value = 'Não foi possível faturar o pedido.'
  }
}

async function remove(order: SalesOrderSummary) {
  if (!confirm(`Excluir o pedido nº ${order.number}?`)) {
    return
  }
  error.value = ''
  try {
    await deleteSalesOrder(order.id)
    await Promise.all([load(page.value.number), loadCounts()])
  } catch {
    error.value = 'Não foi possível excluir o pedido.'
  }
}

function actionsFor(order: SalesOrderSummary): ActionsMenuItem[] {
  const items: ActionsMenuItem[] = [
    { label: 'Editar', action: () => editOrder(order.id), testId: 'action-edit' },
  ]
  const next = NEXT_STATUS[order.status]
  if (next === 'INVOICED') {
    items.push({ label: 'Faturar', action: () => issue(order), testId: 'action-issue' })
  } else if (next) {
    items.push({ label: advanceLabel(order.status)!, action: () => advance(order), testId: 'action-advance' })
  }
  items.push({ label: 'Excluir', action: () => remove(order), danger: true, testId: 'action-delete' })
  return items
}

function exportCsv() {
  const header = ['Nº', 'Cliente', 'Vendedor', 'Data', 'Total', 'Status']
  const rows = page.value.content.map((order) => [
    String(order.number),
    order.customerName,
    order.salespersonName,
    formatDate(order.orderDate),
    formatPrice(order.total),
    statusLabel(order.status),
  ])
  const csv = [header, ...rows].map((cols) => cols.map((col) => `"${col.replace(/"/g, '""')}"`).join(';')).join('\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'pedidos.csv'
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  load(0)
  loadCounts()
  loadSalesReps()
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
}

.btn-primary,
.btn-secondary {
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  font-family: var(--pm-font);
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
}

.table-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 70px 1fr 150px 100px 110px 110px 90px;
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
