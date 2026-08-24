import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  buscarProduto,
  criarProduto,
  atualizarProduto,
  type ProdutoRequest,
} from '@/api/produtos'
import { mensagemErroSalvarProduto } from './erroSalvarProduto'

function novoFormulario(): ProdutoRequest {
  return {
    nome: '',
    sku: '',
    codigoBarras: '',
    marca: '',
    categoria: '',
    precoVenda: 0,
    precoCusto: null,
    status: 'ATIVO',
    descricao: '',
    quantidadeEstoque: 0,
    unidadeMedida: 'UN',
    estoqueMinimo: null,
    estoqueMaximo: null,
    peso: null,
    comprimento: null,
    largura: null,
    altura: null,
  }
}

function numeroOuNull(valor: unknown): number | null {
  return valor === '' || valor === null || valor === undefined ? null : Number(valor)
}

/** Controller for the Produto Simples screen: owns form state, validation and
 * persistence, keeping ProdutoSimplesFormView.vue limited to markup/bindings. */
export function useProdutoSimplesController() {
  const route = useRoute()
  const router = useRouter()

  const modoEdicao = computed(() => typeof route.params.id === 'string')

  const form = reactive<ProdutoRequest>(novoFormulario())
  const erros = reactive<{ nome?: string; sku?: string; precoVenda?: string }>({})
  const erroGeral = ref('')
  const salvando = ref(false)

  onMounted(async () => {
    const id = route.params.id
    if (typeof id === 'string') {
      try {
        const produto = await buscarProduto(id)
        Object.assign(form, produto)
      } catch {
        erroGeral.value = 'Não foi possível carregar os dados do produto.'
      }
    }
  })

  function validar(): boolean {
    erros.nome = form.nome.trim() ? undefined : 'Campo obrigatório'
    erros.sku = form.sku.trim() ? undefined : 'Campo obrigatório'
    erros.precoVenda = Number(form.precoVenda) > 0 ? undefined : 'Informe um preço maior que zero'
    return !erros.nome && !erros.sku && !erros.precoVenda
  }

  function paraPayload(): ProdutoRequest {
    return {
      ...form,
      precoVenda: Number(form.precoVenda) || 0,
      precoCusto: numeroOuNull(form.precoCusto),
      quantidadeEstoque: Number(form.quantidadeEstoque) || 0,
      estoqueMinimo: numeroOuNull(form.estoqueMinimo),
      estoqueMaximo: numeroOuNull(form.estoqueMaximo),
      peso: numeroOuNull(form.peso),
      comprimento: numeroOuNull(form.comprimento),
      largura: numeroOuNull(form.largura),
      altura: numeroOuNull(form.altura),
    }
  }

  async function salvar() {
    erroGeral.value = ''
    if (!validar()) {
      return
    }
    salvando.value = true
    try {
      const id = route.params.id
      const payload = paraPayload()
      if (typeof id === 'string') {
        await atualizarProduto(id, payload)
      } else {
        await criarProduto(payload)
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

  return { modoEdicao, form, erros, erroGeral, salvando, salvar, cancelar }
}
