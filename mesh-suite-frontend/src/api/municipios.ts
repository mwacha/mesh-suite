import { apiClient } from './client'

export interface ListarMunicipiosParams {
  uf?: string
}

export async function listarMunicipios(params: ListarMunicipiosParams = {}): Promise<string[]> {
  const { data } = await apiClient.get<string[]>('/municipios', { params })
  return data
}
