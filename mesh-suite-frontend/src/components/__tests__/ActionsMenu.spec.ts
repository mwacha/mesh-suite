import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ActionsMenu from '@/components/ActionsMenu.vue'

describe('ActionsMenu', () => {
  it('opens the menu when the trigger is clicked, and shows the given items', async () => {
    const wrapper = mount(ActionsMenu, {
      props: { items: [{ label: 'Editar', action: vi.fn(), testId: 'acao-editar' }] },
      attachTo: document.body,
    })

    expect(document.querySelector('[data-test="acao-editar"]')).toBeNull()

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')

    expect(document.querySelector('[data-test="acao-editar"]')?.textContent).toBe('Editar')
    wrapper.unmount()
  })

  it('calls the item action and closes the menu when an item is clicked', async () => {
    const editar = vi.fn()
    const wrapper = mount(ActionsMenu, {
      props: { items: [{ label: 'Editar', action: editar, testId: 'acao-editar' }] },
      attachTo: document.body,
    })

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    const item = document.querySelector('[data-test="acao-editar"]') as HTMLElement
    item.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(editar).toHaveBeenCalledOnce()
    expect(document.querySelector('[data-test="acao-editar"]')).toBeNull()
    wrapper.unmount()
  })

  it('closes the menu on an outside click', async () => {
    const wrapper = mount(ActionsMenu, {
      props: { items: [{ label: 'Excluir', action: vi.fn(), danger: true, testId: 'acao-excluir' }] },
      attachTo: document.body,
    })

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')
    expect(document.querySelector('[data-test="acao-excluir"]')).not.toBeNull()

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(document.querySelector('[data-test="acao-excluir"]')).toBeNull()
    wrapper.unmount()
  })

  it('applies the danger modifier class to danger items', async () => {
    const wrapper = mount(ActionsMenu, {
      props: { items: [{ label: 'Excluir', action: vi.fn(), danger: true, testId: 'acao-excluir' }] },
      attachTo: document.body,
    })

    await wrapper.find('[data-test="btn-acoes"]').trigger('click')

    expect(document.querySelector('[data-test="acao-excluir"]')?.className).toContain('actions-menu-item-danger')
    wrapper.unmount()
  })
})
