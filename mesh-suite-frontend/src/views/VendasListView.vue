<template>
  <AppShell title="Vendas">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar por nº ou cliente..."
        data-test="busca"
        @input="carregar(0)"
      />
    </div>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Vendas</span>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col">Nº</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-cliente" @click="toggleSort('clienteNome')">
            Cliente
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'clienteNome' }">{{ sortIcon('clienteNome') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-data" @click="toggleSort('dataEmissao')">
            Data de Emissão
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'dataEmissao' }">{{ sortIcon('dataEmissao') }}</span>
          </div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-total" @click="toggleSort('total')">
            Total
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'total' }">{{ sortIcon('total') }}</span>
          </div>
        </div>

        <div v-for="venda in pagina.content" :key="venda.id" class="table-grid-row" :data-test="`row-${venda.id}`">
          <div class="table-grid-cell">{{ venda.numero }}</div>
          <div class="table-grid-cell table-grid-cell-nome">{{ venda.clienteNome }}</div>
          <div class="table-grid-cell">{{ formatarData(venda.dataEmissao) }}</div>
          <div class="table-grid-cell">{{ formatarPreco(venda.total) }}</div>
        </div>
      </div>
      <p v-if="!pagina.content.length" class="empty-state">Nenhuma venda para exibir.</p>
    </section>

    <Pagination
      :number="pagina.number"
      :total-pages="pagina.totalPages"
      :total-elements="pagina.totalElements"
      :size="pagina.size"
      @update:page="carregar"
      @update:size="onSizeChange"
    />
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import Pagination from '@/components/Pagination.vue'
import { listarVendas, type VendaSummary, type Page as ApiPage } from '@/api/vendas'

const filtros = reactive({ busca: '' })
const pagina = ref<ApiPage<VendaSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const sortField = ref<'clienteNome' | 'dataEmissao' | 'total' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const erro = ref('')

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(data: string) {
  const [ano, mes, dia] = data.split('-')
  return `${dia}/${mes}/${ano}`
}

function sortIcon(field: 'clienteNome' | 'dataEmissao' | 'total') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'clienteNome' | 'dataEmissao' | 'total') {
  if (sortField.value === field) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortDir.value = 'asc'
  }
  carregar(0)
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listarVendas({
      busca: filtros.busca || undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de vendas.'
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
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

.table-card {
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 12px;
}

.table-card-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--pm-border-light);
  font-family: var(--pm-font);
}

.table-card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.table-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 70px 1fr 150px 110px;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.table-grid-header {
  background: var(--pm-bg);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  padding: 12px;
}

.table-grid-col-sortable {
  cursor: pointer;
  white-space: nowrap;
}

.table-grid-sort-icon {
  font-size: 9px;
  color: var(--pm-text-muted);
  margin-left: 2px;
}

.table-grid-sort-icon-active {
  color: var(--pm-accent);
}

.table-grid-row {
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.table-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  padding: 16px;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}
</style>
