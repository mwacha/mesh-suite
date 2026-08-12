import { apiClient } from './client'

export interface ColorwayRequest {
  name: string
  effectiveDate: string
  description: string | null
  active: boolean | null
}

export interface ColorwayResponse extends ColorwayRequest {
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

export interface ListColorwaysParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listColorways(params: ListColorwaysParams): Promise<Page<ColorwayResponse>> {
  const { data } = await apiClient.get<Page<ColorwayResponse>>('/colorways', { params })
  return data
}

export async function getColorway(id: string): Promise<ColorwayResponse> {
  const { data } = await apiClient.get<ColorwayResponse>(`/colorways/${id}`)
  return data
}

export async function createColorway(payload: ColorwayRequest): Promise<ColorwayResponse> {
  const { data } = await apiClient.post<ColorwayResponse>('/colorways', payload)
  return data
}

export async function updateColorway(id: string, payload: ColorwayRequest): Promise<ColorwayResponse> {
  const { data } = await apiClient.put<ColorwayResponse>(`/colorways/${id}`, payload)
  return data
}

export async function deleteColorway(id: string): Promise<void> {
  await apiClient.delete(`/colorways/${id}`)
}
