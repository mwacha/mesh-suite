import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StatPill from '@/components/StatPill.vue'

describe('StatPill', () => {
  it('renders the value and label', () => {
    const wrapper = mount(StatPill, { props: { value: 1240, label: 'Total' } })
    expect(wrapper.text()).toContain('1240')
    expect(wrapper.text()).toContain('Total')
  })

  it('applies the color modifier class', () => {
    const wrapper = mount(StatPill, { props: { value: 18, label: 'Em Risco', color: 'amber' } })
    expect(wrapper.classes()).toContain('stat-pill-amber')
  })

  it('defaults to dark when no color is given', () => {
    const wrapper = mount(StatPill, { props: { value: 1, label: 'X' } })
    expect(wrapper.classes()).toContain('stat-pill-dark')
  })
})
