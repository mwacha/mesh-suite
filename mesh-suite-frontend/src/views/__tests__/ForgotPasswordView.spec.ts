import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth')

describe('ForgotPasswordView', () => {
  it('shows the generic success message after submit, regardless of API result', async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValue()

    const wrapper = mount(ForgotPasswordView)
    await wrapper.find('input[type="email"]').setValue('marina@aurora.com.br')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()

    expect(authApi.forgotPassword).toHaveBeenCalledWith('marina@aurora.com.br')
    expect(wrapper.text()).toContain('se o e-mail existir')
  })

  it('shows the same generic success message even when the API call fails', async () => {
    vi.mocked(authApi.forgotPassword).mockRejectedValue(new Error('Network Error'))

    const wrapper = mount(ForgotPasswordView)
    await wrapper.find('input[type="email"]').setValue('marina@aurora.com.br')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('se o e-mail existir')
  })
})
