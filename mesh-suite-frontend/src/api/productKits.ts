import { apiClient } from './client'
import type { MeasurementUnit, ProductStatus } from './products'

export interface KitItemInput {
  componentProductId: string
  quantity: number
}

export interface KitItemResponse extends KitItemInput {
  componentName: string
  componentSku: string
  unitPrice: number
  totalPrice: number
}

export interface KitProductRequest {
  name: string
  sku: string
  barcode: string | null
  measurementUnit: MeasurementUnit
  status: ProductStatus
  description: string
  items: KitItemInput[]
  saleMultiple: number | null
}

export interface KitProductResponse {
  id: string
  name: string
  sku: string
  barcode: string | null
  measurementUnit: MeasurementUnit
  status: ProductStatus
  description: string
  items: KitItemResponse[]
  totalPrice: number
  saleMultiple: number | null
}

export async function getKit(id: string): Promise<KitProductResponse> {
  const { data } = await apiClient.get<KitProductResponse>(`/products/kits/${id}`)
  return data
}

export async function createKit(payload: KitProductRequest): Promise<KitProductResponse> {
  const { data } = await apiClient.post<KitProductResponse>('/products/kits', payload)
  return data
}

export async function updateKit(id: string, payload: KitProductRequest): Promise<KitProductResponse> {
  const { data } = await apiClient.put<KitProductResponse>(`/products/kits/${id}`, payload)
  return data
}
