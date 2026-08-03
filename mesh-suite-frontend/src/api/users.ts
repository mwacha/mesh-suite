import { apiClient } from './client'

export interface SalesRep {
  id: string
  name: string
}

export async function listSalesReps(): Promise<SalesRep[]> {
  const { data } = await apiClient.get<SalesRep[]>('/users/sales-reps')
  return data
}
