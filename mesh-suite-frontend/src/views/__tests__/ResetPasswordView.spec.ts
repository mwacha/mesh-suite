import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth')

async function mountWithRoute(query: string) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/redefinir-senha', name: 'reset-password', component: ResetPasswordView }],
  })
  // Must be awaited before mount: router.isReady() only resolves the *initial*
  // navigation (jsdom's implicit "/", which doesn't match any route here), not
  // this explicit push -- so `route.query.token` would still be unset by the
  // time the component reads it unless we wait for this navigation itself.
  await router.push('/redefinir-senha' + query)
  return { router, wrapper: mount(ResetPasswordView, { global: { plugins: [router] } }) }
}

describe('ResetPasswordView', () => {
  // vi.mock auto-mocks persist call history across `it` blocks in this file;
  // without a reset, a later test's `not.toHaveBeenCalled()` assertion could
  // pass/fail based on an earlier test's leftover mock calls instead of its own.
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reads token from the query string and submits it with the new password', async () => {
    vi.mocked(authApi.resetPassword).mockResolvedValue()
    const { router, wrapper } = await mountWithRoute('?token=abc123')
    await router.isReady()

    await wrapper.find('input[name="novaSenha"]').setValue('novaSenha123')
    await wrapper.find('input[name="confirmacao"]').setValue('novaSenha123')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()

    expect(authApi.resetPassword).toHaveBeenCalledWith('abc123', 'novaSenha123')
  })

  it('shows an error when confirmation does not match', async () => {
    const { router, wrapper } = await mountWithRoute('?token=abc123')
    await router.isReady()

    await wrapper.find('input[name="novaSenha"]').setValue('novaSenha123')
    await wrapper.find('input[name="confirmacao"]').setValue('diferente')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()

    expect(authApi.resetPassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('não coincidem')
  })

  it('shows the invalid/expired link message on a 401 from the API', async () => {
    vi.mocked(authApi.resetPassword).mockRejectedValue({ response: { status: 401 } })
    const { router, wrapper } = await mountWithRoute('?token=abc123')
    await router.isReady()

    await wrapper.find('input[name="novaSenha"]').setValue('novaSenha123')
    await wrapper.find('input[name="confirmacao"]').setValue('novaSenha123')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Link inválido ou expirado')
  })

  it('shows a generic connectivity message on network failure or unexpected status, not the invalid-link message', async () => {
    vi.mocked(authApi.resetPassword).mockRejectedValue(new Error('Network Error'))
    const { router, wrapper } = await mountWithRoute('?token=abc123')
    await router.isReady()

    await wrapper.find('input[name="novaSenha"]').setValue('novaSenha123')
    await wrapper.find('input[name="confirmacao"]').setValue('novaSenha123')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Não foi possível conectar')
    expect(wrapper.text()).not.toContain('Link inválido ou expirado')
  })
})
