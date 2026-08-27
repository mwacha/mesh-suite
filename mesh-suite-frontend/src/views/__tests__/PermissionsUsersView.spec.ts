import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PermissionsUsersView from '@/views/PermissionsUsersView.vue'
import * as usersApi from '@/api/users'
import * as perfisApi from '@/api/permissionProfiles'

vi.mock('@/api/users')
vi.mock('@/api/permissionProfiles')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/permissoes/usuarios', name: 'permissoes-usuarios', component: PermissionsUsersView },
      { path: '/permissoes', name: 'permissoes', component: { template: '<div />' } },
      { path: '/usuarios/novo', name: 'usuarios-novo', component: { template: '<div />' } },
    ],
  })
  router.push('/permissoes/usuarios')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PermissionsUsersView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const userBase = {
  id: 'u1', name: 'Carla Vendedora', email: 'carla@aurora.com.br', active: true,
  permissionProfileId: 'pp-vendedor', permissionProfileName: 'Vendedor',
}

const perfilVendedor = {
  id: 'pp-vendedor', name: 'Vendedor', description: '', isSystem: true, moduleCount: 4, userCount: 3,
}

function mockUserResponse() {
  vi.mocked(usersApi.getUser).mockResolvedValue({
    id: 'u1', name: 'Carla Vendedora', email: 'carla@aurora.com.br', phone: '',
    role: 'SALES_REP', active: true,
    permissions: [{ module: 'CUSTOMER', action: 'VIEW' }],
    permissionProfileId: 'pp-vendedor', permissionProfileName: 'Vendedor',
  } as any)
}

describe('PermissionsUsersView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usersApi.listUsers).mockResolvedValue({
      content: [userBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(usersApi.getUserCounts).mockResolvedValue({ total: 1, active: 1, inactive: 0 })
    vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
      content: [perfilVendedor], totalElements: 1, totalPages: 1, number: 0, size: 100,
    })
    mockUserResponse()
  })

  it('loads and displays the user list with the Perfil column', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Carla Vendedora')
    expect(wrapper.text()).toContain('Vendedor')
  })

  it('navigates to the new-user form when "+ Novo Usuário" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-usuario"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('usuarios-novo')
  })

  it('opens the Permissões side panel for a user and loads their grants', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-permissoes-u1"]').trigger('click')
    await flushPromises()

    expect(usersApi.getUser).toHaveBeenCalledWith('u1')
    expect(wrapper.find('[data-test="slide-over"]').exists()).toBe(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-CREATE"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('toggling a checkbox in the panel updates its checked state', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-permissoes-u1"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="perm-CUSTOMER-CREATE"]').setValue(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-CREATE"]').element as HTMLInputElement).checked).toBe(true)
  })

  it('applies a selected profile\'s default grants', async () => {
    vi.mocked(perfisApi.getPermissionProfile).mockResolvedValue({
      id: 'pp-vendedor', name: 'Vendedor', description: '', isSystem: true, createdAt: '2026-01-01T00:00:00Z',
      grants: [{ module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }],
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-permissoes-u1"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="detalhe-aplicar-perfil"]').trigger('click')
    await flushPromises()

    expect((wrapper.find('[data-test="perm-ORDER-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-ORDER-CREATE"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-VIEW"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('saves the updated permissions and closes the panel', async () => {
    vi.mocked(usersApi.updateUser).mockResolvedValue({} as any)
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-permissoes-u1"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="perm-CUSTOMER-CREATE"]').setValue(true)
    await wrapper.find('[data-test="detalhe-salvar"]').trigger('click')
    await flushPromises()

    expect(usersApi.updateUser).toHaveBeenCalledWith('u1', expect.objectContaining({
      permissions: expect.arrayContaining([
        { module: 'CUSTOMER', action: 'VIEW' },
        { module: 'CUSTOMER', action: 'CREATE' },
      ]),
      permissionProfileId: 'pp-vendedor',
    }))
    expect(wrapper.find('[data-test="slide-over"]').exists()).toBe(false)
  })

  it('shows an error message when saving the permissions fails', async () => {
    vi.mocked(usersApi.updateUser).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-permissoes-u1"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="detalhe-salvar"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível salvar as permissões deste usuário.')
  })

  it('closes the panel without saving when Fechar is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-permissoes-u1"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-test="detalhe-fechar"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="slide-over"]').exists()).toBe(false)
    expect(usersApi.updateUser).not.toHaveBeenCalled()
  })
})
