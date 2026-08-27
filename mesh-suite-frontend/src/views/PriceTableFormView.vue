<template>
  <AppShell :title="modoEdicao ? 'Editar Tabela de Preço' : 'Nova Tabela de Preço'">
    <form class="form" @submit.prevent="salvar">
      <CollapsibleSection title="Regras da Tabela">
        <div class="grid grid-2">
          <TextField
            v-model="form.name"
            label="Nome da tabela de preços"
            required
            :error="erros.name"
            test-id="nome"
            @blur="validarNome"
          />
          <SelectField
            v-model="form.productSelectionMode"
            label="Como quer escolher os produtos desta tabela?"
            required
            test-id="modo-selecao"
            @update:model-value="aoMudarModoSelecao"
          >
            <option value="ALL_PRODUCTS">Todos os Produtos</option>
            <option value="SELECT_PRODUCTS">Selecionar os Produtos</option>
          </SelectField>
        </div>

        <div class="grid grid-2">
          <div>
            <label class="field-label">Método de ajuste *</label>
            <SegmentedControl
              :model-value="form.adjustmentMethod"
              :options="metodoOptions"
              test-id="metodo"
              @update:model-value="(v) => (form.adjustmentMethod = v as PriceTableRequest['adjustmentMethod'])"
            />
          </div>
          <div v-if="form.adjustmentMethod === 'AUTOMATIC'">
            <label class="field-label">Operação</label>
            <div class="operacao-row">
              <SegmentedControl
                :model-value="form.adjustmentOperation ?? 'ADD'"
                :options="operacaoOptions"
                test-id="operacao"
                @update:model-value="(v) => (form.adjustmentOperation = v as PriceTableRequest['adjustmentOperation'])"
              />
              <SegmentedControl
                :model-value="form.adjustmentValueType ?? 'FIXED'"
                :options="tipoValorOptions"
                test-id="tipo"
                @update:model-value="(v) => (form.adjustmentValueType = v as PriceTableRequest['adjustmentValueType'])"
              />
              <input v-model.number="form.adjustmentValue" type="number" step="0.01" min="0" data-test="valor-ajuste" />
            </div>
          </div>
        </div>

        <div class="grid grid-3">
          <SelectField v-model="form.rounding" label="Arredondamento" required test-id="arredondamento">
            <option value="NO_ROUNDING">Não arredondar</option>
            <option value="END_IN_0">Terminar em 0</option>
            <option value="END_IN_9">Terminar em 9</option>
            <option value="END_IN_90">Terminar em ,90</option>
            <option value="END_IN_99">Terminar em ,99</option>
          </SelectField>
          <div>
            <label class="field-label">Início de vigência *</label>
            <input v-model="form.effectiveStartDate" type="date" data-test="inicio-vigencia" />
            <p v-if="erros.effectiveStartDate" class="field-error">{{ erros.effectiveStartDate }}</p>
          </div>
          <div>
            <label class="field-label">Término de vigência</label>
            <input v-model="form.effectiveEndDate" type="date" data-test="termino-vigencia" />
          </div>
        </div>

        <div class="grid grid-2">
          <div>
            <label class="field-label">Valor mínimo para venda (R$)</label>
            <input v-model.number="form.minSalePrice" type="number" step="0.01" min="0" />
          </div>
          <div>
            <label class="field-label">% de Comissão (padrão dos itens)</label>
            <input v-model.number="form.defaultCommissionPercentage" type="number" step="0.01" min="0" />
          </div>
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Itens na Tabela">
        <template v-if="form.productSelectionMode === 'ALL_PRODUCTS'">
          <div class="info-banner">
            <span class="info-banner-icon">ℹ️</span>
            <div>
              Todos os produtos serão incluídos automaticamente com a <strong>regra padrão</strong> definida acima.
              Adicione abaixo apenas os produtos que terão <strong>configuração diferente</strong> (exceções).
            </div>
          </div>

          <div class="excecoes-toolbar">
            <span class="excecoes-titulo">Exceções · produtos com configuração diferente</span>
            <button type="button" class="btn-add-itens" data-test="adicionar-itens" @click="modalAberto = true">
              + Adicionar exceção
            </button>
          </div>

          <div v-if="itens.length === 0" class="excecoes-vazio" data-test="excecoes-vazio">
            <div class="itens-vazio-titulo">Nenhuma exceção cadastrada</div>
            <div class="itens-vazio-texto">
              Todos os produtos seguem a regra padrão. Use "+ Adicionar exceção" para configurar preço ou comissão
              específicos.
            </div>
          </div>
        </template>

        <template v-else>
          <div class="itens-toolbar">
            <button type="button" class="btn-add-itens" data-test="adicionar-itens" @click="modalAberto = true">
              + Adicionar mais itens à tabela
            </button>
          </div>

          <div v-if="itens.length" class="filtro-itens">
            <input
              v-model="buscaItens"
              class="filtro-itens-busca"
              placeholder="Busque por nome ou SKU"
              data-test="busca-itens"
              autocomplete="off"
            />
            <SegmentedControl v-model="filtroPreenchimento" :options="filtroOptions" test-id="filtro-preenchimento" />
          </div>
        </template>

        <div v-if="itensExibidos.length" class="itens-grid">
          <div class="itens-grid-header">
            <div>Nome do item</div>
            <div>Código</div>
            <div class="itens-grid-col-preco">Preço cadastrado</div>
            <div>Preço nesta tabela</div>
            <div class="itens-grid-col-centro">Margem</div>
            <div>% Comissão</div>
            <div></div>
          </div>
          <div v-for="{ item, indexReal } in itensExibidos" :key="item.productId" class="itens-grid-row">
            <div class="itens-grid-cell-nome">{{ item.productName }}</div>
            <div>{{ item.productSku }}</div>
            <div class="itens-grid-col-preco">{{ formatarPreco(item.registeredPrice) }}</div>
            <div class="item-preco-cell">
              <span class="item-preco-prefixo">R$</span>
              <input
                v-model.number="item.tablePrice"
                type="number"
                step="0.01"
                min="0"
                :data-test="`item-preco-${indexReal}`"
              />
              <button
                type="button"
                class="item-reset-btn"
                :data-test="`item-reset-${indexReal}`"
                title="Recalcular pela regra"
                @click="resetarItem(indexReal)"
              >
                ↺
              </button>
            </div>
            <div class="itens-grid-col-centro">{{ margem(item) }}</div>
            <div>
              <input v-model.number="item.commissionPercentage" type="number" step="0.01" min="0" :data-test="`item-comissao-${indexReal}`" />
            </div>
            <div class="itens-grid-col-acao">
              <button type="button" class="btn-remover-item" :data-test="`item-remover-${indexReal}`" @click="removerItem(indexReal)">
                × Remover
              </button>
            </div>
          </div>
        </div>
        <div v-else-if="form.productSelectionMode === 'SELECT_PRODUCTS'" class="itens-vazio">
          <div class="itens-vazio-titulo">Nenhum item adicionado</div>
          <div class="itens-vazio-texto">Use "+ Adicionar mais itens à tabela" para incluir produtos.</div>
        </div>

        <div v-if="form.productSelectionMode === 'SELECT_PRODUCTS' && itensFiltrados.length" class="itens-paginacao">
          <span class="itens-paginacao-info" data-test="itens-paginacao-info">
            Exibindo {{ faixaExibida.inicio }}–{{ faixaExibida.fim }} de {{ itensFiltrados.length }} itens
          </span>
          <div v-if="totalPaginasItens > 1" class="itens-paginacao-botoes">
            <button
              type="button"
              class="itens-paginacao-btn"
              data-test="itens-pagina-anterior"
              :disabled="paginaItens === 0"
              @click="paginaItens = Math.max(0, paginaItens - 1)"
            >
              ‹
            </button>
            <button
              v-for="p in totalPaginasItens"
              :key="p"
              type="button"
              class="itens-paginacao-btn"
              :class="{ 'itens-paginacao-btn-ativa': paginaItens === p - 1 }"
              :data-test="`itens-pagina-${p}`"
              @click="paginaItens = p - 1"
            >
              {{ p }}
            </button>
            <button
              type="button"
              class="itens-paginacao-btn"
              data-test="itens-pagina-proxima"
              :disabled="paginaItens >= totalPaginasItens - 1"
              @click="paginaItens = Math.min(totalPaginasItens - 1, paginaItens + 1)"
            >
              ›
            </button>
          </div>
        </div>
      </CollapsibleSection>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <FormActions :saving="salvando" save-label="Salvar Tabela" @cancel="cancelar" />
    </form>

    <AdicionarItensModal
      v-if="modalAberto"
      :itens-adicionados-ids="itens.map((i) => i.productId)"
      @add="aoAdicionarProduto"
      @remove="aoRemoverProdutoPorId"
      @close="modalAberto = false"
    />
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import CollapsibleSection from '@/components/CollapsibleSection.vue'
import TextField from '@/components/TextField.vue'
import SelectField from '@/components/SelectField.vue'
import SegmentedControl, { type SegmentedOption } from '@/components/SegmentedControl.vue'
import FormActions from '@/components/FormActions.vue'
import AdicionarItensModal from '@/components/AdicionarItensModal.vue'
import {
  getPriceTable,
  createPriceTable,
  updatePriceTable,
  type PriceTableRequest,
  type PriceTableItemInput,
} from '@/api/priceTables'
import { type SellableProductItem } from '@/api/products'
import { normalizarTexto } from '@/utils/texto'
import { calculateAdjustedPrice, type AdjustmentRule } from '@/utils/priceCalculation'
import { useToast } from '@/composables/useToast'

