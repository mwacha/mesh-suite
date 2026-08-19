<!-- mesh-suite-frontend/src/views/PurchaseInvoiceFormView.vue -->
<template>
  <AppShell title="Lançar Compra">
    <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

    <section v-if="order" class="card">
      <h3 class="card-title">Ordem de Compra nº {{ order.number }}</h3>
      <div class="resumo-ordem">
        <div><span>Fornecedor</span><span>{{ order.supplierName }}</span></div>
        <div><span>Total</span><span>{{ formatarPreco(order.total) }}</span></div>
      </div>

      <table class="tabela-itens">
        <thead>
          <tr>
            <th>Produto</th>
            <th>Qtd.</th>
            <th>Valor Unit.</th>
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in order.items" :key="item.productId">
            <td>{{ item.productName }}</td>
            <td>{{ item.quantity }}</td>
            <td>{{ formatarPreco(item.unitPrice) }}</td>
            <td>{{ formatarPreco(item.totalValue) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="order" class="card">
      <h3 class="card-title">Dados da Nota Fiscal</h3>
      <div class="campos-nota">
        <label>
          Número
          <input v-model="form.invoiceNumber" data-test="nota-numero" />
        </label>
        <label>
          Série
          <input v-model="form.series" data-test="nota-serie" />
        </label>
        <label>
          Modelo
          <input v-model="form.model" data-test="nota-modelo" />
        </label>
        <label>
          Data de Emissão
          <input v-model="form.issueDate" type="date" data-test="nota-data-emissao" />
        </label>
        <label>
          Data de Entrada
          <input v-model="form.entryDate" type="date" data-test="nota-data-entrada" />
        </label>
      </div>
    </section>

    <section v-if="order" class="card">
      <h3 class="card-title">Parcelas</h3>
      <button type="button" class="btn-secondary" data-test="parcela-adicionar" @click="adicionarParcela">+ Adicionar Parcela</button>

      <table v-if="form.installments.length" class="tabela-itens">
        <thead>
          <tr>
            <th>Valor</th>
            <th>Vencimento</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(parcela, index) in form.installments" :key="index">
            <td>
              <input v-model.number="parcela.amount" type="number" step="0.01" min="0" :data-test="`parcela-valor-${index}`" />
            </td>
            <td>
              <input v-model="parcela.dueDate" type="date" :data-test="`parcela-vencimento-${index}`" />
            </td>
            <td><button type="button" class="btn-remover" :data-test="`parcela-remover-${index}`" @click="removerParcela(index)">✕</button></td>
          </tr>
        </tbody>
      </table>

      <div class="totais">
        <div><span>Soma das parcelas</span><span :data-test="'soma-parcelas'">{{ formatarPreco(somaParcelas) }}</span></div>
        <div><span>Total da nota</span><span>{{ formatarPreco(order.total) }}</span></div>
      </div>
      <p v-if="form.installments.length && !parcelasBatem" class="field-error">
        A soma das parcelas precisa ser igual ao total da nota.
      </p>
    </section>

    <div v-if="order" class="acoes">
      <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
      <button type="button" class="btn-primary" data-test="salvar" :disabled="!podeSalvar" @click="salvar">Salvar</button>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { getPurchaseOrder, type PurchaseOrderResponse } from '@/api/purchaseOrders'
import { issuePurchaseInvoice, type InstallmentInput } from '@/api/purchaseInvoices'

const route = useRoute()
const router = useRouter()

const order = ref<PurchaseOrderResponse | null>(null)
const erroGeral = ref('')
const salvando = ref(false)

interface FormState {
  invoiceNumber: string
  series: string
  model: string
  issueDate: string
  entryDate: string
  installments: InstallmentInput[]
}

const form = reactive<FormState>({
  invoiceNumber: '',
  series: '',
  model: '',
  issueDate: '',
  entryDate: '',
  installments: [],
})

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

const somaParcelas = computed(() =>
  form.installments.reduce((soma, p) => soma + (Number(p.amount) || 0), 0),
)

// Same HALF_UP-at-2-decimals comparison the backend does with BigDecimal --
// floating point sums can land a cent off (e.g. 0.1 + 0.2), so comparing
// rounded cents avoids a false mismatch that would never reproduce server-side.
const parcelasBatem = computed(() => {
  if (!order.value) {
    return false
  }
  return Math.round(somaParcelas.value * 100) === Math.round(order.value.total * 100)
})

const podeSalvar = computed(() =>
  !salvando.value &&
  form.invoiceNumber.trim() !== '' &&
  form.series.trim() !== '' &&
  form.model.trim() !== '' &&
  form.issueDate !== '' &&
  form.entryDate !== '' &&
  form.installments.length > 0 &&
  parcelasBatem.value,
)

function adicionarParcela() {
  form.installments.push({ amount: 0, dueDate: '' })
}

function removerParcela(index: number) {
  form.installments.splice(index, 1)
}

function cancelar() {
  router.push({ name: 'compras' })
}

async function salvar() {
  erroGeral.value = ''
  salvando.value = true
  try {
    await issuePurchaseInvoice(route.params.id as string, {
      invoiceNumber: form.invoiceNumber,
      series: form.series,
      model: form.model,
      issueDate: form.issueDate,
      entryDate: form.entryDate,
      installments: form.installments,
    })
    router.push({ name: 'compras' })
  } catch {
    erroGeral.value = 'Não foi possível lançar a compra.'
  } finally {
    salvando.value = false
  }
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id !== 'string') {
    return
  }
  try {
    order.value = await getPurchaseOrder(id)
  } catch {
    erroGeral.value = 'Não foi possível carregar a ordem de compra.'
  }
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  font-family: var(--pm-font);
}

.card-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0 0 12px;
}

.resumo-ordem {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  font-size: 13px;
}

.resumo-ordem div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.resumo-ordem span:first-child {
  color: var(--pm-text-mid);
  font-size: 11px;
  text-transform: uppercase;
}

.campos-nota {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.campos-nota label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--pm-text-mid);
}

.campos-nota input,
.tabela-itens input {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  background: var(--pm-white);
}

.tabela-itens {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-top: 12px;
}

.tabela-itens th {
  text-align: left;
  font-size: 11px;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  padding: 6px 8px;
  border-bottom: 1px solid var(--pm-border-light);
}

.tabela-itens td {
  padding: 6px 8px;
  border-bottom: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.btn-remover {
  background: none;
  border: none;
  color: var(--pm-error);
  cursor: pointer;
  font-size: 13px;
}

.totais {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
  font-size: 13px;
}

.totais > div {
  display: flex;
  justify-content: space-between;
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: 8px 0 0;
}

.acoes {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: var(--pm-font);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: var(--pm-font);
}
</style>
