<template>
  <AppShell :title="modoEdicao ? 'Editar Pedido' : 'Novo Pedido'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados do Pedido</h2>
        <div class="grid grid-2">
          <div class="busca-wrapper">
            <label class="field-label">Cliente *</label>
            <input
              v-model="clienteBusca"
              data-test="cliente-busca"
              placeholder="Buscar cliente..."
              autocomplete="off"
              @input="buscarClientes"
            />
            <p v-if="erros.clienteId" class="field-error">{{ erros.clienteId }}</p>
            <ul v-if="resultadosClientes.length" class="dropdown-busca" data-test="cliente-resultados">
              <li v-for="c in resultadosClientes" :key="c.id" @click="selecionarCliente(c)">{{ c.tradeName }}</li>
            </ul>
          </div>
          <div>
            <label class="field-label">Vendedor *</label>
            <select v-model="form.vendedorId" data-test="vendedor">
              <option value="">Selecione...</option>
              <option v-for="r in representantes" :key="r.id" :value="r.id">{{ r.name }}</option>
            </select>
            <p v-if="erros.vendedorId" class="field-error">{{ erros.vendedorId }}</p>
          </div>
        </div>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Data do Pedido</label>
            <input v-model="form.dataPedido" type="date" data-test="data-pedido" />
          </div>
          <div>
            <label class="field-label">Previsão de Entrega</label>
            <input v-model="form.dataEntrega" type="date" data-test="data-entrega" />
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
              <li v-for="p in resultadosProdutos" :key="p.id" @click="selecionarProduto(p)">{{ p.name }} ({{ p.sku }})</li>
            </ul>
          </div>
          <input
            v-model.number="itemForm.quantidade"
            type="number"
            step="0.001"
            min="0.001"
            placeholder="Qtd."
            data-test="item-quantidade"
          />
          <input
            v-model.number="itemForm.valorUnitario"
            type="number"
            step="0.01"
            min="0"
            placeholder="Valor unit."
            data-test="item-valor-unitario"
          />
          <button type="button" class="btn-secondary" data-test="item-adicionar" @click="adicionarItem">+ Adicionar</button>
        </div>
        <p v-if="erros.itens" class="field-error">{{ erros.itens }}</p>

        <table v-if="form.itens.length" class="tabela-itens">
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
            <tr v-for="(item, index) in form.itens" :key="index">
              <td>{{ item.produtoNome }}</td>
              <td>{{ item.quantidade }}</td>
              <td>{{ formatarPreco(item.valorUnitario) }}</td>
              <td>{{ formatarPreco(item.quantidade * item.valorUnitario) }}</td>
              <td><button type="button" class="btn-remover" data-test="item-remover" @click="removerItem(index)">✕</button></td>
            </tr>
          </tbody>
        </table>

        <div class="totais">
          <div><span>Subtotal</span><span>{{ formatarPreco(subtotal) }}</span></div>
          <div>
            <span>Desconto</span>
            <input v-model.number="form.desconto" type="number" step="0.01" min="0" data-test="desconto" />
          </div>
          <div class="total-final"><span>Total</span><span>{{ formatarPreco(total) }}</span></div>
        </div>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Pedido</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { buscarPedido, criarPedido, atualizarPedido, type PedidoRequest, type ItemPedidoRequest } from '@/api/pedidos'
import { listPartners, type PartnerListItem } from '@/api/partners'
import { listSalesReps, type SalesRep } from '@/api/users'
import { listProducts, type ProductListItem } from '@/api/products'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

interface ItemForm extends ItemPedidoRequest {
  produtoNome: string
}

interface FormState {
  clienteId: string
  vendedorId: string
  dataPedido: string
  dataEntrega: string
  desconto: number
  itens: ItemForm[]
}

function novoFormulario(): FormState {
  return {
    clienteId: '',
    vendedorId: '',
    dataPedido: new Date().toISOString().slice(0, 10),
    dataEntrega: '',
    desconto: 0,
    itens: [],
  }
}

