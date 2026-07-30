import { describe, it, expect, vi } from 'vitest'
import { apiClient } from '../client'
import {
  listarParceiros,
  buscarParceiro,
  criarParceiro,
  atualizarParceiro,
  atualizarStatusParceiro,
  excluirParceiro,
  buscarResumoParceiros,
} from '../parceiros'

vi.mock('../client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('api/parceiros', () => {
  it('listarParceiros calls GET /parceiros with query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 },
    })

    await listarParceiros({ busca: 'silva', status: 'ATIVO' })

    expect(apiClient.get).toHaveBeenCalledWith('/parceiros', { params: { busca: 'silva', status: 'ATIVO' } })
  })

  it('buscarParceiro calls GET /parceiros/:id', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: {} })
    await buscarParceiro('abc-123')
    expect(apiClient.get).toHaveBeenCalledWith('/parceiros/abc-123')
  })

  it('criarParceiro calls POST /parceiros with the payload', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} })
    const payload = { nomeFantasia: 'Teste' } as any
    await criarParceiro(payload)
    expect(apiClient.post).toHaveBeenCalledWith('/parceiros', payload)
  })

  it('atualizarParceiro calls PUT /parceiros/:id with the payload', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} })
    const payload = { nomeFantasia: 'Teste' } as any
    await atualizarParceiro('abc-123', payload)
    expect(apiClient.put).toHaveBeenCalledWith('/parceiros/abc-123', payload)
  })

  it('atualizarStatusParceiro calls PATCH /parceiros/:id/status', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} })
    await atualizarStatusParceiro('abc-123', 'BLOQUEADO')
    expect(apiClient.patch).toHaveBeenCalledWith('/parceiros/abc-123/status', { status: 'BLOQUEADO' })
  })

  it('excluirParceiro calls DELETE /parceiros/:id', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ data: {} })
    await excluirParceiro('abc-123')
    expect(apiClient.delete).toHaveBeenCalledWith('/parceiros/abc-123')
  })

  it('buscarResumoParceiros calls GET /parceiros/resumo', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { total: 0, ativos: 0, emRisco: 0, bloqueados: 0 } })
    await buscarResumoParceiros()
    expect(apiClient.get).toHaveBeenCalledWith('/parceiros/resumo')
  })
})
