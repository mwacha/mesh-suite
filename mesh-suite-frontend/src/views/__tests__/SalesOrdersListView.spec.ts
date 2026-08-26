import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import SalesOrdersListView from '@/views/SalesOrdersListView.vue'
import * as salesOrdersApi from '@/api/salesOrders'
import * as usersApi from '@/api/users'

vi.mock('@/api/salesOrders')
vi.mock('@/api/users')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/pedidos', name: 'pedidos', component: SalesOrdersListView },
      { path: '/pedidos/novo', name: 'pedidos-novo', component: { template: '<div />' } },
      { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/pedidos')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown and the filter panel are Teleported to <body> so they
    // aren't clipped by ancestor `overflow: hidden` -- stub Teleport here so
    // they render in place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(SalesOrdersListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const orderDraft = {
  id: 'ped1', number: 1, customerName: 'Mercado Silva', salespersonName: 'Carla Vendedora',
  orderDate: '2026-07-31', total: 119.8, status: 'DRAFT' as const,
}

const orderInvoiced = {
  id: 'ped2', number: 2, customerName: 'Padaria Aurora', salespersonName: 'Carla Vendedora',
  orderDate: '2026-07-30', total: 59.9, status: 'INVOICED' as const,
}

const orderInPreparation = {
  id: 'ped3', number: 3, customerName: 'Confecções Bela Vista', salespersonName: 'Carla Vendedora',
  orderDate: '2026-08-01', total: 200.0, status: 'IN_PREPARATION' as const,
}

describe('SalesOrdersListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(salesOrdersApi.listSalesOrders).mockResolvedValue({
      content: [orderDraft], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(salesOrdersApi.getSalesOrderCounts).mockResolvedValue({
      total: 1, draft: 1, inPreparation: 0, invoiced: 0,
    })
    vi.mocked(usersApi.listSalesReps).mockResolvedValue([{ id: 'v1', name: 'Carla Vendedora' }])
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

    await wrapper.find('[data-test="filter-bar-search"]').setValue('silva')
    await flushPromises()

    expect(salesOrdersApi.listSalesOrders).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('re-fetches with the selected status when a "Mais filtros" value is applied', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Status"]').trigger('click')
    await wrapper.find('[data-test="filter-value-Em Preparo"]').trigger('click')
    await wrapper.find('[data-test="filter-bar-apply"]').trigger('click')
    await flushPromises()

    expect(salesOrdersApi.listSalesOrders).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'IN_PREPARATION' }),
    )
  })

  it('re-fetches with the selected vendedor\'s id when a "Mais filtros" value is applied', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Vendedor"]').trigger('click')
    await wrapper.find('[data-test="filter-value-Carla Vendedora"]').trigger('click')
    await wrapper.find('[data-test="filter-bar-apply"]').trigger('click')
    await flushPromises()

    expect(salesOrdersApi.listSalesOrders).toHaveBeenLastCalledWith(
      expect.objectContaining({ salespersonId: 'v1' }),
    )
  })

  it('navigates to the create form when "+ Novo Pedido" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="new-order"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos-novo')
  })

  it('downloads a CSV of the current page when "Exportar" is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const createObjectURL = vi.fn().mockReturnValue('blob:mock')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL })
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    await wrapper.find('[data-test="export-orders"]').trigger('click')

    expect(createObjectURL).toHaveBeenCalled()
    expect(clickSpy).toHaveBeenCalled()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock')

    clickSpy.mockRestore()
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped1"]').trigger('click')
    await wrapper.find('[data-test="action-edit"]').trigger('click')
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
    vi.mocked(salesOrdersApi.advanceSalesOrderStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped1"]').trigger('click')
    expect(wrapper.find('[data-test="action-advance"]').text()).toBe('Avançar para Em Preparo')
    await wrapper.find('[data-test="action-advance"]').trigger('click')
    await flushPromises()

    expect(salesOrdersApi.advanceSalesOrderStatus).toHaveBeenCalledWith('ped1', 'IN_PREPARATION')
  })

  it('hides the "Avançar" item once a pedido is already Faturado', async () => {
    vi.mocked(salesOrdersApi.listSalesOrders).mockResolvedValue({
      content: [orderInvoiced], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped2"]').trigger('click')

    expect(wrapper.find('[data-test="action-advance"]').exists()).toBe(false)
  })

  it('issues the sale via the "Faturar" Ações item when status is Em Preparo', async () => {
    vi.mocked(salesOrdersApi.listSalesOrders).mockResolvedValue({
      content: [orderInPreparation], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const salesApi = await import('@/api/sales')
    vi.spyOn(salesApi, 'issueSale').mockResolvedValue({} as never)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped3"]').trigger('click')
    expect(wrapper.find('[data-test="action-issue"]').text()).toBe('Faturar')
    await wrapper.find('[data-test="action-issue"]').trigger('click')
    await flushPromises()

    expect(salesApi.issueSale).toHaveBeenCalledWith('ped3')
    expect(salesOrdersApi.advanceSalesOrderStatus).not.toHaveBeenCalled()
  })

  it('excludes a pedido via the Ações menu after confirming', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    vi.mocked(salesOrdersApi.deleteSalesOrder).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ped1"]').trigger('click')
    await wrapper.find('[data-test="action-delete"]').trigger('click')
    await flushPromises()

    expect(salesOrdersApi.deleteSalesOrder).toHaveBeenCalledWith('ped1')
  })

  it('re-fetches with the sort param when a sortable column header is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-customer"]').trigger('click')
    await flushPromises()

    expect(salesOrdersApi.listSalesOrders).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'customerName,asc' }))
  })

  it('shows an error message when loading the pedido list fails', async () => {
    vi.mocked(salesOrdersApi.listSalesOrders).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de pedidos.')
  })
})
