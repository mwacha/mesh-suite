import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PermissionsView from '@/views/PermissionsView.vue'
import * as perfisApi from '@/api/permissionProfiles'

vi.mock('@/api/permissionProfiles')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/permissoes', name: 'permissoes', component: PermissionsView },
      { path: '/permissoes/usuarios', name: 'permissoes-usuarios', component: { template: '<div />' } },
      { path: '/usuarios', name: 'usuarios', component: { template: '<div />' } },
    ],
  })
  router.push('/permissoes')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PermissionsView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

describe('PermissionsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
    })
  })

  it('shows Perfis de Permissão by default', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.find('[data-test="tab-perfis"]').classes()).toContain('tab-ativa')
    expect(perfisApi.listPermissionProfiles).toHaveBeenCalled()
  })

  it('navigates to the Usuários e Permissões screen when that tab is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="tab-usuarios"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('permissoes-usuarios')
  })
})
