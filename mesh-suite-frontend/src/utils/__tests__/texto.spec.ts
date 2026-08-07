import { describe, it, expect } from 'vitest'
import { normalizarTexto } from '../texto'

describe('normalizarTexto', () => {
  it('lowercases the text', () => {
    expect(normalizarTexto('SÃO PAULO')).toBe('sao paulo')
  })

  it('strips diacritics', () => {
    expect(normalizarTexto('São Paulo')).toBe('sao paulo')
    expect(normalizarTexto('Ribeirão Preto')).toBe('ribeirao preto')
    expect(normalizarTexto('Florianópolis')).toBe('florianopolis')
    expect(normalizarTexto('Niterói')).toBe('niteroi')
    expect(normalizarTexto('Açu')).toBe('acu')
  })

  it('leaves plain ascii text unchanged besides casing', () => {
    expect(normalizarTexto('Campinas')).toBe('campinas')
  })
})
