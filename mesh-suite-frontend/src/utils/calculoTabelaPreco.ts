export type OperacaoAjuste = 'SOMAR' | 'SUBTRAIR'
export type TipoValorAjuste = 'REAL' | 'PERCENTUAL'
export type Arredondamento = 'NAO_ARREDONDAR' | 'TERMINAR_EM_0' | 'TERMINAR_EM_9' | 'TERMINAR_EM_90' | 'TERMINAR_EM_99'

export interface RegraAjuste {
  operacaoAjuste: OperacaoAjuste
  tipoValorAjuste: TipoValorAjuste
  valorAjuste: number
  arredondamento: Arredondamento
}

// Rounding always goes UP (never below the adjusted price). Every candidate
// value in a rounding rule's set is `k * period + offset` for integer k >= 0,
// expressed in cents to avoid floating-point drift:
//   NAO_ARREDONDAR:  no candidate set, value returned as-is (rounded to the cent)
//   TERMINAR_EM_0:   period=1000, offset=0    (...,100.00, 110.00, 120.00,...)
//   TERMINAR_EM_9:   period=1000, offset=900  (...,99.00, 109.00, 119.00,...)
//   TERMINAR_EM_90:  period=100,  offset=90   (...,ends in ,90)
//   TERMINAR_EM_99:  period=100,  offset=99   (...,ends in ,99)
const REGRAS_ARREDONDAMENTO: Record<Exclude<Arredondamento, 'NAO_ARREDONDAR'>, { period: number; offset: number }> = {
  TERMINAR_EM_0: { period: 1000, offset: 0 },
  TERMINAR_EM_9: { period: 1000, offset: 900 },
  TERMINAR_EM_90: { period: 100, offset: 90 },
  TERMINAR_EM_99: { period: 100, offset: 99 },
}

function arredondarParaCima(valor: number, arredondamento: Arredondamento): number {
  const centavos = Math.round(valor * 100)
  if (arredondamento === 'NAO_ARREDONDAR') {
    return centavos / 100
  }
  const { period, offset } = REGRAS_ARREDONDAMENTO[arredondamento]
  const k = Math.ceil((centavos - offset) / period)
  const alvoCentavos = k * period + offset
  return alvoCentavos / 100
}

export function calcularPrecoAjustado(precoBase: number, regra: RegraAjuste): number {
  let ajustado: number
  if (regra.operacaoAjuste === 'SOMAR') {
    ajustado = regra.tipoValorAjuste === 'REAL'
      ? precoBase + regra.valorAjuste
      : precoBase * (1 + regra.valorAjuste / 100)
  } else {
    ajustado = regra.tipoValorAjuste === 'REAL'
      ? precoBase - regra.valorAjuste
      : precoBase * (1 - regra.valorAjuste / 100)
  }
  return arredondarParaCima(ajustado, regra.arredondamento)
}
