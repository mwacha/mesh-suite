<template>
  <AppShell title="Usuários">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar usuário por nome ou e-mail..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.profile" @change="carregar(0)">
        <option value="">Perfil</option>
        <option value="ADMIN">Admin</option>
        <option value="MANAGER">Gerente</option>
        <option value="SALES">Vendedor</option>
        <option value="VIEWER">Visualizador</option>
      </select>
      <select v-model="filtros.active" @change="carregar(0)">
        <option value="">Status</option>
        <option value="true">Ativo</option>
        <option value="false">Inativo</option>
      </select>
      <button type="button" class="btn-primary" data-test="novo-usuario" @click="novoUsuario">+ Novo Usuário</button>
    </div>

    <div v-if="counts" class="resumo">
      <span class="resumo-item">{{ counts.total }} Total</span>
      <span class="resumo-item resumo-ativo">{{ counts.active }} Ativos</span>
      <span class="resumo-item resumo-inativo">{{ counts.inactive }} Inativos</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nome</th>
            <th>E-mail</th>
            <th>Perfil</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in pagina.content" :key="user.id">
            <td>{{ user.name }}</td>
            <td>{{ user.email }}</td>
            <td><span class="badge-perfil">{{ PROFILE_LABELS[user.profile] }}</span></td>
            <td><span class="badge" :class="user.active ? 'badge-ATIVO' : 'badge-INATIVO'">{{ user.active ? 'Ativo' : 'Inativo' }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(user.id, $event)"
              >
                Ações
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <Teleport to="body">
      <div
        v-if="userAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarUsuario(userAcoesAtual.id)">Editar</div>
        <div data-test="acao-status" @click="alternarStatus(userAcoesAtual)">
          {{ userAcoesAtual.active ? 'Inativar' : 'Ativar' }}
        </div>
      </div>
    </Teleport>

    <div class="paginacao">
      <button type="button" :disabled="pagina.number === 0" @click="carregar(pagina.number - 1)">‹</button>
      <span>Página {{ pagina.number + 1 }} de {{ Math.max(pagina.totalPages, 1) }}</span>
      <button type="button" :disabled="pagina.number + 1 >= pagina.totalPages" @click="carregar(pagina.number + 1)">›</button>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  listUsers,
  getUserCounts,
  updateUserStatus,
  type UserListItem,
  type UserCounts,
  type Page as ApiPage,
  type Profile,
} from '@/api/users'

const PROFILE_LABELS: Record<Profile, string> = {
  ADMIN: 'Admin',
  MANAGER: 'Gerente',
  SALES: 'Vendedor',
  VIEWER: 'Visualizador',
}

const router = useRouter()

const filtros = reactive({ busca: '', profile: '', active: '' })
const pagina = ref<ApiPage<UserListItem>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const counts = ref<UserCounts | null>(null)
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const userAcoesAtual = computed(() =>
  pagina.value.content.find((u) => u.id === acoesAbertas.value) ?? null,
)

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listUsers({
      search: filtros.busca || undefined,
      profile: (filtros.profile || undefined) as Profile | undefined,
      active: filtros.active === '' ? undefined : filtros.active === 'true',
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

function novoUsuario() {
  router.push({ name: 'usuarios-novo' })
}

function editarUsuario(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'usuarios-editar', params: { id } })
}

function toggleAcoes(id: string, event: MouseEvent) {
  if (acoesAbertas.value === id) {
    acoesAbertas.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  posicaoDropdown.value = {
    top: `${rect.bottom + 4}px`,
    left: `${rect.right - 120}px`,
  }
  acoesAbertas.value = id
}

async function alternarStatus(user: UserListItem) {
  acoesAbertas.value = null
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

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  font-family: var(--pm-font);
}

.busca {
  flex: 1;
}

.toolbar input,
.toolbar select {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  background: var(--pm-white);
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
}

.resumo {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.resumo-item {
  background: var(--pm-bg);
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--pm-text-dark);
}

.resumo-ativo {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.resumo-inativo {
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 12px;
}

.tabela {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  font-family: var(--pm-font);
}

.tabela th {
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  background: var(--pm-bg);
  padding: 8px 12px;
}

.tabela td {
  padding: 8px 12px;
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.badge-perfil {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.badge-ATIVO {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.badge-INATIVO {
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.btn-acoes {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}

.dropdown-acoes {
  position: fixed;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  min-width: 120px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 28px rgba(0, 0, 0, 0.12);
  z-index: 10;
}

.dropdown-acoes div {
  padding: 8px 12px;
  font-size: 12px;
  cursor: pointer;
  color: var(--pm-text-dark);
}

.paginacao {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 13px;
  color: var(--pm-text-mid);
}

.paginacao button {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  border-radius: 6px;
  width: 28px;
  height: 28px;
  cursor: pointer;
}

.paginacao button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
