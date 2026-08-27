<template>
  <AppShell :title="modoEdicao ? 'Editar Perfil de Permissão' : 'Novo Perfil de Permissão'">
    <PageHeader :title="modoEdicao ? `Editar Perfil · ${form.name}` : 'Novo Perfil de Permissão'" />

    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados do Perfil</h2>
        <TextField v-model="form.name" label="Nome do Perfil" required :error="erros.name" placeholder="Ex: Supervisor de Vendas" test-id="nome" @blur="validarNome" />
        <div>
          <label class="field-label">Descrição</label>
          <textarea v-model="form.description" data-test="descricao" placeholder="Descreva as responsabilidades deste perfil..." rows="2"></textarea>
        </div>
      </section>

      <section class="card table-card">
        <div class="table-card-header">
          <h2>Permissões por Módulo</h2>
          <p class="field-hint">Selecione quais ações este perfil pode executar em cada módulo</p>
        </div>
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
                  @change="toggleGrant(m, a)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <FormActions :saving="salvando" save-label="Salvar Perfil" @cancel="cancelar" />
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import TextField from '@/components/TextField.vue'
import FormActions from '@/components/FormActions.vue'
import {
  getPermissionProfile,
  createPermissionProfile,
  updatePermissionProfile,
  type PermissionProfileRequest,
  type ModuleName,
  type ActionName,
} from '@/api/permissionProfiles'

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

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): PermissionProfileRequest {
  return { name: '', description: '', grants: [] }
}

const form = reactive<PermissionProfileRequest>(novoFormulario())
const erros = reactive<{ name?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

function isChecked(module: ModuleName, action: ActionName) {
  return form.grants.some((g) => g.module === module && g.action === action)
}

function toggleGrant(module: ModuleName, action: ActionName) {
  const index = form.grants.findIndex((g) => g.module === module && g.action === action)
  if (index >= 0) {
    form.grants.splice(index, 1)
  } else {
    form.grants.push({ module, action })
  }
}

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const perfil = await getPermissionProfile(id)
      form.name = perfil.name
      form.description = perfil.description ?? ''
      form.grants = [...perfil.grants]
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do perfil.'
    }
  }
})

function validarNome() {
  erros.name = form.name.trim() ? undefined : 'Campo obrigatório'
}

function validar(): boolean {
  validarNome()
  return !erros.name
}

async function salvar() {
  erroGeral.value = ''
  if (!validar()) {
    return
  }
  salvando.value = true
  try {
    const id = route.params.id
    const payload: PermissionProfileRequest = { name: form.name, description: form.description, grants: form.grants }
    if (typeof id === 'string') {
      await updatePermissionProfile(id, payload)
    } else {
      await createPermissionProfile(payload)
    }
    router.push({ name: 'permissoes' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um perfil de permissão cadastrado com este nome.'
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
  router.push({ name: 'permissoes' })
}
</script>

<style scoped>
.form { display: flex; flex-direction: column; gap: 12px; font-family: var(--pm-font); }
.card { background: var(--pm-white); border: 1px solid var(--pm-border-light); border-radius: 12px; padding: 16px; }
.card h2 { font-size: 14px; font-weight: 700; color: var(--pm-text-dark); margin: 0 0 12px; }
.table-card { padding: 0; overflow: hidden; }
.table-card-header { padding: 14px 16px; border-bottom: 2px solid var(--pm-border-light); }
.table-card-header h2 { margin: 0; }
.field-label { display: block; font-size: 12px; color: var(--pm-text-mid); margin-bottom: 4px; }
.field-hint { font-size: 11px; color: var(--pm-text-muted); margin: 2px 0 0; }
textarea {
  width: 100%; box-sizing: border-box; background: var(--pm-white); border: 1px solid var(--pm-border-light);
  border-radius: 8px; padding: 8px 10px; color: var(--pm-text-dark); font-size: 13px; font-family: var(--pm-font);
  resize: vertical;
}
.error-geral { color: var(--pm-error); font-size: 14px; }
.tabela-permissoes { width: 100%; border-collapse: collapse; font-size: 12px; }
.tabela-permissoes th, .tabela-permissoes td { text-align: center; padding: 8px 10px; border-top: 1px solid var(--pm-border-light); }
.tabela-permissoes thead tr { background: var(--pm-bg); }
.tabela-permissoes td:first-child { text-align: left; font-weight: 600; color: var(--pm-text-dark); }
</style>
