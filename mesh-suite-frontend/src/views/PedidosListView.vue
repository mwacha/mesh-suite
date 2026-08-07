<template>
  <AppShell title="Pedidos">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Pedidos" :count="countLabel">
      <button type="button" class="btn-primary" data-test="novo-pedido" @click="novoPedido">+ Novo Pedido</button>
    </PageHeader>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº, cliente ou vendedor..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="DIGITADO">Digitado</option>
        <option value="EM_PREPARO">Em Preparo</option>
        <option value="FATURADO">Faturado</option>
      </select>
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Pedidos</span>
        <div v-if="resumo" class="table-card-stats">
          <StatPill :value="resumo.total" label="Total" color="dark" />
          <StatPill :value="resumo.digitados" label="Digitados" color="dark" />
          <StatPill :value="resumo.emPreparo" label="Em Preparo" color="amber" />
          <StatPill :value="resumo.faturados" label="Faturados" color="green" />
        </div>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nº</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-cliente" @click="toggleSort('clienteNome')">
            Cliente
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'clienteNome' }">{{ sortIcon('clienteNome') }}</span>
          </div>
          <div class="table-grid-col">Vendedor</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-data" @click="toggleSort('dataPedido')">
            Data
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'dataPedido' }">{{ sortIcon('dataPedido') }}</span>
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
          v-for="pedido in pagina.content"
          :key="pedido.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${pedido.id}`"
          @click="editarPedido(pedido.id)"
        >
          <div class="table-grid-cell">{{ pedido.numero }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ pedido.clienteNome }}</div>
          <div class="table-grid-cell">{{ pedido.vendedorNome }}</div>
          <div class="table-grid-cell">{{ formatarData(pedido.dataPedido) }}</div>
          <div class="table-grid-cell">{{ formatarPreco(pedido.total) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(pedido.status)" :color="statusColor(pedido.status)" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(pedido)" :test-id="`btn-acoes-${pedido.id}`" />
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
  listarPedidos,
  buscarResumoPedidos,
  avancarStatusPedido,
  excluirPedido,
  type PedidoSummary,
  type PedidoResumo,
  type Page as ApiPage,
  type StatusPedido,
} from '@/api/pedidos'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<PedidoSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<PedidoResumo | null>(null)
const sortField = ref<'clienteNome' | 'dataPedido' | 'total' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

const countLabel = computed(() => (resumo.value ? `${resumo.value.total} pedidos cadastrados` : undefined))

const PROXIMO_STATUS: Record<StatusPedido, StatusPedido | null> = {
  DIGITADO: 'EM_PREPARO',
  EM_PREPARO: 'FATURADO',
  FATURADO: null,
}

const STATUS_LABEL: Record<StatusPedido, string> = {
  DIGITADO: 'Digitado',
  EM_PREPARO: 'Em Preparo',
  FATURADO: 'Faturado',
}

function statusLabel(status: StatusPedido) {
  return STATUS_LABEL[status]
}

function statusColor(status: StatusPedido): StatusBadgeColor {
  return { DIGITADO: 'gray', EM_PREPARO: 'amber', FATURADO: 'green' }[status] as StatusBadgeColor
}

function rotuloAvancar(status: StatusPedido) {
  const proximo = PROXIMO_STATUS[status]
  return proximo ? `Avançar para ${statusLabel(proximo)}` : null
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function sortIcon(field: 'clienteNome' | 'dataPedido' | 'total' | 'status') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'clienteNome' | 'dataPedido' | 'total' | 'status') {
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
    pagina.value = await listarPedidos({
      busca: filtros.busca || undefined,
      status: (filtros.status || undefined) as StatusPedido | undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de pedidos.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await buscarResumoPedidos()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de pedidos.'
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

function novoPedido() {
  router.push({ name: 'pedidos-novo' })
}

function editarPedido(id: string) {
  router.push({ name: 'pedidos-editar', params: { id } })
}

async function avancar(pedido: PedidoSummary) {
  const proximo = PROXIMO_STATUS[pedido.status]
  if (!proximo) {
    return
  }
  erro.value = ''
  try {
    await avancarStatusPedido(pedido.id, proximo)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível avançar o status do pedido.'
  }
}

async function excluir(pedido: PedidoSummary) {
  if (!confirm(`Excluir o pedido nº ${pedido.numero}?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirPedido(pedido.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir o pedido.'
  }
}

function acoesPara(pedido: PedidoSummary): ActionsMenuItem[] {
  const itens: ActionsMenuItem[] = [
    { label: 'Editar', action: () => editarPedido(pedido.id), testId: 'acao-editar' },
  ]
  const rotulo = rotuloAvancar(pedido.status)
  if (rotulo) {
    itens.push({ label: rotulo, action: () => avancar(pedido), testId: 'acao-avancar' })
  }
  itens.push({ label: 'Excluir', action: () => excluir(pedido), danger: true, testId: 'acao-excluir' })
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
