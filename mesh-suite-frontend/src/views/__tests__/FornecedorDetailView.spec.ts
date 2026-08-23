import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import FornecedorDetailView from '@/views/FornecedorDetailView.vue'
import * as partnersApi from '@/api/partners'

vi.mock('@/api/partners')

const parceiroCompleto = {
  id: 'p1', personType: 'LEGAL_ENTITY', document: '11222333000144', tradeName: 'Tecidos Aurora',
  legalName: 'Tecidos Aurora Ltda', status: 'ACTIVE', roles: ['SUPPLIER'], billingEmails: '', whatsapp: '',
  taxIndicator: null, stateRegistration: '', municipalRegistration: '', suframaRegistration: '',
  zipCode: '01310100', street: 'Av. Paulista', number: '1000', neighborhood: 'Bela Vista', complement: '',
  state: 'SP', city: 'São Paulo', notes: '', contacts: [],
} as any

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/fornecedores', name: 'fornecedores', component: { template: '<div />' } },
      { path: '/fornecedores/:id', name: 'fornecedores-detalhe', component: FornecedorDetailView },
      { path: '/fornecedores/:id/editar', name: 'fornecedores-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/fornecedores/p1')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(FornecedorDetailView, { global: { plugins: [router] } }),
  }))
}

describe('FornecedorDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(partnersApi.getPartner).mockResolvedValue(parceiroCompleto)
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [{
        id: 'p1', tradeName: 'Tecidos Aurora', legalName: '', document: '', personType: 'LEGAL_ENTITY',
        city: 'São Paulo', state: 'SP', whatsapp: '', status: 'ACTIVE',
      }],
      totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the selected supplier on the Dados tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect((wrapper.find('input[readonly]').element as HTMLInputElement).value).toBe('Tecidos Aurora Ltda')
  })

  it('filters the rail search by supplier role', async () => {
    await mountWithRouter()
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenCalledWith(expect.objectContaining({ papel: 'SUPPLIER' }))
  })

  it('does not show the sale-only stub fields (Tabela de Preço, Limite de Crédito, Forma de Pagamento, Vendedor Responsável)', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).not.toContain('Tabela de Preço')
    expect(wrapper.text()).not.toContain('Limite de Crédito')
    expect(wrapper.text()).not.toContain('Forma de Pagamento')
    expect(wrapper.text()).not.toContain('Vendedor Responsável')
  })

  it('shows an empty state on the Ordens de Compra tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const tab = wrapper.findAll('.tab').find((t) => t.text() === 'Ordens de Compra')!
    await tab.trigger('click')

    expect(wrapper.text()).toContain('Nenhuma ordem de compra ainda')
  })

  it('navigates to the edit form when Editar is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('navigates back to the supplier list when Cancelar is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cancelar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores')
  })

  it('shows an error message when loading the supplier fails', async () => {
    vi.mocked(partnersApi.getPartner).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do fornecedor.')
  })
})
