import { apiClient } from './client'

export interface CategoriaRequest {
  nome: string
  descricao: string | null
  ativo: boolean | null
}

export interface CategoriaResponse extends CategoriaRequest {
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

export interface ListarCategoriasParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listarCategorias(params: ListarCategoriasParams): Promise<Page<CategoriaResponse>> {
  const { data } = await apiClient.get<Page<CategoriaResponse>>('/categorias', { params })
  return data
}

export async function buscarCategoria(id: string): Promise<CategoriaResponse> {
  const { data } = await apiClient.get<CategoriaResponse>(`/categorias/${id}`)
  return data
}

export async function criarCategoria(payload: CategoriaRequest): Promise<CategoriaResponse> {
  const { data } = await apiClient.post<CategoriaResponse>('/categorias', payload)
  return data
}

export async function atualizarCategoria(id: string, payload: CategoriaRequest): Promise<CategoriaResponse> {
  const { data } = await apiClient.put<CategoriaResponse>(`/categorias/${id}`, payload)
  return data
}

export async function excluirCategoria(id: string): Promise<void> {
  await apiClient.delete(`/categorias/${id}`)
}
