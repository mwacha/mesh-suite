import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SearchSelect, { type SearchSelectItem } from '@/components/SearchSelect.vue'

const CIDADES = [
  'São Paulo', 'Campinas', 'Santos', 'Bauru', 'Ribeirão Preto', 'Sorocaba', 'Jundiaí',
  'Osasco', 'Guarulhos', 'São Bernardo do Campo', 'Santo André', 'Mauá', 'Diadema',
]

function items(names = CIDADES): SearchSelectItem[] {
  return names.map((name) => ({ id: name, label: name }))
}

function mountSelect(props: Record<string, unknown> = {}) {
  return mount(SearchSelect, {
    props: { modelValue: null, items: items(), testId: 'cidade', ...props },
    global: { stubs: { teleport: true } },
  })
}

async function open(wrapper: ReturnType<typeof mountSelect>) {
  await wrapper.find('[data-test="cidade"]').trigger('click')
}

function optionLabels(wrapper: ReturnType<typeof mountSelect>) {
  return wrapper.findAll('.search-select-item').map((item) => item.text())
}

describe('SearchSelect', () => {
  it('shows only the first page of options and offers "Exibir mais"', async () => {
    const wrapper = mountSelect()
    await open(wrapper)

    expect(optionLabels(wrapper)).toHaveLength(10)
    expect(optionLabels(wrapper)[0]).toBe('São Paulo')
    expect(wrapper.find('[data-test="cidade-more"]').text()).toContain('Exibir mais')
  })

  it('reveals the next page on "Exibir mais" and hides the button on the last page', async () => {
    const wrapper = mountSelect()
    await open(wrapper)

    await wrapper.find('[data-test="cidade-more"]').trigger('click')

    expect(optionLabels(wrapper)).toHaveLength(CIDADES.length)
    expect(wrapper.find('[data-test="cidade-more"]').exists()).toBe(false)
  })

  it('honours a custom pageSize', async () => {
    const wrapper = mountSelect({ pageSize: 3 })
    await open(wrapper)

    expect(optionLabels(wrapper)).toHaveLength(3)
  })

  it('filters locally ignoring case and accents, and resets paging', async () => {
    const wrapper = mountSelect({ filterLocally: true })
    await open(wrapper)

    await wrapper.find('[data-test="cidade-input"]').setValue('sao')

    // "São Paulo" and "São Bernardo do Campo" both match "sao".
    expect(optionLabels(wrapper)).toEqual(['São Paulo', 'São Bernardo do Campo'])
    expect(wrapper.find('[data-test="cidade-more"]').exists()).toBe(false)
  })

  it('highlights the matched stretch of the label', async () => {
    const wrapper = mountSelect({ filterLocally: true })
    await open(wrapper)

    await wrapper.find('[data-test="cidade-input"]').setValue('san')

    expect(wrapper.find('.search-select-item mark').text()).toBe('San')
  })

  it('shows the empty message when nothing matches', async () => {
    const wrapper = mountSelect({ filterLocally: true, emptyMessage: 'Nenhuma cidade encontrada' })
    await open(wrapper)

    await wrapper.find('[data-test="cidade-input"]').setValue('zzz')

    expect(wrapper.find('.search-select-empty').text()).toBe('Nenhuma cidade encontrada')
  })

  it('emits the picked option and closes the panel', async () => {
    const wrapper = mountSelect()
    await open(wrapper)

    await wrapper.find('[data-test="cidade-option-Santos"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['Santos'])
    expect(wrapper.emitted('select')?.[0]).toEqual([{ id: 'Santos', label: 'Santos' }])
    expect(wrapper.find('.search-select-panel').exists()).toBe(false)
  })

  it('emits open the first time the panel is shown, so the parent can lazy-load', async () => {
    const wrapper = mountSelect()
    await open(wrapper)

    expect(wrapper.emitted('open')).toHaveLength(1)
  })

  it('shows a loading state instead of options while the parent fetches', async () => {
    const wrapper = mountSelect({ items: [], loading: true })
    await open(wrapper)

    expect(wrapper.find('[data-test="cidade-loading"]').text()).toBe('Carregando...')
  })

  it('delegates paging to the parent when hasMore is provided', async () => {
    const wrapper = mountSelect({ items: items(CIDADES.slice(0, 12)), hasMore: true })
    await open(wrapper)

    // Server-paged: the parent owns the window, so nothing is sliced off.
    expect(optionLabels(wrapper)).toHaveLength(12)

    await wrapper.find('[data-test="cidade-more"]').trigger('click')

    expect(wrapper.emitted('load-more')).toHaveLength(1)
  })

  it('selects the keyboard-highlighted option on Enter', async () => {
    const wrapper = mountSelect()
    await open(wrapper)

    const input = wrapper.find('[data-test="cidade-input"]')
    await input.trigger('keydown.down')
    await input.trigger('keydown.enter')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['Campinas'])
  })

  it('renders the selected label on the trigger, falling back to the placeholder', async () => {
    const wrapper = mountSelect({ placeholder: 'Selecione...' })
    expect(wrapper.find('[data-test="cidade"]').text()).toContain('Selecione...')

    await wrapper.setProps({ modelValue: 'Santos', selectedLabel: 'Santos' })
    expect(wrapper.find('[data-test="cidade"]').text()).toContain('Santos')
  })
})
