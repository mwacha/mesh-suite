# Cadastro de Fornecedores Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the inert "Fornecedores" sidebar item into a working feature — 3 new views (list, form, detail) that manage `Partner` records filtered/defaulted to `PartnerRole.SUPPLIER`, structurally mirroring the existing Cliente (`PartnerRole.CUSTOMER`) views.

**Architecture:** Zero backend changes — `Partner`/`PartnerController`/`PartnerService`/`api/partners.ts` are already generic by role. Three new frontend-only views (`FornecedoresListView.vue`, `FornecedorFormView.vue`, `FornecedorDetailView.vue`) are near-literal copies of `ClientesListView.vue`/`ClienteFormView.vue`/`ClienteDetailView.vue` with labels/defaults swapped to the supplier side, plus router/sidebar wiring.

**Tech Stack:** Vue 3 + TypeScript + Vitest, same stack as every other slice in this repo — no new dependencies.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-23-cadastro-fornecedores-design.md` — read it before starting if anything below is ambiguous.
- **No backend changes of any kind.** `Partner`, `PartnerController`, `PartnerService`, `PartnerRepository`, `api/partners.ts` are consumed as-is.
- **No new `Module.SUPPLIER` permission.** Fornecedores stays under the existing `Module.CUSTOMER` permission gate, exactly like Cliente — this is already how the backend enforces it (`PartnerService` methods are all `@RequiresPermission(module = Module.CUSTOMER, ...)` regardless of the `PartnerRole` being operated on), so no frontend permission-matrix change is needed either.
- `FornecedorFormView.vue`'s role checkboxes show all 3 roles (Cliente/Fornecedor/Transportadora-inert), exactly like `ClienteFormView.vue` — only the *default* checked role differs (`['SUPPLIER']` instead of `['CUSTOMER']`). The user can still check both Cliente and Fornecedor on the same record.
- `FornecedorDetailView.vue`'s "Dados" tab omits the 4 sale-only stub fields present in `ClienteDetailView.vue` (Tabela de Preço, Limite de Crédito, Forma de Pagamento, Vendedor Responsável) — they have no purchase-side equivalent in this codebase. Its "Pedidos" tab becomes "Ordens de Compra" (same static placeholder pattern, no real `PurchaseOrder` integration — matches the existing precedent where Cliente's "Pedidos" tab is *also* just a static placeholder despite `SalesOrder` existing for real).
- Every new view file gets its own `__tests__` spec, mirroring the coverage of its Cliente equivalent.
- Route names use the `fornecedores`/`fornecedores-novo`/`fornecedores-editar`/`fornecedores-detalhe` pattern (Portuguese, matching every other route name already in `router/index.ts`).

---

### Task 1: `FornecedoresListView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/FornecedoresListView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/FornecedoresListView.spec.ts`

**Interfaces:**
- Consumes: `listPartners`, `getPartnerSummary`, `updatePartnerStatus`, `deletePartner`, `type PartnerListItem`, `type PartnerSummary`, `type PartnerStatus`, `type PersonType` (all existing, from `@/api/partners`); `listMunicipalities` (existing, from `@/api/municipalities`); `maskTelefone`, `maskDocumento` (existing, from `@/utils/masks`); `AppShell`, `PageHeader`, `FilterBar`, `TextField`, `StatusBadge`, `StatPill`, `ActionsMenu`, `Pagination` (all existing components, unchanged).
- Produces: `FornecedoresListView` component, not yet mounted to any route (Task 4 wires the route).

- [ ] **Step 1: Write the failing component test**

```typescript
// mesh-suite-frontend/src/views/__tests__/FornecedoresListView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import FornecedoresListView from '@/views/FornecedoresListView.vue'
import * as partnersApi from '@/api/partners'
import * as municipiosApi from '@/api/municipalities'

vi.mock('@/api/partners')
vi.mock('@/api/municipalities')

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/fornecedores', name: 'fornecedores', component: FornecedoresListView },
      { path: '/fornecedores/novo', name: 'fornecedores-novo', component: { template: '<div />' } },
      { path: '/fornecedores/:id/editar', name: 'fornecedores-editar', component: { template: '<div />' } },
      { path: '/fornecedores/:id', name: 'fornecedores-detalhe', component: { template: '<div />' } },
    ],
  })
  router.push('/fornecedores')
  return router.isReady().then(() => ({
    router,
    // The Ações dropdown/filter panels Teleport to <body> -- stub it here
    // so it renders in place, keeping wrapper.find() queries working.
    wrapper: mount(FornecedoresListView, { global: { plugins: [router], stubs: { teleport: true } } }),
  }))
}

const parceiroBase = {
  id: 'p1', tradeName: 'Tecidos Aurora', legalName: 'Tecidos Aurora Ltda',
  document: '11222333000144', personType: 'LEGAL_ENTITY' as const,
  city: 'São Paulo', state: 'SP', whatsapp: '11934567890',
  status: 'ACTIVE' as const,
}

