<template>
  <AppShell title="Empresa">
    <p v-if="error" class="error-general">{{ error }}</p>

    <PageHeader title="Empresa" :count="countLabel">
      <button type="button" class="btn-primary" data-test="new-company" @click="newCompany">+ Nova Empresa</button>
    </PageHeader>

    <FilterBar
      :search="filters.search"
      search-placeholder="Buscar por razão social ou CNPJ..."
      :categories="['Status', 'UF', 'Cidade']"
      :value-map="valueMap"
      @update:search="onSearchChange"
      @update:filters="onFiltersChange"
    />

    <ListCard title="Lista de Empresas" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col table-grid-col-sortable" data-test="col-legal-name" @click="toggleSort('legalName')">
            Razão Social
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'legalName' }">{{ sortIcon('legalName') }}</span>
          </div>
          <div class="table-grid-col">CNPJ</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-city" @click="toggleSort('city')">
            Cidade / UF
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'city' }">{{ sortIcon('city') }}</span>
          </div>
          <div class="table-grid-col">Status</div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="company in page.content"
          :key="company.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${company.id}`"
          @click="editCompany(company.id)"
        >
          <div class="table-grid-cell table-grid-cell-legal-name">{{ company.legalName }}</div>
          <div class="table-grid-cell table-grid-cell-cnpj">{{ maskCnpj(company.cnpj) }}</div>
          <div class="table-grid-cell">{{ cityLabel(company) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="company.active ? 'Ativo' : 'Inativo'" :color="company.active ? 'green' : 'red'" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="actionsFor(company)" :test-id="`actions-${company.id}`" />
          </div>
        </div>
      </div>
      <p v-if="!page.content.length" class="empty-state">Nenhuma empresa para exibir.</p>
    </ListCard>

    <Pagination
      :number="page.number"
      :total-pages="page.totalPages"
      :total-elements="page.totalElements"
      :size="page.size"
      @update:page="load"
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
  listCompanies,
  getCompanyCounts,
  updateCompanyStatus,
  deleteCompany,
  type CompanyResponse,
  type CompanyCounts,
  type Page as ApiPage,
} from '@/api/companies'
import { listMunicipalities } from '@/api/municipalities'
import { maskCnpj } from '@/utils/masks'

const router = useRouter()

const UFS = ['AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI',
  'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO']

const filters = reactive({ search: '' })
const advancedFilters = ref<Record<string, string[]>>({})
const cities = ref<string[]>([])
const valueMap = computed<Record<string, string[]>>(() => ({
  Status: ['Ativo', 'Inativo'],
  UF: UFS,
  Cidade: cities.value,
}))

const sortField = ref<'legalName' | 'city' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const page = ref<ApiPage<CompanyResponse>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<CompanyCounts | null>(null)
const error = ref('')

const countLabel = computed(() => (counts.value ? `${counts.value.total} empresas cadastradas` : undefined))
const statsCard = computed<ListCardStat[]>(() =>
  counts.value
    ? [
        { value: counts.value.total, label: 'Total', color: 'dark' },
        { value: counts.value.active, label: 'Ativas', color: 'green' },
        { value: counts.value.inactive, label: 'Inativas', color: 'red' },
      ]
    : [],
)

function cityLabel(company: CompanyResponse) {
  if (!company.city) {
    return '-'
  }
  return company.state ? `${company.city} / ${company.state}` : company.city
}

function sortIcon(field: 'legalName' | 'city') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'legalName' | 'city') {
  if (sortField.value === field) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortDir.value = 'asc'
  }
  load(0)
}

function labelsFor(category: string): string[] {
  return advancedFilters.value[category] ?? []
}

async function load(pageNumber: number) {
  error.value = ''
  const statusLabels = labelsFor('Status')
  const active = statusLabels.length === 1 ? statusLabels[0] === 'Ativo' : undefined
  const uf = labelsFor('UF')[0]
  const city = labelsFor('Cidade')[0]
  try {
    page.value = await listCompanies({
      busca: filters.search || undefined,
      ativo: active,
      uf: uf || undefined,
      cidade: city || undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page: pageNumber,
      size: page.value.size,
    })
  } catch {
    error.value = 'Não foi possível carregar a lista de empresas.'
  }
}

async function loadCounts() {
  try {
    counts.value = await getCompanyCounts()
  } catch {
    // Pills de contagem são um complemento -- uma falha aqui não deve bloquear a listagem.
  }
}

async function loadCities() {
  const selectedUfs = labelsFor('UF')
  const uf = selectedUfs.length === 1 ? selectedUfs[0] : undefined
  try {
    cities.value = await listMunicipalities({ uf })
  } catch {
    cities.value = []
  }
}

function onSearchChange(value: string) {
  filters.search = value
  load(0)
}

async function onFiltersChange(newFilters: Record<string, string[]>) {
  const previousUf = labelsFor('UF')[0]
  advancedFilters.value = newFilters
  const newUf = labelsFor('UF')[0]
  if (newUf !== previousUf) {
    await loadCities()
  }
  load(0)
}

function onSizeChange(newSize: number) {
  page.value.size = newSize
  load(0)
}

function newCompany() {
  router.push({ name: 'empresas-novo' })
}

function editCompany(id: string) {
  router.push({ name: 'empresas-editar', params: { id } })
}

async function toggleStatus(company: CompanyResponse) {
  error.value = ''
  try {
    await updateCompanyStatus(company.id, !company.active)
    await Promise.all([load(page.value.number), loadCounts()])
  } catch {
    error.value = 'Não foi possível atualizar o status.'
  }
}

async function remove(company: CompanyResponse) {
  if (!confirm(`Excluir a empresa "${company.legalName}"?`)) {
    return
  }
  error.value = ''
  try {
    await deleteCompany(company.id)
    await Promise.all([load(page.value.number), loadCounts()])
  } catch (err: any) {
    error.value = err?.response?.data?.mensagem ?? 'Não foi possível excluir a empresa.'
  }
}

function actionsFor(company: CompanyResponse): ActionsMenuItem[] {
  return [
    { label: 'Editar', action: () => editCompany(company.id), testId: 'action-edit' },
    {
      label: company.active ? 'Desativar' : 'Ativar',
      action: () => toggleStatus(company),
      testId: 'action-status',
    },
    { label: 'Excluir', action: () => remove(company), danger: true, testId: 'action-delete' },
  ]
}

onMounted(() => {
  load(0)
  loadCounts()
  loadCities()
})
</script>

<style scoped>
.error-general {
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
  grid-template-columns: 1fr 160px 160px 90px 90px;
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

.table-grid-cell-legal-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.table-grid-cell-cnpj {
  font-family: monospace;
}

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}
</style>
