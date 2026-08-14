import { apiClient } from './client'

export interface ListMunicipalitiesParams {
  uf?: string
}

export async function listMunicipalities(params: ListMunicipalitiesParams = {}): Promise<string[]> {
  const { data } = await apiClient.get<string[]>('/municipalities', { params })
  return data
}
