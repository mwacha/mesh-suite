import { apiClient } from './client'

export type ModoSelecaoProdutos = 'TODOS_PRODUTOS' | 'SELECIONAR_PRODUTOS'
export type MetodoAjuste = 'AUTOMATICO' | 'MANUAL'
export type OperacaoAjuste = 'SOMAR' | 'SUBTRAIR'
export type TipoValorAjuste = 'REAL' | 'PERCENTUAL'
export type Arredondamento = 'NAO_ARREDONDAR' | 'TERMINAR_EM_0' | 'TERMINAR_EM_9' | 'TERMINAR_EM_90' | 'TERMINAR_EM_99'

export interface TabelaPrecoItemInput {
  produtoId: string
  precoNestaTabela: number | null
  percentualComissao: number | null
}

export interface TabelaPrecoItemResponse extends TabelaPrecoItemInput {
  produtoNome: string
  produtoSku: string
  precoCadastrado: number
}

export interface TabelaPrecoRequest {
  nome: string
  modoSelecaoProdutos: ModoSelecaoProdutos
  metodoAjuste: MetodoAjuste
  operacaoAjuste: OperacaoAjuste | null
  tipoValorAjuste: TipoValorAjuste | null
  valorAjuste: number | null
  arredondamento: Arredondamento
  inicioVigencia: string
  terminoVigencia: string | null
  valorMinimoVenda: number | null
  percentualComissaoPadrao: number | null
  ativo: boolean | null
  itens: TabelaPrecoItemInput[]
}

export interface TabelaPrecoResponse {
  id: string
  nome: string
  modoSelecaoProdutos: ModoSelecaoProdutos
  metodoAjuste: MetodoAjuste
  operacaoAjuste: OperacaoAjuste | null
  tipoValorAjuste: TipoValorAjuste | null
  valorAjuste: number | null
  arredondamento: Arredondamento
  inicioVigencia: string
  terminoVigencia: string | null
  valorMinimoVenda: number | null
  percentualComissaoPadrao: number | null
  ativo: boolean
  criadoEm: string
  itens: TabelaPrecoItemResponse[]
}

export interface TabelaPrecoSummary {
  id: string
  nome: string
  metodoAjuste: MetodoAjuste
  operacaoAjuste: OperacaoAjuste | null
  tipoValorAjuste: TipoValorAjuste | null
  valorAjuste: number | null
  inicioVigencia: string
  terminoVigencia: string | null
  ativo: boolean
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarTabelasPrecoParams {
  busca?: string
  ativo?: boolean
  page?: number
  size?: number
}

export async function listarTabelasPreco(params: ListarTabelasPrecoParams): Promise<Page<TabelaPrecoSummary>> {
  const { data } = await apiClient.get<Page<TabelaPrecoSummary>>('/tabelas-preco', { params })
  return data
}

export async function buscarTabelaPreco(id: string): Promise<TabelaPrecoResponse> {
  const { data } = await apiClient.get<TabelaPrecoResponse>(`/tabelas-preco/${id}`)
  return data
}

export async function criarTabelaPreco(payload: TabelaPrecoRequest): Promise<TabelaPrecoResponse> {
  const { data } = await apiClient.post<TabelaPrecoResponse>('/tabelas-preco', payload)
  return data
}

export async function atualizarTabelaPreco(id: string, payload: TabelaPrecoRequest): Promise<TabelaPrecoResponse> {
  const { data } = await apiClient.put<TabelaPrecoResponse>(`/tabelas-preco/${id}`, payload)
  return data
}

export async function excluirTabelaPreco(id: string): Promise<void> {
  await apiClient.delete(`/tabelas-preco/${id}`)
}
