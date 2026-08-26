<template>
  <AppShell :title="modoEdicao ? 'Editar Produto com Variação' : 'Novo Produto com Variação'">
    <form class="form" @submit.prevent="salvar">
      <CollapsibleSection title="Tipo de Produto">
        <SegmentedControl
          :model-value="'VARIATION_PARENT'"
          :options="tipoOptions"
          :disabled="modoEdicao"
          test-id="tipo-produto"
          @update:model-value="aoMudarTipo"
        />
      </CollapsibleSection>

      <CollapsibleSection title="Informações Gerais">
        <TextField v-model="form.name" label="Nome do Produto" required :error="erros.name" test-id="nome" />
        <div class="grid grid-2">
          <TextField v-model="form.sku" label="Código SKU" required :error="erros.sku" test-id="sku" />
          <div>
            <label class="field-label">Código de Barra (EAN/GTIN)</label>
            <input disabled placeholder="Definido por variante" />
          </div>
        </div>
        <div class="grid grid-2">
          <TextField v-model="form.brand" label="Marca" />
          <div>
            <label class="field-label">Categoria</label>
            <select v-model="form.categoryId" data-test="categoria">
              <option :value="null">Sem categoria</option>
              <option v-for="categoria in categorias" :key="categoria.id" :value="categoria.id">
                {{ categoria.name }}
              </option>
            </select>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Preço de Venda *</label>
            <input v-model.number="form.salePrice" type="number" step="0.01" min="0" data-test="preco-venda" />
            <p v-if="erros.salePrice" class="field-error">{{ erros.salePrice }}</p>
          </div>
          <div>
            <label class="field-label">Preço de Custo</label>
            <input disabled placeholder="Definido por variante" />
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Unidade de Medida</label>
            <select v-model="form.measurementUnit">
              <option v-for="unidade in UNIDADES" :key="unidade" :value="unidade">{{ unidade }}</option>
            </select>
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
        </div>
        <div class="field-full">
          <label class="field-label">Descrição</label>
          <textarea v-model="form.description" rows="3" placeholder="Descreva o produto..."></textarea>
        </div>
        <div class="info-banner">
          <span class="info-banner-icon">ℹ️</span>
          <div>Qtd. em estoque, mínimo e máximo são definidos individualmente em cada variante.</div>
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Variantes">
        <div class="itens-toolbar">
          <button type="button" class="btn-add-itens" data-test="adicionar-variante" @click="abrirNovaVariante">
            + Adicionar Variante
          </button>
        </div>

        <div v-if="children.length" class="itens-grid">
          <div class="itens-grid-header">
            <div>SKU</div>
            <div>Tamanho</div>
            <div>Cor</div>
            <div class="itens-grid-col-preco">Preço</div>
            <div class="itens-grid-col-centro">Estoque</div>
            <div></div>
          </div>
          <div v-for="(filho, index) in children" :key="filho.id ?? index" class="itens-grid-row">
            <div class="itens-grid-cell-nome">{{ filho.sku }}</div>
            <div>{{ filho.size || '—' }}</div>
            <div>{{ filho.colorwayName || '—' }}</div>
            <div class="itens-grid-col-preco">{{ formatarPreco(filho.salePrice) }}</div>
            <div class="itens-grid-col-centro">{{ filho.stockQuantity ?? 0 }}</div>
            <div class="itens-grid-col-acao">
              <button type="button" class="btn-editar-item" :data-test="`variante-editar-${index}`" @click="abrirEdicaoVariante(index)">
                ✏️ Editar
              </button>
            </div>
          </div>
        </div>
        <div v-else class="itens-vazio">
          <div class="itens-vazio-titulo">Nenhuma variante adicionada</div>
          <div class="itens-vazio-texto">Use "+ Adicionar Variante" para cadastrar cada variação (tamanho, cor etc.).</div>
        </div>
        <p v-if="erros.children" class="field-error">{{ erros.children }}</p>
      </CollapsibleSection>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <FormActions :saving="salvando" save-label="Salvar Produto" @cancel="cancelar" />
    </form>

    <SlideOver v-if="painelAberto" title="Editar Variante" width="488px" @close="fecharPainel">
      <CollapsibleSection title="Identificação">
        <TextField v-model="draft.sku" label="Código SKU" required :error="errosDraft.sku" test-id="variante-sku" />
        <TextField v-model="barcodeDraftModel" label="Código de Barra (EAN/GTIN)" placeholder="7891234567890" />
      </CollapsibleSection>

      <CollapsibleSection title="Preços">
        <div class="grid grid-2">
          <div>
            <label class="field-label">Preço de Venda *</label>
            <input v-model.number="draft.salePrice" type="number" step="0.01" min="0" data-test="variante-preco-venda" />
            <p v-if="errosDraft.salePrice" class="field-error">{{ errosDraft.salePrice }}</p>
          </div>
          <div>
            <label class="field-label">Preço de Custo</label>
            <input v-model.number="draft.costPrice" type="number" step="0.01" min="0" />
          </div>
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Estoque">
        <div class="grid grid-2">
          <div>
            <label class="field-label">Qtd. em Estoque</label>
            <input v-model.number="draft.stockQuantity" type="number" step="1" min="0" />
          </div>
          <div>
            <label class="field-label">Tamanho</label>
            <input v-model="sizeDraftModel" placeholder="Ex: M, 40, Único" />
          </div>
          <div>
            <label class="field-label">Estoque Mínimo</label>
            <input v-model.number="draft.minStock" type="number" step="1" min="0" />
          </div>
          <div>
            <label class="field-label">Estoque Máximo</label>
            <input v-model.number="draft.maxStock" type="number" step="1" min="0" />
          </div>
          <div>
            <label class="field-label">Cor / Estampa</label>
            <select v-model="draft.colorwayId">
              <option :value="null">Sem cor/estampa</option>
              <option v-for="corEstampa in coresEstampas" :key="corEstampa.id" :value="corEstampa.id">
                {{ corEstampa.name }}
              </option>
            </select>
          </div>
        </div>
      </CollapsibleSection>

      <template #footer>
        <button type="button" class="btn-secondary" data-test="variante-cancelar" @click="fecharPainel">Cancelar</button>
        <button type="button" class="btn-primary" data-test="variante-salvar" @click="salvarVariante">Salvar Variante</button>
      </template>
    </SlideOver>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import CollapsibleSection from '@/components/CollapsibleSection.vue'
