<template>
  <AppShell title="Cores / Estampas">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Cores / Estampas" :count="countLabel">
      <button type="button" class="btn-primary" data-test="nova-cor-estampa" @click="novaCorEstampa">+ Nova Cor / Estampa</button>
    </PageHeader>

    <FilterBar
      :search="filtros.busca"
      search-placeholder="Buscar cor ou estampa por nome..."
      :categories="['Status']"
      :value-map="{ Status: ['Ativo', 'Inativo'] }"
      @update:search="onBuscaChange"
      @update:filters="onFiltrosChange"
    />

    <ListCard title="Lista de Cores / Estampas" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort('name')">
            Cor / Estampa
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'name' }">{{ sortIcon() }}</span>
          </div>
          <div class="table-grid-col">Vigência</div>
          <div class="table-grid-col">Produtos</div>
          <div class="table-grid-col">Status</div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="colorway in pagina.content"
          :key="colorway.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${colorway.id}`"
          @click="editarCorEstampa(colorway.id)"
        >
          <div class="table-grid-cell table-grid-cell-nome">{{ colorway.name }}</div>
          <div class="table-grid-cell">{{ formatarData(colorway.effectiveDate) }}</div>
          <div class="table-grid-cell">{{ colorway.linkedProducts }} produtos</div>
          <div class="table-grid-cell">
            <StatusBadge :label="colorway.active ? 'Ativo' : 'Inativo'" :color="colorway.active ? 'green' : 'red'" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(colorway)" />
          </div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma cor/estampa para exibir.</p>
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
  listColorways,
  getColorwayCounts,
  deleteColorway,
  type ColorwayResponse,
  type ColorwayCounts,
  type Page as ApiPage,
} from '@/api/colorways'

const router = useRouter()

const filtros = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const sortField = ref<'name' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const pagina = ref<ApiPage<ColorwayResponse>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<ColorwayCounts | null>(null)
const erro = ref('')

const countLabel = computed(() => (counts.value ? `${counts.value.total} cores/estampas cadastradas` : undefined))
const statsCard = computed<ListCardStat[]>(() =>
  counts.value
    ? [
        { value: counts.value.total, label: 'Total', color: 'dark' },
        { value: counts.value.active, label: 'Ativas', color: 'green' },
        { value: counts.value.inactive, label: 'Inativas', color: 'red' },
      ]
    : [],
)

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function sortIcon() {
  if (sortField.value !== 'name') {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(campo: 'name') {
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
  try {
    pagina.value = await listColorways({
      busca: filtros.busca || undefined,
      ativo,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de cores/estampas.'
  }
}

async function carregarContagens() {
  try {
    counts.value = await getColorwayCounts()
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

function novaCorEstampa() {
  router.push({ name: 'cores-estampas-novo' })
}

function editarCorEstampa(id: string) {
  router.push({ name: 'cores-estampas-editar', params: { id } })
}

async function excluir(colorway: ColorwayResponse) {
  if (!confirm(`Excluir a cor/estampa "${colorway.name}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deleteColorway(colorway.id)
    await Promise.all([carregar(pagina.value.number), carregarContagens()])
  } catch (err: any) {
    erro.value = err?.response?.data?.mensagem ?? 'Não foi possível excluir a cor/estampa.'
  }
}

function acoesPara(colorway: ColorwayResponse): ActionsMenuItem[] {
  return [
    { label: 'Editar', action: () => editarCorEstampa(colorway.id), testId: 'acao-editar' },
    { label: 'Excluir', action: () => excluir(colorway), danger: true, testId: 'acao-excluir' },
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
  grid-template-columns: 1fr 130px 130px 100px 72px;
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
