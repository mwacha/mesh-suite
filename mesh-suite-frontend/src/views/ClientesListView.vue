<template>
  <AppShell title="Clientes">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar cliente por nome..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="ATIVO">Ativo</option>
        <option value="EM_RISCO">Em Risco</option>
        <option value="BLOQUEADO">Bloqueado</option>
      </select>
      <select v-model="filtros.tipoDocumento" @change="carregar(0)">
        <option value="">Tipo de Documento</option>
        <option value="JURIDICA">CNPJ</option>
        <option value="FISICA">CPF</option>
      </select>
      <input v-model="filtros.uf" placeholder="UF" @change="carregar(0)" />
      <input v-model="filtros.cidade" placeholder="Cidade" @change="carregar(0)" />
      <button type="button" class="btn-primary" data-test="novo-cliente" @click="novoCliente">+ Novo Cliente</button>
    </div>

    <div v-if="resumo" class="resumo">
      <span class="resumo-item">{{ resumo.total }} Total</span>
      <span class="resumo-item resumo-ativo">{{ resumo.ativos }} Ativos</span>
      <span class="resumo-item resumo-risco">{{ resumo.emRisco }} Em Risco</span>
      <span class="resumo-item resumo-bloqueado">{{ resumo.bloqueados }} Bloqueados</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nome / Razão Social</th>
            <th>Cidade</th>
            <th>Telefone</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="parceiro in pagina.content" :key="parceiro.id">
            <td>{{ parceiro.nomeFantasia }}</td>
            <td>{{ parceiro.cidade }}</td>
            <td>{{ parceiro.whatsapp }}</td>
            <td><span class="badge" :class="`badge-${parceiro.status}`">{{ statusLabel(parceiro.status) }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(parceiro.id, $event)"
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
        v-if="parceiroAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-ver" @click="abrirCliente(parceiroAcoesAtual.id)">Ver</div>
        <div @click="editarCliente(parceiroAcoesAtual.id)">Editar</div>
        <div @click="alternarStatus(parceiroAcoesAtual)">
          {{ parceiroAcoesAtual.status === 'BLOQUEADO' ? 'Ativar' : 'Bloquear' }}
        </div>
        <div class="acao-excluir" @click="excluir(parceiroAcoesAtual)">Excluir</div>
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
  listarParceiros,
  buscarResumoParceiros,
  atualizarStatusParceiro,
  excluirParceiro,
  type ParceiroSummary,
  type ParceiroResumo,
  type Page as ApiPage,
  type StatusParceiro,
  type TipoPessoa,
} from '@/api/parceiros'

const router = useRouter()

const filtros = reactive({ busca: '', status: '', tipoDocumento: '', uf: '', cidade: '' })
const pagina = ref<ApiPage<ParceiroSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<ParceiroResumo | null>(null)
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const parceiroAcoesAtual = computed(() =>
  pagina.value.content.find((p) => p.id === acoesAbertas.value) ?? null,
)

function statusLabel(status: StatusParceiro) {
  return { ATIVO: 'Ativo', EM_RISCO: 'Em Risco', BLOQUEADO: 'Bloqueado' }[status]
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listarParceiros({
      busca: filtros.busca || undefined,
      status: (filtros.status || undefined) as StatusParceiro | undefined,
      tipoDocumento: (filtros.tipoDocumento || undefined) as TipoPessoa | undefined,
      uf: filtros.uf || undefined,
      cidade: filtros.cidade || undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de clientes.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await buscarResumoParceiros()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de clientes.'
  }
}

function novoCliente() {
  router.push({ name: 'clientes-novo' })
}

function abrirCliente(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'clientes-detalhe', params: { id } })
}

function editarCliente(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'clientes-editar', params: { id } })
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

async function alternarStatus(parceiro: ParceiroSummary) {
  acoesAbertas.value = null
  erro.value = ''
  const novoStatus = parceiro.status === 'BLOQUEADO' ? 'ATIVO' : 'BLOQUEADO'
  try {
    await atualizarStatusParceiro(parceiro.id, novoStatus)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status.'
  }
}

async function excluir(parceiro: ParceiroSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir o cliente "${parceiro.nomeFantasia}"?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirParceiro(parceiro.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir o cliente.'
  }
}

onMounted(() => {
  carregar(0)
  carregarResumo()
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

.resumo-risco {
  background: var(--pm-warning-bg);
  color: var(--pm-warning);
}

.resumo-bloqueado {
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

.badge-EM_RISCO {
  background: var(--pm-warning-bg);
  color: var(--pm-warning);
}

.badge-BLOQUEADO {
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

.acao-excluir {
  color: var(--pm-error);
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
