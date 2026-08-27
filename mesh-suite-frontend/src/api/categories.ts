import { apiClient } from './client'

export interface CategoryRequest {
  name: string
  description: string | null
  active: boolean | null
  parentId: string | null
}

export interface CategoryResponse extends CategoryRequest {
  id: string
  parentName: string | null
  linkedProducts: number
  createdAt: string
}

export interface CategoryCounts {
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

export interface ListCategoriesParams {
  busca?: string
  ativo?: boolean
  raiz?: boolean
  sort?: string
  page?: number
  size?: number
}

export async function listCategories(params: ListCategoriesParams): Promise<Page<CategoryResponse>> {
  const { data } = await apiClient.get<Page<CategoryResponse>>('/categories', { params })
  return data
}

export async function getCategoryCounts(): Promise<CategoryCounts> {
  const { data } = await apiClient.get<CategoryCounts>('/categories/counts')
  return data
}

export async function getCategory(id: string): Promise<CategoryResponse> {
  const { data } = await apiClient.get<CategoryResponse>(`/categories/${id}`)
  return data
}

export async function createCategory(payload: CategoryRequest): Promise<CategoryResponse> {
  const { data } = await apiClient.post<CategoryResponse>('/categories', payload)
  return data
}

export async function updateCategory(id: string, payload: CategoryRequest): Promise<CategoryResponse> {
  const { data } = await apiClient.put<CategoryResponse>(`/categories/${id}`, payload)
  return data
}

export async function deleteCategory(id: string): Promise<void> {
  await apiClient.delete(`/categories/${id}`)
}
