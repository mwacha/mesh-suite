<template>
  <AppShell title="Produtos">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="toolbar">
      <input
        v-model="filtros.busca"
        class="busca"
        placeholder="Buscar produto por nome ou SKU..."
        data-test="busca"
        @input="carregar(0)"
      />
      <select v-model="filtros.status" @change="carregar(0)">
        <option value="">Status</option>
        <option value="ATIVO">Ativo</option>
        <option value="INATIVO">Inativo</option>
      </select>
      <button type="button" class="btn-primary" data-test="novo-produto" @click="novoProduto">+ Novo Produto</button>
    </div>

    <div v-if="resumo" class="resumo">
      <span class="resumo-item">{{ resumo.total }} Total</span>
      <span class="resumo-item resumo-ativo">{{ resumo.ativos }} Ativos</span>
      <span class="resumo-item resumo-inativo">{{ resumo.inativos }} Inativos</span>
    </div>

    <section class="card">
      <table class="tabela">
        <thead>
          <tr>
            <th>Código</th>
            <th>Produto</th>
            <th>Marca</th>
            <th>Preço de Venda</th>
            <th>Estoque</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="produto in pagina.content" :key="produto.id">
            <td>{{ produto.sku }}</td>
            <td>{{ produto.nome }}</td>
            <td>{{ produto.marca }}</td>
            <td>{{ formatarPreco(produto.precoVenda) }}</td>
            <td>{{ produto.quantidadeEstoque }}</td>
            <td><span class="badge" :class="`badge-${produto.status}`">{{ statusLabel(produto.status) }}</span></td>
            <td class="acoes">
              <button
                type="button"
                class="btn-acoes"
                data-test="btn-acoes"
                @click="toggleAcoes(produto.id, $event)"
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
        v-if="produtoAcoesAtual"
        class="dropdown-acoes"
        :style="{ top: posicaoDropdown.top, left: posicaoDropdown.left }"
      >
        <div data-test="acao-editar" @click="editarProduto(produtoAcoesAtual.id)">Editar</div>
        <div @click="alternarStatus(produtoAcoesAtual)">
          {{ produtoAcoesAtual.status === 'INATIVO' ? 'Ativar' : 'Inativar' }}
        </div>
        <div class="acao-excluir" @click="excluir(produtoAcoesAtual)">Excluir</div>
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
  listarProdutos,
  buscarResumoProdutos,
  atualizarStatusProduto,
  excluirProduto,
  type ProdutoSummary,
  type ProdutoResumo,
  type Page as ApiPage,
  type StatusProduto,
} from '@/api/produtos'

const router = useRouter()

const filtros = reactive({ busca: '', status: '' })
const pagina = ref<ApiPage<ProdutoSummary>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const resumo = ref<ProdutoResumo | null>(null)
const acoesAbertas = ref<string | null>(null)
const posicaoDropdown = ref({ top: '0px', left: '0px' })
const erro = ref('')

const produtoAcoesAtual = computed(() =>
  pagina.value.content.find((p) => p.id === acoesAbertas.value) ?? null,
)

function statusLabel(status: StatusProduto) {
  return { ATIVO: 'Ativo', INATIVO: 'Inativo' }[status]
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

async function carregar(page: number) {
  erro.value = ''
  try {
    pagina.value = await listarProdutos({
      busca: filtros.busca || undefined,
      status: (filtros.status || undefined) as StatusProduto | undefined,
      page,
      size: pagina.value.size,
    })
  } catch {
    erro.value = 'Não foi possível carregar a lista de produtos.'
  }
}

async function carregarResumo() {
  erro.value = ''
  try {
    resumo.value = await buscarResumoProdutos()
  } catch {
    erro.value = 'Não foi possível carregar o resumo de produtos.'
  }
}

function novoProduto() {
  router.push({ name: 'produtos-novo' })
}

function editarProduto(id: string) {
  acoesAbertas.value = null
  router.push({ name: 'produtos-editar', params: { id } })
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

async function alternarStatus(produto: ProdutoSummary) {
  acoesAbertas.value = null
  erro.value = ''
  const novoStatus = produto.status === 'INATIVO' ? 'ATIVO' : 'INATIVO'
  try {
    await atualizarStatusProduto(produto.id, novoStatus)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível atualizar o status.'
  }
}

async function excluir(produto: ProdutoSummary) {
  acoesAbertas.value = null
  if (!confirm(`Excluir o produto "${produto.nome}"?`)) {
    return
  }
  erro.value = ''
  try {
    await excluirProduto(produto.id)
    await Promise.all([carregar(pagina.value.number), carregarResumo()])
  } catch {
    erro.value = 'Não foi possível excluir o produto.'
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

.resumo-inativo {
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

.badge-INATIVO {
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
