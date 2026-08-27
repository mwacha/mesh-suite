<template>
  <AppShell title="Permissões de Acesso">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Permissões de Acesso">
      <button type="button" class="btn-primary" data-test="novo-usuario" @click="novoUsuario">+ Novo Usuário</button>
    </PageHeader>
    <PermissionsTabs active="usuarios" />

    <FilterBar
      :search="filtros.busca"
      search-placeholder="Buscar usuário por nome ou e-mail..."
      :categories="['Status']"
      :value-map="{ Status: ['Ativo', 'Inativo'] }"
      @update:search="onBuscaChange"
      @update:filters="onFiltrosChange"
    />

    <ListCard title="Usuários" :stats="statsCard">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nome</div>
          <div class="table-grid-col">Perfil</div>
          <div class="table-grid-col">E-mail</div>
          <div class="table-grid-col">Status</div>
          <div class="table-grid-col"></div>
        </div>

        <div v-for="user in pagina.content" :key="user.id" class="table-grid-row" :data-test="`row-${user.id}`">
          <div class="table-grid-cell table-grid-cell-nome">{{ user.name }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="user.permissionProfileName ?? '—'" color="blue" />
          </div>
          <div class="table-grid-cell">{{ user.email }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="user.active ? 'Ativo' : 'Inativo'" :color="user.active ? 'green' : 'red'" />
          </div>
          <div class="table-grid-cell">
            <button type="button" class="btn-permissoes" :data-test="`btn-permissoes-${user.id}`" @click="abrirDetalhe(user)">
              Permissões
            </button>
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

    <SlideOver v-if="detalhe" :title="detalhe.nome" width="620px" @close="fecharDetalhe">
      <p v-if="detalhe.erro" class="error-geral">{{ detalhe.erro }}</p>
      <p v-if="detalhe.carregando" class="carregando">Carregando...</p>
      <template v-else>
        <p class="detalhe-subtitulo">{{ detalhe.subtitulo }}</p>

        <section class="card">
          <h2>Perfil de Acesso</h2>
          <div class="perfil-linha">
            <select v-model="detalhe.permissionProfileId" data-test="detalhe-perfil">
              <option value="">Selecione...</option>
              <option v-for="p in perfis" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
            <button type="button" class="btn-secondary" data-test="detalhe-aplicar-perfil" @click="aplicarPerfil">
              Aplicar Perfil
            </button>
          </div>
        </section>

        <section class="card">
          <h2>Permissões por Módulo</h2>
          <table class="tabela-permissoes">
            <thead>
              <tr>
                <th></th>
                <th v-for="a in ACTIONS" :key="a">{{ ACTION_LABELS[a] }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in MODULES" :key="m">
                <td>{{ MODULE_LABELS[m] }}</td>
                <td v-for="a in ACTIONS" :key="a">
                  <input
                    type="checkbox"
                    :checked="isChecked(m, a)"
                    :data-test="`perm-${m}-${a}`"
                    @change="togglePermission(m, a)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </template>

      <template #footer>
        <button type="button" class="btn-secondary" data-test="detalhe-fechar" @click="fecharDetalhe">Fechar</button>
        <button
          type="button"
          class="btn-primary"
          data-test="detalhe-salvar"
          :disabled="detalhe.carregando || detalhe.salvando"
          @click="salvarDetalhe"
        >
          Salvar Alterações
        </button>
      </template>
    </SlideOver>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import PermissionsTabs from '@/components/PermissionsTabs.vue'
import FilterBar from '@/components/FilterBar.vue'
import ListCard, { type ListCardStat } from '@/components/ListCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Pagination from '@/components/Pagination.vue'
import SlideOver from '@/components/SlideOver.vue'
import {
  listUsers,
  getUserCounts,
  getUser,
  updateUser,
  type UserListItem,
  type UserCounts,
  type Role,
  type ModuleName,
  type ActionName,
  type Permission,
  type Page as ApiPage,
} from '@/api/users'
import { listPermissionProfiles, getPermissionProfile, type PermissionProfileSummary } from '@/api/permissionProfiles'

const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER', 'PURCHASE', 'STOCK', 'PAYABLE', 'SALE', 'PURCHASE_INVOICE']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
  PURCHASE: 'Compras',
  STOCK: 'Estoque',
  PAYABLE: 'Contas a Pagar',
  SALE: 'Vendas',
  PURCHASE_INVOICE: 'Notas de Entrada',
}
const ACTIONS: ActionName[] = ['VIEW', 'CREATE', 'EDIT', 'DELETE']
const ACTION_LABELS: Record<ActionName, string> = {
  VIEW: 'Visualizar',
  CREATE: 'Criar',
  EDIT: 'Editar',
  DELETE: 'Excluir',
}
const ROLE_LABELS: Record<Role, string> = {
  ADMINISTRATIVE: 'Administrativo',
  SALES_REP: 'Representante',
  PRODUCTION: 'Produção',
  OUTSOURCED: 'Terceirizado',
  ADMIN: 'Administrador',
}

const router = useRouter()

const filtros = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const pagina = ref<ApiPage<UserListItem>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<UserCounts | null>(null)
const perfis = ref<PermissionProfileSummary[]>([])
const erro = ref('')

const statsCard = computed<ListCardStat[]>(() =>
  counts.value
    ? [
        { value: counts.value.total, label: 'Total', color: 'dark' },
        { value: counts.value.active, label: 'Ativos', color: 'green' },
        { value: counts.value.inactive, label: 'Inativos', color: 'red' },
      ]
    : [],
)

interface Detalhe {
  userId: string
  nome: string
  subtitulo: string
  permissionProfileId: string
  permissions: Permission[]
  carregando: boolean
  salvando: boolean
  erro: string
  userBase: {
    name: string
    email: string
    phone: string
    role: Role
    active: boolean
  } | null
}

const detalhe = ref<Detalhe | null>(null)

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
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de usuários.'
  }
}

