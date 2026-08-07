import { apiClient } from './client'

export type StatusProduto = 'ATIVO' | 'INATIVO'
export type UnidadeMedida = 'UN' | 'KG' | 'G' | 'L' | 'ML' | 'MT' | 'CM' | 'CX' | 'PC' | 'PAR' | 'DZ'

export interface ProdutoRequest {
  nome: string
  sku: string
  codigoBarras: string
  marca: string
  categoriaId: string | null
  precoVenda: number
  precoCusto: number | null
  status: StatusProduto
  descricao: string
  quantidadeEstoque: number
  unidadeMedida: UnidadeMedida
  estoqueMinimo: number | null
  estoqueMaximo: number | null
  peso: number | null
  comprimento: number | null
  largura: number | null
  altura: number | null
}

export interface ProdutoResponse extends ProdutoRequest {
  id: string
  categoriaNome: string | null
}

export interface ProdutoSummary {
  id: string
  nome: string
  sku: string
  marca: string
  precoVenda: number
  quantidadeEstoque: number
  status: StatusProduto
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarProdutosParams {
  busca?: string
  status?: StatusProduto
  page?: number
  size?: number
  sort?: string
}

export interface ProdutoResumo {
  total: number
  ativos: number
  inativos: number
}

export async function listarProdutos(params: ListarProdutosParams): Promise<Page<ProdutoSummary>> {
  const { data } = await apiClient.get<Page<ProdutoSummary>>('/produtos', { params })
  return data
}

export async function buscarProduto(id: string): Promise<ProdutoResponse> {
  const { data } = await apiClient.get<ProdutoResponse>(`/produtos/${id}`)
  return data
}

export async function criarProduto(payload: ProdutoRequest): Promise<ProdutoResponse> {
  const { data } = await apiClient.post<ProdutoResponse>('/produtos', payload)
  return data
}

export async function atualizarProduto(id: string, payload: ProdutoRequest): Promise<ProdutoResponse> {
  const { data } = await apiClient.put<ProdutoResponse>(`/produtos/${id}`, payload)
  return data
}

export async function atualizarStatusProduto(id: string, status: StatusProduto): Promise<void> {
  await apiClient.patch(`/produtos/${id}/status`, { status })
}

export async function excluirProduto(id: string): Promise<void> {
  await apiClient.delete(`/produtos/${id}`)
}

export async function buscarResumoProdutos(): Promise<ProdutoResumo> {
  const { data } = await apiClient.get<ProdutoResumo>('/produtos/resumo')
  return data
}