describe('FornecedoresListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [parceiroBase], totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
    vi.mocked(partnersApi.getPartnerSummary).mockResolvedValue({ total: 1, active: 1, atRisk: 0, blocked: 0 })
    vi.mocked(municipiosApi.listMunicipalities).mockResolvedValue(['São Paulo'])
  })

  it('loads and displays the supplier list on mount, with the count in the page header', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect(wrapper.text()).toContain('1 fornecedores cadastrados')
  })

  it('only lists Fornecedores, never Clientes/Transportadoras', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ papel: 'SUPPLIER' }))
    expect(partnersApi.getPartnerSummary).toHaveBeenCalledWith('SUPPLIER')
  })

  it('re-fetches with the search term when the search field changes', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="filter-bar-search"]').setValue('aurora')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'aurora' }))
  })

  it('navigates to the create form when "+ Novo Fornecedor" is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="novo-fornecedor"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores-novo')
  })

  it('navigates to the detail view via the Ações menu\'s "Ver" item', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-ver"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores-detalhe')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('navigates to the edit form when the row itself is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="row-p1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('toggles a supplier status via the Ações menu', async () => {
    vi.mocked(partnersApi.updatePartnerStatus).mockResolvedValue()
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="btn-acoes-p1"]').trigger('click')
    await wrapper.find('[data-test="acao-status"]').trigger('click')
    await flushPromises()

    expect(partnersApi.updatePartnerStatus).toHaveBeenCalledWith('p1', 'BLOCKED')
  })

  it('re-fetches with the new page when pagination is used', async () => {
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [parceiroBase], totalElements: 25, totalPages: 3, number: 0, size: 10,
    })
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="pagination-page-2"]').trigger('click')
    await flushPromises()

    expect(partnersApi.listPartners).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
  })

  it('shows an error message when loading the supplier list fails', async () => {
    vi.mocked(partnersApi.listPartners).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar a lista de fornecedores.')
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run FornecedoresListView`
Expected: FAIL — `Failed to resolve import "@/views/FornecedoresListView.vue"`.

- [ ] **Step 3: Implement `FornecedoresListView.vue`**

```vue
<!-- mesh-suite-frontend/src/views/FornecedoresListView.vue -->
<template>
  <AppShell title="Fornecedores">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <PageHeader title="Fornecedores" :count="countLabel">
      <button type="button" class="btn-primary" data-test="novo-fornecedor" @click="novoFornecedor">+ Novo Fornecedor</button>
    </PageHeader>

    <FilterBar
      :search="filtros.busca"
      search-placeholder="Buscar fornecedor por nome..."
      :categories="categorias"
      :value-map="valueMap"
      :custom-categories="['Nr. Documento']"
      @update:search="onBuscaChange"
      @update:filters="onFiltrosChange"
    >
      <template #custom-panel="{ apply }">
        <div class="documento-filtro">
          <div class="documento-filtro-tipo">
            <label class="documento-filtro-radio">
              <input type="radio" value="CNPJ" v-model="tipoDocFiltro" data-test="documento-filtro-tipo-cnpj" />
              CNPJ
            </label>
            <label class="documento-filtro-radio">
              <input type="radio" value="CPF" v-model="tipoDocFiltro" data-test="documento-filtro-tipo-cpf" />
              CPF
            </label>
          </div>
          <TextField
            v-model="numeroDocFiltro"
            placeholder="Número do documento"
            :mask="(v) => maskDocumento(v, TIPO_LABELS[tipoDocFiltro] ?? 'LEGAL_ENTITY')"
            test-id="documento-filtro-numero"
          />
          <button
            type="button"
            class="documento-filtro-aplicar"
            data-test="documento-filtro-aplicar"
            @click="apply(tipoDocFiltro && numeroDocFiltro.trim() ? `${tipoDocFiltro}: ${numeroDocFiltro.trim()}` : null)"
          >
            Aplicar
          </button>
        </div>
      </template>
    </FilterBar>

    <section class="table-card">
      <div class="table-card-header">
        <span class="table-card-title">Lista de Fornecedores</span>
        <div v-if="resumo" class="table-card-stats">
          <StatPill :value="resumo.total" label="Total" color="dark" />
          <StatPill :value="resumo.active" label="Ativos" color="green" />
          <StatPill :value="resumo.atRisk" label="Em Risco" color="amber" />
          <StatPill :value="resumo.blocked" label="Bloqueados" color="red" />
        </div>
      </div>

      <div class="table-grid">
        <div class="table-grid-header">
          <div class="table-grid-col table-grid-col-sortable" data-test="col-nome" @click="toggleSort('tradeName')">
            Nome / Razão Social
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'tradeName' }">{{ sortIcon('tradeName') }}</span>
          </div>
          <div class="table-grid-col">Documento</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-cidade" @click="toggleSort('city')">
            Cidade
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'city' }">{{ sortIcon('city') }}</span>
          </div>
          <div class="table-grid-col" data-test="col-telefone">Telefone</div>
          <div class="table-grid-col table-grid-col-sortable" data-test="col-status" @click="toggleSort('status')">
            Status
            <span class="table-grid-sort-icon" :class="{ 'table-grid-sort-icon-active': sortField === 'status' }">{{ sortIcon('status') }}</span>
          </div>
          <div class="table-grid-col"></div>
        </div>

        <div
          v-for="parceiro in pagina.content"
          :key="parceiro.id"
          class="table-grid-row table-grid-row-clickable"
          :data-test="`row-${parceiro.id}`"
          @click="editarFornecedor(parceiro.id)"
        >
          <div class="table-grid-cell table-grid-cell-nome">{{ parceiro.tradeName }}</div>
          <div class="table-grid-cell">{{ maskDocumento(parceiro.document, parceiro.personType) }}</div>
          <div class="table-grid-cell">{{ parceiro.city }}</div>
          <div class="table-grid-cell">{{ maskTelefone(parceiro.whatsapp) }}</div>
          <div class="table-grid-cell">
            <StatusBadge :label="statusLabel(parceiro.status)" :color="statusColor(parceiro.status)" />
          </div>
          <div class="table-grid-cell" @click.stop>
            <ActionsMenu :items="acoesPara(parceiro)" :test-id="`btn-acoes-${parceiro.id}`" />
          </div>
        </div>
      </div>
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
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import TextField from '@/components/TextField.vue'
import StatusBadge, { type StatusBadgeColor } from '@/components/StatusBadge.vue'
import StatPill from '@/components/StatPill.vue'
import ActionsMenu, { type ActionsMenuItem } from '@/components/ActionsMenu.vue'
import Pagination from '@/components/Pagination.vue'
import type { Page } from '@/api/types'
import {
  listPartners,
  getPartnerSummary,
  updatePartnerStatus,
  deletePartner,
  type PartnerListItem,
  type PartnerSummary,
  type PartnerStatus,
  type PersonType,
} from '@/api/partners'
import { listMunicipalities } from '@/api/municipalities'
import { maskTelefone, maskDocumento } from '@/utils/masks'

const router = useRouter()

const STATUS_LABELS: Record<string, PartnerStatus> = { Ativo: 'ACTIVE', 'Em Risco': 'AT_RISK', Bloqueado: 'BLOCKED' }
const TIPO_LABELS: Record<string, PersonType> = { CNPJ: 'LEGAL_ENTITY', CPF: 'INDIVIDUAL' }
const UFS = ['AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI',
  'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO']

const categorias = ['Status', 'Nr. Documento', 'UF', 'Cidade']
const cidades = ref<string[]>([])
const valueMap = computed<Record<string, string[]>>(() => ({
  Status: Object.keys(STATUS_LABELS),
  UF: UFS,
  Cidade: cidades.value,
}))

const tipoDocFiltro = ref<'CNPJ' | 'CPF' | ''>('')
const numeroDocFiltro = ref('')

const filtros = reactive({ busca: '' })
const filtrosAvancados = ref<Record<string, string[]>>({})
const sortField = ref<'tradeName' | 'city' | 'status' | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')

const pagina = ref<Page<PartnerListItem>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<PartnerSummary | null>(null)
const erro = ref('')

const countLabel = computed(() => (resumo.value ? `${resumo.value.total} fornecedores cadastrados` : undefined))

function statusLabel(status: PartnerStatus) {
  return { ACTIVE: 'Ativo', AT_RISK: 'Em Risco', BLOCKED: 'Bloqueado' }[status]
}

function statusColor(status: PartnerStatus): StatusBadgeColor {
  return { ACTIVE: 'green', AT_RISK: 'amber', BLOCKED: 'red' }[status] as StatusBadgeColor
}

function sortIcon(field: 'tradeName' | 'city' | 'status') {
  if (sortField.value !== field) {
    return '⇅'
  }
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function toggleSort(field: 'tradeName' | 'city' | 'status') {
  if (sortField.value === field) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortDir.value = 'asc'
  }
  carregar(0)
}

