<template>
  <AppShell :title="editMode ? 'Editar Pedido' : 'Novo Pedido'">
    <form class="form" @submit.prevent="save">
      <section class="card">
        <h2>Dados do Pedido</h2>
        <div class="grid grid-3">
          <SearchSelect
            v-model="form.customerId"
            label="Cliente"
            required
            :selected-label="customerLabel"
            :items="customerResults"
            placeholder="Buscar cliente por nome ou CNPJ..."
            :error="errors.customerId"
            :empty-message="customerSearchError || undefined"
            :empty-is-error="!!customerSearchError"
            test-id="customer-search"
            @search="searchCustomers"
            @select="selectCustomer"
          />
          <div>
            <label class="field-label">Data do Pedido</label>
            <input v-model="form.orderDate" type="date" data-test="order-date" />
          </div>
          <div>
            <label class="field-label">Previsão de Entrega</label>
            <input v-model="form.deliveryDate" type="date" data-test="delivery-date" />
          </div>
        </div>
        <div class="grid grid-2">
          <SearchSelect
            v-model="form.salespersonId"
            label="Vendedor"
            required
            :selected-label="salespersonLabel"
            :items="salesRepResults"
            placeholder="Buscar vendedor..."
            :error="errors.salespersonId"
            test-id="salesperson"
            @search="searchSalesReps"
            @select="selectSalesperson"
          />
          <SearchSelect
            :model-value="priceTableId"
            label="Tabela de Preço (padrão)"
            :selected-label="priceTableLabel"
            :items="priceTableResults"
            placeholder="Buscar tabela de preço..."
            :empty-message="priceTableSearchError || undefined"
            :empty-is-error="!!priceTableSearchError"
            test-id="price-table"
            @search="searchPriceTables"
            @select="selectPriceTable"
          />
        </div>
        <div class="grid grid-2">
          <SearchSelect
            :model-value="paymentTermId"
            label="Condição de Pagamento"
            :selected-label="paymentTermLabel"
            :items="paymentTermResults"
            placeholder="Buscar condição de pagamento..."
            :empty-message="paymentTermSearchError || undefined"
            :empty-is-error="!!paymentTermSearchError"
            test-id="payment-term"
            @search="searchPaymentTerms"
            @select="selectPaymentTerm"
          />
        </div>
      </section>

      <section class="card">
        <h2>Itens</h2>
        <div class="item-adicionar">
          <ProductPicker
            class="item-product-busca"
            label="Adicionar Produto"
            :selected-label="productLabel"
            :items="productResults"
            :empty-message="productSearchError || undefined"
            :empty-is-error="!!productSearchError"
            test-id="product-search"
            @search="searchProducts"
            @select="selectProduct"
          />
          <div class="item-qtd">
            <label class="field-label">Qtd</label>
            <input
              v-model.number="itemForm.quantity"
              type="number"
              step="0.001"
              min="0.001"
              placeholder="1"
              data-test="item-quantity"
            />
          </div>
          <div>
            <label class="field-label">Vlr. Produto</label>
            <div class="item-readonly" data-test="item-unit-price">{{ formatPrice(itemForm.unitPrice) }}</div>
          </div>
          <div>
            <label class="field-label">Total</label>
            <div class="item-readonly" data-test="item-line-total">{{ formatPrice(itemLineTotal) }}</div>
          </div>
          <button type="button" class="btn-primary" data-test="item-add" @click="addItem">+ Adicionar</button>
        </div>
        <p v-if="errors.items" class="field-error">{{ errors.items }}</p>

        <table v-if="form.items.length" class="tabela-itens">
          <thead>
            <tr>
              <th>SKU</th>
              <th>Produto</th>
              <th>Qtd.</th>
              <th>Valor Unit.</th>
              <th>Total</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in form.items" :key="index">
              <td>{{ item.sku }}</td>
              <td>{{ item.productName }}</td>
              <td>{{ item.quantity }}</td>
              <td>{{ formatPrice(item.unitPrice) }}</td>
              <td>{{ formatPrice(item.quantity * item.unitPrice) }}</td>
              <td><button type="button" class="btn-remover" data-test="item-remove" @click="removeItem(index)">✕</button></td>
            </tr>
          </tbody>
        </table>

        <div class="itens-rodape">
          <span v-if="form.items.length" class="itens-resumo">{{ itemsSummary }}</span>
          <div class="totais">
            <div><span>Subtotal</span><span>{{ formatPrice(subtotal) }}</span></div>
            <div>
              <span>Desconto</span>
              <MoneyField v-model="discountModel" test-id="discount" />
            </div>
            <div class="total-final"><span>Total</span><span>{{ formatPrice(total) }}</span></div>
          </div>
        </div>
      </section>

      <p v-if="generalError" class="error-geral">{{ generalError }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" data-test="cancel" @click="cancel">Cancelar</button>
        <button type="button" class="btn-secondary" data-test="save-draft" :disabled="saving" @click="save">Salvar Rascunho</button>
        <button type="submit" class="btn-primary" data-test="save" :disabled="saving">Salvar Pedido</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import MoneyField from '@/components/MoneyField.vue'
import SearchSelect, { type SearchSelectItem } from '@/components/SearchSelect.vue'
import ProductPicker from '@/components/ProductPicker.vue'
import { getSalesOrder, createSalesOrder, updateSalesOrder, type SalesOrderRequest, type SalesOrderItemRequest } from '@/api/salesOrders'
import { listPartners } from '@/api/partners'
import { listSalesReps, type SalesRep } from '@/api/users'
import { listSellableProducts, type SellableProductItem } from '@/api/products'
import { listPriceTables } from '@/api/priceTables'
import { listPaymentMethods } from '@/api/paymentMethods'

const route = useRoute()
const router = useRouter()

const editMode = computed(() => typeof route.params.id === 'string')

interface ItemForm extends SalesOrderItemRequest {
  productName: string
  sku: string
}

interface FormState {
  customerId: string
  salespersonId: string
  orderDate: string
  deliveryDate: string
  discount: number
  items: ItemForm[]
}

function newFormState(): FormState {
  return {
    customerId: '',
    salespersonId: '',
    orderDate: new Date().toISOString().slice(0, 10),
    deliveryDate: '',
    discount: 0,
    items: [],
  }
}

const form = reactive<FormState>(newFormState())

// discount is required (never null), but MoneyField's v-model can emit null
// when the user clears the field -- fall back to 0.
const discountModel = computed({
  get: () => form.discount,
  set: (valor: number | null) => {
    form.discount = valor ?? 0
  },
})
const errors = reactive<{ customerId?: string; salespersonId?: string; items?: string }>({})
const generalError = ref('')
const saving = ref(false)

const customerLabel = ref('')
const customerResults = ref<SearchSelectItem[]>([])
const customerSearchError = ref('')

const salesReps = ref<SalesRep[]>([])
const salespersonLabel = ref('')
const salesRepResults = ref<SearchSelectItem[]>([])

// Presentation-only: neither field is persisted by the sales order API yet.
const priceTableId = ref<string | null>(null)
const priceTableLabel = ref('')
const priceTableResults = ref<SearchSelectItem[]>([])
const priceTableSearchError = ref('')

const paymentTermId = ref<string | null>(null)
const paymentTermLabel = ref('')
const paymentTermResults = ref<SearchSelectItem[]>([])
const paymentTermSearchError = ref('')

const productLabel = ref('')
const productResults = ref<SellableProductItem[]>([])
const productSearchError = ref('')

// A failed lookup and a genuinely empty result set both render an empty list, so
// without this the picker silently shows "Nenhum resultado" when the request
// actually errored (offline, expired session, backend down) -- indistinguishable
// from "this SKU doesn't exist" and impossible to diagnose from the UI.
const SEARCH_FAILED = 'Não foi possível buscar. Verifique sua conexão e tente novamente.'
const itemForm = reactive({ productId: '', productName: '', sku: '', quantity: 1, unitPrice: 0 })

// Previews what the row about to be added is worth. v-model.number yields '' for a
// cleared quantity field, so it is coerced here rather than rendering "R$ NaN".
const itemLineTotal = computed(() => (Number(itemForm.quantity) || 0) * itemForm.unitPrice)

const subtotal = computed(() => form.items.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0))
const total = computed(() => subtotal.value - (Number(form.discount) || 0))
const itemsSummary = computed(() => {
  const units = form.items.reduce((sum, item) => sum + item.quantity, 0)
  const itemLabel = form.items.length === 1 ? 'item' : 'itens'
  const unitLabel = units === 1 ? 'unidade' : 'unidades'
  return `${form.items.length} ${itemLabel} · ${units} ${unitLabel}`
})

