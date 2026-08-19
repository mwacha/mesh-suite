<template>
  <AppShell title="Ordens de Compra">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Ordens de Compra" :count="countLabel">
      <button type="button" class="btn-primary" data-test="nova-ordem" @click="novaOrdem">+ Nova Ordem de Compra</button>
    </PageHeader>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº, fornecedor ou comprador..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="OPEN">Aberta</option>
        <option value="RECEIVED">Recebida</option>
        <option value="CANCELLED">Cancelada</option>
      </select>
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Ordens de Compra</span>
        <div v-if="resumo" class="table-card-stats">
          <StatPill :value="resumo.total" label="Total" color="dark" />
          <StatPill :value="resumo.open" label="Abertas" color="dark" />
          <StatPill :value="resumo.received" label="Recebidas" color="green" />
          <StatPill :value="resumo.cancelled" label="Canceladas" color="red" />
        </div>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nº</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-fornecedor" @click="toggleSort('supplierName')">
            Fornecedor
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'supplierName' }">{{ sortIcon('supplierName') }}</span>
          </div>
          <div class="table-grid-col">Comprador</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-data" @click="toggleSort('orderDate')">
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
          v-for="ordem in pagina.content"
          :key="ordem.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${ordem.id}`"
          @click="editarOrdem(ordem.id)"
        >
          <div class="table-grid-cell">{{ ordem.number }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ ordem.supplierName }}</div>
          <div class="table-grid-cell">{{ ordem.buyerName }}</div>
          <div class="table-grid-cell">{{ formatarData(ordem.orderDate) }}</div>
          <div class="table-grid-cell">{{ formatarPreco(ordem.total) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(ordem.status)" :color="statusColor(ordem.status)" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(ordem)" :test-id="`btn-acoes-${ordem.id}`" />
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
import StatusBadge, { type StatusBadgeColor } from '@/components/StatusBadge.vue'
import StatPill from '@/components/StatPill.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import {
  listPurchaseOrders,
  getPurchaseOrderCounts,
  updatePurchaseOrderStatus,
  deletePurchaseOrder,
  type PurchaseOrderSummary,
  type PurchaseOrderCounts,
  type Page as ApiPage,
  type PurchaseOrderStatus,
} from '@/api/purchaseOrders'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<PurchaseOrderSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<PurchaseOrderCounts | null>(null)
const sortField = ref<'supplierName' | 'orderDate' | 'total' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

const countLabel = computed(() => (resumo.value ? `${resumo.value.total} ordens de compra cadastradas` : undefined))

const STATUS_LABEL: Record<PurchaseOrderStatus, string> = {
  OPEN: 'Aberta',
  RECEIVED: 'Recebida',
  CANCELLED: 'Cancelada',
}

function statusLabel(status: PurchaseOrderStatus) {
  return STATUS_LABEL[status]
}

function statusColor(status: PurchaseOrderStatus): StatusBadgeColor {
  return { OPEN: 'gray', RECEIVED: 'green', CANCELLED: 'red' }[status] as StatusBadgeColor
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function sortIcon(field: 'supplierName' | 'orderDate' | 'total' | 'status') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'supplierName' | 'orderDate' | 'total' | 'status') {
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
    pagina.value = await listPurchaseOrders({
      search: filtros.busca || undefined,
      status: (filtros.status || undefined) as PurchaseOrderStatus | undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de ordens de compra.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await getPurchaseOrderCounts()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de ordens de compra.'
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

function novaOrdem() {
  router.push({ name: 'compras-novo' })
}

function editarOrdem(id: string) {
  router.push({ name: 'compras-editar', params: { id } })
}

function lancarCompra(ordem: PurchaseOrderSummary) {
  router.push({ name: 'compras-nota-fiscal', params: { id: ordem.id } })
}

async function cancelarOrdem(ordem: PurchaseOrderSummary) {
  erro.value = ''
  try {
    await updatePurchaseOrderStatus(ordem.id, 'CANCELLED')
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status da ordem de compra.'
  }
}

async function excluir(ordem: PurchaseOrderSummary) {
  if (!confirm(`Excluir a ordem de compra nº ${ordem.number}?`)) {
    return
  }
  erro.value = ''
  try {
    await deletePurchaseOrder(ordem.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir a ordem de compra.'
  }
}

function acoesPara(ordem: PurchaseOrderSummary): ActionsMenuItem[] {
  const itens: ActionsMenuItem[] = [
    { label: 'Editar', action: () => editarOrdem(ordem.id), testId: 'acao-editar' },
  ]
  if (ordem.status === 'OPEN') {
    itens.push({ label: 'Lançar Compra', action: () => lancarCompra(ordem), testId: 'acao-lancar-compra' })
    itens.push({ label: 'Cancelar', action: () => cancelarOrdem(ordem), testId: 'acao-cancelar' })
  }
  itens.push({ label: 'Excluir', action: () => excluir(ordem), danger: true, testId: 'acao-excluir' })
  return itens
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
