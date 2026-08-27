<template>
  <AppShell title="Dashboard">
    <div class="greeting-row">
      <div>
        <div class="greeting-title">{{ saudacao }}, {{ primeiroNome }} 👋</div>
        <div class="greeting-date">{{ dataFormatada }}</div>
      </div>
      <button type="button" class="btn-primary" data-test="novo-pedido" @click="router.push({ name: 'pedidos-novo' })">
        + Novo Pedido
      </button>
    </div>

    <div class="kpi-row">
      <div class="kpi-card" v-for="kpi in kpis" :key="kpi.label">
        <div class="kpi-accent-bar" :style="{ background: kpi.accent }"></div>
        <div class="kpi-body">
          <div class="kpi-icon" :style="{ background: kpi.accentBg }">{{ kpi.icon }}</div>
          <div class="kpi-value">{{ kpi.value }}</div>
          <div class="kpi-label">{{ kpi.label }}</div>
        </div>
      </div>
    </div>

    <section class="card chart-card">
      <div class="chart-header">
        <span class="chart-title">Pedidos por Período</span>
        <SegmentedControl
          :model-value="periodo"
          :options="periodoOptions"
          test-id="periodo"
          @update:model-value="(v) => (periodo = v as PeriodRange)"
        />
      </div>
      <OrdersLineChart :points="chartPoints" />
    </section>

    <ListCard title="Últimos Pedidos" :stats="statsCard">
      <div class="table-grid" v-if="pedidosRecentes.length">
        <div class="table-grid-header">
          <div class="table-grid-col">#</div>
          <div class="table-grid-col">Cliente</div>
          <div class="table-grid-col">Data</div>
          <div class="table-grid-col">Total</div>
          <div class="table-grid-col">Status</div>
          <div class="table-grid-col"></div>
        </div>
        <div v-for="pedido in pedidosRecentes" :key="pedido.id" class="table-grid-row">
          <div class="table-grid-cell">{{ pedido.number }}</div>
          <div class="table-grid-cell">{{ pedido.customerName }}</div>
          <div class="table-grid-cell">{{ formatarData(pedido.orderDate) }}</div>
          <div class="table-grid-cell">{{ formatarPreco(pedido.total) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(pedido.status)" :color="statusColor(pedido.status)" />
          </div>
          <div class="table-grid-cell">
            <button type="button" class="link" @click="router.push({ name: 'pedidos-editar', params: { id: pedido.id } })">
              Ver
            </button>
          </div>
        </div>
      </div>
      <p v-else class="empty-state">Nenhum pedido para exibir.</p>
    </ListCard>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import ListCard, { type ListCardStat } from '@/components/ListCard.vue'
import StatusBadge, { type StatusBadgeColor } from '@/components/StatusBadge.vue'
import SegmentedControl, { type SegmentedOption } from '@/components/SegmentedControl.vue'
import OrdersLineChart from '@/components/OrdersLineChart.vue'
import {
  listSalesOrders,
  getMonthlyRevenue,
  getOrdersByPeriod,
  type SalesOrderSummary,
  type SalesOrderStatus,
  type PeriodRange,
  type OrderPeriodPoint,
} from '@/api/salesOrders'
import { getPartnerSummary, type PartnerSummary } from '@/api/partners'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const primeiroNome = computed(() => authStore.usuario?.nome?.split(' ')[0] ?? '')

const DIAS_SEMANA = ['Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado']
const MESES_ABREV = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']

const saudacao = computed(() => {
  const hora = new Date().getHours()
  if (hora < 12) return 'Bom dia'
  if (hora < 18) return 'Boa tarde'
  return 'Boa noite'
})

const dataFormatada = computed(() => {
  const hoje = new Date()
  const dia = String(hoje.getDate()).padStart(2, '0')
  return `${DIAS_SEMANA[hoje.getDay()]}, ${dia} ${MESES_ABREV[hoje.getMonth()]} ${hoje.getFullYear()}`
})

// Each section is fetched and rendered independently -- a user without VIEW
// permission on one module (Module.CUSTOMER/ORDER) still sees the rest of the
// dashboard; that section just falls back to "—" / an empty state instead of
// blocking the whole page.
const parceiroResumo = ref<PartnerSummary | null>(null)
const faturamentoMes = ref<number | null>(null)
const pedidosRecentes = ref<SalesOrderSummary[]>([])
const chartPoints = ref<OrderPeriodPoint[]>([])

const periodoOptions: SegmentedOption[] = [
  { value: 'CURRENT_MONTH', label: 'Mês Corrente' },
  { value: 'LAST_12_MONTHS', label: 'Últimos 12 Meses' },
]
const periodo = ref<PeriodRange>('CURRENT_MONTH')

interface Kpi {
  icon: string
  label: string
  value: string
  accent: string
  accentBg: string
}

const kpis = computed<Kpi[]>(() => [
  {
    icon: '👥',
    label: 'Clientes Ativos',
    value: parceiroResumo.value ? String(parceiroResumo.value.active) : '—',
    accent: 'var(--pm-accent)',
    accentBg: 'var(--pm-accent-bg)',
  },
  {
    icon: '⚠️',
    label: 'Clientes em Risco',
    value: parceiroResumo.value ? String(parceiroResumo.value.atRisk) : '—',
    accent: 'var(--pm-warning)',
    accentBg: 'var(--pm-warning-bg)',
  },
  {
    icon: '🚫',
    label: 'Clientes Bloqueados',
    value: parceiroResumo.value ? String(parceiroResumo.value.blocked) : '—',
    accent: 'var(--pm-error)',
    accentBg: 'var(--pm-error-bg)',
  },
  {
    icon: '💰',
    label: 'Faturamento do Mês',
    value: faturamentoMes.value !== null ? formatarPreco(faturamentoMes.value) : '—',
    accent: 'var(--pm-success)',
    accentBg: 'var(--pm-success-bg)',
  },
])

// Counts only the orders actually shown in the "Últimos Pedidos" table below
// (not the tenant-wide totals) -- the totalizer must match the listing.
const statsCard = computed<ListCardStat[]>(() => {
  const draft = pedidosRecentes.value.filter((p) => p.status === 'DRAFT').length
  const inPreparation = pedidosRecentes.value.filter((p) => p.status === 'IN_PREPARATION').length
  const invoiced = pedidosRecentes.value.filter((p) => p.status === 'INVOICED').length
  return [
    { value: pedidosRecentes.value.length, label: 'Total', color: 'dark' },
    { value: draft, label: 'Digitados', color: 'dark' },
    { value: inPreparation, label: 'Em Preparo', color: 'amber' },
    { value: invoiced, label: 'Faturados', color: 'green' },
  ]
})

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

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

async function carregarGrafico() {
  try {
    chartPoints.value = await getOrdersByPeriod(periodo.value)
  } catch {
    chartPoints.value = []
  }
}

watch(periodo, carregarGrafico)

onMounted(async () => {
  const [parceiroR, faturamentoR, pedidosR] = await Promise.allSettled([
    getPartnerSummary('CUSTOMER'),
    getMonthlyRevenue(),
    listSalesOrders({ page: 0, size: 10 }),
  ])
  if (parceiroR.status === 'fulfilled') parceiroResumo.value = parceiroR.value
  if (faturamentoR.status === 'fulfilled') faturamentoMes.value = faturamentoR.value.currentMonthRevenue
  if (pedidosR.status === 'fulfilled') pedidosRecentes.value = pedidosR.value.content
  await carregarGrafico()
})
</script>

<style scoped>
.greeting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  font-family: var(--pm-font);
}

.greeting-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.greeting-date {
  font-size: 12px;
  color: var(--pm-text-muted);
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
  padding: 10px 18px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--pm-font);
  cursor: pointer;
  white-space: nowrap;
}

.kpi-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.kpi-card {
  flex: 1;
  background: var(--pm-white);
  border: 2px solid var(--pm-border-light);
  border-radius: 8px;
  overflow: hidden;
}

.kpi-accent-bar {
  height: 4px;
}

.kpi-body {
  padding: 16px 18px;
}

.kpi-icon {
  width: 38px;
  height: 38px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  margin-bottom: 12px;
}

.kpi-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  color: var(--pm-text-dark);
  margin-bottom: 6px;
}

.kpi-label {
  font-size: 12px;
  color: var(--pm-text-muted);
  font-weight: 500;
  font-family: var(--pm-font);
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 12px;
  box-sizing: border-box;
}

.chart-card {
  margin-bottom: 12px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-family: var(--pm-font);
}

.chart-title {
  font-size: 13px;
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
  grid-template-columns: 60px 1fr 100px 110px 100px 56px;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.table-grid-header {
  background: var(--pm-bg);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--pm-text-mid);
}

.table-grid-row {
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.link {
  background: none;
  border: none;
  padding: 0;
  color: var(--pm-accent);
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}
</style>
