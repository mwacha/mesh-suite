<template>
  <AppShell :title="editMode ? 'Editar Pedido' : 'Novo Pedido'">
    <form class="form" @submit.prevent="save">
      <section class="card">
        <h2>Dados do Pedido</h2>
        <div class="grid grid-2">
          <div class="busca-wrapper">
            <label class="field-label">Cliente *</label>
            <input
              v-model="customerSearch"
              data-test="customer-search"
              placeholder="Buscar cliente..."
              autocomplete="off"
              @input="searchCustomers"
            />
            <p v-if="errors.customerId" class="field-error">{{ errors.customerId }}</p>
            <ul v-if="customerResults.length" class="dropdown-busca" data-test="customer-results">
              <li v-for="c in customerResults" :key="c.id" @click="selectCustomer(c)">{{ c.tradeName }}</li>
            </ul>
          </div>
          <div>
            <label class="field-label">Vendedor *</label>
            <select v-model="form.salespersonId" data-test="salesperson">
              <option value="">Selecione...</option>
              <option v-for="r in salesReps" :key="r.id" :value="r.id">{{ r.name }}</option>
            </select>
            <p v-if="errors.salespersonId" class="field-error">{{ errors.salespersonId }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Data do Pedido</label>
            <input v-model="form.orderDate" type="date" data-test="order-date" />
          </div>
          <div>
            <label class="field-label">Previsão de Entrega</label>
            <input v-model="form.deliveryDate" type="date" data-test="delivery-date" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Itens</h2>
        <div class="item-adicionar">
          <div class="busca-wrapper item-produto-busca">
            <input
              v-model="productSearch"
              placeholder="Buscar produto por nome ou SKU..."
              data-test="product-search"
              autocomplete="off"
              @input="searchProducts"
            />
            <ul v-if="productResults.length" class="dropdown-busca" data-test="product-results">
              <li v-for="p in productResults" :key="p.id" @click="selectProduct(p)">{{ p.name }} ({{ p.sku }})</li>
            </ul>
          </div>
          <input
            v-model.number="itemForm.quantity"
            type="number"
            step="0.001"
            min="0.001"
            placeholder="Qtd."
            data-test="item-quantity"
          />
          <input
            v-model.number="itemForm.unitPrice"
            type="number"
            step="0.01"
            min="0"
            placeholder="Valor unit."
            data-test="item-unit-price"
          />
          <button type="button" class="btn-secondary" data-test="item-add" @click="addItem">+ Adicionar</button>
        </div>
        <p v-if="errors.items" class="field-error">{{ errors.items }}</p>

        <table v-if="form.items.length" class="tabela-itens">
          <thead>
            <tr>
              <th>Produto</th>
              <th>Qtd.</th>
              <th>Valor Unit.</th>
              <th>Total</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in form.items" :key="index">
              <td>{{ item.productName }}</td>
              <td>{{ item.quantity }}</td>
              <td>{{ formatPrice(item.unitPrice) }}</td>
              <td>{{ formatPrice(item.quantity * item.unitPrice) }}</td>
              <td><button type="button" class="btn-remover" data-test="item-remove" @click="removeItem(index)">✕</button></td>
            </tr>
          </tbody>
        </table>

        <div class="totais">
          <div><span>Subtotal</span><span>{{ formatPrice(subtotal) }}</span></div>
          <div>
            <span>Desconto</span>
            <input v-model.number="form.discount" type="number" step="0.01" min="0" data-test="discount" />
          </div>
          <div class="total-final"><span>Total</span><span>{{ formatPrice(total) }}</span></div>
        </div>
      </section>

      <p v-if="generalError" class="error-geral">{{ generalError }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancel">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="saving">Salvar Pedido</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { getSalesOrder, createSalesOrder, updateSalesOrder, type SalesOrderRequest, type SalesOrderItemRequest } from '@/api/salesOrders'
import { listPartners, type PartnerListItem } from '@/api/partners'
import { listSalesReps, type SalesRep } from '@/api/users'
import { listProducts, type ProductListItem } from '@/api/products'

const route = useRoute()
const router = useRouter()

const editMode = computed(() => typeof route.params.id === 'string')

interface ItemForm extends SalesOrderItemRequest {
  productName: string
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
const errors = reactive<{ customerId?: string; salespersonId?: string; items?: string }>({})
const generalError = ref('')
const saving = ref(false)

const customerSearch = ref('')
const customerResults = ref<PartnerListItem[]>([])
const salesReps = ref<SalesRep[]>([])

const productSearch = ref('')
const productResults = ref<ProductListItem[]>([])
const itemForm = reactive({ productId: '', productName: '', quantity: 1, unitPrice: 0 })

const subtotal = computed(() => form.items.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0))
const total = computed(() => subtotal.value - (Number(form.discount) || 0))

function formatPrice(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

async function searchCustomers() {
  if (!customerSearch.value.trim()) {
    customerResults.value = []
    return
  }
  try {
    const page = await listPartners({ busca: customerSearch.value, papel: 'CUSTOMER', size: 5 })
    customerResults.value = page.content
  } catch {
    customerResults.value = []
  }
}

function selectCustomer(customer: PartnerListItem) {
  form.customerId = customer.id
  customerSearch.value = customer.tradeName
  customerResults.value = []
}

async function searchProducts() {
  if (!productSearch.value.trim()) {
    productResults.value = []
    return
  }
  try {
    const page = await listProducts({ busca: productSearch.value, size: 5 })
    productResults.value = page.content
  } catch {
    productResults.value = []
  }
}

function selectProduct(product: ProductListItem) {
  itemForm.productId = product.id
  itemForm.productName = product.name
  itemForm.unitPrice = product.salePrice
  productSearch.value = product.name
  productResults.value = []
}

function addItem() {
  const quantity = Number(itemForm.quantity) || 0
  if (!itemForm.productId || quantity <= 0) {
    return
  }
  form.items.push({
    productId: itemForm.productId,
    productName: itemForm.productName,
    quantity,
    // Normalized here for the same reason toPayload() normalizes on submit:
    // v-model.number on a blank input yields '' (not 0), and that would flow
    // straight into form.items and later into the request payload untouched.
    unitPrice: Number(itemForm.unitPrice) || 0,
  })
  itemForm.productId = ''
  itemForm.productName = ''
  itemForm.quantity = 1
  itemForm.unitPrice = 0
  productSearch.value = ''
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
      customerSearch.value = order.customerName
      form.salespersonId = order.salespersonId
      form.orderDate = order.orderDate
      form.deliveryDate = order.deliveryDate ?? ''
      form.discount = order.discount
      form.items = order.items.map((item) => ({
        productId: item.productId,
        productName: item.productName,
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

.busca-wrapper {
  position: relative;
}

.dropdown-busca {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin: 4px 0 0;
  padding: 4px 0;
  list-style: none;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 28px rgba(0, 0, 0, 0.12);
  z-index: 10;
  max-height: 200px;
  overflow-y: auto;
}

.dropdown-busca li {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--pm-text-dark);
  cursor: pointer;
}

.item-adicionar {
  display: grid;
  grid-template-columns: 1fr 100px 120px auto;
  gap: 8px;
  align-items: start;
  margin-bottom: 10px;
}

.item-produto-busca {
  min-width: 0;
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
</style>
