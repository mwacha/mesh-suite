import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SlideOver from '@/components/SlideOver.vue'

describe('SlideOver', () => {
  it('teleports the title, body slot and (optional) footer slot to the document body', () => {
    const wrapper = mount(SlideOver, {
      props: { title: 'Adicionar produtos à tabela de preços' },
      slots: { default: '<div class="corpo">conteúdo</div>', footer: '<button class="rodape">Concluir</button>' },
      attachTo: document.body,
    })

    expect(document.body.textContent).toContain('Adicionar produtos à tabela de preços')
    expect(document.querySelector('.corpo')).not.toBeNull()
    expect(document.querySelector('.rodape')).not.toBeNull()
    wrapper.unmount()
  })

  it('omits the footer section when no footer slot is given', () => {
    const wrapper = mount(SlideOver, { props: { title: 'X' }, attachTo: document.body })
    expect(document.querySelector('.slide-over-footer')).toBeNull()
    wrapper.unmount()
  })

  it('emits close when the backdrop is clicked', async () => {
    const wrapper = mount(SlideOver, { props: { title: 'X' }, attachTo: document.body })
    const backdrop = document.querySelector('.slide-over-backdrop') as HTMLElement
    backdrop.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })

  it('emits close when the close button is clicked', async () => {
    const wrapper = mount(SlideOver, { props: { title: 'X' }, attachTo: document.body })
    const closeBtn = document.querySelector('[data-test="slide-over-close"]') as HTMLElement
    closeBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
})