function formatPrice(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

async function searchCustomers(query: string) {
  customerSearchError.value = ''
  if (!query.trim()) {
    customerResults.value = []
    return
  }
  try {
    const page = await listPartners({ busca: query, papel: 'CUSTOMER', size: 5 })
    customerResults.value = page.content.map((c) => ({ id: c.id, label: c.tradeName }))
  } catch {
    customerResults.value = []
    customerSearchError.value = SEARCH_FAILED
  }
}

function selectCustomer(item: SearchSelectItem) {
  form.customerId = item.id
  customerLabel.value = item.label
}

function searchSalesReps(query: string) {
  const term = query.trim().toLowerCase()
  salesRepResults.value = salesReps.value
    .filter((r) => !term || r.name.toLowerCase().includes(term))
    .map((r) => ({ id: r.id, label: r.name }))
}

function selectSalesperson(item: SearchSelectItem) {
  form.salespersonId = item.id
  salespersonLabel.value = item.label
}

async function searchPriceTables(query: string) {
  priceTableSearchError.value = ''
  try {
    const page = await listPriceTables({ busca: query || undefined, ativo: true, size: 5 })
    priceTableResults.value = page.content.map((t) => ({ id: t.id, label: t.name }))
  } catch {
    priceTableResults.value = []
    priceTableSearchError.value = SEARCH_FAILED
  }
}

function selectPriceTable(item: SearchSelectItem) {
  priceTableId.value = item.id
  priceTableLabel.value = item.label
}

async function searchPaymentTerms(query: string) {
  paymentTermSearchError.value = ''
  try {
    const page = await listPaymentMethods({ busca: query || undefined, ativo: true, size: 5 })
    paymentTermResults.value = page.content.map((m) => ({ id: m.id, label: m.description }))
  } catch {
    paymentTermResults.value = []
    paymentTermSearchError.value = SEARCH_FAILED
  }
}

function selectPaymentTerm(item: SearchSelectItem) {
  paymentTermId.value = item.id
  paymentTermLabel.value = item.label
}

async function searchProducts(query: string) {
  productSearchError.value = ''
  if (!query.trim()) {
    productResults.value = []
    return
  }
  try {
    // Wider than the other pickers on purpose: the Tamanho/Cor filters are derived
    // from this result set, so too small a page would hide axis values that exist.
    const page = await listSellableProducts({ search: query, size: 50 })
    productResults.value = page.content
  } catch {
    productResults.value = []
    productSearchError.value = SEARCH_FAILED
  }
}

function selectProduct(product: SellableProductItem) {
  itemForm.productId = product.id
  itemForm.productName = product.name
  itemForm.sku = product.sku
  itemForm.unitPrice = product.salePrice
  productLabel.value = `${product.sku} · ${product.name}`
}

function addItem() {
  const quantity = Number(itemForm.quantity) || 0
  if (!itemForm.productId || quantity <= 0) {
    return
  }
  form.items.push({
    productId: itemForm.productId,
    productName: itemForm.productName,
    sku: itemForm.sku,
    quantity,
    unitPrice: itemForm.unitPrice,
  })
  itemForm.productId = ''
  itemForm.productName = ''
  itemForm.sku = ''
  itemForm.quantity = 1
  itemForm.unitPrice = 0
  productLabel.value = ''
}

function removeItem(index: number) {
  form.items.splice(index, 1)
}

onMounted(async () => {
  try {
    salesReps.value = await listSalesReps()
  } catch {
    generalError.value = 'Não foi possível carregar a lista de vendedores.'
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const order = await getSalesOrder(id)
      form.customerId = order.customerId
      customerLabel.value = order.customerName
      form.salespersonId = order.salespersonId
      salespersonLabel.value = order.salespersonName
      form.orderDate = order.orderDate
      form.deliveryDate = order.deliveryDate ?? ''
      form.discount = order.discount
      form.items = order.items.map((item) => ({
        productId: item.productId,
        productName: item.productName,
        sku: '',
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      }))
    } catch {
      generalError.value = 'Não foi possível carregar os dados do pedido.'
    }
  }
})

