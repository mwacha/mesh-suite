import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CollapsibleSection from '@/components/CollapsibleSection.vue'

describe('CollapsibleSection', () => {
  it('shows the title and slot content when open by default', () => {
    const wrapper = mount(CollapsibleSection, {
      props: { title: 'Endereço' },
      slots: { default: '<div data-test="conteudo">CEP</div>' },
    })
    expect(wrapper.text()).toContain('Endereço')
    expect(wrapper.find('[data-test="conteudo"]').exists()).toBe(true)
  })

  it('starts closed when defaultOpen is false', () => {
    const wrapper = mount(CollapsibleSection, {
      props: { title: 'Endereço', defaultOpen: false },
      slots: { default: '<div data-test="conteudo">CEP</div>' },
    })
    expect(wrapper.find('[data-test="conteudo"]').exists()).toBe(false)
  })

  it('toggles the slot content when the header is clicked', async () => {
    const wrapper = mount(CollapsibleSection, {
      props: { title: 'Endereço' },
      slots: { default: '<div data-test="conteudo">CEP</div>' },
    })

    await wrapper.find('[data-test="collapsible-header"]').trigger('click')
    expect(wrapper.find('[data-test="conteudo"]').exists()).toBe(false)

    await wrapper.find('[data-test="collapsible-header"]').trigger('click')
    expect(wrapper.find('[data-test="conteudo"]').exists()).toBe(true)
  })
})
