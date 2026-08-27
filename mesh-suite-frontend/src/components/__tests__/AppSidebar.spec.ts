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
      { path: '/vendas', name: 'vendas', component: { template: '<div />' } },
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

    await wrapper.find('[data-test="group-vendas"]').trigger('click')
    await wrapper.find('[data-test="nav-Pedidos"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/pedidos')
  })

  it('navigates to /vendas when Vendas is clicked', async () => {
    const { router, wrapper } = mountWithRouter()
    await router.push('/outra')

    await wrapper.find('[data-test="group-vendas"]').trigger('click')
    await wrapper.find('[data-test="nav-Vendas"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/vendas')
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

    await wrapper.find('[data-test="group-cadastros"]').trigger('click')
    await wrapper.find('[data-test="nav-Clientes"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/clientes')
    expect(wrapper.find('[data-test="nav-Clientes"]').classes()).toContain('nav-item-active')

    await router.push('/clientes/novo')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="nav-Clientes"]').classes()).toContain('nav-item-active')
  })

  it('navigates to /fornecedores when Fornecedores is clicked', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/fornecedores', name: 'fornecedores', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    await wrapper.find('[data-test="group-cadastros"]').trigger('click')
    await wrapper.find('[data-test="nav-Fornecedores"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/fornecedores')
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

    await wrapper.find('[data-test="group-compras"]').trigger('click')
    await wrapper.find('[data-test="nav-Compras"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/compras')
  })

  it('navigates to /notas-fiscais-entrada when Notas de Entrada is clicked', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/notas-fiscais-entrada', name: 'notas-fiscais-entrada', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    await wrapper.find('[data-test="group-compras"]').trigger('click')
    await wrapper.find('[data-test="nav-Notas de Entrada"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/notas-fiscais-entrada')
  })

  it('lists Formas de Recebimento under VENDAS, not under CADASTROS', async () => {
    const { wrapper } = mountWithRouter()

    await wrapper.find('[data-test="group-cadastros"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Formas de Recebimento"]').exists()).toBe(false)

    // accordion: abrir "vendas" fecha "cadastros"
    await wrapper.find('[data-test="group-vendas"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Formas de Recebimento"]').exists()).toBe(true)
  })

  it('navigates to /formas-recebimento when Formas de Recebimento is clicked', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/formas-recebimento', name: 'formas-recebimento', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    await wrapper.find('[data-test="group-vendas"]').trigger('click')
    await wrapper.find('[data-test="nav-Formas de Recebimento"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/formas-recebimento')
  })

  it('groups nav items under category headers, collapsed by default', () => {
    const { wrapper } = mountWithRouter()

    expect(wrapper.find('[data-test="group-vendas"]').text()).toContain('VENDAS')
    expect(wrapper.find('[data-test="group-catalogo"]').text()).toContain('CATÁLOGO')
    expect(wrapper.find('[data-test="group-cadastros"]').text()).toContain('CADASTROS')
    expect(wrapper.find('[data-test="group-configuracoes"]').text()).toContain('CONFIGURAÇÕES')

    // no route matches the default '/' path inside any group, so every group starts collapsed
    for (const label of ['Pedidos', 'Categorias', 'Fornecedores', 'Transportadoras', 'Cores / Estampas']) {
      expect(wrapper.find(`[data-test="nav-${label}"]`).exists()).toBe(false)
    }
  })

  it('shows inert (not-yet-implemented) items once their group is expanded', async () => {
    const { wrapper } = mountWithRouter()

    // items not yet backed by a screen still show, same route:null/inert
    // pattern used by Empresa/Marcas/Tab. Preços/Permissões.
    await wrapper.find('[data-test="group-catalogo"]').trigger('click')
    for (const label of ['Marcas']) {
      expect(wrapper.find(`[data-test="nav-${label}"]`).exists()).toBe(true)
      expect(wrapper.find(`[data-test="nav-${label}"]`).classes()).toContain('nav-item-inert')
    }
    // Categorias and Cores / Estampas now route to real screens (Task 3),
    // so they're no longer inert. Checked here, while "catalogo" is still
    // the open group -- accordion behavior means opening "cadastros" below
    // would close it again.
    for (const label of ['Categorias', 'Cores / Estampas']) {
      expect(wrapper.find(`[data-test="nav-${label}"]`).exists()).toBe(true)
      expect(wrapper.find(`[data-test="nav-${label}"]`).classes()).not.toContain('nav-item-inert')
    }

    await wrapper.find('[data-test="group-cadastros"]').trigger('click')
    // Fornecedores now routes to a real screen (this task), so it's no longer inert.
    expect(wrapper.find('[data-test="nav-Fornecedores"]').classes()).not.toContain('nav-item-inert')
    for (const label of ['Transportadoras']) {
      expect(wrapper.find(`[data-test="nav-${label}"]`).exists()).toBe(true)
      expect(wrapper.find(`[data-test="nav-${label}"]`).classes()).toContain('nav-item-inert')
    }
  })

  it('expands a single group when its header is clicked, closing any other open group (accordion)', async () => {
    const { wrapper } = mountWithRouter()
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(false)

    await wrapper.find('[data-test="group-vendas"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(true)

    await wrapper.find('[data-test="group-cadastros"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Clientes"]').exists()).toBe(true)
    // opening a different group closes the previously open one
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(false)

    await wrapper.find('[data-test="group-cadastros"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Clientes"]').exists()).toBe(false)
  })

  it('when the sidebar itself is collapsed to icon rail, group items stay visible regardless of group state', async () => {
    const { wrapper } = mountWithRouter()

    // vendas is collapsed by default; the icon rail should still show every item
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(false)

    await wrapper.find('[data-test="collapse-toggle"]').trigger('click')
    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="group-vendas"]').exists()).toBe(false)
  })

  it('expands the group containing the active route by default', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/pedidos', name: 'pedidos', component: { template: '<div />' } },
      ],
    })
    await router.push('/pedidos')
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    expect(wrapper.find('[data-test="nav-Pedidos"]').exists()).toBe(true)
  })
})
