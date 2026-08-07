import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PageHeader from '@/components/PageHeader.vue'

describe('PageHeader', () => {
  it('renders the title', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Clientes' } })
    expect(wrapper.text()).toContain('Clientes')
  })

  it('renders the count subtitle when given', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Clientes', count: '1.240 clientes cadastrados' } })
    expect(wrapper.text()).toContain('1.240 clientes cadastrados')
  })

  it('omits the count subtitle when not given', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Clientes' } })
    expect(wrapper.find('.page-header-count').exists()).toBe(false)
  })

  it('renders slot content in the actions area', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Clientes' },
      slots: { default: '<button data-test="novo">+ Novo Cliente</button>' },
    })
    expect(wrapper.find('[data-test="novo"]').exists()).toBe(true)
  })
})
