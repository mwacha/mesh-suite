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

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Perfil</th>
            <th>Descrição</th>
            <th>Módulos</th>
            <th>Usuários</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="perfil in pagina.content" :key="perfil.id">
            <td>{{ perfil.name }}</td>
            <td>{{ perfil.description || '—' }}</td>
            <td>{{ perfil.moduleCount }} de 9 módulos</td>
            <td>{{ perfil.userCount }}</td>
            <td class="acoes">
              <button type="button" class="btn-acoes" data-test="btn-acoes" @click="toggleAcoes(perfil.id, $event)">
                Ações
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!pagina.content.length" class="empty-state">Nenhum perfil de permissão para exibir.</p>
    </section>

    <Teleport to="body">
      <div v-if="perfilAcoesAtual" class="dropdown-acoes" :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }">
        <div data-test="acao-editar" @click="editarPerfil(perfilAcoesAtual.id)">Editar</div>
        <div data-test="acao-excluir" class="acao-excluir" @click="excluir(perfilAcoesAtual)">Excluir</div>
      </div>
    </Teleport>

    <div class="paginacao">
      <button type="button" :disabled="pagina.number === 0" @click="carregar(pagina.number - 1)">‹</button>
      <span>Página {{ pagina.number + 1 }} de {{ Math.max(pagina.totalPages, 1) }}</span>
      <button type="button" :disabled="pagina.number + 1 >= pagina.totalPages" @click="carregar(pagina.number + 1)">›</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  listPermissionProfiles,
  deletePermissionProfile,
  type PermissionProfileSummary,
  type Page as ApiPage,
} from '@/api/permissionProfiles'

const router = useRouter()

const filtros = reactive({ busca: '' })
const pagina = ref<ApiPage<PermissionProfileSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const perfilAcoesAtual = computed(() =>
  pagina.value.content.find((p) => p.id === acoesAbertas.value) ?? null,
)

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

function novoPerfil() {
  router.push({ name: 'permissoes-perfis-novo' })
}

function editarPerfil(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'permissoes-perfis-editar', params: { id } })
}

function toggleAcoes(id: string, event: MouseEvent) {
  if (acoesAbertas.value === id) {
    acoesAbertas.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  posicaoDropdown.value = { top: `${rect.bottom + 4}px`, left: `${rect.right - 120}px` }
  acoesAbertas.value = id
}

async function excluir(perfil: PermissionProfileSummary) {
  acoesAbertas.value = null
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

onMounted(() => {
  carregar(0)
})
</script>

<style scoped>
.error-geral { color: var(--pm-error); font-size: 14px; margin: 0 0 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; font-family: var(--pm-font); }
.busca { flex: 1; }
.toolbar input {
  border: 1px solid var(--pm-border-light); border-radius: 8px; padding: 8px 10px;
  font-size: 13px; font-family: var(--pm-font); color: var(--pm-text-dark); background: var(--pm-white);
}
.btn-primary {
  background: var(--pm-accent); color: var(--pm-white); border: none; border-radius: 8px;
  padding: 8px 16px; font-size: 13px; font-weight: 600; cursor: pointer; white-space: nowrap;
}
.card { background: var(--pm-white); border: 1px solid var(--pm-border-light); border-radius: 12px; overflow: hidden; margin-bottom: 12px; }
.tabela { width: 100%; border-collapse: collapse; font-size: 13px; font-family: var(--pm-font); }
.tabela th {
  text-align: left; font-size: 11px; font-weight: 600; text-transform: uppercase;
  color: var(--pm-text-mid); background: var(--pm-bg); padding: 8px 12px;
}
.tabela td { padding: 8px 12px; border-top: 1px solid var(--pm-border-light); color: var(--pm-text-dark); }
.empty-state { padding: 16px; color: var(--pm-text-mid); font-size: 13px; margin: 0; }
.btn-acoes { border: 1px solid var(--pm-border-light); background: var(--pm-white); border-radius: 6px; padding: 4px 10px; font-size: 12px; cursor: pointer; }
.dropdown-acoes {
  position: fixed; background: var(--pm-white); border: 1px solid var(--pm-border-light); border-radius: 6px;
  min-width: 120px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08), 0 8px 28px rgba(0, 0, 0, 0.12); z-index: 10;
}
.dropdown-acoes div { padding: 8px 12px; font-size: 12px; cursor: pointer; color: var(--pm-text-dark); }
.acao-excluir { color: var(--pm-error); }
.paginacao { display: flex; align-items: center; justify-content: center; gap: 12px; font-size: 13px; color: var(--pm-text-mid); }
.paginacao button { border: 1px solid var(--pm-border-light); background: var(--pm-white); border-radius: 6px; width: 28px; height: 28px; cursor: pointer; }
.paginacao button:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
