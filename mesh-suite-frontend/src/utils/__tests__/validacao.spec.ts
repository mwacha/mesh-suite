import { describe, it, expect } from 'vitest'
import { emailValido, emailsValidos, telefoneValido, documentoValido, cepValido } from '../validacao'

describe('emailValido', () => {
  it('accepts a well-formed email', () => {
    expect(emailValido('financeiro@mercadosilva.com.br')).toBe(true)
  })

  it('rejects missing @ or domain', () => {
    expect(emailValido('financeiro-mercadosilva.com')).toBe(false)
    expect(emailValido('financeiro@')).toBe(false)
    expect(emailValido('')).toBe(false)
  })
})

describe('emailsValidos', () => {
  it('accepts an empty value (email is optional)', () => {
    expect(emailsValidos('')).toBe(true)
  })

  it('accepts multiple comma-separated valid emails', () => {
    expect(emailsValidos('a@x.com, b@y.com.br')).toBe(true)
  })

  it('rejects if any of the comma-separated emails is invalid', () => {
    expect(emailsValidos('a@x.com, not-an-email')).toBe(false)
  })
})

describe('telefoneValido', () => {
  it('accepts a 10-digit landline', () => {
    expect(telefoneValido('(11) 3333-4444')).toBe(true)
  })

  it('accepts an 11-digit mobile', () => {
    expect(telefoneValido('(11) 93333-4444')).toBe(true)
  })

  it('rejects incomplete numbers', () => {
    expect(telefoneValido('(11) 333')).toBe(false)
  })
})

describe('documentoValido', () => {
  it('accepts an 11-digit CPF for INDIVIDUAL', () => {
    expect(documentoValido('111.222.333-44', 'INDIVIDUAL')).toBe(true)
  })

  it('accepts a 14-digit CNPJ for LEGAL_ENTITY', () => {
    expect(documentoValido('11.222.333/0001-44', 'LEGAL_ENTITY')).toBe(true)
  })

  it('rejects the wrong digit count for the given tipoPessoa', () => {
    expect(documentoValido('111.222.333-44', 'LEGAL_ENTITY')).toBe(false)
    expect(documentoValido('11.222.333/0001-44', 'INDIVIDUAL')).toBe(false)
  })
})

describe('cepValido', () => {
  it('accepts an 8-digit CEP', () => {
    expect(cepValido('01310-100')).toBe(true)
  })

  it('rejects an incomplete CEP', () => {
    expect(cepValido('01310')).toBe(false)
  })
})
