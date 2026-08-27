import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import { useAuthStore } from '@/stores/auth'
import * as pedidosApi from '@/api/salesOrders'
import * as partnersApi from '@/api/partners'

vi.mock('@/api/salesOrders')
vi.mock('@/api/partners')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: DashboardView },
      { path: '/pedidos/novo', name: 'pedidos-novo', component: { template: '<div />' } },
      { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(DashboardView, { global: { plugins: [router] } }),
  }))
}

const pedidoRecente = {
  id: 'ped1', number: 41, customerName: 'Mercado Silva', salespersonName: 'Carla Vendedora',
  orderDate: '2026-08-03', total: 450, status: 'DRAFT' as const,
}
const pedidoRecente2 = {
  id: 'ped2', number: 40, customerName: 'Loja XYZ', salespersonName: 'Roberto Vendas',
  orderDate: '2026-08-02', total: 300, status: 'IN_PREPARATION' as const,
}
const pedidoRecente3 = {
  id: 'ped3', number: 39, customerName: 'Padaria São João', salespersonName: 'Juliana Comercial',
  orderDate: '2026-08-01', total: 900, status: 'INVOICED' as const,
}

const chartPoints = [
  { label: '1', count: 2 },
  { label: '2', count: 5 },
]

describe('DashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(pedidosApi.listSalesOrders).mockResolvedValue({
      content: [pedidoRecente, pedidoRecente2, pedidoRecente3], totalElements: 3, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(pedidosApi.getMonthlyRevenue).mockResolvedValue({ currentMonthRevenue: 4200 })
    vi.mocked(pedidosApi.getOrdersByPeriod).mockResolvedValue(chartPoints)
    vi.mocked(partnersApi.getPartnerSummary).mockResolvedValue({
      total: 1500, active: 1240, atRisk: 30, blocked: 5,
    })
  })

  it('greets the logged-in user by name', async () => {
    const authStore = useAuthStore()
    authStore.usuario = { nome: 'Marina Aurora', papel: 'ADMINISTRADOR' }

    const { wrapper } = await mountWithRouter()

    expect(wrapper.text()).toContain('Marina')
  })

  it('renders inside the app shell (sidebar and topbar present)', async () => {
    const { wrapper } = await mountWithRouter()

    expect(wrapper.text()).toContain('PediMais')
    expect(wrapper.text()).toContain('Dashboard')
  })

  it('loads the customer and revenue KPIs from the API on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Clientes Ativos')
    expect(wrapper.text()).toContain('1240')
    expect(wrapper.text()).toContain('Clientes em Risco')
    expect(wrapper.text()).toContain('30')
    expect(wrapper.text()).toContain('Clientes Bloqueados')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('Faturamento do Mês')
    expect(wrapper.text()).toContain('4.200')
  })

  it('loads the recent-orders table and status pills from the API on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Digitados')
    expect(wrapper.text()).toContain('Em Preparo')
    expect(wrapper.text()).toContain('Faturados')
    expect(wrapper.text()).toContain('Mercado Silva')
  })

  it('counts the status pills from only the orders shown in the table, not the tenant-wide total', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.findAll('.table-grid-row')).toHaveLength(3)

    const pill = (label: string) => wrapper.findAll('.stat-pill').find((p) => p.text().includes(label))!
    expect(pill('Total').text()).toContain('3')
    expect(pill('Digitados').text()).toContain('1')
    expect(pill('Em Preparo').text()).toContain('1')
    expect(pill('Faturados').text()).toContain('1')
  })

  it('falls back to a dash for a section the caller lacks permission to view', async () => {
    vi.mocked(partnersApi.getPartnerSummary).mockRejectedValue({ response: { status: 403 } })

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Clientes Ativos')
    const kpi = wrapper.findAll('.kpi-card').find((c) => c.text().includes('Clientes Ativos'))!
    expect(kpi.text()).toContain('—')
    // The rest of the dashboard still renders normally.
    expect(wrapper.text()).toContain('Faturamento do Mês')
    expect(wrapper.text()).toContain('4.200')
  })

  it('shows an empty state when there are no recent orders', async () => {
    vi.mocked(pedidosApi.listSalesOrders).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhum pedido para exibir.')
  })

  it('navigates to the pedido creation form when + Novo Pedido is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-pedido"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-novo')
  })

  it('navigates to the pedido edit view when a row\'s "Ver" link is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('.link').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-editar')
    expect(router.currentRoute.value.params.id).toBe('ped1')
  })

  it('loads the current-month chart by default and switches to the last-12-months period', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(pedidosApi.getOrdersByPeriod).toHaveBeenLastCalledWith('CURRENT_MONTH')

    await wrapper.find('[data-test="periodo-LAST_12_MONTHS"]').trigger('click')
    await flushPromises()

    expect(pedidosApi.getOrdersByPeriod).toHaveBeenLastCalledWith('LAST_12_MONTHS')
  })
})