function validate(): boolean {
  errors.customerId = form.customerId ? undefined : 'Selecione um cliente'
  errors.salespersonId = form.salespersonId ? undefined : 'Selecione um vendedor'
  errors.items = form.items.length > 0 ? undefined : 'Adicione ao menos um item'
  return !errors.customerId && !errors.salespersonId && !errors.items
}

function toPayload(): SalesOrderRequest {
  return {
    customerId: form.customerId,
    salespersonId: form.salespersonId,
    orderDate: form.orderDate,
    deliveryDate: form.deliveryDate || null,
    discount: Number(form.discount) || 0,
    items: form.items.map(({ productId, quantity, unitPrice }) => ({ productId, quantity, unitPrice })),
  }
}

async function save() {
  generalError.value = ''
  if (!validate()) {
    return
  }
  saving.value = true
  try {
    const id = route.params.id
    const payload = toPayload()
    if (typeof id === 'string') {
      await updateSalesOrder(id, payload)
    } else {
      await createSalesOrder(payload)
    }
    router.push({ name: 'pedidos' })
  } catch (err: any) {
    if (err?.response?.status === 403) {
      generalError.value = 'Você não tem permissão para executar esta ação.'
    } else if (err?.response?.status === 400) {
      generalError.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      generalError.value = 'Não foi possível salvar. Tente novamente em instantes.'
    }
  } finally {
    saving.value = false
  }
}

