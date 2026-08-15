import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import SalesOrderFormView from '@/views/SalesOrderFormView.vue'
import * as salesOrdersApi from '@/api/salesOrders'
import * as partnersApi from '@/api/partners'
import * as usersApi from '@/api/users'
import * as productsApi from '@/api/products'

vi.mock('@/api/salesOrders')
vi.mock('@/api/partners')
vi.mock('@/api/users')
vi.mock('@/api/products')

function mountWithRouter(path = '/pedidos/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/pedidos', name: 'pedidos', component: { template: '<div />' } },
      { path: '/pedidos/novo', name: 'pedidos-novo', component: SalesOrderFormView },
      { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: SalesOrderFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(SalesOrderFormView, { global: { plugins: [router] } }),
  }))
}

const customerBase = {
  id: 'c1', tradeName: 'Mercado Silva', legalName: 'Mercado Silva Ltda',
  document: '11222333000144', personType: 'LEGAL_ENTITY' as const,
  city: 'São Paulo', state: 'SP', whatsapp: '', status: 'ACTIVE' as const,
}

const salesRepBase = { id: 'v1', name: 'Carla Vendedora' }

const productBase = {
  id: 'p1', name: 'Camiseta Polo', sku: 'P0001', brand: 'Marca Alpha',
  salePrice: 59.9, stockQuantity: 10, status: 'ACTIVE' as const,
}

describe('SalesOrderFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usersApi.listSalesReps).mockResolvedValue([salesRepBase])
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [customerBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(productsApi.listProducts).mockResolvedValue({
      content: [productBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
  })

  it('shows required-field errors when customer/salesperson/items are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione um cliente')
    expect(wrapper.text()).toContain('Selecione um vendedor')
    expect(wrapper.text()).toContain('Adicione ao menos um item')
    expect(salesOrdersApi.createSalesOrder).not.toHaveBeenCalled()
  })

  it('loads the salesReps list for the salesperson select', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(usersApi.listSalesReps).toHaveBeenCalled()
    expect(wrapper.find('[data-test="salesperson"]').text()).toContain('Carla Vendedora')
  })

  it('searches and selects a customer via the busca dropdown', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="customer-search"]').setValue('silva')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenCalledWith(
      expect.objectContaining({ busca: 'silva', papel: 'CUSTOMER' }),
    )
    await wrapper.find('[data-test="customer-results"] li').trigger('click')

    expect((wrapper.find('[data-test="customer-search"]').element as HTMLInputElement).value).toBe('Mercado Silva')
  })

  it('searches for a product, adds it as an item and computes totals live', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="product-search"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="product-results"] li').trigger('click')
    await wrapper.find('[data-test="item-quantity"]').setValue('2')
    await wrapper.find('[data-test="item-add"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).toContain('R$ 119,80')

    await wrapper.find('[data-test="item-remove"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Camiseta Polo')
  })

  it('normalizes a cleared unitPrice to the number 0 (not empty-string) when added immediately', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="customer-search"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="customer-results"] li').trigger('click')
    await wrapper.find('[data-test="salesperson"]').setValue('v1')

    await wrapper.find('[data-test="product-search"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="product-results"] li').trigger('click')
    // Simulate the auto-filled unit price being manually cleared, then
    // "Adicionar" clicked immediately -- v-model.number drives the underlying
    // value to '' (empty string) when cleared, and addItem() must
    // normalize that '' to 0 before it lands in form.items/payload. This must
    // NOT be refilled before clicking Adicionar, or the empty-string state
    // never reaches addItem() and the normalization guard goes untested.
    await wrapper.find('[data-test="item-unit-price"]').setValue('')
    await wrapper.find('[data-test="item-quantity"]').setValue('1')
    await wrapper.find('[data-test="item-add"]').trigger('click')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(salesOrdersApi.createSalesOrder).mock.calls[0][0]
    expect(payload.items[0].unitPrice).toBe(0)
    expect(typeof payload.items[0].unitPrice).toBe('number')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="customer-search"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="customer-results"] li').trigger('click')
    await wrapper.find('[data-test="salesperson"]').setValue('v1')

    await wrapper.find('[data-test="product-search"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="product-results"] li').trigger('click')
    await wrapper.find('[data-test="item-quantity"]').setValue('1')
    await wrapper.find('[data-test="item-add"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(salesOrdersApi.createSalesOrder).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('pedidos')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="customer-search"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="customer-results"] li').trigger('click')
    await wrapper.find('[data-test="salesperson"]').setValue('v1')

    await wrapper.find('[data-test="product-search"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="product-results"] li').trigger('click')
    await wrapper.find('[data-test="item-quantity"]').setValue('1')
    await wrapper.find('[data-test="item-add"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing sales order data in edit mode', async () => {
    vi.mocked(salesOrdersApi.getSalesOrder).mockResolvedValue({
      id: 'ped-1', number: 3, customerId: 'c1', customerName: 'Mercado Silva', salespersonId: 'v1',
      salespersonName: 'Carla Vendedora', orderDate: '2026-07-31', deliveryDate: null, status: 'DRAFT',
      discount: 0, subtotal: 119.8, total: 119.8,
      items: [{ productId: 'p1', productName: 'Camiseta Polo', quantity: 2, unitPrice: 59.9, totalAmount: 119.8 }],
    } as any)

    const { wrapper } = await mountWithRouter('/pedidos/ped-1/editar')
    await flushPromises()

    expect(salesOrdersApi.getSalesOrder).toHaveBeenCalledWith('ped-1')
    expect((wrapper.find('[data-test="customer-search"]').element as HTMLInputElement).value).toBe('Mercado Silva')
    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('shows an error message when loading sales order data fails in edit mode', async () => {
    vi.mocked(salesOrdersApi.getSalesOrder).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/pedidos/ped-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do pedido.')
  })
})
