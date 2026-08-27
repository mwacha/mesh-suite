import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import SalesOrderFormView from '@/views/SalesOrderFormView.vue'
import * as salesOrdersApi from '@/api/salesOrders'
import * as partnersApi from '@/api/partners'
import * as usersApi from '@/api/users'
import * as productsApi from '@/api/products'
import * as priceTablesApi from '@/api/priceTables'
import * as paymentMethodsApi from '@/api/paymentMethods'

vi.mock('@/api/salesOrders')
vi.mock('@/api/partners')
vi.mock('@/api/users')
vi.mock('@/api/products')
vi.mock('@/api/priceTables')
vi.mock('@/api/paymentMethods')

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
    // SearchSelect's dropdown panel is Teleported to <body> -- stub it so it
    // renders in place instead, keeping wrapper.find() queries working.
    wrapper: mount(SalesOrderFormView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const customerBase = {
  id: 'c1', tradeName: 'Mercado Silva', legalName: 'Mercado Silva Ltda',
  document: '11222333000144', personType: 'LEGAL_ENTITY' as const,
  city: 'São Paulo', state: 'SP', whatsapp: '', status: 'ACTIVE' as const,
}

const salesRepBase = { id: 'v1', name: 'Carla Vendedora' }

const productBase = {
  id: 'p1', name: 'Camiseta Polo', sku: 'P0001', type: 'PRODUCT' as const,
  salePrice: 59.9, stockQuantity: 10, status: 'ACTIVE' as const,
  size: null, colorwayName: null,
}

async function selectCustomer(wrapper: Awaited<ReturnType<typeof mountWithRouter>>['wrapper']) {
  await wrapper.find('[data-test="customer-search"]').trigger('click')
  await wrapper.find('[data-test="customer-search-input"]').setValue('silva')
  await flushPromises()
  await wrapper.find('[data-test="customer-search-option-c1"]').trigger('click')
}

async function selectSalesperson(wrapper: Awaited<ReturnType<typeof mountWithRouter>>['wrapper']) {
  await wrapper.find('[data-test="salesperson"]').trigger('click')
  await wrapper.find('[data-test="salesperson-option-v1"]').trigger('click')
}

async function selectProduct(wrapper: Awaited<ReturnType<typeof mountWithRouter>>['wrapper']) {
  await wrapper.find('[data-test="product-search"]').trigger('click')
  await wrapper.find('[data-test="product-search-input"]').setValue('camiseta')
  await flushPromises()
  await wrapper.find('[data-test="product-search-option-p1"]').trigger('click')
}

describe('SalesOrderFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usersApi.listSalesReps).mockResolvedValue([salesRepBase])
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [customerBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(productsApi.listSellableProducts).mockResolvedValue({
      content: [productBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(priceTablesApi.listPriceTables).mockResolvedValue({
      content: [{
        id: 't1', name: 'Tabela Varejo', adjustmentMethod: 'MANUAL', adjustmentOperation: null,
        adjustmentValueType: null, adjustmentValue: null, effectiveStartDate: '2026-01-01',
        effectiveEndDate: null, active: true,
      }],
      totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(paymentMethodsApi.listPaymentMethods).mockResolvedValue({
      content: [{ id: 'pm1', description: 'À vista', type: 'CASH', active: true, maxInstallments: 1, installmentsCount: 1, installmentDays: [0] }],
      totalElements: 1, totalPages: 1, number: 0, size: 5,
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
    await wrapper.find('[data-test="salesperson"]').trigger('click')

    expect(wrapper.text()).toContain('Carla Vendedora')
  })

  it('searches and selects a customer via the SearchSelect dropdown', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await selectCustomer(wrapper)

    expect(partnersApi.listPartners).toHaveBeenCalledWith(
      expect.objectContaining({ busca: 'silva', papel: 'CUSTOMER' }),
    )
    expect(wrapper.find('[data-test="customer-search"]').text()).toContain('Mercado Silva')
  })

  it('lets the user pick a Tabela de Preço and a Condição de Pagamento', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="price-table"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="price-table-option-t1"]').trigger('click')

    await wrapper.find('[data-test="payment-term"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="payment-term-option-pm1"]').trigger('click')

    expect(wrapper.find('[data-test="price-table"]').text()).toContain('Tabela Varejo')
    expect(wrapper.find('[data-test="payment-term"]').text()).toContain('À vista')
  })

  it('renders the "+ Adicionar" button as the primary (blue) action, matching the wireframe', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.find('[data-test="item-add"]').classes()).toContain('btn-primary')
  })

  it('searches for a product, adds it as an item and computes totals live', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await selectProduct(wrapper)
    await wrapper.find('[data-test="item-quantity"]').setValue('2')
    await wrapper.find('[data-test="item-add"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).toContain('R$ 119,80')

    await wrapper.find('[data-test="item-remove"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Camiseta Polo')
  })

  it('shows Vlr. Produto and Total as read-only, never as editable inputs', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const unitPrice = wrapper.find('[data-test="item-unit-price"]')
    const lineTotal = wrapper.find('[data-test="item-line-total"]')
    expect(unitPrice.element.tagName).not.toBe('INPUT')
    expect(lineTotal.element.tagName).not.toBe('INPUT')
    expect(unitPrice.text()).toContain('0,00')

    await selectProduct(wrapper)
    await wrapper.find('[data-test="item-quantity"]').setValue('3')
    await flushPromises()

    expect(unitPrice.text()).toContain('59,90')
    expect(lineTotal.text()).toContain('179,70')
  })

  it('resets Vlr. Produto and Total once the pending row is added', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await selectProduct(wrapper)
    await wrapper.find('[data-test="item-quantity"]').setValue('2')
    await wrapper.find('[data-test="item-add"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="item-unit-price"]').text()).toContain('0,00')
    expect(wrapper.find('[data-test="item-line-total"]').text()).toContain('0,00')
  })

  it('takes the unit price from the selected product, matching the wireframe', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await selectCustomer(wrapper)
    await selectSalesperson(wrapper)

    await selectProduct(wrapper)
    await wrapper.find('[data-test="item-quantity"]').setValue('1')
    await wrapper.find('[data-test="item-add"]').trigger('click')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(salesOrdersApi.createSalesOrder).mock.calls[0][0]
    expect(payload.items[0].unitPrice).toBe(productBase.salePrice)
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await selectCustomer(wrapper)
    await selectSalesperson(wrapper)

    await selectProduct(wrapper)
    await wrapper.find('[data-test="item-quantity"]').setValue('1')
    await wrapper.find('[data-test="item-add"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(salesOrdersApi.createSalesOrder).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('pedidos')
  })

  it('saves the same payload via the "Salvar Rascunho" button', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await selectCustomer(wrapper)
    await selectSalesperson(wrapper)

    await selectProduct(wrapper)
    await wrapper.find('[data-test="item-quantity"]').setValue('1')
    await wrapper.find('[data-test="item-add"]').trigger('click')

    await wrapper.find('[data-test="save-draft"]').trigger('click')
    await flushPromises()

    expect(salesOrdersApi.createSalesOrder).toHaveBeenCalled()
  })

  it('navigates back to the list when "Cancelar" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cancel"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('pedidos')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(salesOrdersApi.createSalesOrder).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await selectCustomer(wrapper)
    await selectSalesperson(wrapper)

    await selectProduct(wrapper)
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
    expect(wrapper.find('[data-test="customer-search"]').text()).toContain('Mercado Silva')
    expect(wrapper.find('[data-test="salesperson"]').text()).toContain('Carla Vendedora')
    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('shows an error message when loading sales order data fails in edit mode', async () => {
    vi.mocked(salesOrdersApi.getSalesOrder).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/pedidos/ped-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do pedido.')
  })
})
