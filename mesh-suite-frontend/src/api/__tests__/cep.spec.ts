import { describe, it, expect, vi, afterEach } from 'vitest'
import { buscarEnderecoPorCep } from '../cep'

describe('buscarEnderecoPorCep', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns the address when ViaCEP finds the CEP', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ logradouro: 'Av. Paulista', bairro: 'Bela Vista', localidade: 'São Paulo', uf: 'SP' }),
    })
    vi.stubGlobal('fetch', mockFetch)

    const endereco = await buscarEnderecoPorCep('01310-100')

    expect(mockFetch).toHaveBeenCalledWith('https://viacep.com.br/ws/01310100/json/')
    expect(endereco).toEqual({ logradouro: 'Av. Paulista', bairro: 'Bela Vista', localidade: 'São Paulo', uf: 'SP' })
  })

  it('returns null when ViaCEP reports the CEP does not exist', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ erro: true }) }))

    const endereco = await buscarEnderecoPorCep('00000000')

    expect(endereco).toBeNull()
  })

  it('returns null for a malformed CEP without calling the API', async () => {
    const mockFetch = vi.fn()
    vi.stubGlobal('fetch', mockFetch)

    const endereco = await buscarEnderecoPorCep('123')

    expect(endereco).toBeNull()
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('returns null when the request fails or the network errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))
    expect(await buscarEnderecoPorCep('01310100')).toBeNull()

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network error')))
    expect(await buscarEnderecoPorCep('01310100')).toBeNull()
  })
})
