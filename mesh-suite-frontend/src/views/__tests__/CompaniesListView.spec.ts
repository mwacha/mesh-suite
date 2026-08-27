import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CompaniesListView from '@/views/CompaniesListView.vue'
import * as companiesApi from '@/api/companies'
import * as municipalitiesApi from '@/api/municipalities'
import type { CompanyResponse } from '@/api/companies'

vi.mock('@/api/companies', async (importOriginal) => {
  const original = await importOriginal<typeof companiesApi>()
  return {
    ...original,
    listCompanies: vi.fn(),
    getCompanyCounts: vi.fn(),
    updateCompanyStatus: vi.fn(),
    deleteCompany: vi.fn(),
  }
})

vi.mock('@/api/municipalities')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/empresas', name: 'empresas', component: CompaniesListView },
      { path: '/empresas/novo', name: 'empresas-novo', component: { template: '<div />' } },
      { path: '/empresas/:id/editar', name: 'empresas-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/empresas')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(CompaniesListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const companyExample: CompanyResponse = {
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
  city: 'São Paulo',
  state: 'SP',
  active: true,
}

function pageWith(...content: CompanyResponse[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 10 }
}

describe('CompaniesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(companiesApi.getCompanyCounts).mockResolvedValue({ total: 2, active: 1, inactive: 1 })
    vi.mocked(municipalitiesApi.listMunicipalities).mockResolvedValue(['São Paulo'])
  })

  it('loads and displays the company list', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith(companyExample))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Confecção Aurora Ltda')
    expect(wrapper.text()).toContain('São Paulo / SP')
  })

  it('shows the header count and the Total/Ativas/Inativas pills', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith(companyExample))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('2 empresas cadastradas')
    expect(wrapper.text()).toContain('Ativas')
    expect(wrapper.text()).toContain('Inativas')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(companiesApi.listCompanies).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de empresas.')
  })

  it('reloads the list when the search field changes', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith())
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-search"]').setValue('Aurora')
    await flushPromises()

    expect(companiesApi.listCompanies).toHaveBeenLastCalledWith(
      expect.objectContaining({ busca: 'Aurora' }),
    )
  })

  it('navigates to the new-company route when the button is clicked', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith())
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="new-company"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('empresas-novo')
  })

  it('sorts by legalName when the column header is clicked', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith(companyExample))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-legal-name"]').trigger('click')
    await flushPromises()

    expect(companiesApi.listCompanies).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'legalName,asc' }),
    )
  })

  it('navigates to edit when a row is clicked', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith(companyExample))
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-company-1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('empresas-editar')
    expect(router.currentRoute.value.params.id).toBe('company-1')
  })

  it('does not navigate to edit when clicking inside the Ações menu', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith(companyExample))
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="actions-company-1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('empresas')
  })

  it('toggles status after confirmation from the Ações menu', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith(companyExample))
    vi.mocked(companiesApi.updateCompanyStatus).mockResolvedValue({ ...companyExample, active: false })

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="actions-company-1"]').trigger('click')
    await wrapper.find('[data-test="action-status"]').trigger('click')
    await flushPromises()

    expect(companiesApi.updateCompanyStatus).toHaveBeenCalledWith('company-1', false)
  })

  it('deletes a company after confirmation and reloads the list', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith(companyExample))
    vi.mocked(companiesApi.deleteCompany).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="actions-company-1"]').trigger('click')
    await wrapper.find('[data-test="action-delete"]').trigger('click')
    await flushPromises()

    expect(companiesApi.deleteCompany).toHaveBeenCalledWith('company-1')
  })

  it('shows the backend message when deletion is blocked because it is the last company', async () => {
    vi.mocked(companiesApi.listCompanies).mockResolvedValue(pageWith(companyExample))
    vi.mocked(companiesApi.deleteCompany).mockRejectedValue({
      response: { data: { mensagem: 'Não é possível excluir: esta é a única empresa cadastrada para o tenant' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="actions-company-1"]').trigger('click')
    await wrapper.find('[data-test="action-delete"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('esta é a única empresa cadastrada para o tenant')
  })
})
