import { describe, it, expect, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AppTopbar from '@/components/AppTopbar.vue'
import { useAuthStore } from '@/stores/auth'

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
  const wrapper = mount(AppTopbar, { props: { title: 'Dashboard' }, global: { plugins: [router] } })
  return { router, wrapper }
}

describe('AppTopbar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders the page title', () => {
    const { wrapper } = mountWithRouter()
    expect(wrapper.text()).toContain('Dashboard')
  })

  it('opens and closes the user menu on avatar click', async () => {
    const { wrapper } = mountWithRouter()
    expect(wrapper.find('[data-test="user-dropdown"]').exists()).toBe(false)

    await wrapper.find('[data-test="avatar-button"]').trigger('click')
    expect(wrapper.find('[data-test="user-dropdown"]').exists()).toBe(true)

    await wrapper.find('[data-test="avatar-button"]').trigger('click')
    expect(wrapper.find('[data-test="user-dropdown"]').exists()).toBe(false)
  })

  it('logging out clears the auth store and navigates to /login', async () => {
    const { router, wrapper } = mountWithRouter()
    const authStore = useAuthStore()
    authStore.usuario = { nome: 'Marina Aurora', papel: 'ADMINISTRADOR' }
    authStore.checked = true

    await wrapper.find('[data-test="avatar-button"]').trigger('click')
    await wrapper.find('[data-test="logout"]').trigger('click')
    // router.isReady() only waits for the router's initial navigation, not
    // this click-triggered one -- see the same note in AppSidebar.spec.ts
    // (Task 2). flushPromises() lets the pending router.push() settle first.
    await flushPromises()

    expect(authStore.usuario).toBeNull()
    expect(router.currentRoute.value.name).toBe('login')
  })
})
