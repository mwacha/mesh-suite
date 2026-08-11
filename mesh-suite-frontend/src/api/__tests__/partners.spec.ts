import { describe, it, expect, vi } from 'vitest'
import { apiClient } from '../client'
import {
  listPartners,
  getPartner,
  createPartner,
  updatePartner,
  updatePartnerStatus,
  deletePartner,
  getPartnerSummary,
} from '../partners'

vi.mock('../client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('api/partners', () => {
  it('listPartners calls GET /partners with query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 },
    })

    await listPartners({ busca: 'silva', status: ['ACTIVE'] })

    expect(apiClient.get).toHaveBeenCalledWith('/partners', { params: { busca: 'silva', status: ['ACTIVE'] } })
  })

  it('getPartner calls GET /partners/:id', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: {} })
    await getPartner('abc-123')
    expect(apiClient.get).toHaveBeenCalledWith('/partners/abc-123')
  })

  it('createPartner calls POST /partners with the payload', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} })
    const payload = { tradeName: 'Teste' } as any
    await createPartner(payload)
    expect(apiClient.post).toHaveBeenCalledWith('/partners', payload)
  })

  it('updatePartner calls PUT /partners/:id with the payload', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} })
    const payload = { tradeName: 'Teste' } as any
    await updatePartner('abc-123', payload)
    expect(apiClient.put).toHaveBeenCalledWith('/partners/abc-123', payload)
  })

  it('updatePartnerStatus calls PATCH /partners/:id/status', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} })
    await updatePartnerStatus('abc-123', 'BLOCKED')
    expect(apiClient.patch).toHaveBeenCalledWith('/partners/abc-123/status', { status: 'BLOCKED' })
  })

  it('deletePartner calls DELETE /partners/:id', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ data: {} })
    await deletePartner('abc-123')
    expect(apiClient.delete).toHaveBeenCalledWith('/partners/abc-123')
  })

  it('getPartnerSummary calls GET /partners/summary', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { total: 0, active: 0, atRisk: 0, blocked: 0 } })
    await getPartnerSummary()
    expect(apiClient.get).toHaveBeenCalledWith('/partners/summary', { params: { papel: undefined } })
  })

  it('getPartnerSummary scopes by papel when given', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { total: 0, active: 0, atRisk: 0, blocked: 0 } })
    await getPartnerSummary('CUSTOMER')
    expect(apiClient.get).toHaveBeenCalledWith('/partners/summary', { params: { papel: 'CUSTOMER' } })
  })
})
