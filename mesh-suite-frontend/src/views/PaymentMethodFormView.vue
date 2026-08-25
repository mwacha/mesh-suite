<template>
  <AppShell :title="modoEdicao ? 'Editar Forma de Pagamento' : 'Nova Forma de Pagamento'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados Gerais</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Descrição *</label>
            <input v-model="form.description" data-test="descricao" placeholder="Ex: 30/60/90" />
            <p v-if="erros.description" class="field-error">{{ erros.description }}</p>
          </div>
          <div>
            <label class="field-label">Status</label>
            <div class="toggle-pair">
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': form.active }" @click="form.active = true">Ativo</button>
              <button type="button" class="toggle-btn" :class="{ 'toggle-btn--ativo': !form.active }" @click="form.active = false">Inativo</button>
            </div>
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Parcelas</h2>
        <table class="tabela-parcelas">
          <thead>
            <tr>
              <th>Parcela</th>
              <th>Prazo (dias)</th>
              <th>Percentual (%)</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(parcela, index) in parcelas" :key="index">
              <td>{{ index + 1 }}</td>
              <td>
                <input
                  v-model.number="parcela.daysDue"
                  type="number"
                  min="0"
                  :data-test="`parcela-dias-${index}`"
                />
              </td>
              <td>
                <input
                  v-model.number="parcela.percentage"
                  type="number"
                  step="0.01"
                  min="0"
                  :data-test="`parcela-percentual-${index}`"
                />
              </td>
              <td>
                <button
                  type="button"
                  class="btn-remover"
                  :data-test="`remover-parcela-${index}`"
                  :disabled="parcelas.length === 1"
                  @click="removerParcela(index)"
                >
                  ✕
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <button type="button" class="btn-add-parcela" data-test="adicionar-parcela" @click="adicionarParcela">+ Adicionar Parcela</button>
        <p class="hint">Total: {{ totalPercentual.toFixed(2) }}%</p>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Forma de Pagamento</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getPaymentMethod,
  createPaymentMethod,
  updatePaymentMethod,
  type PaymentMethodRequest,
  type PaymentMethodInstallmentInput,
} from '@/api/paymentMethods'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): PaymentMethodRequest {
  return {
    description: '',
    active: true,
    installments: [],
  }
}

const form = reactive<PaymentMethodRequest>(novoFormulario())
const parcelas = ref<PaymentMethodInstallmentInput[]>([{ daysDue: 0, percentage: 100 }])
const erros = reactive<{ description?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

const totalPercentual = computed(() =>
  parcelas.value.reduce((total, p) => total + (Number(p.percentage) || 0), 0),
)

function adicionarParcela() {
  parcelas.value.push({ daysDue: 0, percentage: 0 })
}

function removerParcela(index: number) {
  if (parcelas.value.length === 1) {
    return
  }
  parcelas.value.splice(index, 1)
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const forma = await getPaymentMethod(id)
      form.description = forma.description
      form.active = forma.active
      parcelas.value = forma.installments.map((i) => ({ daysDue: i.daysDue, percentage: i.percentage }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da forma de pagamento.'
    }
  }
})

function validar(): boolean {
  erros.description = form.description.trim() ? undefined : 'Campo obrigatório'
  if (!erros.description && Math.abs(totalPercentual.value - 100) > 0.01) {
    erroGeral.value = 'A soma dos percentuais das parcelas deve ser igual a 100%'
    return false
  }
  return !erros.description
}

function paraPayload(): PaymentMethodRequest {
  return {
    description: form.description,
    active: form.active,
    installments: parcelas.value.map(({ daysDue, percentage }) => ({
      daysDue: Number(daysDue),
      percentage: Number(percentage),
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
      await updatePaymentMethod(id, payload)
    } else {
      await createPaymentMethod(payload)
    }
    router.push({ name: 'formas-pagamento' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma forma de pagamento cadastrada com esta descrição.'
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
  router.push({ name: 'formas-pagamento' })
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

input,
select {
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

.toggle-pair {
  display: flex;
  gap: 6px;
}

.toggle-btn {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.toggle-btn--ativo {
  background: var(--pm-accent);
  color: var(--pm-white);
  border-color: var(--pm-accent);
}

.tabela-parcelas {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-bottom: 10px;
}

.tabela-parcelas th {
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  background: var(--pm-bg);
  padding: 6px 10px;
}

.tabela-parcelas td {
  padding: 6px 10px;
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.btn-remover {
  border: none;
  background: none;
  color: var(--pm-error);
  cursor: pointer;
  font-size: 13px;
}

.btn-remover:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-add-parcela {
  background: none;
  border: 1.5px dashed var(--pm-accent);
  color: var(--pm-accent);
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  cursor: pointer;
}

.hint {
  font-size: 11px;
  color: var(--pm-text-muted);
  margin: 8px 0 0;
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
