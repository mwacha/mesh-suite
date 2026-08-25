import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PaymentMethodFormView from '@/views/PaymentMethodFormView.vue'
import * as formasPagamentoApi from '@/api/paymentMethods'

vi.mock('@/api/paymentMethods')

function mountWithRouter(path = '/formas-pagamento/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/formas-pagamento', name: 'formas-pagamento', component: { template: '<div />' } },
      { path: '/formas-pagamento/novo', name: 'formas-pagamento-novo', component: PaymentMethodFormView },
      { path: '/formas-pagamento/:id/editar', name: 'formas-pagamento-editar', component: PaymentMethodFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PaymentMethodFormView, { global: { plugins: [router] } }),
  }))
}

describe('PaymentMethodFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows a required-field error when descrição is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="descricao"]').setValue('')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(formasPagamentoApi.createPaymentMethod).not.toHaveBeenCalled()
  })

  it('starts with one installment row defaulting to à vista (0 dias, 100%)', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const dias = wrapper.find('[data-test="parcela-dias-0"]').element as HTMLInputElement
    const percentual = wrapper.find('[data-test="parcela-percentual-0"]').element as HTMLInputElement
    expect(Number(dias.value)).toBe(0)
    expect(Number(percentual.value)).toBe(100)
  })

  it('adds and removes installment rows', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="adicionar-parcela"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="parcela-dias-1"]').exists()).toBe(true)

    await wrapper.find('[data-test="remover-parcela-1"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="parcela-dias-1"]').exists()).toBe(false)
  })

  it('rejects submitting when installment percentages do not sum to 100', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="descricao"]').setValue('30/60')
    await wrapper.find('[data-test="parcela-percentual-0"]').setValue('50')
    await wrapper.find('[data-test="adicionar-parcela"]').trigger('click')
    await wrapper.find('[data-test="parcela-percentual-1"]').setValue('40')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('A soma dos percentuais das parcelas deve ser igual a 100%')
    expect(formasPagamentoApi.createPaymentMethod).not.toHaveBeenCalled()
  })

  it('creates a payment method and navigates to the list on success', async () => {
    vi.mocked(formasPagamentoApi.createPaymentMethod).mockResolvedValue({
      id: 'pm-1', description: 'À Vista', active: true, createdAt: '2026-01-01T00:00:00Z',
      installments: [{ installmentNumber: 1, daysDue: 0, percentage: 100 }],
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="descricao"]').setValue('À Vista')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(formasPagamentoApi.createPaymentMethod).toHaveBeenCalledWith({
      description: 'À Vista',
      active: true,
      installments: [{ daysDue: 0, percentage: 100 }],
    })
    expect(router.currentRoute.value.name).toBe('formas-pagamento')
  })

  it('shows a conflict message when the description already exists', async () => {
    vi.mocked(formasPagamentoApi.createPaymentMethod).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="descricao"]').setValue('À Vista')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma forma de pagamento cadastrada com esta descrição.')
  })

  it('loads existing data in edit mode', async () => {
    vi.mocked(formasPagamentoApi.getPaymentMethod).mockResolvedValue({
      id: 'pm-1', description: '30/60/90', active: true, createdAt: '2026-01-01T00:00:00Z',
      installments: [
        { installmentNumber: 1, daysDue: 30, percentage: 34 },
        { installmentNumber: 2, daysDue: 60, percentage: 33 },
        { installmentNumber: 3, daysDue: 90, percentage: 33 },
      ],
    })
    const { wrapper } = await mountWithRouter('/formas-pagamento/pm-1/editar')
    await flushPromises()

    expect((wrapper.find('[data-test="descricao"]').element as HTMLInputElement).value).toBe('30/60/90')
    expect(wrapper.find('[data-test="parcela-dias-2"]').exists()).toBe(true)
  })
})
