import { apiClient } from './client'

export type StatusProduto = 'ATIVO' | 'INATIVO'
export type UnidadeMedida = 'UN' | 'KG' | 'G' | 'L' | 'ML' | 'MT' | 'CM' | 'CX' | 'PC' | 'PAR' | 'DZ'
export type ProdutoTipo = 'PRODUCT' | 'PRODUCT_KIT' | 'VARIATION_PARENT' | 'VARIATION_CHILD'

export interface ProdutoRequest {
  nome: string
  sku: string
  codigoBarras: string
  marca: string
  categoria: string
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
}

export interface ProdutoSummary {
  id: string
  nome: string
  sku: string
  marca: string
  precoVenda: number
  quantidadeEstoque: number
  status: StatusProduto
  tipo: ProdutoTipo
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

// ── Produto Kit ──────────────────────────────────────────────────────────
// Composição reaproveita ProdutoSummary (via listarProdutos) para buscar
// produtos simples a adicionar ao kit, em vez de duplicar um endpoint de busca.

export interface ProdutoKitItemRequest {
  produtoId: string
  quantidade: number
}

export interface ProdutoKitRequest {
  nome: string
  sku: string
  codigoBarras: string
  unidadeMedida: UnidadeMedida
  status: StatusProduto
  descricao: string
  itens: ProdutoKitItemRequest[]
}

export interface ProdutoKitResponse extends ProdutoKitRequest {
  id: string
  precoVenda: number
}

export async function criarProdutoKit(payload: ProdutoKitRequest): Promise<ProdutoKitResponse> {
  const { data } = await apiClient.post<ProdutoKitResponse>('/produtos/kits', payload)
  return data
}

// ── Produto com Variação ────────────────────────────────────────────────

export interface TipoVariacaoRequest {
  nome: string
  valores: string[]
}

export interface VarianteRequest {
  /** Um valor por tipo de variação, na mesma ordem de `tiposVariacao`. */
  combinacao: string[]
  sku: string
  codigoBarras: string
  precoVenda: number
  precoCusto: number | null
  quantidadeEstoque: number
  estoqueMinimo: number | null
  estoqueMaximo: number | null
  peso: number | null
  comprimento: number | null
  largura: number | null
  altura: number | null
}

export interface ProdutoVariacaoRequest {
  nome: string
  sku: string
  marca: string
  categoria: string
  precoVenda: number
  status: StatusProduto
  descricao: string
  unidadeMedida: UnidadeMedida
  tiposVariacao: TipoVariacaoRequest[]
  variantes: VarianteRequest[]
}

export interface ProdutoVariacaoResponse extends ProdutoVariacaoRequest {
  id: string
}

export async function criarProdutoVariacao(payload: ProdutoVariacaoRequest): Promise<ProdutoVariacaoResponse> {
  const { data } = await apiClient.post<ProdutoVariacaoResponse>('/produtos/variacoes', payload)
  return data
}

export async function buscarProdutoVariacao(id: string): Promise<ProdutoVariacaoResponse> {
  const { data } = await apiClient.get<ProdutoVariacaoResponse>(`/produtos/variacoes/${id}`)
  return data
}

export async function atualizarProdutoVariacao(
  id: string,
  payload: ProdutoVariacaoRequest,
): Promise<ProdutoVariacaoResponse> {
  const { data } = await apiClient.put<ProdutoVariacaoResponse>(`/produtos/variacoes/${id}`, payload)
  return data
}
