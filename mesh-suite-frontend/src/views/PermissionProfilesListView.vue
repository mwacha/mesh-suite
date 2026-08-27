<template>
  <div class="perfis-permissao">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar perfil por nome..."
        data-test="busca"
        @input="carregar(0)"
      />
      <button type="button" class="btn-primary" data-test="novo-perfil" @click="novoPerfil">+ Novo Perfil</button>
    </div>

    <ListCard title="Perfis de Permissão" :stats="stats">
      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Perfil</div>
          <div class="table-grid-col">Descrição</div>
          <div class="table-grid-col">Usuários</div>
          <div class="table-grid-col">Acesso</div>
          <div class="table-grid-col"></div>
        </div>

        <div v-for="perfil in pagina.content" :key="perfil.id" class="table-grid-row">
          <div class="table-grid-cell table-grid-cell-perfil">
            <span class="perfil-icone">🔒</span>
            <span>{{ perfil.name }}</span>
          </div>
          <div class="table-grid-cell">{{ perfil.description || '—' }}</div>
          <div class="table-grid-cell">{{ perfil.userCount }}</div>
          <div class="table-grid-cell">{{ perfil.moduleCount }} de 9 módulos</div>
          <div class="table-grid-cell table-grid-cell-acoes">
            <ActionsMenu :items="acoesPara(perfil)" test-id="btn-acoes" trigger-label="Ações" />
          </div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhum perfil de permissão para exibir.</p>
    </ListCard>

    <p class="dica">💡 Clique em <strong>Editar</strong> para configurar as ações liberadas por módulo em cada perfil.</p>

    <Pagination
      :number="pagina.number"
      :total-pages="pagina.totalPages"
      :total-elements="pagina.totalElements"
      :size="pagina.size"
      @update:page="carregar"
      @update:size="onSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import ListCard, { type ListCardStat } from '@/components/ListCard.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import {
  listPermissionProfiles,
  deletePermissionProfile,
  type PermissionProfileSummary,
  type Page as ApiPage,
} from '@/api/permissionProfiles'

const router = useRouter()

const filtros = reactive({ busca: '' })
const pagina = ref<ApiPage<PermissionProfileSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const erro = ref('')

const stats = computed<ListCardStat[]>(() => [
  { value: pagina.value.totalElements, label: 'perfis', color: 'blue' },
])

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listPermissionProfiles({
      busca: filtros.busca || undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de perfis de permissão.'
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

function novoPerfil() {
  router.push({ name: 'permissoes-perfis-novo' })
}

function editarPerfil(id: string) {
  router.push({ name: 'permissoes-perfis-editar', params: { id } })
}

async function excluir(perfil: PermissionProfileSummary) {
  if (!confirm(`Excluir o perfil "${perfil.name}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deletePermissionProfile(perfil.id)
    await carregar(pagina.value.number)
  } catch (err: any) {
    erro.value = err?.response?.data?.mensagem ?? 'Não foi possível excluir o perfil de permissão.'
  }
}

function acoesPara(perfil: PermissionProfileSummary): ActionsMenuItem[] {
  const itens: ActionsMenuItem[] = [
    { label: 'Editar', action: () => editarPerfil(perfil.id), testId: 'acao-editar' },
  ]
  if (!perfil.isSystem) {
    itens.push({ label: 'Excluir', action: () => excluir(perfil), danger: true, testId: 'acao-excluir' })
  }
  return itens
}

onMounted(() => {
  carregar(0)
})
</script>

<style scoped>
.perfis-permissao {
  font-family: var(--pm-font);
}

.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.busca {
  flex: 1;
}

.toolbar input {
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
  font-family: var(--pm-font);
}

.table-grid {
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 160px 1fr 90px 150px 96px;
  gap: 8px;
  align-items: center;
  padding: 8px 14px;
}

.table-grid-header {
  background: var(--pm-bg);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--pm-text-mid);
}

.table-grid-row {
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.table-grid-cell-perfil {
  display: flex;
  align-items: center;
  gap: 7px;
  font-weight: 600;
}

.perfil-icone {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: var(--pm-accent-bg);
  border: 1px solid var(--pm-border-light);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.table-grid-cell-acoes {
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}

.dica {
  font-size: 12px;
  color: var(--pm-text-muted);
  margin: 0 0 12px;
}
</style>
