import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PaymentMethodsListView from '@/views/PaymentMethodsListView.vue'
import * as formasPagamentoApi from '@/api/paymentMethods'
import type { PaymentMethodSummary } from '@/api/paymentMethods'

vi.mock('@/api/paymentMethods', async (importOriginal) => {
  const original = await importOriginal<typeof formasPagamentoApi>()
  return {
    ...original,
    listPaymentMethods: vi.fn(),
    getPaymentMethodCounts: vi.fn(),
    deletePaymentMethod: vi.fn(),
  }
})

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/formas-recebimento', name: 'formas-recebimento', component: PaymentMethodsListView },
      { path: '/formas-recebimento/novo', name: 'formas-recebimento-novo', component: { template: '<div />' } },
      { path: '/formas-recebimento/:id/editar', name: 'formas-recebimento-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/formas-recebimento')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PaymentMethodsListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const formaExemplo: PaymentMethodSummary = {
  id: 'pm-1',
  description: 'Duplicata',
  type: 'DUPLICATA',
  active: true,
  maxInstallments: 4,
  installmentsCount: 4,
  installmentDays: [30, 60, 90, 120],
}

function paginaCom(...content: PaymentMethodSummary[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 10 }
}

describe('PaymentMethodsListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(formasPagamentoApi.getPaymentMethodCounts).mockResolvedValue({ total: 6, active: 5, inactive: 1 })
  })

  it('loads and displays the payment method list with type and installment summary', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue(paginaCom(formaExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Duplicata')
    expect(wrapper.text()).toContain('30/60/90/120')
  })

  it('summarizes installments as "até Nx" when only a maximum is set', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue(
      paginaCom({ ...formaExemplo, description: 'Cartão Crédito', type: 'CARD', maxInstallments: 12, installmentsCount: 0, installmentDays: [] }),
    )
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Cartão')
    expect(wrapper.text()).toContain('até 12x')
  })

  it('summarizes a single-payment method as "1x"', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue(
      paginaCom({ ...formaExemplo, description: 'Pix', type: 'PIX', maxInstallments: 1, installmentsCount: 0, installmentDays: [] }),
    )
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('1x')
  })

  it('shows the header count and the Total/Ativas/Inativas pills', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue(paginaCom(formaExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('6 formas cadastradas')
    expect(wrapper.text()).toContain('Ativas')
    expect(wrapper.text()).toContain('Inativas')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de formas de recebimento.')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue(paginaCom(formaExemplo))
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-pm-1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('formas-recebimento-editar')
    expect(router.currentRoute.value.params.id).toBe('pm-1')
  })

  it('navigates to the new-forma route when the button is clicked', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue(paginaCom())
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-forma-recebimento"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('formas-recebimento-novo')
  })

  it('sorts by name when the column header is clicked', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue(paginaCom(formaExemplo))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-nome"]').trigger('click')
    await flushPromises()

    expect(formasPagamentoApi.listPaymentMethods).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'description,asc' }),
    )
  })

  it('deletes a payment method after confirmation and reloads the list', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue(paginaCom(formaExemplo))
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
