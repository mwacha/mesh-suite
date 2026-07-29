import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AppShell from '@/components/AppShell.vue'

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/', name: 'dashboard', component: { template: '<div />' } }],
  })
  return mount(AppShell, {
    props: { title: 'Dashboard' },
    slots: { default: '<p data-test="slot-content">Conteúdo da página</p>' },
    global: { plugins: [router] },
  })
}

describe('AppShell', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders the slot content', () => {
    const wrapper = mountWithRouter()
    expect(wrapper.find('[data-test="slot-content"]').text()).toBe('Conteúdo da página')
  })

  it('renders the sidebar brand and the topbar title', () => {
    const wrapper = mountWithRouter()
    expect(wrapper.text()).toContain('PediMais')
    expect(wrapper.text()).toContain('Dashboard')
  })
})
