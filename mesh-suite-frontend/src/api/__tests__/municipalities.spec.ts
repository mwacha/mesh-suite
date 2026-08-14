import { describe, it, expect, vi } from 'vitest'
import { apiClient } from '../client'
import { listMunicipalities } from '../municipalities'

vi.mock('../client', () => ({
  apiClient: { get: vi.fn() },
}))

describe('api/municipalities', () => {
  it('listMunicipalities calls GET /municipalities with query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: ['São Paulo', 'Campinas'] })
    const result = await listMunicipalities({ uf: 'SP' })
    expect(apiClient.get).toHaveBeenCalledWith('/municipalities', { params: { uf: 'SP' } })
    expect(result).toEqual(['São Paulo', 'Campinas'])
  })

  it('listMunicipalities works without params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] })
    await listMunicipalities()
    expect(apiClient.get).toHaveBeenCalledWith('/municipalities', { params: {} })
  })
})
