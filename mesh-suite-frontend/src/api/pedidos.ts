import { apiClient } from './client'

export type StatusPedido = 'DIGITADO' | 'EM_PREPARO' | 'FATURADO'

export interface ItemPedidoRequest {
  produtoId: string
  quantidade: number
  valorUnitario: number
}

export interface ItemPedidoResponse extends ItemPedidoRequest {
  produtoNome: string
  valorTotal: number
}

export interface PedidoRequest {
  clienteId: string
  vendedorId: string
  dataPedido: string
  dataEntrega: string | null
  desconto: number
  itens: ItemPedidoRequest[]
}

export interface PedidoResponse {
  id: string
  numero: number
  clienteId: string
  clienteNome: string
  vendedorId: string
  vendedorNome: string
  dataPedido: string
  dataEntrega: string | null
  status: StatusPedido
  desconto: number
  subtotal: number
  total: number
  itens: ItemPedidoResponse[]
}

export interface PedidoSummary {
  id: string
  numero: number
  clienteNome: string
  vendedorNome: string
  dataPedido: string
  total: number
  status: StatusPedido
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ListarPedidosParams {
  busca?: string
  status?: StatusPedido
  page?: number
  size?: number
  sort?: string
}

export interface PedidoResumo {
  total: number
  digitados: number
  emPreparo: number
  faturados: number
}

export async function listarPedidos(params: ListarPedidosParams): Promise<Page<PedidoSummary>> {
  const { data } = await apiClient.get<Page<PedidoSummary>>('/pedidos', { params })
  return data
}

export async function buscarPedido(id: string): Promise<PedidoResponse> {
  const { data } = await apiClient.get<PedidoResponse>(`/pedidos/${id}`)
  return data
}

export async function criarPedido(payload: PedidoRequest): Promise<PedidoResponse> {
  const { data } = await apiClient.post<PedidoResponse>('/pedidos', payload)
  return data
}

export async function atualizarPedido(id: string, payload: PedidoRequest): Promise<PedidoResponse> {
  const { data } = await apiClient.put<PedidoResponse>(`/pedidos/${id}`, payload)
  return data
}

export async function avancarStatusPedido(id: string, status: StatusPedido): Promise<void> {
  await apiClient.patch(`/pedidos/${id}/status`, { status })
}

export async function excluirPedido(id: string): Promise<void> {
  await apiClient.delete(`/pedidos/${id}`)
}

export async function buscarResumoPedidos(): Promise<PedidoResumo> {
  const { data } = await apiClient.get<PedidoResumo>('/pedidos/resumo')
  return data
}
