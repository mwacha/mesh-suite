import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PaymentMethodsListView from '@/views/PaymentMethodsListView.vue'
import * as formasPagamentoApi from '@/api/paymentMethods'

vi.mock('@/api/paymentMethods')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/formas-pagamento', name: 'formas-pagamento', component: PaymentMethodsListView },
      { path: '/formas-pagamento/novo', name: 'formas-pagamento-novo', component: { template: '<div />' } },
      { path: '/formas-pagamento/:id/editar', name: 'formas-pagamento-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/formas-pagamento')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PaymentMethodsListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const formaExemplo = {
  id: 'pm-1',
  description: '30/60/90',
  active: true,
  installmentsCount: 3,
}

describe('PaymentMethodsListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the payment method list', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue({
      content: [formaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('30/60/90')
    expect(wrapper.text()).toContain('3x')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de formas de pagamento.')
  })

  it('navigates to the new-forma route when the button is clicked', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-forma-pagamento"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('formas-pagamento-novo')
  })

  it('deletes a payment method after confirmation and reloads the list', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue({
      content: [formaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(formasPagamentoApi.deletePaymentMethod).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(formasPagamentoApi.deletePaymentMethod).toHaveBeenCalledWith('pm-1')
  })
})