import TextField from '@/components/TextField.vue'
import FormActions from '@/components/FormActions.vue'
import SegmentedControl, { type SegmentedOption } from '@/components/SegmentedControl.vue'
import SlideOver from '@/components/SlideOver.vue'
import { getVariation, createVariation, updateVariation, type VariationParentRequest } from '@/api/productVariations'
import type { MeasurementUnit, ProductStatus } from '@/api/products'
import { listCategories, type CategoryResponse } from '@/api/categories'
import { listColorways, type ColorwayResponse } from '@/api/colorways'

const UNIDADES: MeasurementUnit[] = ['UN', 'KG', 'G', 'L', 'ML', 'MT', 'CM', 'CX', 'PC', 'PAR', 'DZ']
const STATUS_OPCOES: { value: ProductStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Ativo' },
  { value: 'INACTIVE', label: 'Inativo' },
]

const tipoOptions: SegmentedOption[] = [
  { value: 'PRODUCT', label: 'Simples' },
  { value: 'PRODUCT_KIT', label: 'Kit' },
  { value: 'VARIATION_PARENT', label: 'Com Variação' },
]

interface ChildForm {
  id?: string
  sku: string
  barcode: string | null
  salePrice: number
  costPrice: number | null
  stockQuantity: number | null
  minStock: number | null
  maxStock: number | null
  size: string | null
  colorwayId: string | null
  colorwayName?: string | null
}

function novoDraft(): ChildForm {
  return {
    sku: '',
    barcode: null,
    salePrice: 0,
    costPrice: null,
    stockQuantity: 0,
    minStock: null,
    maxStock: null,
    size: null,
    colorwayId: null,
  }
}

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario() {
  return {
    name: '',
    sku: '',
    brand: '',
    categoryId: null as string | null,
    salePrice: 0,
    status: 'ACTIVE' as ProductStatus,
    description: '',
    measurementUnit: 'UN' as MeasurementUnit,
  }
}

const form = reactive(novoFormulario())
const children = ref<ChildForm[]>([])
const erros = reactive<{ name?: string; sku?: string; salePrice?: string; children?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)
const categorias = ref<CategoryResponse[]>([])
const coresEstampas = ref<ColorwayResponse[]>([])

const painelAberto = ref(false)
const editingIndex = ref<number | null>(null)
const draft = reactive<ChildForm>(novoDraft())
const errosDraft = reactive<{ sku?: string; salePrice?: string }>({})

const barcodeDraftModel = computed({
  get: () => draft.barcode ?? '',
  set: (valor: string) => {
    draft.barcode = valor
  },
})
const sizeDraftModel = computed({
  get: () => draft.size ?? '',
  set: (valor: string) => {
    draft.size = valor
  },
})

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function aoMudarTipo(tipo: string) {
  if (tipo === 'PRODUCT') {
    router.push({ name: 'produtos-novo' })
  } else if (tipo === 'PRODUCT_KIT') {
    router.push({ name: 'produtos-novo-kit' })
  }
}

