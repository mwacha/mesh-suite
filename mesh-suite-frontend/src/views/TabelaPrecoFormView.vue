<template>
  <AppShell :title="modoEdicao ? 'Editar Tabela de Preço' : 'Nova Tabela de Preço'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Regras da Tabela</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Nome da tabela de preços *</label>
            <input v-model="form.nome" data-test="nome" />
            <p v-if="erros.nome" class="field-error">{{ erros.nome }}</p>
          </div>
          <div>
            <label class="field-label">Como quer escolher os produtos desta tabela? *</label>
            <select v-model="form.modoSelecaoProdutos" data-test="modo-selecao" @change="aoMudarModoSelecao">
              <option value="TODOS_PRODUTOS">Todos os Produtos</option>
              <option value="SELECIONAR_PRODUTOS">Selecionar os Produtos</option>
            </select>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Método de ajuste *</label>
            <div class="toggle-pair">
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.metodoAjuste === 'AUTOMATICO' }" data-test="metodo-automatico" @click="form.metodoAjuste = 'AUTOMATICO'">Automático</button>
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.metodoAjuste === 'MANUAL' }" data-test="metodo-manual" @click="form.metodoAjuste = 'MANUAL'">Manual</button>
            </div>
          </div>
          <div v-if="form.metodoAjuste === 'AUTOMATICO'">
            <label class="field-label">Operação</label>
            <div class="toggle-pair">
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.operacaoAjuste === 'SOMAR' }" data-test="operacao-somar" @click="form.operacaoAjuste = 'SOMAR'">Somar</button>
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.operacaoAjuste === 'SUBTRAIR' }" data-test="operacao-subtrair" @click="form.operacaoAjuste = 'SUBTRAIR'">Subtrair</button>
            </div>
            <div class="toggle-pair" style="margin-top: 6px">
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.tipoValorAjuste === 'REAL' }" data-test="tipo-real" @click="form.tipoValorAjuste = 'REAL'">R$</button>
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.tipoValorAjuste === 'PERCENTUAL' }" data-test="tipo-percentual" @click="form.tipoValorAjuste = 'PERCENTUAL'">%</button>
              <input v-model.number="form.valorAjuste" type="number" step="0.01" min="0" data-test="valor-ajuste" />
            </div>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Arredondamento *</label>
            <select v-model="form.arredondamento" data-test="arredondamento">
              <option value="NAO_ARREDONDAR">Não arredondar</option>
              <option value="TERMINAR_EM_0">Terminar em 0</option>
              <option value="TERMINAR_EM_9">Terminar em 9</option>
              <option value="TERMINAR_EM_90">Terminar em ,90</option>
              <option value="TERMINAR_EM_99">Terminar em ,99</option>
            </select>
          </div>
          <div class="grid grid-2">
            <div>
              <label class="field-label">Início de vigência *</label>
              <input v-model="form.inicioVigencia" type="date" data-test="inicio-vigencia" />
              <p v-if="erros.inicioVigencia" class="field-error">{{ erros.inicioVigencia }}</p>
            </div>
            <div>
              <label class="field-label">Término de vigência</label>
              <input v-model="form.terminoVigencia" type="date" data-test="termino-vigencia" />
            </div>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Valor mínimo para venda (R$)</label>
            <input v-model.number="form.valorMinimoVenda" type="number" step="0.01" min="0" />
          </div>
          <div>
            <label class="field-label">% de Comissão (padrão dos itens)</label>
            <input v-model.number="form.percentualComissaoPadrao" type="number" step="0.01" min="0" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Itens na Tabela</h2>

        <div v-if="form.modoSelecaoProdutos === 'SELECIONAR_PRODUTOS'" class="busca-wrapper">
          <input
            v-model="produtoBusca"
            placeholder="Buscar produto por nome ou SKU..."
            data-test="produto-busca"
            autocomplete="off"
            @input="buscarProdutos"
          />
          <ul v-if="resultadosProdutos.length" class="dropdown-busca" data-test="produto-resultados">
            <li v-for="p in resultadosProdutos" :key="p.id" @click="adicionarProduto(p)">{{ p.name }} ({{ p.sku }})</li>
          </ul>
        </div>

        <div v-if="itens.length" class="filtro-itens">
          <label class="field-label">Mostrar</label>
          <select v-model="filtroPreenchimento" data-test="filtro-preenchimento">
            <option value="TODOS">Todos</option>
            <option value="PREENCHIDO">Preenchido</option>
            <option value="PENDENTE">Pendente</option>
          </select>
        </div>

        <table v-if="itensExibidos.length" class="tabela-itens">
          <thead>
            <tr>
              <th>Nome do item</th>
              <th>Código</th>
              <th>Preço cadastrado</th>
              <th>Preço nesta tabela</th>
              <th>Margem</th>
              <th>% Comissão</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="{ item, indexReal } in itensExibidos" :key="item.produtoId">
              <td>{{ item.produtoNome }}</td>
              <td>{{ item.produtoSku }}</td>
              <td>{{ formatarPreco(item.precoCadastrado) }}</td>
              <td>
                <input
                  v-model.number="item.precoNestaTabela"
                  type="number"
                  step="0.01"
                  min="0"
                  :data-test="`item-preco-${indexReal}`"
                />
                <button type="button" :data-test="`item-reset-${indexReal}`" @click="resetarItem(indexReal)" title="Recalcular pela regra">↺</button>
              </td>
              <td>{{ margem(item) }}</td>
              <td>
                <input v-model.number="item.percentualComissao" type="number" step="0.01" min="0" :data-test="`item-comissao-${indexReal}`" />
              </td>
              <td><button type="button" class="btn-remover" :data-test="`item-remover-${indexReal}`" @click="removerItem(indexReal)">✕</button></td>
            </tr>
          </tbody>
        </table>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Tabela</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  buscarTabelaPreco,
  criarTabelaPreco,
  atualizarTabelaPreco,
  type TabelaPrecoRequest,
  type TabelaPrecoItemInput,
} from '@/api/tabelasPreco'
import { listProducts, type ProductListItem } from '@/api/products'
import { calcularPrecoAjustado, type RegraAjuste } from '@/utils/calculoTabelaPreco'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

