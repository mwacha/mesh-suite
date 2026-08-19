import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PurchaseInvoicesListView from '@/views/PurchaseInvoicesListView.vue'
import * as purchaseInvoicesApi from '@/api/purchaseInvoices'

vi.mock('@/api/purchaseInvoices')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/notas-fiscais-entrada', name: 'notas-fiscais-entrada', component: PurchaseInvoicesListView }],
  })
  router.push('/notas-fiscais-entrada')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PurchaseInvoicesListView, { global: { plugins: [router] } }),
  }))
}

const invoice = {
  id: 'pi1', number: 1, invoiceNumber: 'NF-1001', supplierName: 'Tecidos Aurora', issueDate: '2026-08-10', total: 200.0,
}

describe('PurchaseInvoicesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(purchaseInvoicesApi.listPurchaseInvoices).mockResolvedValue({
      content: [invoice], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the purchase invoice list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('NF-1001')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('aurora')
    await flushPromises()

    expect(purchaseInvoicesApi.listPurchaseInvoices).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'aurora' }))
  })

  it('shows an empty state when there are no purchase invoices', async () => {
    vi.mocked(purchaseInvoicesApi.listPurchaseInvoices).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma compra para exibir.')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(purchaseInvoicesApi.listPurchaseInvoices).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de compras.')
  })
})
