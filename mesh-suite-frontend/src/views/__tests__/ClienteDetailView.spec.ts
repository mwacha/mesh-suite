import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ClienteDetailView from '@/views/ClienteDetailView.vue'
import * as partnersApi from '@/api/partners'

vi.mock('@/api/partners')

const parceiroCompleto = {
  id: 'p1', personType: 'LEGAL_ENTITY', document: '11222333000144', tradeName: 'Mercado Silva',
  legalName: 'Mercado Silva Ltda', status: 'ACTIVE', roles: ['CUSTOMER'], billingEmails: '', whatsapp: '',
  taxIndicator: null, stateRegistration: '', municipalRegistration: '', suframaRegistration: '',
  zipCode: '01310100', street: 'Av. Paulista', number: '1000', neighborhood: 'Bela Vista', complement: '',
  state: 'SP', city: 'São Paulo', notes: '', contacts: [],
} as any

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/clientes', name: 'clientes', component: { template: '<div />' } },
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
    vi.mocked(partnersApi.getPartner).mockResolvedValue(parceiroCompleto)
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [{
        id: 'p1', tradeName: 'Mercado Silva', legalName: '', document: '', personType: 'LEGAL_ENTITY',
        city: 'São Paulo', state: 'SP', whatsapp: '', status: 'ACTIVE',
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

    await wrapper.find('[data-test="editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('navigates back to the client list when Cancelar is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cancelar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes')
  })

  it('shows an error message when loading the client fails', async () => {
    vi.mocked(partnersApi.getPartner).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do cliente.')
  })
})
