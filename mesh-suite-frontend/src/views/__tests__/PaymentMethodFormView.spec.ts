import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PaymentMethodFormView from '@/views/PaymentMethodFormView.vue'
import * as formasPagamentoApi from '@/api/paymentMethods'

vi.mock('@/api/paymentMethods', async (importOriginal) => {
  const original = await importOriginal<typeof formasPagamentoApi>()
  return {
    ...original,
    getPaymentMethod: vi.fn(),
    createPaymentMethod: vi.fn(),
    updatePaymentMethod: vi.fn(),
  }
})

function mountWithRouter(path = '/formas-recebimento/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/formas-recebimento', name: 'formas-recebimento', component: { template: '<div />' } },
      { path: '/formas-recebimento/novo', name: 'formas-recebimento-novo', component: PaymentMethodFormView },
      { path: '/formas-recebimento/:id/editar', name: 'formas-recebimento-editar', component: PaymentMethodFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PaymentMethodFormView, { global: { plugins: [router] } }),
  }))
}

const formaSalva = {
  id: 'pm-1',
  description: 'Cartão Crédito',
  type: 'CARD' as const,
  notes: null,
  active: true,
  maxInstallments: 12,
  interestRate: null,
  settlementDays: null,
  createdAt: '2026-01-01T00:00:00Z',
  installments: [],
}

describe('PaymentMethodFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows a required-field error when the name is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="descricao"]').setValue('')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(formasPagamentoApi.createPaymentMethod).not.toHaveBeenCalled()
  })

  it('requires a type before saving', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="descricao"]').setValue('Cartão Crédito')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(formasPagamentoApi.createPaymentMethod).not.toHaveBeenCalled()
  })

  it('renders the Condições fields from the wireframe', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Condições')
    expect(wrapper.find('[data-test="max-parcelas"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="taxa-juros"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="prazo-compensacao"]').exists()).toBe(true)
  })

  it('renders the status control as a pill inside the Condições card', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const condicoes = wrapper.findAll('section.card')[1]
    expect(condicoes.text()).toContain('Condições')
    expect(condicoes.text()).toContain('Status')
    expect(condicoes.find('.segmented-control-status').exists()).toBe(true)
    expect(condicoes.find('[data-test="status-ATIVO"] .segmented-dot').exists()).toBe(true)
  })

  it('creates a payment method and navigates to the list on success', async () => {
    vi.mocked(formasPagamentoApi.createPaymentMethod).mockResolvedValue(formaSalva)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="descricao"]').setValue('Cartão Crédito')
    await wrapper.find('[data-test="tipo"]').setValue('CARD')
    await wrapper.find('[data-test="max-parcelas"]').setValue('12')
    await wrapper.find('[data-test="taxa-juros"]').setValue('2.5')
    await wrapper.find('[data-test="prazo-compensacao"]').setValue('30')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(formasPagamentoApi.createPaymentMethod).toHaveBeenCalledWith({
      description: 'Cartão Crédito',
      type: 'CARD',
      notes: undefined,
      active: true,
      maxInstallments: 12,
      interestRate: 2.5,
      settlementDays: 30,
    })
    expect(router.currentRoute.value.name).toBe('formas-recebimento')
  })

  it('sends the inactive status picked in the segmented control', async () => {
    vi.mocked(formasPagamentoApi.createPaymentMethod).mockResolvedValue(formaSalva)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="descricao"]').setValue('Duplicata')
    await wrapper.find('[data-test="tipo"]').setValue('DUPLICATA')
    await wrapper.find('[data-test="status-INATIVO"]').trigger('click')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(formasPagamentoApi.createPaymentMethod).toHaveBeenCalledWith(
      expect.objectContaining({ active: false }),
    )
  })

  it('shows a conflict message when the name already exists', async () => {
    vi.mocked(formasPagamentoApi.createPaymentMethod).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="descricao"]').setValue('Pix')
    await wrapper.find('[data-test="tipo"]').setValue('PIX')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma forma de recebimento cadastrada com este nome.')
  })

  it('loads existing data in edit mode', async () => {
    vi.mocked(formasPagamentoApi.getPaymentMethod).mockResolvedValue({
      ...formaSalva,
      description: '30/60/90',
      type: 'DUPLICATA',
      notes: 'Para clientes com crédito aprovado',
      maxInstallments: 3,
      interestRate: 1.5,
      settlementDays: 2,
      installments: [
        { installmentNumber: 1, daysDue: 30, percentage: 34 },
        { installmentNumber: 2, daysDue: 60, percentage: 33 },
        { installmentNumber: 3, daysDue: 90, percentage: 33 },
      ],
    })
    const { wrapper } = await mountWithRouter('/formas-recebimento/pm-1/editar')
    await flushPromises()

    expect((wrapper.find('[data-test="descricao"]').element as HTMLInputElement).value).toBe('30/60/90')
    expect((wrapper.find('[data-test="tipo"]').element as HTMLSelectElement).value).toBe('DUPLICATA')
    expect((wrapper.find('[data-test="max-parcelas"]').element as HTMLInputElement).value).toBe('3')
    expect((wrapper.find('[data-test="observacao"]').element as HTMLInputElement).value).toBe('Para clientes com crédito aprovado')
  })

  it('omits installments on update so the stored breakdown is preserved', async () => {
    vi.mocked(formasPagamentoApi.getPaymentMethod).mockResolvedValue({
      ...formaSalva,
      description: '30/60/90',
      type: 'DUPLICATA',
      maxInstallments: 3,
      installments: [{ installmentNumber: 1, daysDue: 30, percentage: 100 }],
    })
    vi.mocked(formasPagamentoApi.updatePaymentMethod).mockResolvedValue(formaSalva)
    const { wrapper } = await mountWithRouter('/formas-recebimento/pm-1/editar')
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = vi.mocked(formasPagamentoApi.updatePaymentMethod).mock.calls[0][1]
    expect(payload).not.toHaveProperty('installments')
  })
})
