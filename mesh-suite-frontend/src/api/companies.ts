import { apiClient } from './client'

export interface CompanyRequest {
  legalName: string
  cnpj: string
  tradeName: string | null
  stateRegistration: string | null
  municipalRegistration: string | null
  phone: string | null
  email: string | null
  website: string | null
  zipCode: string | null
  street: string | null
  number: string | null
  complement: string | null
  neighborhood: string | null
  city: string | null
  state: string | null
}

export interface CompanyResponse extends CompanyRequest {
  id: string
  active: boolean
}

export interface CompanyCounts {
  total: number
  active: number
  inactive: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListCompaniesParams {
  busca?: string
  ativo?: boolean
  uf?: string
  cidade?: string
  sort?: string
  page?: number
  size?: number
}

export async function listCompanies(params: ListCompaniesParams): Promise<Page<CompanyResponse>> {
  const { data } = await apiClient.get<Page<CompanyResponse>>('/companies', { params })
  return data
}

export async function getCompanyCounts(): Promise<CompanyCounts> {
  const { data } = await apiClient.get<CompanyCounts>('/companies/counts')
  return data
}

export async function getCompany(id: string): Promise<CompanyResponse> {
  const { data } = await apiClient.get<CompanyResponse>(`/companies/${id}`)
  return data
}

export async function createCompany(payload: CompanyRequest): Promise<CompanyResponse> {
  const { data } = await apiClient.post<CompanyResponse>('/companies', payload)
  return data
}

export async function updateCompany(id: string, payload: CompanyRequest): Promise<CompanyResponse> {
  const { data } = await apiClient.put<CompanyResponse>(`/companies/${id}`, payload)
  return data
}

export async function updateCompanyStatus(id: string, active: boolean): Promise<CompanyResponse> {
  const { data } = await apiClient.patch<CompanyResponse>(`/companies/${id}/status`, { active })
  return data
}

export async function deleteCompany(id: string): Promise<void> {
  await apiClient.delete(`/companies/${id}`)
}
