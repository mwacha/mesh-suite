import { apiClient } from './client'

export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE' | 'STOCK' | 'PAYABLE' | 'SALE' | 'PURCHASE_INVOICE'
export type ActionName = 'VIEW' | 'CREATE' | 'EDIT' | 'DELETE'

export interface PermissionGrant {
  module: ModuleName
  action: ActionName
}

export interface PermissionProfileRequest {
  name: string
  description: string
  grants: PermissionGrant[]
}

export interface PermissionProfileResponse {
  id: string
  name: string
  description: string | null
  isSystem: boolean
  createdAt: string
  grants: PermissionGrant[]
}

export interface PermissionProfileSummary {
  id: string
  name: string
  description: string | null
  isSystem: boolean
  moduleCount: number
  userCount: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListPermissionProfilesParams {
  busca?: string
  page?: number
  size?: number
}

export async function listPermissionProfiles(params: ListPermissionProfilesParams): Promise<Page<PermissionProfileSummary>> {
  const { data } = await apiClient.get<Page<PermissionProfileSummary>>('/permission-profiles', { params })
  return data
}

export async function getPermissionProfile(id: string): Promise<PermissionProfileResponse> {
  const { data } = await apiClient.get<PermissionProfileResponse>(`/permission-profiles/${id}`)
  return data
}

export async function createPermissionProfile(payload: PermissionProfileRequest): Promise<PermissionProfileResponse> {
  const { data } = await apiClient.post<PermissionProfileResponse>('/permission-profiles', payload)
  return data
}

export async function updatePermissionProfile(id: string, payload: PermissionProfileRequest): Promise<PermissionProfileResponse> {
  const { data } = await apiClient.put<PermissionProfileResponse>(`/permission-profiles/${id}`, payload)
  return data
}

export async function deletePermissionProfile(id: string): Promise<void> {
  await apiClient.delete(`/permission-profiles/${id}`)
}
