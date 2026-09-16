import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import SignupView from '@/views/SignupView.vue'
import * as authApi from '@/api/auth'
import * as cepApi from '@/api/cep'

vi.mock('@/api/auth', async (importOriginal) => {
  const original = await importOriginal<typeof authApi>()
  return { ...original, signup: vi.fn() }
})

vi.mock('@/api/cep', async (importOriginal) => {
  const original = await importOriginal<typeof cepApi>()
  return { ...original, buscarEnderecoPorCep: vi.fn() }
})

function mountWithRouter(path = '/cadastro') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/cadastro', name: 'signup', component: SignupView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(SignupView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

describe('SignupView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows required-field errors when legalName, cnpj, adminName, adminEmail and senha are blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(authApi.signup).not.toHaveBeenCalled()
  })

  it('shows an error when senha and confirmarSenha do not match', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('[data-test="admin-name"]').setValue('Marina')
    await wrapper.find('[data-test="admin-email"]').setValue('marina@aurora.com.br')
    await wrapper.find('[data-test="senha"]').setValue('senha1234')
    await wrapper.find('[data-test="confirmar-senha"]').setValue('outrasenha')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('As senhas não coincidem')
    expect(authApi.signup).not.toHaveBeenCalled()
  })

  it('submits the form and shows the confirmation message on success', async () => {
    vi.mocked(authApi.signup).mockResolvedValue(undefined)
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('[data-test="admin-name"]').setValue('Marina')
    await wrapper.find('[data-test="admin-email"]').setValue('marina@aurora.com.br')
    await wrapper.find('[data-test="senha"]').setValue('senha1234')
    await wrapper.find('[data-test="confirmar-senha"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(authApi.signup).toHaveBeenCalledWith(
      expect.objectContaining({
        legalName: 'Confecção Aurora Ltda',
        cnpj: '11222333000144',
        adminName: 'Marina',
        adminEmail: 'marina@aurora.com.br',
        senha: 'senha1234',
      }),
    )
    expect(wrapper.text()).toContain('Verifique seu e-mail')
  })

  it('shows a specific message on 409 (CNPJ already confirmed)', async () => {
    vi.mocked(authApi.signup).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('[data-test="admin-name"]').setValue('Marina')
    await wrapper.find('[data-test="admin-email"]').setValue('marina@aurora.com.br')
    await wrapper.find('[data-test="senha"]').setValue('senha1234')
    await wrapper.find('[data-test="confirmar-senha"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma conta confirmada com este CNPJ')
  })

  it('shows a rate-limit message on 429', async () => {
    vi.mocked(authApi.signup).mockRejectedValue({ response: { status: 429 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="legal-name"]').setValue('Confecção Aurora Ltda')
    await wrapper.find('[data-test="cnpj"]').setValue('11222333000144')
    await wrapper.find('[data-test="admin-name"]').setValue('Marina')
    await wrapper.find('[data-test="admin-email"]').setValue('marina@aurora.com.br')
    await wrapper.find('[data-test="senha"]').setValue('senha1234')
    await wrapper.find('[data-test="confirmar-senha"]').setValue('senha1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Muitas tentativas')
  })
})
