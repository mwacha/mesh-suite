import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import ClienteFormView from '@/views/ClienteFormView.vue'
import * as parceirosApi from '@/api/parceiros'
import * as cepApi from '@/api/cep'

vi.mock('@/api/parceiros')
vi.mock('@/api/cep')

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
  })

  it('shows a required-field error when nomeFantasia is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(parceirosApi.criarParceiro).not.toHaveBeenCalled()
  })

  it('requires at least Cliente or Fornecedor to be selected', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    // Cliente starts checked by default -- one toggle unchecks it, leaving papeis empty.
    await wrapper.find('input[type="checkbox"]').setValue(false)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione ao menos Cliente ou Fornecedor')
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(parceirosApi.criarParceiro).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(parceirosApi.criarParceiro).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('clientes')
  })

  it('shows a conflict message on duplicate documento (409)', async () => {
    vi.mocked(parceirosApi.criarParceiro).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Mercado Silva')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um parceiro cadastrado com este documento')
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
    vi.mocked(parceirosApi.buscarParceiro).mockResolvedValue({
      id: 'abc-123', tipoPessoa: 'JURIDICA', documento: '11222333000144', nomeFantasia: 'Mercado Silva',
      razaoSocial: '', status: 'ATIVO', papeis: ['CLIENTE'], emailsCobranca: '', whatsapp: '',
      indicadorIe: null, inscricaoEstadual: '', inscricaoMunicipal: '', inscricaoSuframa: '',
      cep: '', logradouro: '', numero: '', bairro: '', complemento: '', uf: '', cidade: '',
      observacao: '', contatos: [],
    } as any)

    const { wrapper } = await mountWithRouter('/clientes/abc-123/editar')
    await flushPromises()

    expect(parceirosApi.buscarParceiro).toHaveBeenCalledWith('abc-123')
    expect((wrapper.find('[data-test="nomeFantasia"]').element as HTMLInputElement).value).toBe('Mercado Silva')
  })
})
