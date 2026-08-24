import { reactive, ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  criarProdutoVariacao,
  buscarProdutoVariacao,
  atualizarProdutoVariacao,
  type UnidadeMedida,
  type StatusProduto,
} from '@/api/produtos'
import { mensagemErroSalvarProduto } from './erroSalvarProduto'

export interface TipoVariacaoLocal {
  id: string
  nome: string
  valores: string[]
}

export interface VarianteEditavel {
  combinacao: string[]
  sku: string
  codigoBarras: string
  precoVenda: number
  precoCusto: number | null
  quantidadeEstoque: number
  estoqueMinimo: number | null
  estoqueMaximo: number | null
  peso: number | null
  comprimento: number | null
  largura: number | null
  altura: number | null
}

interface FormVariacao {
  nome: string
  sku: string
  marca: string
  categoria: string
  precoVenda: number
  status: StatusProduto
  descricao: string
  unidadeMedida: UnidadeMedida
}

function novoFormulario(): FormVariacao {
  return {
    nome: '',
    sku: '',
    marca: '',
    categoria: '',
    precoVenda: 0,
    status: 'ATIVO',
    descricao: '',
    unidadeMedida: 'UN',
  }
}

function gerarCombinacoes(tipos: TipoVariacaoLocal[]): string[][] {
  if (tipos.length === 0) return []
  let resultado: string[][] = [[]]
  for (const tipo of tipos) {
    const proximo: string[][] = []
    for (const combo of resultado) {
      for (const valor of tipo.valores) {
        proximo.push([...combo, valor])
      }
    }
    resultado = proximo
  }
  return resultado
}

/** Controller for the Produto com Variação screen: owns the base fields, the
 * variation types/values, the auto-generated combinations table and the
 * variant edit panel, keeping ProdutoVariacaoFormView.vue markup-only. */
