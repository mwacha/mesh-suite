import { apiClient } from './client'
import type { Page } from './types'

export type PersonType = 'INDIVIDUAL' | 'LEGAL_ENTITY'
export type PartnerRole = 'CUSTOMER' | 'SUPPLIER' | 'CARRIER'
export type PartnerStatus = 'ACTIVE' | 'AT_RISK' | 'BLOCKED'
export type TaxIndicator = 'NON_TAXPAYER' | 'TAXPAYER' | 'EXEMPT_TAXPAYER'

export interface PartnerContact {
  name: string
  email: string
  businessPhone: string
  mobilePhone: string
  jobTitle: string
}

export interface PartnerRequest {
  personType: PersonType
  document: string
  tradeName: string
  legalName: string
  roles: PartnerRole[]
  billingEmails: string
  whatsapp: string
  taxIndicator: TaxIndicator | null
  stateRegistration: string
  municipalRegistration: string
  suframaRegistration: string
  zipCode: string
  street: string
  number: string
  neighborhood: string
  complement: string
  state: string
  city: string
  notes: string
  contacts: PartnerContact[]
}

export interface PartnerResponse extends PartnerRequest {
  id: string
  status: PartnerStatus
}

export interface PartnerListItem {
  id: string
  tradeName: string
  legalName: string
  document: string
  personType: PersonType
  city: string
  state: string
  whatsapp: string
  status: PartnerStatus
}

export interface ListPartnersParams {
  busca?: string
  status?: PartnerStatus[]
  tipoDocumento?: PersonType[]
  documento?: string
  uf?: string[]
  cidade?: string[]
  papel?: PartnerRole
  page?: number
  size?: number
  sort?: string
}

export interface PartnerSummary {
  total: number
  active: number
  atRisk: number
  blocked: number
}

export async function listPartners(params: ListPartnersParams): Promise<Page<PartnerListItem>> {
  const { data } = await apiClient.get<Page<PartnerListItem>>('/partners', { params })
  return data
}

export async function getPartner(id: string): Promise<PartnerResponse> {
  const { data } = await apiClient.get<PartnerResponse>(`/partners/${id}`)
  return data
}

export async function createPartner(payload: PartnerRequest): Promise<PartnerResponse> {
  const { data } = await apiClient.post<PartnerResponse>('/partners', payload)
  return data
}

export async function updatePartner(id: string, payload: PartnerRequest): Promise<PartnerResponse> {
  const { data } = await apiClient.put<PartnerResponse>(`/partners/${id}`, payload)
  return data
}

export async function updatePartnerStatus(id: string, status: PartnerStatus): Promise<void> {
  await apiClient.patch(`/partners/${id}/status`, { status })
}

export async function deletePartner(id: string): Promise<void> {
  await apiClient.delete(`/partners/${id}`)
}

export async function getPartnerSummary(papel?: PartnerRole): Promise<PartnerSummary> {
  const { data } = await apiClient.get<PartnerSummary>('/partners/summary', { params: { papel } })
  return data
}
