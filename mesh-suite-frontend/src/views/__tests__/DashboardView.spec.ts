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

  it('renders the example stat cards and the orders table', () => {
    const { wrapper } = mountWithRouter()

    expect(wrapper.text()).toContain('Pedidos hoje')
    expect(wrapper.text()).toContain('38')
    expect(wrapper.text()).toContain('Faturamento mês')
    expect(wrapper.findAll('tbody tr')).toHaveLength(5)
    expect(wrapper.text()).toContain('Mercado Silva')
  })

  it('renders quick-action buttons and table row links as inert (no click handlers)', () => {
    const { wrapper } = mountWithRouter()

    const novoPedido = wrapper.find('[title="Cadastro de pedidos fora de escopo desta fatia"]')
    expect(novoPedido.exists()).toBe(true)
    expect(novoPedido.attributes('onclick')).toBeUndefined()

    const verPedido = wrapper.find('[title="Detalhe de pedido fora de escopo desta fatia"]')
    expect(verPedido.exists()).toBe(true)
  })
})
