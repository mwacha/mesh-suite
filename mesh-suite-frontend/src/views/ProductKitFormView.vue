<template>
  <AppShell :title="modoEdicao ? 'Editar Kit' : 'Novo Kit'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Tipo de Produto</h2>
        <ProductTypeSelector model-value="PRODUCT_KIT" :disabled="modoEdicao" @update:model-value="aoMudarTipo" />
      </section>

      <CollapsibleSection title="Informações Gerais">
        <TextField v-model="form.name" label="Nome do Kit" required :error="erros.name" test-id="nome" />
        <div class="grid grid-2">
          <TextField v-model="form.sku" label="Código SKU" required :error="erros.sku" test-id="sku" />
          <TextField v-model="barcodeModel" label="Código de Barra (EAN/GTIN)" placeholder="7891234567890" />
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Unidade de Medida</label>
            <select v-model="form.measurementUnit">
              <option v-for="unidade in UNIDADES" :key="unidade" :value="unidade">{{ unidade }}</option>
            </select>
          </div>
          <div>
            <label class="field-label">Múltiplo de Venda</label>
            <input v-model.number="form.saleMultiple" type="number" step="0.001" min="0.001" data-test="multiplo-venda" />
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Valor de Venda do Kit</label>
            <div class="valor-calculado" data-test="valor-kit">
              {{ formatarPreco(totalKit) }}
              <span class="valor-calculado-badge">calculado automaticamente</span>
            </div>
          </div>
        </div>
        <div class="field-full">
          <label class="field-label">Status</label>
          <div class="status-pills">
            <button
              v-for="opt in STATUS_OPCOES"
              :key="opt.value"
              type="button"
              class="status-pill"
              :class="{
                'status-pill--ativo': form.status === opt.value && opt.value === 'ACTIVE',
                'status-pill--inativo': form.status === opt.value && opt.value === 'INACTIVE',
              }"
              @click="form.status = opt.value"
            >
              <span class="status-dot"></span>
              {{ opt.label }}
            </button>
          </div>
        </div>
        <div class="field-full">
          <label class="field-label">Descrição</label>
          <textarea v-model="form.description" rows="3" placeholder="Descreva o kit..."></textarea>
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Composição do Kit">
        <div class="itens-toolbar">
          <button type="button" class="btn-add-itens" data-test="adicionar-itens" @click="modalAberto = true">
            + Adicionar Produto
          </button>
        </div>

        <div v-if="itens.length" class="itens-grid">
          <div class="itens-grid-header">
            <div>Produto</div>
            <div class="itens-grid-col-centro">Qtd.</div>
            <div class="itens-grid-col-preco">Vlr. de Venda</div>
            <div class="itens-grid-col-preco">Total Item</div>
            <div></div>
          </div>
          <div v-for="(item, index) in itens" :key="item.componentProductId" class="itens-grid-row">
            <div class="itens-grid-cell-nome">{{ item.componentName }} <span class="item-sku">({{ item.componentSku }})</span></div>
            <div class="itens-grid-col-centro">
              <input v-model.number="item.quantity" type="number" step="1" min="1" :data-test="`item-qtd-${index}`" />
            </div>
            <div class="itens-grid-col-preco">{{ formatarPreco(item.unitPrice) }}</div>
            <div class="itens-grid-col-preco">{{ formatarPreco(item.quantity * item.unitPrice) }}</div>
            <div class="itens-grid-col-acao">
              <button type="button" class="btn-remover-item" :data-test="`item-remover-${index}`" @click="removerItem(index)">
                × Remover
              </button>
            </div>
          </div>
        </div>
        <div v-else class="itens-vazio">
          <div class="itens-vazio-titulo">Nenhum produto adicionado</div>
          <div class="itens-vazio-texto">Use "+ Adicionar Produto" para compor o kit.</div>
        </div>
        <p v-if="erros.itens" class="field-error">{{ erros.itens }}</p>
      </CollapsibleSection>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <FormActions :saving="salvando" save-label="Salvar Kit" @cancel="cancelar" />
    </form>

    <AdicionarItensModal
      v-if="modalAberto"
      title="Adicionar produtos ao kit"
      :itens-adicionados-ids="itens.map((i) => i.componentProductId)"
      @add="aoAdicionarProduto"
      @remove="aoRemoverProdutoPorId"
      @close="modalAberto = false"
    />
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import CollapsibleSection from '@/components/CollapsibleSection.vue'
import TextField from '@/components/TextField.vue'
import FormActions from '@/components/FormActions.vue'
import ProductTypeSelector from '@/components/ProductTypeSelector.vue'
import AdicionarItensModal from '@/components/AdicionarItensModal.vue'
import { getKit, createKit, updateKit, type KitProductRequest } from '@/api/productKits'
import type { ProductListItem, MeasurementUnit, ProductStatus } from '@/api/products'

