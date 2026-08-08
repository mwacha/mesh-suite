<template>
  <AppShell :title="modoEdicao ? 'Editar Produto' : 'Novo Produto'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <div class="field-full">
          <label class="field-label">Nome do Produto *</label>
          <input v-model="form.nome" data-test="nome" />
          <p v-if="erros.nome" class="field-error">{{ erros.nome }}</p>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Código SKU *</label>
            <input v-model="form.sku" data-test="sku" />
            <p v-if="erros.sku" class="field-error">{{ erros.sku }}</p>
          </div>
          <div>
            <label class="field-label">Código de Barra (EAN/GTIN)</label>
            <input v-model="form.codigoBarras" placeholder="7891234567890" />
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Marca</label>
            <input v-model="form.marca" />
          </div>
          <div>
            <label class="field-label">Categoria</label>
            <select v-model="form.categoriaId" data-test="categoria">
              <option :value="null">Sem categoria</option>
              <option v-for="categoria in categorias" :key="categoria.id" :value="categoria.id">
                {{ categoria.nome }}
              </option>
            </select>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Cor / Estampa</label>
            <select v-model="form.corEstampaId" data-test="cor-estampa">
              <option :value="null">Sem cor/estampa</option>
              <option v-for="corEstampa in coresEstampas" :key="corEstampa.id" :value="corEstampa.id">
                {{ corEstampa.nome }}
              </option>
            </select>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Preço de Venda *</label>
            <input v-model.number="form.precoVenda" type="number" step="0.01" min="0" data-test="preco-venda" />
            <p v-if="erros.precoVenda" class="field-error">{{ erros.precoVenda }}</p>
          </div>
          <div>
            <label class="field-label">Preço de Custo</label>
            <input v-model.number="form.precoCusto" type="number" step="0.01" min="0" data-test="preco-custo" />
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
                'status-pill--ativo': form.status === opt.value && opt.value === 'ATIVO',
                'status-pill--inativo': form.status === opt.value && opt.value === 'INATIVO',
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
          <textarea v-model="form.descricao" rows="3" placeholder="Descreva o produto..."></textarea>
        </div>
      </section>

      <div class="grid-cards">
        <section class="card">
          <h2>Estoque</h2>
          <div class="grid grid-2">
            <div>
              <label class="field-label">Qtd. em Estoque</label>
              <input v-model.number="form.quantidadeEstoque" type="number" step="1" min="0" />
            </div>
            <div>
              <label class="field-label">Unidade de Medida</label>
              <select v-model="form.unidadeMedida">
                <option v-for="unidade in UNIDADES" :key="unidade" :value="unidade">{{ unidade }}</option>
              </select>
            </div>
            <div>
              <label class="field-label">Estoque Mínimo</label>
              <input v-model.number="form.estoqueMinimo" type="number" step="1" min="0" />
            </div>
            <div>
              <label class="field-label">Estoque Máximo</label>
              <input v-model.number="form.estoqueMaximo" type="number" step="1" min="0" />
            </div>
          </div>
        </section>

        <section class="card">
          <h2>Pesos &amp; Dimensões</h2>
          <div class="grid grid-2">
            <div>
              <label class="field-label">Peso (kg)</label>
              <input v-model.number="form.peso" type="number" step="0.001" min="0" />
            </div>
            <div>
              <label class="field-label">Comprimento (cm)</label>
              <input v-model.number="form.comprimento" type="number" step="0.01" min="0" />
            </div>
            <div>
              <label class="field-label">Largura (cm)</label>
              <input v-model.number="form.largura" type="number" step="0.01" min="0" />
            </div>
            <div>
              <label class="field-label">Altura (cm)</label>
              <input v-model.number="form.altura" type="number" step="0.01" min="0" />
            </div>
          </div>
        </section>
      </div>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Produto</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  buscarProduto,
  criarProduto,
  atualizarProduto,
  type ProdutoRequest,
  type StatusProduto,
  type UnidadeMedida,
} from '@/api/produtos'
import { listarCategorias, type CategoriaResponse } from '@/api/categorias'
import { listarCoresEstampas, type CorEstampaResponse } from '@/api/coresEstampas'

const UNIDADES: UnidadeMedida[] = ['UN', 'KG', 'G', 'L', 'ML', 'MT', 'CM', 'CX', 'PC', 'PAR', 'DZ']
const STATUS_OPCOES: { value: StatusProduto; label: string }[] = [
  { value: 'ATIVO', label: 'Ativo' },
  { value: 'INATIVO', label: 'Inativo' },
]

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): ProdutoRequest {
  return {
    nome: '',
    sku: '',
    codigoBarras: '',
    marca: '',
    categoriaId: null,
    corEstampaId: null,
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

const form = reactive<ProdutoRequest>(novoFormulario())
const erros = reactive<{ nome?: string; sku?: string; precoVenda?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)
const categorias = ref<CategoriaResponse[]>([])
const coresEstampas = ref<CorEstampaResponse[]>([])

onMounted(async () => {
  try {
    const pagina = await listarCategorias({ ativo: true, size: 100 })
    categorias.value = pagina.content
  } catch {
    // Categoria list is a convenience dropdown, not a required field --
    // if it fails to load, the form still works with "Sem categoria" as
    // the only option, and the current value (if editing) still round-trips.
  }

  try {
    const pagina = await listarCoresEstampas({ ativo: true, size: 100 })
    coresEstampas.value = pagina.content
  } catch {
    // Same convenience-dropdown reasoning as categorias above.
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const produto = await buscarProduto(id)
      Object.assign(form, produto)

      // An inactive categoria is filtered out of the `ativo: true` list above,
      // but per design spec it must stay visible in the dropdown when it's
      // already linked to this produto (it just can't be picked as a new
      // option for produtos without one). Splice in a minimal synthetic entry
      // from the produto response itself so the <select> has a matching
      // <option> to bind to -- a full CategoriaResponse isn't needed since
      // the template only reads `id`/`nome`.
      if (
        produto.categoriaId &&
        !categorias.value.some((categoria) => categoria.id === produto.categoriaId)
      ) {
        categorias.value = [
          ...categorias.value,
          {
            id: produto.categoriaId,
            nome: produto.categoriaNome ?? '',
          } as CategoriaResponse,
        ]
      }

      // Same reasoning as the categoria splice above, mirrored for corEstampa.
      if (
        produto.corEstampaId &&
        !coresEstampas.value.some((corEstampa) => corEstampa.id === produto.corEstampaId)
      ) {
        coresEstampas.value = [
          ...coresEstampas.value,
          {
            id: produto.corEstampaId,
            nome: produto.corEstampaNome ?? '',
          } as CorEstampaResponse,
        ]
      }
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

function numeroOuNull(valor: unknown): number | null {
  return valor === '' || valor === null || valor === undefined ? null : Number(valor)
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

.grid-cards .card {
  flex: 1;
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
