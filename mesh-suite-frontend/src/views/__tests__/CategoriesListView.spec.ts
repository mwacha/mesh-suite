import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CategoriesListView from '@/views/CategoriesListView.vue'
import * as categoriesApi from '@/api/categories'

vi.mock('@/api/categories')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/categorias', name: 'categorias', component: CategoriesListView },
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
    wrapper: mount(CategoriesListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const categoriaExemplo = {
  id: 'cat-1',
  name: 'Camisas',
  description: 'Camisas em geral',
  active: true,
  linkedProducts: 3,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('CategoriesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the category list', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue({
      content: [categoriaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Camisas')
    expect(wrapper.text()).toContain('3 produtos')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(categoriesApi.listCategories).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de categorias.')
  })

  it('reloads the list when the search field changes', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('Cam')
    await flushPromises()

    expect(categoriesApi.listCategories).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: 'Cam' }),
    )
  })

  it('navigates to the new-category route when the button is clicked', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-categoria"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('categorias-novo')
  })

  it('deletes a category after confirmation and reloads the list', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue({
      content: [categoriaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(categoriesApi.deleteCategory).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(categoriesApi.deleteCategory).toHaveBeenCalledWith('cat-1')
  })

  it('shows the backend message when deletion is blocked because the category is in use', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue({
      content: [categoriaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(categoriesApi.deleteCategory).mockRejectedValue({
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
