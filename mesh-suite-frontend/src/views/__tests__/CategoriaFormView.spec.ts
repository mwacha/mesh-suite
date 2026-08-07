import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CategoriaFormView from '@/views/CategoriaFormView.vue'
import * as categoriasApi from '@/api/categorias'

vi.mock('@/api/categorias')

function mountWithRouter(path = '/categorias/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/categorias', name: 'categorias', component: { template: '<div />' } },
      { path: '/categorias/novo', name: 'categorias-novo', component: CategoriaFormView },
      { path: '/categorias/:id/editar', name: 'categorias-editar', component: CategoriaFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(CategoriaFormView, { global: { plugins: [router] } }),
  }))
}

describe('CategoriaFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows a required-field error when nome is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(categoriasApi.criarCategoria).not.toHaveBeenCalled()
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(categoriasApi.criarCategoria).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camisas')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(categoriasApi.criarCategoria).toHaveBeenCalledWith(
      expect.objectContaining({ nome: 'Camisas', ativo: true }),
    )
    expect(router.currentRoute.value.name).toBe('categorias')
  })

  it('shows a conflict message on duplicate nome (409)', async () => {
    vi.mocked(categoriasApi.criarCategoria).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camisas')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma categoria cadastrada com este nome')
  })

  it('toggles between Ativo and Inativo status', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="status-inativo"]').trigger('click')
    vi.mocked(categoriasApi.criarCategoria).mockResolvedValue({} as any)
    await wrapper.find('[data-test="nome"]').setValue('Camisas')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(categoriasApi.criarCategoria).toHaveBeenCalledWith(
      expect.objectContaining({ ativo: false }),
    )
  })

  it('loads existing categoria data in edit mode', async () => {
    vi.mocked(categoriasApi.buscarCategoria).mockResolvedValue({
      id: 'cat-1', nome: 'Camisas', descricao: 'Descrição', ativo: true,
      produtosVinculados: 2, criadoEm: '2026-01-01T00:00:00Z',
    })

    const { wrapper } = await mountWithRouter('/categorias/cat-1/editar')
    await flushPromises()

    expect(categoriasApi.buscarCategoria).toHaveBeenCalledWith('cat-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Camisas')
  })

  it('shows an error message when loading categoria data fails in edit mode', async () => {
    vi.mocked(categoriasApi.buscarCategoria).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/categorias/cat-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da categoria.')
  })
})
