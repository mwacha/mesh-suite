export interface EnderecoViaCep {
  logradouro: string
  bairro: string
  localidade: string
  uf: string
}

export async function buscarEnderecoPorCep(cep: string): Promise<EnderecoViaCep | null> {
  const cepLimpo = cep.replace(/\D/g, '')
  if (cepLimpo.length !== 8) {
    return null
  }

  let response: Response
  try {
    response = await fetch(`https://viacep.com.br/ws/${cepLimpo}/json/`)
  } catch {
    return null
  }
  if (!response.ok) {
    return null
  }

  let data: any
  try {
    data = await response.json()
  } catch {
    return null
  }
  if (data.erro) {
    return null
  }
  return {
    logradouro: data.logradouro,
    bairro: data.bairro,
    localidade: data.localidade,
    uf: data.uf,
  }
}
