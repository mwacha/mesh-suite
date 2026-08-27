import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CategoryFormView from '@/views/CategoryFormView.vue'
import * as categoriesApi from '@/api/categories'

vi.mock('@/api/categories', async (importOriginal) => {
  const original = await importOriginal<typeof categoriesApi>()
  return {
    ...original,
    getCategory: vi.fn(),
    createCategory: vi.fn(),
    updateCategory: vi.fn(),
    listCategories: vi.fn(),
  }
})

function mountWithRouter(path = '/categorias/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/categorias', name: 'categorias', component: { template: '<div />' } },
      { path: '/categorias/novo', name: 'categorias-novo', component: CategoryFormView },
      { path: '/categorias/:id/editar', name: 'categorias-editar', component: CategoryFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(CategoryFormView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

describe('CategoryFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(categoriesApi.listCategories).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
    })
  })

  it('shows a required-field error when nome is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(categoriesApi.createCategory).not.toHaveBeenCalled()
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(categoriesApi.createCategory).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camisas')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(categoriesApi.createCategory).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Camisas', active: true, parentId: null }),
    )
    expect(router.currentRoute.value.name).toBe('categorias')
  })

  it('shows a conflict message on duplicate nome (409)', async () => {
    vi.mocked(categoriesApi.createCategory).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camisas')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma categoria cadastrada com este nome')
  })

  it('shows the backend message when the parent category is invalid (400)', async () => {
    vi.mocked(categoriesApi.createCategory).mockRejectedValue({
      response: { status: 400, data: { mensagem: 'A categoria selecionada como pai já possui uma categoria pai; escolha uma categoria raiz' } },
    })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Maquiagem')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('A categoria selecionada como pai já possui uma categoria pai; escolha uma categoria raiz')
  })

  it('toggles between Ativo and Inativo status', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="status-INATIVO"]').trigger('click')
    vi.mocked(categoriesApi.createCategory).mockResolvedValue({} as any)
    await wrapper.find('[data-test="nome"]').setValue('Camisas')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(categoriesApi.createCategory).toHaveBeenCalledWith(
      expect.objectContaining({ active: false }),
    )
  })

  it('searches root categories for the Categoria Pai field and selects one', async () => {
    vi.mocked(categoriesApi.listCategories).mockResolvedValue({
      content: [
        { id: 'cat-0', name: 'Higiene Pessoal', description: null, active: true, parentId: null, parentName: null, linkedProducts: 0, createdAt: '2026-01-01T00:00:00Z' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 20,
    })
    vi.mocked(categoriesApi.createCategory).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="categoria-pai"]').trigger('click')
    await flushPromises()

    expect(categoriesApi.listCategories).toHaveBeenCalledWith(
      expect.objectContaining({ raiz: true }),
    )

    await wrapper.find('[data-test="categoria-pai-option-cat-0"]').trigger('click')
    await wrapper.find('[data-test="nome"]').setValue('Beleza')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(categoriesApi.createCategory).toHaveBeenCalledWith(
      expect.objectContaining({ parentId: 'cat-0' }),
    )
  })

  it('loads existing categoria data in edit mode, including its parent', async () => {
    vi.mocked(categoriesApi.getCategory).mockResolvedValue({
      id: 'cat-1', name: 'Beleza', description: 'Descrição', active: true,
      parentId: 'cat-0', parentName: 'Higiene Pessoal', linkedProducts: 2, createdAt: '2026-01-01T00:00:00Z',
    })

    const { wrapper } = await mountWithRouter('/categorias/cat-1/editar')
    await flushPromises()

    expect(categoriesApi.getCategory).toHaveBeenCalledWith('cat-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Beleza')
    expect(wrapper.text()).toContain('Higiene Pessoal')
  })

  it('shows an error message when loading categoria data fails in edit mode', async () => {
    vi.mocked(categoriesApi.getCategory).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/categorias/cat-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da categoria.')
  })
})
