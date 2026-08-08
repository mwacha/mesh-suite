import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import TabelasPrecoListView from '@/views/TabelasPrecoListView.vue'
import * as tabelasPrecoApi from '@/api/tabelasPreco'

vi.mock('@/api/tabelasPreco')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/tabelas-preco', name: 'tabelas-preco', component: TabelasPrecoListView },
      { path: '/tabelas-preco/novo', name: 'tabelas-preco-novo', component: { template: '<div />' } },
      { path: '/tabelas-preco/:id/editar', name: 'tabelas-preco-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/tabelas-preco')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(TabelasPrecoListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const tabelaExemplo = {
  id: 'tp-1',
  nome: 'Varejo',
  metodoAjuste: 'AUTOMATICO' as const,
  operacaoAjuste: 'SOMAR' as const,
  tipoValorAjuste: 'REAL' as const,
  valorAjuste: 10,
  inicioVigencia: '2026-01-01',
  terminoVigencia: null,
  ativo: true,
}

describe('TabelasPrecoListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the tabela de preço list', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockResolvedValue({
      content: [tabelaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Varejo')
    expect(wrapper.text()).toContain('Automático · Somar')
  })

  it('shows Manual for tabelas without an automatic rule', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockResolvedValue({
      content: [{ ...tabelaExemplo, metodoAjuste: 'MANUAL', operacaoAjuste: null, tipoValorAjuste: null, valorAjuste: null }],
      totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Manual')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de tabelas de preço.')
  })

  it('navigates to the new-tabela route when the button is clicked', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nova-tabela"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('tabelas-preco-novo')
  })

  it('deletes a tabela after confirmation and reloads the list', async () => {
    vi.mocked(tabelasPrecoApi.listarTabelasPreco).mockResolvedValue({
      content: [tabelaExemplo], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(tabelasPrecoApi.excluirTabelaPreco).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(tabelasPrecoApi.excluirTabelaPreco).toHaveBeenCalledWith('tp-1')
  })
})
