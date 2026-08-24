/** Maps a failed save request (Simples, Kit or Variação) to the user-facing
 * message. Shared by the three produto controllers so the same HTTP-status
 * mapping isn't reimplemented per screen. */
export function mensagemErroSalvarProduto(err: any): string {
  if (err?.response?.status === 409) {
    return 'Já existe um produto cadastrado com este SKU.'
  }
  if (err?.response?.status === 403) {
    return 'Você não tem permissão para executar esta ação.'
  }
  if (err?.response?.status === 400) {
    return err.response.data?.mensagem ?? 'Verifique os dados informados.'
  }
  return 'Não foi possível salvar. Tente novamente em instantes.'
}
