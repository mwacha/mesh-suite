<template>
  <AppShell :title="modoEdicao ? 'Editar Produto com Variação' : 'Novo Produto com Variação'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Tipo de Produto</h2>
        <ProductTypeSelector model-value="VARIATION_PARENT" :disabled="modoEdicao" @update:model-value="aoMudarTipo" />
      </section>

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
          <textarea v-model="form.description" rows="3" placeholder="Descreva o produto..."></textarea>
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Estoque">
        <div class="grid grid-2 grid-narrow">
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
        <div class="info-banner">
          <span class="info-banner-icon">ℹ️</span>
          <div>Qtd. em estoque, mínimo e máximo são definidos individualmente em cada variante.</div>
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Tipos de Variação">
        <div class="tipos-variacao-header">
          <span class="tipos-variacao-hint">Os valores definem as combinações da tabela abaixo</span>
        </div>

        <div v-for="(vt, vtIndex) in varTypes" :key="vt.name" class="var-type-box">
          <div class="var-type-header">
            <span class="var-type-name">{{ vt.name }}</span>
            <span class="var-type-remove" :data-test="`var-tipo-remover-${vtIndex}`" @click="removerTipo(vtIndex)">Remover tipo</span>
          </div>
          <div class="var-type-values">
            <span v-for="(val, valIndex) in vt.values" :key="val" class="var-value-chip">
              {{ val }}
              <span class="var-value-remove" @click="removerValor(vtIndex, valIndex)">✕</span>
            </span>
            <template v-if="addingValueTo === vtIndex">
              <input
                v-model="novoValor"
                class="var-value-input"
                placeholder="Ex: XG"
                :data-test="`var-tipo-valor-input-${vtIndex}`"
                @keyup.enter="confirmarValor(vtIndex)"
              />
              <span class="var-value-confirm" :data-test="`var-tipo-valor-confirmar-${vtIndex}`" @click="confirmarValor(vtIndex)">✓</span>
              <span class="var-value-cancel" @click="cancelarValor">✕</span>
            </template>
            <span v-else class="var-value-add" :data-test="`var-tipo-adicionar-valor-${vtIndex}`" @click="abrirAdicionarValor(vtIndex)">+ Valor</span>
          </div>
        </div>

        <div v-if="addingType" class="var-type-new">
          <div class="var-type-new-title">Novo Tipo de Variação</div>
          <TextField v-model="novoTipoNome" label="Nome do tipo" placeholder="Ex: Material, Voltagem, Estilo..." test-id="var-novo-tipo-nome" />
          <label class="field-label">Valores</label>
          <div class="var-type-values var-type-values-new">
            <span v-for="(val, i) in novoTipoValores" :key="val" class="var-value-chip">
              {{ val }} <span class="var-value-remove" @click="novoTipoValores.splice(i, 1)">✕</span>
            </span>
            <input
              v-model="novoTipoValorTemp"
              class="var-value-input"
              placeholder="Ex: Algodão"
              data-test="var-novo-tipo-valor-input"
              @keyup.enter="adicionarValorAoNovoTipo"
            />
            <span class="var-value-confirm" data-test="var-novo-tipo-valor-confirmar" @click="adicionarValorAoNovoTipo">✓</span>
          </div>
          <div class="var-type-new-actions">
            <button type="button" class="btn-secondary" @click="cancelarNovoTipo">Cancelar</button>
            <button type="button" class="btn-primary" data-test="var-novo-tipo-confirmar" @click="confirmarNovoTipo">Confirmar Tipo</button>
          </div>
        </div>
        <div v-else class="btn-add-tipo" data-test="adicionar-tipo-variacao" @click="addingType = true">
          + Adicionar Tipo de Variação
        </div>
      </CollapsibleSection>

      <CollapsibleSection :title="`Variantes Geradas (${children.length})`">
        <div v-if="children.length" class="itens-grid">
          <div class="itens-grid-header">
            <div>Variante</div>
            <div>SKU</div>
            <div>Código de Barra</div>
            <div class="itens-grid-col-preco">Valor de Venda</div>
            <div class="itens-grid-col-centro">Estoque</div>
            <div></div>
          </div>
          <div v-for="(filho, index) in children" :key="filho.id ?? index" class="itens-grid-row">
            <div class="itens-grid-cell-combo">
              <template v-if="filho.comboLabels && filho.comboLabels.length">
                <StatusBadge v-for="label in filho.comboLabels" :key="label" :label="label" color="gray" />
              </template>
              <template v-else>
                <StatusBadge v-if="filho.size" :label="filho.size" color="gray" />
                <StatusBadge v-if="filho.colorwayName" :label="filho.colorwayName" color="gray" />
                <span v-if="!filho.size && !filho.colorwayName">—</span>
              </template>
            </div>
            <div class="itens-grid-cell-nome">{{ filho.sku }}</div>
            <div>{{ filho.barcode || '—' }}</div>
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
          <div class="itens-vazio-titulo">Nenhuma variante gerada</div>
          <div class="itens-vazio-texto">Defina os Tipos de Variação acima para gerar as combinações automaticamente.</div>
        </div>
        <p v-if="erros.children" class="field-error">{{ erros.children }}</p>
      </CollapsibleSection>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <FormActions :saving="salvando" save-label="Salvar Produto" @cancel="cancelar" />
    </form>

    <SlideOver v-if="painelAberto" :title="painelTitle" width="488px" @close="fecharPainel">
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
            <label class="field-label">Estoque Mínimo</label>
            <input v-model.number="draft.minStock" type="number" step="1" min="0" />
          </div>
          <div>
            <label class="field-label">Estoque Máximo</label>
            <input v-model.number="draft.maxStock" type="number" step="1" min="0" />
          </div>
          <div>
            <label class="field-label">Múltiplo de Venda</label>
            <input v-model.number="draft.saleMultiple" type="number" step="0.001" min="0.001" data-test="variante-multiplo-venda" />
          </div>
          <div>
            <label class="field-label">Tamanho</label>
            <input v-model="sizeDraftModel" placeholder="Ex: M, 40, Único" />
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
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import CollapsibleSection from '@/components/CollapsibleSection.vue'
import TextField from '@/components/TextField.vue'
import FormActions from '@/components/FormActions.vue'
import ProductTypeSelector from '@/components/ProductTypeSelector.vue'
import SlideOver from '@/components/SlideOver.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { getVariation, createVariation, updateVariation, type VariationParentRequest } from '@/api/productVariations'
import type { MeasurementUnit, ProductStatus } from '@/api/products'
import { listCategories, type CategoryResponse } from '@/api/categories'
import { listColorways, type ColorwayResponse } from '@/api/colorways'

