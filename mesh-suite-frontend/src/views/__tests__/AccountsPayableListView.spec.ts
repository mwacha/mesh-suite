import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AccountsPayableListView from '@/views/AccountsPayableListView.vue'
import * as accountsPayableApi from '@/api/accountsPayable'

vi.mock('@/api/accountsPayable')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/contas-a-pagar', name: 'contas-a-pagar', component: AccountsPayableListView },
    ],
  })
  router.push('/contas-a-pagar')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown is Teleported to <body> so it isn't clipped by the
    // table card's `overflow: hidden` -- stub it here so it renders in
    // place instead, keeping the existing wrapper.find() queries working.
    wrapper: mount(AccountsPayableListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const tituloAberto = {
  id: 'ap1', number: 1, installmentNumber: 1, totalInstallments: 3, supplierId: 'f1',
  supplierName: 'Tecidos Aurora', amount: 50.0, issueDate: '2026-08-06', dueDate: '2026-09-05',
  paymentDate: null, status: 'OPEN' as const, referenceId: null, createdAt: '2026-08-06T10:00:00Z',
}

const tituloPago = {
  id: 'ap2', number: 2, installmentNumber: 2, totalInstallments: 3, supplierId: 'f1',
  supplierName: 'Tecidos Aurora', amount: 50.0, issueDate: '2026-08-06', dueDate: '2026-10-05',
  paymentDate: '2026-08-10', status: 'PAID' as const, referenceId: null, createdAt: '2026-08-06T10:00:00Z',
}

describe('AccountsPayableListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(accountsPayableApi.listAccountsPayable).mockResolvedValue({
      content: [tituloAberto], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the accounts payable list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('1/3')
  })

  it('re-fetches with the status filter when it changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('select').setValue('PAID')
    await flushPromises()

    expect(accountsPayableApi.listAccountsPayable).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'PAID' }))
  })

  it('re-fetches with the sort param when a sortable column header is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-vencimento"]').trigger('click')
    await flushPromises()

    expect(accountsPayableApi.listAccountsPayable).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'dueDate,asc' }),
    )
  })

  it('gives baixa via the Ações menu', async () => {
    vi.mocked(accountsPayableApi.updateAccountsPayableStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ap1"]').trigger('click')
    await wrapper.find('[data-test="acao-baixa"]').trigger('click')
    await flushPromises()

    expect(accountsPayableApi.updateAccountsPayableStatus).toHaveBeenCalledWith('ap1', 'PAID')
  })

  it('reverses a baixa via the Ações menu', async () => {
    vi.mocked(accountsPayableApi.listAccountsPayable).mockResolvedValue({
      content: [tituloPago], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(accountsPayableApi.updateAccountsPayableStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ap2"]').trigger('click')
    await wrapper.find('[data-test="acao-reverter"]').trigger('click')
    await flushPromises()

    expect(accountsPayableApi.updateAccountsPayableStatus).toHaveBeenCalledWith('ap2', 'OPEN')
  })

  it('hides the baixa action for an already-paid entry and the reversal action for an open one', async () => {
    vi.mocked(accountsPayableApi.listAccountsPayable).mockResolvedValue({
      content: [tituloPago], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-ap2"]').trigger('click')

    expect(wrapper.find('[data-test="acao-baixa"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="acao-reverter"]').exists()).toBe(true)
  })

  it('shows an empty state when there are no accounts payable', async () => {
    vi.mocked(accountsPayableApi.listAccountsPayable).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma conta a pagar para exibir.')
  })

  it('shows an error message when loading the list fails', async () => {
    vi.mocked(accountsPayableApi.listAccountsPayable).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de contas a pagar.')
  })
})
