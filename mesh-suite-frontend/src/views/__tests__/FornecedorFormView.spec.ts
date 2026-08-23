import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import FornecedorFormView from '@/views/FornecedorFormView.vue'
import * as partnersApi from '@/api/partners'
import * as cepApi from '@/api/cep'
import { useToast } from '@/composables/useToast'

vi.mock('@/api/partners')
vi.mock('@/api/cep')

function mountWithRouter(path = '/fornecedores/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/fornecedores', name: 'fornecedores', component: { template: '<div />' } },
      { path: '/fornecedores/novo', name: 'fornecedores-novo', component: FornecedorFormView },
      { path: '/fornecedores/:id/editar', name: 'fornecedores-editar', component: FornecedorFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(FornecedorFormView, { global: { plugins: [router] } }),
  }))
}

describe('FornecedorFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    useToast().toasts.splice(0, useToast().toasts.length)
  })

  it('defaults the Fornecedor role checkbox to checked and Cliente to unchecked', async () => {
    const { wrapper } = await mountWithRouter()

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    // Order in the template is Cliente, Fornecedor, Transportadora (inert) --
    // unchanged from ClienteFormView; only which one starts checked differs.
    expect((checkboxes[0].element as HTMLInputElement).checked).toBe(false)
    expect((checkboxes[1].element as HTMLInputElement).checked).toBe(true)
  })

  it('shows a required-field error when nomeFantasia is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(partnersApi.createPartner).not.toHaveBeenCalled()
  })

  it('requires at least Cliente or Fornecedor to be selected', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Tecidos Aurora')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Tecidos Aurora Comércio LTDA')
    // Fornecedor starts checked by default -- unchecking it leaves papeis empty.
    await wrapper.findAll('input[type="checkbox"]')[1].setValue(false)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione ao menos Cliente ou Fornecedor')
  })

  it('submits the form with roles defaulted to SUPPLIER and navigates to the list on success', async () => {
    vi.mocked(partnersApi.createPartner).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Tecidos Aurora')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Tecidos Aurora Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(partnersApi.createPartner).toHaveBeenCalledWith(expect.objectContaining({ roles: ['SUPPLIER'] }))
    expect(router.currentRoute.value.name).toBe('fornecedores')
    expect(useToast().toasts.some((t) => t.message === 'Fornecedor salvo com sucesso!')).toBe(true)
  })

  it('masks the documento as CNPJ while typing (tipoPessoa defaults to JURIDICA)', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="documento"]').setValue('11222333000144')

    expect((wrapper.find('[data-test="documento"]').element as HTMLInputElement).value).toBe('11.222.333/0001-44')
  })

  it('shows a conflict message on duplicate documento (409)', async () => {
    vi.mocked(partnersApi.createPartner).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Tecidos Aurora')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Tecidos Aurora Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um parceiro cadastrado com este documento')
  })

  it('loads existing parceiro data in edit mode', async () => {
    vi.mocked(partnersApi.getPartner).mockResolvedValue({
      id: 'abc-123', personType: 'LEGAL_ENTITY', document: '11222333000144', tradeName: 'Tecidos Aurora',
      legalName: '', status: 'ACTIVE', roles: ['SUPPLIER'], billingEmails: '', whatsapp: '',
      taxIndicator: null, stateRegistration: '', municipalRegistration: '', suframaRegistration: '',
      zipCode: '', street: '', number: '', neighborhood: '', complement: '', state: '', city: '',
      notes: '', contacts: [],
    } as any)

    const { wrapper } = await mountWithRouter('/fornecedores/abc-123/editar')
    await flushPromises()

    expect(partnersApi.getPartner).toHaveBeenCalledWith('abc-123')
    expect((wrapper.find('[data-test="nomeFantasia"]').element as HTMLInputElement).value).toBe('Tecidos Aurora')
  })

  it('shows an error message when loading parceiro data fails in edit mode', async () => {
    vi.mocked(partnersApi.getPartner).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/fornecedores/abc-123/editar')
    await flushPromises()

    expect(partnersApi.getPartner).toHaveBeenCalledWith('abc-123')
    expect(wrapper.text()).toContain('Não foi possível carregar os dados do fornecedor')
  })
})
