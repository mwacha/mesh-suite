import { describe, it, expect } from 'vitest'
import { maskTelefone, maskCep, maskCpf, maskCnpj, maskDocumento } from '../masks'

describe('maskTelefone', () => {
  it('formats progressively as digits are typed', () => {
    expect(maskTelefone('1')).toBe('(1')
    expect(maskTelefone('11')).toBe('(11')
    expect(maskTelefone('1133')).toBe('(11) 33')
    expect(maskTelefone('113334')).toBe('(11) 3334')
  })

  it('formats a landline (10 digits) as (XX) XXXX-XXXX', () => {
    expect(maskTelefone('1133334444')).toBe('(11) 3333-4444')
  })

  it('formats a mobile (11 digits) as (XX) XXXXX-XXXX', () => {
    expect(maskTelefone('11933334444')).toBe('(11) 93333-4444')
  })

  it('strips existing punctuation and re-masks', () => {
    expect(maskTelefone('(11) 93333-4444')).toBe('(11) 93333-4444')
  })

  it('caps input at 11 digits', () => {
    expect(maskTelefone('119333344449999')).toBe('(11) 93333-4444')
  })

  it('returns empty string for empty input', () => {
    expect(maskTelefone('')).toBe('')
  })
})

describe('maskCep', () => {
  it('formats as 00000-000', () => {
    expect(maskCep('01310100')).toBe('01310-100')
  })

  it('formats progressively', () => {
    expect(maskCep('01310')).toBe('01310')
    expect(maskCep('013101')).toBe('01310-1')
  })
})

describe('maskCpf', () => {
  it('formats as 000.000.000-00', () => {
    expect(maskCpf('11122233344')).toBe('111.222.333-44')
  })

  it('formats progressively', () => {
    expect(maskCpf('111')).toBe('111')
    expect(maskCpf('111222')).toBe('111.222')
    expect(maskCpf('111222333')).toBe('111.222.333')
  })
})

describe('maskCnpj', () => {
  it('formats as 00.000.000/0000-00', () => {
    expect(maskCnpj('11222333000144')).toBe('11.222.333/0001-44')
  })

  it('formats progressively', () => {
    expect(maskCnpj('11')).toBe('11')
    expect(maskCnpj('11222')).toBe('11.222')
    expect(maskCnpj('11222333')).toBe('11.222.333')
    expect(maskCnpj('112223330001')).toBe('11.222.333/0001')
  })
})

describe('maskDocumento', () => {
  it('uses the CPF mask for INDIVIDUAL', () => {
    expect(maskDocumento('11122233344', 'INDIVIDUAL')).toBe('111.222.333-44')
  })

  it('uses the CNPJ mask for LEGAL_ENTITY', () => {
    expect(maskDocumento('11222333000144', 'LEGAL_ENTITY')).toBe('11.222.333/0001-44')
  })
})
