import { apiClient } from './client'

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type PaymentMethodType = 'CASH' | 'CARD' | 'BOLETO' | 'PIX' | 'DUPLICATA' | 'TRANSFER'

export const PAYMENT_METHOD_TYPE_LABEL: Record<PaymentMethodType, string> = {
  CASH: 'Dinheiro',
  CARD: 'Cartão',
  BOLETO: 'Boleto',
  PIX: 'Pix',
  DUPLICATA: 'Duplicata',
  TRANSFER: 'Transferência',
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
  type: PaymentMethodType | ''
  notes?: string
  active: boolean
  maxInstallments: number
  interestRate?: number | null
  settlementDays?: number | null
  /**
   * Omitido pela tela de cadastro, que trabalha com "máx. de parcelas" e não
   * edita o parcelamento detalhado -- o backend preserva o que já está gravado.
   */
  installments?: PaymentMethodInstallmentInput[]
}

export interface PaymentMethodResponse {
  id: string
  description: string
  type: PaymentMethodType | null
  notes: string | null
  active: boolean
  maxInstallments: number
  interestRate: number | null
  settlementDays: number | null
  createdAt: string
  installments: PaymentMethodInstallmentResponse[]
}

export interface PaymentMethodSummary {
  id: string
  description: string
  type: PaymentMethodType | null
  active: boolean
  maxInstallments: number
  installmentsCount: number
  installmentDays: number[]
}

export interface PaymentMethodCounts {
  total: number
  active: number
  inactive: number
}

export interface ListPaymentMethodsParams {
  busca?: string
  tipo?: PaymentMethodType
  ativo?: boolean
  sort?: string
  page?: number
  size?: number
}

export async function listPaymentMethods(params: ListPaymentMethodsParams): Promise<Page<PaymentMethodSummary>> {
  const { data } = await apiClient.get<Page<PaymentMethodSummary>>('/payment-methods', { params })
  return data
}

export async function getPaymentMethodCounts(): Promise<PaymentMethodCounts> {
  const { data } = await apiClient.get<PaymentMethodCounts>('/payment-methods/counts')
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
