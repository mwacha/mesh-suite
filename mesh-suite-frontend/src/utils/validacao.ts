const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function emailValido(email: string): boolean {
  return EMAIL_REGEX.test(email.trim())
}

/** `emails` may hold several comma-separated addresses (see the "E-mail(s)" field). */
export function emailsValidos(emails: string): boolean {
  const lista = emails
    .split(',')
    .map((e) => e.trim())
    .filter((e) => e.length > 0)
  if (lista.length === 0) {
    return true
  }
  return lista.every(emailValido)
}

/** Accepts Brazilian landline (10 digits) or mobile (11 digits) numbers. */
export function telefoneValido(telefone: string): boolean {
  const digitos = telefone.replace(/\D/g, '')
  return digitos.length === 10 || digitos.length === 11
}

export function documentoValido(documento: string, tipoPessoa: 'INDIVIDUAL' | 'LEGAL_ENTITY'): boolean {
  const digitos = documento.replace(/\D/g, '')
  return digitos.length === (tipoPessoa === 'LEGAL_ENTITY' ? 14 : 11)
}

export function cepValido(cep: string): boolean {
  return cep.replace(/\D/g, '').length === 8
}
