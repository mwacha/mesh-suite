import { apiClient } from './client'

export type SalesOrderStatus = 'DRAFT' | 'IN_PREPARATION' | 'INVOICED'

export interface SalesOrderItemRequest {
  productId: string
  quantity: number
  unitPrice: number
}

export interface SalesOrderItemResponse extends SalesOrderItemRequest {
  productName: string
  totalAmount: number
}

export interface SalesOrderRequest {
  customerId: string
  salespersonId: string
  orderDate: string
  deliveryDate: string | null
  discount: number
  items: SalesOrderItemRequest[]
}

export interface SalesOrderResponse {
  id: string
  number: number
  customerId: string
  customerName: string
  salespersonId: string
  salespersonName: string
  orderDate: string
  deliveryDate: string | null
  status: SalesOrderStatus
  discount: number
  subtotal: number
  total: number
  items: SalesOrderItemResponse[]
}

export interface SalesOrderSummary {
  id: string
  number: number
  customerName: string
  salespersonName: string
  orderDate: string
  total: number
  status: SalesOrderStatus
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListSalesOrdersParams {
  busca?: string
  status?: SalesOrderStatus
  salespersonId?: string
  page?: number
  size?: number
  sort?: string
}

export interface SalesOrderCounts {
  total: number
  draft: number
  inPreparation: number
  invoiced: number
}

export type PeriodRange = 'CURRENT_MONTH' | 'LAST_12_MONTHS'

export interface MonthlyRevenue {
  currentMonthRevenue: number
}

export interface OrderPeriodPoint {
  label: string
  count: number
}

export async function listSalesOrders(params: ListSalesOrdersParams): Promise<Page<SalesOrderSummary>> {
  const { data } = await apiClient.get<Page<SalesOrderSummary>>('/sales-orders', { params })
  return data
}

export async function getSalesOrder(id: string): Promise<SalesOrderResponse> {
  const { data } = await apiClient.get<SalesOrderResponse>(`/sales-orders/${id}`)
  return data
}

export async function createSalesOrder(payload: SalesOrderRequest): Promise<SalesOrderResponse> {
  const { data } = await apiClient.post<SalesOrderResponse>('/sales-orders', payload)
  return data
}

export async function updateSalesOrder(id: string, payload: SalesOrderRequest): Promise<SalesOrderResponse> {
  const { data } = await apiClient.put<SalesOrderResponse>(`/sales-orders/${id}`, payload)
  return data
}

export async function advanceSalesOrderStatus(id: string, status: SalesOrderStatus): Promise<void> {
  await apiClient.patch(`/sales-orders/${id}/status`, { status })
}

export async function deleteSalesOrder(id: string): Promise<void> {
  await apiClient.delete(`/sales-orders/${id}`)
}

export async function getSalesOrderCounts(): Promise<SalesOrderCounts> {
  const { data } = await apiClient.get<SalesOrderCounts>('/sales-orders/counts')
  return data
}

export async function getMonthlyRevenue(): Promise<MonthlyRevenue> {
  const { data } = await apiClient.get<MonthlyRevenue>('/sales-orders/monthly-revenue')
  return data
}

export async function getOrdersByPeriod(period: PeriodRange): Promise<OrderPeriodPoint[]> {
  const { data } = await apiClient.get<OrderPeriodPoint[]>('/sales-orders/orders-by-period', { params: { period } })
  return data
}
