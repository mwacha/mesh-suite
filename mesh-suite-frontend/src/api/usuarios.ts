import { apiClient } from './client'

export interface UsuarioRepresentante {
  id: string
  nome: string
}

export async function listarRepresentantes(): Promise<UsuarioRepresentante[]> {
  const { data } = await apiClient.get<UsuarioRepresentante[]>('/usuarios/representantes')
  return data
}
