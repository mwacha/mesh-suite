import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ProdutoVariacaoFormView from '@/views/ProdutoVariacaoFormView.vue'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/produtos')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/produtos', name: 'produtos', component: { template: '<div />' } },
      { path: '/produtos/novo/variacao', name: 'produtos-novo-variacao', component: ProdutoVariacaoFormView },
    ],
  })
  router.push('/produtos/novo/variacao')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ProdutoVariacaoFormView, { global: { plugins: [router] } }),
  }))
}

async function adicionarTipo(wrapper: any, nome: string) {
  await wrapper.findAll('button').find((b: any) => b.text().includes('Adicionar Tipo de Variação'))!.trigger('click')
  await wrapper.find('.novo-tipo input').setValue(nome)
  await wrapper.findAll('button').find((b: any) => b.text() === 'Confirmar Tipo')!.trigger('click')
}

// "+ Valor" appears once per existing tipo; the most recently added tipo is
// always the last one rendered, so its button is the last match in the DOM.
async function adicionarValor(wrapper: any, valor: string) {
  const botoesValor = wrapper.findAll('button').filter((b: any) => b.text() === '+ Valor')
  await botoesValor[botoesValor.length - 1].trigger('click')
  await wrapper.find('.chip-input').setValue(valor)
  await wrapper.findAll('.chip-acao').find((b: any) => b.text() === '✓')!.trigger('click')
}

describe('ProdutoVariacaoFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field and missing-type errors on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(wrapper.text()).toContain('Adicione ao menos um tipo de variação com valores')
    expect(produtosApi.criarProdutoVariacao).not.toHaveBeenCalled()
  })

  it('generates the cartesian product of variation types as the combinations table', async () => {
    const { wrapper } = await mountWithRouter()

    await adicionarTipo(wrapper, 'Tamanho')
    await adicionarValor(wrapper, 'P')
    await adicionarValor(wrapper, 'M')

    await adicionarTipo(wrapper, 'Cor')
    await adicionarValor(wrapper, 'Branco')

    expect(wrapper.text()).toContain('2 combinações')
    const linhas = wrapper.findAll('.tabela tbody tr')
    expect(linhas).toHaveLength(2)
    expect(linhas[0].text()).toContain('P')
    expect(linhas[0].text()).toContain('Branco')
    expect(linhas[1].text()).toContain('M')
    expect(linhas[1].text()).toContain('Branco')
  })

  it('removes a value via the confirmation dialog and shrinks the combinations table', async () => {
    const { wrapper } = await mountWithRouter()

    await adicionarTipo(wrapper, 'Tamanho')
    await adicionarValor(wrapper, 'P')
    await adicionarValor(wrapper, 'M')

    expect(wrapper.findAll('.tabela tbody tr')).toHaveLength(2)

    await wrapper.findAll('.chip-remover')[0].trigger('click')
    expect(wrapper.text()).toContain('Confirmar remoção')
    await wrapper.findAll('button').find((b: any) => b.text() === 'Remover')!.trigger('click')

    expect(wrapper.findAll('.tabela tbody tr')).toHaveLength(1)
  })

  it('edits a variant through the side panel and reflects the change in the table', async () => {
    const { wrapper } = await mountWithRouter()

    await adicionarTipo(wrapper, 'Tamanho')
    await adicionarValor(wrapper, 'P')

    await wrapper.find('.editar-link').trigger('click')
    expect(wrapper.text()).toContain('Editar Variante')

    const inputs = wrapper.findAll('.painel-conteudo input')
    await inputs[0].setValue('P0001-P-CUSTOM')

    await wrapper.findAll('button').find((b: any) => b.text() === 'Salvar Variante')!.trigger('click')

    expect(wrapper.find('.painel-variante').exists()).toBe(false)
    expect(wrapper.find('.tabela tbody tr').text()).toContain('P0001-P-CUSTOM')
  })

  it('submits the variation payload and navigates to the list on success', async () => {
    vi.mocked(produtosApi.criarProdutoVariacao).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue(59.9)
    await adicionarTipo(wrapper, 'Tamanho')
    await adicionarValor(wrapper, 'P')
    await adicionarValor(wrapper, 'M')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(produtosApi.criarProdutoVariacao).toHaveBeenCalledWith(
      expect.objectContaining({
        nome: 'Camiseta Polo',
        sku: 'P0001',
        tiposVariacao: [{ nome: 'Tamanho', valores: ['P', 'M'] }],
      }),
    )
    const payload = vi.mocked(produtosApi.criarProdutoVariacao).mock.calls[0][0]
    expect(payload.variantes).toHaveLength(2)
    expect(router.currentRoute.value.name).toBe('produtos')
  })

  it('shows a conflict message on duplicate SKU (409), reusing the shared error mapping', async () => {
    vi.mocked(produtosApi.criarProdutoVariacao).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Camiseta Polo')
    await wrapper.find('[data-test="sku"]').setValue('P0001')
    await wrapper.find('[data-test="preco-venda"]').setValue(59.9)
    await adicionarTipo(wrapper, 'Tamanho')
    await adicionarValor(wrapper, 'P')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um produto cadastrado com este SKU')
  })
})
