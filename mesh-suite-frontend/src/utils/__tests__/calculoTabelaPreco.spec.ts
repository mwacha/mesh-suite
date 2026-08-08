import { describe, it, expect } from 'vitest'
import { calcularPrecoAjustado, type RegraAjuste } from '../calculoTabelaPreco'

describe('calcularPrecoAjustado', () => {
  it('somar + real, sem arredondamento', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 10, arredondamento: 'NAO_ARREDONDAR' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(110, 2)
  })

  it('somar + percentual, sem arredondamento', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'PERCENTUAL', valorAjuste: 10, arredondamento: 'NAO_ARREDONDAR' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(110, 2)
  })

  it('subtrair + real, sem arredondamento', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SUBTRAIR', tipoValorAjuste: 'REAL', valorAjuste: 10, arredondamento: 'NAO_ARREDONDAR' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(90, 2)
  })

  it('subtrair + percentual, sem arredondamento', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SUBTRAIR', tipoValorAjuste: 'PERCENTUAL', valorAjuste: 20, arredondamento: 'NAO_ARREDONDAR' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(80, 2)
  })

  it('terminar em 0 arredonda pra cima', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_0' }
    expect(calcularPrecoAjustado(117.32, regra)).toBeCloseTo(120, 2)
  })

  it('terminar em 9 arredonda pra cima', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_9' }
    expect(calcularPrecoAjustado(117.32, regra)).toBeCloseTo(119, 2)
  })

  it('terminar em ,90 arredonda pra cima', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_90' }
    expect(calcularPrecoAjustado(117.32, regra)).toBeCloseTo(117.90, 2)
  })

  it('terminar em ,99 arredonda pra cima', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_99' }
    expect(calcularPrecoAjustado(117.32, regra)).toBeCloseTo(117.99, 2)
  })

  it('valor exato já na regra não muda', () => {
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'REAL', valorAjuste: 0, arredondamento: 'TERMINAR_EM_0' }
    expect(calcularPrecoAjustado(120, regra)).toBeCloseTo(120, 2)
  })

  it('combina ajuste percentual com arredondamento terminar em 9', () => {
    // base 100, +12% = 112.00 -> arredonda pra próximo terminando em 9 (119.00)
    const regra: RegraAjuste = { operacaoAjuste: 'SOMAR', tipoValorAjuste: 'PERCENTUAL', valorAjuste: 12, arredondamento: 'TERMINAR_EM_9' }
    expect(calcularPrecoAjustado(100, regra)).toBeCloseTo(119, 2)
  })
})
