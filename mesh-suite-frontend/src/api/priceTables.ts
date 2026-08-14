import { apiClient } from './client'

export type ProductSelectionMode = 'ALL_PRODUCTS' | 'SELECT_PRODUCTS'
export type AdjustmentMethod = 'AUTOMATIC' | 'MANUAL'
export type AdjustmentOperation = 'ADD' | 'SUBTRACT'
export type AdjustmentValueType = 'FIXED' | 'PERCENTAGE'
export type Rounding = 'NO_ROUNDING' | 'END_IN_0' | 'END_IN_9' | 'END_IN_90' | 'END_IN_99'

export interface PriceTableItemInput {
  productId: string
  tablePrice: number | null
  commissionPercentage: number | null
}

export interface PriceTableItemResponse extends PriceTableItemInput {
  productName: string
  productSku: string
  registeredPrice: number
}

export interface PriceTableRequest {
  name: string
  productSelectionMode: ProductSelectionMode
  adjustmentMethod: AdjustmentMethod
  adjustmentOperation: AdjustmentOperation | null
  adjustmentValueType: AdjustmentValueType | null
  adjustmentValue: number | null
  rounding: Rounding
  effectiveStartDate: string
  effectiveEndDate: string | null
  minSalePrice: number | null
  defaultCommissionPercentage: number | null
  active: boolean | null
  items: PriceTableItemInput[]
}

export interface PriceTableResponse {
  id: string
  name: string
  productSelectionMode: ProductSelectionMode
  adjustmentMethod: AdjustmentMethod
  adjustmentOperation: AdjustmentOperation | null
  adjustmentValueType: AdjustmentValueType | null
  adjustmentValue: number | null
  rounding: Rounding
  effectiveStartDate: string
  effectiveEndDate: string | null
  minSalePrice: number | null
  defaultCommissionPercentage: number | null
  active: boolean
  createdAt: string
  items: PriceTableItemResponse[]
}

export interface PriceTableSummary {
  id: string
  name: string
  adjustmentMethod: AdjustmentMethod
  adjustmentOperation: AdjustmentOperation | null
  adjustmentValueType: AdjustmentValueType | null
  adjustmentValue: number | null
  effectiveStartDate: string
  effectiveEndDate: string | null
  active: boolean
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListPriceTablesParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listPriceTables(params: ListPriceTablesParams): Promise<Page<PriceTableSummary>> {
  const { data } = await apiClient.get<Page<PriceTableSummary>>('/price-tables', { params })
  return data
}

export async function getPriceTable(id: string): Promise<PriceTableResponse> {
  const { data } = await apiClient.get<PriceTableResponse>(`/price-tables/${id}`)
  return data
}

export async function createPriceTable(payload: PriceTableRequest): Promise<PriceTableResponse> {
  const { data } = await apiClient.post<PriceTableResponse>('/price-tables', payload)
  return data
}

export async function updatePriceTable(id: string, payload: PriceTableRequest): Promise<PriceTableResponse> {
  const { data } = await apiClient.put<PriceTableResponse>(`/price-tables/${id}`, payload)
  return data
}

export async function deletePriceTable(id: string): Promise<void> {
  await apiClient.delete(`/price-tables/${id}`)
}
