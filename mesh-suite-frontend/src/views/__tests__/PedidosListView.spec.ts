import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PedidosListView from '@/views/PedidosListView.vue'
import * as pedidosApi from '@/api/pedidos'

vi.mock('@/api/pedidos')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/pedidos', name: 'pedidos', component: PedidosListView },
      { path: '/pedidos/novo', name: 'pedidos-novo', component: { template: '<div />' } },
      { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/pedidos')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(PedidosListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const pedidoDigitado = {
  id: 'ped1', numero: 1, clienteNome: 'Mercado Silva', vendedorNome: 'Carla Vendedora',
  dataPedido: '2026-07-31', total: 119.8, status: 'DIGITADO' as const,
}

const pedidoFaturado = {
  id: 'ped2', numero: 2, clienteNome: 'Padaria Aurora', vendedorNome: 'Carla Vendedora',
  dataPedido: '2026-07-30', total: 59.9, status: 'FATURADO' as const,
}

const pedidoEmPreparo = {
  id: 'ped3', numero: 3, clienteNome: 'Confecções Bela Vista', vendedorNome: 'Carla Vendedora',
  dataPedido: '2026-08-01', total: 200.0, status: 'EM_PREPARO' as const,
}

describe('PedidosListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [pedidoDigitado], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(pedidosApi.buscarResumoPedidos).mockResolvedValue({
      total: 1, digitados: 1, emPreparo: 0, faturados: 0,
    })
  })

  it('loads and displays the pedido list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
    expect(wrapper.text()).toContain('1 pedidos cadastrados')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('silva')
    await flushPromises()

    expect(pedidosApi.listarPedidos).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('navigates to the create form when "+ Novo Pedido" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-pedido"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-novo')
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped1"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-editar')
    expect(router.currentRoute.value.params.id).toBe('ped1')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-ped1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-editar')
    expect(router.currentRoute.value.params.id).toBe('ped1')
  })

  it('advances the status via the "Avançar para Em Preparo" Ações item', async () => {
    vi.mocked(pedidosApi.avancarStatusPedido).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped1"]').trigger('click')
    expect(wrapper.find('[data-test="acao-avancar"]').text()).toBe('Avançar para Em Preparo')
    await wrapper.find('[data-test="acao-avancar"]').trigger('click')
    await flushPromises()

    expect(pedidosApi.avancarStatusPedido).toHaveBeenCalledWith('ped1', 'EM_PREPARO')
  })

  it('hides the "Avançar" item once a pedido is already Faturado', async () => {
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [pedidoFaturado], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped2"]').trigger('click')

    expect(wrapper.find('[data-test="acao-avancar"]').exists()).toBe(false)
  })

  it('faturns the pedido via the "Faturar" Ações item when status is Em Preparo', async () => {
    vi.mocked(pedidosApi.listarPedidos).mockResolvedValue({
      content: [pedidoEmPreparo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const vendasApi = await import('@/api/vendas')
    vi.spyOn(vendasApi, 'faturarPedido').mockResolvedValue({} as never)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped3"]').trigger('click')
    expect(wrapper.find('[data-test="acao-faturar"]').text()).toBe('Faturar')
    await wrapper.find('[data-test="acao-faturar"]').trigger('click')
    await flushPromises()

    expect(vendasApi.faturarPedido).toHaveBeenCalledWith('ped3')
    expect(pedidosApi.avancarStatusPedido).not.toHaveBeenCalled()
  })

  it('excludes a pedido via the Ações menu after confirming', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    vi.mocked(pedidosApi.excluirPedido).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped1"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(pedidosApi.excluirPedido).toHaveBeenCalledWith('ped1')
  })

  it('re-fetches with the sort param when a sortable column header is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-cliente"]').trigger('click')
    await flushPromises()

    expect(pedidosApi.listarPedidos).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'clienteNome,asc' }))
  })

  it('shows an error message when loading the pedido list fails', async () => {
    vi.mocked(pedidosApi.listarPedidos).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de pedidos.')
  })
})
