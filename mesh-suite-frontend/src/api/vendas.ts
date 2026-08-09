import { apiClient } from './client'

export interface ItemVendaResponse {
  produtoId: string
  produtoNome: string
  quantidade: number
  valorUnitario: number
  valorTotal: number
  valorIcms: number
  valorIpi: number
  valorPis: number
  valorCofins: number
}

export interface VendaResponse {
  id: string
  numero: number
  pedidoId: string
  pedidoNumero: number
  clienteId: string
  clienteNome: string
  vendedorId: string
  vendedorNome: string
  dataEmissao: string
  desconto: number
  subtotal: number
  total: number
  valorIcms: number
  valorIpi: number
  valorPis: number
  valorCofins: number
  itens: ItemVendaResponse[]
}

export interface VendaSummary {
  id: string
  numero: number
  clienteNome: string
  dataEmissao: string
  total: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarVendasParams {
  busca?: string
  page?: number
  size?: number
  sort?: string
}

export async function listarVendas(params: ListarVendasParams): Promise<Page<VendaSummary>> {
  const { data } = await apiClient.get<Page<VendaSummary>>('/vendas', { params })
  return data
}

export async function buscarVenda(id: string): Promise<VendaResponse> {
  const { data } = await apiClient.get<VendaResponse>(`/vendas/${id}`)
  return data
}

export async function faturarPedido(pedidoId: string): Promise<VendaResponse> {
  const { data } = await apiClient.post<VendaResponse>(`/vendas/faturar/${pedidoId}`)
  return data
}
