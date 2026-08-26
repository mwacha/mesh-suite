import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProductKitFormView from '@/views/ProductKitFormView.vue'
import * as kitsApi from '@/api/productKits'
import * as produtosApi from '@/api/products'

vi.mock('@/api/productKits')
vi.mock('@/api/products')

function mountWithRouter(path = '/produtos/novo/kit') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: { template: '<div />' } },
      { path: '/produtos/novo', name: 'produtos-novo', component: { template: '<div />' } },
      { path: '/produtos/novo/kit', name: 'produtos-novo-kit', component: ProductKitFormView },
      { path: '/produtos/novo/variacao', name: 'produtos-novo-variacao', component: { template: '<div />' } },
      { path: '/produtos/:id/editar/kit', name: 'produtos-editar-kit', component: ProductKitFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ProductKitFormView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const componente = { id: 'prod-1', name: 'Camiseta Polo', sku: 'P0001', brand: '', salePrice: 89.9, stockQuantity: 10, status: 'ACTIVE' as const }

async function adicionarComponenteViaModal(wrapper: Awaited<ReturnType<typeof mountWithRouter>>['wrapper']) {
  await wrapper.find('[data-test="adicionar-itens"]').trigger('click')
  await flushPromises()
  await wrapper.find(`[data-test="modal-adicionar-${componente.id}"]`).trigger('click')
  await flushPromises()
  await wrapper.find('[data-test="slide-over-close"]').trigger('click')
  await flushPromises()
}

describe('ProductKitFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [componente], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('shows required-field errors when nome/sku/items are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(wrapper.text()).toContain('Adicione ao menos um produto ao kit')
    expect(kitsApi.createKit).not.toHaveBeenCalled()
  })

  it('adds a component via the modal and computes the total kit price', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await adicionarComponenteViaModal(wrapper)

    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.find('[data-test="valor-kit"]').text()).toContain('89,90')
  })

  it('recalculates the total when the item quantity changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await adicionarComponenteViaModal(wrapper)
    await wrapper.find('[data-test="item-qtd-0"]').setValue('3')
    await flushPromises()

    expect(wrapper.find('[data-test="valor-kit"]').text()).toContain('269,70')
  })

  it('removes an item and recalculates the total', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await adicionarComponenteViaModal(wrapper)
    expect(wrapper.text()).toContain('Camiseta Polo')

    await wrapper.find('[data-test="item-remover-0"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Camiseta Polo')
    expect(wrapper.find('[data-test="valor-kit"]').text()).toContain('0,00')
  })

  it('submits the form with the composed items and navigates to the list', async () => {
    vi.mocked(kitsApi.createKit).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Kit Combo')
    await wrapper.find('[data-test="sku"]').setValue('KIT001')
    await adicionarComponenteViaModal(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(kitsApi.createKit).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Kit Combo',
        sku: 'KIT001',
        items: [{ componentProductId: 'prod-1', quantity: 1 }],
      }),
    )
    expect(router.currentRoute.value.name).toBe('produtos')
  })

  it('loads existing kit data in edit mode', async () => {
    vi.mocked(kitsApi.getKit).mockResolvedValue({
      id: 'kit-1', name: 'Kit Combo', sku: 'KIT001', barcode: null, measurementUnit: 'UN',
      status: 'ACTIVE', description: '',
      items: [{ componentProductId: 'prod-1', componentName: 'Camiseta Polo', componentSku: 'P0001', quantity: 2, unitPrice: 89.9, totalPrice: 179.8 }],
      totalPrice: 179.8,
    })

    const { wrapper } = await mountWithRouter('/produtos/kit-1/editar/kit')
    await flushPromises()

    expect(kitsApi.getKit).toHaveBeenCalledWith('kit-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Kit Combo')
    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('shows an error message when loading kit data fails in edit mode', async () => {
    vi.mocked(kitsApi.getKit).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/produtos/kit-1/editar/kit')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do kit.')
  })

  it('navigates to the Simples form when the Tipo de Produto switcher picks Simples', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="tipo-produto-PRODUCT"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-novo')
  })
})
