import { apiClient } from './client'

export interface SaleItemResponse {
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  totalAmount: number
  icmsAmount: number
  ipiAmount: number
  pisAmount: number
  cofinsAmount: number
}

export interface SaleResponse {
  id: string
  number: number
  orderId: string
  orderNumber: number
  customerId: string
  customerName: string
  salespersonId: string
  salespersonName: string
  issueDate: string
  discount: number
  subtotal: number
  total: number
  icmsAmount: number
  ipiAmount: number
  pisAmount: number
  cofinsAmount: number
  items: SaleItemResponse[]
}

export interface SaleSummary {
  id: string
  number: number
  customerName: string
  issueDate: string
  total: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListSalesParams {
  busca?: string
  page?: number
  size?: number
  sort?: string
}

export async function listSales(params: ListSalesParams): Promise<Page<SaleSummary>> {
  const { data } = await apiClient.get<Page<SaleSummary>>('/sales', { params })
  return data
}

export async function getSale(id: string): Promise<SaleResponse> {
  const { data } = await apiClient.get<SaleResponse>(`/sales/${id}`)
  return data
}

export async function issueSale(orderId: string): Promise<SaleResponse> {
  const { data } = await apiClient.post<SaleResponse>(`/sales/issue/${orderId}`)
  return data
}
