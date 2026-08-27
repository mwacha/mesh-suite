<template>
  <AppShell title="Formas de Recebimento">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Formas de Recebimento" :count="countLabel">
      <button type="button" class="btn-primary" data-test="nova-forma-recebimento" @click="novaFormaRecebimento">+ Nova Forma</button>
    </PageHeader>

    <FilterBar
      :search="filtros.busca"
      search-placeholder="Buscar forma de recebimento por nome..."
      :categories="['Tipo', 'Status']"
      :value-map="{ Tipo: tipoLabels, Status: ['Ativo', 'Inativo'] }"
      @update:search="onBuscaChange"
      @update:filters="onFiltrosChange"
    />

    <ListCard title="Lista de Formas de Recebimento" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort('description')">
            Forma de Recebimento
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'description' }">{{ sortIcon('description') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-tipo" @click="toggleSort('type')">
            Tipo
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'type' }">{{ sortIcon('type') }}</span>
          </div>
          <div class="table-grid-col">Parcelamento</div>
          <div class="table-grid-col">Status</div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="forma in pagina.content"
          :key="forma.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${forma.id}`"
          @click="editarFormaRecebimento(forma.id)"
        >
          <div class="table-grid-cell table-grid-cell-nome">{{ forma.description }}</div>
          <div class="table-grid-cell">{{ forma.type ? PAYMENT_METHOD_TYPE_LABEL[forma.type] : '—' }}</div>
          <div class="table-grid-cell">{{ resumoParcelamento(forma) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="forma.active ? 'Ativo' : 'Inativo'" :color="forma.active ? 'green' : 'red'" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(forma)" />
          </div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma forma de recebimento para exibir.</p>
    </ListCard>

    <Pagination
      :number="pagina.number"
      :total-pages="pagina.totalPages"
      :total-elements="pagina.totalElements"
      :size="pagina.size"
      @update:page="carregar"
      @update:size="onSizeChange"
    />
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import ListCard, { type ListCardStat } from '@/components/ListCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import {
  listPaymentMethods,
  getPaymentMethodCounts,
  deletePaymentMethod,
  PAYMENT_METHOD_TYPE_LABEL,
  type PaymentMethodSummary,
  type PaymentMethodType,
  type PaymentMethodCounts,
  type Page as ApiPage,
} from '@/api/paymentMethods'

const router = useRouter()

const tipoLabels = Object.values(PAYMENT_METHOD_TYPE_LABEL)
const LABEL_TO_TIPO = Object.fromEntries(
  Object.entries(PAYMENT_METHOD_TYPE_LABEL).map(([valor, rotulo]) => [rotulo, valor as PaymentMethodType]),
) as Record<string, PaymentMethodType>

const filtros = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const sortField = ref<'description' | 'type' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const pagina = ref<ApiPage<PaymentMethodSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<PaymentMethodCounts | null>(null)
const erro = ref('')

const countLabel = computed(() => (counts.value ? `${counts.value.total} formas cadastradas` : undefined))
const statsCard = computed<ListCardStat[]>(() =>
  counts.value
    ? [
        { value: counts.value.total, label: 'Total', color: 'dark' },
        { value: counts.value.active, label: 'Ativas', color: 'green' },
        { value: counts.value.inactive, label: 'Inativas', color: 'red' },
      ]
    : [],
)

// O wireframe mostra três formatos nesta coluna: "1x" para pagamento único,
// "até 12x" quando só há um teto de parcelas e "30/60/90/120" quando o
// parcelamento detalhado está cadastrado.
function resumoParcelamento(forma: PaymentMethodSummary) {
  if (forma.installmentDays.length > 1) {
    return forma.installmentDays.join('/')
  }
  if (forma.maxInstallments > 1) {
    return `até ${forma.maxInstallments}x`
  }
  return '1x'
}

function sortIcon(campo: 'description' | 'type') {
  if (sortField.value !== campo) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(campo: 'description' | 'type') {
  if (sortField.value === campo) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = campo
    sortDir.value = 'asc'
  }
  carregar(0)
}

function labelsFor(categoria: string): string[] {
  return filtrosAvancados.value[categoria] ?? []
}

async function carregar(page: number) {
  erro.value = ''
  const statusLabels = labelsFor('Status')
  const ativo = statusLabels.length === 1 ? statusLabels[0] === 'Ativo' : undefined
  const tipoSelecionado = labelsFor('Tipo')
  const tipo = tipoSelecionado.length === 1 ? LABEL_TO_TIPO[tipoSelecionado[0]] : undefined
  try {
    pagina.value = await listPaymentMethods({
      busca: filtros.busca || undefined,
      tipo,
      ativo,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de formas de recebimento.'
  }
}

async function carregarContagens() {
  try {
    counts.value = await getPaymentMethodCounts()
  } catch {
    // Pills de contagem são um complemento -- uma falha aqui não deve bloquear a listagem.
  }
}

function onBuscaChange(valor: string) {
  filtros.busca = valor
  carregar(0)
}

function onFiltrosChange(filtrosNovos: Record<string, string[]>) {
  filtrosAvancados.value = filtrosNovos
  carregar(0)
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

function novaFormaRecebimento() {
  router.push({ name: 'formas-recebimento-novo' })
}

function editarFormaRecebimento(id: string) {
  router.push({ name: 'formas-recebimento-editar', params: { id } })
}

async function excluir(forma: PaymentMethodSummary) {
  if (!confirm(`Excluir a forma de recebimento "${forma.description}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deletePaymentMethod(forma.id)
    await Promise.all([carregar(pagina.value.number), carregarContagens()])
  } catch (err: any) {
    erro.value = err?.response?.data?.mensagem ?? 'Não foi possível excluir a forma de recebimento.'
  }
}

function acoesPara(forma: PaymentMethodSummary): ActionsMenuItem[] {
  return [
    { label: 'Editar', action: () => editarFormaRecebimento(forma.id), testId: 'acao-editar' },
    { label: 'Excluir', action: () => excluir(forma), danger: true, testId: 'acao-excluir' },
  ]
}

onMounted(() => {
  carregar(0)
  carregarContagens()
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
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
  white-space: nowrap;
  font-family: var(--pm-font);
}

.table-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 1fr 150px 160px 100px 80px;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.table-grid-header {
  background: var(--pm-bg);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  padding: 12px;
}

.table-grid-col-sortable {
  cursor: pointer;
  white-space: nowrap;
}

.table-grid-sort-icon {
  font-size: 9px;
  color: var(--pm-text-muted);
  margin-left: 2px;
}

.table-grid-sort-icon-active {
  color: var(--pm-accent);
}

.table-grid-row {
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.table-grid-row-clickable {
  cursor: pointer;
  transition: background-color 0.1s;
}

.table-grid-row-clickable:hover {
  background: var(--pm-bg);
}

.table-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}
</style>
