import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FilterBar from '@/components/FilterBar.vue'

function mountBar() {
  return mount(FilterBar, {
    props: {
      search: '',
      searchPlaceholder: 'Buscar cliente por nome...',
      categories: ['Status', 'UF'],
      valueMap: { Status: ['Ativo', 'Bloqueado'], UF: ['SP', 'RJ'] },
    },
    attachTo: document.body,
  })
}

describe('FilterBar', () => {
  it('emits update:search as the user types', async () => {
    const wrapper = mountBar()
    await wrapper.find('[data-test="filter-bar-search"]').setValue('Silva')
    expect(wrapper.emitted('update:search')?.[0]).toEqual(['Silva'])
    wrapper.unmount()
  })

  it('opens the category menu, then the value list, and applies a multi-value selection', async () => {
    const wrapper = mountBar()

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    expect(wrapper.find('[data-test="filter-cat-Status"]').exists()).toBe(true)

    await wrapper.find('[data-test="filter-cat-Status"]').trigger('click')
    expect(wrapper.find('[data-test="filter-value-Ativo"]').exists()).toBe(true)

    await wrapper.find('[data-test="filter-value-Ativo"]').trigger('click')
    await wrapper.find('[data-test="filter-value-Bloqueado"]').trigger('click')
    await wrapper.find('[data-test="filter-bar-apply"]').trigger('click')

    expect(wrapper.emitted('update:filters')?.[0]).toEqual([{ Status: ['Ativo', 'Bloqueado'] }])
    // panel closes after applying
    expect(wrapper.find('[data-test="filter-bar-values"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows a chip per applied filter and removes it individually', async () => {
    const wrapper = mountBar()
    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Status"]').trigger('click')
    await wrapper.find('[data-test="filter-value-Ativo"]').trigger('click')
    await wrapper.find('[data-test="filter-bar-apply"]').trigger('click')

    expect(wrapper.text()).toContain('Status:')
    expect(wrapper.text()).toContain('Ativo')

    await wrapper.find('[data-test="filter-chip-remove-Status"]').trigger('click')
    expect(wrapper.emitted('update:filters')?.at(-1)).toEqual([{}])
    expect(wrapper.text()).not.toContain('Status:')
    wrapper.unmount()
  })

  it('clears all applied filters via "Limpar filtros"', async () => {
    const wrapper = mountBar()
    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-UF"]').trigger('click')
    await wrapper.find('[data-test="filter-value-SP"]').trigger('click')
    await wrapper.find('[data-test="filter-bar-apply"]').trigger('click')

    await wrapper.find('[data-test="filter-bar-clear"]').trigger('click')

    expect(wrapper.emitted('update:filters')?.at(-1)).toEqual([{}])
    expect(wrapper.find('.filter-bar-chips').exists()).toBe(false)
    wrapper.unmount()
  })

  it('filters the value list ignoring case and accents', async () => {
    const wrapper = mount(FilterBar, {
      props: {
        search: '',
        categories: ['Cidade'],
        valueMap: { Cidade: ['São Paulo', 'Ribeirão Preto', 'Campinas'] },
      },
      attachTo: document.body,
    })

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Cidade"]').trigger('click')

    await wrapper.find('.filter-bar-values-search').setValue('sao pau')
    expect(wrapper.find('[data-test="filter-value-São Paulo"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="filter-value-Campinas"]').exists()).toBe(false)

    await wrapper.find('.filter-bar-values-search').setValue('RIBEIRAO')
    expect(wrapper.find('[data-test="filter-value-Ribeirão Preto"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders the custom-panel slot for a customCategory instead of the checkbox value list', async () => {
    const wrapper = mount(FilterBar, {
      props: {
        search: '',
        categories: ['Status', 'Nr. Documento'],
        valueMap: { Status: ['Ativo'] },
        customCategories: ['Nr. Documento'],
      },
      attachTo: document.body,
      slots: {
        'custom-panel': `
          <template #default="{ category, apply }">
            <div data-test="custom-slot-category">{{ category }}</div>
            <button data-test="custom-slot-apply" @click="apply('CNPJ: 12345678000199')">Aplicar</button>
          </template>
        `,
      },
    })

    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    await wrapper.find('[data-test="filter-cat-Nr. Documento"]').trigger('click')

    expect(wrapper.find('[data-test="filter-bar-values"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="custom-slot-category"]').text()).toBe('Nr. Documento')

    await wrapper.find('[data-test="custom-slot-apply"]').trigger('click')

    expect(wrapper.emitted('update:filters')?.[0]).toEqual([{ 'Nr. Documento': ['CNPJ: 12345678000199'] }])
    expect(wrapper.find('[data-test="filter-bar-custom"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('closes the open panel on an outside click', async () => {
    const wrapper = mountBar()
    await wrapper.find('[data-test="filter-bar-more"]').trigger('click')
    expect(wrapper.find('[data-test="filter-bar-menu"]').exists()).toBe(true)

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-test="filter-bar-menu"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
