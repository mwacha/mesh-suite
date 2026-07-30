import { apiClient } from './client'

export type TipoPessoa = 'FISICA' | 'JURIDICA'
export type PapelParceiro = 'CLIENTE' | 'FORNECEDOR' | 'TRANSPORTADORA'
export type StatusParceiro = 'ATIVO' | 'EM_RISCO' | 'BLOQUEADO'
export type IndicadorIe = 'NAO_CONTRIBUINTE' | 'CONTRIBUINTE' | 'CONTRIBUINTE_ISENTO'

export interface ParceiroContato {
  nome: string
  email: string
  telefoneComercial: string
  telefoneCelular: string
  cargo: string
}

export interface ParceiroRequest {
  tipoPessoa: TipoPessoa
  documento: string
  nomeFantasia: string
  razaoSocial: string
  papeis: PapelParceiro[]
  emailsCobranca: string
  whatsapp: string
  indicadorIe: IndicadorIe | null
  inscricaoEstadual: string
  inscricaoMunicipal: string
  inscricaoSuframa: string
  cep: string
  logradouro: string
  numero: string
  bairro: string
  complemento: string
  uf: string
  cidade: string
  observacao: string
  contatos: ParceiroContato[]
}

export interface ParceiroResponse extends ParceiroRequest {
  id: string
  status: StatusParceiro
}

export interface ParceiroSummary {
  id: string
  nomeFantasia: string
  razaoSocial: string
  documento: string
  cidade: string
  uf: string
  whatsapp: string
  status: StatusParceiro
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarParceirosParams {
  busca?: string
  status?: StatusParceiro
  tipoDocumento?: TipoPessoa
  uf?: string
  cidade?: string
  page?: number
  size?: number
}

export interface ParceiroResumo {
  total: number
  ativos: number
  emRisco: number
  bloqueados: number
}

export async function listarParceiros(params: ListarParceirosParams): Promise<Page<ParceiroSummary>> {
  const { data } = await apiClient.get<Page<ParceiroSummary>>('/parceiros', { params })
  return data
}

export async function buscarParceiro(id: string): Promise<ParceiroResponse> {
  const { data } = await apiClient.get<ParceiroResponse>(`/parceiros/${id}`)
  return data
}

export async function criarParceiro(payload: ParceiroRequest): Promise<ParceiroResponse> {
  const { data } = await apiClient.post<ParceiroResponse>('/parceiros', payload)
  return data
}

export async function atualizarParceiro(id: string, payload: ParceiroRequest): Promise<ParceiroResponse> {
  const { data } = await apiClient.put<ParceiroResponse>(`/parceiros/${id}`, payload)
  return data
}

export async function atualizarStatusParceiro(id: string, status: StatusParceiro): Promise<void> {
  await apiClient.patch(`/parceiros/${id}/status`, { status })
}

export async function excluirParceiro(id: string): Promise<void> {
  await apiClient.delete(`/parceiros/${id}`)
}

export async function buscarResumoParceiros(): Promise<ParceiroResumo> {
  const { data } = await apiClient.get<ParceiroResumo>('/parceiros/resumo')
  return data
}