const UNIDADES: MeasurementUnit[] = ['UN', 'KG', 'G', 'L', 'ML', 'MT', 'CM', 'CX', 'PC', 'PAR', 'DZ']
const STATUS_OPCOES: { value: ProductStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Ativo' },
  { value: 'INACTIVE', label: 'Inativo' },
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
  saleMultiple: number | null
  // Frontend-only bookkeeping for rows generated by the Tipos de Variação matrix
  // below -- never sent to the backend (VariationChildInput has no such field).
  // Rows added manually via "+ Adicionar Variante" leave these undefined, which
  // is what keeps the combinations watcher below from ever touching them.
  comboKey?: string
  comboLabels?: string[]
}

interface VarType {
  name: string
  values: string[]
}

function novoDraft(): ChildForm {
  return {
    id: undefined,
    sku: '',
    barcode: null,
    salePrice: 0,
    costPrice: null,
    stockQuantity: 0,
    minStock: null,
    maxStock: null,
    size: null,
    colorwayId: null,
    saleMultiple: 1,
    comboKey: undefined,
    comboLabels: undefined,
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
    saleMultiple: 1 as number | null,
  }
}

const form = reactive(novoFormulario())
const children = ref<ChildForm[]>([])
const erros = reactive<{ name?: string; sku?: string; salePrice?: string; children?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)
const categorias = ref<CategoryResponse[]>([])
const coresEstampas = ref<ColorwayResponse[]>([])

const varTypes = ref<VarType[]>([])
const addingValueTo = ref<number | null>(null)
const novoValor = ref('')
const addingType = ref(false)
const novoTipoNome = ref('')
const novoTipoValores = ref<string[]>([])
const novoTipoValorTemp = ref('')

