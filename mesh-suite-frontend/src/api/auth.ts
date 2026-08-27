import { apiClient } from './client'

export interface LoginPayload {
  email: string
  senha: string
  manterConectado: boolean
}

export interface AccountOption {
  tenantId: string
  nomeEmpresa: string
}

// `contas` empty means the login already completed (session cookie set);
// non-empty means credentials were valid for more than one account and the
// caller must show a picker, then call selectAccount() with one of these ids.
export type LoginResult =
  | { status: 'logged-in' }
  | { status: 'select-account'; contas: AccountOption[] }

export interface MeResponse {
  nome: string
  papel: string
  nomeEmpresa: string | null
}

export async function login(payload: LoginPayload): Promise<LoginResult> {
  const { data } = await apiClient.post<{ contas: AccountOption[] }>('/auth/login', payload)
  return data.contas.length > 0
    ? { status: 'select-account', contas: data.contas }
    : { status: 'logged-in' }
}

export async function selectAccount(tenantId: string, manterConectado: boolean): Promise<void> {
  await apiClient.post('/auth/select-account', { tenantId, manterConectado })
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
