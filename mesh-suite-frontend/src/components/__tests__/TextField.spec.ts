import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TextField from '@/components/TextField.vue'
import { maskTelefone } from '@/utils/masks'

describe('TextField', () => {
  it('renders the label and required marker', () => {
    const wrapper = mount(TextField, { props: { modelValue: '', label: 'Nome Fantasia', required: true } })
    expect(wrapper.text()).toContain('Nome Fantasia')
    expect(wrapper.text()).toContain('*')
  })

  it('emits update:modelValue on input, unmasked when no mask is given', async () => {
    const wrapper = mount(TextField, { props: { modelValue: '' } })
    await wrapper.find('input').setValue('Mercado Silva')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['Mercado Silva'])
  })

  it('applies the mask function to both the displayed value and the emitted value', async () => {
    const wrapper = mount(TextField, { props: { modelValue: '1133334444', mask: maskTelefone } })
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('(11) 3333-4444')

    await wrapper.find('input').setValue('11933334444')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['(11) 93333-4444'])
  })

  it('shows the error message and applies the error class when error is set', () => {
    const wrapper = mount(TextField, { props: { modelValue: '', error: 'Campo obrigatório' } })
    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(wrapper.find('input').classes()).toContain('input-error')
  })

  it('does not show an error or the error class when error is unset', () => {
    const wrapper = mount(TextField, { props: { modelValue: '' } })
    expect(wrapper.find('.field-error').exists()).toBe(false)
    expect(wrapper.find('input').classes()).not.toContain('input-error')
  })

  it('emits blur', async () => {
    const wrapper = mount(TextField, { props: { modelValue: '' } })
    await wrapper.find('input').trigger('blur')
    expect(wrapper.emitted('blur')).toHaveLength(1)
  })

  it('passes testId through as a data-test attribute', () => {
    const wrapper = mount(TextField, { props: { modelValue: '', testId: 'documento' } })
    expect(wrapper.find('[data-test="documento"]').exists()).toBe(true)
  })

  it('applies maxlength so the browser stops accepting keystrokes at the masked field\'s max length', () => {
    const wrapper = mount(TextField, { props: { modelValue: '', mask: maskTelefone, maxlength: 15 } })
    expect(wrapper.find('input').attributes('maxlength')).toBe('15')
  })

  it('omits maxlength when not given', () => {
    const wrapper = mount(TextField, { props: { modelValue: '' } })
    expect(wrapper.find('input').attributes('maxlength')).toBeUndefined()
  })
})
