<template>
  <AppShell :title="modoEdicao ? 'Editar Ordem de Compra' : 'Nova Ordem de Compra'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados da Ordem</h2>
        <div class="grid grid-2">
          <div class="busca-wrapper">
            <label class="field-label">Fornecedor *</label>
            <input
              v-model="fornecedorBusca"
              data-test="fornecedor-busca"
              placeholder="Buscar fornecedor..."
              autocomplete="off"
              @input="buscarFornecedores"
            />
            <p v-if="erros.supplierId" class="field-error">{{ erros.supplierId }}</p>
            <ul v-if="resultadosFornecedores.length" class="dropdown-busca" data-test="fornecedor-resultados">
              <li v-for="f in resultadosFornecedores" :key="f.id" @click="selecionarFornecedor(f)">{{ f.nomeFantasia }}</li>
            </ul>
          </div>
          <div>
            <label class="field-label">Comprador *</label>
            <select v-model="form.buyerId" data-test="comprador">
              <option value="">Selecione...</option>
              <option v-for="c in compradores" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <p v-if="erros.buyerId" class="field-error">{{ erros.buyerId }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Data da Ordem</label>
            <input v-model="form.orderDate" type="date" data-test="data-ordem" />
          </div>
          <div>
            <label class="field-label">Previsão de Entrega</label>
            <input v-model="form.expectedDeliveryDate" type="date" data-test="data-entrega" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Itens</h2>
        <div class="item-adicionar">
          <div class="busca-wrapper item-produto-busca">
            <input
              v-model="produtoBusca"
              placeholder="Buscar produto por nome ou SKU..."
              data-test="produto-busca"
              autocomplete="off"
              @input="buscarProdutos"
            />
            <ul v-if="resultadosProdutos.length" class="dropdown-busca" data-test="produto-resultados">
              <li v-for="p in resultadosProdutos" :key="p.id" @click="selecionarProduto(p)">{{ p.nome }} ({{ p.sku }})</li>
            </ul>
          </div>
          <input
            v-model.number="itemForm.quantity"
            type="number"
            step="0.001"
            min="0.001"
            placeholder="Qtd."
            data-test="item-quantidade"
          />
          <input
            v-model.number="itemForm.unitPrice"
            type="number"
            step="0.01"
            min="0"
            placeholder="Valor unit."
            data-test="item-valor-unitario"
          />
          <button type="button" class="btn-secondary" data-test="item-adicionar" @click="adicionarItem">+ Adicionar</button>
        </div>
        <p v-if="erros.items" class="field-error">{{ erros.items }}</p>

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
              <td>{{ formatarPreco(item.unitPrice) }}</td>
              <td>{{ formatarPreco(item.quantity * item.unitPrice) }}</td>
              <td><button type="button" class="btn-remover" data-test="item-remover" @click="removerItem(index)">✕</button></td>
            </tr>
          </tbody>
        </table>

        <div class="totais">
          <div><span>Subtotal</span><span>{{ formatarPreco(subtotal) }}</span></div>
          <div>
            <span>Desconto</span>
            <input v-model.number="form.discount" type="number" step="0.01" min="0" data-test="desconto" />
          </div>
          <div class="total-final"><span>Total</span><span>{{ formatarPreco(total) }}</span></div>
        </div>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Ordem</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  getPurchaseOrder,
  createPurchaseOrder,
  updatePurchaseOrder,
  type PurchaseOrderRequest,
  type PurchaseOrderItemRequest,
} from '@/api/purchaseOrders'
import { listarParceiros, type ParceiroSummary } from '@/api/parceiros'
import { listBuyers, type Buyer } from '@/api/users'
import { listarProdutos, type ProdutoSummary } from '@/api/produtos'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

interface ItemForm extends PurchaseOrderItemRequest {
  productName: string
}

interface FormState {
  supplierId: string
  buyerId: string
  orderDate: string
  expectedDeliveryDate: string
  discount: number
  items: ItemForm[]
}

function novoFormulario(): FormState {
  return {
    supplierId: '',
    buyerId: '',
    orderDate: new Date().toISOString().slice(0, 10),
    expectedDeliveryDate: '',
    discount: 0,
    items: [],
  }
}

