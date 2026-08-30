<template>
  <AppShell title="Transportadoras">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Transportadoras" :count="countLabel">
      <button type="button" class="btn-primary" data-test="nova-transportadora" @click="novaTransportadora">+ Nova Transportadora</button>
    </PageHeader>

    <FilterBar
      :search="filtros.busca"
      search-placeholder="Buscar transportadora por nome..."
      :categories="categorias"
      :value-map="valueMap"
      @update:search="onBuscaChange"
      @update:filters="onFiltrosChange"
    />

    <ListCard title="Lista de Transportadoras" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort('tradeName')">
            Nome / Razão Social
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'tradeName' }">{{ sortIcon('tradeName') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-cidade" @click="toggleSort('city')">
            Cidade
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'city' }">{{ sortIcon('city') }}</span>
          </div>
          <div class="table-grid-col" data-test="col-telefone">Telefone</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-status" @click="toggleSort('status')">
            Status
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'status' }">{{ sortIcon('status') }}</span>
          </div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="parceiro in pagina.content"
          :key="parceiro.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${parceiro.id}`"
          @click="editarTransportadora(parceiro.id)"
        >
          <div class="table-grid-cell table-grid-cell-nome">{{ parceiro.tradeName }}</div>
          <div class="table-grid-cell">{{ parceiro.city }}</div>
          <div class="table-grid-cell">{{ maskTelefone(parceiro.whatsapp) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(parceiro.status)" :color="statusColor(parceiro.status)" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(parceiro)" :test-id="`btn-acoes-${parceiro.id}`" />
          </div>
        </div>
      </div>
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
import StatusBadge, { type StatusBadgeColor } from '@/components/StatusBadge.vue'
import ListCard, { type ListCardStat } from '@/components/ListCard.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import type { Page } from '@/api/types'
import {
  listPartners,
  getPartnerSummary,
  updatePartnerStatus,
  deletePartner,
  type PartnerListItem,
  type PartnerSummary,
  type PartnerStatus,
} from '@/api/partners'
import { listMunicipalities } from '@/api/municipalities'
import { maskTelefone } from '@/utils/masks'

const router = useRouter()

const STATUS_LABELS: Record<string, PartnerStatus> = { Ativo: 'ACTIVE', 'Em Risco': 'AT_RISK', Bloqueado: 'BLOCKED' }
const UFS = ['AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI',
  'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO']

const categorias = ['Status', 'UF', 'Cidade']
const cidades = ref<string[]>([])
const valueMap = computed<Record<string, string[]>>(() => ({
  Status: Object.keys(STATUS_LABELS),
  UF: UFS,
  Cidade: cidades.value,
}))

const filtros = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const sortField = ref<'tradeName' | 'city' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')

const pagina = ref<Page<PartnerListItem>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<PartnerSummary | null>(null)
const erro = ref('')

const countLabel = computed(() => (resumo.value ? `${resumo.value.total} transportadoras cadastradas` : undefined))
const statsCard = computed<ListCardStat[]>(() =>
  resumo.value
    ? [
        { value: resumo.value.total, label: 'Total', color: 'dark' },
        { value: resumo.value.active, label: 'Ativos', color: 'green' },
        { value: resumo.value.atRisk, label: 'Em Risco', color: 'amber' },
        { value: resumo.value.blocked, label: 'Bloqueados', color: 'red' },
      ]
    : [],
)

function statusLabel(status: PartnerStatus) {
  return { ACTIVE: 'Ativo', AT_RISK: 'Em Risco', BLOCKED: 'Bloqueado' }[status]
}

function statusColor(status: PartnerStatus): StatusBadgeColor {
  return { ACTIVE: 'green', AT_RISK: 'amber', BLOCKED: 'red' }[status] as StatusBadgeColor
}

function sortIcon(field: 'tradeName' | 'city' | 'status') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'tradeName' | 'city' | 'status') {
  if (sortField.value === field) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortDir.value = 'asc'
  }
  carregar(0)
}

function labelsFor(categoria: string): string[] {
  return filtrosAvancados.value[categoria] ?? []
}

async function carregar(page: number) {
  erro.value = ''
  const status = labelsFor('Status').map((l) => STATUS_LABELS[l])
  const uf = labelsFor('UF')
  const cidade = labelsFor('Cidade')
  try {
    pagina.value = await listPartners({
      busca: filtros.busca || undefined,
      papel: 'CARRIER',
      status: status.length ? status : undefined,
      uf: uf.length ? uf : undefined,
      cidade: cidade.length ? cidade : undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de transportadoras.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await getPartnerSummary('CARRIER')
  } catch {
    erro.value = 'Não foi possível carregar o resumo de transportadoras.'
  }
}

async function carregarCidades() {
  const ufsSelecionadas = labelsFor('UF')
  const uf = ufsSelecionadas.length === 1 ? ufsSelecionadas[0] : undefined
  try {
    cidades.value = await listMunicipalities({ uf })
  } catch {
    cidades.value = []
  }
}

function onBuscaChange(valor: string) {
  filtros.busca = valor
  carregar(0)
}

async function onFiltrosChange(filtrosNovos: Record<string, string[]>) {
  const ufAnterior = labelsFor('UF')[0]
  filtrosAvancados.value = filtrosNovos
  const ufNova = labelsFor('UF')[0]
  if (ufNova !== ufAnterior) {
    await carregarCidades()
  }
  carregar(0)
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

function novaTransportadora() {
  router.push({ name: 'transportadoras-novo' })
}

function abrirTransportadora(id: string) {
  router.push({ name: 'transportadoras-detalhe', params: { id } })
}

function editarTransportadora(id: string) {
  router.push({ name: 'transportadoras-editar', params: { id } })
}

async function alternarStatus(parceiro: PartnerListItem) {
  erro.value = ''
  const novoStatus = parceiro.status === 'BLOCKED' ? 'ACTIVE' : 'BLOCKED'
  try {
    await updatePartnerStatus(parceiro.id, novoStatus)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status.'
  }
}

async function excluir(parceiro: PartnerListItem) {
  if (!confirm(`Excluir a transportadora "${parceiro.tradeName}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deletePartner(parceiro.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir a transportadora.'
  }
}

function acoesPara(parceiro: PartnerListItem): ActionsMenuItem[] {
  return [
    { label: 'Ver', action: () => abrirTransportadora(parceiro.id), testId: 'acao-ver' },
    { label: 'Editar', action: () => editarTransportadora(parceiro.id), testId: 'acao-editar' },
    {
      label: parceiro.status === 'BLOCKED' ? 'Ativar' : 'Bloquear',
      action: () => alternarStatus(parceiro),
      testId: 'acao-status',
    },
    { label: 'Excluir', action: () => excluir(parceiro), danger: true, testId: 'acao-excluir' },
  ]
}

onMounted(() => {
  carregar(0)
  carregarResumo()
  carregarCidades()
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
  grid-template-columns: 1fr 150px 150px 100px 90px;
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
}
</style>
