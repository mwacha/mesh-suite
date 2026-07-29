import { describe, it, expect, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AppSidebar from '@/components/AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div />' } },
      { path: '/outra', name: 'outra', component: { template: '<div />' } },
    ],
  })
  const wrapper = mount(AppSidebar, { global: { plugins: [router] } })
  return { router, wrapper }
}

describe('AppSidebar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('navigates to / when Home is clicked', async () => {
    const { router, wrapper } = mountWithRouter()
    await router.push('/outra')

    // router.isReady() only ever waits for the router's FIRST navigation --
    // once resolved, later calls return an already-resolved promise, so it
    // does NOT wait for this click's push('/'). flushPromises() drains the
    // microtask queue instead, letting the click handler's router.push()
    // actually settle before the assertion runs.
    await wrapper.find('[data-test="nav-Home"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('does not navigate when an inert item (Pedidos) is clicked', async () => {
    const { router, wrapper } = mountWithRouter()
    await router.push('/outra')

    await wrapper.find('[data-test="nav-Pedidos"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/outra')
  })

  it('toggles collapsed state, hiding the brand name and nav labels', async () => {
    const { wrapper } = mountWithRouter()
    expect(wrapper.text()).toContain('PediMais')
    expect(wrapper.text()).toContain('Home')

    await wrapper.find('[data-test="collapse-toggle"]').trigger('click')

    expect(wrapper.text()).not.toContain('PediMais')
    expect(wrapper.text()).not.toContain('Home')
  })

  it("shows the logged-in user's name and role in the footer", async () => {
    const { wrapper } = mountWithRouter()
    const authStore = useAuthStore()
    authStore.usuario = { nome: 'Marina Aurora', papel: 'ADMINISTRADOR' }
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Marina Aurora')
    expect(wrapper.text()).toContain('ADMINISTRADOR')
  })
})
