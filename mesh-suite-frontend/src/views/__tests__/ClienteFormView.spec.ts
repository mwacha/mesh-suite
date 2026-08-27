import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ClienteFormView from '@/views/ClienteFormView.vue'
import * as partnersApi from '@/api/partners'
import * as cepApi from '@/api/cep'
import * as formasPagamentoApi from '@/api/paymentMethods'
import { useToast } from '@/composables/useToast'

vi.mock('@/api/partners')
vi.mock('@/api/cep')
vi.mock('@/api/paymentMethods')

function mountWithRouter(path = '/clientes/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/clientes', name: 'clientes', component: { template: '<div />' } },
      { path: '/clientes/novo', name: 'clientes-novo', component: ClienteFormView },
      { path: '/clientes/:id/editar', name: 'clientes-editar', component: ClienteFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ClienteFormView, { global: { plugins: [router] } }),
  }))
}

describe('ClienteFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    useToast().toasts.splice(0, useToast().toasts.length)
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 100,
    })
  })

  it('populates the forma de recebimento select with active payment methods and submits the chosen one', async () => {
    vi.mocked(formasPagamentoApi.listPaymentMethods).mockResolvedValue({
      content: [{ id: 'pm-1', description: 'À Vista', type: 'CASH', active: true, maxInstallments: 1, installmentsCount: 1, installmentDays: [0] }],
      totalElements: 1, totalPages: 1, number: 0, size: 100,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('À Vista')

    await wrapper.find('[data-test="forma-recebimento"]').setValue('pm-1')
    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Mercado Silva Ltda')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(partnersApi.createPartner).toHaveBeenCalledWith(
      expect.objectContaining({ paymentMethodId: 'pm-1' }),
    )
  })

  it('shows a required-field error when nomeFantasia is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(partnersApi.createPartner).not.toHaveBeenCalled()
  })

  it('shows a required-field error when razaoSocial is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(partnersApi.createPartner).not.toHaveBeenCalled()
  })

  it('requires at least Cliente or Fornecedor to be selected', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Mercado Silva Comércio LTDA')
    // Cliente starts checked by default -- one toggle unchecks it, leaving papeis empty.
    await wrapper.find('input[type="checkbox"]').setValue(false)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione ao menos Cliente ou Fornecedor')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(partnersApi.createPartner).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Mercado Silva Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(partnersApi.createPartner).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('clientes')
    expect(useToast().toasts.some((t) => t.message === 'Cliente salvo com sucesso!')).toBe(true)
  })

  it('masks the documento as CNPJ while typing (tipoPessoa defaults to JURIDICA)', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="documento"]').setValue('11222333000144')

    expect((wrapper.find('[data-test="documento"]').element as HTMLInputElement).value).toBe('11.222.333/0001-44')
  })

  it('shows an inline error when documento has the wrong digit count for tipoPessoa, in red', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="documento"]').setValue('123')
    await wrapper.find('[data-test="documento"]').trigger('blur')

    expect(wrapper.text()).toContain('Informe um CNPJ válido')
    expect(wrapper.find('[data-test="documento"]').classes()).toContain('input-error')
  })

  it('masks the WhatsApp field as a phone number while typing', async () => {
    const { wrapper } = await mountWithRouter()

    const campos = wrapper.findAll('input')
    const whatsapp = campos.find((c) => (c.element as HTMLInputElement).placeholder === '(11) 99999-9999')!
    await whatsapp.setValue('11933334444')

    expect((whatsapp.element as HTMLInputElement).value).toBe('(11) 93333-4444')
  })

  it('shows an inline validation error for an invalid e-mail in "E-mail(s)"', async () => {
    const { wrapper } = await mountWithRouter()

    const campos = wrapper.findAll('input')
    const email = campos.find((c) => (c.element as HTMLInputElement).placeholder === 'email@exemplo.com.br')!
    await email.setValue('nao-e-um-email')
    await email.trigger('blur')

    expect(wrapper.text()).toContain('Informe um e-mail válido')
    expect(email.classes()).toContain('input-error')
  })

  it('masks the CEP field while typing', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="cep"]').setValue('01310100')

    expect((wrapper.find('[data-test="cep"]').element as HTMLInputElement).value).toBe('01310-100')
  })

  it('shows an inline validation error for an incomplete CEP', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="cep"]').setValue('013')
    await wrapper.find('[data-test="cep"]').trigger('blur')

    expect(wrapper.text()).toContain('CEP inválido')
  })

  it('validates a new contact\'s e-mail and phone fields, masking the phone as typed', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('.btn-add-contato').trigger('click')
    const emailInputs = wrapper.findAll('input').filter((c) => (c.element as HTMLInputElement).placeholder === 'email@exemplo.com')
    const contatoEmail = emailInputs[0]
    const telInputs = wrapper.findAll('input').filter((c) => (c.element as HTMLInputElement).placeholder === '(11) 99999-9999')
    const contatoCelular = telInputs[telInputs.length - 1]

    await contatoEmail.setValue('invalido')
    await contatoEmail.trigger('blur')
    expect(wrapper.text()).toContain('E-mail inválido')

    await contatoCelular.setValue('11987654321')
    expect((contatoCelular.element as HTMLInputElement).value).toBe('(11) 98765-4321')
  })

  it('blocks submit while any field has a validation error', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="documento"]').setValue('123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(partnersApi.createPartner).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Informe um CNPJ válido')
  })

  it('shows a conflict message on duplicate documento (409)', async () => {
    vi.mocked(partnersApi.createPartner).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Mercado Silva Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um parceiro cadastrado com este documento')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(partnersApi.createPartner).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Mercado Silva Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('fills address fields when CEP lookup succeeds', async () => {
    vi.mocked(cepApi.buscarEnderecoPorCep).mockResolvedValue({
      logradouro: 'Av. Paulista', bairro: 'Bela Vista', localidade: 'São Paulo', uf: 'SP',
    })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="cep"]').setValue('01310100')
    await wrapper.find('[data-test="buscar-cep"]').trigger('click')
    await flushPromises()

    expect((wrapper.find('[data-test="logradouro"]').element as HTMLInputElement).value).toBe('Av. Paulista')
    expect((wrapper.find('[data-test="cidade"]').element as HTMLInputElement).value).toBe('São Paulo')
  })

  it('shows an error message when CEP lookup fails, without blocking manual entry', async () => {
    vi.mocked(cepApi.buscarEnderecoPorCep).mockResolvedValue(null)
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="cep"]').setValue('00000000')
    await wrapper.find('[data-test="buscar-cep"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('CEP não encontrado')
  })

  it('loads existing parceiro data in edit mode', async () => {
    vi.mocked(partnersApi.getPartner).mockResolvedValue({
      id: 'abc-123', personType: 'LEGAL_ENTITY', document: '11222333000144', tradeName: 'Mercado Silva',
      legalName: '', status: 'ACTIVE', roles: ['CUSTOMER'], billingEmails: '', whatsapp: '',
      taxIndicator: null, stateRegistration: '', municipalRegistration: '', suframaRegistration: '',
      zipCode: '', street: '', number: '', neighborhood: '', complement: '', state: '', city: '',
      notes: '', contacts: [],
    } as any)

    const { wrapper } = await mountWithRouter('/clientes/abc-123/editar')
    await flushPromises()

    expect(partnersApi.getPartner).toHaveBeenCalledWith('abc-123')
    expect((wrapper.find('[data-test="nomeFantasia"]').element as HTMLInputElement).value).toBe('Mercado Silva')
  })

  it('shows an error message when loading parceiro data fails in edit mode', async () => {
    vi.mocked(partnersApi.getPartner).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/clientes/abc-123/editar')
    await flushPromises()

    expect(partnersApi.getPartner).toHaveBeenCalledWith('abc-123')
    expect(wrapper.text()).toContain('Não foi possível carregar os dados do cliente')
  })
})