const UNIDADES: MeasurementUnit[] = ['UN', 'KG', 'G', 'L', 'ML', 'MT', 'CM', 'CX', 'PC', 'PAR', 'DZ']
const STATUS_OPCOES: { value: ProductStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Ativo' },
  { value: 'INACTIVE', label: 'Inativo' },
]

interface ItemForm {
  componentProductId: string
  componentName: string
  componentSku: string
  quantity: number
  unitPrice: number
}

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario() {
  return {
    name: '',
    sku: '',
    barcode: '' as string | null,
    measurementUnit: 'UN' as MeasurementUnit,
    saleMultiple: 1 as number | null,
    status: 'ACTIVE' as ProductStatus,
    description: '',
  }
}

const form = reactive(novoFormulario())
const itens = ref<ItemForm[]>([])
const erros = reactive<{ name?: string; sku?: string; itens?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)
const modalAberto = ref(false)

const barcodeModel = computed({
  get: () => form.barcode ?? '',
  set: (valor: string) => {
    form.barcode = valor
  },
})

const totalKit = computed(() => itens.value.reduce((acc, item) => acc + item.quantity * item.unitPrice, 0))

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function aoMudarTipo(tipo: string) {
  if (tipo === 'PRODUCT') {
    router.push({ name: 'produtos-novo' })
  } else if (tipo === 'VARIATION_PARENT') {
    router.push({ name: 'produtos-novo-variacao' })
  }
}

function aoAdicionarProduto(produto: ProductListItem) {
  itens.value.push({
    componentProductId: produto.id,
    componentName: produto.name,
    componentSku: produto.sku,
    quantity: 1,
    unitPrice: produto.salePrice,
  })
}

function aoRemoverProdutoPorId(produtoId: string) {
  const index = itens.value.findIndex((i) => i.componentProductId === produtoId)
  if (index !== -1) {
    itens.value.splice(index, 1)
  }
}

function removerItem(index: number) {
  itens.value.splice(index, 1)
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const kit = await getKit(id)
      form.name = kit.name
      form.sku = kit.sku
      form.barcode = kit.barcode
      form.measurementUnit = kit.measurementUnit
      form.saleMultiple = kit.saleMultiple
      form.status = kit.status
      form.description = kit.description
      itens.value = kit.items.map((i) => ({
        componentProductId: i.componentProductId,
        componentName: i.componentName,
        componentSku: i.componentSku,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
      }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do kit.'
    }
  }
})

function validar(): boolean {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
  erros.sku = form.sku.trim() ? undefined : 'Campo obrigatório'
  erros.itens = itens.value.length > 0 ? undefined : 'Adicione ao menos um produto ao kit'
  return !erros.name && !erros.sku && !erros.itens
}

function paraPayload(): KitProductRequest {
  return {
    name: form.name,
    sku: form.sku,
    barcode: form.barcode?.trim() || null,
    measurementUnit: form.measurementUnit,
    status: form.status,
    description: form.description,
    items: itens.value.map((i) => ({ componentProductId: i.componentProductId, quantity: i.quantity })),
    saleMultiple: Number(form.saleMultiple) || 1,
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
      await updateKit(id, payload)
    } else {
      await createKit(payload)
    }
    router.push({ name: 'produtos' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um produto cadastrado com este SKU.'
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
  router.push({ name: 'produtos' })
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

.field-full {
  margin-bottom: 10px;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

input,
select,
textarea {
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

.valor-calculado {
  height: 36px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--pm-bg);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 0 10px;
  color: var(--pm-text-dark);
  font-size: 14px;
  font-weight: 700;
}

.valor-calculado-badge {
  font-size: 10px;
  font-weight: 600;
  color: var(--pm-text-muted);
  text-transform: uppercase;
}

/* Status */
.status-pills {
  display: flex;
  gap: 8px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 18px;
  border: 2px solid var(--pm-border-light);
  border-radius: 20px;
  background: var(--pm-white);
  color: var(--pm-text-muted);
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--pm-border-light);
}

.status-pill--ativo {
  border-color: var(--pm-success);
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.status-pill--ativo .status-dot {
  background: var(--pm-success);
}

.status-pill--inativo {
  border-color: var(--pm-text-mid);
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.status-pill--inativo .status-dot {
  background: var(--pm-text-mid);
}

/* Itens */
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

.itens-grid {
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  overflow: hidden;
  font-size: 12px;
}

.itens-grid-header,
.itens-grid-row {
  display: grid;
  grid-template-columns: 1.6fr 90px 130px 130px 100px;
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

.item-sku {
  color: var(--pm-text-muted);
  font-size: 11px;
}

.itens-grid-col-preco {
  text-align: right;
}

.itens-grid-col-centro {
  text-align: center;
}

.itens-grid-col-centro input {
  text-align: center;
}

.itens-grid-col-acao {
  display: flex;
  justify-content: center;
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
</style>
