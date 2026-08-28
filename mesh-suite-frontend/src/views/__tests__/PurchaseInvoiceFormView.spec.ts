import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PurchaseInvoiceFormView from '@/views/PurchaseInvoiceFormView.vue'
import * as purchaseOrdersApi from '@/api/purchaseOrders'
import * as purchaseInvoicesApi from '@/api/purchaseInvoices'

vi.mock('@/api/purchaseOrders')
vi.mock('@/api/purchaseInvoices')

const order = {
  id: 'po1', number: 7, supplierId: 's1', supplierName: 'Tecidos Aurora', buyerId: 'b1', buyerName: 'Carlos Comprador',
  orderDate: '2026-08-01', expectedDeliveryDate: null, status: 'OPEN' as const, discount: 0, subtotal: 200, total: 200,
  items: [{ productId: 'p1', productName: 'Tecido Algodão', quantity: 2, unitPrice: 100, totalValue: 200 }],
}

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/compras/:id/nota-fiscal', name: 'compras-nota-fiscal', component: PurchaseInvoiceFormView },
      { path: '/compras', name: 'compras', component: { template: '<div />' } },
    ],
  })
  router.push('/compras/po1/nota-fiscal')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PurchaseInvoiceFormView, { global: { plugins: [router] } }),
  }))
}

describe('PurchaseInvoiceFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(purchaseOrdersApi.getPurchaseOrder).mockResolvedValue(order)
  })

  it('loads the purchase order and shows its read-only items and total', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('Tecido Algodão')
    expect(wrapper.text()).toContain('R$ 200,00')
  })

  it('keeps the submit button disabled until the installments sum matches the total', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nota-numero"]').setValue('NF-1001')
    await wrapper.find('[data-test="nota-serie"]').setValue('1')
    await wrapper.find('[data-test="nota-modelo"]').setValue('55')
    await wrapper.find('[data-test="nota-data-emissao"]').setValue('2026-08-10')
    await wrapper.find('[data-test="nota-data-entrada"]').setValue('2026-08-12')

    expect(wrapper.find('[data-test="salvar"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="parcela-adicionar"]').trigger('click')
    await wrapper.find('[data-test="parcela-valor-0"]').setValue('10000')
    await wrapper.find('[data-test="parcela-vencimento-0"]').setValue('2026-09-01')
    await wrapper.find('[data-test="parcela-adicionar"]').trigger('click')
    await wrapper.find('[data-test="parcela-valor-1"]').setValue('10000')
    await wrapper.find('[data-test="parcela-vencimento-1"]').setValue('2026-10-01')

    expect(wrapper.find('[data-test="salvar"]').attributes('disabled')).toBeUndefined()
  })

  it('issues the purchase invoice and navigates to the list on success', async () => {
    vi.mocked(purchaseInvoicesApi.issuePurchaseInvoice).mockResolvedValue({} as never)
    const { wrapper, router } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nota-numero"]').setValue('NF-1001')
    await wrapper.find('[data-test="nota-serie"]').setValue('1')
    await wrapper.find('[data-test="nota-modelo"]').setValue('55')
    await wrapper.find('[data-test="nota-data-emissao"]').setValue('2026-08-10')
    await wrapper.find('[data-test="nota-data-entrada"]').setValue('2026-08-12')
    await wrapper.find('[data-test="parcela-adicionar"]').trigger('click')
    await wrapper.find('[data-test="parcela-valor-0"]').setValue('20000')
    await wrapper.find('[data-test="parcela-vencimento-0"]').setValue('2026-09-10')

    await wrapper.find('[data-test="salvar"]').trigger('click')
    await flushPromises()

    expect(purchaseInvoicesApi.issuePurchaseInvoice).toHaveBeenCalledWith('po1', {
      invoiceNumber: 'NF-1001',
      series: '1',
      model: '55',
      issueDate: '2026-08-10',
      entryDate: '2026-08-12',
      installments: [{ amount: 200, dueDate: '2026-09-10' }],
    })
    expect(router.currentRoute.value.name).toBe('compras')
  })

  it('shows an error message when issuing fails', async () => {
    vi.mocked(purchaseInvoicesApi.issuePurchaseInvoice).mockRejectedValue(new Error('boom'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nota-numero"]').setValue('NF-1001')
    await wrapper.find('[data-test="nota-serie"]').setValue('1')
    await wrapper.find('[data-test="nota-modelo"]').setValue('55')
    await wrapper.find('[data-test="nota-data-emissao"]').setValue('2026-08-10')
    await wrapper.find('[data-test="nota-data-entrada"]').setValue('2026-08-12')
    await wrapper.find('[data-test="parcela-adicionar"]').trigger('click')
    await wrapper.find('[data-test="parcela-valor-0"]').setValue('20000')
    await wrapper.find('[data-test="parcela-vencimento-0"]').setValue('2026-09-10')

    await wrapper.find('[data-test="salvar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível lançar a compra.')
  })

  it('shows the backend validation message when issuing fails with a 400 response', async () => {
    vi.mocked(purchaseInvoicesApi.issuePurchaseInvoice).mockRejectedValue({
      response: { status: 400, data: { mensagem: 'Já existe uma nota NF-1001 cadastrada para este fornecedor' } },
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nota-numero"]').setValue('NF-1001')
    await wrapper.find('[data-test="nota-serie"]').setValue('1')
    await wrapper.find('[data-test="nota-modelo"]').setValue('55')
    await wrapper.find('[data-test="nota-data-emissao"]').setValue('2026-08-10')
    await wrapper.find('[data-test="nota-data-entrada"]').setValue('2026-08-12')
    await wrapper.find('[data-test="parcela-adicionar"]').trigger('click')
    await wrapper.find('[data-test="parcela-valor-0"]').setValue('20000')
    await wrapper.find('[data-test="parcela-vencimento-0"]').setValue('2026-09-10')

    await wrapper.find('[data-test="salvar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma nota NF-1001 cadastrada para este fornecedor')
    expect(wrapper.text()).not.toContain('Não foi possível lançar a compra.')
  })
})