const { showToast } = useToast()
const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

const metodoOptions = computed<SegmentedOption[]>(() => [
  { value: 'AUTOMATIC', label: 'Automático' },
  { value: 'MANUAL', label: 'Manual', disabled: form.productSelectionMode === 'ALL_PRODUCTS' },
])
const operacaoOptions: SegmentedOption[] = [
  { value: 'ADD', label: 'Somar' },
  { value: 'SUBTRACT', label: 'Subtrair' },
]
const tipoValorOptions: SegmentedOption[] = [
  { value: 'FIXED', label: 'R$' },
  { value: 'PERCENTAGE', label: '%' },
]
const filtroOptions: SegmentedOption[] = [
  { value: 'PREENCHIDO', label: 'Preenchido' },
  { value: 'PENDENTE', label: 'Pendente' },
  { value: 'TODOS', label: 'Todos' },
]

const ITENS_POR_PAGINA = 20

interface ItemForm extends PriceTableItemInput {
  productName: string
  productSku: string
  registeredPrice: number
}

function novoFormulario(): PriceTableRequest {
  return {
    name: '',
    productSelectionMode: 'ALL_PRODUCTS',
    adjustmentMethod: 'AUTOMATIC',
    adjustmentOperation: 'ADD',
    adjustmentValueType: 'FIXED',
    adjustmentValue: 0,
    rounding: 'NO_ROUNDING',
    effectiveStartDate: new Date().toISOString().slice(0, 10),
    effectiveEndDate: null,
    minSalePrice: null,
    defaultCommissionPercentage: null,
    active: true,
    items: [],
  }
}

