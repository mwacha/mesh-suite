import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PurchaseOrdersListView from '@/views/PurchaseOrdersListView.vue'
import * as purchaseOrdersApi from '@/api/purchaseOrders'

vi.mock('@/api/purchaseOrders')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/compras', name: 'compras', component: PurchaseOrdersListView },
      { path: '/compras/novo', name: 'compras-novo', component: { template: '<div />' } },
      { path: '/compras/:id/editar', name: 'compras-editar', component: { template: '<div />' } },
      { path: '/compras/:id/nota-fiscal', name: 'compras-nota-fiscal', component: { template: '<div />' } },
    ],
  })
  router.push('/compras')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(PurchaseOrdersListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const ordemAberta = {
  id: 'po1', number: 1, supplierName: 'Tecidos Aurora', buyerName: 'Carlos Comprador',
  orderDate: '2026-08-03', total: 250.0, status: 'OPEN' as const,
}

const ordemRecebida = {
  id: 'po2', number: 2, supplierName: 'Botões Boreal', buyerName: 'Carlos Comprador',
  orderDate: '2026-08-02', total: 90.0, status: 'RECEIVED' as const,
}

describe('PurchaseOrdersListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(purchaseOrdersApi.listPurchaseOrders).mockResolvedValue({
      content: [ordemAberta], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(purchaseOrdersApi.getPurchaseOrderCounts).mockResolvedValue({
      total: 1, open: 1, received: 0, cancelled: 0,
    })
  })

  it('loads and displays the purchase order list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('1 ordens de compra cadastradas')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('aurora')
    await flushPromises()

    expect(purchaseOrdersApi.listPurchaseOrders).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'aurora' }))
  })

  it('navigates to the create form when "+ Nova Ordem de Compra" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-ordem"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('compras-novo')
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-po1"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('compras-editar')
    expect(router.currentRoute.value.params.id).toBe('po1')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-po1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('compras-editar')
    expect(router.currentRoute.value.params.id).toBe('po1')
  })

  it('re-fetches with the sort param when a sortable column header is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-fornecedor"]').trigger('click')
    await flushPromises()

    expect(purchaseOrdersApi.listPurchaseOrders).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'supplierName,asc' }),
    )
  })

  it('navigates to the Lançar Compra screen via the Ações menu', async () => {
    const { wrapper, router } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-po1"]').trigger('click')
    expect(wrapper.find('[data-test="acao-lancar-compra"]').text()).toBe('Lançar Compra')
    await wrapper.find('[data-test="acao-lancar-compra"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('compras-nota-fiscal')
    expect(router.currentRoute.value.params.id).toBe('po1')
    expect(purchaseOrdersApi.updatePurchaseOrderStatus).not.toHaveBeenCalled()
  })

  it('cancels the order via the Ações menu', async () => {
    vi.mocked(purchaseOrdersApi.updatePurchaseOrderStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-po1"]').trigger('click')
    await wrapper.find('[data-test="acao-cancelar"]').trigger('click')
    await flushPromises()

    expect(purchaseOrdersApi.updatePurchaseOrderStatus).toHaveBeenCalledWith('po1', 'CANCELLED')
  })

  it('hides the receber/cancelar actions once an order is already Recebida', async () => {
    vi.mocked(purchaseOrdersApi.listPurchaseOrders).mockResolvedValue({
      content: [ordemRecebida], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-po2"]').trigger('click')

    expect(wrapper.find('[data-test="acao-lancar-compra"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="acao-cancelar"]').exists()).toBe(false)
  })

  it('excludes an order via the Ações menu after confirming', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    vi.mocked(purchaseOrdersApi.deletePurchaseOrder).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-po1"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(purchaseOrdersApi.deletePurchaseOrder).toHaveBeenCalledWith('po1')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(purchaseOrdersApi.listPurchaseOrders).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de ordens de compra.')
  })
})
