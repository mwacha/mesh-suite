<template>
  <AppShell title="Dashboard">
    <div class="stats-row">
      <div class="stat-card" v-for="stat in stats" :key="stat.label">
        <span class="stat-icon">{{ stat.icon }}</span>
        <div class="stat-body">
          <div class="stat-label">{{ stat.label }}</div>
          <div class="stat-value">
            {{ stat.value }}
            <span v-if="stat.delta" class="stat-delta">{{ stat.delta }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="dashboard-columns">
      <section class="card orders-card">
        <div class="card-header">
          <h2>Últimos Pedidos</h2>
          <button
            type="button"
            class="btn-secondary btn-inert"
            title="Listagem de pedidos fora de escopo desta fatia"
          >
            Ver todos
          </button>
        </div>
        <table class="orders-table">
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
            <tr v-for="pedido in pedidos" :key="pedido.numero">
              <td>{{ pedido.numero }}</td>
              <td>{{ pedido.cliente }}</td>
              <td>{{ pedido.data }}</td>
              <td>{{ pedido.total }}</td>
              <td>
                <span class="badge" :class="`badge-${pedido.statusClasse}`">{{ pedido.status }}</span>
              </td>
              <td>
                <span class="link-inert" title="Detalhe de pedido fora de escopo desta fatia">Ver</span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <aside class="side-column">
        <section class="card">
          <h2>Ações Rápidas</h2>
          <div class="quick-actions">
            <button
              type="button"
              class="btn-primary btn-inert"
              title="Cadastro de pedidos fora de escopo desta fatia"
            >
              + Novo Pedido
            </button>
            <button type="button" class="btn-secondary" @click="router.push({ name: 'clientes-novo' })">
              + Novo Cliente
            </button>
            <button
              type="button"
              class="btn-secondary btn-inert"
              title="Cadastro de produtos fora de escopo desta fatia"
            >
              + Novo Produto
            </button>
          </div>
        </section>

        <section class="card">
          <h2>Status Pedidos</h2>
          <ul class="status-list">
            <li v-for="item in statusPedidos" :key="item.label">
              <span class="status-dot" :class="`dot-${item.classe}`"></span>
              <span class="status-label">{{ item.label }}</span>
              <span class="status-value">{{ item.value }}</span>
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'

const router = useRouter()

type StatusPedido = 'pendente' | 'aprovado' | 'faturamento' | 'cancelado'

interface Stat {
  icon: string
  label: string
  value: string
  delta?: string
}

// Dados de exemplo fixos — sem chamada de API. Nenhum módulo de negócio
// (Pedidos, Clientes, Produtos) existe ainda no backend; estes números
// só demonstram o layout de referência.
const stats: Stat[] = [
  { icon: '📋', label: 'Pedidos hoje', value: '38', delta: '+12%' },
  { icon: '👥', label: 'Clientes ativos', value: '1.240' },
  { icon: '💰', label: 'Faturamento mês', value: 'R$ 42k', delta: '+8%' },
  { icon: '📦', label: 'Produtos ativos', value: '856' },
]

interface Pedido {
  numero: string
  cliente: string
  data: string
  total: string
  status: string
  statusClasse: StatusPedido
}

const pedidos: Pedido[] = [
  { numero: '#041', cliente: 'Mercado Silva', data: 'hoje', total: 'R$ 450', status: 'Pendente', statusClasse: 'pendente' },
  { numero: '#040', cliente: 'Dist. ABC', data: 'hoje', total: 'R$ 1.200', status: 'Aprovado', statusClasse: 'aprovado' },
  { numero: '#039', cliente: 'Loja XYZ', data: 'ontem', total: 'R$ 320', status: 'Faturamento', statusClasse: 'faturamento' },
  { numero: '#038', cliente: 'Super M.', data: 'ontem', total: 'R$ 890', status: 'Cancelado', statusClasse: 'cancelado' },
  { numero: '#037', cliente: 'Farmácia Z', data: '01/06', total: 'R$ 210', status: 'Pendente', statusClasse: 'pendente' },
]

interface StatusResumo {
  label: string
  value: number
  classe: StatusPedido
}

const statusPedidos: StatusResumo[] = [
  { label: 'Pendentes', value: 12, classe: 'pendente' },
  { label: 'Aprovados', value: 18, classe: 'aprovado' },
  { label: 'Faturamento', value: 5, classe: 'faturamento' },
  { label: 'Cancelados', value: 3, classe: 'cancelado' },
]
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
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 20px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.stat-delta {
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-success);
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

.badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.badge-pendente {
  background: var(--pm-warning-bg);
  color: var(--pm-warning);
}

.badge-aprovado {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.badge-faturamento {
  background: var(--pm-accent-bg);
  color: var(--pm-accent-text);
}

.badge-cancelado {
  background: var(--pm-error-bg);
  color: var(--pm-error);
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

.btn-inert,
.link-inert {
  cursor: not-allowed;
}

.link-inert {
  color: var(--pm-accent);
  font-size: 13px;
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

.dot-pendente {
  background: var(--pm-warning);
}

.dot-aprovado {
  background: var(--pm-success);
}

.dot-faturamento {
  background: var(--pm-accent);
}

.dot-cancelado {
  background: var(--pm-error);
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
