import { apiClient } from './client'

export type PurchaseOrderStatus = 'OPEN' | 'RECEIVED' | 'CANCELLED'

export interface PurchaseOrderItemRequest {
  productId: string
  quantity: number
  unitPrice: number
}

export interface PurchaseOrderItemResponse extends PurchaseOrderItemRequest {
  productName: string
  totalValue: number
}

export interface PurchaseOrderRequest {
  supplierId: string
  buyerId: string
  orderDate: string
  expectedDeliveryDate: string | null
  discount: number
  items: PurchaseOrderItemRequest[]
}

export interface PurchaseOrderResponse {
  id: string
  number: number
  supplierId: string
  supplierName: string
  buyerId: string
  buyerName: string
  orderDate: string
  expectedDeliveryDate: string | null
  status: PurchaseOrderStatus
  discount: number
  subtotal: number
  total: number
  items: PurchaseOrderItemResponse[]
}

export interface PurchaseOrderSummary {
  id: string
  number: number
  supplierName: string
  buyerName: string
  orderDate: string
  total: number
  status: PurchaseOrderStatus
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListPurchaseOrdersParams {
  search?: string
  status?: PurchaseOrderStatus
  page?: number
  size?: number
}

export interface PurchaseOrderCounts {
  total: number
  open: number
  received: number
  cancelled: number
}

export async function listPurchaseOrders(params: ListPurchaseOrdersParams): Promise<Page<PurchaseOrderSummary>> {
  const { data } = await apiClient.get<Page<PurchaseOrderSummary>>('/purchase-orders', { params })
  return data
}

export async function getPurchaseOrder(id: string): Promise<PurchaseOrderResponse> {
  const { data } = await apiClient.get<PurchaseOrderResponse>(`/purchase-orders/${id}`)
  return data
}

export async function createPurchaseOrder(payload: PurchaseOrderRequest): Promise<PurchaseOrderResponse> {
  const { data } = await apiClient.post<PurchaseOrderResponse>('/purchase-orders', payload)
  return data
}

export async function updatePurchaseOrder(id: string, payload: PurchaseOrderRequest): Promise<PurchaseOrderResponse> {
  const { data } = await apiClient.put<PurchaseOrderResponse>(`/purchase-orders/${id}`, payload)
  return data
}

export async function updatePurchaseOrderStatus(id: string, status: PurchaseOrderStatus): Promise<void> {
  await apiClient.patch(`/purchase-orders/${id}/status`, { status })
}

export async function deletePurchaseOrder(id: string): Promise<void> {
  await apiClient.delete(`/purchase-orders/${id}`)
}

export async function getPurchaseOrderCounts(): Promise<PurchaseOrderCounts> {
  const { data } = await apiClient.get<PurchaseOrderCounts>('/purchase-orders/counts')
  return data
}
