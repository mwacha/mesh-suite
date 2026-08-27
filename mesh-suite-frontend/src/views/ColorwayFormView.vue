<template>
  <AppShell :title="modoEdicao ? 'Editar Cor / Estampa' : 'Nova Cor / Estampa'">
    <PageHeader :title="modoEdicao ? 'Edição de Cor / Estampa' : 'Cadastro de Cor / Estampa'" />

    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <TextField
          v-model="form.name"
          label="Cor / Estampa"
          required
          :error="erros.name"
          placeholder="Ex: Azul Marinho, Floral Primavera"
          test-id="nome"
          @blur="validarNome"
        />
        <div class="grid grid-2">
          <div>
            <label class="field-label">Data de Vigência *</label>
            <input v-model="form.effectiveDate" type="date" data-test="data-vigencia" @blur="validarVigencia" />
            <p v-if="erros.effectiveDate" class="field-error">{{ erros.effectiveDate }}</p>
          </div>
          <TextField
            v-model="form.description"
            label="Descrição"
            placeholder="Descrição opcional..."
            test-id="descricao"
          />
        </div>
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

      <FormActions :saving="salvando" save-label="Salvar Cor / Estampa" @cancel="cancelar" />
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import TextField from '@/components/TextField.vue'
import SegmentedControl, { type SegmentedOption } from '@/components/SegmentedControl.vue'
import FormActions from '@/components/FormActions.vue'
import {
  getColorway,
  createColorway,
  updateColorway,
  type ColorwayRequest,
} from '@/api/colorways'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

const statusOptions: SegmentedOption[] = [
  { value: 'ATIVO', label: 'Ativo' },
  { value: 'INATIVO', label: 'Inativo' },
]

interface FormularioCorEstampa {
  name: string
  effectiveDate: string
  description: string
  active: boolean
}

function novoFormulario(): FormularioCorEstampa {
  return { name: '', effectiveDate: '', description: '', active: true }
}

const form = reactive<FormularioCorEstampa>(novoFormulario())
const erros = reactive<{ name?: string; effectiveDate?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const colorway = await getColorway(id)
      form.name = colorway.name
      form.effectiveDate = colorway.effectiveDate
      form.description = colorway.description ?? ''
      form.active = colorway.active ?? true
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da cor/estampa.'
    }
  }
})

function validarNome() {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
}

function validarVigencia() {
  erros.effectiveDate = form.effectiveDate ? undefined : 'Campo obrigatório'
}

function validar(): boolean {
  validarNome()
  validarVigencia()
  return !erros.name && !erros.effectiveDate
}

function paraPayload(): ColorwayRequest {
  return {
    name: form.name,
    effectiveDate: form.effectiveDate,
    description: form.description.trim() || null,
    active: form.active,
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
      await updateColorway(id, payload)
    } else {
      await createColorway(payload)
    }
    router.push({ name: 'cores-estampas' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma cor/estampa cadastrada com este nome.'
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
  router.push({ name: 'cores-estampas' })
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

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 4px 0 0;
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

.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0;
}
</style>