function cancel() {
  router.push({ name: 'pedidos' })
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
  grid-template-columns: 2fr 1fr 1fr;
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

.item-adicionar {
  display: grid;
  grid-template-columns: 1fr 76px 110px 110px auto;
  gap: 8px;
  align-items: end;
  margin-bottom: 10px;
}

.item-product-busca {
  min-width: 0;
}

.item-qtd {
  min-width: 0;
}

/* Derived from the picked product and the quantity -- shown, never typed into. */
.item-readonly {
  height: 34px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 10px;
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  background: var(--pm-bg);
  color: var(--pm-text-muted);
  font-size: 13px;
  font-family: var(--pm-font);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Keeps the button's baseline on the inputs' row, not the labels' row above them. */
.item-adicionar .btn-primary {
  height: 34px;
  padding: 0 16px;
}

.tabela-itens {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-bottom: 12px;
}

.tabela-itens th {
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  background: var(--pm-bg);
  padding: 6px 10px;
}

.tabela-itens td {
  padding: 6px 10px;
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.btn-remover {
  border: none;
  background: none;
  color: var(--pm-error);
  cursor: pointer;
  font-size: 13px;
}

.itens-rodape {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.itens-resumo {
  font-size: 12px;
  color: var(--pm-text-muted);
  font-family: var(--pm-font);
}

.totais {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 260px;
  margin-left: auto;
  font-size: 13px;
  color: var(--pm-text-dark);
}

.totais > div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.totais input {
  width: 100px;
  text-align: right;
}

.total-final {
  font-weight: 700;
  font-size: 14px;
  border-top: 1px solid var(--pm-border-light);
  padding-top: 6px;
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

.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
