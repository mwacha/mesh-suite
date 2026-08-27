import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import PermissionProfileFormView from '@/views/PermissionProfileFormView.vue'
import * as perfisApi from '@/api/permissionProfiles'

vi.mock('@/api/permissionProfiles')

function mountWithRouter(path = '/permissoes/perfis/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/permissoes', name: 'permissoes', component: { template: '<div />' } },
      { path: '/permissoes/perfis/novo', name: 'permissoes-perfis-novo', component: PermissionProfileFormView },
      { path: '/permissoes/perfis/:id/editar', name: 'permissoes-perfis-editar', component: PermissionProfileFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(PermissionProfileFormView, { global: { plugins: [router] } }),
  }))
}

describe('PermissionProfileFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows a required-field error when nome is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nome"]').setValue('')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(perfisApi.createPermissionProfile).not.toHaveBeenCalled()
  })

  it('renders all 9 modules with a checkbox per action', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Estoque')
    expect(wrapper.find('[data-test="perm-STOCK-VIEW"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="perm-PURCHASE_INVOICE-DELETE"]').exists()).toBe(true)
  })

  it('creates a profile with the checked grants and navigates to the list', async () => {
    vi.mocked(perfisApi.createPermissionProfile).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Financeiro')
    await wrapper.find('[data-test="perm-PAYABLE-VIEW"]').setValue(true)
    await wrapper.find('[data-test="perm-PAYABLE-EDIT"]').setValue(true)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(perfisApi.createPermissionProfile).toHaveBeenCalledWith({
      name: 'Financeiro',
      description: '',
      grants: [{ module: 'PAYABLE', action: 'VIEW' }, { module: 'PAYABLE', action: 'EDIT' }],
    })
    expect(router.currentRoute.value.name).toBe('permissoes')
  })

  it('shows a conflict message when the name already exists', async () => {
    vi.mocked(perfisApi.createPermissionProfile).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="nome"]').setValue('Financeiro')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um perfil de permissão cadastrado com este nome.')
  })

  it('loads existing data and pre-checks the right boxes in edit mode', async () => {
    vi.mocked(perfisApi.getPermissionProfile).mockResolvedValue({
      id: 'pp-1', name: 'Gerente', description: 'Gestão', isSystem: true, createdAt: '2026-01-01T00:00:00Z',
      grants: [{ module: 'CUSTOMER', action: 'VIEW' }, { module: 'STOCK', action: 'VIEW' }],
    })
    const { wrapper } = await mountWithRouter('/permissoes/perfis/pp-1/editar')
    await flushPromises()

    expect((wrapper.find('[data-test="nome"]').element as HTMLInputElement).value).toBe('Gerente')
    expect((wrapper.find('[data-test="perm-CUSTOMER-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-STOCK-VIEW"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="perm-CUSTOMER-DELETE"]').element as HTMLInputElement).checked).toBe(false)
  })
})
