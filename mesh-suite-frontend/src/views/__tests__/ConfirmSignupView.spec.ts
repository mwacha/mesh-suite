import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import ConfirmSignupView from '@/views/ConfirmSignupView.vue'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', async (importOriginal) => {
  const original = await importOriginal<typeof authApi>()
  return { ...original, confirmSignup: vi.fn() }
})

function mountWithRouter(path: string) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/cadastro', name: 'signup', component: { template: '<div />' } },
      { path: '/confirmar-cadastro', name: 'confirm-signup', component: ConfirmSignupView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(ConfirmSignupView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

describe('ConfirmSignupView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('confirms the token from the query string on mount and shows success', async () => {
    vi.mocked(authApi.confirmSignup).mockResolvedValue(undefined)
    const { wrapper } = await mountWithRouter('/confirmar-cadastro?token=abc123')
    await flushPromises()

    expect(authApi.confirmSignup).toHaveBeenCalledWith('abc123')
    expect(wrapper.text()).toContain('confirmada com sucesso')
  })

  it('shows an invalid/expired message on 401', async () => {
    vi.mocked(authApi.confirmSignup).mockRejectedValue({ response: { status: 401 } })
    const { wrapper } = await mountWithRouter('/confirmar-cadastro?token=expirado')
    await flushPromises()

    expect(wrapper.text()).toContain('Link inválido ou expirado')
  })

  it('shows a generic connection error on network failure', async () => {
    vi.mocked(authApi.confirmSignup).mockRejectedValue(new Error('network'))
    const { wrapper } = await mountWithRouter('/confirmar-cadastro?token=abc123')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível conectar')
  })
})