export function useProdutoVariacaoController() {
  const route = useRoute()
  const router = useRouter()

  const modoEdicao = computed(() => typeof route.params.id === 'string')

  const form = reactive<FormVariacao>(novoFormulario())
  const erros = reactive<{
    nome?: string
    sku?: string
    precoVenda?: string
    tiposVariacao?: string
    variantes?: string
  }>({})
  const erroGeral = ref('')
  const salvando = ref(false)

  const tiposVariacao = ref<TipoVariacaoLocal[]>([])
  // Keyed by combinacao.join('|') so per-combination edits (SKU, preço,
  // estoque...) survive re-derivation of the combinations table when types
  // or values change.
  const variantesDados = reactive<Record<string, VarianteEditavel>>({})

  function criarVarianteDefault(combinacao: string[]): VarianteEditavel {
    return {
      combinacao,
      sku: `${form.sku || 'SKU'}-${combinacao.join('-')}`,
      codigoBarras: '',
      precoVenda: Number(form.precoVenda) || 0,
      precoCusto: null,
      quantidadeEstoque: 0,
      estoqueMinimo: null,
      estoqueMaximo: null,
      peso: null,
      comprimento: null,
      largura: null,
      altura: null,
    }
  }

  watch(
    () => gerarCombinacoes(tiposVariacao.value),
    (novasCombinacoes) => {
      const chavesValidas = new Set(novasCombinacoes.map((c) => c.join('|')))
      for (const chave of Object.keys(variantesDados)) {
        if (!chavesValidas.has(chave)) delete variantesDados[chave]
      }
      for (const combinacao of novasCombinacoes) {
        const chave = combinacao.join('|')
        if (!variantesDados[chave]) {
          variantesDados[chave] = criarVarianteDefault(combinacao)
        }
      }
    },
    { deep: true, immediate: true },
  )

  const variantesGeradas = computed(() =>
    gerarCombinacoes(tiposVariacao.value).map((combinacao) => variantesDados[combinacao.join('|')]),
  )

  onMounted(async () => {
    const id = route.params.id
    if (typeof id !== 'string') return
    try {
      const produto = await buscarProdutoVariacao(id)
      Object.assign(form, {
        nome: produto.nome,
        sku: produto.sku,
        marca: produto.marca,
        categoria: produto.categoria,
        precoVenda: produto.precoVenda,
        status: produto.status,
        descricao: produto.descricao,
        unidadeMedida: produto.unidadeMedida,
      })
      // Populate variantesDados with the loaded values *before* assigning
      // tiposVariacao below -- the watch above regenerates any combinação
      // missing from variantesDados, and it only skips ones already present
      // (`if (!variantesDados[chave])`), so this order keeps it from
      // clobbering real saved data with criarVarianteDefault() placeholders.
      for (const variante of produto.variantes) {
        variantesDados[variante.combinacao.join('|')] = { ...variante, codigoBarras: variante.codigoBarras ?? '' }
      }
      tiposVariacao.value = produto.tiposVariacao.map((t) => ({ id: crypto.randomUUID(), ...t }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do produto.'
    }
  })

  function adicionarTipo(nome: string) {
    if (!nome.trim()) return
    tiposVariacao.value.push({ id: crypto.randomUUID(), nome: nome.trim(), valores: [] })
  }

  function removerTipo(id: string) {
    tiposVariacao.value = tiposVariacao.value.filter((t) => t.id !== id)
  }

  function adicionarValor(tipoId: string, valor: string) {
    const tipo = tiposVariacao.value.find((t) => t.id === tipoId)
    if (tipo && valor.trim() && !tipo.valores.includes(valor.trim())) {
      tipo.valores.push(valor.trim())
    }
  }

  function removerValor(tipoId: string, valor: string) {
    const tipo = tiposVariacao.value.find((t) => t.id === tipoId)
    if (tipo) {
      tipo.valores = tipo.valores.filter((v) => v !== valor)
    }
  }

  const confirmacao = ref<{ mensagem: string; executar: () => void } | null>(null)

  function confirmarRemocaoTipo(tipo: TipoVariacaoLocal) {
    confirmacao.value = {
      mensagem: `Remover o tipo "${tipo.nome}" e todas as suas combinações geradas?`,
      executar: () => removerTipo(tipo.id),
    }
  }

  function confirmarRemocaoValor(tipo: TipoVariacaoLocal, valor: string) {
    confirmacao.value = {
      mensagem: `Remover o valor "${valor}" do tipo "${tipo.nome}"?`,
      executar: () => removerValor(tipo.id, valor),
    }
  }

  function confirmar() {
    confirmacao.value?.executar()
    confirmacao.value = null
  }

  function cancelarConfirmacao() {
    confirmacao.value = null
  }

  const editingVariant = ref<VarianteEditavel | null>(null)

  function abrirEdicaoVariante(variante: VarianteEditavel) {
    editingVariant.value = variante
  }

  // Editing binds directly to the same reactive object stored in
  // variantesDados (there's no separate persistence step yet), so Cancelar
  // and Salvar both just close the panel.
  function fecharEdicaoVariante() {
    editingVariant.value = null
  }

  // Matches the `sku`/`codigo_barras` VARCHAR(50) columns shared by every
  // produto row (see V6__create_produto.sql) -- variant SKUs are
  // auto-generated by concatenating the base SKU with the combination
  // (criarVarianteDefault above), so this easily overflows without the
  // user typing anything unusually long themselves.
  const SKU_MAX_LENGTH = 50
  // Matches @DecimalMin("0.01") on precoVenda in ProdutoVariacaoRequest/
  // VarianteRequest -- the base form and every generated variant default
  // their preço to 0 (novoFormulario / criarVarianteDefault above), which
  // the backend rejects, so this has to be caught before submit too.
  const PRECO_MIN = 0.01

  function validar(): boolean {
    erros.nome = form.nome.trim() ? undefined : 'Campo obrigatório'
    erros.sku = form.sku.trim()
      ? form.sku.trim().length > SKU_MAX_LENGTH
        ? `O SKU pode ter no máximo ${SKU_MAX_LENGTH} caracteres`
        : undefined
      : 'Campo obrigatório'
    erros.precoVenda = Number(form.precoVenda) >= PRECO_MIN ? undefined : 'Informe um preço de venda válido'
    erros.tiposVariacao = tiposVariacao.value.some((t) => t.valores.length > 0)
      ? undefined
      : 'Adicione ao menos um tipo de variação com valores'

    const varianteMuitoLonga = variantesGeradas.value.find(
      (v) => v.sku.length > SKU_MAX_LENGTH || v.codigoBarras.length > SKU_MAX_LENGTH,
    )
    const varianteSemPreco = variantesGeradas.value.find((v) => Number(v.precoVenda) < PRECO_MIN)
    erros.variantes = varianteMuitoLonga
      ? `O SKU e o código de barra de cada variante podem ter no máximo ${SKU_MAX_LENGTH} caracteres. Edite a variante "${varianteMuitoLonga.combinacao.join(' / ')}".`
      : varianteSemPreco
        ? `Informe um preço de venda válido para a variante "${varianteSemPreco.combinacao.join(' / ')}".`
        : undefined

    return !erros.nome && !erros.sku && !erros.precoVenda && !erros.tiposVariacao && !erros.variantes
  }

  async function salvar() {
    erroGeral.value = ''
    if (!validar()) {
      return
    }
    salvando.value = true
    try {
      const payload = {
        nome: form.nome,
        sku: form.sku,
        marca: form.marca,
        categoria: form.categoria,
        precoVenda: Number(form.precoVenda) || 0,
        status: form.status,
        descricao: form.descricao,
        unidadeMedida: form.unidadeMedida,
        tiposVariacao: tiposVariacao.value.map((t) => ({ nome: t.nome, valores: t.valores })),
        variantes: variantesGeradas.value.map((v) => ({ ...v })),
      }
      const id = route.params.id
      if (typeof id === 'string') {
        await atualizarProdutoVariacao(id, payload)
      } else {
        await criarProdutoVariacao(payload)
      }
      router.push({ name: 'produtos' })
    } catch (err: any) {
      erroGeral.value = mensagemErroSalvarProduto(err)
    } finally {
      salvando.value = false
    }
  }

  function cancelar() {
    router.push({ name: 'produtos' })
  }

  return {
    modoEdicao,
    form,
    erros,
    erroGeral,
    salvando,
    tiposVariacao,
    variantesGeradas,
    adicionarTipo,
    confirmarRemocaoTipo,
    adicionarValor,
    confirmarRemocaoValor,
    confirmacao,
    confirmar,
    cancelarConfirmacao,
    editingVariant,
    abrirEdicaoVariante,
    fecharEdicaoVariante,
    salvar,
    cancelar,
  }
}