// Cartesian product of every type's values, e.g. Tamanho:[P,M] x Cor:[Branco] ->
// [[P,Branco],[M,Branco]]. A type with no values yet is skipped rather than
// zeroing out every combination -- it just hasn't contributed a dimension yet.
// Also reused (not just the computed below) to figure out, when loading an
// existing Variação for editing, which combination each already-saved child
// actually belongs to.
function combosFor(types: VarType[]): string[][] {
  return types.reduce<string[][]>((acc, vt) => {
    if (vt.values.length === 0) {
      return acc
    }
    if (acc.length === 0) {
      return vt.values.map((v) => [v])
    }
    return acc.flatMap((combo) => vt.values.map((v) => [...combo, v]))
  }, [])
}

const combinations = computed<string[][]>(() => combosFor(varTypes.value))

function comboKeyFor(combo: string[]) {
  return combo.join('|')
}

// "Tamanho" is the one dimension the backend actually models (VariationChildInput.size);
// any other type (Cor, Material, ...) is combinator bookkeeping only -- its value shows
// as a badge in the Variantes Geradas table, but real colorway linking still happens
// per-row via the Editar panel, since a colorway is a linked entity, not free text.
function sizeFromCombo(combo: string[]): string | null {
  const idx = varTypes.value.findIndex((vt) => vt.name.trim().toLowerCase() === 'tamanho')
  return idx !== -1 ? (combo[idx] ?? null) : null
}

function sanitizeSkuPart(value: string) {
  const semAcentos = value
    .normalize('NFD')
    .split('')
    .filter((ch) => {
      const code = ch.charCodeAt(0)
      return code < 0x0300 || code > 0x036f
    })
    .join('')
  return semAcentos.toUpperCase().replace(/[^A-Z0-9]+/g, '')
}

// Keeps `children` in sync with the matrix: adds a default row for every new
// combination, removes rows whose combination no longer exists. Rows without a
// comboKey (added via "+ Adicionar Variante") are never touched here.
watch(
  combinations,
  (novasCombinacoes) => {
    const chaves = new Set(novasCombinacoes.map(comboKeyFor))
    children.value = children.value.filter((c) => !c.comboKey || chaves.has(c.comboKey))

    const existentes = new Set(children.value.map((c) => c.comboKey).filter(Boolean))
    for (const combo of novasCombinacoes) {
      const key = comboKeyFor(combo)
      if (existentes.has(key)) {
        continue
      }
      const sufixo = combo.map(sanitizeSkuPart).join('-')
      children.value.push({
        sku: form.sku ? `${form.sku}-${sufixo}` : sufixo,
        barcode: null,
        salePrice: Number(form.salePrice) || 0,
        costPrice: null,
        stockQuantity: 0,
        minStock: null,
        maxStock: null,
        size: sizeFromCombo(combo),
        colorwayId: null,
        saleMultiple: 1,
        comboKey: key,
        comboLabels: combo,
      })
    }
  },
  { deep: true },
)

function removerTipo(index: number) {
  varTypes.value.splice(index, 1)
}

function abrirAdicionarValor(index: number) {
  addingValueTo.value = index
  novoValor.value = ''
}

function confirmarValor(index: number) {
  const valor = novoValor.value.trim()
  if (valor && !varTypes.value[index].values.includes(valor)) {
    varTypes.value[index].values.push(valor)
  }
  addingValueTo.value = null
  novoValor.value = ''
}

function cancelarValor() {
  addingValueTo.value = null
  novoValor.value = ''
}

function removerValor(vtIndex: number, valIndex: number) {
  varTypes.value[vtIndex].values.splice(valIndex, 1)
}

function adicionarValorAoNovoTipo() {
  const valor = novoTipoValorTemp.value.trim()
  if (valor && !novoTipoValores.value.includes(valor)) {
    novoTipoValores.value.push(valor)
  }
  novoTipoValorTemp.value = ''
}

function confirmarNovoTipo() {
  const nome = novoTipoNome.value.trim()
  if (!nome || novoTipoValores.value.length === 0) {
    return
  }
  varTypes.value.push({ name: nome, values: [...novoTipoValores.value] })
  cancelarNovoTipo()
}

function cancelarNovoTipo() {
  addingType.value = false
  novoTipoNome.value = ''
  novoTipoValores.value = []
  novoTipoValorTemp.value = ''
}

