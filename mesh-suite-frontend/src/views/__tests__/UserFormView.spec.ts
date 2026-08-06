import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import UserFormView from '@/views/UserFormView.vue'
import * as usersApi from '@/api/users'

vi.mock('@/api/users')

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

describe('UserFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows required-field errors when name/email/role/profile/password are missing on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(usersApi.createUser).not.toHaveBeenCalled()
  })

  it('rejects mismatched password confirmation', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('SALES')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('outraSenha1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('As senhas não coincidem')
  })

  it('pre-checks the permission grid from the default matrix when a Perfil is selected', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="profile"]').setValue('VIEWER')
    await wrapper.find('[data-test="profile"]').trigger('change')
    await flushPromises()

    expect((wrapper.find('[data-test="perm-CUSTOMER-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-CREATE"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('includes Compras in the permission grid and pre-checks it for the Admin profile', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="profile"]').setValue('ADMIN')
    await wrapper.find('[data-test="profile"]').trigger('change')

    expect(wrapper.text()).toContain('Compras')
    expect((wrapper.find('[data-test="perm-PURCHASE-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PURCHASE-CREATE"]').element as HTMLInputElement).checked).toBe(true)
  })

  it('includes Contas a Pagar in the permission grid, pre-checked for Admin but without Create/Delete', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="profile"]').setValue('ADMIN')
    await wrapper.find('[data-test="profile"]').trigger('change')

    expect(wrapper.text()).toContain('Contas a Pagar')
    expect((wrapper.find('[data-test="perm-PAYABLE-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PAYABLE-EDIT"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-PAYABLE-CREATE"]').element as HTMLInputElement).checked).toBe(false)
    expect((wrapper.find('[data-test="perm-PAYABLE-DELETE"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('submits the form and navigates to the list on success', async () => {
    vi.mocked(usersApi.createUser).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('SALES')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(usersApi.createUser).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('usuarios')
  })

  it('shows a conflict message on duplicate e-mail (409)', async () => {
    vi.mocked(usersApi.createUser).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('SALES')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um usuário cadastrado com este e-mail')
  })

  it('shows a permission-denied message on 403', async () => {
    vi.mocked(usersApi.createUser).mockRejectedValue({ response: { status: 403 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="name"]').setValue('Carla')
    await wrapper.find('[data-test="email"]').setValue('carla@aurora.com.br')
    await wrapper.find('[data-test="role"]').setValue('SALES_REP')
    await wrapper.find('[data-test="profile"]').setValue('SALES')
    await wrapper.find('[data-test="password"]').setValue('senha1234')
    await wrapper.find('[data-test="confirm-password"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Você não tem permissão para executar esta ação')
  })

  it('loads existing user data in edit mode with blank password fields', async () => {
    vi.mocked(usersApi.getUser).mockResolvedValue({
      id: 'u1', name: 'Carla', email: 'carla@aurora.com.br', phone: '(11) 98888-7777',
      role: 'SALES_REP', profile: 'SALES', active: true,
      permissions: [{ module: 'ORDER', action: 'VIEW' }],
    } as any)

    const { wrapper } = await mountWithRouter('/usuarios/u1/editar')
    await flushPromises()

    expect(usersApi.getUser).toHaveBeenCalledWith('u1')
    expect((wrapper.find('[data-test="name"]').element as HTMLInputElement).value).toBe('Carla')
    expect((wrapper.find('[data-test="password"]').element as HTMLInputElement).value).toBe('')
  })

  it('shows an error message when loading user data fails in edit mode', async () => {
    vi.mocked(usersApi.getUser).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/usuarios/u1/editar')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do usuário.')
  })
})
