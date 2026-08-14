import { describe, it, expect } from 'vitest'
import { calculateAdjustedPrice, type AdjustmentRule } from '../priceCalculation'

describe('calculateAdjustedPrice', () => {
  it('somar + real, sem arredondamento', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 10, rounding: 'NO_ROUNDING' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(110, 2)
  })

  it('somar + percentual, sem arredondamento', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'PERCENTAGE', adjustmentValue: 10, rounding: 'NO_ROUNDING' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(110, 2)
  })

  it('subtrair + real, sem arredondamento', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'SUBTRACT', adjustmentValueType: 'FIXED', adjustmentValue: 10, rounding: 'NO_ROUNDING' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(90, 2)
  })

  it('subtrair + percentual, sem arredondamento', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'SUBTRACT', adjustmentValueType: 'PERCENTAGE', adjustmentValue: 20, rounding: 'NO_ROUNDING' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(80, 2)
  })

  it('terminar em 0 arredonda pra cima', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_0' }
    expect(calculateAdjustedPrice(117.32, regra)).toBeCloseTo(120, 2)
  })

  it('terminar em 9 arredonda pra cima', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_9' }
    expect(calculateAdjustedPrice(117.32, regra)).toBeCloseTo(119, 2)
  })

  it('terminar em ,90 arredonda pra cima', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_90' }
    expect(calculateAdjustedPrice(117.32, regra)).toBeCloseTo(117.90, 2)
  })

  it('terminar em ,99 arredonda pra cima', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_99' }
    expect(calculateAdjustedPrice(117.32, regra)).toBeCloseTo(117.99, 2)
  })

  it('valor exato já na regra não muda', () => {
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'FIXED', adjustmentValue: 0, rounding: 'END_IN_0' }
    expect(calculateAdjustedPrice(120, regra)).toBeCloseTo(120, 2)
  })

  it('combina ajuste percentual com arredondamento terminar em 9', () => {
    // base 100, +12% = 112.00 -> arredonda pra próximo terminando em 9 (119.00)
    const regra: AdjustmentRule = { adjustmentOperation: 'ADD', adjustmentValueType: 'PERCENTAGE', adjustmentValue: 12, rounding: 'END_IN_9' }
    expect(calculateAdjustedPrice(100, regra)).toBeCloseTo(119, 2)
  })
})
