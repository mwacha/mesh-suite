import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import TransportadoraDetailView from '@/views/TransportadoraDetailView.vue'
import * as partnersApi from '@/api/partners'

vi.mock('@/api/partners')

const parceiroCompleto = {
  id: 'p1', personType: 'LEGAL_ENTITY', document: '11222333000144', tradeName: 'Transportes Rápido Ltda',
  legalName: 'Transportes Rápido Ltda', status: 'ACTIVE', roles: ['CARRIER'], billingEmails: '', whatsapp: '',
  taxIndicator: null, stateRegistration: '', municipalRegistration: '', suframaRegistration: '',
  zipCode: '01310100', street: 'Av. Paulista', number: '1000', neighborhood: 'Bela Vista', complement: '',
  state: 'SP', city: 'São Paulo', notes: '', contacts: [],
} as any

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/transportadoras', name: 'transportadoras', component: { template: '<div />' } },
      { path: '/transportadoras/:id', name: 'transportadoras-detalhe', component: TransportadoraDetailView },
      { path: '/transportadoras/:id/editar', name: 'transportadoras-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/transportadoras/p1')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(TransportadoraDetailView, { global: { plugins: [router] } }),
  }))
}

describe('TransportadoraDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(partnersApi.getPartner).mockResolvedValue(parceiroCompleto)
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [{
        id: 'p1', tradeName: 'Transportes Rápido Ltda', legalName: '', document: '', personType: 'LEGAL_ENTITY',
        city: 'São Paulo', state: 'SP', whatsapp: '', status: 'ACTIVE',
      }],
      totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the selected carrier on the Dados tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Transportes Rápido Ltda')
    expect((wrapper.find('input[readonly]').element as HTMLInputElement).value).toBe('Transportes Rápido Ltda')
  })

  it('filters the rail search by carrier role', async () => {
    await mountWithRouter()
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenCalledWith(expect.objectContaining({ papel: 'CARRIER' }))
  })

  it('does not show Ordens de Compra or Financeiro tabs (not applicable to Transportadora)', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const tabTexts = wrapper.findAll('.tab').map((t) => t.text())
    expect(tabTexts).toEqual(['Dados', 'Endereços', 'Contatos'])
  })

  it('shows the address on the Endereços tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const tab = wrapper.findAll('.tab').find((t) => t.text() === 'Endereços')!
    await tab.trigger('click')

    expect(wrapper.text()).toContain('Av. Paulista, 1000')
    expect(wrapper.text()).toContain('São Paulo / SP')
  })

  it('navigates to the edit form when Editar is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('transportadoras-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('navigates back to the carrier list when Cancelar is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cancelar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('transportadoras')
  })

  it('shows an error message when loading the carrier fails', async () => {
    vi.mocked(partnersApi.getPartner).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da transportadora.')
  })
})
