import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import TransportadoraFormView from '@/views/TransportadoraFormView.vue'
import * as partnersApi from '@/api/partners'
import * as municipalitiesApi from '@/api/municipalities'
import { useToast } from '@/composables/useToast'

vi.mock('@/api/partners')
vi.mock('@/api/cep')
vi.mock('@/api/municipalities')

function mountWithRouter(path = '/transportadoras/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/transportadoras', name: 'transportadoras', component: { template: '<div />' } },
      { path: '/transportadoras/novo', name: 'transportadoras-novo', component: TransportadoraFormView },
      { path: '/transportadoras/:id/editar', name: 'transportadoras-editar', component: TransportadoraFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    // Cidade's SearchSelect teleports its panel to <body> -- stub it so it
    // renders in place, keeping wrapper.find() queries working.
    wrapper: mount(TransportadoraFormView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

async function selectCidade(
  wrapper: Awaited<ReturnType<typeof mountWithRouter>>['wrapper'],
  name = 'São Paulo',
) {
  await wrapper.find('[data-test="cidade"]').trigger('click')
  await flushPromises()
  await wrapper.find(`[data-test="cidade-option-${name}"]`).trigger('click')
}

describe('TransportadoraFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    useToast().toasts.splice(0, useToast().toasts.length)
    vi.mocked(municipalitiesApi.listMunicipalities).mockResolvedValue(['São Paulo', 'Campinas'])
  })

  it('defaults the Transportadora role checkbox to checked and Cliente/Fornecedor to unchecked', async () => {
    const { wrapper } = await mountWithRouter()

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    // Order in the template is Cliente, Fornecedor, Transportadora.
    expect((checkboxes[0].element as HTMLInputElement).checked).toBe(false)
    expect((checkboxes[1].element as HTMLInputElement).checked).toBe(false)
    expect((checkboxes[2].element as HTMLInputElement).checked).toBe(true)
  })

  it('shows a required-field error when nomeFantasia is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(partnersApi.createPartner).not.toHaveBeenCalled()
  })

  it('requires at least one papel to be selected', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Transportes Rápido Ltda')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Transportes Rápido Comércio LTDA')
    // Transportadora starts checked by default -- unchecking it leaves papeis empty.
    await wrapper.findAll('input[type="checkbox"]')[2].setValue(false)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione ao menos um papel')
  })

  it('requires cidade (município) to be filled on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Transportes Rápido Ltda')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Transportes Rápido Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const cidadeInput = wrapper.find('[data-test="cidade"]').element.parentElement!
    expect(cidadeInput.textContent).toContain('Campo obrigatório')
    expect(partnersApi.createPartner).not.toHaveBeenCalled()
  })

  it('submits the form with roles defaulted to CARRIER and navigates to the list on success', async () => {
    vi.mocked(partnersApi.createPartner).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Transportes Rápido Ltda')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Transportes Rápido Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await selectCidade(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(partnersApi.createPartner).toHaveBeenCalledWith(expect.objectContaining({ roles: ['CARRIER'] }))
    expect(router.currentRoute.value.name).toBe('transportadoras')
    expect(useToast().toasts.some((t) => t.message === 'Transportadora salva com sucesso!')).toBe(true)
  })

  it('masks the documento as CNPJ while typing (tipoPessoa defaults to JURIDICA)', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="documento"]').setValue('11222333000144')

    expect((wrapper.find('[data-test="documento"]').element as HTMLInputElement).value).toBe('11.222.333/0001-44')
  })

  it('shows a conflict message on duplicate documento (409)', async () => {
    vi.mocked(partnersApi.createPartner).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Transportes Rápido Ltda')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Transportes Rápido Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await selectCidade(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(
      useToast().toasts.some(
        (t) => t.type === 'error' && t.message === 'Já existe um parceiro cadastrado com este documento.',
      ),
    ).toBe(true)
  })

  it('loads existing parceiro data in edit mode', async () => {
    vi.mocked(partnersApi.getPartner).mockResolvedValue({
      id: 'abc-123', personType: 'LEGAL_ENTITY', document: '11222333000144', tradeName: 'Transportes Rápido Ltda',
      legalName: '', status: 'ACTIVE', roles: ['CARRIER'], billingEmails: '', whatsapp: '',
      taxIndicator: null, stateRegistration: '', municipalRegistration: '', suframaRegistration: '',
      zipCode: '', street: '', number: '', neighborhood: '', complement: '', state: '', city: 'São Paulo',
      notes: '', contacts: [],
    } as any)

    const { wrapper } = await mountWithRouter('/transportadoras/abc-123/editar')
    await flushPromises()

    expect(partnersApi.getPartner).toHaveBeenCalledWith('abc-123')
    expect((wrapper.find('[data-test="nomeFantasia"]').element as HTMLInputElement).value).toBe('Transportes Rápido Ltda')
  })

  it('shows an error message when loading parceiro data fails in edit mode', async () => {
    vi.mocked(partnersApi.getPartner).mockRejectedValue(new Error('network error'))

    await mountWithRouter('/transportadoras/abc-123/editar')
    await flushPromises()

    expect(partnersApi.getPartner).toHaveBeenCalledWith('abc-123')
    expect(
      useToast().toasts.some(
        (t) =>
          t.type === 'error' &&
          t.message === 'Não foi possível carregar os dados da transportadora. Tente novamente em instantes.',
      ),
    ).toBe(true)
  })
})
