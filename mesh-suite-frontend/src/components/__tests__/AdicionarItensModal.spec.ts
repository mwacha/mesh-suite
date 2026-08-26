import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AdicionarItensModal from '@/components/AdicionarItensModal.vue'
import * as produtosApi from '@/api/products'

vi.mock('@/api/products')

const produto = { id: 'prod-1', name: 'Camiseta Polo', sku: 'P0001', brand: '', salePrice: 100, stockQuantity: 10, status: 'ACTIVE' as const }

describe('AdicionarItensModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads active products on mount and lists them', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [produto], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    expect(produtosApi.listProducts).toHaveBeenCalledWith(expect.objectContaining({ status: 'ACTIVE', page: 0 }))
    expect(document.body.textContent).toContain('Camiseta Polo')
    wrapper.unmount()
  })

  it('re-queries with the busca term on input', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    const busca = document.querySelector('[data-test="modal-busca"]') as HTMLInputElement
    busca.value = 'Camiseta'
    busca.dispatchEvent(new Event('input'))
    await flushPromises()

    expect(produtosApi.listProducts).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'Camiseta', page: 0 }))
    wrapper.unmount()
  })

  it('shows "+ Adicionar" for a product not yet in the tabela, and emits add when clicked', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
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
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
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
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [produto], totalElements: 5, totalPages: 1, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: ['prod-1', 'prod-2'] }, attachTo: document.body })
    await flushPromises()

    expect(document.body.textContent).toContain('Total')
    expect(document.body.textContent).toContain('Adicionados')
    wrapper.unmount()
  })

  it('emits close when the "Concluir" footer button is clicked', async () => {
    vi.mocked(produtosApi.listProducts).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    })
    const wrapper = mount(AdicionarItensModal, { props: { itensAdicionadosIds: [] }, attachTo: document.body })
    await flushPromises()

    const concluir = document.querySelector('[data-test="modal-concluir"]') as HTMLElement
    concluir.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
})
