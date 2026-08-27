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
  // This variante's own coordinate in the parent's Tipos de Variação matrix,
  // e.g. ["40","VERMELHA"], in the same order as variationAxes. Only "Tamanho"
  // maps to a real field (size), so without this any other axis value would be
  // lost on reload and the row could not be matched back to its combination.
  variationValues: string[]
}

export interface VariationChildResponse extends VariationChildInput {
  id: string
  colorwayName: string | null
}

export interface VariationAxis {
  name: string
  values: string[]
}

export interface VariationParentRequest {
  name: string
  sku: string
  brandId: string | null
  categoryId: string | null
  salePrice: number
  status: ProductStatus
  description: string
  measurementUnit: MeasurementUnit
  children: VariationChildInput[]
  saleMultiple: number | null
  variationAxes: VariationAxis[]
}

export interface VariationParentResponse {
  id: string
  name: string
  sku: string
  brandId: string | null
  brandName: string | null
  categoryId: string | null
  categoryName: string | null
  salePrice: number
  status: ProductStatus
  description: string
  measurementUnit: MeasurementUnit
  children: VariationChildResponse[]
  saleMultiple: number | null
  variationAxes: VariationAxis[]
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
