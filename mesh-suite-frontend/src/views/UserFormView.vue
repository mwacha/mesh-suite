<template>
  <AppShell :title="modoEdicao ? 'Editar Usuário' : 'Novo Usuário'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados do Usuário</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Nome completo *</label>
            <input v-model="form.name" data-test="name" />
            <p v-if="erros.name" class="field-error">{{ erros.name }}</p>
          </div>
          <div>
            <label class="field-label">E-mail *</label>
            <input v-model="form.email" data-test="email" />
            <p v-if="erros.email" class="field-error">{{ erros.email }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Telefone</label>
            <input v-model="form.phone" placeholder="(11) 99999-9999" />
          </div>
          <div>
            <label class="field-label">Papel *</label>
            <select v-model="form.role" data-test="role">
              <option value="">Selecione...</option>
              <option v-for="r in ROLES" :key="r" :value="r">{{ ROLE_LABELS[r] }}</option>
            </select>
            <p v-if="erros.role" class="field-error">{{ erros.role }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Perfil de Acesso *</label>
            <select v-model="form.profile" data-test="profile" @change="applyDefaultPermissions">
              <option value="">Selecione...</option>
              <option v-for="p in PROFILES" :key="p" :value="p">{{ PROFILE_LABELS[p] }}</option>
            </select>
            <p v-if="erros.profile" class="field-error">{{ erros.profile }}</p>
          </div>
          <div>
            <label class="field-label">Status</label>
            <select v-model="form.active">
              <option :value="true">Ativo</option>
              <option :value="false">Inativo</option>
            </select>
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Acesso ao Sistema</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Senha{{ modoEdicao ? '' : ' *' }}</label>
            <input v-model="form.password" type="password" data-test="password" />
          </div>
          <div>
            <label class="field-label">Confirmar Senha{{ modoEdicao ? '' : ' *' }}</label>
            <input v-model="form.confirmPassword" type="password" data-test="confirm-password" />
          </div>
        </div>
        <p v-if="erros.password" class="field-error">{{ erros.password }}</p>
        <p v-if="erros.confirmPassword" class="field-error">{{ erros.confirmPassword }}</p>
        <p class="field-hint">Mínimo 8 caracteres, com letras e números. Deixe em branco para manter a senha atual.</p>
      </section>

      <details class="card">
        <summary>Permissões por Módulo</summary>
        <p class="field-hint">As permissões são herdadas do perfil selecionado. Você pode personalizar abaixo.</p>
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
      </details>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Usuário</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getUser,
  createUser,
  updateUser,
  type UserRequest,
  type Role,
  type Profile,
  type ModuleName,
  type ActionName,
  type Permission,
} from '@/api/users'

const ROLES: Role[] = ['ADMINISTRATIVE', 'SALES_REP', 'PRODUCTION', 'OUTSOURCED', 'ADMIN']
const ROLE_LABELS: Record<Role, string> = {
  ADMINISTRATIVE: 'Administrativo',
  SALES_REP: 'Representante',
  PRODUCTION: 'Produção',
  OUTSOURCED: 'Terceirizado',
  ADMIN: 'Administrador',
}
const PROFILES: Profile[] = ['ADMIN', 'MANAGER', 'SALES', 'VIEWER']
const PROFILE_LABELS: Record<Profile, string> = {
  ADMIN: 'Admin',
  MANAGER: 'Gerente',
  SALES: 'Vendedor',
  VIEWER: 'Visualizador',
}
const MODULES: ModuleName[] = ['CUSTOMER', 'PRODUCT', 'ORDER', 'USER', 'PURCHASE', 'PAYABLE']
const MODULE_LABELS: Record<ModuleName, string> = {
  CUSTOMER: 'Clientes',
  PRODUCT: 'Produtos',
  ORDER: 'Pedidos',
  USER: 'Usuários',
  PURCHASE: 'Compras',
  PAYABLE: 'Contas a Pagar',
}
const ACTIONS: ActionName[] = ['VIEW', 'CREATE', 'EDIT', 'DELETE']
const ACTION_LABELS: Record<ActionName, string> = {
  VIEW: 'Visualizar',
  CREATE: 'Criar',
  EDIT: 'Editar',
  DELETE: 'Excluir',
}

// Business-judgment default matrix, not confirmed by PRD or prototype -- see the
// plan's Task 6 note. Only used to pre-check the grid on Perfil selection; the
// backend never recomputes this, it persists whatever is checked at submit time.
const DEFAULT_MATRIX: Record<Profile, Permission[]> = {
  ADMIN: [
    ...MODULES.flatMap((m) => ACTIONS.filter((a) =>
      !(m === 'USER' && a === 'DELETE') && !(m === 'PAYABLE' && (a === 'CREATE' || a === 'DELETE')),
    ).map((a) => ({ module: m, action: a }))),
  ],
  MANAGER: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' }, { module: 'PRODUCT', action: 'CREATE' }, { module: 'PRODUCT', action: 'EDIT' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
    { module: 'PURCHASE', action: 'VIEW' }, { module: 'PURCHASE', action: 'CREATE' }, { module: 'PURCHASE', action: 'EDIT' },
    { module: 'PAYABLE', action: 'VIEW' }, { module: 'PAYABLE', action: 'EDIT' },
    { module: 'USER', action: 'VIEW' },
  ],
  SALES: [
    { module: 'CUSTOMER', action: 'VIEW' }, { module: 'CUSTOMER', action: 'CREATE' }, { module: 'CUSTOMER', action: 'EDIT' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' }, { module: 'ORDER', action: 'CREATE' }, { module: 'ORDER', action: 'EDIT' },
  ],
  VIEWER: [
    { module: 'CUSTOMER', action: 'VIEW' },
    { module: 'PRODUCT', action: 'VIEW' },
    { module: 'ORDER', action: 'VIEW' },
    { module: 'PURCHASE', action: 'VIEW' },
    { module: 'PAYABLE', action: 'VIEW' },
  ],
}

const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

interface FormState {
  name: string
  email: string
  phone: string
  role: Role | ''
  profile: Profile | ''
  active: boolean
  password: string
  confirmPassword: string
  permissions: Permission[]
}

function novoFormulario(): FormState {
  return {
    name: '',
    email: '',
    phone: '',
    role: '',
    profile: '',
    active: true,
    password: '',
    confirmPassword: '',
    permissions: [],
  }
}

const form = reactive<FormState>(novoFormulario())
const erros = reactive<{ name?: string; email?: string; role?: string; profile?: string; password?: string; confirmPassword?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

function isChecked(module: ModuleName, action: ActionName) {
  return form.permissions.some((p) => p.module === module && p.action === action)
}

function togglePermission(module: ModuleName, action: ActionName) {
  const index = form.permissions.findIndex((p) => p.module === module && p.action === action)
  if (index >= 0) {
    form.permissions.splice(index, 1)
  } else {
    form.permissions.push({ module, action })
  }
}

function applyDefaultPermissions() {
  if (form.profile) {
    form.permissions = [...DEFAULT_MATRIX[form.profile]]
  }
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const user = await getUser(id)
      form.name = user.name
      form.email = user.email
      form.phone = user.phone ?? ''
      form.role = user.role
      form.profile = user.profile
      form.active = user.active
      form.permissions = [...user.permissions]
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do usuário.'
    }
  }
})

