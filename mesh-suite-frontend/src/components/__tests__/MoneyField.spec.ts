import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MoneyField from '@/components/MoneyField.vue'

describe('MoneyField', () => {
  it('formats an initial value with 2 decimal places by default', () => {
    const wrapper = mount(MoneyField, { props: { modelValue: 1234.5 } })
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('1.234,50')
  })

  it('shows an empty field for a null value', () => {
    const wrapper = mount(MoneyField, { props: { modelValue: null } })
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('')
  })

  it('masks digits typed as they come in, calculator-style', async () => {
    const wrapper = mount(MoneyField, { props: { modelValue: null } })
    const input = wrapper.find('input')

    await input.setValue('1')
    expect((input.element as HTMLInputElement).value).toBe('0,01')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([0.01])

    await input.setValue('123')
    expect((input.element as HTMLInputElement).value).toBe('1,23')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([1.23])

    await input.setValue('123456')
    expect((input.element as HTMLInputElement).value).toBe('1.234,56')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([1234.56])
  })

  it('emits null when the field is cleared', async () => {
    const wrapper = mount(MoneyField, { props: { modelValue: 10 } })
    await wrapper.find('input').setValue('')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([null])
  })

  it('respects a custom decimalPlaces prop', async () => {
    const wrapper = mount(MoneyField, { props: { modelValue: null, decimalPlaces: 3 } })
    await wrapper.find('input').setValue('1234')
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('1,234')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([1.234])
  })

  it('ignores non-digit characters typed into the field', async () => {
    const wrapper = mount(MoneyField, { props: { modelValue: null } })
    await wrapper.find('input').setValue('abc12de3')
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('1,23')
  })

  it('shows the label, required mark and error message', () => {
    const wrapper = mount(MoneyField, {
      props: { modelValue: null, label: 'Preço de Venda', required: true, error: 'Campo obrigatório' },
    })
    expect(wrapper.text()).toContain('Preço de Venda')
    expect(wrapper.find('.required-mark').exists()).toBe(true)
    expect(wrapper.text()).toContain('Campo obrigatório')
  })

  it('updates the display when modelValue changes externally (e.g. loading existing data)', async () => {
    const wrapper = mount(MoneyField, { props: { modelValue: null } })
    await wrapper.setProps({ modelValue: 59.9 })
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('59,90')
  })

  it('emits blur', async () => {
    const wrapper = mount(MoneyField, { props: { modelValue: null } })
    await wrapper.find('input').trigger('blur')
    expect(wrapper.emitted('blur')).toBeTruthy()
  })
})
