import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import SalesListView from '@/views/SalesListView.vue'
import * as salesApi from '@/api/sales'

vi.mock('@/api/sales')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/vendas', name: 'vendas', component: SalesListView }],
  })
  router.push('/vendas')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(SalesListView, { global: { plugins: [router] } }),
  }))
}

const sale = {
  id: 'v1', number: 1, customerName: 'Mercado Silva', issueDate: '2026-08-08', total: 119.8,
}

describe('SalesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(salesApi.listSales).mockResolvedValue({
      content: [sale], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the sale list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('silva')
    await flushPromises()

    expect(salesApi.listSales).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('shows an empty state when there are no sales', async () => {
    vi.mocked(salesApi.listSales).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma venda para exibir.')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(salesApi.listSales).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de vendas.')
  })
})