function abrirNovaVariante() {
  editingIndex.value = null
  Object.assign(draft, novoDraft())
  errosDraft.sku = undefined
  errosDraft.salePrice = undefined
  painelAberto.value = true
}

function abrirEdicaoVariante(index: number) {
  editingIndex.value = index
  Object.assign(draft, children.value[index])
  errosDraft.sku = undefined
  errosDraft.salePrice = undefined
  painelAberto.value = true
}

function fecharPainel() {
  painelAberto.value = false
}

function salvarVariante() {
  errosDraft.sku = draft.sku.trim() ? undefined : 'Campo obrigatório'
  errosDraft.salePrice = Number(draft.salePrice) > 0 ? undefined : 'Informe um preço maior que zero'
  if (errosDraft.sku || errosDraft.salePrice) {
    return
  }
  const colorway = coresEstampas.value.find((c) => c.id === draft.colorwayId)
  const registro: ChildForm = { ...draft, colorwayName: colorway?.name ?? null }
  if (editingIndex.value === null) {
    children.value.push(registro)
  } else {
    children.value[editingIndex.value] = registro
  }
  painelAberto.value = false
}

onMounted(async () => {
  try {
    const pagina = await listCategories({ ativo: true, size: 100 })
    categorias.value = pagina.content
  } catch {
    // Categoria list is a convenience dropdown -- a load failure just leaves
    // "Sem categoria" as the only option.
  }

  try {
    const pagina = await listColorways({ ativo: true, size: 100 })
    coresEstampas.value = pagina.content
  } catch {
    // Same reasoning as categorias above.
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const variacao = await getVariation(id)
      form.name = variacao.name
      form.sku = variacao.sku
      form.brand = variacao.brand
      form.categoryId = variacao.categoryId
      form.salePrice = variacao.salePrice
      form.status = variacao.status
      form.description = variacao.description
      form.measurementUnit = variacao.measurementUnit
      children.value = variacao.children.map((c) => ({
        id: c.id,
        sku: c.sku,
        barcode: c.barcode,
        salePrice: c.salePrice,
        costPrice: c.costPrice,
        stockQuantity: c.stockQuantity,
        minStock: c.minStock,
        maxStock: c.maxStock,
        size: c.size,
        colorwayId: c.colorwayId,
        colorwayName: c.colorwayName,
      }))

      if (
        variacao.categoryId &&
        !categorias.value.some((categoria) => categoria.id === variacao.categoryId)
      ) {
        categorias.value = [...categorias.value, { id: variacao.categoryId, name: variacao.categoryName ?? '' } as CategoryResponse]
      }
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do produto.'
    }
  }
})

function validar(): boolean {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
  erros.sku = form.sku.trim() ? undefined : 'Campo obrigatório'
  erros.salePrice = Number(form.salePrice) > 0 ? undefined : 'Informe um preço maior que zero'
  erros.children = children.value.length > 0 ? undefined : 'Adicione ao menos uma variante'
  return !erros.name && !erros.sku && !erros.salePrice && !erros.children
}

function paraPayload(): VariationParentRequest {
  return {
    name: form.name,
    sku: form.sku,
    brand: form.brand,
    categoryId: form.categoryId,
    salePrice: Number(form.salePrice) || 0,
    status: form.status,
    description: form.description,
    measurementUnit: form.measurementUnit,
    children: children.value.map((c) => ({
      id: c.id,
      sku: c.sku,
      barcode: c.barcode?.trim() || null,
      salePrice: Number(c.salePrice) || 0,
      costPrice: c.costPrice === null || c.costPrice === undefined ? null : Number(c.costPrice),
      stockQuantity: c.stockQuantity === null || c.stockQuantity === undefined ? null : Number(c.stockQuantity),
      minStock: c.minStock === null || c.minStock === undefined ? null : Number(c.minStock),
      maxStock: c.maxStock === null || c.maxStock === undefined ? null : Number(c.maxStock),
      size: c.size?.trim() || null,
      colorwayId: c.colorwayId,
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
      await updateVariation(id, payload)
    } else {
      await createVariation(payload)
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

input:disabled {
  background: var(--pm-bg);
  color: var(--pm-text-muted);
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

.info-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--pm-accent-bg);
  border: 1px solid var(--pm-accent);
  border-radius: 6px;
  font-size: 12px;
  color: var(--pm-text-mid);
}

.info-banner-icon {
  font-size: 16px;
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

/* Variantes */
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
  grid-template-columns: 1.2fr 90px 120px 110px 90px 100px;
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
  font-weight: 600;
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

.btn-editar-item {
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--pm-border-light);
  border-radius: 4px;
  background: var(--pm-white);
  color: var(--pm-text-dark);
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

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
}
</style>
