import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ToastContainer from '@/components/ToastContainer.vue'
import { useToast } from '@/composables/useToast'

describe('ToastContainer', () => {
  beforeEach(() => {
    const { toasts } = useToast()
    toasts.splice(0, toasts.length)
  })

  it('renders a toast message when showToast is called', async () => {
    const wrapper = mount(ToastContainer, { global: { stubs: { teleport: true } } })
    const { showToast } = useToast()

    showToast('Cliente salvo com sucesso!')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-test="toast-success"]').text()).toBe('Cliente salvo com sucesso!')
    wrapper.unmount()
  })

  it('applies the error style for an error toast', async () => {
    const wrapper = mount(ToastContainer, { global: { stubs: { teleport: true } } })
    const { showToast } = useToast()

    showToast('Não foi possível salvar.', 'error')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-test="toast-error"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('auto-dismisses a toast after its duration', async () => {
    vi.useFakeTimers()
    const wrapper = mount(ToastContainer, { global: { stubs: { teleport: true } } })
    const { showToast } = useToast()

    showToast('Cliente salvo com sucesso!', 'success', 3000)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="toast-success"]').exists()).toBe(true)

    vi.advanceTimersByTime(3000)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="toast-success"]').exists()).toBe(false)

    wrapper.unmount()
    vi.useRealTimers()
  })
})
