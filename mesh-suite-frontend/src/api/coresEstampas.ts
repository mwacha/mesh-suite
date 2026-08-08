import { apiClient } from './client'

export interface CorEstampaRequest {
  nome: string
  dataVigencia: string
  descricao: string | null
  ativo: boolean | null
}

export interface CorEstampaResponse extends CorEstampaRequest {
  id: string
  produtosVinculados: number
  criadoEm: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarCoresEstampasParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listarCoresEstampas(params: ListarCoresEstampasParams): Promise<Page<CorEstampaResponse>> {
  const { data } = await apiClient.get<Page<CorEstampaResponse>>('/cores-estampas', { params })
  return data
}

export async function buscarCorEstampa(id: string): Promise<CorEstampaResponse> {
  const { data } = await apiClient.get<CorEstampaResponse>(`/cores-estampas/${id}`)
  return data
}

export async function criarCorEstampa(payload: CorEstampaRequest): Promise<CorEstampaResponse> {
  const { data } = await apiClient.post<CorEstampaResponse>('/cores-estampas', payload)
  return data
}

export async function atualizarCorEstampa(id: string, payload: CorEstampaRequest): Promise<CorEstampaResponse> {
  const { data } = await apiClient.put<CorEstampaResponse>(`/cores-estampas/${id}`, payload)
  return data
}

export async function excluirCorEstampa(id: string): Promise<void> {
  await apiClient.delete(`/cores-estampas/${id}`)
}
