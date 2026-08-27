import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CompanyFormView from '@/views/CompanyFormView.vue'
import * as companiesApi from '@/api/companies'
import * as cepApi from '@/api/cep'

vi.mock('@/api/companies', async (importOriginal) => {
  const original = await importOriginal<typeof companiesApi>()
  return {
    ...original,
    getCompany: vi.fn(),
    createCompany: vi.fn(),
    updateCompany: vi.fn(),
  }
})

vi.mock('@/api/cep', async (importOriginal) => {
  const original = await importOriginal<typeof cepApi>()
  return {
    ...original,
    buscarEnderecoPorCep: vi.fn(),
  }
})

function mountWithRouter(path = '/empresas/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/empresas', name: 'empresas', component: { template: '<div />' } },
      { path: '/empresas/novo', name: 'empresas-novo', component: CompanyFormView },
      { path: '/empresas/:id/editar', name: 'empresas-editar', component: CompanyFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(CompanyFormView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const companyExample: companiesApi.CompanyResponse = {
  id: 'company-1',
  legalName: 'Confecção Aurora Ltda',
  cnpj: '11222333000144',
  tradeName: 'Confecção Aurora',
  stateRegistration: null,
  municipalRegistration: null,
  phone: null,
  email: null,
  website: null,
  zipCode: null,
  street: null,
  number: null,
  complement: null,
  neighborhood: null,
  city: null,
  state: null,
  active: true,
}

describe('CompanyFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field errors when legalName and cnpj are blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(companiesApi.createCompany).not.toHaveBeenCalled()
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(companiesApi.createCompany).mockResolvedValue(companyExample)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(companiesApi.createCompany).toHaveBeenCalledWith(
      expect.objectContaining({ legalName: 'Confecção Aurora Ltda', cnpj: '11222333000144' }),
    )
    expect(router.currentRoute.value.name).toBe('empresas')
  })

  it('shows a conflict message on duplicate cnpj (409)', async () => {
    vi.mocked(companiesApi.createCompany).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma empresa cadastrada com este CNPJ')
  })

  it('loads existing company data in edit mode', async () => {
    vi.mocked(companiesApi.getCompany).mockResolvedValue(companyExample)

    const { wrapper } = await mountWithRouter('/empresas/company-1/editar')
    await flushPromises()

    expect(companiesApi.getCompany).toHaveBeenCalledWith('company-1')
    expect((wrapper.find('[data-test="legal-name"]').element as HTMLInputElement).value).toBe('Confecção Aurora Ltda')
  })

  it('shows an error message when loading company data fails in edit mode', async () => {
    vi.mocked(companiesApi.getCompany).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/empresas/company-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da empresa.')
  })

  it('fills the address fields from the CEP lookup', async () => {
    vi.mocked(cepApi.buscarEnderecoPorCep).mockResolvedValue({
      logradouro: 'Av. Paulista', bairro: 'Bela Vista', localidade: 'São Paulo', uf: 'SP',
    })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="zip-code"]').setValue('01310-100')
    await wrapper.find('[data-test="search-cep"]').trigger('click')
    await flushPromises()

    expect((wrapper.find('[data-test="street"]').element as HTMLInputElement).value).toBe('Av. Paulista')
    expect((wrapper.find('[data-test="city"]').element as HTMLInputElement).value).toBe('São Paulo')
  })
})
