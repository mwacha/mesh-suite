<template>
  <AppShell :title="modoEdicao ? 'Editar Produto' : 'Novo Produto'">
    <form class="form" @submit.prevent="salvar">
      <CollapsibleSection title="Tipo de Produto">
        <SegmentedControl
          :model-value="'PRODUCT'"
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
          <TextField v-model="form.barcode" label="Código de Barra (EAN/GTIN)" placeholder="7891234567890" />
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
            <input v-model.number="form.costPrice" type="number" step="0.01" min="0" data-test="preco-custo" />
          </div>
        </div>
        <div class="grid grid-2">
          <TextField v-model="sizeModel" label="Tamanho" placeholder="Ex: M, 40, Único" test-id="tamanho" />
          <div>
            <label class="field-label">Cor / Estampa</label>
            <select v-model="form.colorwayId" data-test="cor-estampa">
              <option :value="null">Sem cor/estampa</option>
              <option v-for="corEstampa in coresEstampas" :key="corEstampa.id" :value="corEstampa.id">
                {{ corEstampa.name }}
              </option>
            </select>
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

      <div class="grid-cards">
        <CollapsibleSection title="Estoque">
          <div class="grid grid-2">
            <div>
              <label class="field-label">Qtd. em Estoque</label>
              <input v-model.number="form.stockQuantity" type="number" step="1" min="0" />
            </div>
            <div>
              <label class="field-label">Unidade de Medida</label>
              <select v-model="form.measurementUnit">
                <option v-for="unidade in UNIDADES" :key="unidade" :value="unidade">{{ unidade }}</option>
              </select>
            </div>
            <div>
              <label class="field-label">Estoque Mínimo</label>
              <input v-model.number="form.minStock" type="number" step="1" min="0" />
            </div>
            <div>
              <label class="field-label">Estoque Máximo</label>
              <input v-model.number="form.maxStock" type="number" step="1" min="0" />
            </div>
          </div>
        </CollapsibleSection>

        <CollapsibleSection title="Pesos & Dimensões">
          <div class="grid grid-2">
            <div>
              <label class="field-label">Peso (kg)</label>
              <input v-model.number="form.weight" type="number" step="0.001" min="0" />
            </div>
            <div>
              <label class="field-label">Comprimento (cm)</label>
              <input v-model.number="form.length" type="number" step="0.01" min="0" />
            </div>
            <div>
              <label class="field-label">Largura (cm)</label>
              <input v-model.number="form.width" type="number" step="0.01" min="0" />
            </div>
            <div>
              <label class="field-label">Altura (cm)</label>
              <input v-model.number="form.height" type="number" step="0.01" min="0" />
            </div>
          </div>
        </CollapsibleSection>
      </div>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <FormActions :saving="salvando" save-label="Salvar Produto" @cancel="cancelar" />
    </form>
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
import {
  getProduct,
  createProduct,
  updateProduct,
  type ProductRequest,
  type ProductStatus,
  type MeasurementUnit,
} from '@/api/products'
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

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): ProductRequest {
  return {
    name: '',
    sku: '',
    barcode: '',
    brand: '',
    categoryId: null,
    colorwayId: null,
    salePrice: 0,
    costPrice: null,
    status: 'ACTIVE',
    description: '',
    stockQuantity: 0,
    measurementUnit: 'UN',
    minStock: null,
    maxStock: null,
    size: null,
    weight: null,
    length: null,
    width: null,
    height: null,
  }
}

const form = reactive<ProductRequest>(novoFormulario())
const erros = reactive<{ name?: string; sku?: string; salePrice?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)
const categorias = ref<CategoryResponse[]>([])
const coresEstampas = ref<ColorwayResponse[]>([])

const sizeModel = computed({
  get: () => form.size ?? '',
  set: (valor: string) => {
    form.size = valor
  },
})

// Switching type in create mode discards this form and starts the Kit/Variação
// one instead -- there's no "convert" operation, so no state to carry over.
function aoMudarTipo(tipo: string) {
  if (tipo === 'PRODUCT_KIT') {
    router.push({ name: 'produtos-novo-kit' })
  } else if (tipo === 'VARIATION_PARENT') {
    router.push({ name: 'produtos-novo-variacao' })
  }
}

onMounted(async () => {
  try {
    const pagina = await listCategories({ ativo: true, size: 100 })
    categorias.value = pagina.content
  } catch {
    // Categoria list is a convenience dropdown, not a required field --
    // if it fails to load, the form still works with "Sem categoria" as
    // the only option, and the current value (if editing) still round-trips.
  }

  try {
    const pagina = await listColorways({ ativo: true, size: 100 })
    coresEstampas.value = pagina.content
  } catch {
    // Same convenience-dropdown reasoning as categorias above.
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const produto = await getProduct(id)
      Object.assign(form, produto)

      // An inactive categoria is filtered out of the `ativo: true` list above,
      // but per design spec it must stay visible in the dropdown when it's
      // already linked to this produto (it just can't be picked as a new
      // option for produtos without one). Splice in a minimal synthetic entry
      // from the produto response itself so the <select> has a matching
      // <option> to bind to -- a full CategoryResponse isn't needed since
      // the template only reads `id`/`name`.
      if (
        produto.categoryId &&
        !categorias.value.some((categoria) => categoria.id === produto.categoryId)
      ) {
        categorias.value = [
          ...categorias.value,
          {
            id: produto.categoryId,
            name: produto.categoryName ?? '',
          } as CategoryResponse,
        ]
      }

      // Same reasoning as the categoria splice above, mirrored for corEstampa.
      if (
        produto.colorwayId &&
        !coresEstampas.value.some((corEstampa) => corEstampa.id === produto.colorwayId)
      ) {
        coresEstampas.value = [
          ...coresEstampas.value,
          {
            id: produto.colorwayId,
            name: produto.colorwayName ?? '',
          } as ColorwayResponse,
        ]
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
  return !erros.name && !erros.sku && !erros.salePrice
}

function numeroOuNull(valor: unknown): number | null {
  return valor === '' || valor === null || valor === undefined ? null : Number(valor)
}

function paraPayload(): ProductRequest {
  return {
    ...form,
    salePrice: Number(form.salePrice) || 0,
    costPrice: numeroOuNull(form.costPrice),
    stockQuantity: Number(form.stockQuantity) || 0,
    minStock: numeroOuNull(form.minStock),
    maxStock: numeroOuNull(form.maxStock),
    size: form.size?.trim() || null,
    weight: numeroOuNull(form.weight),
    length: numeroOuNull(form.length),
    width: numeroOuNull(form.width),
    height: numeroOuNull(form.height),
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
      await updateProduct(id, payload)
    } else {
      await createProduct(payload)
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

.grid-cards {
  display: flex;
  gap: 12px;
}

.grid-cards > * {
  flex: 1;
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
</style>