interface ItemForm extends TabelaPrecoItemInput {
  produtoNome: string
  produtoSku: string
  precoCadastrado: number
}

function novoFormulario(): TabelaPrecoRequest {
  return {
    nome: '',
    modoSelecaoProdutos: 'TODOS_PRODUTOS',
    metodoAjuste: 'AUTOMATICO',
    operacaoAjuste: 'SOMAR',
    tipoValorAjuste: 'REAL',
    valorAjuste: 0,
    arredondamento: 'NAO_ARREDONDAR',
    inicioVigencia: new Date().toISOString().slice(0, 10),
    terminoVigencia: null,
    valorMinimoVenda: null,
    percentualComissaoPadrao: null,
    ativo: true,
    itens: [],
  }
}

const form = reactive<TabelaPrecoRequest>(novoFormulario())
const itens = ref<ItemForm[]>([])
const erros = reactive<{ nome?: string; inicioVigencia?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

const produtoBusca = ref('')
const resultadosProdutos = ref<ProductListItem[]>([])

const filtroPreenchimento = ref<'TODOS' | 'PREENCHIDO' | 'PENDENTE'>('TODOS')

const itensExibidos = computed(() =>
  itens.value
    .map((item, indexReal) => ({ item, indexReal }))
    .filter(({ item }) => {
      if (filtroPreenchimento.value === 'PREENCHIDO') return item.precoNestaTabela !== null
      if (filtroPreenchimento.value === 'PENDENTE') return item.precoNestaTabela === null
      return true
    }),
)

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function margem(item: ItemForm) {
  if (!item.precoCadastrado || item.precoNestaTabela === null) {
    return '0%'
  }
  const pct = ((item.precoNestaTabela - item.precoCadastrado) / item.precoCadastrado) * 100
  return `${pct.toFixed(0)}%`
}

function regraAtual(): RegraAjuste {
  return {
    operacaoAjuste: form.operacaoAjuste ?? 'SOMAR',
    tipoValorAjuste: form.tipoValorAjuste ?? 'REAL',
    valorAjuste: form.valorAjuste ?? 0,
    arredondamento: form.arredondamento,
  }
}

function precoParaNovoItem(precoBase: number): number | null {
  return form.metodoAjuste === 'AUTOMATICO' ? calcularPrecoAjustado(precoBase, regraAtual()) : null
}

// Spec §5: "recalcula sempre que a regra muda" -- in TODOS_PRODUTOS mode, every
// item's price is rule-driven and gets overwritten live whenever the rule
// changes. Scoped to TODOS_PRODUTOS only: SELECIONAR_PRODUTOS items are only
// touched by a direct edit or their own reset button.
watch(
  () => [form.metodoAjuste, form.operacaoAjuste, form.tipoValorAjuste, form.valorAjuste, form.arredondamento],
  () => {
    if (form.modoSelecaoProdutos !== 'TODOS_PRODUTOS') {
      return
    }
    itens.value = itens.value.map((item) => ({
      ...item,
      precoNestaTabela: precoParaNovoItem(item.precoCadastrado),
    }))
  },
)

async function popularTodosOsProdutos() {
  try {
    const pagina = await listProducts({ status: 'ACTIVE', size: 1000 })
    itens.value = pagina.content.map((p) => ({
      produtoId: p.id,
      produtoNome: p.name,
      produtoSku: p.sku,
      precoCadastrado: p.salePrice,
      precoNestaTabela: precoParaNovoItem(p.salePrice),
      percentualComissao: form.percentualComissaoPadrao,
    }))
  } catch {
    erroGeral.value = 'Não foi possível carregar a lista de produtos.'
  }
}

function aoMudarModoSelecao() {
  if (form.modoSelecaoProdutos === 'TODOS_PRODUTOS') {
    popularTodosOsProdutos()
  } else {
    itens.value = []
  }
}

async function buscarProdutos() {
  if (!produtoBusca.value.trim()) {
    resultadosProdutos.value = []
    return
  }
  try {
    const pagina = await listProducts({ busca: produtoBusca.value, status: 'ACTIVE', size: 5 })
    resultadosProdutos.value = pagina.content.filter((p) => !itens.value.some((i) => i.produtoId === p.id))
  } catch {
    resultadosProdutos.value = []
  }
}

function adicionarProduto(produto: ProductListItem) {
  itens.value.push({
    produtoId: produto.id,
    produtoNome: produto.name,
    produtoSku: produto.sku,
    precoCadastrado: produto.salePrice,
    precoNestaTabela: precoParaNovoItem(produto.salePrice),
    percentualComissao: form.percentualComissaoPadrao,
  })
  produtoBusca.value = ''
  resultadosProdutos.value = []
}

function removerItem(index: number) {
  itens.value.splice(index, 1)
}

function resetarItem(index: number) {
  const item = itens.value[index]
  item.precoNestaTabela = precoParaNovoItem(item.precoCadastrado)
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const tabela = await buscarTabelaPreco(id)
      form.nome = tabela.nome
      form.modoSelecaoProdutos = tabela.modoSelecaoProdutos
      form.metodoAjuste = tabela.metodoAjuste
      form.operacaoAjuste = tabela.operacaoAjuste
      form.tipoValorAjuste = tabela.tipoValorAjuste
      form.valorAjuste = tabela.valorAjuste
      form.arredondamento = tabela.arredondamento
      form.inicioVigencia = tabela.inicioVigencia
      form.terminoVigencia = tabela.terminoVigencia
      form.valorMinimoVenda = tabela.valorMinimoVenda
      form.percentualComissaoPadrao = tabela.percentualComissaoPadrao
      form.ativo = tabela.ativo
      itens.value = tabela.itens.map((i) => ({
        produtoId: i.produtoId,
        produtoNome: i.produtoNome,
        produtoSku: i.produtoSku,
        precoCadastrado: i.precoCadastrado,
        precoNestaTabela: i.precoNestaTabela,
        percentualComissao: i.percentualComissao,
      }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da tabela de preço.'
    }
  } else if (form.modoSelecaoProdutos === 'TODOS_PRODUTOS') {
    await popularTodosOsProdutos()
  }
})

