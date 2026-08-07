import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProdutosListView from '@/views/ProdutosListView.vue'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/produtos')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: ProdutosListView },
      { path: '/produtos/novo', name: 'produtos-novo', component: { template: '<div />' } },
      { path: '/produtos/:id/editar', name: 'produtos-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/produtos')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(ProdutosListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const produtoBase = {
  id: 'p1', nome: 'Camiseta Polo', sku: 'P0001', marca: 'Marca Alpha',
  precoVenda: 59.9, quantidadeEstoque: 10, status: 'ATIVO' as const,
}

describe('ProdutosListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(produtosApi.buscarResumoProdutos).mockResolvedValue({ total: 1, ativos: 1, inativos: 0 })
  })

  it('loads and displays the product list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).toContain('1 produtos cadastrados')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('camiseta')
    await flushPromises()

    expect(produtosApi.listarProdutos).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'camiseta' }))
  })

  it('navigates to the create form when "+ Novo Produto" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-produto"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-novo')
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-p1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('toggles a product status via the Ações menu', async () => {
    vi.mocked(produtosApi.atualizarStatusProduto).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-status"]').trigger('click')
    await flushPromises()

    expect(produtosApi.atualizarStatusProduto).toHaveBeenCalledWith('p1', 'INATIVO')
  })

  it('re-fetches with the sort param when a sortable column header is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-nome"]').trigger('click')
    await flushPromises()

    expect(produtosApi.listarProdutos).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'nome,asc' }))
  })

  it('shows an error message when loading the product list fails', async () => {
    vi.mocked(produtosApi.listarProdutos).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de produtos.')
  })
})
