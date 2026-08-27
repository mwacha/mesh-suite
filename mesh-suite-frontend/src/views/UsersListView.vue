<template>
  <AppShell title="Usuários">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Usuários" :count="countLabel">
      <button type="button" class="btn-primary" data-test="novo-usuario" @click="novoUsuario">+ Novo Usuário</button>
    </PageHeader>

    <FilterBar
      :search="filtros.busca"
      search-placeholder="Buscar usuário por nome ou e-mail..."
      :categories="['Status']"
      :value-map="{ Status: ['Ativo', 'Inativo'] }"
      @update:search="onBuscaChange"
      @update:filters="onFiltrosChange"
    />

    <ListCard title="Lista de Usuários" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort('name')">
            Nome
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'name' }">{{ sortIcon('name') }}</span>
          </div>
          <div class="table-grid-col">E-mail</div>
          <div class="table-grid-col">Perfil</div>
          <div class="table-grid-col">Status</div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="user in pagina.content"
          :key="user.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${user.id}`"
          @click="editarUsuario(user.id)"
        >
          <div class="table-grid-cell table-grid-cell-nome">{{ user.name }}</div>
          <div class="table-grid-cell">{{ user.email }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="user.permissionProfileName ?? '—'" color="blue" />
          </div>
          <div class="table-grid-cell">
            <StatusBadge :label="user.active ? 'Ativo' : 'Inativo'" :color="user.active ? 'green' : 'red'" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(user)" :test-id="`btn-acoes-${user.id}`" />
          </div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhum usuário para exibir.</p>
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
  listUsers,
  getUserCounts,
  updateUserStatus,
  type UserListItem,
  type UserCounts,
  type Page as ApiPage,
} from '@/api/users'

const router = useRouter()

const filtros = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const pagina = ref<ApiPage<UserListItem>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<UserCounts | null>(null)
const sortField = ref<'name' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

const countLabel = computed(() => (counts.value ? `${counts.value.total} usuários cadastrados` : undefined))
const statsCard = computed<ListCardStat[]>(() =>
  counts.value
    ? [
        { value: counts.value.total, label: 'Total', color: 'dark' },
        { value: counts.value.active, label: 'Ativos', color: 'green' },
        { value: counts.value.inactive, label: 'Inativos', color: 'red' },
      ]
    : [],
)

function sortIcon(field: 'name') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'name') {
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
  const statusLabels = labelsFor('Status')
  const active = statusLabels.length === 1 ? statusLabels[0] === 'Ativo' : undefined
  try {
    pagina.value = await listUsers({
      search: filtros.busca || undefined,
      active,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de usuários.'
  }
}

async function carregarCounts() {
  erro.value = ''
  try {
    counts.value = await getUserCounts()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de usuários.'
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

function novoUsuario() {
  router.push({ name: 'usuarios-novo' })
}

function editarUsuario(id: string) {
  router.push({ name: 'usuarios-editar', params: { id } })
}

async function alternarStatus(user: UserListItem) {
  erro.value = ''
  try {
    await updateUserStatus(user.id, !user.active)
    await Promise.all([carregar(pagina.value.number), carregarCounts()])
  } catch (err: any) {
    erro.value = err?.response?.status === 403
      ? 'Você não tem permissão para executar esta ação.'
      : 'Não foi possível atualizar o status.'
  }
}

function acoesPara(user: UserListItem): ActionsMenuItem[] {
  return [
    { label: 'Editar', action: () => editarUsuario(user.id), testId: 'acao-editar' },
    {
      label: user.active ? 'Inativar' : 'Ativar',
      action: () => alternarStatus(user),
      testId: 'acao-status',
    },
  ]
}

onMounted(() => {
  carregar(0)
  carregarCounts()
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
  grid-template-columns: 1fr 200px 130px 100px 90px;
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

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}
</style>
