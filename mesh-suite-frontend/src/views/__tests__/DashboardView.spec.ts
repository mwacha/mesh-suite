import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import { useAuthStore } from '@/stores/auth'

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/', name: 'dashboard', component: DashboardView }],
  })
  return { router, wrapper: mount(DashboardView, { global: { plugins: [router] } }) }
}

describe('DashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('greets the logged-in user by name', () => {
    const authStore = useAuthStore()
    authStore.usuario = { nome: 'Marina Aurora', papel: 'ADMINISTRADOR' }

    const { wrapper } = mountWithRouter()

    expect(wrapper.text()).toContain('Marina Aurora')
  })

  it('renders inside the app shell (sidebar and topbar present)', () => {
    const { wrapper } = mountWithRouter()

    expect(wrapper.text()).toContain('PediMais')
    expect(wrapper.text()).toContain('Dashboard')
  })
})
