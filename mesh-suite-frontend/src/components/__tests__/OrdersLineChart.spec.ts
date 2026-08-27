import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import OrdersLineChart from '@/components/OrdersLineChart.vue'

const points = [
  { label: '1', count: 2 },
  { label: '2', count: 5 },
  { label: '3', count: 3 },
]

describe('OrdersLineChart', () => {
  it('draws the line and area for the given points', () => {
    const wrapper = mount(OrdersLineChart, { props: { points } })
    expect(wrapper.find('.orders-chart-line').exists()).toBe(true)
    expect(wrapper.find('.orders-chart-area').exists()).toBe(true)
  })

  it('renders an x-axis label for every point', () => {
    const wrapper = mount(OrdersLineChart, { props: { points } })
    expect(wrapper.findAll('.orders-chart-axis-label').length).toBeGreaterThan(0)
    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('3')
  })

  it('shows an empty state when there are no points', () => {
    const wrapper = mount(OrdersLineChart, { props: { points: [] } })
    expect(wrapper.text()).toContain('Sem pedidos no período.')
    expect(wrapper.find('.orders-chart-line').exists()).toBe(false)
  })

  it('shows a hover tooltip on mousemove', async () => {
    const wrapper = mount(OrdersLineChart, { props: { points }, attachTo: document.body })
    const el = wrapper.find('.orders-chart').element as HTMLElement
    el.getBoundingClientRect = () => ({
      left: 0, top: 0, right: 700, bottom: 160, width: 700, height: 160, x: 0, y: 0, toJSON: () => {},
    })

    await wrapper.find('.orders-chart').trigger('mousemove', { clientX: 350, clientY: 80 })

    expect(wrapper.find('.orders-chart-tooltip').exists()).toBe(true)
    wrapper.unmount()
  })
})
