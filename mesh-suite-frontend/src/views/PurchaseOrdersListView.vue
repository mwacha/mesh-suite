<template>
  <AppShell title="Ordens de Compra">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº, fornecedor ou comprador..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="OPEN">Aberta</option>
        <option value="RECEIVED">Recebida</option>
        <option value="CANCELLED">Cancelada</option>
      </select>
      <button type="button" class="btn-primary" data-test="nova-ordem" @click="novaOrdem">+ Nova Ordem de Compra</button>
    </div>

    <div v-if="resumo" class="resumo">
      <span class="resumo-item">{{ resumo.total }} Total</span>
      <span class="resumo-item resumo-open">{{ resumo.open }} Abertas</span>
      <span class="resumo-item resumo-received">{{ resumo.received }} Recebidas</span>
      <span class="resumo-item resumo-cancelled">{{ resumo.cancelled }} Canceladas</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nº</th>
            <th>Fornecedor</th>
            <th>Comprador</th>
            <th>Data</th>
            <th>Total</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ordem in pagina.content" :key="ordem.id">
            <td>{{ ordem.number }}</td>
            <td>{{ ordem.supplierName }}</td>
            <td>{{ ordem.buyerName }}</td>
            <td>{{ formatarData(ordem.orderDate) }}</td>
            <td>{{ formatarPreco(ordem.total) }}</td>
            <td><span class="badge" :class="`badge-${ordem.status}`">{{ statusLabel(ordem.status) }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(ordem.id, $event)"
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
        v-if="ordemAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarOrdem(ordemAcoesAtual.id)">Editar</div>
        <div v-if="ordemAcoesAtual.status === 'OPEN'" data-test="acao-receber" @click="marcarComoRecebida(ordemAcoesAtual)">
          Marcar como Recebida
        </div>
        <div v-if="ordemAcoesAtual.status === 'OPEN'" data-test="acao-cancelar" @click="cancelarOrdem(ordemAcoesAtual)">
          Cancelar
        </div>
        <div class="acao-excluir" data-test="acao-excluir" @click="excluir(ordemAcoesAtual)">Excluir</div>
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
  listPurchaseOrders,
  getPurchaseOrderCounts,
  updatePurchaseOrderStatus,
  deletePurchaseOrder,
  type PurchaseOrderSummary,
  type PurchaseOrderCounts,
  type Page as ApiPage,
  type PurchaseOrderStatus,
} from '@/api/purchaseOrders'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<PurchaseOrderSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<PurchaseOrderCounts | null>(null)
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const ordemAcoesAtual = computed(() =>
  pagina.value.content.find((o) => o.id === acoesAbertas.value) ?? null,
)

const STATUS_LABEL: Record<PurchaseOrderStatus, string> = {
  OPEN: 'Aberta',
  RECEIVED: 'Recebida',
  CANCELLED: 'Cancelada',
}

function statusLabel(status: PurchaseOrderStatus) {
  return STATUS_LABEL[status]
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
    pagina.value = await listPurchaseOrders({
      search: filtros.busca || undefined,
      status: (filtros.status || undefined) as PurchaseOrderStatus | undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de ordens de compra.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await getPurchaseOrderCounts()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de ordens de compra.'
  }
}

function novaOrdem() {
  router.push({ name: 'compras-novo' })
}

function editarOrdem(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'compras-editar', params: { id } })
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

async function marcarComoRecebida(ordem: PurchaseOrderSummary) {
  acoesAbertas.value = null
  erro.value = ''
  try {
    await updatePurchaseOrderStatus(ordem.id, 'RECEIVED')
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status da ordem de compra.'
  }
}

async function cancelarOrdem(ordem: PurchaseOrderSummary) {
  acoesAbertas.value = null
  erro.value = ''
  try {
    await updatePurchaseOrderStatus(ordem.id, 'CANCELLED')
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status da ordem de compra.'
  }
}

async function excluir(ordem: PurchaseOrderSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir a ordem de compra nº ${ordem.number}?`)) {
    return
  }
  erro.value = ''
  try {
    await deletePurchaseOrder(ordem.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir a ordem de compra.'
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

.resumo-open {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.resumo-received {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.resumo-cancelled {
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

.badge-OPEN {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.badge-RECEIVED {
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.badge-CANCELLED {
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