function validar(): boolean {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
  erros.email = form.email.trim() ? undefined : 'Campo obrigatório'
  erros.role = form.role ? undefined : 'Campo obrigatório'
  erros.profile = form.profile ? undefined : 'Campo obrigatório'
  erros.password = !modoEdicao.value && !form.password ? 'Campo obrigatório' : undefined
  if (form.password) {
    if (form.password !== form.confirmPassword) {
      erros.confirmPassword = 'As senhas não coincidem'
    } else if (!PASSWORD_PATTERN.test(form.password)) {
      erros.confirmPassword = 'Mínimo 8 caracteres, com letras e números'
    } else {
      erros.confirmPassword = undefined
    }
  } else {
    erros.confirmPassword = undefined
  }
  return !erros.name && !erros.email && !erros.role && !erros.profile && !erros.password && !erros.confirmPassword
}

function paraPayload(): UserRequest {
  return {
    name: form.name,
    email: form.email,
    phone: form.phone,
    role: form.role as Role,
    profile: form.profile as Profile,
    active: form.active,
    password: form.password,
    confirmPassword: form.confirmPassword,
    permissions: form.permissions,
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
      await updateUser(id, payload)
    } else {
      await createUser(payload)
    }
    router.push({ name: 'usuarios' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um usuário cadastrado com este e-mail.'
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
  router.push({ name: 'usuarios' })
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

details.card summary {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  cursor: pointer;
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
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

.field-hint {
  font-size: 11px;
  color: var(--pm-text-mid);
  margin: 4px 0 0;
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

.tabela-permissoes {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  margin-top: 10px;
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
