import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PedidoFormView from '@/views/PedidoFormView.vue'
import * as pedidosApi from '@/api/pedidos'
import * as parceirosApi from '@/api/parceiros'
import * as usersApi from '@/api/users'
import * as produtosApi from '@/api/produtos'

vi.mock('@/api/pedidos')
vi.mock('@/api/parceiros')
vi.mock('@/api/users')
vi.mock('@/api/produtos')

function mountWithRouter(path = '/pedidos/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/pedidos', name: 'pedidos', component: { template: '<div />' } },
      { path: '/pedidos/novo', name: 'pedidos-novo', component: PedidoFormView },
      { path: '/pedidos/:id/editar', name: 'pedidos-editar', component: PedidoFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PedidoFormView, { global: { plugins: [router] } }),
  }))
}

const clienteBase = {
  id: 'c1', nomeFantasia: 'Mercado Silva', razaoSocial: 'Mercado Silva Ltda',
  documento: '11222333000144', cidade: 'São Paulo', uf: 'SP', whatsapp: '', status: 'ATIVO' as const,
}

const salesRepBase = { id: 'v1', name: 'Carla Vendedora' }

const produtoBase = {
  id: 'p1', nome: 'Camiseta Polo', sku: 'P0001', marca: 'Marca Alpha',
  precoVenda: 59.9, quantidadeEstoque: 10, status: 'ATIVO' as const,
}

describe('PedidoFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usersApi.listSalesReps).mockResolvedValue([salesRepBase])
    vi.mocked(parceirosApi.listarParceiros).mockResolvedValue({
      content: [clienteBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
    vi.mocked(produtosApi.listarProdutos).mockResolvedValue({
      content: [produtoBase], totalElements: 1, totalPages: 1, number: 0, size: 5,
    })
  })

  it('shows required-field errors when cliente/vendedor/itens are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione um cliente')
    expect(wrapper.text()).toContain('Selecione um vendedor')
    expect(wrapper.text()).toContain('Adicione ao menos um item')
    expect(pedidosApi.criarPedido).not.toHaveBeenCalled()
  })

  it('loads the representantes list for the vendedor select', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(usersApi.listSalesReps).toHaveBeenCalled()
    expect(wrapper.find('[data-test="vendedor"]').text()).toContain('Carla Vendedora')
  })

  it('searches and selects a cliente via the busca dropdown', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cliente-busca"]').setValue('silva')
    await flushPromises()

    expect(parceirosApi.listarParceiros).toHaveBeenCalledWith(
      expect.objectContaining({ busca: 'silva', papel: 'CLIENTE' }),
    )
    await wrapper.find('[data-test="cliente-resultados"] li').trigger('click')

    expect((wrapper.find('[data-test="cliente-busca"]').element as HTMLInputElement).value).toBe('Mercado Silva')
  })

  it('searches for a produto, adds it as an item and computes totals live', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="produto-busca"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('2')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Camiseta Polo')
    expect(wrapper.text()).toContain('R$ 119,80')

    await wrapper.find('[data-test="item-remover"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Camiseta Polo')
  })

  it('normalizes a cleared valorUnitario to the number 0 (not empty-string) when added immediately', async () => {
    vi.mocked(pedidosApi.criarPedido).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cliente-busca"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="cliente-resultados"] li').trigger('click')
    await wrapper.find('[data-test="vendedor"]').setValue('v1')

    await wrapper.find('[data-test="produto-busca"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    // Simulate the auto-filled valor unitário being manually cleared, then
    // "Adicionar" clicked immediately -- v-model.number drives the underlying
    // value to '' (empty string) when cleared, and adicionarItem() must
    // normalize that '' to 0 before it lands in form.itens/payload. This must
    // NOT be refilled before clicking Adicionar, or the empty-string state
    // never reaches adicionarItem() and the normalization guard goes untested.
    await wrapper.find('[data-test="item-valor-unitario"]').setValue('')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(pedidosApi.criarPedido).mock.calls[0][0]
    expect(payload.itens[0].valorUnitario).toBe(0)
    expect(typeof payload.itens[0].valorUnitario).toBe('number')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(pedidosApi.criarPedido).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cliente-busca"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="cliente-resultados"] li').trigger('click')
    await wrapper.find('[data-test="vendedor"]').setValue('v1')

    await wrapper.find('[data-test="produto-busca"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(pedidosApi.criarPedido).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('pedidos')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(pedidosApi.criarPedido).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cliente-busca"]').setValue('silva')
    await flushPromises()
    await wrapper.find('[data-test="cliente-resultados"] li').trigger('click')
    await wrapper.find('[data-test="vendedor"]').setValue('v1')

    await wrapper.find('[data-test="produto-busca"]').setValue('camiseta')
    await flushPromises()
    await wrapper.find('[data-test="produto-resultados"] li').trigger('click')
    await wrapper.find('[data-test="item-quantidade"]').setValue('1')
    await wrapper.find('[data-test="item-adicionar"]').trigger('click')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing pedido data in edit mode', async () => {
    vi.mocked(pedidosApi.buscarPedido).mockResolvedValue({
      id: 'ped-1', numero: 3, clienteId: 'c1', clienteNome: 'Mercado Silva', vendedorId: 'v1',
      vendedorNome: 'Carla Vendedora', dataPedido: '2026-07-31', dataEntrega: null, status: 'DIGITADO',
      desconto: 0, subtotal: 119.8, total: 119.8,
      itens: [{ produtoId: 'p1', produtoNome: 'Camiseta Polo', quantidade: 2, valorUnitario: 59.9, valorTotal: 119.8 }],
    } as any)

    const { wrapper } = await mountWithRouter('/pedidos/ped-1/editar')
    await flushPromises()

    expect(pedidosApi.buscarPedido).toHaveBeenCalledWith('ped-1')
    expect((wrapper.find('[data-test="cliente-busca"]').element as HTMLInputElement).value).toBe('Mercado Silva')
    expect(wrapper.text()).toContain('Camiseta Polo')
  })

  it('shows an error message when loading pedido data fails in edit mode', async () => {
    vi.mocked(pedidosApi.buscarPedido).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/pedidos/ped-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do pedido.')
  })
})
