<template>
  <AppShell title="Contas a Pagar">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="OPEN">Em Aberto</option>
        <option value="PAID">Paga</option>
      </select>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Nº</th>
            <th>Parcela</th>
            <th>Fornecedor</th>
            <th>Vencimento</th>
            <th>Valor</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="titulo in pagina.content" :key="titulo.id">
            <td>{{ titulo.number }}</td>
            <td>{{ titulo.installmentNumber }}/{{ titulo.totalInstallments }}</td>
            <td>{{ titulo.supplierName }}</td>
            <td>{{ formatarData(titulo.dueDate) }}</td>
            <td>{{ formatarPreco(titulo.amount) }}</td>
            <td><span class="badge" :class="`badge-${titulo.status}`">{{ statusLabel(titulo.status) }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(titulo.id, $event)"
              >
                Ações
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma conta a pagar para exibir.</p>
    </section>

    <Teleport to="body">
      <div
        v-if="tituloAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div v-if="tituloAcoesAtual.status === 'OPEN'" data-test="acao-baixa" @click="darBaixa(tituloAcoesAtual)">
          Dar Baixa
        </div>
        <div v-if="tituloAcoesAtual.status === 'PAID'" data-test="acao-reverter" @click="reverterBaixa(tituloAcoesAtual)">
          Reverter Baixa
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
import AppShell from '@/components/AppShell.vue'
import {
  listAccountsPayable,
  updateAccountsPayableStatus,
  type AccountsPayable,
  type AccountsPayableStatus,
  type Page as ApiPage,
} from '@/api/accountsPayable'

const filtros = reactive({ status: '' })
const pagina = ref<ApiPage<AccountsPayable>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const tituloAcoesAtual = computed(() =>
  pagina.value.content.find((t) => t.id === acoesAbertas.value) ?? null,
)

const STATUS_LABEL: Record<AccountsPayableStatus, string> = {
  OPEN: 'Em Aberto',
  PAID: 'Paga',
}

function statusLabel(status: AccountsPayableStatus) {
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
    pagina.value = await listAccountsPayable({
      status: (filtros.status || undefined) as AccountsPayableStatus | undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de contas a pagar.'
  }
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

async function darBaixa(titulo: AccountsPayable) {
  acoesAbertas.value = null
  erro.value = ''
  try {
    await updateAccountsPayableStatus(titulo.id, 'PAID')
    await carregar(pagina.value.number)
  } catch {
    erro.value = 'Não foi possível dar baixa na conta a pagar.'
  }
}

async function reverterBaixa(titulo: AccountsPayable) {
  acoesAbertas.value = null
  erro.value = ''
  try {
    await updateAccountsPayableStatus(titulo.id, 'OPEN')
    await carregar(pagina.value.number)
  } catch {
    erro.value = 'Não foi possível reverter a baixa da conta a pagar.'
  }
}

onMounted(() => {
  carregar(0)
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

.toolbar select {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
  background: var(--pm-white);
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

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}

.badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.badge-OPEN {
  background: var(--pm-warning-bg, var(--pm-bg));
  color: var(--pm-warning, var(--pm-text-mid));
}

.badge-PAID {
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