async function carregarCounts() {
  try {
    counts.value = await getUserCounts()
  } catch {
    // Pills de contagem são um complemento -- uma falha aqui não deve bloquear a listagem.
  }
}

async function carregarPerfis() {
  try {
    const paginaPerfis = await listPermissionProfiles({ size: 100 })
    perfis.value = paginaPerfis.content
  } catch {
    perfis.value = []
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

async function abrirDetalhe(user: UserListItem) {
  detalhe.value = {
    userId: user.id,
    nome: user.name,
    subtitulo: user.email,
    permissionProfileId: user.permissionProfileId ?? '',
    permissions: [],
    carregando: true,
    salvando: false,
    erro: '',
    userBase: null,
  }
  try {
    const completo = await getUser(user.id)
    if (!detalhe.value || detalhe.value.userId !== user.id) {
      return
    }
    detalhe.value.subtitulo = `${ROLE_LABELS[completo.role]} · ${completo.email}`
    detalhe.value.permissionProfileId = completo.permissionProfileId ?? ''
    detalhe.value.permissions = [...completo.permissions]
    detalhe.value.userBase = {
      name: completo.name,
      email: completo.email,
      phone: completo.phone ?? '',
      role: completo.role,
      active: completo.active,
    }
  } catch {
    detalhe.value.erro = 'Não foi possível carregar as permissões deste usuário.'
  } finally {
    if (detalhe.value) {
      detalhe.value.carregando = false
    }
  }
}

function fecharDetalhe() {
  detalhe.value = null
}

function isChecked(module: ModuleName, action: ActionName) {
  return !!detalhe.value?.permissions.some((p) => p.module === module && p.action === action)
}

function togglePermission(module: ModuleName, action: ActionName) {
  if (!detalhe.value) {
    return
  }
  const index = detalhe.value.permissions.findIndex((p) => p.module === module && p.action === action)
  if (index >= 0) {
    detalhe.value.permissions.splice(index, 1)
  } else {
    detalhe.value.permissions.push({ module, action })
  }
}

async function aplicarPerfil() {
  if (!detalhe.value || !detalhe.value.permissionProfileId) {
    return
  }
  try {
    const perfil = await getPermissionProfile(detalhe.value.permissionProfileId)
    detalhe.value.permissions = [...perfil.grants]
  } catch {
    detalhe.value.erro = 'Não foi possível carregar as permissões padrão deste perfil.'
  }
}

async function salvarDetalhe() {
  if (!detalhe.value || !detalhe.value.userBase) {
    return
  }
  detalhe.value.erro = ''
  detalhe.value.salvando = true
  try {
    await updateUser(detalhe.value.userId, {
      name: detalhe.value.userBase.name,
      email: detalhe.value.userBase.email,
      phone: detalhe.value.userBase.phone,
      role: detalhe.value.userBase.role,
      active: detalhe.value.userBase.active,
      password: '',
      confirmPassword: '',
      permissions: detalhe.value.permissions,
      permissionProfileId: detalhe.value.permissionProfileId || null,
    })
    fecharDetalhe()
    await Promise.all([carregar(pagina.value.number), carregarCounts()])
  } catch (err: any) {
    detalhe.value.erro = err?.response?.status === 403
      ? 'Você não tem permissão para executar esta ação.'
      : 'Não foi possível salvar as permissões deste usuário.'
  } finally {
    if (detalhe.value) {
      detalhe.value.salvando = false
    }
  }
}

onMounted(() => {
  carregar(0)
  carregarCounts()
  carregarPerfis()
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

.btn-primary:disabled {
  opacity: 0.6;
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
  grid-template-columns: 1fr 130px 200px 100px 96px;
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

.table-grid-row {
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.table-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-permissoes {
  height: 26px;
  padding: 0 10px;
  border: 1.5px solid var(--pm-accent);
  border-radius: 4px;
  background: var(--pm-white);
  font-size: 11px;
  font-weight: 600;
  color: var(--pm-accent);
  cursor: pointer;
  font-family: var(--pm-font);
}

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}

.detalhe-subtitulo {
  font-size: 12px;
  color: var(--pm-text-muted);
  margin: 0 0 12px;
}

.carregando {
  font-size: 13px;
  color: var(--pm-text-mid);
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 12px;
}

.card h2 {
  font-size: 13px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0 0 10px;
}

.perfil-linha {
  display: flex;
  gap: 8px;
  align-items: center;
}

.perfil-linha select {
  flex: 1;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  color: var(--pm-text-dark);
  font-size: 13px;
  font-family: var(--pm-font);
}

.tabela-permissoes {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.tabela-permissoes th,
.tabela-permissoes td {
  text-align: center;
  padding: 6px 8px;
  border-top: 1px solid var(--pm-border-light);
}

.tabela-permissoes td:first-child {
  text-align: left;
  font-weight: 600;
  color: var(--pm-text-dark);
}
</style>