function validar(): boolean {
  erros.nome = form.nome.trim() ? undefined : 'Campo obrigatório'
  erros.inicioVigencia = form.inicioVigencia ? undefined : 'Campo obrigatório'
  return !erros.nome && !erros.inicioVigencia
}

function paraPayload(): TabelaPrecoRequest {
  return {
    ...form,
    valorAjuste: form.metodoAjuste === 'AUTOMATICO' ? Number(form.valorAjuste) || 0 : null,
    itens: itens.value.map(({ produtoId, precoNestaTabela, percentualComissao }) => ({
      produtoId,
      precoNestaTabela,
      percentualComissao,
    })),
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
      await atualizarTabelaPreco(id, payload)
    } else {
      await criarTabelaPreco(payload)
    }
    router.push({ name: 'tabelas-preco' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma tabela de preço cadastrada com este nome.'
    } else if (err?.response?.status === 403) {
      erroGeral.value = 'Você não tem permissão para executar esta ação.'
    } else if (err?.response?.status === 400) {
      erroGeral.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      erroGeral.value = 'Não foi possível salvar. Tente novamente em instantes.'
    }
  } finally {
    salvando.value = false
  }
}

function cancelar() {
  router.push({ name: 'tabelas-preco' })
}
</script>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-family: var(--pm-font);
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px;
}

.card h2 {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0 0 12px;
}

.grid {
  display: grid;
  gap: 0 14px;
  margin-bottom: 10px;
}

.grid-2 {
  grid-template-columns: 1fr 1fr;
}

.field-label {
  display: block;
  font-size: 12px;
  color: var(--pm-text-mid);
  margin-bottom: 4px;
}

input,
select {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  color: var(--pm-text-dark);
  font-size: 13px;
  font-family: var(--pm-font);
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 4px 0 0;
}

.error-geral {
  color: var(--pm-error);
  font-size: 14px;
}

.toggle-pair {
  display: flex;
  gap: 6px;
}

.toggle-btn {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.toggle-btn--ativo {
  background: var(--pm-accent);
  color: var(--pm-white);
  border-color: var(--pm-accent);
}

.busca-wrapper {
  position: relative;
  margin-bottom: 10px;
}

.dropdown-busca {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin: 4px 0 0;
  padding: 4px 0;
  list-style: none;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 28px rgba(0, 0, 0, 0.12);
  z-index: 10;
  max-height: 200px;
  overflow-y: auto;
}

.dropdown-busca li {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--pm-text-dark);
  cursor: pointer;
}

.filtro-itens {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.filtro-itens select {
  width: auto;
}

.tabela-itens {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.tabela-itens th {
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  background: var(--pm-bg);
  padding: 6px 10px;
}

.tabela-itens td {
  padding: 6px 10px;
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.btn-remover {
  border: none;
  background: none;
  color: var(--pm-error);
  cursor: pointer;
  font-size: 13px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn-primary,
.btn-secondary {
  border-radius: 8px;
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--pm-font);
  cursor: pointer;
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
}
</style>
