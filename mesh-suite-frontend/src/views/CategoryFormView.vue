<template>
  <AppShell :title="modoEdicao ? 'Editar Categoria' : 'Nova Categoria'">
    <PageHeader :title="modoEdicao ? 'Edição de Categoria' : 'Cadastro de Categoria'" />

    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <TextField
          v-model="form.name"
          label="Nome da Categoria"
          required
          :error="erros.name"
          placeholder="Ex: Higiene Pessoal"
          test-id="nome"
          @blur="validarNome"
        />
        <SearchSelect
          v-model="form.parentId"
          label="Categoria Pai"
          :selected-label="parentLabel"
          :items="parentResults"
          placeholder="Nenhuma (categoria raiz)"
          :empty-message="parentSearchError || undefined"
          :empty-is-error="!!parentSearchError"
          test-id="categoria-pai"
          @search="searchCategoriaPai"
          @select="selectParent"
        />
        <TextField
          v-model="form.description"
          label="Descrição"
          placeholder="Descrição opcional da categoria..."
          test-id="descricao"
        />
        <div class="status-bloco">
          <label class="status-label">Status</label>
          <SegmentedControl
            :model-value="form.active ? 'ATIVO' : 'INATIVO'"
            :options="statusOptions"
            variant="status"
            test-id="status"
            @update:model-value="(v) => (form.active = v === 'ATIVO')"
          />
        </div>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <FormActions :saving="salvando" save-label="Salvar Categoria" @cancel="cancelar" />
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import TextField from '@/components/TextField.vue'
import SearchSelect, { type SearchSelectItem } from '@/components/SearchSelect.vue'
import SegmentedControl, { type SegmentedOption } from '@/components/SegmentedControl.vue'
import FormActions from '@/components/FormActions.vue'
import {
  getCategory,
  createCategory,
  updateCategory,
  listCategories,
  type CategoryRequest,
} from '@/api/categories'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

const statusOptions: SegmentedOption[] = [
  { value: 'ATIVO', label: 'Ativo' },
  { value: 'INATIVO', label: 'Inativo' },
]

interface FormularioCategoria {
  name: string
  description: string
  active: boolean
  parentId: string | null
}

function novoFormulario(): FormularioCategoria {
  return { name: '', description: '', active: true, parentId: null }
}

const form = reactive<FormularioCategoria>(novoFormulario())
const erros = reactive<{ name?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

const parentLabel = ref('')
const parentResults = ref<SearchSelectItem[]>([])
const parentSearchError = ref('')

// Uma busca com erro e uma lista genuinamente vazia rendem a mesma lista
// vazia -- sem isso a busca de categoria pai mostraria silenciosamente
// "Nenhum resultado" quando a chamada falhou de verdade.
const SEARCH_FAILED = 'Não foi possível buscar. Verifique sua conexão e tente novamente.'

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const category = await getCategory(id)
      form.name = category.name
      form.description = category.description ?? ''
      form.active = category.active ?? true
      form.parentId = category.parentId
      parentLabel.value = category.parentName ?? ''
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da categoria.'
    }
  }
})

// O wireframe só lista categorias raiz como opção de pai: mantém a hierarquia
// em dois níveis, e o backend rejeita uma categoria pai que já tenha pai.
// Categorias raiz inativas continuam aparecendo (ex: "Limpeza" no wireframe).
async function searchCategoriaPai(query: string) {
  parentSearchError.value = ''
  try {
    const pagina = await listCategories({ busca: query || undefined, raiz: true, size: 20 })
    parentResults.value = pagina.content
      .filter((c) => c.id !== route.params.id)
      .map((c) => ({ id: c.id, label: c.name }))
  } catch {
    parentResults.value = []
    parentSearchError.value = SEARCH_FAILED
  }
}

function selectParent(item: SearchSelectItem) {
  form.parentId = item.id
  parentLabel.value = item.label
}

function validarNome() {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
}

function validar(): boolean {
  validarNome()
  return !erros.name
}

function paraPayload(): CategoryRequest {
  return {
    name: form.name,
    description: form.description.trim() || null,
    active: form.active,
    parentId: form.parentId,
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
      await updateCategory(id, payload)
    } else {
      await createCategory(payload)
    }
    router.push({ name: 'categorias' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma categoria cadastrada com este nome.'
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
  router.push({ name: 'categorias' })
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
  font-size: 13px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0 0 12px;
}

.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0;
}

.status-bloco {
  margin-top: 2px;
}

.status-label {
  display: block;
  font-size: 12px;
  color: var(--pm-text-muted);
  margin-bottom: 5px;
}
</style>
