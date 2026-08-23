import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import FornecedoresListView from '@/views/FornecedoresListView.vue'
import * as partnersApi from '@/api/partners'
import * as municipiosApi from '@/api/municipalities'

vi.mock('@/api/partners')
vi.mock('@/api/municipalities')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/fornecedores', name: 'fornecedores', component: FornecedoresListView },
      { path: '/fornecedores/novo', name: 'fornecedores-novo', component: { template: '<div />' } },
      { path: '/fornecedores/:id/editar', name: 'fornecedores-editar', component: { template: '<div />' } },
      { path: '/fornecedores/:id', name: 'fornecedores-detalhe', component: { template: '<div />' } },
    ],
  })
  router.push('/fornecedores')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown/filter panels Teleport to <body> -- stub it here
    // so it renders in place, keeping wrapper.find() queries working.
    wrapper: mount(FornecedoresListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const parceiroBase = {
  id: 'p1', tradeName: 'Tecidos Aurora', legalName: 'Tecidos Aurora Ltda',
  document: '11222333000144', personType: 'LEGAL_ENTITY' as const,
  city: 'São Paulo', state: 'SP', whatsapp: '11934567890',
  status: 'ACTIVE' as const,
}

describe('FornecedoresListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [parceiroBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(partnersApi.getPartnerSummary).mockResolvedValue({ total: 1, active: 1, atRisk: 0, blocked: 0 })
    vi.mocked(municipiosApi.listMunicipalities).mockResolvedValue(['São Paulo'])
  })

  it('loads and displays the supplier list on mount, with the count in the page header', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('1 fornecedores cadastrados')
  })

  it('only lists Fornecedores, never Clientes/Transportadoras', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ papel: 'SUPPLIER' }))
    expect(partnersApi.getPartnerSummary).toHaveBeenCalledWith('SUPPLIER')
  })

  it('re-fetches with the search term when the search field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-search"]').setValue('aurora')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'aurora' }))
  })

  it('navigates to the create form when "+ Novo Fornecedor" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-fornecedor"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores-novo')
  })

  it('navigates to the detail view via the Ações menu\'s "Ver" item', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-ver"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores-detalhe')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-p1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('toggles a supplier status via the Ações menu', async () => {
    vi.mocked(partnersApi.updatePartnerStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-status"]').trigger('click')
    await flushPromises()

    expect(partnersApi.updatePartnerStatus).toHaveBeenCalledWith('p1', 'BLOCKED')
  })

  it('re-fetches with the new page when pagination is used', async () => {
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [parceiroBase], totalElements: 25, totalPages: 3, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="pagination-page-2"]').trigger('click')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
  })

  it('shows an error message when loading the supplier list fails', async () => {
    vi.mocked(partnersApi.listPartners).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de fornecedores.')
  })
})
