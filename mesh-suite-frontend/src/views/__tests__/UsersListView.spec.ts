import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import UsersListView from '@/views/UsersListView.vue'
import * as usersApi from '@/api/users'

vi.mock('@/api/users')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/usuarios', name: 'usuarios', component: UsersListView },
      { path: '/usuarios/novo', name: 'usuarios-novo', component: { template: '<div />' } },
      { path: '/usuarios/:id/editar', name: 'usuarios-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/usuarios')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(UsersListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const userBase = {
  id: 'u1', name: 'Carla Vendedora', email: 'carla@aurora.com.br', active: true,
  permissionProfileId: 'pp-vendedor', permissionProfileName: 'Vendedor',
}

describe('UsersListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(usersApi.listUsers).mockResolvedValue({
      content: [userBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(usersApi.getUserCounts).mockResolvedValue({ total: 1, active: 1, inactive: 0 })
  })

  it('loads and displays the user list on mount', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Carla Vendedora')
    expect(wrapper.text()).toContain('1 usuários cadastrados')
  })

  it('re-fetches with the search term when the busca field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="busca"]').setValue('carla')
    await flushPromises()

    expect(usersApi.listUsers).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'carla' }))
  })

  it('navigates to the create form when "+ Novo Usuário" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-usuario"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('usuarios-novo')
  })

  it('navigates to the edit form via the Ações menu', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-u1"]').trigger('click')
    await wrapper.find('[data-test="acao-editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('usuarios-editar')
    expect(router.currentRoute.value.params.id).toBe('u1')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-u1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('usuarios-editar')
    expect(router.currentRoute.value.params.id).toBe('u1')
  })

  it('re-fetches with the sort param when a sortable column header is clicked', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="col-nome"]').trigger('click')
    await flushPromises()

    expect(usersApi.listUsers).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'name,asc' }))
  })

  it('has no exclusion item in the Ações menu', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-u1"]').trigger('click')

    expect(wrapper.find('[data-test="acao-excluir"]').exists()).toBe(false)
  })

  it('toggles a user status via the Ações menu', async () => {
    vi.mocked(usersApi.updateUserStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-u1"]').trigger('click')
    await wrapper.find('[data-test="acao-status"]').trigger('click')
    await flushPromises()

    expect(usersApi.updateUserStatus).toHaveBeenCalledWith('u1', false)
  })

  it('shows an error message when loading the user list fails', async () => {
    vi.mocked(usersApi.listUsers).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de usuários.')
  })
})
