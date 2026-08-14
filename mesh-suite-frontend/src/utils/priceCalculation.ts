export type AdjustmentOperation = 'ADD' | 'SUBTRACT'
export type AdjustmentValueType = 'FIXED' | 'PERCENTAGE'
export type Rounding = 'NO_ROUNDING' | 'END_IN_0' | 'END_IN_9' | 'END_IN_90' | 'END_IN_99'

export interface AdjustmentRule {
  adjustmentOperation: AdjustmentOperation
  adjustmentValueType: AdjustmentValueType
  adjustmentValue: number
  rounding: Rounding
}

// Rounding always goes UP (never below the adjusted price). Every candidate
// value in a rounding rule's set is `k * period + offset` for integer k >= 0,
// expressed in cents to avoid floating-point drift:
//   NO_ROUNDING:  no candidate set, value returned as-is (rounded to the cent)
//   END_IN_0:   period=1000, offset=0    (...,100.00, 110.00, 120.00,...)
//   END_IN_9:   period=1000, offset=900  (...,99.00, 109.00, 119.00,...)
//   END_IN_90:  period=100,  offset=90   (...,ends in ,90)
//   END_IN_99:  period=100,  offset=99   (...,ends in ,99)
const ROUNDING_RULES: Record<Exclude<Rounding, 'NO_ROUNDING'>, { period: number; offset: number }> = {
  END_IN_0: { period: 1000, offset: 0 },
  END_IN_9: { period: 1000, offset: 900 },
  END_IN_90: { period: 100, offset: 90 },
  END_IN_99: { period: 100, offset: 99 },
}

function roundUp(valor: number, rounding: Rounding): number {
  const centavos = Math.round(valor * 100)
  if (rounding === 'NO_ROUNDING') {
    return centavos / 100
  }
  const { period, offset } = ROUNDING_RULES[rounding]
  const k = Math.ceil((centavos - offset) / period)
  const alvoCentavos = k * period + offset
  return alvoCentavos / 100
}

export function calculateAdjustedPrice(precoBase: number, regra: AdjustmentRule): number {
  let ajustado: number
  if (regra.adjustmentOperation === 'ADD') {
    ajustado = regra.adjustmentValueType === 'FIXED'
      ? precoBase + regra.adjustmentValue
      : precoBase * (1 + regra.adjustmentValue / 100)
  } else {
    ajustado = regra.adjustmentValueType === 'FIXED'
      ? precoBase - regra.adjustmentValue
      : precoBase * (1 - regra.adjustmentValue / 100)
  }
  return roundUp(ajustado, regra.rounding)
}
