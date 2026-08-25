import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SegmentedControl from '@/components/SegmentedControl.vue'

const options = [
  { value: 'AUTOMATIC', label: 'Automático' },
  { value: 'MANUAL', label: 'Manual' },
]

describe('SegmentedControl', () => {
  it('renders one button per option', () => {
    const wrapper = mount(SegmentedControl, { props: { modelValue: 'AUTOMATIC', options } })
    expect(wrapper.findAll('button')).toHaveLength(2)
  })

  it('marks the option matching modelValue as active', () => {
    const wrapper = mount(SegmentedControl, { props: { modelValue: 'MANUAL', options } })
    const buttons = wrapper.findAll('button')
    expect(buttons[0].classes()).not.toContain('segmented-option-active')
    expect(buttons[1].classes()).toContain('segmented-option-active')
  })

  it('emits update:modelValue with the clicked option value', async () => {
    const wrapper = mount(SegmentedControl, { props: { modelValue: 'AUTOMATIC', options } })
    await wrapper.findAll('button')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['MANUAL'])
  })

  it('disables an individual option marked disabled, independent of the others', () => {
    const wrapper = mount(SegmentedControl, {
      props: { modelValue: 'AUTOMATIC', options: [options[0], { ...options[1], disabled: true }] },
    })
    const buttons = wrapper.findAll('button')
    expect((buttons[0].element as HTMLButtonElement).disabled).toBe(false)
    expect((buttons[1].element as HTMLButtonElement).disabled).toBe(true)
  })

  it('builds data-test as `${testId}-${value}` per option', () => {
    const wrapper = mount(SegmentedControl, { props: { modelValue: 'AUTOMATIC', options, testId: 'metodo' } })
    expect(wrapper.find('[data-test="metodo-AUTOMATIC"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="metodo-MANUAL"]').exists()).toBe(true)
  })
})