const painelAberto = ref(false)
const editingIndex = ref(0)
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

const painelTitle = computed(() => {
  if (draft.comboLabels && draft.comboLabels.length) {
    return `Editar Variante — ${draft.comboLabels.join(', ')}`
  }
  return 'Editar Variante'
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
  children.value[editingIndex.value] = { ...draft, colorwayName: colorway?.name ?? null }
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
      form.saleMultiple = variacao.saleMultiple

      // The Tipos de Variação matrix (axis names + values) is now persisted exactly as
      // defined -- load it as-is instead of guessing it back from the children. Each
      // child is still only tagged as combo-generated if its own value per axis is
      // actually derivable (only "Tamanho" maps to a real child field, child.size --
      // any other axis has no per-child value stored anywhere, e.g. "Cor" is a best-
      // effort read of child.colorwayName, and a truly custom axis like "Material"
      // can't be derived at all) AND that combo is covered by the loaded matrix --
      // otherwise the child is left as a plain row so it's never silently dropped by
      // the sync watcher below.
      const tiposCarregados: VarType[] = variacao.variationAxes.map((axis) => ({
        name: axis.name,
        values: [...axis.values],
      }))
      const chavesValidas = new Set(combosFor(tiposCarregados).map((combo) => combo.join('|')))

      children.value = variacao.children.map((c) => {
        const labels = tiposCarregados.map((vt) =>
          vt.name.trim().toLowerCase() === 'tamanho' ? c.size : c.colorwayName,
        )
        const completo = labels.every((v): v is string => !!v)
        const key = completo ? labels.join('|') : null
        const comboLabels = key && chavesValidas.has(key) ? (labels as string[]) : undefined
        return {
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
          saleMultiple: c.saleMultiple,
          comboKey: comboLabels ? key! : undefined,
          comboLabels,
        }
      })
      varTypes.value = tiposCarregados

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
      saleMultiple: Number(c.saleMultiple) || 1,
    })),
    saleMultiple: Number(form.saleMultiple) || 1,
    variationAxes: varTypes.value.map((vt) => ({ name: vt.name, values: vt.values })),
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

.grid-narrow {
  max-width: 460px;
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

.itens-grid-cell-combo {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

/* Tipos de Variação */
.tipos-variacao-header {
  margin-bottom: 12px;
}

.tipos-variacao-hint {
  font-size: 11px;
  color: var(--pm-text-muted);
}

.var-type-box {
  margin-bottom: 10px;
  padding: 10px 12px;
  background: var(--pm-bg);
  border-radius: 6px;
  border: 1.5px solid var(--pm-border-light);
}

.var-type-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.var-type-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--pm-text-dark);
}

.var-type-remove {
  font-size: 11px;
  color: var(--pm-error);
  cursor: pointer;
}

.var-type-values {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.var-value-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  background: var(--pm-white);
  border: 1.5px solid var(--pm-border-light);
  border-radius: 16px;
  font-size: 12px;
  color: var(--pm-text-dark);
}

.var-value-remove {
  font-size: 10px;
  color: var(--pm-text-muted);
  cursor: pointer;
  line-height: 1;
}

.var-value-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1.5px dashed var(--pm-text-muted);
  border-radius: 16px;
  font-size: 12px;
  color: var(--pm-text-muted);
  cursor: pointer;
}

.var-value-input {
  width: 90px;
  height: 28px;
  border: 1.5px solid var(--pm-accent);
  border-radius: 4px;
  padding: 0 8px;
  font-size: 12px;
  color: var(--pm-text-dark);
}

.var-value-confirm,
.var-value-cancel {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
}

.var-value-confirm {
  background: var(--pm-accent);
  color: var(--pm-white);
}

.var-value-cancel {
  background: var(--pm-bg);
  color: var(--pm-text-muted);
}

.var-type-new {
  padding: 14px;
  background: var(--pm-accent-bg);
  border: 2px solid var(--pm-accent);
  border-radius: 6px;
}

.var-type-new-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin-bottom: 12px;
}

.var-type-values-new {
  margin-bottom: 14px;
}

.var-type-new-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.btn-add-tipo {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1.5px dashed var(--pm-accent);
  border-radius: 6px;
  font-size: 12px;
  color: var(--pm-accent);
  cursor: pointer;
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
