import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ClientesListView from '@/views/ClientesListView.vue'
import * as partnersApi from '@/api/partners'
import * as municipiosApi from '@/api/municipios'

vi.mock('@/api/partners')
vi.mock('@/api/municipios')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/clientes', name: 'clientes', component: ClientesListView },
      { path: '/clientes/novo', name: 'clientes-novo', component: { template: '<div />' } },
      { path: '/clientes/:id/editar', name: 'clientes-editar', component: { template: '<div />' } },
      { path: '/clientes/:id', name: 'clientes-detalhe', component: { template: '<div />' } },
    ],
  })
  router.push('/clientes')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown/filter panels Teleport to <body> -- stub it here
    // so it renders in place, keeping wrapper.find() queries working.
    wrapper: mount(ClientesListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const parceiroBase = {
  id: 'p1', tradeName: 'Mercado Silva', legalName: 'Mercado Silva Ltda',
  document: '11222333000144', personType: 'LEGAL_ENTITY' as const,
  city: 'São Paulo', state: 'SP', whatsapp: '11934567890',
  status: 'ACTIVE' as const,
}

describe('ClientesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [parceiroBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(partnersApi.getPartnerSummary).mockResolvedValue({ total: 1, active: 1, atRisk: 0, blocked: 0 })
    vi.mocked(municipiosApi.listarMunicipios).mockResolvedValue(['São Paulo'])
  })

  it('loads and displays the client list on mount, with the count in the page header', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Mercado Silva')
    expect(wrapper.text()).toContain('1 clientes cadastrados')
  })

  it('formats the documento (CNPJ/CPF) and telefone columns according to tipoPessoa', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('11.222.333/0001-44')
    expect(wrapper.text()).toContain('(11) 93456-7890')
  })

  it('only lists Clientes, never Fornecedores/Transportadoras, and loads the city list from the backend', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ papel: 'CUSTOMER' }))
    expect(municipiosApi.listarMunicipios).toHaveBeenCalledWith({ uf: undefined })
    expect(partnersApi.getPartnerSummary).toHaveBeenCalledWith('CUSTOMER')

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Cidade"]').trigger('click')
    expect(wrapper.find('[data-test="filter-value-São Paulo"]').exists()).toBe(true)
  })

  it('re-fetches the city list scoped to the selected UF when a single UF is applied', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()
    vi.mocked(municipiosApi.listarMunicipios).mockResolvedValue(['Rio de Janeiro'])

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-UF"]').trigger('click')
    await wrapper.find('[data-test="filter-value-RJ"]').trigger('click')
    await wrapper.find('[data-test="filter-bar-apply"]').trigger('click')
    await flushPromises()

    expect(municipiosApi.listarMunicipios).toHaveBeenLastCalledWith({ uf: 'RJ' })
  })

  it('re-fetches with the search term when the search field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-search"]').setValue('silva')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'silva' }))
  })

  it('re-fetches with mapped API values when a multi-value Status filter is applied', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Status"]').trigger('click')
    await wrapper.find('[data-test="filter-value-Ativo"]').trigger('click')
    await wrapper.find('[data-test="filter-value-Bloqueado"]').trigger('click')
    await wrapper.find('[data-test="filter-bar-apply"]').trigger('click')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: ['ACTIVE', 'BLOCKED'] }),
    )
  })

  it('shows a type+number panel (not a checkbox list) for the Nr. Documento filter', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Nr. Documento"]').trigger('click')

    expect(wrapper.find('[data-test="filter-bar-values"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="documento-filtro-tipo-cnpj"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="documento-filtro-numero"]').exists()).toBe(true)
  })

  it('re-fetches with tipoDocumento + documento when the document type and number are applied', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Nr. Documento"]').trigger('click')
    await wrapper.find('[data-test="documento-filtro-tipo-cnpj"]').setValue(true)
    await wrapper.find('[data-test="documento-filtro-numero"]').setValue('11222333000144')
    await wrapper.find('[data-test="documento-filtro-aplicar"]').trigger('click')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(
      expect.objectContaining({ tipoDocumento: ['LEGAL_ENTITY'], documento: '11.222.333/0001-44' }),
    )
  })

  it('does not apply the Nr. Documento filter until both type and number are given', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Nr. Documento"]').trigger('click')
    await wrapper.find('[data-test="documento-filtro-tipo-cnpj"]').setValue(true)
    await wrapper.find('[data-test="documento-filtro-aplicar"]').trigger('click')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(
      expect.objectContaining({ tipoDocumento: undefined, documento: undefined }),
    )
  })

  it('re-fetches with the sort param when a sortable column header is clicked, toggling direction', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-cidade"]').trigger('click')
    await flushPromises()
    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'cidade,asc' }))

    await wrapper.find('[data-test="col-cidade"]').trigger('click')
    await flushPromises()
    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'cidade,desc' }))
  })

  it('sorts by Nome and Status columns as well', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-nome"]').trigger('click')
    await flushPromises()
    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'nomeFantasia,asc' }))

    await wrapper.find('[data-test="col-status"]').trigger('click')
    await flushPromises()
    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'status,asc' }))
  })

  it('navigates to the create form when "+ Novo Cliente" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-cliente"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-novo')
  })

  it('navigates to the detail view via the Ações menu\'s "Ver" item', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-ver"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-detalhe')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-p1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('does not navigate to edit when clicking inside the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('clientes')
  })

  it('toggles a client status via the Ações menu', async () => {
    vi.mocked(partnersApi.updatePartnerStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-status"]').trigger('click')
    await flushPromises()

    expect(partnersApi.updatePartnerStatus).toHaveBeenCalledWith('p1', 'BLOCKED')
  })

  it('re-fetches with the new page when pagination is used', async () => {
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [parceiroBase], totalElements: 25, totalPages: 3, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="pagination-page-2"]').trigger('click')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
  })

  it('shows an error message when loading the client list fails', async () => {
    vi.mocked(partnersApi.listPartners).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de clientes.')
  })
})
