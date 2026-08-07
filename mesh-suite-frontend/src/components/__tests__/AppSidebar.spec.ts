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
      { path: '/pedidos', name: 'pedidos', component: { template: '<div />' } },
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

  it('navigates to /pedidos when Pedidos is clicked', async () => {
    const { router, wrapper } = mountWithRouter()
    await router.push('/outra')

    await wrapper.find('[data-test="nav-Pedidos"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/pedidos')
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

  it('navigates to /clientes when Clientes is clicked, and highlights it from a sub-route', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/clientes', name: 'clientes', component: { template: '<div />' } },
        { path: '/clientes/novo', name: 'clientes-novo', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    await wrapper.find('[data-test="nav-Clientes"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/clientes')
    expect(wrapper.find('[data-test="nav-Clientes"]').classes()).toContain('nav-item-active')

    await router.push('/clientes/novo')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="nav-Clientes"]').classes()).toContain('nav-item-active')
  })

  it('navigates to /compras when Compras is clicked', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/compras', name: 'compras', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    await wrapper.find('[data-test="nav-Compras"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/compras')
  })

  it('groups nav items under category headers, all expanded by default', () => {
    const { wrapper } = mountWithRouter()

    expect(wrapper.find('[data-test="group-vendas"]').text()).toContain('VENDAS')
    expect(wrapper.find('[data-test="group-catalogo"]').text()).toContain('CATÁLOGO')
    expect(wrapper.find('[data-test="group-cadastros"]').text()).toContain('CADASTROS')
    expect(wrapper.find('[data-test="group-configuracoes"]').text()).toContain('CONFIGURAÇÕES')

    // items not yet backed by a screen still show, same route:null/inert
    // pattern used by Empresa/Marcas/Tab. Preços/Permissões.
    for (const label of ['Fornecedores', 'Transportadoras', 'Cores / Estampas']) {
      expect(wrapper.find(`[data-test="nav-${label}"]`).exists()).toBe(true)
      expect(wrapper.find(`[data-test="nav-${label}"]`).classes()).toContain('nav-item-inert')
    }

    // Categorias now routes to /categorias (Task 3), so it's no longer inert.
    expect(wrapper.find('[data-test="nav-Categorias"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="nav-Categorias"]').classes()).not.toContain('nav-item-inert')
  })

  it('collapses and expands a single group when its header is clicked', async () => {
    const { wrapper } = mountWithRouter()
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(true)

    await wrapper.find('[data-test="group-vendas"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(false)
    // other groups stay untouched
    expect(wrapper.find('[data-test="nav-Clientes"]').exists()).toBe(true)

    await wrapper.find('[data-test="group-vendas"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(true)
  })

  it('toggles all groups at once via "Recolher tudo" / "Expandir tudo"', async () => {
    const { wrapper } = mountWithRouter()
    expect(wrapper.find('[data-test="toggle-all-groups"]').text()).toBe('Recolher tudo')

    await wrapper.find('[data-test="toggle-all-groups"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="nav-Clientes"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="toggle-all-groups"]').text()).toBe('Expandir tudo')

    await wrapper.find('[data-test="toggle-all-groups"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="nav-Clientes"]').exists()).toBe(true)
  })

  it('when the sidebar itself is collapsed to icon rail, group items stay visible regardless of group state', async () => {
    const { wrapper } = mountWithRouter()

    await wrapper.find('[data-test="group-vendas"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(false)

    await wrapper.find('[data-test="collapse-toggle"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="group-vendas"]').exists()).toBe(false)
  })
})
