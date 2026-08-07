import { describe, it, expect, vi } from 'vitest'
import { apiClient } from '../client'
import { listarMunicipios } from '../municipios'

vi.mock('../client', () => ({
  apiClient: { get: vi.fn() },
}))

describe('api/municipios', () => {
  it('listarMunicipios calls GET /municipios with query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: ['São Paulo', 'Campinas'] })
    const result = await listarMunicipios({ uf: 'SP' })
    expect(apiClient.get).toHaveBeenCalledWith('/municipios', { params: { uf: 'SP' } })
    expect(result).toEqual(['São Paulo', 'Campinas'])
  })

  it('listarMunicipios works without params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] })
    await listarMunicipios()
    expect(apiClient.get).toHaveBeenCalledWith('/municipios', { params: {} })
  })
})
