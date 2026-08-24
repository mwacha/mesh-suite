import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import { useAuthStore } from '@/stores/auth'
import * as pedidosApi from '@/api/pedidos'
import * as parceirosApi from '@/api/parceiros'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/pedidos')
vi.mock('@/api/parceiros')
vi.mock('@/api/produtos')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: DashboardView },
      { path: '/clientes/novo', name: 'clientes-novo', component: { template: '<div />' } },
      { path: '/produtos/novo/simples', name: 'produtos-novo-simples', component: { template: '<div />' } },
      { path: '/pedidos', name: 'pedidos', component: { template: '<div />' } },
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
  id: 'ped1', numero: 41, clienteNome: 'Mercado Silva', vendedorNome: 'Carla Vendedora',
  dataPedido: '2026-08-03', total: 450, status: 'DIGITADO' as const,
}

describe('DashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(pedidosApi.buscarResumoPedidos).mockResolvedValue({
      total: 38, digitados: 12, emPreparo: 18, faturados: 8,
    })
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [pedidoRecente], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(parceirosApi.buscarResumoParceiros).mockResolvedValue({
      total: 1500, ativos: 1240, emRisco: 30, bloqueados: 5,
    })
    vi.mocked(produtosApi.buscarResumoProdutos).mockResolvedValue({
      total: 900, ativos: 856, inativos: 44,
    })
  })

  it('greets the logged-in user by name', async () => {
    const authStore = useAuthStore()
    authStore.usuario = { nome: 'Marina Aurora', papel: 'ADMINISTRADOR' }

    const { wrapper } = await mountWithRouter()

    expect(wrapper.text()).toContain('Marina Aurora')
  })

  it('renders inside the app shell (sidebar and topbar present)', async () => {
    const { wrapper } = await mountWithRouter()

    expect(wrapper.text()).toContain('PediMais')
    expect(wrapper.text()).toContain('Dashboard')
  })

  it('loads real stats and the recent-orders table from the API on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Total de Pedidos')
    expect(wrapper.text()).toContain('38')
    expect(wrapper.text()).toContain('Clientes Ativos')
    expect(wrapper.text()).toContain('1240')
    expect(wrapper.text()).toContain('Pedidos Faturados')
    expect(wrapper.text()).toContain('Produtos Ativos')
    expect(wrapper.text()).toContain('856')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.text()).toContain('Mercado Silva')
  })

  it('renders the real order-status breakdown', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Digitados')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('Em Preparo')
    expect(wrapper.text()).toContain('18')
  })

  it('falls back to a dash for a section the caller lacks permission to view', async () => {
    vi.mocked(produtosApi.buscarResumoProdutos).mockRejectedValue({ response: { status: 403 } })

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Produtos Ativos')
    const card = wrapper.findAll('.stat-card').find((c) => c.text().includes('Produtos Ativos'))!
    expect(card.text()).toContain('—')
    // The rest of the dashboard still renders normally.
    expect(wrapper.text()).toContain('Clientes Ativos')
    expect(wrapper.text()).toContain('1240')
  })

  it('shows an empty state when there are no recent orders', async () => {
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 5,
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

  it('navigates to the produto creation form when + Novo Produto is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-produto"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-novo-simples')
  })

  it('navigates to the client creation form when + Novo Cliente is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await router.isReady()

    const novoCliente = wrapper.findAll('button').find((b) => b.text() === '+ Novo Cliente')!
    await novoCliente.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-novo')
  })

  it('navigates to the pedidos list when "Ver todos" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="ver-todos-pedidos"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos')
  })

  it('navigates to the pedido edit view when a row\'s "Ver" link is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('.link').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-editar')
    expect(router.currentRoute.value.params.id).toBe('ped1')
  })
})