function labelsFor(categoria: string): string[] {
  return filtrosAvancados.value[categoria] ?? []
}

function parseFiltroDocumento(): { tipoDocumento?: PersonType[]; documento?: string } {
  const valor = labelsFor('Nr. Documento')[0]
  if (!valor) {
    return {}
  }
  const [tipo, ...resto] = valor.split(': ')
  const tipoPessoa = TIPO_LABELS[tipo]
  const documento = resto.join(': ').trim()
  if (!tipoPessoa || !documento) {
    return {}
  }
  return { tipoDocumento: [tipoPessoa], documento }
}

async function carregar(page: number) {
  erro.value = ''
  const status = labelsFor('Status').map((l) => STATUS_LABELS[l])
  const { tipoDocumento, documento } = parseFiltroDocumento()
  const uf = labelsFor('UF')
  const cidade = labelsFor('Cidade')
  try {
    pagina.value = await listPartners({
      busca: filtros.busca || undefined,
      papel: 'SUPPLIER',
      status: status.length ? status : undefined,
      tipoDocumento,
      documento,
      uf: uf.length ? uf : undefined,
      cidade: cidade.length ? cidade : undefined,
      sort: sortField.value ? `${sortField.value},${sortDir.value}` : undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de fornecedores.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await getPartnerSummary('SUPPLIER')
  } catch {
    erro.value = 'Não foi possível carregar o resumo de fornecedores.'
  }
}

async function carregarCidades() {
  const ufsSelecionadas = labelsFor('UF')
  const uf = ufsSelecionadas.length === 1 ? ufsSelecionadas[0] : undefined
  try {
    cidades.value = await listMunicipalities({ uf })
  } catch {
    cidades.value = []
  }
}

function onBuscaChange(valor: string) {
  filtros.busca = valor
  carregar(0)
}

async function onFiltrosChange(filtrosNovos: Record<string, string[]>) {
  const ufAnterior = labelsFor('UF')[0]
  filtrosAvancados.value = filtrosNovos
  const ufNova = labelsFor('UF')[0]
  if (ufNova !== ufAnterior) {
    await carregarCidades()
  }
  carregar(0)
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

function novoFornecedor() {
  router.push({ name: 'fornecedores-novo' })
}

function abrirFornecedor(id: string) {
  router.push({ name: 'fornecedores-detalhe', params: { id } })
}

function editarFornecedor(id: string) {
  router.push({ name: 'fornecedores-editar', params: { id } })
}

async function alternarStatus(parceiro: PartnerListItem) {
  erro.value = ''
  const novoStatus = parceiro.status === 'BLOCKED' ? 'ACTIVE' : 'BLOCKED'
  try {
    await updatePartnerStatus(parceiro.id, novoStatus)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status.'
  }
}

async function excluir(parceiro: PartnerListItem) {
  if (!confirm(`Excluir o fornecedor "${parceiro.tradeName}"?`)) {
    return
  }
  erro.value = ''
  try {
    await deletePartner(parceiro.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir o fornecedor.'
  }
}

function acoesPara(parceiro: PartnerListItem): ActionsMenuItem[] {
  return [
    { label: 'Ver', action: () => abrirFornecedor(parceiro.id), testId: 'acao-ver' },
    { label: 'Editar', action: () => editarFornecedor(parceiro.id), testId: 'acao-editar' },
    {
      label: parceiro.status === 'BLOCKED' ? 'Ativar' : 'Bloquear',
      action: () => alternarStatus(parceiro),
      testId: 'acao-status',
    },
    { label: 'Excluir', action: () => excluir(parceiro), danger: true, testId: 'acao-excluir' },
  ]
}

onMounted(() => {
  carregar(0)
  carregarResumo()
  carregarCidades()
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: var(--pm-font);
}

.table-card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.table-card-stats {
  display: flex;
  gap: 8px;
}

.table-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.table-grid-header,
.table-grid-row {
  display: grid;
  grid-template-columns: 1fr 150px 130px 150px 100px 90px;
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

.table-grid-row-clickable {
  cursor: pointer;
  transition: background-color 0.1s;
}

.table-grid-row-clickable:hover {
  background: var(--pm-bg);
}

.table-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.documento-filtro {
  padding: 12px;
  font-family: var(--pm-font);
}

.documento-filtro-tipo {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
}

.documento-filtro-radio {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--pm-text-dark);
  cursor: pointer;
}

.documento-filtro-aplicar {
  width: 100%;
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 6px;
  height: 32px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
</style>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd mesh-suite-frontend && npx vitest run FornecedoresListView`
Expected: all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/FornecedoresListView.vue mesh-suite-frontend/src/views/__tests__/FornecedoresListView.spec.ts
git commit -m "feat(fornecedores): add read-only+actions FornecedoresListView"
```

---

### Task 2: `FornecedorFormView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/FornecedorFormView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/FornecedorFormView.spec.ts`

**Interfaces:**
- Consumes: `getPartner`, `createPartner`, `updatePartner`, `type PartnerRequest`, `type PartnerRole` (existing, from `@/api/partners`); `buscarEnderecoPorCep` (existing, from `@/api/cep`); `maskTelefone`, `maskCep`, `maskDocumento` (existing, from `@/utils/masks`); `emailValido`, `emailsValidos`, `telefoneValido`, `documentoValido`, `cepValido` (existing, from `@/utils/validacao`); `useToast` (existing, from `@/composables/useToast`); `AppShell`, `TextField`, `CollapsibleSection` (existing components).
- Produces: `FornecedorFormView` component, handles both `/fornecedores/novo` and `/fornecedores/:id/editar` (route wiring is Task 4).

- [ ] **Step 1: Write the failing component test**

```typescript
// mesh-suite-frontend/src/views/__tests__/FornecedorFormView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import FornecedorFormView from '@/views/FornecedorFormView.vue'
import * as partnersApi from '@/api/partners'
import * as cepApi from '@/api/cep'
import { useToast } from '@/composables/useToast'

vi.mock('@/api/partners')
vi.mock('@/api/cep')

function mountWithRouter(path = '/fornecedores/novo') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/fornecedores', name: 'fornecedores', component: { template: '<div />' } },
      { path: '/fornecedores/novo', name: 'fornecedores-novo', component: FornecedorFormView },
      { path: '/fornecedores/:id/editar', name: 'fornecedores-editar', component: FornecedorFormView },
    ],
  })
  router.push(path)
  return router.isReady().then(() => ({
    router,
    wrapper: mount(FornecedorFormView, { global: { plugins: [router] } }),
  }))
}

describe('FornecedorFormView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    useToast().toasts.splice(0, useToast().toasts.length)
  })

  it('defaults the Fornecedor role checkbox to checked and Cliente to unchecked', async () => {
    const { wrapper } = await mountWithRouter()

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    // Order in the template is Cliente, Fornecedor, Transportadora (inert) --
    // unchanged from ClienteFormView; only which one starts checked differs.
    expect((checkboxes[0].element as HTMLInputElement).checked).toBe(false)
    expect((checkboxes[1].element as HTMLInputElement).checked).toBe(true)
  })

  it('shows a required-field error when nomeFantasia is blank on submit', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Campo obrigatório')
    expect(partnersApi.createPartner).not.toHaveBeenCalled()
  })

  it('requires at least Cliente or Fornecedor to be selected', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Tecidos Aurora')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Tecidos Aurora Comércio LTDA')
    // Fornecedor starts checked by default -- unchecking it leaves papeis empty.
    await wrapper.findAll('input[type="checkbox"]')[1].setValue(false)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Selecione ao menos Cliente ou Fornecedor')
  })

  it('submits the form with roles defaulted to SUPPLIER and navigates to the list on success', async () => {
    vi.mocked(partnersApi.createPartner).mockResolvedValue({} as any)
    const { router, wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Tecidos Aurora')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Tecidos Aurora Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(partnersApi.createPartner).toHaveBeenCalledWith(expect.objectContaining({ roles: ['SUPPLIER'] }))
    expect(router.currentRoute.value.name).toBe('fornecedores')
    expect(useToast().toasts.some((t) => t.message === 'Fornecedor salvo com sucesso!')).toBe(true)
  })

  it('masks the documento as CNPJ while typing (tipoPessoa defaults to JURIDICA)', async () => {
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="documento"]').setValue('11222333000144')

    expect((wrapper.find('[data-test="documento"]').element as HTMLInputElement).value).toBe('11.222.333/0001-44')
  })

  it('shows a conflict message on duplicate documento (409)', async () => {
    vi.mocked(partnersApi.createPartner).mockRejectedValue({ response: { status: 409 } })
    const { wrapper } = await mountWithRouter()

    await wrapper.find('[data-test="nomeFantasia"]').setValue('Tecidos Aurora')
    await wrapper.find('[data-test="razaoSocial"]').setValue('Tecidos Aurora Comércio LTDA')
    await wrapper.find('[data-test="documento"]').setValue('11222333000144')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe um parceiro cadastrado com este documento')
  })

  it('loads existing parceiro data in edit mode', async () => {
    vi.mocked(partnersApi.getPartner).mockResolvedValue({
      id: 'abc-123', personType: 'LEGAL_ENTITY', document: '11222333000144', tradeName: 'Tecidos Aurora',
      legalName: '', status: 'ACTIVE', roles: ['SUPPLIER'], billingEmails: '', whatsapp: '',
      taxIndicator: null, stateRegistration: '', municipalRegistration: '', suframaRegistration: '',
      zipCode: '', street: '', number: '', neighborhood: '', complement: '', state: '', city: '',
      notes: '', contacts: [],
    } as any)

    const { wrapper } = await mountWithRouter('/fornecedores/abc-123/editar')
    await flushPromises()

    expect(partnersApi.getPartner).toHaveBeenCalledWith('abc-123')
    expect((wrapper.find('[data-test="nomeFantasia"]').element as HTMLInputElement).value).toBe('Tecidos Aurora')
  })

  it('shows an error message when loading parceiro data fails in edit mode', async () => {
    vi.mocked(partnersApi.getPartner).mockRejectedValue(new Error('network error'))

    const { wrapper } = await mountWithRouter('/fornecedores/abc-123/editar')
    await flushPromises()

    expect(partnersApi.getPartner).toHaveBeenCalledWith('abc-123')
    expect(wrapper.text()).toContain('Não foi possível carregar os dados do fornecedor')
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run FornecedorFormView`
Expected: FAIL — `Failed to resolve import "@/views/FornecedorFormView.vue"`.

- [ ] **Step 3: Implement `FornecedorFormView.vue`**

```vue
<!-- mesh-suite-frontend/src/views/FornecedorFormView.vue -->
<template>
  <AppShell :title="modoEdicao ? 'Editar Fornecedor' : 'Novo Fornecedor'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados Gerais</h2>
        <div class="grid grid-3">
          <div>
            <label class="field-label">Tipo de Pessoa *</label>
            <select v-model="form.personType">
              <option value="LEGAL_ENTITY">Jurídica</option>
              <option value="INDIVIDUAL">Física</option>
            </select>
          </div>
          <TextField
            v-model="form.document"
            label="CNPJ / CPF"
            required
            :mask="(v) => maskDocumento(v, form.personType)"
            :maxlength="form.personType === 'LEGAL_ENTITY' ? 18 : 14"
            :error="erros.documento"
            test-id="documento"
            @blur="validarDocumento"
          />
          <TextField
            v-model="form.tradeName"
            label="Nome Fantasia"
            required
            placeholder="Ex: Tecidos Aurora"
            :error="erros.nomeFantasia"
            test-id="nomeFantasia"
            @blur="validarNomeFantasia"
          />
        </div>
        <TextField
          v-model="form.legalName"
          label="Razão Social"
          required
          :error="erros.razaoSocial"
          test-id="razaoSocial"
          @blur="validarRazaoSocial"
        />
        <div>
          <label class="field-label">
            Tipo de Papel * <span class="hint">(pode selecionar mais de uma opção)</span>
          </label>
          <div class="checkbox-row">
            <label class="checkbox-label">
              <input type="checkbox" :checked="form.roles.includes('CUSTOMER')" @change="togglePapel('CUSTOMER')" />
              Cliente
            </label>
            <label class="checkbox-label">
              <input type="checkbox" :checked="form.roles.includes('SUPPLIER')" @change="togglePapel('SUPPLIER')" />
              Fornecedor
            </label>
            <label
              class="checkbox-label checkbox-inert"
              title="Pertence ao domínio Expedição/Logística, ainda não implementado"
            >
              <input type="checkbox" disabled />
              Transportadora
            </label>
          </div>
          <p v-if="erros.papeis" class="field-error">{{ erros.papeis }}</p>
        </div>
      </section>

      <CollapsibleSection title="Contato para Cobrança e Faturamento">
        <div class="grid grid-2">
          <TextField
            v-model="form.billingEmails"
            label="E-mail(s)"
            placeholder="email@exemplo.com.br"
            :error="erros.emailsCobranca"
            @blur="validarEmailsCobranca"
          />
          <TextField
            v-model="form.whatsapp"
            label="Número do WhatsApp"
            placeholder="(11) 99999-9999"
            :mask="maskTelefone"
            :maxlength="15"
            :error="erros.whatsapp"
            @blur="validarWhatsapp"
          />
        </div>
        <p class="hint">Para inserir mais de um e-mail, use a vírgula</p>
      </CollapsibleSection>

      <CollapsibleSection title="Informações Fiscais">
        <div class="grid grid-4">
          <div>
            <label class="field-label">Indicador de Inscrição Estadual</label>
            <select v-model="form.taxIndicator">
              <option :value="null">Selecione...</option>
              <option value="NON_TAXPAYER">Não contribuinte</option>
              <option value="TAXPAYER">Contribuinte</option>
              <option value="EXEMPT_TAXPAYER">Contribuinte isento</option>
            </select>
          </div>
          <div>
            <label class="field-label">Inscrição Estadual</label>
            <input v-model="form.stateRegistration" />
          </div>
          <div>
            <label class="field-label">Inscrição Municipal</label>
            <input v-model="form.municipalRegistration" />
          </div>
          <div>
            <label class="field-label">Inscrição Suframa</label>
            <input v-model="form.suframaRegistration" />
          </div>
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Endereço">
        <div class="grid grid-3">
          <div>
            <label class="field-label">CEP</label>
            <div class="input-action">
              <TextField
                v-model="form.zipCode"
                :mask="maskCep"
                :maxlength="9"
                :error="erros.cep"
                test-id="cep"
                @blur="validarCep"
              />
              <button type="button" data-test="buscar-cep" @click="buscarCep">Buscar dados</button>
            </div>
            <p v-if="erroCep" class="field-error">{{ erroCep }}</p>
          </div>
          <div>
            <label class="field-label">Endereço</label>
            <input v-model="form.street" data-test="logradouro" />
          </div>
          <div>
            <label class="field-label">Número</label>
            <input v-model="form.number" />
          </div>
        </div>
        <div class="grid grid-4">
          <div>
            <label class="field-label">Estado</label>
            <select v-model="form.state" data-test="uf">
              <option value="">UF</option>
              <option v-for="estado in UFS" :key="estado" :value="estado">{{ estado }}</option>
            </select>
          </div>
          <div>
            <label class="field-label">Cidade</label>
            <input v-model="form.city" data-test="cidade" />
          </div>
          <div>
            <label class="field-label">Bairro</label>
            <input v-model="form.neighborhood" />
          </div>
          <div>
            <label class="field-label">Complemento</label>
            <input v-model="form.complement" />
          </div>
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Outros Contatos">
        <div v-for="(contato, index) in form.contacts" :key="index" class="grid grid-contato">
          <input v-model="contato.name" placeholder="Nome" />
          <TextField
            v-model="contato.email"
            placeholder="email@exemplo.com"
            :error="errosContatos[index]?.email"
            @blur="validarContatoEmail(index)"
          />
          <TextField
            v-model="contato.businessPhone"
            placeholder="(11) 3333-3333"
            :mask="maskTelefone"
            :maxlength="15"
            :error="errosContatos[index]?.businessPhone"
            @blur="validarContatoTelefone(index, 'businessPhone')"
          />
          <TextField
            v-model="contato.mobilePhone"
            placeholder="(11) 99999-9999"
            :mask="maskTelefone"
            :maxlength="15"
            :error="errosContatos[index]?.mobilePhone"
            @blur="validarContatoTelefone(index, 'mobilePhone')"
          />
          <input v-model="contato.jobTitle" placeholder="Ex: Financeiro" />
          <button type="button" class="btn-remove" @click="removerContato(index)">🗑</button>
        </div>
        <button type="button" class="btn-add-contato" @click="adicionarContato">+ Adicionar Contato</button>
      </CollapsibleSection>

      <section class="card">
        <h2>Observação</h2>
        <textarea v-model="form.notes" rows="4" placeholder="Informações adicionais sobre o fornecedor..."></textarea>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Fornecedor</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import TextField from '@/components/TextField.vue'
import CollapsibleSection from '@/components/CollapsibleSection.vue'
import {
  getPartner,
  createPartner,
  updatePartner,
  type PartnerRequest,
  type PartnerRole,
} from '@/api/partners'
import { buscarEnderecoPorCep } from '@/api/cep'
import { maskTelefone, maskCep, maskDocumento } from '@/utils/masks'
import { emailValido, emailsValidos, telefoneValido, documentoValido, cepValido } from '@/utils/validacao'
import { useToast } from '@/composables/useToast'

const { showToast } = useToast()

const UFS = [
  'AC', 'AL', 'AM', 'AP', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MG', 'MS', 'MT', 'PA', 'PB',
  'PE', 'PI', 'PR', 'RJ', 'RN', 'RO', 'RR', 'RS', 'SC', 'SE', 'SP', 'TO',
]

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): PartnerRequest {
  return {
    personType: 'LEGAL_ENTITY',
    document: '',
    tradeName: '',
    legalName: '',
    roles: ['SUPPLIER'],
    billingEmails: '',
    whatsapp: '',
    taxIndicator: null,
    stateRegistration: '',
    municipalRegistration: '',
    suframaRegistration: '',
    zipCode: '',
    street: '',
    number: '',
    neighborhood: '',
    complement: '',
    state: '',
    city: '',
    notes: '',
    contacts: [],
  }
}

interface ErrosContato {
  email?: string
  businessPhone?: string
  mobilePhone?: string
}

const form = reactive<PartnerRequest>(novoFormulario())
const erros = reactive<{
  nomeFantasia?: string
  razaoSocial?: string
  papeis?: string
  documento?: string
  emailsCobranca?: string
  whatsapp?: string
  cep?: string
}>({})
const errosContatos = ref<ErrosContato[]>([])
const erroGeral = ref('')
const erroCep = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const parceiro = await getPartner(id)
      Object.assign(form, parceiro)
      errosContatos.value = form.contacts.map(() => ({}))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do fornecedor. Tente novamente em instantes.'
    }
  }
})

function togglePapel(papel: PartnerRole) {
  const index = form.roles.indexOf(papel)
  if (index === -1) {
    form.roles.push(papel)
  } else {
    form.roles.splice(index, 1)
  }
}

function adicionarContato() {
  form.contacts.push({ name: '', email: '', businessPhone: '', mobilePhone: '', jobTitle: '' })
  errosContatos.value.push({})
}

function removerContato(index: number) {
  form.contacts.splice(index, 1)
  errosContatos.value.splice(index, 1)
}

async function buscarCep() {
  erroCep.value = ''
  const endereco = await buscarEnderecoPorCep(form.zipCode)
  if (!endereco) {
    erroCep.value = 'CEP não encontrado — preencha o endereço manualmente'
    return
  }
  form.street = endereco.logradouro
  form.neighborhood = endereco.bairro
  form.city = endereco.localidade
  form.state = endereco.uf
}

function validarNomeFantasia() {
  erros.nomeFantasia = form.tradeName.trim() ? undefined : 'Campo obrigatório'
}

function validarRazaoSocial() {
  erros.razaoSocial = form.legalName.trim() ? undefined : 'Campo obrigatório'
}

function validarDocumento() {
  if (!form.document.trim()) {
    erros.documento = 'Campo obrigatório'
  } else if (!documentoValido(form.document, form.personType)) {
    erros.documento = `Informe um ${form.personType === 'LEGAL_ENTITY' ? 'CNPJ' : 'CPF'} válido`
  } else {
    erros.documento = undefined
  }
}

function validarEmailsCobranca() {
  erros.emailsCobranca = emailsValidos(form.billingEmails) ? undefined : 'Informe um e-mail válido'
}

function validarWhatsapp() {
  erros.whatsapp = !form.whatsapp || telefoneValido(form.whatsapp) ? undefined : 'Informe um telefone válido'
}

function validarCep() {
  erros.cep = !form.zipCode || cepValido(form.zipCode) ? undefined : 'CEP inválido'
}

function validarContatoEmail(index: number) {
  const contato = form.contacts[index]
  errosContatos.value[index] = {
    ...errosContatos.value[index],
    email: !contato.email || emailValido(contato.email) ? undefined : 'E-mail inválido',
  }
}

function validarContatoTelefone(index: number, campo: 'businessPhone' | 'mobilePhone') {
  const contato = form.contacts[index]
  const valor = contato[campo]
  errosContatos.value[index] = {
    ...errosContatos.value[index],
    [campo]: !valor || telefoneValido(valor) ? undefined : 'Telefone inválido',
  }
}

function validarPapeis() {
  erros.papeis = form.roles.some((p) => p === 'CUSTOMER' || p === 'SUPPLIER')
    ? undefined
    : 'Selecione ao menos Cliente ou Fornecedor'
}

function validar(): boolean {
  validarNomeFantasia()
  validarRazaoSocial()
  validarDocumento()
  validarEmailsCobranca()
  validarWhatsapp()
  validarCep()
  validarPapeis()
  form.contacts.forEach((_, index) => {
    validarContatoEmail(index)
    validarContatoTelefone(index, 'businessPhone')
    validarContatoTelefone(index, 'mobilePhone')
  })

  const semErroContatos = errosContatos.value.every(
    (e) => !e?.email && !e?.businessPhone && !e?.mobilePhone,
  )
  return (
    !erros.nomeFantasia &&
    !erros.razaoSocial &&
    !erros.papeis &&
    !erros.documento &&
    !erros.emailsCobranca &&
    !erros.whatsapp &&
    !erros.cep &&
    semErroContatos
  )
}

async function salvar() {
  erroGeral.value = ''
  if (!validar()) {
    return
  }
  salvando.value = true
  try {
    const id = route.params.id
    if (typeof id === 'string') {
      await updatePartner(id, form)
    } else {
      await createPartner(form)
    }
    showToast('Fornecedor salvo com sucesso!')
    router.push({ name: 'fornecedores' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um parceiro cadastrado com este documento.'
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
  router.push({ name: 'fornecedores' })
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

.grid-3 {
  grid-template-columns: 200px 1fr 1fr;
}

.grid-4 {
  grid-template-columns: repeat(4, 1fr);
}

.grid-contato {
  grid-template-columns: 1fr 1fr 130px 130px 130px 36px;
  align-items: start;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

.hint {
  font-size: 11px;
  color: var(--pm-text-muted);
  margin: 0 0 8px;
}

input,
select,
textarea {
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

.checkbox-row {
  display: flex;
  gap: 24px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--pm-text-dark);
}

.checkbox-inert {
  cursor: not-allowed;
  color: var(--pm-text-muted);
}

.input-action {
  display: flex;
  gap: 6px;
  align-items: flex-start;
}

.input-action :deep(.text-field) {
  flex: 1;
}

.input-action button {
  height: 36px;
  flex-shrink: 0;
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.btn-remove {
  width: 36px;
  height: 36px;
  border: 1px solid var(--pm-error-bg);
  background: var(--pm-error-bg);
  color: var(--pm-error);
  border-radius: 8px;
  cursor: pointer;
}

.btn-add-contato {
  background: none;
  border: 1.5px dashed var(--pm-accent);
  color: var(--pm-accent);
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  cursor: pointer;
}

.error-geral {
  color: var(--pm-error);
  font-size: 14px;
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd mesh-suite-frontend && npx vitest run FornecedorFormView`
Expected: all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/FornecedorFormView.vue mesh-suite-frontend/src/views/__tests__/FornecedorFormView.spec.ts
git commit -m "feat(fornecedores): add FornecedorFormView with roles defaulted to SUPPLIER"
```

---

### Task 3: `FornecedorDetailView.vue`

**Files:**
- Create: `mesh-suite-frontend/src/views/FornecedorDetailView.vue`
- Test: `mesh-suite-frontend/src/views/__tests__/FornecedorDetailView.spec.ts`

**Interfaces:**
- Consumes: `getPartner`, `listPartners`, `type PartnerResponse`, `type PartnerListItem` (existing, from `@/api/partners`); `AppShell` (existing component).
- Produces: `FornecedorDetailView` component, handles `/fornecedores/:id` (route wiring is Task 4).

- [ ] **Step 1: Write the failing component test**

```typescript
// mesh-suite-frontend/src/views/__tests__/FornecedorDetailView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import FornecedorDetailView from '@/views/FornecedorDetailView.vue'
import * as partnersApi from '@/api/partners'

vi.mock('@/api/partners')

const parceiroCompleto = {
  id: 'p1', personType: 'LEGAL_ENTITY', document: '11222333000144', tradeName: 'Tecidos Aurora',
  legalName: 'Tecidos Aurora Ltda', status: 'ACTIVE', roles: ['SUPPLIER'], billingEmails: '', whatsapp: '',
  taxIndicator: null, stateRegistration: '', municipalRegistration: '', suframaRegistration: '',
  zipCode: '01310100', street: 'Av. Paulista', number: '1000', neighborhood: 'Bela Vista', complement: '',
  state: 'SP', city: 'São Paulo', notes: '', contacts: [],
} as any

function mountWithRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/fornecedores', name: 'fornecedores', component: { template: '<div />' } },
      { path: '/fornecedores/:id', name: 'fornecedores-detalhe', component: FornecedorDetailView },
      { path: '/fornecedores/:id/editar', name: 'fornecedores-editar', component: { template: '<div />' } },
    ],
  })
  router.push('/fornecedores/p1')
  return router.isReady().then(() => ({
    router,
    wrapper: mount(FornecedorDetailView, { global: { plugins: [router] } }),
  }))
}

describe('FornecedorDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(partnersApi.getPartner).mockResolvedValue(parceiroCompleto)
    vi.mocked(partnersApi.listPartners).mockResolvedValue({
      content: [{
        id: 'p1', tradeName: 'Tecidos Aurora', legalName: '', document: '', personType: 'LEGAL_ENTITY',
        city: 'São Paulo', state: 'SP', whatsapp: '', status: 'ACTIVE',
      }],
      totalElements: 1, totalPages: 1, number: 0, size: 10,
    })
  })

  it('loads and displays the selected supplier on the Dados tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Tecidos Aurora')
    expect((wrapper.find('input[readonly]').element as HTMLInputElement).value).toBe('Tecidos Aurora Ltda')
  })

  it('does not show the sale-only stub fields (Tabela de Preço, Limite de Crédito, Forma de Pagamento, Vendedor Responsável)', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).not.toContain('Tabela de Preço')
    expect(wrapper.text()).not.toContain('Limite de Crédito')
    expect(wrapper.text()).not.toContain('Forma de Pagamento')
    expect(wrapper.text()).not.toContain('Vendedor Responsável')
  })

  it('shows an empty state on the Ordens de Compra tab', async () => {
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    const tab = wrapper.findAll('.tab').find((t) => t.text() === 'Ordens de Compra')!
    await tab.trigger('click')

    expect(wrapper.text()).toContain('Nenhuma ordem de compra ainda')
  })

  it('navigates to the edit form when Editar is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="editar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores-editar')
    expect(router.currentRoute.value.params.id).toBe('p1')
  })

  it('navigates back to the supplier list when Cancelar is clicked', async () => {
    const { router, wrapper } = await mountWithRouter()
    await flushPromises()

    await wrapper.find('[data-test="cancelar"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('fornecedores')
  })

  it('shows an error message when loading the supplier fails', async () => {
    vi.mocked(partnersApi.getPartner).mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountWithRouter()
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os dados do fornecedor.')
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd mesh-suite-frontend && npx vitest run FornecedorDetailView`
Expected: FAIL — `Failed to resolve import "@/views/FornecedorDetailView.vue"`.

- [ ] **Step 3: Implement `FornecedorDetailView.vue`**

```vue
<!-- mesh-suite-frontend/src/views/FornecedorDetailView.vue -->
<template>
  <AppShell title="Fornecedor">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="detalhe">
      <aside class="rail">
        <input v-model="buscaRail" class="busca-rail" placeholder="Buscar fornecedor..." @input="carregarRail" />
        <div
          v-for="item in listaRail"
          :key="item.id"
          class="item-rail"
          :class="{ 'item-rail-ativo': item.id === parceiroId }"
          @click="selecionar(item.id)"
        >
          <div class="item-rail-nome">{{ item.tradeName }}</div>
          <div class="item-rail-info">{{ item.city }}</div>
        </div>
      </aside>

      <div v-if="parceiro" class="painel">
        <div class="painel-header">
          <h1>{{ parceiro.tradeName }}</h1>
          <div class="painel-acoes">
            <button type="button" class="btn-secondary" data-test="cancelar" @click="cancelar">Cancelar</button>
            <button type="button" class="btn-secondary" data-test="editar" @click="editar">✏️ Editar</button>
            <button
              type="button"
              class="btn-primary btn-inert"
              title="Cadastro de ordens de compra fora de escopo desta fatia"
            >
              + Ordem de Compra
            </button>
          </div>
        </div>

        <div class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab"
            type="button"
            class="tab"
            :class="{ 'tab-ativa': abaAtiva === tab }"
            @click="abaAtiva = tab"
          >
            {{ tab }}
          </button>
        </div>

        <div v-if="abaAtiva === 'Dados'" class="grid grid-2">
          <div><label class="field-label">Razão Social</label><input :value="parceiro.legalName" readonly /></div>
          <div><label class="field-label">CNPJ / CPF</label><input :value="parceiro.document" readonly /></div>
          <div><label class="field-label">Nome Fantasia</label><input :value="parceiro.tradeName" readonly /></div>
          <div><label class="field-label">Inscrição Estadual</label><input :value="parceiro.stateRegistration" readonly /></div>
        </div>

        <div v-else-if="abaAtiva === 'Endereços'" class="endereco">
          <p>{{ parceiro.street }}, {{ parceiro.number }} — {{ parceiro.neighborhood }}</p>
          <p>{{ parceiro.city }} / {{ parceiro.state }} — CEP {{ parceiro.zipCode }}</p>
        </div>

        <div v-else-if="abaAtiva === 'Contatos'">
          <div v-if="parceiro.contacts.length === 0" class="estado-vazio">Nenhum contato cadastrado</div>
          <div v-for="(contato, index) in parceiro.contacts" :key="index" class="contato-item">
            <strong>{{ contato.name }}</strong> — {{ contato.jobTitle }}
            <div>{{ contato.email }} · {{ contato.businessPhone }}</div>
          </div>
        </div>

        <div v-else-if="abaAtiva === 'Ordens de Compra'" class="estado-vazio">Nenhuma ordem de compra ainda</div>

        <div v-else-if="abaAtiva === 'Financeiro'" class="estado-vazio">Nenhum lançamento financeiro ainda</div>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { getPartner, listPartners, type PartnerResponse, type PartnerListItem } from '@/api/partners'

const route = useRoute()
const router = useRouter()

const tabs = ['Dados', 'Endereços', 'Contatos', 'Ordens de Compra', 'Financeiro'] as const
const abaAtiva = ref<(typeof tabs)[number]>('Dados')

const parceiroId = ref('')
const parceiro = ref<PartnerResponse | null>(null)
const listaRail = ref<PartnerListItem[]>([])
const buscaRail = ref('')
const erro = ref('')

async function carregarParceiro(id: string) {
  parceiroId.value = id
  erro.value = ''
  try {
    parceiro.value = await getPartner(id)
    abaAtiva.value = 'Dados'
  } catch {
    erro.value = 'Não foi possível carregar os dados do fornecedor.'
  }
}

async function carregarRail() {
  erro.value = ''
  try {
    const pagina = await listPartners({ busca: buscaRail.value || undefined, page: 0, size: 10 })
    listaRail.value = pagina.content
  } catch {
    erro.value = 'Não foi possível carregar a lista de fornecedores.'
  }
}

function selecionar(id: string) {
  router.push({ name: 'fornecedores-detalhe', params: { id } })
}

function editar() {
  router.push({ name: 'fornecedores-editar', params: { id: parceiroId.value } })
}

function cancelar() {
  router.push({ name: 'fornecedores' })
}

watch(
  () => route.params.id,
  (id) => {
    if (typeof id === 'string') {
      carregarParceiro(id)
    }
  },
)

onMounted(() => {
  carregarRail()
  const id = route.params.id
  if (typeof id === 'string') {
    carregarParceiro(id)
  }
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
}

.detalhe {
  display: flex;
  gap: 16px;
  font-family: var(--pm-font);
}

.rail {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.busca-rail {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  margin-bottom: 6px;
  box-sizing: border-box;
  width: 100%;
}

.item-rail {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  background: var(--pm-white);
}

.item-rail-ativo {
  border-color: var(--pm-accent);
  background: var(--pm-accent-bg);
}

.item-rail-nome {
  font-size: 12px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.item-rail-info {
  font-size: 11px;
  color: var(--pm-text-muted);
}

.painel {
  flex: 1;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px;
}

.painel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.painel-header h1 {
  font-size: 18px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0;
}

.painel-acoes {
  display: flex;
  gap: 8px;
}

.btn-primary,
.btn-secondary {
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
}

.btn-inert {
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
}

.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--pm-border-light);
  margin-bottom: 14px;
}

.tab {
  background: none;
  border: none;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--pm-text-mid);
  cursor: pointer;
  border-bottom: 2px solid transparent;
}

.tab-ativa {
  color: var(--pm-accent);
  border-bottom-color: var(--pm-accent);
  font-weight: 600;
}

.grid {
  display: grid;
  gap: 12px 16px;
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

input,
select {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  color: var(--pm-text-dark);
  font-family: var(--pm-font);
}

input:disabled,
select:disabled,
input[readonly] {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.endereco p {
  font-size: 13px;
  color: var(--pm-text-dark);
  margin: 0 0 4px;
}

.contato-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--pm-border-light);
  font-size: 13px;
  color: var(--pm-text-dark);
}

.estado-vazio {
  color: var(--pm-text-muted);
  font-size: 13px;
  padding: 24px 0;
  text-align: center;
}
</style>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd mesh-suite-frontend && npx vitest run FornecedorDetailView`
Expected: all 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add mesh-suite-frontend/src/views/FornecedorDetailView.vue mesh-suite-frontend/src/views/__tests__/FornecedorDetailView.spec.ts
git commit -m "feat(fornecedores): add FornecedorDetailView with Ordens de Compra tab replacing Pedidos"
```

---

### Task 4: Router + sidebar wiring

**Files:**
- Modify: `mesh-suite-frontend/src/router/index.ts`
- Modify: `mesh-suite-frontend/src/components/AppSidebar.vue`
- Modify: `mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts`

**Interfaces:**
- Consumes: `FornecedoresListView` (Task 1), `FornecedorFormView` (Task 2), `FornecedorDetailView` (Task 3).
- Produces: routes `fornecedores`, `fornecedores-novo`, `fornecedores-editar`, `fornecedores-detalhe`; sidebar nav item "Fornecedores" no longer inert.

- [ ] **Step 1: Write the failing sidebar test**

`AppSidebar.spec.ts` already has a self-contained-router test pattern for exactly this shape (`'navigates to /compras when Compras is clicked'`, `'navigates to /notas-fiscais-entrada when Notas de Entrada is clicked'`). Add a new test right after `'navigates to /clientes when Clientes is clicked, and highlights it from a sub-route'` (around line 105), following the same shape:

```typescript
  it('navigates to /fornecedores when Fornecedores is clicked', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'dashboard', component: { template: '<div />' } },
        { path: '/fornecedores', name: 'fornecedores', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(AppSidebar, { global: { plugins: [router] } })

    await wrapper.find('[data-test="group-cadastros"]').trigger('click')
    await wrapper.find('[data-test="nav-Fornecedores"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/fornecedores')
  })
```

Then update the existing `'shows inert (not-yet-implemented) items once their group is expanded'` test (around line 155-179): `Fornecedores` moves out of the still-inert loop, since it now routes to a real screen. Change:

```typescript
    await wrapper.find('[data-test="group-cadastros"]').trigger('click')
    for (const label of ['Fornecedores', 'Transportadoras']) {
      expect(wrapper.find(`[data-test="nav-${label}"]`).exists()).toBe(true)
      expect(wrapper.find(`[data-test="nav-${label}"]`).classes()).toContain('nav-item-inert')
    }
```

to:

```typescript
    await wrapper.find('[data-test="group-cadastros"]').trigger('click')
    // Fornecedores now routes to a real screen (this task), so it's no longer inert.
    expect(wrapper.find('[data-test="nav-Fornecedores"]').classes()).not.toContain('nav-item-inert')
    for (const label of ['Transportadoras']) {
      expect(wrapper.find(`[data-test="nav-${label}"]`).exists()).toBe(true)
      expect(wrapper.find(`[data-test="nav-${label}"]`).classes()).toContain('nav-item-inert')
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd mesh-suite-frontend && npx vitest run AppSidebar`
Expected: FAIL — the new navigation test fails to resolve `/fornecedores`, and the updated inert-check fails because `Fornecedores` is still inert (`route: null`).

- [ ] **Step 3: Register the routes**

In `mesh-suite-frontend/src/router/index.ts`, add the imports next to `ClienteDetailView`:

```typescript
import FornecedoresListView from '@/views/FornecedoresListView.vue'
import FornecedorFormView from '@/views/FornecedorFormView.vue'
import FornecedorDetailView from '@/views/FornecedorDetailView.vue'
```

and add the routes right after the `/clientes/*` routes:

```typescript
    { path: '/fornecedores', name: 'fornecedores', component: FornecedoresListView },
    { path: '/fornecedores/novo', name: 'fornecedores-novo', component: FornecedorFormView },
    { path: '/fornecedores/:id/editar', name: 'fornecedores-editar', component: FornecedorFormView },
    { path: '/fornecedores/:id', name: 'fornecedores-detalhe', component: FornecedorDetailView },
```

- [ ] **Step 4: Update the sidebar item**

In `mesh-suite-frontend/src/components/AppSidebar.vue`, change the `cadastros` group's `items` array from:

```typescript
    items: [
      { icon: '👥', label: 'Clientes', route: '/clientes' },
      { icon: '🏭', label: 'Fornecedores', route: null },
      { icon: '🚚', label: 'Transportadoras', route: null },
    ],
```

to:

```typescript
    items: [
      { icon: '👥', label: 'Clientes', route: '/clientes' },
      { icon: '🏭', label: 'Fornecedores', route: '/fornecedores' },
      { icon: '🚚', label: 'Transportadoras', route: null },
    ],
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd mesh-suite-frontend && npx vitest run AppSidebar`
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add mesh-suite-frontend/src/router/index.ts mesh-suite-frontend/src/components/AppSidebar.vue \
        mesh-suite-frontend/src/components/__tests__/AppSidebar.spec.ts
git commit -m "feat(fornecedores): register fornecedores routes and activate sidebar entry"
```

---

### Task 5: Full-suite verification + docs update

**Files:** none (verification only), plus `tabela-execucao.md`.

- [ ] **Step 1: Run the full frontend suite**

Run: `cd mesh-suite-frontend && npx vitest run`
Expected: all tests pass (this is a frontend-only slice, so — unlike backend-touching plans in this repo — there's no known suite instability to work around here).

- [ ] **Step 2: Type-check the frontend**

Run: `cd mesh-suite-frontend && npx vue-tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Update `tabela-execucao.md`**

In `tabela-execucao.md`, change row `MENU-01` from:

```
| MENU-01 | Fornecedores | CADASTROS | Tela dedicada de listagem/cadastro — hoje Fornecedor é só um papel (`PartnerRole.SUPPLIER`) dentro do cadastro genérico de Parceiro, sem view própria | Em andamento |
```

to:

```
| MENU-01 | Fornecedores | CADASTROS | **Concluído** — `FornecedoresListView.vue`/`FornecedorFormView.vue`/`FornecedorDetailView.vue`, reaproveitando 100% do backend Partner/`PartnerRole.SUPPLIER` já existente, sem mudança de backend. | Concluído |
```

- [ ] **Step 4: Commit**

```bash
git add tabela-execucao.md
git commit -m "docs: mark Fornecedores (MENU-01) as concluído in tabela-execucao.md"
```
