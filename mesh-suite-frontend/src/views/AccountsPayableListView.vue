<template>
  <AppShell title="Contas a Pagar">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="OPEN">Em Aberto</option>
        <option value="PAID">Paga</option>
      </select>
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Contas a Pagar</span>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nº</div>
          <div class="table-grid-col">Parcela</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-fornecedor" @click="toggleSort('supplierName')">
            Fornecedor
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'supplierName' }">{{ sortIcon('supplierName') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-vencimento" @click="toggleSort('dueDate')">
            Vencimento
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'dueDate' }">{{ sortIcon('dueDate') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-valor" @click="toggleSort('amount')">
            Valor
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'amount' }">{{ sortIcon('amount') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-status" @click="toggleSort('status')">
            Status
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'status' }">{{ sortIcon('status') }}</span>
          </div>
          <div class="table-grid-col"></div>
        </div>

        <div v-for="titulo in pagina.content" :key="titulo.id" class="table-grid-row" :data-test="`row-${titulo.id}`">
          <div class="table-grid-cell">{{ titulo.number }}</div>
          <div class="table-grid-cell">{{ titulo.installmentNumber }}/{{ titulo.totalInstallments }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ titulo.supplierName }}</div>
          <div class="table-grid-cell">{{ formatarData(titulo.dueDate) }}</div>
          <div class="table-grid-cell">{{ formatarPreco(titulo.amount) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(titulo.status)" :color="titulo.status === 'PAID' ? 'green' : 'amber'" />
          </div>
          <div class="table-grid-cell">
            <ActionsMenu :items="acoesPara(titulo)" :test-id="`btn-acoes-${titulo.id}`" />
          </div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma conta a pagar para exibir.</p>
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
import StatusBadge from '@/components/StatusBadge.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import {
  listAccountsPayable,
  updateAccountsPayableStatus,
  type AccountsPayable,
  type AccountsPayableStatus,
  type Page as ApiPage,
} from '@/api/accountsPayable'

const filtros = reactive({ status: '' })
const pagina = ref<ApiPage<AccountsPayable>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const sortField = ref<'supplierName' | 'dueDate' | 'amount' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

const STATUS_LABEL: Record<AccountsPayableStatus, string> = {
  OPEN: 'Em Aberto',
  PAID: 'Paga',
}

function statusLabel(status: AccountsPayableStatus) {
  return STATUS_LABEL[status]
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function sortIcon(field: 'supplierName' | 'dueDate' | 'amount' | 'status') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'supplierName' | 'dueDate' | 'amount' | 'status') {
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
    pagina.value = await listAccountsPayable({
      status: (filtros.status || undefined) as AccountsPayableStatus | undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de contas a pagar.'
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

async function darBaixa(titulo: AccountsPayable) {
  erro.value = ''
  try {
    await updateAccountsPayableStatus(titulo.id, 'PAID')
    await carregar(pagina.value.number)
  } catch {
    erro.value = 'Não foi possível dar baixa na conta a pagar.'
  }
}

async function reverterBaixa(titulo: AccountsPayable) {
  erro.value = ''
  try {
    await updateAccountsPayableStatus(titulo.id, 'OPEN')
    await carregar(pagina.value.number)
  } catch {
    erro.value = 'Não foi possível reverter a baixa da conta a pagar.'
  }
}

function acoesPara(titulo: AccountsPayable): ActionsMenuItem[] {
  return titulo.status === 'OPEN'
    ? [{ label: 'Dar Baixa', action: () => darBaixa(titulo), testId: 'acao-baixa' }]
    : [{ label: 'Reverter Baixa', action: () => reverterBaixa(titulo), testId: 'acao-reverter' }]
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
  grid-template-columns: 60px 90px 1fr 130px 130px 110px 90px;
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
