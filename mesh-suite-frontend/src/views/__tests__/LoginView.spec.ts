import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: LoginView },
      { path: '/esqueci-senha', name: 'forgot-password', component: { template: '<div />' } },
    ],
  })
  return mount(LoginView, { global: { plugins: [router] } })
}

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('submits email and senha to the login API', async () => {
    vi.mocked(authApi.login).mockResolvedValue()

    const wrapper = mountWithRouter()
    await wrapper.find('input[type="email"]').setValue('marina@aurora.com.br')
    await wrapper.find('input[type="password"]').setValue('senha123')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()

    expect(authApi.login).toHaveBeenCalledWith({
      email: 'marina@aurora.com.br',
      senha: 'senha123',
      manterConectado: false,
    })
  })

  it('shows the generic error message on 401', async () => {
    vi.mocked(authApi.login).mockRejectedValue({ response: { status: 401 } })

    const wrapper = mountWithRouter()
    await wrapper.find('input[type="email"]').setValue('marina@aurora.com.br')
    await wrapper.find('input[type="password"]').setValue('errada')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('E-mail ou senha inválidos')
  })

  it('shows the rate limit message on 429', async () => {
    vi.mocked(authApi.login).mockRejectedValue({ response: { status: 429 } })

    const wrapper = mountWithRouter()
    await wrapper.find('input[type="email"]').setValue('marina@aurora.com.br')
    await wrapper.find('input[type="password"]').setValue('senha123')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Muitas tentativas')
  })

  it('toggles the senha field between masked and visible text on click', async () => {
    const wrapper = mountWithRouter()

    expect(wrapper.find('input#senha').attributes('type')).toBe('password')

    await wrapper.find('button.toggle-senha').trigger('click')
    expect(wrapper.find('input#senha').attributes('type')).toBe('text')

    await wrapper.find('button.toggle-senha').trigger('click')
    expect(wrapper.find('input#senha').attributes('type')).toBe('password')
  })
})
