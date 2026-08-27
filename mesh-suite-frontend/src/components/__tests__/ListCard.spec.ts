import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ListCard from '@/components/ListCard.vue'

describe('ListCard', () => {
  it('renders the title and the default slot', () => {
    const wrapper = mount(ListCard, {
      props: { title: 'Lista de Clientes' },
      slots: { default: '<div class="conteudo">linhas</div>' },
    })
    expect(wrapper.text()).toContain('Lista de Clientes')
    expect(wrapper.find('.conteudo').exists()).toBe(true)
  })

  it('renders a StatPill per stat when stats are given', () => {
    const wrapper = mount(ListCard, {
      props: {
        title: 'Lista',
        stats: [
          { value: 10, label: 'Total', color: 'dark' },
          { value: 8, label: 'Ativos', color: 'green' },
        ],
      },
    })
    expect(wrapper.text()).toContain('Total')
    expect(wrapper.text()).toContain('Ativos')
    expect(wrapper.findAll('.stat-pill')).toHaveLength(2)
  })

  it('renders no stat pills when stats is omitted', () => {
    const wrapper = mount(ListCard, { props: { title: 'Lista' } })
    expect(wrapper.find('.list-card-stats').exists()).toBe(false)
  })
})
