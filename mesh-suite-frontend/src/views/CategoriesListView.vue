<template>
  <AppShell title="Categorias">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Categorias" :count="countLabel">
      <button type="button" class="btn-primary" data-test="nova-categoria" @click="novaCategoria">+ Nova Categoria</button>
    </PageHeader>

    <FilterBar
      :search="filtros.busca"
      search-placeholder="Buscar categoria por nome..."
      :categories="['Status']"
      :value-map="{ Status: ['Ativo', 'Inativo'] }"
      @update:search="onBuscaChange"
      @update:filters="onFiltrosChange"
    />

    <ListCard title="Lista de Categorias" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort('name')">
            Nome
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'name' }">{{ sortIcon() }}</span>
          </div>
          <div class="table-grid-col">Categoria Pai</div>
          <div class="table-grid-col">Produtos</div>
          <div class="table-grid-col">Status</div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="category in pagina.content"
          :key="category.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${category.id}`"
          @click="editarCategoria(category.id)"
        >
          <div class="table-grid-cell table-grid-cell-nome">{{ category.name }}</div>
          <div class="table-grid-cell">{{ category.parentName ?? '—' }}</div>
          <div class="table-grid-cell">{{ category.linkedProducts }} produtos</div>
          <div class="table-grid-cell">
            <StatusBadge :label="category.active ? 'Ativo' : 'Inativo'" :color="category.active ? 'green' : 'red'" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(category)" />
          </div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma categoria para exibir.</p>
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
  listCategories,
  getCategoryCounts,
  deleteCategory,
  type CategoryResponse,
  type CategoryCounts,
  type Page as ApiPage,
} from '@/api/categories'

const router = useRouter()

const filtros = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const sortField = ref<'name' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const pagina = ref<ApiPage<CategoryResponse>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<CategoryCounts | null>(null)
const erro = ref('')

const countLabel = computed(() => (counts.value ? `${counts.value.total} categorias cadastradas` : undefined))
const statsCard = computed<ListCardStat[]>(() =>
  counts.value
    ? [
        { value: counts.value.total, label: 'Total', color: 'dark' },
        { value: counts.value.active, label: 'Ativas', color: 'green' },
        { value: counts.value.inactive, label: 'Inativas', color: 'red' },
      ]
    : [],
)

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
    pagina.value = await listCategories({
      busca: filtros.busca || undefined,
      ativo,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de categorias.'
  }
}

async function carregarContagens() {
  try {
    counts.value = await getCategoryCounts()
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

function novaCategoria() {
  router.push({ name: 'categorias-novo' })
}

function editarCategoria(id: string) {
  router.push({ name: 'categorias-editar', params: { id } })
}

async function excluir(category: CategoryResponse) {
  if (!confirm(`Excluir a categoria "${category.name}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deleteCategory(category.id)
    await Promise.all([carregar(pagina.value.number), carregarContagens()])
  } catch (err: any) {
    erro.value = err?.response?.data?.mensagem ?? 'Não foi possível excluir a categoria.'
  }
}

function acoesPara(category: CategoryResponse): ActionsMenuItem[] {
  return [
    { label: 'Editar', action: () => editarCategoria(category.id), testId: 'acao-editar' },
    { label: 'Excluir', action: () => excluir(category), danger: true, testId: 'acao-excluir' },
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
  grid-template-columns: 1fr 160px 130px 100px 72px;
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
