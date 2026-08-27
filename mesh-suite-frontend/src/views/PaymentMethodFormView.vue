<template>
  <AppShell :title="modoEdicao ? 'Editar Forma de Recebimento' : 'Nova Forma de Recebimento'">
    <PageHeader :title="modoEdicao ? 'Edição de Forma de Recebimento' : 'Cadastro de Forma de Recebimento'" />

    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <div class="grid grid-2">
          <TextField
            v-model="form.description"
            label="Nome da forma"
            required
            :error="erros.description"
            placeholder="Ex: Cartão Crédito"
            test-id="descricao"
            @blur="validarNome"
          />
          <SelectField
            v-model="form.type"
            label="Tipo"
            required
            :error="erros.type"
            test-id="tipo"
          >
            <option value="">Selecione o tipo...</option>
            <option v-for="(rotulo, valor) in PAYMENT_METHOD_TYPE_LABEL" :key="valor" :value="valor">{{ rotulo }}</option>
          </SelectField>
        </div>
        <TextField
          v-model="form.notes"
          label="Descrição"
          placeholder="Descrição opcional..."
          test-id="observacao"
        />
      </section>

      <section class="card">
        <h2>Condições</h2>
        <div class="grid grid-3">
          <div>
            <label class="field-label">Máx. de parcelas<span class="required-mark">*</span></label>
            <input v-model.number="form.maxInstallments" type="number" min="1" placeholder="Ex: 12" data-test="max-parcelas" />
            <p v-if="erros.maxInstallments" class="field-error">⚠️ {{ erros.maxInstallments }}</p>
          </div>
          <div>
            <label class="field-label">Taxa / juros (%)</label>
            <input v-model.number="form.interestRate" type="number" step="0.01" min="0" placeholder="0,00%" data-test="taxa-juros" />
          </div>
          <div>
            <label class="field-label">Prazo de compensação (dias)</label>
            <input v-model.number="form.settlementDays" type="number" min="0" placeholder="Ex: 1" data-test="prazo-compensacao" />
          </div>
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

      <FormActions :saving="salvando" save-label="Salvar Forma" @cancel="cancelar" />
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import TextField from '@/components/TextField.vue'
import SelectField from '@/components/SelectField.vue'
import SegmentedControl, { type SegmentedOption } from '@/components/SegmentedControl.vue'
import FormActions from '@/components/FormActions.vue'
import {
  getPaymentMethod,
  createPaymentMethod,
  updatePaymentMethod,
  PAYMENT_METHOD_TYPE_LABEL,
  type PaymentMethodRequest,
} from '@/api/paymentMethods'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

const statusOptions: SegmentedOption[] = [
  { value: 'ATIVO', label: 'Ativo' },
  { value: 'INATIVO', label: 'Inativo' },
]

interface FormularioFormaRecebimento {
  description: string
  type: PaymentMethodRequest['type']
  notes: string
  active: boolean
  maxInstallments: number
  interestRate: number | null
  settlementDays: number | null
}

function novoFormulario(): FormularioFormaRecebimento {
  return {
    description: '',
    type: '',
    notes: '',
    active: true,
    maxInstallments: 1,
    interestRate: null,
    settlementDays: null,
  }
}

const form = reactive<FormularioFormaRecebimento>(novoFormulario())
const erros = reactive<{ description?: string; type?: string; maxInstallments?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const forma = await getPaymentMethod(id)
      form.description = forma.description
      form.type = forma.type ?? ''
      form.notes = forma.notes ?? ''
      form.active = forma.active
      form.maxInstallments = forma.maxInstallments
      form.interestRate = forma.interestRate
      form.settlementDays = forma.settlementDays
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da forma de recebimento.'
    }
  }
})

function validarNome() {
  erros.description = form.description.trim() ? undefined : 'Campo obrigatório'
}

function validar(): boolean {
  validarNome()
  erros.type = form.type ? undefined : 'Campo obrigatório'
  erros.maxInstallments = Number(form.maxInstallments) >= 1 ? undefined : 'Informe ao menos 1 parcela'
  return !erros.description && !erros.type && !erros.maxInstallments
}

function paraPayload(): PaymentMethodRequest {
  return {
    description: form.description,
    type: form.type,
    notes: form.notes.trim() || undefined,
    active: form.active,
    maxInstallments: Number(form.maxInstallments),
    interestRate: form.interestRate ?? null,
    settlementDays: form.settlementDays ?? null,
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
      await updatePaymentMethod(id, payload)
    } else {
      await createPaymentMethod(payload)
    }
    router.push({ name: 'formas-recebimento' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma forma de recebimento cadastrada com este nome.'
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
  router.push({ name: 'formas-recebimento' })
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

.required-mark {
  color: var(--pm-error);
  margin-left: 2px;
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
