<template>
  <AppShell title="Pedidos">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº, cliente ou vendedor..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="DIGITADO">Digitado</option>
        <option value="EM_PREPARO">Em Preparo</option>
        <option value="FATURADO">Faturado</option>
      </select>
      <button type="button" class="btn-primary" data-test="novo-pedido" @click="novoPedido">+ Novo Pedido</button>
    </div>

    <div v-if="resumo" class="resumo">
      <span class="resumo-item">{{ resumo.total }} Total</span>
      <span class="resumo-item resumo-digitado">{{ resumo.digitados }} Digitados</span>
      <span class="resumo-item resumo-em-preparo">{{ resumo.emPreparo }} Em Preparo</span>
      <span class="resumo-item resumo-faturado">{{ resumo.faturados }} Faturados</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nº</th>
            <th>Cliente</th>
            <th>Vendedor</th>
            <th>Data</th>
            <th>Total</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="pedido in pagina.content" :key="pedido.id">
            <td>{{ pedido.numero }}</td>
            <td>{{ pedido.clienteNome }}</td>
            <td>{{ pedido.vendedorNome }}</td>
            <td>{{ formatarData(pedido.dataPedido) }}</td>
            <td>{{ formatarPreco(pedido.total) }}</td>
            <td><span class="badge" :class="`badge-${pedido.status}`">{{ statusLabel(pedido.status) }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(pedido.id, $event)"
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
        v-if="pedidoAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarPedido(pedidoAcoesAtual.id)">Editar</div>
        <div v-if="rotuloAvancar(pedidoAcoesAtual.status)" data-test="acao-avancar" @click="avancar(pedidoAcoesAtual)">
          {{ rotuloAvancar(pedidoAcoesAtual.status) }}
        </div>
        <div class="acao-excluir" data-test="acao-excluir" @click="excluir(pedidoAcoesAtual)">Excluir</div>
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
  listarPedidos,
  buscarResumoPedidos,
  avancarStatusPedido,
  excluirPedido,
  type PedidoSummary,
  type PedidoResumo,
  type Page as ApiPage,
  type StatusPedido,
} from '@/api/pedidos'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<PedidoSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<PedidoResumo | null>(null)
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const pedidoAcoesAtual = computed(() =>
  pagina.value.content.find((p) => p.id === acoesAbertas.value) ?? null,
)

const PROXIMO_STATUS: Record<StatusPedido, StatusPedido | null> = {
  DIGITADO: 'EM_PREPARO',
  EM_PREPARO: 'FATURADO',
  FATURADO: null,
}

const STATUS_LABEL: Record<StatusPedido, string> = {
  DIGITADO: 'Digitado',
  EM_PREPARO: 'Em Preparo',
  FATURADO: 'Faturado',
}

function statusLabel(status: StatusPedido) {
  return STATUS_LABEL[status]
}

function rotuloAvancar(status: StatusPedido) {
  const proximo = PROXIMO_STATUS[status]
  return proximo ? `Avançar para ${statusLabel(proximo)}` : null
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listarPedidos({
      busca: filtros.busca || undefined,
      status: (filtros.status || undefined) as StatusPedido | undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de pedidos.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await buscarResumoPedidos()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de pedidos.'
  }
}

function novoPedido() {
  router.push({ name: 'pedidos-novo' })
}

function editarPedido(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'pedidos-editar', params: { id } })
}

function toggleAcoes(id: string, event: MouseEvent) {
  if (acoesAbertas.value === id) {
    acoesAbertas.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  posicaoDropdown.value = {
    top: `${rect.bottom + 4}px`,
    left: `${rect.right - 160}px`,
  }
  acoesAbertas.value = id
}

async function avancar(pedido: PedidoSummary) {
  acoesAbertas.value = null
  const proximo = PROXIMO_STATUS[pedido.status]
  if (!proximo) {
    return
  }
  erro.value = ''
  try {
    await avancarStatusPedido(pedido.id, proximo)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível avançar o status do pedido.'
  }
}

async function excluir(pedido: PedidoSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir o pedido nº ${pedido.numero}?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirPedido(pedido.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir o pedido.'
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

.resumo-digitado {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.resumo-em-preparo {
  background: var(--pm-warning-bg, var(--pm-bg));
  color: var(--pm-warning, var(--pm-text-mid));
}

.resumo-faturado {
  background: var(--pm-success-bg);
  color: var(--pm-success);
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

.badge-DIGITADO {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.badge-EM_PREPARO {
  background: var(--pm-warning-bg, var(--pm-bg));
  color: var(--pm-warning, var(--pm-text-mid));
}

.badge-FATURADO {
  background: var(--pm-success-bg);
  color: var(--pm-success);
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
  min-width: 160px;
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
