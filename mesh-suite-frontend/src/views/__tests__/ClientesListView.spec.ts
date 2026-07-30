import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ClientesListView from '@/views/ClientesListView.vue'
import * as parceirosApi from '@/api/parceiros'

vi.mock('@/api/parceiros')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/clientes', name: 'clientes', component: ClientesListView },
      { path: '/clientes/novo', name: 'clientes-novo', component: { template: '<div />' } },
      { path: '/clientes/:id/editar', name: 'clientes-editar', component: { template: '<div />' } },
      { path: '/clientes/:id', name: 'clientes-detalhe', component: { template: '<div />' } },
    ],
  })
  router.push('/clientes')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ClientesListView, { global: { plugins: [router] } }),
  }))
}

const parceiroBase = {
  id: 'p1', nomeFantasia: 'Mercado Silva', razaoSocial: 'Mercado Silva Ltda',
  documento: '11222333000144', cidade: 'São Paulo', uf: 'SP', whatsapp: '(11) 3456-7890',
  status: 'ATIVO' as const,
}

describe('ClientesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(parceirosApi.listarParceiros).mockResolvedValue({
      content: [parceiroBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(parceirosApi.buscarResumoParceiros).mockResolvedValue({ total: 1, ativos: 1, emRisco: 0, bloqueados: 0 })
  })

  it('loads and displays the client list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
    expect(wrapper.text()).toContain('1 Total')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('silva')
    await flushPromises()

    expect(parceirosApi.listarParceiros).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('navigates to the create form when "+ Novo Cliente" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-cliente"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-novo')
  })

  it('navigates to the detail view when a client name is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="abrir-cliente"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-detalhe')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('toggles a client status via the Ações menu', async () => {
    vi.mocked(parceirosApi.atualizarStatusParceiro).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('.btn-acoes').trigger('click')
    await wrapper.findAll('.dropdown-acoes div')[1].trigger('click')
    await flushPromises()

    expect(parceirosApi.atualizarStatusParceiro).toHaveBeenCalledWith('p1', 'BLOQUEADO')
  })

  it('shows an error message when loading the client list fails', async () => {
    vi.mocked(parceirosApi.listarParceiros).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de clientes.')
  })
})
