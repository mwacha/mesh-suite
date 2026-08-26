import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AdicionarItensModal from '@/components/AdicionarItensModal.vue'
import * as produtosApi from '@/api/products'

vi.mock('@/api/products')

const produto = { id: 'prod-1', name: 'Camiseta Polo', sku: 'P0001', type: 'PRODUCT' as const, salePrice: 100, stockQuantity: 10, status: 'ACTIVE' as const, size: null, colorwayName: null }

describe('AdicionarItensModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads active products on mount and lists them', async () => {
    vi.mocked(produtosApi.listSellableProducts).mockResolvedValue({
      content: [produto], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    expect(produtosApi.listSellableProducts).toHaveBeenCalledWith(expect.objectContaining({ status: 'ACTIVE', page: 0 }))
    expect(document.body.textContent).toContain('Camiseta Polo')
    wrapper.unmount()
  })

  it('re-queries with the busca term on input', async () => {
    vi.mocked(produtosApi.listSellableProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    const busca = document.querySelector('[data-test="modal-busca"]') as HTMLInputElement
    busca.value = 'Camiseta'
    busca.dispatchEvent(new Event('input'))
    await flushPromises()

    expect(produtosApi.listSellableProducts).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'Camiseta', page: 0 }))
    wrapper.unmount()
  })

  it('discards a stale response that resolves after a newer, more specific request', async () => {
    // Regression test: every keystroke fires a new request with no debounce, so an
    // earlier, broader query (e.g. "P") can resolve AFTER a later, narrower one
    // (e.g. "P0001") if the network happens to reorder them. Without a sequence
    // guard, that stale response would clobber the correct, narrower result and
    // the list would look like it "isn't filtering" even though search works.
    let resolveBroad!: (value: unknown) => void
    let resolveNarrow!: (value: unknown) => void
    const broad = new Promise((resolve) => { resolveBroad = resolve })
    const narrow = new Promise((resolve) => { resolveNarrow = resolve })

    vi.mocked(produtosApi.listSellableProducts)
      .mockResolvedValueOnce({ content: [produto], totalElements: 1, totalPages: 1, number: 0, size: 10 })
      .mockReturnValueOnce(broad as any)
      .mockReturnValueOnce(narrow as any)

    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    const busca = document.querySelector('[data-test="modal-busca"]') as HTMLInputElement
    busca.value = 'P'
    busca.dispatchEvent(new Event('input'))
    busca.value = 'P0001'
    busca.dispatchEvent(new Event('input'))

    // The narrower, later request resolves first...
    resolveNarrow({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
    await flushPromises()
    // ...then the stale broader request resolves after it and must be ignored.
    resolveBroad({ content: [produto], totalElements: 1, totalPages: 1, number: 0, size: 10 })
    await flushPromises()

    expect(document.body.textContent).toContain('Nenhum produto encontrado')
    wrapper.unmount()
  })

  it('shows "+ Adicionar" for a product not yet in the tabela, and emits add when clicked', async () => {
    vi.mocked(produtosApi.listSellableProducts).mockResolvedValue({
      content: [produto], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    const botao = document.querySelector('[data-test="modal-adicionar-prod-1"]') as HTMLElement
    expect(botao).not.toBeNull()
    botao.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    expect(wrapper.emitted('add')?.[0]).toEqual([produto])
    wrapper.unmount()
  })

  it('shows "× Remover" for a product already in the tabela, and emits remove when clicked', async () => {
    vi.mocked(produtosApi.listSellableProducts).mockResolvedValue({
      content: [produto], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: ['prod-1'] }, attachTo: document.body })
    await flushPromises()

    const botao = document.querySelector('[data-test="modal-remover-prod-1"]') as HTMLElement
    expect(botao).not.toBeNull()
    botao.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    expect(wrapper.emitted('remove')?.[0]).toEqual(['prod-1'])
    wrapper.unmount()
  })

  it('shows Total and Adicionados stat pills', async () => {
    vi.mocked(produtosApi.listSellableProducts).mockResolvedValue({
      content: [produto], totalElements: 5, totalPages: 1, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: ['prod-1', 'prod-2'] }, attachTo: document.body })
    await flushPromises()

    expect(document.body.textContent).toContain('Total')
    expect(document.body.textContent).toContain('Adicionados')
    wrapper.unmount()
  })

  it('emits close when the "Concluir" footer button is clicked', async () => {
    vi.mocked(produtosApi.listSellableProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    const concluir = document.querySelector('[data-test="modal-concluir"]') as HTMLElement
    concluir.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
  it('lists every sellable type by default, badging each row by kind', async () => {
    const kit = { ...produto, id: 'kit-1', sku: 'KIT001', name: 'Cesta', type: 'PRODUCT_KIT' as const }
    const filho = { ...produto, id: 'var-1', sku: 'V1-P', name: 'Polo P', type: 'VARIATION_CHILD' as const }
    vi.mocked(produtosApi.listSellableProducts).mockResolvedValue({
      content: [produto, kit, filho], totalElements: 3, totalPages: 1, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    // No types prop -> the API is asked for the full sellable set.
    expect(produtosApi.listSellableProducts).toHaveBeenCalledWith(
      expect.objectContaining({ types: undefined }),
    )
    const badges = [...document.querySelectorAll('.status-badge')].map((b) => b.textContent?.trim())
    expect(badges).toEqual(['Simples', 'Kit', 'Variação'])
    wrapper.unmount()
  })

  it('forwards a narrowed type set so a Kit composer never sees kits', async () => {
    vi.mocked(produtosApi.listSellableProducts).mockResolvedValue({
      content: [produto], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, {
      props: { itensAdicionadosIds: [], types: ['PRODUCT', 'VARIATION_CHILD'] },
      attachTo: document.body,
    })
    await flushPromises()

    expect(produtosApi.listSellableProducts).toHaveBeenCalledWith(
      expect.objectContaining({ types: ['PRODUCT', 'VARIATION_CHILD'] }),
    )
    wrapper.unmount()
  })

  it('says so when the lookup fails instead of looking like an empty result', async () => {
    vi.mocked(produtosApi.listSellableProducts).mockRejectedValue(new Error('network'))
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    const erro = document.querySelector('[data-test="modal-erro"]')
    expect(erro).not.toBeNull()
    expect(erro?.textContent).toContain('Não foi possível carregar')
    expect(document.querySelector('[data-test="modal-row-prod-1"]')).toBeNull()
    wrapper.unmount()
  })
})
