import { apiClient } from './client'

export interface InstallmentInput {
  amount: number
  dueDate: string
}

export interface PurchaseInvoiceItemResponse {
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  totalValue: number
  icmsAmount: number
  ipiAmount: number
  pisAmount: number
  cofinsAmount: number
}

export interface PurchaseInvoiceResponse {
  id: string
  number: number
  invoiceNumber: string
  series: string
  model: string
  purchaseOrderId: string
  purchaseOrderNumber: number
  supplierId: string
  supplierName: string
  issueDate: string
  entryDate: string
  discount: number
  subtotal: number
  total: number
  icmsAmount: number
  ipiAmount: number
  pisAmount: number
  cofinsAmount: number
  items: PurchaseInvoiceItemResponse[]
}

export interface PurchaseInvoiceSummary {
  id: string
  number: number
  invoiceNumber: string
  supplierName: string
  issueDate: string
  total: number
}

export interface PurchaseInvoiceRequest {
  invoiceNumber: string
  series: string
  model: string
  issueDate: string
  entryDate: string
  installments: InstallmentInput[]
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListPurchaseInvoicesParams {
  search?: string
  page?: number
  size?: number
  sort?: string
}

export async function listPurchaseInvoices(params: ListPurchaseInvoicesParams): Promise<Page<PurchaseInvoiceSummary>> {
  const { data } = await apiClient.get<Page<PurchaseInvoiceSummary>>('/purchase-invoices', { params })
  return data
}

export async function getPurchaseInvoice(id: string): Promise<PurchaseInvoiceResponse> {
  const { data } = await apiClient.get<PurchaseInvoiceResponse>(`/purchase-invoices/${id}`)
  return data
}

export async function issuePurchaseInvoice(purchaseOrderId: string, payload: PurchaseInvoiceRequest): Promise<PurchaseInvoiceResponse> {
  const { data } = await apiClient.post<PurchaseInvoiceResponse>(`/purchase-invoices/issue/${purchaseOrderId}`, payload)
  return data
}
