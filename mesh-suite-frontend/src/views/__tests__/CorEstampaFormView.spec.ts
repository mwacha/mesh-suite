import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CorEstampaFormView from '@/views/CorEstampaFormView.vue'
import * as coresEstampasApi from '@/api/coresEstampas'

vi.mock('@/api/coresEstampas')

function mountWithRouter(path = '/cores-estampas/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/cores-estampas', name: 'cores-estampas', component: { template: '<div />' } },
      { path: '/cores-estampas/novo', name: 'cores-estampas-novo', component: CorEstampaFormView },
      { path: '/cores-estampas/:id/editar', name: 'cores-estampas-editar', component: CorEstampaFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(CorEstampaFormView, { global: { plugins: [router] } }),
  }))
}

describe('CorEstampaFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field errors when nome/dataVigencia are blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(coresEstampasApi.criarCorEstampa).not.toHaveBeenCalled()
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(coresEstampasApi.criarCorEstampa).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Azul Marinho')
    await wrapper.find('[data-test="data-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(coresEstampasApi.criarCorEstampa).toHaveBeenCalledWith(
      expect.objectContaining({ nome: 'Azul Marinho', dataVigencia: '2026-01-01', ativo: true }),
    )
    expect(router.currentRoute.value.name).toBe('cores-estampas')
  })

  it('shows a conflict message on duplicate nome (409)', async () => {
    vi.mocked(coresEstampasApi.criarCorEstampa).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('Azul Marinho')
    await wrapper.find('[data-test="data-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma cor/estampa cadastrada com este nome')
  })

  it('toggles between Ativo and Inativo status', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="status-inativo"]').trigger('click')
    vi.mocked(coresEstampasApi.criarCorEstampa).mockResolvedValue({} as any)
    await wrapper.find('[data-test="nome"]').setValue('Azul Marinho')
    await wrapper.find('[data-test="data-vigencia"]').setValue('2026-01-01')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(coresEstampasApi.criarCorEstampa).toHaveBeenCalledWith(
      expect.objectContaining({ ativo: false }),
    )
  })

  it('loads existing cor/estampa data in edit mode', async () => {
    vi.mocked(coresEstampasApi.buscarCorEstampa).mockResolvedValue({
      id: 'ce-1', nome: 'Azul Marinho', dataVigencia: '2026-01-01', descricao: 'Descrição', ativo: true,
      produtosVinculados: 2, criadoEm: '2026-01-01T00:00:00Z',
    })

    const { wrapper } = await mountWithRouter('/cores-estampas/ce-1/editar')
    await flushPromises()

    expect(coresEstampasApi.buscarCorEstampa).toHaveBeenCalledWith('ce-1')
    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Azul Marinho')
    expect((wrapper.find('[data-test="data-vigencia"]').element as HTMLInputElement).value).toBe('2026-01-01')
  })

  it('shows an error message when loading cor/estampa data fails in edit mode', async () => {
    vi.mocked(coresEstampasApi.buscarCorEstampa).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/cores-estampas/ce-1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados da cor/estampa.')
  })
})
