import { apiClient } from './client'

export interface SalesRep {
  id: string
  name: string
}

export async function listSalesReps(): Promise<SalesRep[]> {
  const { data } = await apiClient.get<SalesRep[]>('/users/sales-reps')
  return data
}

export interface Buyer {
  id: string
  name: string
}

export async function listBuyers(): Promise<Buyer[]> {
  const { data } = await apiClient.get<Buyer[]>('/users/buyers')
  return data
}

export type Role = 'ADMINISTRATIVE' | 'SALES_REP' | 'PRODUCTION' | 'OUTSOURCED' | 'ADMIN'
export type Profile = 'ADMIN' | 'MANAGER' | 'SALES' | 'VIEWER'
export type ModuleName = 'CUSTOMER' | 'PRODUCT' | 'ORDER' | 'USER' | 'PURCHASE' | 'PAYABLE'
export type ActionName = 'VIEW' | 'CREATE' | 'EDIT' | 'DELETE'

export interface Permission {
  module: ModuleName
  action: ActionName
}

export interface UserRequest {
  name: string
  email: string
  phone: string
  role: Role
  profile: Profile
  active: boolean
  password: string
  confirmPassword: string
  permissions: Permission[]
}

export interface UserResponse {
  id: string
  name: string
  email: string
  phone: string
  role: Role
  profile: Profile
  active: boolean
  permissions: Permission[]
}

export interface UserListItem {
  id: string
  name: string
  email: string
  profile: Profile
  active: boolean
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListUsersParams {
  search?: string
  profile?: Profile
  active?: boolean
  page?: number
  size?: number
}

export interface UserCounts {
  total: number
  active: number
  inactive: number
}

export async function listUsers(params: ListUsersParams): Promise<Page<UserListItem>> {
  const { data } = await apiClient.get<Page<UserListItem>>('/users', { params })
  return data
}

export async function getUserCounts(): Promise<UserCounts> {
  const { data } = await apiClient.get<UserCounts>('/users/counts')
  return data
}

export async function getUser(id: string): Promise<UserResponse> {
  const { data } = await apiClient.get<UserResponse>(`/users/${id}`)
  return data
}

export async function createUser(payload: UserRequest): Promise<UserResponse> {
  const { data } = await apiClient.post<UserResponse>('/users', payload)
  return data
}

export async function updateUser(id: string, payload: UserRequest): Promise<UserResponse> {
  const { data } = await apiClient.put<UserResponse>(`/users/${id}`, payload)
  return data
}

export async function updateUserStatus(id: string, active: boolean): Promise<void> {
  await apiClient.patch(`/users/${id}/status`, { active })
}