const form = reactive<FormState>(novoFormulario())
const erros = reactive<{ supplierId?: string; buyerId?: string; items?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

const fornecedorBusca = ref('')
const resultadosFornecedores = ref<ParceiroSummary[]>([])
const compradores = ref<Buyer[]>([])

const produtoBusca = ref('')
const resultadosProdutos = ref<ProdutoSummary[]>([])
const itemForm = reactive({ productId: '', productName: '', quantity: 1, unitPrice: 0 })

const subtotal = computed(() => form.items.reduce((soma, item) => soma + item.quantity * item.unitPrice, 0))
const total = computed(() => subtotal.value - (Number(form.discount) || 0))

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

async function buscarFornecedores() {
  if (!fornecedorBusca.value.trim()) {
    resultadosFornecedores.value = []
    return
  }
  try {
    const pagina = await listarParceiros({ busca: fornecedorBusca.value, papel: 'FORNECEDOR', size: 5 })
    resultadosFornecedores.value = pagina.content
  } catch {
    resultadosFornecedores.value = []
  }
}

function selecionarFornecedor(fornecedor: ParceiroSummary) {
  form.supplierId = fornecedor.id
  fornecedorBusca.value = fornecedor.nomeFantasia
  resultadosFornecedores.value = []
}

async function buscarProdutos() {
  if (!produtoBusca.value.trim()) {
    resultadosProdutos.value = []
    return
  }
  try {
    const pagina = await listarProdutos({ busca: produtoBusca.value, size: 5 })
    resultadosProdutos.value = pagina.content
  } catch {
    resultadosProdutos.value = []
  }
}

function selecionarProduto(produto: ProdutoSummary) {
  itemForm.productId = produto.id
  itemForm.productName = produto.nome
  itemForm.unitPrice = produto.precoVenda
  produtoBusca.value = produto.nome
  resultadosProdutos.value = []
}

function adicionarItem() {
  const quantity = Number(itemForm.quantity) || 0
  if (!itemForm.productId || quantity <= 0) {
    return
  }
  form.items.push({
    productId: itemForm.productId,
    productName: itemForm.productName,
    quantity,
    // Normalized here for the same reason paraPayload() normalizes on submit:
    // v-model.number on a blank input yields '' (not 0), and that would flow
    // straight into form.items and later into the request payload untouched.
    unitPrice: Number(itemForm.unitPrice) || 0,
  })
  itemForm.productId = ''
  itemForm.productName = ''
  itemForm.quantity = 1
  itemForm.unitPrice = 0
  produtoBusca.value = ''
}

function removerItem(index: number) {
  form.items.splice(index, 1)
}

onMounted(async () => {
  try {
    compradores.value = await listBuyers()
  } catch {
    erroGeral.value = 'Não foi possível carregar a lista de compradores.'
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const order = await getPurchaseOrder(id)
      form.supplierId = order.supplierId
      fornecedorBusca.value = order.supplierName
      form.buyerId = order.buyerId
      form.orderDate = order.orderDate
      form.expectedDeliveryDate = order.expectedDeliveryDate ?? ''
      form.discount = order.discount
      form.items = order.items.map((item) => ({
        productId: item.productId,
        productName: item.productName,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da ordem de compra.'
    }
  }
})

function validar(): boolean {
  erros.supplierId = form.supplierId ? undefined : 'Selecione um fornecedor'
  erros.buyerId = form.buyerId ? undefined : 'Selecione um comprador'
  erros.items = form.items.length > 0 ? undefined : 'Adicione ao menos um item'
  return !erros.supplierId && !erros.buyerId && !erros.items
}

function paraPayload(): PurchaseOrderRequest {
  return {
    supplierId: form.supplierId,
    buyerId: form.buyerId,
    orderDate: form.orderDate,
    expectedDeliveryDate: form.expectedDeliveryDate || null,
    discount: Number(form.discount) || 0,
    items: form.items.map(({ productId, quantity, unitPrice }) => ({ productId, quantity, unitPrice })),
  }
}

async function salvar() {
  erroGeral.value = ''
  if (!validar()) {
    return
  }
  salvando.value = true
  try {
    const id = route.params.id
    const payload = paraPayload()
    if (typeof id === 'string') {
      await updatePurchaseOrder(id, payload)
    } else {
      await createPurchaseOrder(payload)
    }
    router.push({ name: 'compras' })
  } catch (err: any) {
    if (err?.response?.status === 403) {
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
  router.push({ name: 'compras' })
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
  color: var(--pm-text-mid);
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
