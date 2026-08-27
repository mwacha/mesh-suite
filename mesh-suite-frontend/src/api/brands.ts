import { apiClient } from './client'

export interface BrandRequest {
  name: string
  active: boolean | null
}

export interface BrandResponse extends BrandRequest {
  id: string
  linkedProducts: number
  createdAt: string
}

export interface BrandCounts {
  total: number
  active: number
  inactive: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListBrandsParams {
  busca?: string
  ativo?: boolean
  sort?: string
  page?: number
  size?: number
}

export async function listBrands(params: ListBrandsParams): Promise<Page<BrandResponse>> {
  const { data } = await apiClient.get<Page<BrandResponse>>('/brands', { params })
  return data
}

export async function getBrandCounts(): Promise<BrandCounts> {
  const { data } = await apiClient.get<BrandCounts>('/brands/counts')
  return data
}

export async function getBrand(id: string): Promise<BrandResponse> {
  const { data } = await apiClient.get<BrandResponse>(`/brands/${id}`)
  return data
}

export async function createBrand(payload: BrandRequest): Promise<BrandResponse> {
  const { data } = await apiClient.post<BrandResponse>('/brands', payload)
  return data
}

export async function updateBrand(id: string, payload: BrandRequest): Promise<BrandResponse> {
  const { data } = await apiClient.put<BrandResponse>(`/brands/${id}`, payload)
  return data
}

export async function deleteBrand(id: string): Promise<void> {
  await apiClient.delete(`/brands/${id}`)
}
