<template>
  <AppShell title="Dashboard">
    <div class="stats-row">
      <div class="stat-card" v-for="stat in stats" :key="stat.label">
        <span class="stat-icon">{{ stat.icon }}</span>
        <div class="stat-body">
          <div class="stat-label">{{ stat.label }}</div>
          <div class="stat-value">{{ stat.value }}</div>
        </div>
      </div>
    </div>

    <div class="dashboard-columns">
      <section class="card orders-card">
        <div class="card-header">
          <h2>Últimos Pedidos</h2>
          <button type="button" class="btn-secondary" data-test="ver-todos-pedidos" @click="router.push({ name: 'pedidos' })">
            Ver todos
          </button>
        </div>
        <table class="orders-table" v-if="pedidosRecentes.length">
          <thead>
            <tr>
              <th>#</th>
              <th>Cliente</th>
              <th>Data</th>
              <th>Total</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="pedido in pedidosRecentes" :key="pedido.id">
              <td>{{ pedido.number }}</td>
              <td>{{ pedido.customerName }}</td>
              <td>{{ formatarData(pedido.orderDate) }}</td>
              <td>{{ formatarPreco(pedido.total) }}</td>
              <td>
                <span class="badge" :class="`badge-${pedido.status}`">{{ statusLabel(pedido.status) }}</span>
              </td>
              <td>
                <button
                  type="button"
                  class="link"
                  @click="router.push({ name: 'pedidos-editar', params: { id: pedido.id } })"
                >
                  Ver
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-state">Nenhum pedido para exibir.</p>
      </section>

      <aside class="side-column">
        <section class="card">
          <h2>Ações Rápidas</h2>
          <div class="quick-actions">
            <button type="button" class="btn-primary" data-test="novo-pedido" @click="router.push({ name: 'pedidos-novo' })">
              + Novo Pedido
            </button>
            <button type="button" class="btn-secondary" @click="router.push({ name: 'clientes-novo' })">
              + Novo Cliente
            </button>
            <button type="button" class="btn-secondary" data-test="novo-produto" @click="router.push({ name: 'produtos-novo' })">
              + Novo Produto
            </button>
          </div>
        </section>

        <section class="card">
          <h2>Status Pedidos</h2>
          <ul class="status-list" v-if="statusPedidos.length">
            <li v-for="item in statusPedidos" :key="item.label">
              <span class="status-dot" :class="`dot-${item.classe}`"></span>
              <span class="status-label">{{ item.label }}</span>
              <span class="status-value">{{ item.value }}</span>
            </li>
          </ul>
          <p v-else class="empty-state">Sem dados.</p>
        </section>
      </aside>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getSalesOrderCounts,
  listSalesOrders,
  type SalesOrderCounts,
  type SalesOrderSummary,
  type SalesOrderStatus,
} from '@/api/salesOrders'
import { getPartnerSummary, type PartnerSummary } from '@/api/partners'
import { getProductSummary, type ProductSummary } from '@/api/products'

const router = useRouter()

interface Stat {
  icon: string
  label: string
  value: string
}

// Each section is fetched and rendered independently -- a user without VIEW
// permission on one module (Module.CUSTOMER/PRODUCT/ORDER) still sees the
// rest of the dashboard; that section just falls back to "—" / an empty
// state instead of blocking the whole page.
const pedidoResumo = ref<SalesOrderCounts | null>(null)
const parceiroResumo = ref<PartnerSummary | null>(null)
const produtoResumo = ref<ProductSummary | null>(null)
const pedidosRecentes = ref<SalesOrderSummary[]>([])

const stats = computed<Stat[]>(() => [
  { icon: '📋', label: 'Total de Pedidos', value: pedidoResumo.value ? String(pedidoResumo.value.total) : '—' },
  { icon: '👥', label: 'Clientes Ativos', value: parceiroResumo.value ? String(parceiroResumo.value.active) : '—' },
  { icon: '🧾', label: 'Pedidos Faturados', value: pedidoResumo.value ? String(pedidoResumo.value.invoiced) : '—' },
  { icon: '📦', label: 'Produtos Ativos', value: produtoResumo.value ? String(produtoResumo.value.active) : '—' },
])

const STATUS_LABEL: Record<SalesOrderStatus, string> = {
  DRAFT: 'Digitado',
  IN_PREPARATION: 'Em Preparo',
  INVOICED: 'Faturado',
}

function statusLabel(status: SalesOrderStatus) {
  return STATUS_LABEL[status]
}

const statusPedidos = computed(() => {
  const r = pedidoResumo.value
  if (!r) return []
  return [
    { label: 'Digitados', value: r.draft, classe: 'DRAFT' as SalesOrderStatus },
    { label: 'Em Preparo', value: r.inPreparation, classe: 'IN_PREPARATION' as SalesOrderStatus },
    { label: 'Faturados', value: r.invoiced, classe: 'INVOICED' as SalesOrderStatus },
  ]
})

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

onMounted(async () => {
  const [pedidoR, parceiroR, produtoR, pedidosR] = await Promise.allSettled([
    getSalesOrderCounts(),
    getPartnerSummary(),
    getProductSummary(),
    listSalesOrders({ page: 0, size: 5 }),
  ])
  if (pedidoR.status === 'fulfilled') pedidoResumo.value = pedidoR.value
  if (parceiroR.status === 'fulfilled') parceiroResumo.value = parceiroR.value
  if (produtoR.status === 'fulfilled') produtoResumo.value = produtoR.value
  if (pedidosR.status === 'fulfilled') pedidosRecentes.value = pedidosR.value.content
})
</script>

<style scoped>
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px 18px;
}

.stat-icon {
  font-size: 22px;
  flex-shrink: 0;
}

.stat-label {
  font-size: 12px;
  color: var(--pm-text-mid);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.dashboard-columns {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px;
  box-sizing: border-box;
}

.card h2 {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0 0 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.card-header h2 {
  margin: 0;
}

.orders-card {
  flex: 1;
}

.orders-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.orders-table th {
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.02em;
  color: var(--pm-text-mid);
  padding: 0 8px 8px;
  border-bottom: 1px solid var(--pm-border-light);
}

.orders-table td {
  padding: 10px 8px;
  color: var(--pm-text-dark);
  border-bottom: 1px solid var(--pm-border-light);
}

.orders-table tbody tr:last-child td {
  border-bottom: none;
}

.empty-state {
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}

.badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.badge-DRAFT {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.badge-IN_PREPARATION {
  background: var(--pm-warning-bg, var(--pm-bg));
  color: var(--pm-warning, var(--pm-text-mid));
}

.badge-INVOICED {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.side-column {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.btn-primary,
.btn-secondary {
  width: 100%;
  border-radius: 8px;
  padding: 10px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--pm-font);
  box-sizing: border-box;
  cursor: pointer;
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
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

.status-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.status-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-DRAFT {
  background: var(--pm-text-mid);
}

.dot-IN_PREPARATION {
  background: var(--pm-warning, var(--pm-text-mid));
}

.dot-INVOICED {
  background: var(--pm-success);
}

.status-label {
  flex: 1;
  color: var(--pm-text-dark);
}

.status-value {
  font-weight: 700;
  color: var(--pm-text-dark);
}
</style>
