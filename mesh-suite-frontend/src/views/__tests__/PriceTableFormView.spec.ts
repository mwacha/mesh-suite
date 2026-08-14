import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PriceTableFormView from '@/views/PriceTableFormView.vue'
import * as tabelasPrecoApi from '@/api/priceTables'
import * as produtosApi from '@/api/products'

vi.mock('@/api/priceTables')
vi.mock('@/api/products')

function mountWithRouter(path = '/tabelas-preco/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/tabelas-preco', name: 'tabelas-preco', component: { template: '<div />' } },
      { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: PriceTableFormView },
      { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: PriceTableFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PriceTableFormView, { global: { plugins: [router] } }),
  }))
}

const produtoAtivo = { id: 'prod-1', name: 'Camiseta Polo', sku: 'P0001', brand: '', salePrice: 100, stockQuantity: 10, status: 'ACTIVE' as const }

describe('PriceTableFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field errors when nome/inicioVigencia are blank on submit', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('')
    await wrapper.find('[data-test="inicio-vigencia"]').setValue('')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(tabelasPrecoApi.createPriceTable).not.toHaveBeenCalled()
  })

  it('populates items from all active produtos in TODOS_PRODUTOS mode, with live-calculated prices', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    // default rule is AUTOMATIC/ADD/FIXED/adjustmentValue=0 -> preço = precoVenda
    expect(wrapper.text()).toContain('Camiseta Polo')
    const precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(100, 2)
  })

  it('recalculates every item live when the ajuste rule changes in TODOS_PRODUTOS mode', async () => {
    // Per spec §5 ("recalcula sempre que a regra muda"): TODOS_PRODUTOS items stay
    // fully rule-driven, so a manually typed price is overwritten the next time a
    // rule field changes -- this is deliberate, not a bug.
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="item-preco-0"]').setValue('250')
    await wrapper.find('[data-test="valor-ajuste"]').setValue('50')
    await flushPromises()

    const precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(150, 2) // produtoAtivo.salePrice=100, ADD+FIXED+50
  })

  it('starts empty in SELECIONAR_PRODUTOS mode and adds an item via search', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="modo-selecao"]').setValue('SELECT_PRODUCTS')
    await flushPromises()
    expect(wrapper.find('.tabela-itens').exists()).toBe(false)

    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    await wrapper.find('[data-test="produto-busca"]').setValue('Camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('does not auto-recalculate SELECIONAR_PRODUTOS items when the rule changes, but the reset button does', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="modo-selecao"]').setValue('SELECT_PRODUCTS')
    await flushPromises()

    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    await wrapper.find('[data-test="produto-busca"]').setValue('Camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="item-preco-0"]').setValue('999')
    await wrapper.find('[data-test="valor-ajuste"]').setValue('20')
    await flushPromises()

    let precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(999, 2)

    await wrapper.find('[data-test="item-reset-0"]').trigger('click')
    await flushPromises()

    precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(120, 2) // produtoAtivo.salePrice=100, ADD+FIXED+20
  })

  it('filters the item list by Preenchido/Pendente', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [produtoAtivo, { ...produtoAtivo, id: 'prod-2', name: 'Bermuda', sku: 'P0002' }],
      totalElements: 2, totalPages: 1, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    // Switching to MANUAL clears every item's price to Pendente (null) via the
    // same rule-change watcher, since TODOS_PRODUTOS items are always rule-driven.
    await wrapper.find('[data-test="metodo-manual"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="item-preco-0"]').setValue('120')
    await flushPromises()

    await wrapper.find('[data-test="filtro-preenchimento"]').setValue('PENDENTE')
    await flushPromises()
    expect(wrapper.text()).toContain('Bermuda')
    expect(wrapper.text()).not.toContain('Camiseta Polo')

    await wrapper.find('[data-test="filtro-preenchimento"]').setValue('PREENCHIDO')
    await flushPromises()
    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).not.toContain('Bermuda')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    vi.mocked(tabelasPrecoApi.createPriceTable).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Varejo')
    await wrapper.find('[data-test="inicio-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(tabelasPrecoApi.createPriceTable).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Varejo', effectiveStartDate: '2026-01-01' }),
    )
    expect(router.currentRoute.value.name).toBe('tabelas-preco')
  })

  it('shows a conflict message on duplicate nome (409)', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    vi.mocked(tabelasPrecoApi.createPriceTable).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Varejo')
    await wrapper.find('[data-test="inicio-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma tabela de preço cadastrada com este nome')
  })

  it('loads existing tabela data in edit mode', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    vi.mocked(tabelasPrecoApi.getPriceTable).mockResolvedValue({
      id: 'tp-1', name: 'Varejo', productSelectionMode: 'SELECT_PRODUCTS', adjustmentMethod: 'MANUAL',
      adjustmentOperation: null, adjustmentValueType: null, adjustmentValue: null, rounding: 'NO_ROUNDING',
      effectiveStartDate: '2026-01-01', effectiveEndDate: null, minSalePrice: null, defaultCommissionPercentage: null,
      active: true, createdAt: '2026-01-01T00:00:00Z',
      items: [{ productId: 'prod-1', productName: 'Camiseta Polo', productSku: 'P0001', registeredPrice: 100, tablePrice: 120, commissionPercentage: 5 }],
    })

    const { wrapper } = await mountWithRouter('/tabelas-preco/tp-1/editar')
    await flushPromises()

    expect(tabelasPrecoApi.getPriceTable).toHaveBeenCalledWith('tp-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Varejo')
    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('shows an error message when loading tabela data fails in edit mode', async () => {
    vi.mocked(tabelasPrecoApi.getPriceTable).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/tabelas-preco/tp-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da tabela de preço.')
  })
})
