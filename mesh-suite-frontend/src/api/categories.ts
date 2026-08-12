import { apiClient } from './client'

export interface CategoryRequest {
  name: string
  description: string | null
  active: boolean | null
}

export interface CategoryResponse extends CategoryRequest {
  id: string
  linkedProducts: number
  createdAt: string
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
  page?: number
  size?: number
}

export async function listCategories(params: ListCategoriesParams): Promise<Page<CategoryResponse>> {
  const { data } = await apiClient.get<Page<CategoryResponse>>('/categories', { params })
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
