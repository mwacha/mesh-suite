import { describe, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ProductPicker from '@/components/ProductPicker.vue'
import type { SellableProductItem } from '@/api/products'

function produto(over: Partial<SellableProductItem> & Pick<SellableProductItem, 'id' | 'sku'>): SellableProductItem {
  return {
    name: 'Camiseta Polo',
    type: 'PRODUCT',
    salePrice: 59.9,
    stockQuantity: 10,
    status: 'ACTIVE',
    size: null,
    colorwayName: null,
    ...over,
  }
}

const items: SellableProductItem[] = [
  produto({ id: 'p1', sku: 'P0012', name: 'Arroz Tio João 5kg', type: 'PRODUCT', salePrice: 24.9 }),
  produto({ id: 'k1', sku: 'K0007', name: 'Cesta Básica Essencial', type: 'PRODUCT_KIT', salePrice: 89.9 }),
  produto({ id: 'v1', sku: 'V0003-P-AZ', name: 'Camiseta Polo — P / Azul', type: 'VARIATION_CHILD', salePrice: 39.9, size: 'P', colorwayName: 'Azul' }),
  produto({ id: 'v2', sku: 'V0003-M-PR', name: 'Camiseta Polo — M / Preto', type: 'VARIATION_CHILD', salePrice: 44.9, size: 'M', colorwayName: 'Preto' }),
]

function mountPicker(props: Partial<InstanceType<typeof ProductPicker>['$props']> = {}) {
  return mount(ProductPicker, {
    props: { items, testId: 'pp', ...props },
    global: { stubs: { teleport: true } },
  })
}

async function open(wrapper: ReturnType<typeof mountPicker>) {
  await wrapper.find('[data-test="pp"]').trigger('click')
  await flushPromises()
}

describe('ProductPicker', () => {
  it('renders sku, name, type badge and price as separate columns, per the wireframe', async () => {
    const wrapper = mountPicker()
    await open(wrapper)

    const row = wrapper.find('[data-test="pp-option-k1"]')
    expect(row.find('.product-picker-sku').text()).toBe('K0007')
    expect(row.find('.product-picker-name').text()).toBe('Cesta Básica Essencial')
    expect(row.find('.status-badge').text()).toBe('Kit')
    expect(row.find('.product-picker-price').text()).toContain('89,90')
  })

  it('badges each orderable type distinctly', async () => {
    const wrapper = mountPicker()
    await open(wrapper)

    expect(wrapper.find('[data-test="pp-option-p1"] .status-badge').text()).toBe('Simples')
    expect(wrapper.find('[data-test="pp-option-k1"] .status-badge').text()).toBe('Kit')
    expect(wrapper.find('[data-test="pp-option-v1"] .status-badge').text()).toBe('Variação')
  })

  it('emits the picked product', async () => {
    const wrapper = mountPicker()
    await open(wrapper)

    await wrapper.find('[data-test="pp-option-v1"]').trigger('click')

    expect(wrapper.emitted('select')?.[0][0]).toMatchObject({ id: 'v1', sku: 'V0003-P-AZ' })
  })

  it('emits search when typing in the panel', async () => {
    const wrapper = mountPicker()
    await open(wrapper)

    await wrapper.find('[data-test="pp-input"]').setValue('polo')

    expect(wrapper.emitted('search')?.at(-1)).toEqual(['polo'])
  })

  it('filters the list down by Tamanho', async () => {
    const wrapper = mountPicker()
    await open(wrapper)

    await wrapper.find('[data-test="pp-filters"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-cat-Tamanho"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-value-P"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-apply"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="pp-option-v1"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pp-option-v2"]').exists()).toBe(false)
    // Rows with no size at all are not "size P", so they drop out too.
    expect(wrapper.find('[data-test="pp-option-p1"]').exists()).toBe(false)
  })

  it('shows an applied filter as a removable chip that restores the full list', async () => {
    const wrapper = mountPicker()
    await open(wrapper)

    await wrapper.find('[data-test="pp-filters"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-cat-Cor"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-value-Azul"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-apply"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="pp-chip-Azul"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pp-option-v2"]').exists()).toBe(false)

    await wrapper.find('[data-test="pp-chip-Azul"] .product-picker-chip-remove').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="pp-option-v2"]').exists()).toBe(true)
  })

  it('narrows one axis by the other so no combination yields an empty list', async () => {
    const wrapper = mountPicker()
    await open(wrapper)

    await wrapper.find('[data-test="pp-filters"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-cat-Cor"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-value-Azul"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-apply"]').trigger('click')
    await flushPromises()

    // Only the Azul row (size P) remains, so Tamanho must no longer offer M.
    await wrapper.find('[data-test="pp-filters"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-cat-Tamanho"]').trigger('click')

    expect(wrapper.find('[data-test="pp-filter-value-P"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pp-filter-value-M"]').exists()).toBe(false)
  })

  it('drops a selected filter value once a new result set no longer offers it', async () => {
    const wrapper = mountPicker()
    await open(wrapper)

    await wrapper.find('[data-test="pp-filters"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-cat-Tamanho"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-value-P"]').trigger('click')
    await wrapper.find('[data-test="pp-filter-apply"]').trigger('click')
    await flushPromises()

    // A fresh search returns rows with no sizes at all -- the stale "P" chip must not
    // survive and silently filter everything away.
    await wrapper.setProps({ items: [produto({ id: 'p9', sku: 'P9999', name: 'Outro' })] })
    await flushPromises()

    expect(wrapper.find('[data-test="pp-chip-P"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="pp-option-p9"]').exists()).toBe(true)
  })

  it('renders a failed lookup distinctly from a genuine no-match', async () => {
    const wrapper = mountPicker({ items: [], emptyMessage: 'Falhou', emptyIsError: true })
    await open(wrapper)

    expect(wrapper.find('.product-picker-empty').classes()).toContain('product-picker-empty-error')
    expect(wrapper.find('.product-picker-empty').text()).toBe('Falhou')
  })
})
