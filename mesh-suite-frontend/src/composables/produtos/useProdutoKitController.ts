import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  listarProdutos,
  criarProdutoKit,
  type ProdutoSummary,
  type UnidadeMedida,
  type StatusProduto,
} from '@/api/produtos'
import { mensagemErroSalvarProduto } from './erroSalvarProduto'

interface ItemKit {
  produtoId: string
  nome: string
  sku: string
  quantidade: number
  precoVenda: number
}

interface FormKit {
  nome: string
  sku: string
  codigoBarras: string
  unidadeMedida: UnidadeMedida
  status: StatusProduto
  descricao: string
}

function novoFormulario(): FormKit {
  return {
    nome: '',
    sku: '',
    codigoBarras: '',
    unidadeMedida: 'UN',
    status: 'ATIVO',
    descricao: '',
  }
}

/** Controller for the Produto Kit screen: owns the kit's own fields, the
 * item search/composition (reusing listarProdutos from the Produto Simples
 * service) and persistence, keeping ProdutoKitFormView.vue markup-only. */
export function useProdutoKitController() {
  const router = useRouter()

  const form = reactive<FormKit>(novoFormulario())
  const itens = ref<ItemKit[]>([])
  const erros = reactive<{ nome?: string; sku?: string; itens?: string }>({})
  const erroGeral = ref('')
  const salvando = ref(false)

  const buscaAberta = ref(false)
  const termoBusca = ref('')
  const buscando = ref(false)
  const resultadosBusca = ref<ProdutoSummary[]>([])

  const totalKit = computed(() => itens.value.reduce((acc, it) => acc + it.quantidade * it.precoVenda, 0))
  const totalItens = computed(() => itens.value.reduce((acc, it) => acc + it.quantidade, 0))

  async function buscarProdutosParaAdicionar() {
    buscando.value = true
    try {
      const pagina = await listarProdutos({ busca: termoBusca.value, size: 5 })
      resultadosBusca.value = pagina.content
    } finally {
      buscando.value = false
    }
  }

  function adicionarItem(produto: ProdutoSummary) {
    const existente = itens.value.find((it) => it.produtoId === produto.id)
    if (existente) {
      existente.quantidade += 1
    } else {
      itens.value.push({
        produtoId: produto.id,
        nome: produto.nome,
        sku: produto.sku,
        quantidade: 1,
        precoVenda: produto.precoVenda,
      })
    }
    buscaAberta.value = false
    termoBusca.value = ''
    resultadosBusca.value = []
  }

  function removerItem(index: number) {
    itens.value.splice(index, 1)
  }

  function incrementarQuantidade(index: number) {
    itens.value[index].quantidade += 1
  }

  function decrementarQuantidade(index: number) {
    const item = itens.value[index]
    item.quantidade = Math.max(1, item.quantidade - 1)
  }

  function validar(): boolean {
    erros.nome = form.nome.trim() ? undefined : 'Campo obrigatório'
    erros.sku = form.sku.trim() ? undefined : 'Campo obrigatório'
    erros.itens = itens.value.length > 0 ? undefined : 'Adicione ao menos um produto ao kit'
    return !erros.nome && !erros.sku && !erros.itens
  }

  async function salvar() {
    erroGeral.value = ''
    if (!validar()) {
      return
    }
    salvando.value = true
    try {
      await criarProdutoKit({
        ...form,
        itens: itens.value.map((it) => ({ produtoId: it.produtoId, quantidade: it.quantidade })),
      })
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
    form,
    itens,
    erros,
    erroGeral,
    salvando,
    buscaAberta,
    termoBusca,
    buscando,
    resultadosBusca,
    totalKit,
    totalItens,
    buscarProdutosParaAdicionar,
    adicionarItem,
    removerItem,
    incrementarQuantidade,
    decrementarQuantidade,
    salvar,
    cancelar,
  }
}