const form = reactive<PriceTableRequest>(novoFormulario())
const itens = ref<ItemForm[]>([])
const erros = reactive<{ name?: string; effectiveStartDate?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)
const modalAberto = ref(false)

const filtroPreenchimento = ref<'TODOS' | 'PREENCHIDO' | 'PENDENTE'>('TODOS')
const buscaItens = ref('')
const paginaItens = ref(0)

const itensFiltrados = computed(() => {
  const consulta = normalizarTexto(buscaItens.value)
  return itens.value
    .map((item, indexReal) => ({ item, indexReal }))
    .filter(({ item }) => {
      if (filtroPreenchimento.value === 'PREENCHIDO' && item.tablePrice === null) return false
      if (filtroPreenchimento.value === 'PENDENTE' && item.tablePrice !== null) return false
      if (!consulta) return true
      return normalizarTexto(`${item.productName} ${item.productSku}`).includes(consulta)
    })
})

// Exceptions are never paginated -- the wireframe pages only the "Selecionar os
// Produtos" grid, where the list can run to the whole catalogue.
const itensExibidos = computed(() => {
  if (form.productSelectionMode === 'ALL_PRODUCTS') {
    return itensFiltrados.value
  }
  const inicio = paginaItens.value * ITENS_POR_PAGINA
  return itensFiltrados.value.slice(inicio, inicio + ITENS_POR_PAGINA)
})

const totalPaginasItens = computed(() => Math.max(1, Math.ceil(itensFiltrados.value.length / ITENS_POR_PAGINA)))

const faixaExibida = computed(() => ({
  inicio: itensFiltrados.value.length === 0 ? 0 : paginaItens.value * ITENS_POR_PAGINA + 1,
  fim: Math.min((paginaItens.value + 1) * ITENS_POR_PAGINA, itensFiltrados.value.length),
}))

