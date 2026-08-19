<!-- mesh-suite-frontend/src/views/PurchaseInvoicesListView.vue -->
<template>
  <AppShell title="Notas de Entrada">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº ou fornecedor..."
        data-test="busca"
        @input="carregar(0)"
      />
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Compras</span>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nº</div>
          <div class="table-grid-col">Nota Fiscal</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-fornecedor" @click="toggleSort('supplierName')">
            Fornecedor
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'supplierName' }">{{ sortIcon('supplierName') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-data" @click="toggleSort('issueDate')">
            Data de Emissão
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'issueDate' }">{{ sortIcon('issueDate') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-total" @click="toggleSort('total')">
            Total
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'total' }">{{ sortIcon('total') }}</span>
          </div>
        </div>

        <div v-for="invoice in pagina.content" :key="invoice.id" class="table-grid-row" :data-test="`row-${invoice.id}`">
          <div class="table-grid-cell">{{ invoice.number }}</div>
          <div class="table-grid-cell">{{ invoice.invoiceNumber }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ invoice.supplierName }}</div>
          <div class="table-grid-cell">{{ formatarData(invoice.issueDate) }}</div>
          <div class="table-grid-cell">{{ formatarPreco(invoice.total) }}</div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma compra para exibir.</p>
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
import { reactive, ref, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import Pagination from '@/components/Pagination.vue'
import { listPurchaseInvoices, type PurchaseInvoiceSummary, type Page as ApiPage } from '@/api/purchaseInvoices'

const filtros = reactive({ busca: '' })
const pagina = ref<ApiPage<PurchaseInvoiceSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const sortField = ref<'supplierName' | 'issueDate' | 'total' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function sortIcon(field: 'supplierName' | 'issueDate' | 'total') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'supplierName' | 'issueDate' | 'total') {
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
    pagina.value = await listPurchaseInvoices({
      search: filtros.busca || undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de compras.'
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

onMounted(() => {
  carregar(0)
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
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

.toolbar input {
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
  font-family: var(--pm-font);
}

.table-card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.table-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 60px 120px 1fr 150px 110px;
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

.table-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}
</style>
