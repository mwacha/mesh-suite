import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PermissionProfilesListView from '@/views/PermissionProfilesListView.vue'
import * as perfisApi from '@/api/permissionProfiles'

vi.mock('@/api/permissionProfiles')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/permissoes', name: 'permissoes', component: PermissionProfilesListView },
      { path: '/permissoes/perfis/novo', name: 'permissoes-perfis-novo', component: { template: '<div />' } },
      { path: '/permissoes/perfis/:id/editar', name: 'permissoes-perfis-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/permissoes')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PermissionProfilesListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const perfilExemplo = {
  id: 'pp-1', name: 'Gerente', description: 'Gestão operacional', isSystem: true, moduleCount: 7, userCount: 5,
}

describe('PermissionProfilesListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads and displays the profile list', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [perfilExemplo], totalElements: 1, totalPages: 1, number: 0, size: 20,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Gerente')
    expect(wrapper.text()).toContain('7 de 9 módulos')
    expect(wrapper.text()).toContain('5')
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de perfis de permissão.')
  })

  it('navigates to the new-profile route when the button is clicked', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
    })
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-perfil"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('permissoes-perfis-novo')
  })

  it('deletes a custom profile after confirmation and reloads the list', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [{ ...perfilExemplo, isSystem: false }], totalElements: 1, totalPages: 1, number: 0, size: 20,
    })
    vi.mocked(perfisApi.deletePermissionProfile).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(perfisApi.deletePermissionProfile).toHaveBeenCalledWith('pp-1')
  })

  it('shows the backend error message when deleting a system profile fails', async () => {
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [perfilExemplo], totalElements: 1, totalPages: 1, number: 0, size: 20,
    })
    vi.mocked(perfisApi.deletePermissionProfile).mockRejectedValue({
      response: { data: { mensagem: 'Não é possível excluir um perfil padrão do sistema' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    await wrapper.find('[data-test="acao-excluir"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não é possível excluir um perfil padrão do sistema')
  })
})