const form = reactive<FormState>(novoFormulario())
const erros = reactive<{ clienteId?: string; vendedorId?: string; itens?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

const clienteBusca = ref('')
const resultadosClientes = ref<PartnerListItem[]>([])
const representantes = ref<SalesRep[]>([])

const produtoBusca = ref('')
const resultadosProdutos = ref<ProductListItem[]>([])
const itemForm = reactive({ produtoId: '', produtoNome: '', quantidade: 1, valorUnitario: 0 })

const subtotal = computed(() => form.itens.reduce((soma, item) => soma + item.quantidade * item.valorUnitario, 0))
const total = computed(() => subtotal.value - (Number(form.desconto) || 0))

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

async function buscarClientes() {
  if (!clienteBusca.value.trim()) {
    resultadosClientes.value = []
    return
  }
  try {
    const pagina = await listPartners({ busca: clienteBusca.value, papel: 'CUSTOMER', size: 5 })
    resultadosClientes.value = pagina.content
  } catch {
    resultadosClientes.value = []
  }
}

function selecionarCliente(cliente: PartnerListItem) {
  form.clienteId = cliente.id
  clienteBusca.value = cliente.tradeName
  resultadosClientes.value = []
}

async function buscarProdutos() {
  if (!produtoBusca.value.trim()) {
    resultadosProdutos.value = []
    return
  }
  try {
    const pagina = await listProducts({ busca: produtoBusca.value, size: 5 })
    resultadosProdutos.value = pagina.content
  } catch {
    resultadosProdutos.value = []
  }
}

function selecionarProduto(produto: ProductListItem) {
  itemForm.produtoId = produto.id
  itemForm.produtoNome = produto.name
  itemForm.valorUnitario = produto.salePrice
  produtoBusca.value = produto.name
  resultadosProdutos.value = []
}

function adicionarItem() {
  const quantidade = Number(itemForm.quantidade) || 0
  if (!itemForm.produtoId || quantidade <= 0) {
    return
  }
  form.itens.push({
    produtoId: itemForm.produtoId,
    produtoNome: itemForm.produtoNome,
    quantidade,
    // Normalized here for the same reason paraPayload() normalizes on submit:
    // v-model.number on a blank input yields '' (not 0), and that would flow
    // straight into form.itens and later into the request payload untouched.
    valorUnitario: Number(itemForm.valorUnitario) || 0,
  })
  itemForm.produtoId = ''
  itemForm.produtoNome = ''
  itemForm.quantidade = 1
  itemForm.valorUnitario = 0
  produtoBusca.value = ''
}

function removerItem(index: number) {
  form.itens.splice(index, 1)
}

onMounted(async () => {
  try {
    representantes.value = await listSalesReps()
  } catch {
    erroGeral.value = 'Não foi possível carregar a lista de vendedores.'
  }

  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const pedido = await buscarPedido(id)
      form.clienteId = pedido.clienteId
      clienteBusca.value = pedido.clienteNome
      form.vendedorId = pedido.vendedorId
      form.dataPedido = pedido.dataPedido
      form.dataEntrega = pedido.dataEntrega ?? ''
      form.desconto = pedido.desconto
      form.itens = pedido.itens.map((item) => ({
        produtoId: item.produtoId,
        produtoNome: item.produtoNome,
        quantidade: item.quantidade,
        valorUnitario: item.valorUnitario,
      }))
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do pedido.'
    }
  }
})

function validar(): boolean {
  erros.clienteId = form.clienteId ? undefined : 'Selecione um cliente'
  erros.vendedorId = form.vendedorId ? undefined : 'Selecione um vendedor'
  erros.itens = form.itens.length > 0 ? undefined : 'Adicione ao menos um item'
  return !erros.clienteId && !erros.vendedorId && !erros.itens
}

function paraPayload(): PedidoRequest {
  return {
    clienteId: form.clienteId,
    vendedorId: form.vendedorId,
    dataPedido: form.dataPedido,
    dataEntrega: form.dataEntrega || null,
    desconto: Number(form.desconto) || 0,
    itens: form.itens.map(({ produtoId, quantidade, valorUnitario }) => ({ produtoId, quantidade, valorUnitario })),
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
      await atualizarPedido(id, payload)
    } else {
      await criarPedido(payload)
    }
    router.push({ name: 'pedidos' })
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
