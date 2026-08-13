import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProductFormView from '@/views/ProductFormView.vue'
import * as produtosApi from '@/api/products'

vi.mock('@/api/products')
vi.mock('@/api/categories')
vi.mock('@/api/colorways')

function mountWithRouter(path = '/produtos/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: { template: '<div />' } },
      { path: '/produtos/novo', name: 'produtos-novo', component: ProductFormView },
      { path: '/produtos/:id/editar', name: 'produtos-editar', component: ProductFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ProductFormView, { global: { plugins: [router] } }),
  }))
}

describe('ProductFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // Mocks otherwise persist mock.calls across tests in this file, so a later
    // test's `mock.calls[0]` can silently pick up an earlier test's call
    // (e.g. the "submits successfully" test) instead of its own.
    vi.clearAllMocks()
  })

  it('shows required-field errors when nome/sku are blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(produtosApi.createProduct).not.toHaveBeenCalled()
  })

  it('requires a preço de venda greater than zero', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Informe um preço maior que zero')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(produtosApi.createProduct).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(produtosApi.createProduct).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('produtos')
  })

  it('sends null (not empty string) for blank optional numeric fields', async () => {
    vi.mocked(produtosApi.createProduct).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    // Simulate a user typing into the optional "Preço de Custo" field and then
    // clearing it. With v-model.number, clearing a numeric input drives the
    // underlying form value to '' (empty string), not null -- this is the exact
    // state that must be normalized by paraPayload()/numeroOuNull() before the
    // request is sent, or the backend's BigDecimal deserialization 400s.
    await wrapper.find('[data-test="preco-custo"]').setValue('123.45')
    await wrapper.find('[data-test="preco-custo"]').setValue('')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(produtosApi.createProduct).mock.calls[0][0]
    expect(payload.costPrice).toBeNull()
    expect(payload.minStock).toBeNull()
    expect(payload.weight).toBeNull()
  })

  it('shows a conflict message on duplicate SKU (409)', async () => {
    vi.mocked(produtosApi.createProduct).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um produto cadastrado com este SKU')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(produtosApi.createProduct).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing produto data in edit mode', async () => {
    vi.mocked(produtosApi.getProduct).mockResolvedValue({
      id: 'abc-123', name: 'Camiseta Polo', sku: 'P0001', barcode: '', brand: '', categoryId: null,
      categoryName: null, colorwayId: null, colorwayName: null, salePrice: 59.9, costPrice: null,
      status: 'ACTIVE', description: '', stockQuantity: 10, measurementUnit: 'UN', minStock: null,
      maxStock: null, weight: null, length: null, width: null, height: null,
    } as any)

    const { wrapper } = await mountWithRouter('/produtos/abc-123/editar')
    await flushPromises()

    expect(produtosApi.getProduct).toHaveBeenCalledWith('abc-123')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Camiseta Polo')
  })

  it('shows an error message when loading produto data fails in edit mode', async () => {
    vi.mocked(produtosApi.getProduct).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/produtos/abc-123/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do produto.')
  })

  it('loads categorias into the dropdown and lets the user pick one', async () => {
    const categoriasApi = await import('@/api/categories')
    vi.mocked(categoriasApi.listCategories).mockResolvedValue({
      content: [
        { id: 'cat-1', name: 'Camisas', description: null, active: true, linkedProducts: 0, createdAt: '2026-01-01T00:00:00Z' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 100,
    })
    vi.mocked(produtosApi.createProduct).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('[data-test="categoria"]').setValue('cat-1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(produtosApi.createProduct).mock.calls[0][0]
    expect(payload.categoryId).toBe('cat-1')
  })

  it('keeps an inactive-but-linked categoria selected in the dropdown when editing', async () => {
    const categoriasApi = await import('@/api/categories')
    // The active-only categoria list does NOT include this produto's categoria
    // (simulating it having been deactivated after the produto was linked to it).
    vi.mocked(categoriasApi.listCategories).mockResolvedValue({
      content: [
        { id: 'cat-active', name: 'Camisas', description: null, active: true, linkedProducts: 0, createdAt: '2026-01-01T00:00:00Z' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 100,
    })
    vi.mocked(produtosApi.getProduct).mockResolvedValue({
      id: 'abc-123', name: 'Camiseta Polo', sku: 'P0001', barcode: '', brand: '',
      categoryId: 'cat-inactive', categoryName: 'Descontinuados', colorwayId: null, colorwayName: null,
      salePrice: 59.9, costPrice: null, status: 'ACTIVE', description: '', stockQuantity: 10,
      measurementUnit: 'UN', minStock: null, maxStock: null, weight: null, length: null,
      width: null, height: null,
    } as any)

    const { wrapper } = await mountWithRouter('/produtos/abc-123/editar')
    await flushPromises()

    const select = wrapper.find('[data-test="categoria"]').element as HTMLSelectElement
    expect(select.value).toBe('cat-inactive')
    expect(wrapper.text()).toContain('Descontinuados')
  })

  it('loads cores/estampas into the dropdown and lets the user pick one', async () => {
    const coresEstampasApi = await import('@/api/colorways')
    vi.mocked(coresEstampasApi.listColorways).mockResolvedValue({
      content: [
        { id: 'ce-1', name: 'Azul Marinho', effectiveDate: '2026-01-01', description: null, active: true, linkedProducts: 0, createdAt: '2026-01-01T00:00:00Z' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 100,
    })
    vi.mocked(produtosApi.createProduct).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('[data-test="cor-estampa"]').setValue('ce-1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(produtosApi.createProduct).mock.calls[0][0]
    expect(payload.colorwayId).toBe('ce-1')
  })

  it('keeps an inactive-but-linked cor/estampa selected in the dropdown when editing', async () => {
    const coresEstampasApi = await import('@/api/colorways')
    // The active-only list does NOT include this produto's cor/estampa
    // (simulating it having been deactivated after the produto was linked to it).
    vi.mocked(coresEstampasApi.listColorways).mockResolvedValue({
      content: [
        { id: 'ce-active', name: 'Preto', effectiveDate: '2026-01-01', description: null, active: true, linkedProducts: 0, createdAt: '2026-01-01T00:00:00Z' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 100,
    })
    vi.mocked(produtosApi.getProduct).mockResolvedValue({
      id: 'abc-123', name: 'Camiseta Polo', sku: 'P0001', barcode: '', brand: '',
      categoryId: null, categoryName: null,
      colorwayId: 'ce-inactive', colorwayName: 'Floral Descontinuado',
      salePrice: 59.9, costPrice: null, status: 'ACTIVE', description: '', stockQuantity: 10,
      measurementUnit: 'UN', minStock: null, maxStock: null, weight: null, length: null,
      width: null, height: null,
    } as any)

    const { wrapper } = await mountWithRouter('/produtos/abc-123/editar')
    await flushPromises()

    const select = wrapper.find('[data-test="cor-estampa"]').element as HTMLSelectElement
    expect(select.value).toBe('ce-inactive')
    expect(wrapper.text()).toContain('Floral Descontinuado')
  })
})
