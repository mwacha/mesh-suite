import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ProductTypeSelector from '@/components/ProductTypeSelector.vue'

describe('ProductTypeSelector', () => {
  it('renders the three product type tiles', () => {
    const wrapper = mount(ProductTypeSelector, { props: { modelValue: 'PRODUCT' } })

    expect(wrapper.text()).toContain('Simples')
    expect(wrapper.text()).toContain('Kit')
    expect(wrapper.text()).toContain('Com Variação')
  })

  it('emits update:modelValue when a tile is clicked', async () => {
    const wrapper = mount(ProductTypeSelector, { props: { modelValue: 'PRODUCT' } })

    await wrapper.find('[data-test="tipo-produto-PRODUCT_KIT"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['PRODUCT_KIT']])
  })

  it('does not emit when disabled', async () => {
    const wrapper = mount(ProductTypeSelector, { props: { modelValue: 'PRODUCT', disabled: true } })

    await wrapper.find('[data-test="tipo-produto-PRODUCT_KIT"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
