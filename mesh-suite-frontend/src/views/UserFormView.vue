<template>
  <AppShell :title="modoEdicao ? 'Editar Usuário' : 'Novo Usuário'">
    <PageHeader :title="modoEdicao ? 'Edição de Usuário' : 'Cadastro de Usuário'" />

    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados do Usuário</h2>
        <div class="grid grid-2">
          <TextField v-model="form.name" label="Nome completo" required :error="erros.name" placeholder="Ex: Ana Santos" test-id="name" @blur="validarNome" />
          <TextField v-model="form.email" label="E-mail" required :error="erros.email" placeholder="usuario@empresa.com" test-id="email" @blur="validarEmail" />
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
            <label class="field-label">Perfil de Acesso</label>
            <select v-model="form.permissionProfileId" data-test="profile" @change="applyProfilePermissions">
              <option value="">Selecione...</option>
              <option v-for="p in perfis" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
          </div>
          <div class="status-bloco">
            <label class="status-label">Status</label>
            <SegmentedControl
              :model-value="form.active ? 'ATIVO' : 'INATIVO'"
              :options="statusOptions"
              variant="status"
              test-id="status"
              @update:model-value="(v) => (form.active = v === 'ATIVO')"
            />
          </div>
        </div>
      </section>

      <section v-if="!modoEdicao" class="card">
        <h2>Acesso ao Sistema</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Senha *</label>
            <input v-model="form.password" type="password" data-test="password" />
          </div>
          <div>
            <label class="field-label">Confirmar Senha *</label>
            <input v-model="form.confirmPassword" type="password" data-test="confirm-password" />
          </div>
        </div>
        <p v-if="erros.password" class="field-error">{{ erros.password }}</p>
        <p v-if="erros.confirmPassword" class="field-error">{{ erros.confirmPassword }}</p>
        <p class="field-hint">Mínimo 8 caracteres, com letras e números.</p>
      </section>

      <CollapsibleSection title="Permissões por Módulo">
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
      </CollapsibleSection>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <FormActions :saving="salvando" save-label="Salvar Usuário" @cancel="cancelar" />
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import TextField from '@/components/TextField.vue'
import SegmentedControl, { type SegmentedOption } from '@/components/SegmentedControl.vue'
import CollapsibleSection from '@/components/CollapsibleSection.vue'
import FormActions from '@/components/FormActions.vue'
import {
  getUser,
  createUser,
  updateUser,
  type UserRequest,
  type Role,
  type ModuleName,
  type ActionName,
  type Permission,
} from '@/api/users'
import { listPermissionProfiles, getPermissionProfile, type PermissionProfileSummary } from '@/api/permissionProfiles'

const ROLES: Role[] = ['ADMINISTRATIVE', 'SALES_REP', 'PRODUCTION', 'OUTSOURCED', 'ADMIN']
const ROLE_LABELS: Record<Role, string> = {
  ADMINISTRATIVE: 'Administrativo',
  SALES_REP: 'Representante',
  PRODUCTION: 'Produção',
  OUTSOURCED: 'Terceirizado',
  ADMIN: 'Administrador',
}
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

const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

const statusOptions: SegmentedOption[] = [
  { value: 'ATIVO', label: 'Ativo' },
  { value: 'INATIVO', label: 'Inativo' },
]

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

interface FormState {
  name: string
  email: string
  phone: string
  role: Role | ''
  permissionProfileId: string
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
    permissionProfileId: '',
    active: true,
    password: '',
    confirmPassword: '',
    permissions: [],
  }
}

const form = reactive<FormState>(novoFormulario())
const perfis = ref<PermissionProfileSummary[]>([])
const erros = reactive<{ name?: string; email?: string; role?: string; password?: string; confirmPassword?: string }>({})
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

async function applyProfilePermissions() {
  erroGeral.value = ''
  if (!form.permissionProfileId) {
    return
  }
  try {
    const perfil = await getPermissionProfile(form.permissionProfileId)
    form.permissions = [...perfil.grants]
  } catch {
    erroGeral.value = 'Não foi possível carregar as permissões padrão deste perfil.'
  }
}

onMounted(async () => {
  try {
    const pagina = await listPermissionProfiles({ size: 100 })
    perfis.value = pagina.content
  } catch {
    perfis.value = []
    erroGeral.value = 'Não foi possível carregar os perfis de permissão disponíveis.'
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const user = await getUser(id)
      form.name = user.name
      form.email = user.email
      form.phone = user.phone ?? ''
      form.role = user.role
      form.permissionProfileId = user.permissionProfileId ?? ''
      form.active = user.active
      form.permissions = [...user.permissions]
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do usuário.'
    }
  }
})

function validarNome() {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
}

function validarEmail() {
  erros.email = form.email.trim() ? undefined : 'Campo obrigatório'
}

function validar(): boolean {
  validarNome()
  validarEmail()
  erros.role = form.role ? undefined : 'Campo obrigatório'
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
  return !erros.name && !erros.email && !erros.role && !erros.password && !erros.confirmPassword
}

function paraPayload(): UserRequest {
  return {
    name: form.name,
    email: form.email,
    phone: form.phone,
    role: form.role as Role,
    active: form.active,
    password: form.password,
    confirmPassword: form.confirmPassword,
    permissions: form.permissions,
    permissionProfileId: form.permissionProfileId || null,
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

.grid {
  display: grid;
  gap: 0 14px;
  margin-bottom: 10px;
}

.grid-2 {
  grid-template-columns: 1fr 1fr;
}

.status-bloco {
  margin-top: 2px;
}

.status-label {
  display: block;
  font-size: 12px;
  color: var(--pm-text-muted);
  margin-bottom: 5px;
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
</style>
