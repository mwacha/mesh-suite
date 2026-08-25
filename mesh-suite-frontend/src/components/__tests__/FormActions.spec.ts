import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FormActions from '@/components/FormActions.vue'

describe('FormActions', () => {
  it('renders the default save label', () => {
    const wrapper = mount(FormActions)
    expect(wrapper.text()).toContain('Salvar')
  })

  it('renders a custom save label', () => {
    const wrapper = mount(FormActions, { props: { saveLabel: 'Salvar Tabela' } })
    expect(wrapper.text()).toContain('Salvar Tabela')
  })

  it('emits cancel when the secondary button is clicked', async () => {
    const wrapper = mount(FormActions)
    await wrapper.find('[data-test="cancelar"]').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('the save button is type=submit so it works inside a parent <form>', () => {
    const wrapper = mount(FormActions)
    expect(wrapper.find('[data-test="salvar"]').attributes('type')).toBe('submit')
  })

  it('disables the save button while saving', () => {
    const wrapper = mount(FormActions, { props: { saving: true } })
    expect((wrapper.find('[data-test="salvar"]').element as HTMLButtonElement).disabled).toBe(true)
  })
})