// Narrowing the list can strand the viewer on a page that no longer exists.
watch([buscaItens, filtroPreenchimento], () => {
  paginaItens.value = 0
})

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function margem(item: ItemForm) {
  if (!item.registeredPrice || item.tablePrice === null) {
    return '0%'
  }
  const pct = ((item.tablePrice - item.registeredPrice) / item.registeredPrice) * 100
  return `${pct.toFixed(0)}%`
}

function regraAtual(): AdjustmentRule {
  return {
    adjustmentOperation: form.adjustmentOperation ?? 'ADD',
    adjustmentValueType: form.adjustmentValueType ?? 'FIXED',
    adjustmentValue: form.adjustmentValue ?? 0,
    rounding: form.rounding,
  }
}

function precoParaNovoItem(precoBase: number): number | null {
  return form.adjustmentMethod === 'AUTOMATIC' ? calculateAdjustedPrice(precoBase, regraAtual()) : null
}

// Spec §5: "recalcula sempre que a regra muda" -- in ALL_PRODUCTS mode, every
// item's price is rule-driven and gets overwritten live whenever the rule
// changes. Scoped to ALL_PRODUCTS only: SELECT_PRODUCTS items are only
// touched by a direct edit or their own reset button.
watch(
  () => [form.adjustmentMethod, form.adjustmentOperation, form.adjustmentValueType, form.adjustmentValue, form.rounding],
  () => {
    if (form.productSelectionMode !== 'ALL_PRODUCTS') {
      return
    }
    itens.value = itens.value.map((item) => ({
      ...item,
      tablePrice: precoParaNovoItem(item.registeredPrice),
    }))
  },
)

function aoMudarModoSelecao() {
  // "Todos os Produtos" stores only the exceptions: the catalogue is covered by the
  // default rule at read time, so materialising every product as an item row would
  // both freeze today's catalogue into the table and bury the real exceptions.
  itens.value = []
  paginaItens.value = 0
  if (form.productSelectionMode === 'ALL_PRODUCTS') {
    // Ver wireframe: tabelas "todos os produtos" só fazem sentido com regra
    // automática -- não há como preencher preço manual item a item de todo o catálogo.
    form.adjustmentMethod = 'AUTOMATIC'
  }
}

function aoAdicionarProduto(produto: SellableProductItem) {
  itens.value.push({
    productId: produto.id,
    productName: produto.name,
    productSku: produto.sku,
    registeredPrice: produto.salePrice,
    tablePrice: precoParaNovoItem(produto.salePrice),
    commissionPercentage: form.defaultCommissionPercentage,
  })
}

function aoRemoverProdutoPorId(produtoId: string) {
  const index = itens.value.findIndex((i) => i.productId === produtoId)
  if (index !== -1) {
    itens.value.splice(index, 1)
  }
}

function removerItem(index: number) {
  itens.value.splice(index, 1)
}

function resetarItem(index: number) {
  const item = itens.value[index]
  item.tablePrice = precoParaNovoItem(item.registeredPrice)
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const tabela = await getPriceTable(id)
      form.name = tabela.name
      form.productSelectionMode = tabela.productSelectionMode
      form.adjustmentMethod = tabela.adjustmentMethod
      form.adjustmentOperation = tabela.adjustmentOperation
      form.adjustmentValueType = tabela.adjustmentValueType
      form.adjustmentValue = tabela.adjustmentValue
      form.rounding = tabela.rounding
      form.effectiveStartDate = tabela.effectiveStartDate
      form.effectiveEndDate = tabela.effectiveEndDate
      form.minSalePrice = tabela.minSalePrice
      form.defaultCommissionPercentage = tabela.defaultCommissionPercentage
      form.active = tabela.active
      itens.value = tabela.items.map((i) => ({
        productId: i.productId,
        productName: i.productName,
        productSku: i.productSku,
        registeredPrice: i.registeredPrice,
        tablePrice: i.tablePrice,
        commissionPercentage: i.commissionPercentage,
      }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da tabela de preço.'
    }
  }
})

function validarNome() {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
}

function validar(): boolean {
  validarNome()
  erros.effectiveStartDate = form.effectiveStartDate ? undefined : 'Campo obrigatório'
  return !erros.name && !erros.effectiveStartDate
}

