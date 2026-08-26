import { apiClient } from './client'
import type { MeasurementUnit, ProductStatus } from './products'

export interface VariationChildInput {
  id?: string
  sku: string
  barcode: string | null
  salePrice: number
  costPrice: number | null
  stockQuantity: number | null
  minStock: number | null
  maxStock: number | null
  size: string | null
  colorwayId: string | null
  saleMultiple: number | null
}

export interface VariationChildResponse extends VariationChildInput {
  id: string
  colorwayName: string | null
}

export interface VariationParentRequest {
  name: string
  sku: string
  brand: string
  categoryId: string | null
  salePrice: number
  status: ProductStatus
  description: string
  measurementUnit: MeasurementUnit
  children: VariationChildInput[]
  saleMultiple: number | null
}

export interface VariationParentResponse {
  id: string
  name: string
  sku: string
  brand: string
  categoryId: string | null
  categoryName: string | null
  salePrice: number
  status: ProductStatus
  description: string
  measurementUnit: MeasurementUnit
  children: VariationChildResponse[]
  saleMultiple: number | null
}

export async function getVariation(id: string): Promise<VariationParentResponse> {
  const { data } = await apiClient.get<VariationParentResponse>(`/products/variations/${id}`)
  return data
}

export async function createVariation(payload: VariationParentRequest): Promise<VariationParentResponse> {
  const { data } = await apiClient.post<VariationParentResponse>('/products/variations', payload)
  return data
}

export async function updateVariation(id: string, payload: VariationParentRequest): Promise<VariationParentResponse> {
  const { data } = await apiClient.put<VariationParentResponse>(`/products/variations/${id}`, payload)
  return data
}
