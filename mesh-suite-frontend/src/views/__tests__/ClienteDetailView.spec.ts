import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ClienteDetailView from '@/views/ClienteDetailView.vue'
import * as parceirosApi from '@/api/parceiros'

vi.mock('@/api/parceiros')

const parceiroCompleto = {
  id: 'p1', tipoPessoa: 'JURIDICA', documento: '11222333000144', nomeFantasia: 'Mercado Silva',
  razaoSocial: 'Mercado Silva Ltda', status: 'ATIVO', papeis: ['CLIENTE'], emailsCobranca: '', whatsapp: '',
  indicadorIe: null, inscricaoEstadual: '', inscricaoMunicipal: '', inscricaoSuframa: '',
  cep: '01310100', logradouro: 'Av. Paulista', numero: '1000', bairro: 'Bela Vista', complemento: '',
  uf: 'SP', cidade: 'São Paulo', observacao: '', contatos: [],
} as any

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/clientes/:id', name: 'clientes-detalhe', component: ClienteDetailView },
      { path: '/clientes/:id/editar', name: 'clientes-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/clientes/p1')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ClienteDetailView, { global: { plugins: [router] } }),
  }))
}

describe('ClienteDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(parceirosApi.buscarParceiro).mockResolvedValue(parceiroCompleto)
    vi.mocked(parceirosApi.listarParceiros).mockResolvedValue({
      content: [{
        id: 'p1', nomeFantasia: 'Mercado Silva', razaoSocial: '', documento: '',
        cidade: 'São Paulo', uf: 'SP', whatsapp: '', status: 'ATIVO',
      }],
      totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the selected client on the Dados tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
    expect((wrapper.find('input[readonly]').element as HTMLInputElement).value).toBe('Mercado Silva Ltda')
  })

  it('shows an empty state on the Pedidos tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const pedidosTab = wrapper.findAll('.tab').find((t) => t.text() === 'Pedidos')!
    await pedidosTab.trigger('click')

    expect(wrapper.text()).toContain('Nenhum pedido ainda')
  })

  it('navigates to the edit form when Editar is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('button.btn-secondary').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('shows an error message when loading the client fails', async () => {
    vi.mocked(parceirosApi.buscarParceiro).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do cliente.')
  })
})
