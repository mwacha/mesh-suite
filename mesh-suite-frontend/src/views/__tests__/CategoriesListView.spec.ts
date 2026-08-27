import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CategoriesListView from '@/views/CategoriesListView.vue'
import * as categoriesApi from '@/api/categories'
import type { CategoryResponse } from '@/api/categories'

vi.mock('@/api/categories', async (importOriginal) => {
  const original = await importOriginal<typeof categoriesApi>()
  return {
    ...original,
    listCategories: vi.fn(),
    getCategoryCounts: vi.fn(),
    deleteCategory: vi.fn(),
  }
})

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
    wrapper: mount(CategoriesListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const categoriaExemplo: CategoryResponse = {
  id: 'cat-1',
  name: 'Beleza',
  description: null,
  active: true,
  parentId: 'cat-0',
  parentName: 'Higiene Pessoal',
  linkedProducts: 3,
  createdAt: '2026-01-01T00:00:00Z',
}

function paginaCom(...content: CategoryResponse[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 10 }
}

describe('CategoriesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(categoriesApi.getCategoryCounts).mockResolvedValue({ total: 7, active: 6, inactive: 1 })
  })

  it('loads and displays the category list with its parent category', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue(paginaCom(categoriaExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Beleza')
    expect(wrapper.text()).toContain('Higiene Pessoal')
    expect(wrapper.text()).toContain('3 produtos')
  })

  it('shows an em dash for a root category with no parent', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue(
      paginaCom({ ...categoriaExemplo, name: 'Higiene Pessoal', parentId: null, parentName: null }),
    )
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const row = wrapper.find('[data-test="row-cat-1"]')
    expect(row.text()).toContain('—')
  })

  it('shows the header count and the Total/Ativas/Inativas pills', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue(paginaCom(categoriaExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('7 categorias cadastradas')
    expect(wrapper.text()).toContain('Ativas')
    expect(wrapper.text()).toContain('Inativas')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(categoriesApi.listCategories).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de categorias.')
  })

  it('reloads the list when the search field changes', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue(paginaCom())
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-search"]').setValue('Cam')
    await flushPromises()

    expect(categoriesApi.listCategories).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: 'Cam' }),
    )
  })

  it('navigates to the new-category route when the button is clicked', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue(paginaCom())
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-categoria"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('categorias-novo')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue(paginaCom(categoriaExemplo))
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-cat-1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('categorias-editar')
    expect(router.currentRoute.value.params.id).toBe('cat-1')
  })

  it('sorts by name when the column header is clicked', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue(paginaCom(categoriaExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-nome"]').trigger('click')
    await flushPromises()

    expect(categoriesApi.listCategories).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'name,asc' }),
    )
  })

  it('deletes a category after confirmation and reloads the list', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue(paginaCom(categoriaExemplo))
    vi.mocked(categoriesApi.deleteCategory).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(categoriesApi.deleteCategory).toHaveBeenCalledWith('cat-1')
  })

  it('shows the backend message when deletion is blocked because the category has subcategories', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue(paginaCom(categoriaExemplo))
    vi.mocked(categoriesApi.deleteCategory).mockRejectedValue({
      response: { data: { mensagem: 'Não é possível excluir: esta categoria possui subcategorias vinculadas' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não é possível excluir: esta categoria possui subcategorias vinculadas')
  })
})
