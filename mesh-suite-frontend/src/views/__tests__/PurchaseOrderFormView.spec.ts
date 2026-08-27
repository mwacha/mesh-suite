import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PurchaseOrderFormView from '@/views/PurchaseOrderFormView.vue'
import * as purchaseOrdersApi from '@/api/purchaseOrders'
import * as partnersApi from '@/api/partners'
import * as usersApi from '@/api/users'
import * as produtosApi from '@/api/products'

vi.mock('@/api/purchaseOrders')
vi.mock('@/api/partners')
vi.mock('@/api/users')
vi.mock('@/api/products')

function mountWithRouter(path = '/compras/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/compras', name: 'compras', component: { template: '<div />' } },
      { path: '/compras/novo', name: 'compras-novo', component: PurchaseOrderFormView },
      { path: '/compras/:id/editar', name: 'compras-editar', component: PurchaseOrderFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PurchaseOrderFormView, { global: { plugins: [router] } }),
  }))
}

const fornecedorBase = {
  id: 'f1', tradeName: 'Tecidos Aurora', legalName: 'Tecidos Aurora Ltda',
  document: '11222333000144', personType: 'LEGAL_ENTITY' as const,
  city: 'São Paulo', state: 'SP', whatsapp: '', status: 'ACTIVE' as const,
}

const compradorBase = { id: 'b1', name: 'Carlos Comprador' }

const produtoBase = {
  id: 'p1', name: 'Tecido Algodão', sku: 'P0001',
  salePrice: 25.0, stockQuantity: 100, status: 'ACTIVE' as const,
}

describe('PurchaseOrderFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usersApi.listBuyers).mockResolvedValue([compradorBase])
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [fornecedorBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [produtoBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
  })

  it('shows required-field errors when fornecedor/comprador/items are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione um fornecedor')
    expect(wrapper.text()).toContain('Selecione um comprador')
    expect(wrapper.text()).toContain('Adicione ao menos um item')
    expect(purchaseOrdersApi.createPurchaseOrder).not.toHaveBeenCalled()
  })

  it('loads the compradores list for the comprador select', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(usersApi.listBuyers).toHaveBeenCalled()
    expect(wrapper.find('[data-test="comprador"]').text()).toContain('Carlos Comprador')
  })

  it('searches and selects a fornecedor via the busca dropdown', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="fornecedor-busca"]').setValue('aurora')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenCalledWith(
      expect.objectContaining({ busca: 'aurora', papel: 'SUPPLIER' }),
    )
    await wrapper.find('[data-test="fornecedor-resultados"] li').trigger('click')

    expect((wrapper.find('[data-test="fornecedor-busca"]').element as HTMLInputElement).value).toBe('Tecidos Aurora')
  })

  it('searches for a produto, adds it as an item and computes totals live', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="produto-busca"]').setValue('algodão')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('10')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Tecido Algodão')
    // pt-BR currency formatting inserts a non-breaking space (U+00A0) between
    // "R$" and the amount; normalize to a regular space before comparing.
    expect(wrapper.text().replace(/ /g, ' ')).toContain('R$ 250,00')

    await wrapper.find('[data-test="item-remover"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Tecido Algodão')
  })

  it('normalizes a cleared unitPrice to the number 0 (not empty-string) when added immediately', async () => {
    vi.mocked(purchaseOrdersApi.createPurchaseOrder).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="fornecedor-busca"]').setValue('aurora')
    await flushPromises()
    await wrapper.find('[data-test="fornecedor-resultados"] li').trigger('click')
    await wrapper.find('[data-test="comprador"]').setValue('b1')

    await wrapper.find('[data-test="produto-busca"]').setValue('algodão')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    // Simulate the auto-filled valor unitário being manually cleared, then
    // "Adicionar" clicked immediately -- v-model.number drives the underlying
    // value to '' (empty string) when cleared, and adicionarItem() must
    // normalize that '' to 0 before it lands in form.items/payload. This must
    // NOT be refilled before clicking Adicionar, or the empty-string state
    // never reaches adicionarItem() and the normalization guard goes untested.
    await wrapper.find('[data-test="item-valor-unitario"]').setValue('')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(purchaseOrdersApi.createPurchaseOrder).mock.calls[0][0]
    expect(payload.items[0].unitPrice).toBe(0)
    expect(typeof payload.items[0].unitPrice).toBe('number')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(purchaseOrdersApi.createPurchaseOrder).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="fornecedor-busca"]').setValue('aurora')
    await flushPromises()
    await wrapper.find('[data-test="fornecedor-resultados"] li').trigger('click')
    await wrapper.find('[data-test="comprador"]').setValue('b1')

    await wrapper.find('[data-test="produto-busca"]').setValue('algodão')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(purchaseOrdersApi.createPurchaseOrder).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('compras')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(purchaseOrdersApi.createPurchaseOrder).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="fornecedor-busca"]').setValue('aurora')
    await flushPromises()
    await wrapper.find('[data-test="fornecedor-resultados"] li').trigger('click')
    await wrapper.find('[data-test="comprador"]').setValue('b1')

    await wrapper.find('[data-test="produto-busca"]').setValue('algodão')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing purchase order data in edit mode', async () => {
    vi.mocked(purchaseOrdersApi.getPurchaseOrder).mockResolvedValue({
      id: 'po-1', number: 3, supplierId: 'f1', supplierName: 'Tecidos Aurora', buyerId: 'b1',
      buyerName: 'Carlos Comprador', orderDate: '2026-08-04', expectedDeliveryDate: null, status: 'OPEN',
      discount: 0, subtotal: 250.0, total: 250.0,
      items: [{ productId: 'p1', productName: 'Tecido Algodão', quantity: 10, unitPrice: 25.0, totalValue: 250.0 }],
    } as any)

    const { wrapper } = await mountWithRouter('/compras/po-1/editar')
    await flushPromises()

    expect(purchaseOrdersApi.getPurchaseOrder).toHaveBeenCalledWith('po-1')
    expect((wrapper.find('[data-test="fornecedor-busca"]').element as HTMLInputElement).value).toBe('Tecidos Aurora')
    expect(wrapper.text()).toContain('Tecido Algodão')
  })

  it('shows an error message when loading purchase order data fails in edit mode', async () => {
    vi.mocked(purchaseOrdersApi.getPurchaseOrder).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/compras/po-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da ordem de compra.')
  })
})
