import { apiClient } from './client'

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface PaymentMethodInstallmentInput {
  daysDue: number
  percentage: number
}

export interface PaymentMethodInstallmentResponse extends PaymentMethodInstallmentInput {
  installmentNumber: number
}

export interface PaymentMethodRequest {
  description: string
  active: boolean
  installments: PaymentMethodInstallmentInput[]
}

export interface PaymentMethodResponse {
  id: string
  description: string
  active: boolean
  createdAt: string
  installments: PaymentMethodInstallmentResponse[]
}

export interface PaymentMethodSummary {
  id: string
  description: string
  active: boolean
  installmentsCount: number
}

export interface ListPaymentMethodsParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listPaymentMethods(params: ListPaymentMethodsParams): Promise<Page<PaymentMethodSummary>> {
  const { data } = await apiClient.get<Page<PaymentMethodSummary>>('/payment-methods', { params })
  return data
}

export async function getPaymentMethod(id: string): Promise<PaymentMethodResponse> {
  const { data } = await apiClient.get<PaymentMethodResponse>(`/payment-methods/${id}`)
  return data
}

export async function createPaymentMethod(payload: PaymentMethodRequest): Promise<PaymentMethodResponse> {
  const { data } = await apiClient.post<PaymentMethodResponse>('/payment-methods', payload)
  return data
}

export async function updatePaymentMethod(id: string, payload: PaymentMethodRequest): Promise<PaymentMethodResponse> {
  const { data } = await apiClient.put<PaymentMethodResponse>(`/payment-methods/${id}`, payload)
  return data
}

export async function deletePaymentMethod(id: string): Promise<void> {
  await apiClient.delete(`/payment-methods/${id}`)
}
