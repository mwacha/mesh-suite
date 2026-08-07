import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CategoriasListView from '@/views/CategoriasListView.vue'
import * as categoriasApi from '@/api/categorias'

vi.mock('@/api/categorias')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/categorias', name: 'categorias', component: CategoriasListView },
      { path: '/categorias/novo', name: 'categorias-novo', component: { template: '<div />' } },
      { path: '/categorias/:id/editar', name: 'categorias-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/categorias')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(CategoriasListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const categoriaExemplo = {
  id: 'cat-1',
  nome: 'Camisas',
  descricao: 'Camisas em geral',
  ativo: true,
  produtosVinculados: 3,
  criadoEm: '2026-01-01T00:00:00Z',
}

describe('CategoriasListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the category list', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [categoriaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Camisas')
    expect(wrapper.text()).toContain('3 produtos')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de categorias.')
  })

  it('reloads the list when the search field changes', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('Cam')
    await flushPromises()

    expect(categoriasApi.listarCategorias).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: 'Cam' }),
    )
  })

  it('navigates to the new-category route when the button is clicked', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-categoria"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('categorias-novo')
  })

  it('deletes a category after confirmation and reloads the list', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [categoriaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(categoriasApi.excluirCategoria).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(categoriasApi.excluirCategoria).toHaveBeenCalledWith('cat-1')
  })

  it('shows the backend message when deletion is blocked because the category is in use', async () => {
    vi.mocked(categoriasApi.listarCategorias).mockResolvedValue({
      content: [categoriaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(categoriasApi.excluirCategoria).mockRejectedValue({
      response: { data: { mensagem: 'Não é possível excluir: 3 produto(s) usam esta categoria' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não é possível excluir: 3 produto(s) usam esta categoria')
  })
})
