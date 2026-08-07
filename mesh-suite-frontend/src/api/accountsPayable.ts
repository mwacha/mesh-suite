import { apiClient } from './client'

export type AccountsPayableStatus = 'OPEN' | 'PAID'

export interface AccountsPayable {
  id: string
  number: number
  installmentNumber: number
  totalInstallments: number
  supplierId: string
  supplierName: string
  amount: number
  issueDate: string
  dueDate: string
  paymentDate: string | null
  status: AccountsPayableStatus
  referenceId: string | null
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListAccountsPayableParams {
  status?: AccountsPayableStatus
  page?: number
  size?: number
  sort?: string
}

export async function listAccountsPayable(params: ListAccountsPayableParams): Promise<Page<AccountsPayable>> {
  const { data } = await apiClient.get<Page<AccountsPayable>>('/accounts-payable', { params })
  return data
}

export async function updateAccountsPayableStatus(id: string, status: AccountsPayableStatus): Promise<void> {
  await apiClient.patch(`/accounts-payable/${id}/status`, { status })
}
