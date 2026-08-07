import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Pagination from '@/components/Pagination.vue'

describe('Pagination', () => {
  it('renders the range label and numbered pages for a small total', () => {
    const wrapper = mount(Pagination, {
      props: { number: 0, totalPages: 3, totalElements: 25, size: 10 },
    })
    expect(wrapper.find('[data-test="pagination-range"]').text()).toBe('1–10 de 25')
    expect(wrapper.find('[data-test="pagination-page-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pagination-page-3"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pagination-page-1"]').classes()).toContain('pagination-btn-active')
  })

  it('disables prev on the first page and next on the last page', () => {
    const first = mount(Pagination, { props: { number: 0, totalPages: 3, totalElements: 25, size: 10 } })
    expect((first.find('[data-test="pagination-prev"]').element as HTMLButtonElement).disabled).toBe(true)
    expect((first.find('[data-test="pagination-next"]').element as HTMLButtonElement).disabled).toBe(false)

    const last = mount(Pagination, { props: { number: 2, totalPages: 3, totalElements: 25, size: 10 } })
    expect((last.find('[data-test="pagination-next"]').element as HTMLButtonElement).disabled).toBe(true)
  })

  it('emits update:page when a page button, prev, or next is clicked', async () => {
    const wrapper = mount(Pagination, { props: { number: 1, totalPages: 5, totalElements: 50, size: 10 } })

    await wrapper.find('[data-test="pagination-page-1"]').trigger('click')
    expect(wrapper.emitted('update:page')?.[0]).toEqual([0])

    await wrapper.find('[data-test="pagination-next"]').trigger('click')
    expect(wrapper.emitted('update:page')?.[1]).toEqual([2])

    await wrapper.find('[data-test="pagination-prev"]').trigger('click')
    expect(wrapper.emitted('update:page')?.[2]).toEqual([0])
  })

  it('emits update:size when the page-size selector changes', async () => {
    const wrapper = mount(Pagination, { props: { number: 0, totalPages: 3, totalElements: 25, size: 10 } })
    await wrapper.find('[data-test="pagination-size"]').setValue('50')
    expect(wrapper.emitted('update:size')?.[0]).toEqual([50])
  })

  it('collapses a large page count with ellipses around the current page', () => {
    const wrapper = mount(Pagination, { props: { number: 60, totalPages: 124, totalElements: 1240, size: 10 } })
    expect(wrapper.find('[data-test="pagination-page-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pagination-page-124"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pagination-page-61"]').exists()).toBe(true)
    expect(wrapper.findAll('.pagination-ellipsis').length).toBe(2)
    expect(wrapper.find('[data-test="pagination-page-2"]').exists()).toBe(false)
  })

  it('jumps to the typed page, clamped to the valid range', async () => {
    const wrapper = mount(Pagination, { props: { number: 0, totalPages: 5, totalElements: 50, size: 10 } })

    await wrapper.find('[data-test="pagination-jump-input"]').setValue(3)
    expect(wrapper.emitted('update:page')?.[0]).toEqual([2])

    await wrapper.find('[data-test="pagination-jump-input"]').setValue(999)
    expect(wrapper.emitted('update:page')?.[1]).toEqual([4])
  })
})
