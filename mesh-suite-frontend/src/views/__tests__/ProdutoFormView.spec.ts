import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProdutoFormView from '@/views/ProdutoFormView.vue'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/produtos')

function mountWithRouter(path = '/produtos/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: { template: '<div />' } },
      { path: '/produtos/novo', name: 'produtos-novo', component: ProdutoFormView },
      { path: '/produtos/:id/editar', name: 'produtos-editar', component: ProdutoFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ProdutoFormView, { global: { plugins: [router] } }),
  }))
}

describe('ProdutoFormView', () => {
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
    expect(produtosApi.criarProduto).not.toHaveBeenCalled()
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
    vi.mocked(produtosApi.criarProduto).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(produtosApi.criarProduto).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('produtos')
  })

  it('sends null (not empty string) for blank optional numeric fields', async () => {
    vi.mocked(produtosApi.criarProduto).mockResolvedValue({} as any)
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

    const payload = vi.mocked(produtosApi.criarProduto).mock.calls[0][0]
    expect(payload.precoCusto).toBeNull()
    expect(payload.estoqueMinimo).toBeNull()
    expect(payload.peso).toBeNull()
  })

  it('shows a conflict message on duplicate SKU (409)', async () => {
    vi.mocked(produtosApi.criarProduto).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um produto cadastrado com este SKU')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(produtosApi.criarProduto).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue('59.90')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing produto data in edit mode', async () => {
    vi.mocked(produtosApi.buscarProduto).mockResolvedValue({
      id: 'abc-123', nome: 'Camiseta Polo', sku: 'P0001', codigoBarras: '', marca: '', categoria: '',
      precoVenda: 59.9, precoCusto: null, status: 'ATIVO', descricao: '', quantidadeEstoque: 10,
      unidadeMedida: 'UN', estoqueMinimo: null, estoqueMaximo: null, peso: null, comprimento: null,
      largura: null, altura: null,
    } as any)

    const { wrapper } = await mountWithRouter('/produtos/abc-123/editar')
    await flushPromises()

    expect(produtosApi.buscarProduto).toHaveBeenCalledWith('abc-123')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Camiseta Polo')
  })

  it('shows an error message when loading produto data fails in edit mode', async () => {
    vi.mocked(produtosApi.buscarProduto).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/produtos/abc-123/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do produto.')
  })
})
