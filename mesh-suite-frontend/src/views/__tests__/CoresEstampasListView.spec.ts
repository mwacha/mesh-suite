import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CoresEstampasListView from '@/views/CoresEstampasListView.vue'
import * as coresEstampasApi from '@/api/coresEstampas'

vi.mock('@/api/coresEstampas')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/cores-estampas', name: 'cores-estampas', component: CoresEstampasListView },
      { path: '/cores-estampas/novo', name: 'cores-estampas-novo', component: { template: '<div />' } },
      { path: '/cores-estampas/:id/editar', name: 'cores-estampas-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/cores-estampas')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> -- stub it here so it
    // renders in place instead, keeping wrapper.find() queries working.
    wrapper: mount(CoresEstampasListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const corEstampaExemplo = {
  id: 'ce-1',
  nome: 'Azul Marinho',
  dataVigencia: '2026-01-01',
  descricao: 'Cor sólida padrão',
  ativo: true,
  produtosVinculados: 3,
  criadoEm: '2026-01-01T00:00:00Z',
}

describe('CoresEstampasListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the cor/estampa list', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [corEstampaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Azul Marinho')
    expect(wrapper.text()).toContain('3 produtos')
    expect(wrapper.text()).toContain('01/01/2026')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de cores/estampas.')
  })

  it('reloads the list when the search field changes', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('Azul')
    await flushPromises()

    expect(coresEstampasApi.listarCoresEstampas).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: 'Azul' }),
    )
  })

  it('navigates to the new-cor-estampa route when the button is clicked', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-cor-estampa"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('cores-estampas-novo')
  })

  it('deletes a cor/estampa after confirmation and reloads the list', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [corEstampaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(coresEstampasApi.excluirCorEstampa).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(coresEstampasApi.excluirCorEstampa).toHaveBeenCalledWith('ce-1')
  })

  it('shows the backend message when deletion is blocked because the cor/estampa is in use', async () => {
    vi.mocked(coresEstampasApi.listarCoresEstampas).mockResolvedValue({
      content: [corEstampaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(coresEstampasApi.excluirCorEstampa).mockRejectedValue({
      response: { data: { mensagem: 'Não é possível excluir: 3 produto(s) usam esta cor/estampa' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não é possível excluir: 3 produto(s) usam esta cor/estampa')
  })
})
