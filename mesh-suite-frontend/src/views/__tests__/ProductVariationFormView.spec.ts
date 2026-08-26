import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProductVariationFormView from '@/views/ProductVariationFormView.vue'
import * as variationsApi from '@/api/productVariations'
import * as categoriesApi from '@/api/categories'
import * as colorwaysApi from '@/api/colorways'

vi.mock('@/api/productVariations')
vi.mock('@/api/categories')
vi.mock('@/api/colorways')

function mountWithRouter(path = '/produtos/novo/variacao') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: { template: '<div />' } },
      { path: '/produtos/novo', name: 'produtos-novo', component: { template: '<div />' } },
      { path: '/produtos/novo/kit', name: 'produtos-novo-kit', component: { template: '<div />' } },
      { path: '/produtos/novo/variacao', name: 'produtos-novo-variacao', component: ProductVariationFormView },
      { path: '/produtos/:id/editar/variacao', name: 'produtos-editar-variacao', component: ProductVariationFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ProductVariationFormView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

async function adicionarVariante(wrapper: Awaited<ReturnType<typeof mountWithRouter>>['wrapper'], sku: string, preco: string) {
  await wrapper.find('[data-test="adicionar-variante"]').trigger('click')
  await flushPromises()
  await wrapper.find('[data-test="variante-sku"]').setValue(sku)
  await wrapper.find('[data-test="variante-preco-venda"]').setValue(preco)
  await wrapper.find('[data-test="variante-salvar"]').trigger('click')
  await flushPromises()
}

async function adicionarTipoDeVariacao(
  wrapper: Awaited<ReturnType<typeof mountWithRouter>>['wrapper'],
  nome: string,
  valores: string[],
) {
  await wrapper.find('[data-test="adicionar-tipo-variacao"]').trigger('click')
  await flushPromises()
  await wrapper.find('[data-test="var-novo-tipo-nome"]').setValue(nome)
  for (const valor of valores) {
    await wrapper.find('[data-test="var-novo-tipo-valor-input"]').setValue(valor)
    await wrapper.find('[data-test="var-novo-tipo-valor-confirmar"]').trigger('click')
  }
  await wrapper.find('[data-test="var-novo-tipo-confirmar"]').trigger('click')
  await flushPromises()
}

describe('ProductVariationFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(categoriesApi.listCategories).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 })
    vi.mocked(colorwaysApi.listColorways).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 })
  })

  it('shows required-field errors when nome/sku/preço/children are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(wrapper.text()).toContain('Adicione ao menos uma variante')
    expect(variationsApi.createVariation).not.toHaveBeenCalled()
  })

  it('adds a variante via the slide-over panel', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await adicionarVariante(wrapper, 'V0001-P', '79.90')

    expect(wrapper.text()).toContain('V0001-P')
  })

  it('validates required fields inside the variante panel before saving', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="adicionar-variante"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="variante-salvar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(wrapper.text()).toContain('Informe um preço maior que zero')
  })

  it('edits an existing variante row', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await adicionarVariante(wrapper, 'V0001-P', '79.90')
    await wrapper.find('[data-test="variante-editar-0"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="variante-preco-venda"]').setValue('84.90')
    await wrapper.find('[data-test="variante-salvar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('R$ 84,90')
  })

  it('submits the form with parent fields and children, navigating to the list', async () => {
    vi.mocked(variationsApi.createVariation).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('V0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('89.90')
    await adicionarVariante(wrapper, 'V0001-P', '79.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(variationsApi.createVariation).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Camiseta Polo',
        sku: 'V0001',
        salePrice: 89.9,
        children: [expect.objectContaining({ sku: 'V0001-P', salePrice: 79.9 })],
      }),
    )
    expect(router.currentRoute.value.name).toBe('produtos')
  })

  it('loads existing variação data in edit mode', async () => {
    vi.mocked(variationsApi.getVariation).mockResolvedValue({
      id: 'v-1', name: 'Camiseta Polo', sku: 'V0001', brand: 'Marca Alpha', categoryId: null, categoryName: null,
      salePrice: 89.9, status: 'ACTIVE', description: '', measurementUnit: 'UN',
      children: [{ id: 'c-1', sku: 'V0001-P', barcode: null, salePrice: 79.9, costPrice: null, stockQuantity: 5, minStock: null, maxStock: null, size: 'P', colorwayId: null, colorwayName: null }],
    })

    const { wrapper } = await mountWithRouter('/produtos/v-1/editar/variacao')
    await flushPromises()

    expect(variationsApi.getVariation).toHaveBeenCalledWith('v-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Camiseta Polo')
    expect(wrapper.text()).toContain('V0001-P')
  })

  it('shows an error message when loading variação data fails in edit mode', async () => {
    vi.mocked(variationsApi.getVariation).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/produtos/v-1/editar/variacao')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do produto.')
  })

  it('generates a row per value when a single Tipo de Variação is defined', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="sku"]').setValue('V0001')
    await adicionarTipoDeVariacao(wrapper, 'Tamanho', ['P', 'M'])

    expect(wrapper.text()).toContain('Variantes Geradas (2)')
    expect(wrapper.text()).toContain('V0001-P')
    expect(wrapper.text()).toContain('V0001-M')
  })

  it('generates the cartesian product across two Tipos de Variação', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="sku"]').setValue('V0001')
    await adicionarTipoDeVariacao(wrapper, 'Tamanho', ['P', 'M'])
    await adicionarTipoDeVariacao(wrapper, 'Cor', ['Branco', 'Vermelho'])

    expect(wrapper.text()).toContain('Variantes Geradas (4)')
    expect(wrapper.text()).toContain('V0001-P-BRANCO')
    expect(wrapper.text()).toContain('V0001-M-VERMELHO')
  })

  it('maps the Tamanho type onto the generated child\'s size field', async () => {
    vi.mocked(variationsApi.createVariation).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('V0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('89.90')
    await adicionarTipoDeVariacao(wrapper, 'Tamanho', ['P'])
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(variationsApi.createVariation).toHaveBeenCalledWith(
      expect.objectContaining({
        children: [expect.objectContaining({ sku: 'V0001-P', size: 'P' })],
      }),
    )
  })

  it('removes the generated rows when a value is removed from its type', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await adicionarTipoDeVariacao(wrapper, 'Tamanho', ['P', 'M'])
    expect(wrapper.text()).toContain('Variantes Geradas (2)')

    await wrapper.find('[data-test="var-tipo-remover-0"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Variantes Geradas (0)')
  })

  it('does not affect a manually-added variante when the matrix changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await adicionarVariante(wrapper, 'MANUAL-001', '50.00')
    await adicionarTipoDeVariacao(wrapper, 'Tamanho', ['P'])

    expect(wrapper.text()).toContain('Variantes Geradas (2)')
    expect(wrapper.text()).toContain('MANUAL-001')

    await wrapper.find('[data-test="var-tipo-remover-0"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Variantes Geradas (1)')
    expect(wrapper.text()).toContain('MANUAL-001')
  })

  it('navigates to the Kit form when the Tipo de Produto switcher picks Kit', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="tipo-produto-PRODUCT_KIT"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('produtos-novo-kit')
  })
})
