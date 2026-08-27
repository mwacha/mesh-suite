import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ColorwaysListView from '@/views/ColorwaysListView.vue'
import * as colorwaysApi from '@/api/colorways'

vi.mock('@/api/colorways', async (importOriginal) => {
  const original = await importOriginal<typeof colorwaysApi>()
  return {
    ...original,
    listColorways: vi.fn(),
    getColorwayCounts: vi.fn(),
    deleteColorway: vi.fn(),
  }
})

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/cores-estampas', name: 'cores-estampas', component: ColorwaysListView },
      { path: '/cores-estampas/novo', name: 'cores-estampas-novo', component: { template: '<div />' } },
      { path: '/cores-estampas/:id/editar', name: 'cores-estampas-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/cores-estampas')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> -- stub it here so it
    // renders in place instead, keeping wrapper.find() queries working.
    wrapper: mount(ColorwaysListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const colorwayExemplo = {
  id: 'ce-1',
  name: 'Azul Marinho',
  effectiveDate: '2026-01-01',
  description: 'Cor sólida padrão',
  active: true,
  linkedProducts: 3,
  createdAt: '2026-01-01T00:00:00Z',
}

function paginaCom(...content: (typeof colorwayExemplo)[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 10 }
}

describe('ColorwaysListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(colorwaysApi.getColorwayCounts).mockResolvedValue({ total: 8, active: 6, inactive: 2 })
  })

  it('loads and displays the cor/estampa list', async () => {
    vi.mocked(colorwaysApi.listColorways).mockResolvedValue(paginaCom(colorwayExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Azul Marinho')
    expect(wrapper.text()).toContain('3 produtos')
    expect(wrapper.text()).toContain('01/01/2026')
  })

  it('shows the header count and the Total/Ativas/Inativas pills', async () => {
    vi.mocked(colorwaysApi.listColorways).mockResolvedValue(paginaCom(colorwayExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('8 cores/estampas cadastradas')
    expect(wrapper.text()).toContain('Ativas')
    expect(wrapper.text()).toContain('Inativas')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(colorwaysApi.listColorways).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de cores/estampas.')
  })

  it('reloads the list when the search field changes', async () => {
    vi.mocked(colorwaysApi.listColorways).mockResolvedValue(paginaCom())
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-search"]').setValue('Azul')
    await flushPromises()

    expect(colorwaysApi.listColorways).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: 'Azul' }),
    )
  })

  it('sorts by name when the column header is clicked', async () => {
    vi.mocked(colorwaysApi.listColorways).mockResolvedValue(paginaCom(colorwayExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-nome"]').trigger('click')
    await flushPromises()

    expect(colorwaysApi.listColorways).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'name,asc' }),
    )
  })

  it('navigates to the new-cor-estampa route when the button is clicked', async () => {
    vi.mocked(colorwaysApi.listColorways).mockResolvedValue(paginaCom())
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-cor-estampa"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('cores-estampas-novo')
  })

  it('deletes a cor/estampa after confirmation and reloads the list', async () => {
    vi.mocked(colorwaysApi.listColorways).mockResolvedValue(paginaCom(colorwayExemplo))
    vi.mocked(colorwaysApi.deleteColorway).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(colorwaysApi.deleteColorway).toHaveBeenCalledWith('ce-1')
  })

  it('shows the backend message when deletion is blocked because the cor/estampa is in use', async () => {
    vi.mocked(colorwaysApi.listColorways).mockResolvedValue(paginaCom(colorwayExemplo))
    vi.mocked(colorwaysApi.deleteColorway).mockRejectedValue({
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
