import { apiClient } from './client'

export type ProductStatus = 'ACTIVE' | 'INACTIVE'
export type MeasurementUnit = 'UN' | 'KG' | 'G' | 'L' | 'ML' | 'MT' | 'CM' | 'CX' | 'PC' | 'PAR' | 'DZ'

export interface ProductRequest {
  name: string
  sku: string
  barcode: string
  brand: string
  categoryId: string | null
  colorwayId: string | null
  salePrice: number
  costPrice: number | null
  status: ProductStatus
  description: string
  stockQuantity: number
  measurementUnit: MeasurementUnit
  minStock: number | null
  maxStock: number | null
  weight: number | null
  length: number | null
  width: number | null
  height: number | null
}

export interface ProductResponse extends ProductRequest {
  id: string
  categoryName: string | null
  colorwayName: string | null
}

export interface ProductListItem {
  id: string
  name: string
  sku: string
  brand: string
  salePrice: number
  stockQuantity: number
  status: ProductStatus
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListProductsParams {
  busca?: string
  status?: ProductStatus
  page?: number
  size?: number
  sort?: string
}

export interface ProductSummary {
  total: number
  active: number
  inactive: number
}

export async function listProducts(params: ListProductsParams): Promise<Page<ProductListItem>> {
  const { data } = await apiClient.get<Page<ProductListItem>>('/products', { params })
  return data
}

export async function getProduct(id: string): Promise<ProductResponse> {
  const { data } = await apiClient.get<ProductResponse>(`/products/${id}`)
  return data
}

export async function createProduct(payload: ProductRequest): Promise<ProductResponse> {
  const { data } = await apiClient.post<ProductResponse>('/products', payload)
  return data
}

export async function updateProduct(id: string, payload: ProductRequest): Promise<ProductResponse> {
  const { data } = await apiClient.put<ProductResponse>(`/products/${id}`, payload)
  return data
}

export async function updateProductStatus(id: string, status: ProductStatus): Promise<void> {
  await apiClient.patch(`/products/${id}/status`, { status })
}

export async function deleteProduct(id: string): Promise<void> {
  await apiClient.delete(`/products/${id}`)
}

export async function getProductSummary(): Promise<ProductSummary> {
  const { data } = await apiClient.get<ProductSummary>('/products/resumo')
  return data
}
