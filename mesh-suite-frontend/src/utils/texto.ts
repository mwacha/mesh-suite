// \p{Mn} matches any Unicode "nonspacing mark" -- the combining diacritics
// that NFD decomposition splits accented letters into (e.g. "a" + combining
// tilde). Stripping them is what turns "São" into "Sao".
const MARCAS_DIACRITICAS = /\p{Mn}/gu

/** Lowercases and strips diacritics (e.g. "São Paulo" -> "sao paulo"), so callers can do
 * accent- and case-insensitive comparisons/searches. */
export function normalizarTexto(valor: string): string {
  return valor.normalize('NFD').replace(MARCAS_DIACRITICAS, '').toLowerCase()
}
