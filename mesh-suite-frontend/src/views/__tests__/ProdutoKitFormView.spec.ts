import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProdutoKitFormView from '@/views/ProdutoKitFormView.vue'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/produtos')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: { template: '<div />' } },
      { path: '/produtos/novo/kit', name: 'produtos-novo-kit', component: ProdutoKitFormView },
    ],
  })
  router.push('/produtos/novo/kit')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ProdutoKitFormView, { global: { plugins: [router] } }),
  }))
}

function produtoSummary(overrides: Partial<produtosApi.ProdutoSummary> = {}): produtosApi.ProdutoSummary {
  return {
    id: 'p1',
    nome: 'Camiseta Polo Masculina',
    sku: 'P0001',
    marca: 'Marca A',
    precoVenda: 89.9,
    quantidadeEstoque: 20,
    status: 'ATIVO',
    tipo: 'PRODUCT',
    ...overrides,
  }
}

describe('ProdutoKitFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field and empty-composition errors on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(wrapper.text()).toContain('Adicione ao menos um produto ao kit')
    expect(produtosApi.criarProdutoKit).not.toHaveBeenCalled()
  })

  it('searches products by reusing listarProdutos and adds one to the composition', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoSummary()],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 5,
    })
    const { wrapper } = await mountWithRouter()

    await wrapper.findAll('button').find((b) => b.text().includes('Adicionar Produto'))!.trigger('click')
    const buscarBtn = wrapper.findAll('button').find((b) => b.text() === 'Buscar')!
    await buscarBtn.trigger('click')
    await flushPromises()

    expect(produtosApi.listarProdutos).toHaveBeenCalledWith(expect.objectContaining({ size: 5 }))
    const adicionarBtn = wrapper.findAll('button').find((b) => b.text() === 'Adicionar')!
    await adicionarBtn.trigger('click')

    expect(wrapper.text()).toContain('Camiseta Polo Masculina')
    expect(wrapper.text()).toContain('R$ 89,90')
  })

  it('updates quantity and total when using the stepper', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoSummary()],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 5,
    })
    const { wrapper } = await mountWithRouter()

    await wrapper.findAll('button').find((b) => b.text().includes('Adicionar Produto'))!.trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Buscar')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === 'Adicionar')!.trigger('click')

    const incrementar = wrapper.findAll('.qtd-stepper button')[1]
    await incrementar.trigger('click')
    await incrementar.trigger('click')

    expect(wrapper.text()).toContain('R$ 269,70') // 3 * 89.90
  })

  it('removes an item from the composition', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoSummary()],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 5,
    })
    const { wrapper } = await mountWithRouter()

    await wrapper.findAll('button').find((b) => b.text().includes('Adicionar Produto'))!.trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Buscar')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === 'Adicionar')!.trigger('click')

    await wrapper.find('.item-remover').trigger('click')

    expect(wrapper.text()).toContain('Nenhum produto adicionado ao kit ainda.')
  })

  it('submits the kit payload and navigates to the list on success', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoSummary()],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 5,
    })
    vi.mocked(produtosApi.criarProdutoKit).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Kit Look Casual')
    await wrapper.find('[data-test="sku"]').setValue('KIT001')
    await wrapper.findAll('button').find((b) => b.text().includes('Adicionar Produto'))!.trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Buscar')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === 'Adicionar')!.trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(produtosApi.criarProdutoKit).toHaveBeenCalledWith(
      expect.objectContaining({
        nome: 'Kit Look Casual',
        sku: 'KIT001',
        itens: [{ produtoId: 'p1', quantidade: 1 }],
      }),
    )
    expect(router.currentRoute.value.name).toBe('produtos')
  })

  it('shows a conflict message on duplicate SKU (409), reusing the shared error mapping', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoSummary()],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 5,
    })
    vi.mocked(produtosApi.criarProdutoKit).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Kit Look Casual')
    await wrapper.find('[data-test="sku"]').setValue('KIT001')
    await wrapper.findAll('button').find((b) => b.text().includes('Adicionar Produto'))!.trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Buscar')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === 'Adicionar')!.trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um produto cadastrado com este SKU')
  })
})