function paraPayload(): PriceTableRequest {
  return {
    ...form,
    adjustmentValue: form.adjustmentMethod === 'AUTOMATIC' ? Number(form.adjustmentValue) || 0 : null,
    items: itens.value.map(({ productId, tablePrice, commissionPercentage }) => ({
      productId,
      tablePrice,
      commissionPercentage,
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
      await updatePriceTable(id, payload)
    } else {
      await createPriceTable(payload)
    }
    showToast('Tabela de preço salva com sucesso!')
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

.grid {
  display: grid;
  gap: 0 14px;
  margin-bottom: 10px;
}

.grid-2 {
  grid-template-columns: 1fr 1fr;
}

.grid-3 {
  grid-template-columns: 1fr 1fr 1fr;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 4px 0 0;
}

input {
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

.operacao-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.operacao-row input {
  width: auto;
  flex: 1;
  min-width: 0;
}

.info-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--pm-accent-bg);
  border: 1px solid var(--pm-accent);
  border-radius: 6px;
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--pm-text-mid);
}

.info-banner-icon {
  font-size: 16px;
}

.itens-toolbar {
  margin-bottom: 12px;
}

.btn-add-itens {
  background: none;
  border: 1.5px dashed var(--pm-accent);
  color: var(--pm-accent);
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.filtro-itens {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.filtro-itens .field-label {
  margin-bottom: 0;
}

.filtro-itens-busca {
  flex: 1;
  min-width: 0;
  height: 32px;
  box-sizing: border-box;
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  background: var(--pm-white);
  padding: 0 10px;
  font-size: 12px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
}

.excecoes-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.excecoes-titulo {
  font-size: 13px;
  font-weight: 700;
  color: var(--pm-text-dark);
  font-family: var(--pm-font);
}

.excecoes-vazio {
  padding: 28px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border: 1px dashed var(--pm-border-light);
  border-radius: 6px;
  text-align: center;
}

.itens-paginacao {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 10px;
  font-family: var(--pm-font);
}

.itens-paginacao-info {
  font-size: 12px;
  color: var(--pm-text-muted);
}

.itens-paginacao-botoes {
  display: flex;
  gap: 4px;
}

.itens-paginacao-btn {
  width: 26px;
  height: 26px;
  border: 1px solid var(--pm-border-light);
  border-radius: 4px;
  background: var(--pm-white);
  color: var(--pm-text-dark);
  font-size: 12px;
  cursor: pointer;
}

.itens-paginacao-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.itens-paginacao-btn-ativa {
  background: var(--pm-accent);
  border-color: var(--pm-accent);
  color: var(--pm-white);
}

.itens-grid {
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  overflow: hidden;
  font-size: 12px;
}

.itens-grid-header,
.itens-grid-row {
  display: grid;
  grid-template-columns: 1.4fr 90px 110px 150px 70px 100px 90px;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
}

.itens-grid-header {
  background: var(--pm-bg);
  font-weight: 700;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  font-size: 11px;
}

.itens-grid-row {
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.itens-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.itens-grid-col-preco {
  text-align: right;
}

.itens-grid-col-centro {
  text-align: center;
}

.itens-grid-col-acao {
  display: flex;
  justify-content: center;
}

.item-preco-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.item-preco-prefixo {
  font-size: 11px;
  color: var(--pm-text-muted);
  background: var(--pm-bg);
  border: 1px solid var(--pm-border-light);
  border-radius: 4px;
  padding: 5px 6px;
  flex-shrink: 0;
}

.item-preco-cell input {
  flex: 1;
  min-width: 0;
}

.item-reset-btn {
  width: 24px;
  height: 28px;
  flex-shrink: 0;
  border: 1px solid var(--pm-border-light);
  border-radius: 4px;
  background: var(--pm-white);
  color: var(--pm-accent);
  cursor: pointer;
  font-size: 12px;
}

.btn-remover-item {
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--pm-error-bg);
  border-radius: 4px;
  background: var(--pm-error-bg);
  color: var(--pm-error);
  font-size: 11px;
  font-weight: 600;
  font-family: var(--pm-font);
  cursor: pointer;
  white-space: nowrap;
}

.itens-vazio {
  padding: 28px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border: 1px dashed var(--pm-border-light);
  border-radius: 6px;
}

.itens-vazio-titulo {
  font-size: 13px;
  font-weight: 600;
  color: var(--pm-text-mid);
}

.itens-vazio-texto {
  font-size: 12px;
  color: var(--pm-text-muted);
  text-align: center;
}

.error-geral {
  color: var(--pm-error);
  font-size: 14px;
}
</style>
