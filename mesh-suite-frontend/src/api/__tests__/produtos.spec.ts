import { describe, it, expect, vi } from 'vitest'
import { apiClient } from '../client'
import {
  listarProdutos,
  buscarProduto,
  criarProduto,
  atualizarProduto,
  atualizarStatusProduto,
  excluirProduto,
  buscarResumoProdutos,
} from '../produtos'

vi.mock('../client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('api/produtos', () => {
  it('listarProdutos calls GET /produtos with query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 },
    })

    await listarProdutos({ busca: 'camiseta', status: 'ATIVO' })

    expect(apiClient.get).toHaveBeenCalledWith('/produtos', { params: { busca: 'camiseta', status: 'ATIVO' } })
  })

  it('buscarProduto calls GET /produtos/:id', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: {} })
    await buscarProduto('abc-123')
    expect(apiClient.get).toHaveBeenCalledWith('/produtos/abc-123')
  })

  it('criarProduto calls POST /produtos with the payload', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} })
    const payload = { nome: 'Teste' } as any
    await criarProduto(payload)
    expect(apiClient.post).toHaveBeenCalledWith('/produtos', payload)
  })

  it('atualizarProduto calls PUT /produtos/:id with the payload', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} })
    const payload = { nome: 'Teste' } as any
    await atualizarProduto('abc-123', payload)
    expect(apiClient.put).toHaveBeenCalledWith('/produtos/abc-123', payload)
  })

  it('atualizarStatusProduto calls PATCH /produtos/:id/status', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} })
    await atualizarStatusProduto('abc-123', 'INATIVO')
    expect(apiClient.patch).toHaveBeenCalledWith('/produtos/abc-123/status', { status: 'INATIVO' })
  })

  it('excluirProduto calls DELETE /produtos/:id', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ data: {} })
    await excluirProduto('abc-123')
    expect(apiClient.delete).toHaveBeenCalledWith('/produtos/abc-123')
  })

  it('buscarResumoProdutos calls GET /produtos/resumo', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { total: 0, ativos: 0, inativos: 0 } })
    await buscarResumoProdutos()
    expect(apiClient.get).toHaveBeenCalledWith('/produtos/resumo')
  })
})
