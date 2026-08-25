import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SelectField from '@/components/SelectField.vue'

describe('SelectField', () => {
  it('renders the label and required marker', () => {
    const wrapper = mount(SelectField, {
      props: { modelValue: '', label: 'Arredondamento', required: true },
      slots: { default: '<option value="NO_ROUNDING">Não arredondar</option>' },
    })
    expect(wrapper.text()).toContain('Arredondamento')
    expect(wrapper.text()).toContain('*')
  })

  it('renders the slotted options and reflects modelValue', () => {
    const wrapper = mount(SelectField, {
      props: { modelValue: 'B' },
      slots: { default: '<option value="A">A</option><option value="B">B</option>' },
    })
    expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('B')
  })

  it('emits update:modelValue on change', async () => {
    const wrapper = mount(SelectField, {
      props: { modelValue: 'A' },
      slots: { default: '<option value="A">A</option><option value="B">B</option>' },
    })
    await wrapper.find('select').setValue('B')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['B'])
  })

  it('shows the error message when error is set', () => {
    const wrapper = mount(SelectField, { props: { modelValue: '', error: 'Campo obrigatório' } })
    expect(wrapper.text()).toContain('Campo obrigatório')
  })

  it('passes testId through as a data-test attribute', () => {
    const wrapper = mount(SelectField, { props: { modelValue: '', testId: 'arredondamento' } })
    expect(wrapper.find('[data-test="arredondamento"]').exists()).toBe(true)
  })
})
