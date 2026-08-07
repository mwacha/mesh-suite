import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusBadge from '@/components/StatusBadge.vue'

describe('StatusBadge', () => {
  it('renders the label text', () => {
    const wrapper = mount(StatusBadge, { props: { label: 'Ativo', color: 'green' } })
    expect(wrapper.text()).toBe('Ativo')
  })

  it('applies the color modifier class', () => {
    const wrapper = mount(StatusBadge, { props: { label: 'Bloqueado', color: 'red' } })
    expect(wrapper.classes()).toContain('status-badge-red')
  })

  it('defaults to gray when no color is given', () => {
    const wrapper = mount(StatusBadge, { props: { label: 'N/A' } })
    expect(wrapper.classes()).toContain('status-badge-gray')
  })
})
