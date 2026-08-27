import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProductsListView from '@/views/ProductsListView.vue'
import * as produtosApi from '@/api/products'

vi.mock('@/api/products')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: ProductsListView },
      { path: '/produtos/novo', name: 'produtos-novo', component: { template: '<div />' } },
      { path: '/produtos/:id/editar', name: 'produtos-editar', component: { template: '<div />' } },
      { path: '/produtos/:id/editar/kit', name: 'produtos-editar-kit', component: { template: '<div />' } },
      { path: '/produtos/:id/editar/variacao', name: 'produtos-editar-variacao', component: { template: '<div />' } },
    ],
  })
  router.push('/produtos')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(ProductsListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const simples = {
  id: 'p1', name: 'Camiseta Polo', sku: 'P0001', brandName: 'Marca Alpha', type: 'PRODUCT' as const,
  salePrice: 59.9, stockQuantity: 10, status: 'ACTIVE' as const, children: [],
}
const kit = {
  id: 'k1', name: 'Kit Combo', sku: 'KIT001', brandName: null, type: 'PRODUCT_KIT' as const,
  salePrice: 99.9, stockQuantity: 0, status: 'ACTIVE' as const, children: [],
}
const variacaoPai = {
  id: 'v1', name: 'Camiseta com Variação', sku: 'V0001', brandName: 'Marca Alpha', type: 'VARIATION_PARENT' as const,
  salePrice: 79.9, stockQuantity: 0, status: 'ACTIVE' as const,
  children: [{ id: 'v1-p', name: 'Camiseta com Variação', sku: 'V0001-P', salePrice: 79.9, stockQuantity: 5 }],
}

describe('ProductsListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(produtosApi.listAllProducts).mockResolvedValue({
      content: [simples, kit, variacaoPai], totalElements: 3, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(produtosApi.getAllProductsSummary).mockResolvedValue({ total: 3, active: 3, inactive: 0 })
  })

  it('loads and displays the unified product list with a Tipo column per row', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).toContain('Kit Combo')
    expect(wrapper.text()).toContain('Simples')
    expect(wrapper.text()).toContain('Kit')
    expect(wrapper.text()).toContain('Variação')
    expect(wrapper.text()).toContain('3 produtos cadastrados')
  })

  it('shows Total/Ativos/Inativos totalizers from the unified summary endpoint', async () => {
    vi.mocked(produtosApi.getAllProductsSummary).mockResolvedValue({ total: 12, active: 9, inactive: 3 })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('Ativos')
    expect(wrapper.text()).toContain('9')
    expect(wrapper.text()).toContain('Inativos')
    expect(wrapper.text()).toContain('3')
  })

  it('re-fetches with the search term when the filter bar search changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-search"]').setValue('camiseta')
    await flushPromises()

    expect(produtosApi.listAllProducts).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'camiseta' }))
  })

  it('navigates to the create form when "+ Novo Produto" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-produto"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-novo')
  })

  it('routes Editar to the Simples edit form for a type=PRODUCT row', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('routes Editar to the Kit edit form for a type=PRODUCT_KIT row', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-k1"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-editar-kit')
    expect(router.currentRoute.value.params.id).toBe('k1')
  })

  it('routes Editar to the Variação edit form for a type=VARIATION_PARENT row', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-v1"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-editar-variacao')
    expect(router.currentRoute.value.params.id).toBe('v1')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-p1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('does not navigate when the expand toggle is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="expandir-v1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos')
  })

  it('does not navigate when the Ações menu is opened from the row', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos')
  })

  it('navigates to the parent Variação edit form when a child row is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="expandir-v1"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="row-filho-v1-p"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-editar-variacao')
    expect(router.currentRoute.value.params.id).toBe('v1')
  })

  it('toggles a product status via the Ações menu', async () => {
    vi.mocked(produtosApi.updateProductStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-status"]').trigger('click')
    await flushPromises()

    expect(produtosApi.updateProductStatus).toHaveBeenCalledWith('p1', 'INACTIVE')
  })

  it('deletes a product via the Ações menu after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(produtosApi.deleteProduct).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(produtosApi.deleteProduct).toHaveBeenCalledWith('p1')
  })

  it('expands a Variação parent row to reveal its children', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.find('[data-test="row-filho-v1-p"]').exists()).toBe(false)

    await wrapper.find('[data-test="expandir-v1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="row-filho-v1-p"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('V0001-P')
  })

  it('routes a child row\'s Editar action to the parent Variação edit form', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="expandir-v1"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-v1-p"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-editar-variacao')
    expect(router.currentRoute.value.params.id).toBe('v1')
  })

  it('re-fetches with the sort param when a sortable column header is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-nome"]').trigger('click')
    await flushPromises()

    expect(produtosApi.listAllProducts).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'name,asc' }))
  })

  it('shows an error message when loading the product list fails', async () => {
    vi.mocked(produtosApi.listAllProducts).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de produtos.')
  })
})
