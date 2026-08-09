import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import VendasListView from '@/views/VendasListView.vue'
import * as vendasApi from '@/api/vendas'

vi.mock('@/api/vendas')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/vendas', name: 'vendas', component: VendasListView }],
  })
  router.push('/vendas')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(VendasListView, { global: { plugins: [router] } }),
  }))
}

const venda = {
  id: 'v1', numero: 1, clienteNome: 'Mercado Silva', dataEmissao: '2026-08-08', total: 119.8,
}

describe('VendasListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(vendasApi.listarVendas).mockResolvedValue({
      content: [venda], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the venda list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('silva')
    await flushPromises()

    expect(vendasApi.listarVendas).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('shows an empty state when there are no vendas', async () => {
    vi.mocked(vendasApi.listarVendas).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma venda para exibir.')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(vendasApi.listarVendas).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de vendas.')
  })
})
