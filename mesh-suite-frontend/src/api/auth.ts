import { apiClient } from './client'

export interface LoginPayload {
  email: string
  senha: string
  manterConectado: boolean
}

export interface MeResponse {
  nome: string
  papel: string
}

export async function login(payload: LoginPayload): Promise<void> {
  await apiClient.post('/auth/login', payload)
}

export async function me(): Promise<MeResponse> {
  const { data } = await apiClient.get<MeResponse>('/auth/me')
  return data
}

export async function forgotPassword(email: string): Promise<void> {
  await apiClient.post('/auth/forgot-password', { email })
}

export async function resetPassword(token: string, novaSenha: string): Promise<void> {
  await apiClient.post('/auth/reset-password', { token, novaSenha })
}
