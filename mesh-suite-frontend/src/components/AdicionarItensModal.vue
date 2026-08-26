<template>
  <SlideOver :title="title" @close="$emit('close')">
    <div class="modal-busca">
      <input
        v-model="busca"
        class="modal-busca-input"
        placeholder="Buscar produto por nome ou código..."
        data-test="modal-busca"
        autocomplete="off"
        @input="carregar(0)"
      />
    </div>

    <ListCard title="Produtos" :stats="stats">
      <div class="produtos-grid">
        <div class="produtos-grid-header">
          <div class="produtos-grid-col">Nome do item</div>
          <div class="produtos-grid-col">Código</div>
          <div class="produtos-grid-col produtos-grid-col-preco">Preço cadastrado</div>
          <div class="produtos-grid-col"></div>
        </div>

        <div v-for="produto in pagina.content" :key="produto.id" class="produtos-grid-row" :data-test="`modal-row-${produto.id}`">
          <div class="produtos-grid-cell produtos-grid-cell-nome">{{ produto.name }}</div>
          <div class="produtos-grid-cell">{{ produto.sku }}</div>
          <div class="produtos-grid-cell produtos-grid-cell-preco">{{ formatarPreco(produto.salePrice) }}</div>
          <div class="produtos-grid-cell produtos-grid-cell-acao">
            <button
              v-if="jaAdicionado(produto.id)"
              type="button"
              class="btn-remover-produto"
              :data-test="`modal-remover-${produto.id}`"
              @click="$emit('remove', produto.id)"
            >
              × Remover
            </button>
            <button
              v-else
              type="button"
              class="btn-adicionar-produto"
              :data-test="`modal-adicionar-${produto.id}`"
              @click="$emit('add', produto)"
            >
              + Adicionar
            </button>
          </div>
        </div>

        <div v-if="!carregando && pagina.content.length === 0" class="produtos-grid-empty">
          Nenhum produto encontrado.
        </div>
      </div>
    </ListCard>

    <Pagination
      :number="pagina.number"
      :total-pages="pagina.totalPages"
      :total-elements="pagina.totalElements"
      :size="pagina.size"
      @update:page="carregar"
      @update:size="onSizeChange"
    />

    <template #footer>
      <button type="button" class="btn-primary" data-test="modal-concluir" @click="$emit('close')">Concluir</button>
    </template>
  </SlideOver>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import SlideOver from './SlideOver.vue'
import ListCard, { type ListCardStat } from './ListCard.vue'
import Pagination from './Pagination.vue'
import { listProducts, type ProductListItem, type Page } from '@/api/products'

const props = withDefaults(
  defineProps<{ itensAdicionadosIds: string[]; title?: string }>(),
  { title: 'Adicionar produtos à tabela de preços' },
)
defineEmits<{ add: [produto: ProductListItem]; remove: [produtoId: string]; close: [] }>()

const busca = ref('')
const pagina = ref<Page<ProductListItem>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 })
const carregando = ref(false)

const adicionadosSet = computed(() => new Set(props.itensAdicionadosIds))

function jaAdicionado(id: string) {
  return adicionadosSet.value.has(id)
}

function formatarPreco(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

const stats = computed<ListCardStat[]>(() => [
  { value: pagina.value.totalElements, label: 'Total', color: 'dark' },
  { value: props.itensAdicionadosIds.length, label: 'Adicionados', color: 'green' },
])

// Every keystroke fires a new request with no debounce, so responses can come back
// out of order (e.g. the broader query for "P" resolving after the narrower "P0001").
// A sequence guard discards any response that isn't for the most recently issued
// request, so a slow, stale response can never clobber a newer, more specific one.
let requestSeq = 0

async function carregar(page: number) {
  const seq = ++requestSeq
  carregando.value = true
  try {
    const resultado = await listProducts({
      search: busca.value || undefined,
      status: 'ACTIVE',
      page,
      size: pagina.value.size,
    })
    if (seq === requestSeq) {
      pagina.value = resultado
    }
  } finally {
    if (seq === requestSeq) {
      carregando.value = false
    }
  }
}

function onSizeChange(novoSize: number) {
  pagina.value.size = novoSize
  carregar(0)
}

onMounted(() => carregar(0))
</script>

<style scoped>
.modal-busca {
  margin-bottom: 10px;
}

.modal-busca-input {
  width: 100%;
  box-sizing: border-box;
  height: 36px;
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  background: var(--pm-white);
  padding: 0 12px;
  font-size: 13px;
  font-family: var(--pm-font);
  color: var(--pm-text-dark);
}

.produtos-grid {
  font-family: var(--pm-font);
  font-size: 12px;
}

.produtos-grid-header,
.produtos-grid-row {
  display: grid;
  grid-template-columns: 1fr 110px 130px 120px;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.produtos-grid-header {
  background: var(--pm-bg);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--pm-text-mid);
  padding: 12px;
}

.produtos-grid-col-preco {
  text-align: right;
}

.produtos-grid-row {
  border-top: 1px solid var(--pm-border-light);
  color: var(--pm-text-dark);
}

.produtos-grid-cell-nome {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.produtos-grid-cell-preco {
  text-align: right;
}

.produtos-grid-cell-acao {
  display: flex;
  justify-content: flex-end;
}

.produtos-grid-empty {
  padding: 28px 0;
  text-align: center;
  color: var(--pm-text-muted);
  font-size: 13px;
}

.btn-adicionar-produto,
.btn-remover-produto {
  height: 26px;
  padding: 0 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  font-family: var(--pm-font);
  cursor: pointer;
  white-space: nowrap;
}

.btn-adicionar-produto {
  border: 1px solid var(--pm-accent);
  background: var(--pm-accent);
  color: var(--pm-white);
}

.btn-remover-produto {
  border: 1px solid var(--pm-error-bg);
  background: var(--pm-error-bg);
  color: var(--pm-error);
}

.btn-primary {
  border-radius: 8px;
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--pm-font);
  cursor: pointer;
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
}
</style>
