<template>
  <AppShell :title="modoEdicao ? 'Editar Marca' : 'Nova Marca'">
    <PageHeader :title="modoEdicao ? 'Edição de Marca' : 'Cadastro de Marca'" />

    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <TextField
          v-model="form.name"
          label="Nome da Marca"
          required
          :error="erros.name"
          placeholder="Ex: Marca Alpha"
          test-id="nome"
          @blur="validarNome"
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

      <FormActions :saving="salvando" save-label="Salvar Marca" @cancel="cancelar" />
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
  getBrand,
  createBrand,
  updateBrand,
  type BrandRequest,
} from '@/api/brands'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

const statusOptions: SegmentedOption[] = [
  { value: 'ATIVO', label: 'Ativo' },
  { value: 'INATIVO', label: 'Inativo' },
]

interface FormularioMarca {
  name: string
  active: boolean
}

function novoFormulario(): FormularioMarca {
  return { name: '', active: true }
}

const form = reactive<FormularioMarca>(novoFormulario())
const erros = reactive<{ name?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const brand = await getBrand(id)
      form.name = brand.name
      form.active = brand.active ?? true
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da marca.'
    }
  }
})

function validarNome() {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
}

function validar(): boolean {
  validarNome()
  return !erros.name
}

function paraPayload(): BrandRequest {
  return {
    name: form.name,
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
      await updateBrand(id, payload)
    } else {
      await createBrand(payload)
    }
    router.push({ name: 'marcas' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma marca cadastrada com este nome.'
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
  router.push({ name: 'marcas' })
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
