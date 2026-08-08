import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import TabelaPrecoFormView from '@/views/TabelaPrecoFormView.vue'
import * as tabelasPrecoApi from '@/api/tabelasPreco'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/tabelasPreco')
vi.mock('@/api/produtos')

function mountWithRouter(path = '/tabelas-preco/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/tabelas-preco', name: 'tabelas-preco', component: { template: '<div />' } },
      { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: TabelaPrecoFormView },
      { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: TabelaPrecoFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(TabelaPrecoFormView, { global: { plugins: [router] } }),
  }))
}

const produtoAtivo = { id: 'prod-1', nome: 'Camiseta Polo', sku: 'P0001', marca: '', precoVenda: 100, quantidadeEstoque: 10, status: 'ATIVO' as const }

describe('TabelaPrecoFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field errors when nome/inicioVigencia are blank on submit', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('')
    await wrapper.find('[data-test="inicio-vigencia"]').setValue('')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(tabelasPrecoApi.criarTabelaPreco).not.toHaveBeenCalled()
  })

  it('populates items from all active produtos in TODOS_PRODUTOS mode, with live-calculated prices', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    // default rule is AUTOMATICO/SOMAR/REAL/valorAjuste=0 -> preço = precoVenda
    expect(wrapper.text()).toContain('Camiseta Polo')
    const precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(100, 2)
  })

  it('recalculates every item live when the ajuste rule changes in TODOS_PRODUTOS mode', async () => {
    // Per spec §5 ("recalcula sempre que a regra muda"): TODOS_PRODUTOS items stay
    // fully rule-driven, so a manually typed price is overwritten the next time a
    // rule field changes -- this is deliberate, not a bug.
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="item-preco-0"]').setValue('250')
    await wrapper.find('[data-test="valor-ajuste"]').setValue('50')
    await flushPromises()

    const precoInput = wrapper.find('[data-test="item-preco-0"]').element as HTMLInputElement
    expect(Number(precoInput.value)).toBeCloseTo(150, 2) // produtoAtivo.precoVenda=100, SOMAR+REAL+50
  })

  it('starts empty in SELECIONAR_PRODUTOS mode and adds an item via search', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="modo-selecao"]').setValue('SELECIONAR_PRODUTOS')
    await flushPromises()
    expect(wrapper.find('.tabela-itens').exists()).toBe(false)

    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoAtivo], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    await wrapper.find('[data-test="produto-busca"]').setValue('Camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('does not auto-recalculate SELECIONAR_PRODUTOS items when the rule changes, but the reset button does', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="modo-selecao"]').setValue('SELECIONAR_PRODUTOS')
    await flushPromises()

    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
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
    expect(Number(precoInput.value)).toBeCloseTo(120, 2) // produtoAtivo.precoVenda=100, SOMAR+REAL+20
  })

  it('filters the item list by Preenchido/Pendente', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoAtivo, { ...produtoAtivo, id: 'prod-2', nome: 'Bermuda', sku: 'P0002' }],
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
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    vi.mocked(tabelasPrecoApi.criarTabelaPreco).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Varejo')
    await wrapper.find('[data-test="inicio-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(tabelasPrecoApi.criarTabelaPreco).toHaveBeenCalledWith(
      expect.objectContaining({ nome: 'Varejo', inicioVigencia: '2026-01-01' }),
    )
    expect(router.currentRoute.value.name).toBe('tabelas-preco')
  })

  it('shows a conflict message on duplicate nome (409)', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    vi.mocked(tabelasPrecoApi.criarTabelaPreco).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Varejo')
    await wrapper.find('[data-test="inicio-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma tabela de preço cadastrada com este nome')
  })

  it('loads existing tabela data in edit mode', async () => {
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 1000,
    })
    vi.mocked(tabelasPrecoApi.buscarTabelaPreco).mockResolvedValue({
      id: 'tp-1', nome: 'Varejo', modoSelecaoProdutos: 'SELECIONAR_PRODUTOS', metodoAjuste: 'MANUAL',
      operacaoAjuste: null, tipoValorAjuste: null, valorAjuste: null, arredondamento: 'NAO_ARREDONDAR',
      inicioVigencia: '2026-01-01', terminoVigencia: null, valorMinimoVenda: null, percentualComissaoPadrao: null,
      ativo: true, criadoEm: '2026-01-01T00:00:00Z',
      itens: [{ produtoId: 'prod-1', produtoNome: 'Camiseta Polo', produtoSku: 'P0001', precoCadastrado: 100, precoNestaTabela: 120, percentualComissao: 5 }],
    })

    const { wrapper } = await mountWithRouter('/tabelas-preco/tp-1/editar')
    await flushPromises()

    expect(tabelasPrecoApi.buscarTabelaPreco).toHaveBeenCalledWith('tp-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Varejo')
    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('shows an error message when loading tabela data fails in edit mode', async () => {
    vi.mocked(tabelasPrecoApi.buscarTabelaPreco).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/tabelas-preco/tp-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da tabela de preço.')
  })
})
