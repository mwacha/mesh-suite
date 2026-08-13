import { describe, it, expect, vi } from 'vitest'
import { apiClient } from '../client'
import {
  listProducts,
  getProduct,
  createProduct,
  updateProduct,
  updateProductStatus,
  deleteProduct,
  getProductSummary,
} from '../products'

vi.mock('../client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('api/products', () => {
  it('listProducts calls GET /products with query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 },
    })

    await listProducts({ busca: 'camiseta', status: 'ACTIVE' })

    expect(apiClient.get).toHaveBeenCalledWith('/products', { params: { busca: 'camiseta', status: 'ACTIVE' } })
  })

  it('getProduct calls GET /products/:id', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: {} })
    await getProduct('abc-123')
    expect(apiClient.get).toHaveBeenCalledWith('/products/abc-123')
  })

  it('createProduct calls POST /products with the payload', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} })
    const payload = { name: 'Teste' } as any
    await createProduct(payload)
    expect(apiClient.post).toHaveBeenCalledWith('/products', payload)
  })

  it('updateProduct calls PUT /products/:id with the payload', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} })
    const payload = { name: 'Teste' } as any
    await updateProduct('abc-123', payload)
    expect(apiClient.put).toHaveBeenCalledWith('/products/abc-123', payload)
  })

  it('updateProductStatus calls PATCH /products/:id/status', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} })
    await updateProductStatus('abc-123', 'INACTIVE')
    expect(apiClient.patch).toHaveBeenCalledWith('/products/abc-123/status', { status: 'INACTIVE' })
  })

  it('deleteProduct calls DELETE /products/:id', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ data: {} })
    await deleteProduct('abc-123')
    expect(apiClient.delete).toHaveBeenCalledWith('/products/abc-123')
  })

  it('getProductSummary calls GET /products/resumo', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { total: 0, active: 0, inactive: 0 } })
    await getProductSummary()
    expect(apiClient.get).toHaveBeenCalledWith('/products/resumo')
  })
})
