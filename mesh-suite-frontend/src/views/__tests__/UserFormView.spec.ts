import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import UserFormView from '@/views/UserFormView.vue'
import * as usersApi from '@/api/users'
import * as perfisApi from '@/api/permissionProfiles'

vi.mock('@/api/users')
vi.mock('@/api/permissionProfiles')

function mountWithRouter(path = '/usuarios/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/usuarios', name: 'usuarios', component: { template: '<div />' } },
      { path: '/usuarios/novo', name: 'usuarios-novo', component: UserFormView },
      { path: '/usuarios/:id/editar', name: 'usuarios-editar', component: UserFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(UserFormView, { global: { plugins: [router] } }),
  }))
}

const perfilAdmin = {
  id: 'pp-admin', name: 'Admin', description: '', isSystem: true, moduleCount: 9, userCount: 1,
}
const perfilVendedor = {
  id: 'pp-vendedor', name: 'Vendedor', description: '', isSystem: true, moduleCount: 4, userCount: 5,
}

function mockPerfis() {
  vi.mocked(perfisApi.listPermissionProfiles).mockResolvedValue({
    content: [perfilAdmin, perfilVendedor], totalElements: 2, totalPages: 1, number: 0, size: 100,
  })
  vi.mocked(perfisApi.getPermissionProfile).mockImplementation(async (id: string) => {
    if (id === 'pp-admin') {
      return {
        id: 'pp-admin', name: 'Admin', description: '', isSystem: true, createdAt: '2026-01-01T00:00:00Z',
        grants: [
          { module: 'PURCHASE', action: 'VIEW' }, { module: 'PURCHASE', action: 'CREATE' },
          { module: 'PAYABLE', action: 'VIEW' }, { module: 'PAYABLE', action: 'EDIT' },
        ],
      }
    }
    return {
      id: 'pp-vendedor', name: 'Vendedor', description: '', isSystem: true, createdAt: '2026-01-01T00:00:00Z',
      grants: [{ module: 'CUSTOMER', action: 'VIEW' }],
    }
  })
}

describe('UserFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockPerfis()
  })

  it('shows required-field errors when name/email/role/password are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(usersApi.createUser).not.toHaveBeenCalled()
  })

  it('rejects mismatched password confirmation', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('outraSenha1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('As senhas não coincidem')
  })

  it('lists the profiles fetched from the API in the Perfil de Acesso select', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Admin')
    expect(wrapper.text()).toContain('Vendedor')
  })

  it('pre-checks the permission grid from the selected profile', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="profile"]').trigger('change')
    await flushPromises()

    expect((wrapper.find('[data-test="perm-CUSTOMER-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-CREATE"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('includes Compras e Contas a Pagar in the grid, pre-checked for the Admin profile', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="profile"]').setValue('pp-admin')
    await wrapper.find('[data-test="profile"]').trigger('change')
    await flushPromises()

    expect(wrapper.text()).toContain('Compras')
    expect(wrapper.text()).toContain('Contas a Pagar')
    expect((wrapper.find('[data-test="perm-PURCHASE-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PAYABLE-EDIT"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PAYABLE-CREATE"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('submits the form with the chosen permissionProfileId and navigates to the list on success', async () => {
    vi.mocked(usersApi.createUser).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="profile"]').trigger('change')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await flushPromises()
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(usersApi.createUser).toHaveBeenCalledWith(expect.objectContaining({ permissionProfileId: 'pp-vendedor' }))
    expect(router.currentRoute.value.name).toBe('usuarios')
  })

  it('shows a conflict message on duplicate e-mail (409)', async () => {
    vi.mocked(usersApi.createUser).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um usuário cadastrado com este e-mail')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(usersApi.createUser).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('pp-vendedor')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing user data in edit mode with blank password fields and the right profile selected', async () => {
    vi.mocked(usersApi.getUser).mockResolvedValue({
      id: 'u1', name: 'Carla', email: 'carla@aurora.com.br', phone: '(11) 98888-7777',
      role: 'SALES_REP', active: true,
      permissions: [{ module: 'ORDER', action: 'VIEW' }],
      permissionProfileId: 'pp-vendedor', permissionProfileName: 'Vendedor',
    } as any)

    const { wrapper } = await mountWithRouter('/usuarios/u1/editar')
    await flushPromises()

    expect(usersApi.getUser).toHaveBeenCalledWith('u1')
    expect((wrapper.find('[data-test="name"]').element as HTMLInputElement).value).toBe('Carla')
    expect((wrapper.find('[data-test="password"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.find('[data-test="profile"]').element as HTMLInputElement).value).toBe('pp-vendedor')
  })

  it('shows an error message when loading user data fails in edit mode', async () => {
    vi.mocked(usersApi.getUser).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/usuarios/u1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do usuário.')
  })
})
