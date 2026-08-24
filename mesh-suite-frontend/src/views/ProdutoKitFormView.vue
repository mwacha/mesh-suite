<template>
  <AppShell title="Novo Kit">
    <form class="pm-form" @submit.prevent="salvar">
      <TipoProdutoSelector tipo-atual="KIT" />

      <section class="pm-card">
        <h2>Informações Gerais</h2>
        <TextField v-model="form.nome" label="Nome do Kit" required :error="erros.nome" test-id="nome" />
        <div class="pm-grid pm-grid-2">
          <TextField v-model="form.sku" label="Código SKU" required :error="erros.sku" test-id="sku" />
          <TextField v-model="form.codigoBarras" label="Código de Barra (EAN/GTIN)" placeholder="7891234567890" />
          <SelectField v-model="form.unidadeMedida" label="Unidade de Medida" :options="OPCOES_UNIDADE" />
          <div class="valor-kit">
            <label class="pm-field-label">Valor de Venda do Kit <span class="badge-calculado">calculado automaticamente</span></label>
            <div class="valor-kit-box">
              <span class="valor-kit-numero">{{ formatarMoeda(totalKit) }}</span>
              <span class="valor-kit-bloqueado">bloqueado</span>
            </div>
          </div>
        </div>
        <StatusPillGroup v-model="form.status" label="Status" :options="STATUS_OPCOES" />
        <TextField v-model="form.descricao" label="Descrição" placeholder="Descreva o kit e seus benefícios..." />
      </section>

      <section class="pm-card">
        <div class="secao-header">
          <h2>Composição do Kit</h2>
          <button type="button" class="pm-btn-secondary pm-btn-small" @click="buscaAberta = !buscaAberta">
            + Adicionar Produto
          </button>
        </div>
        <p v-if="erros.itens" class="pm-field-error">{{ erros.itens }}</p>

        <div v-if="buscaAberta" class="busca-produto">
          <div class="busca-linha">
            <input
              v-model="termoBusca"
              class="busca-input"
              placeholder="Digite nome ou SKU do produto..."
              @keyup.enter="buscarProdutosParaAdicionar"
            />
            <button type="button" class="pm-btn-primary pm-btn-small" @click="buscarProdutosParaAdicionar">Buscar</button>
            <button type="button" class="pm-btn-secondary pm-btn-small" @click="buscaAberta = false">Cancelar</button>
          </div>
          <div v-if="resultadosBusca.length" class="busca-resultados">
            <div v-for="produto in resultadosBusca" :key="produto.id" class="busca-resultado-item">
              <div>
                <span class="busca-resultado-nome">{{ produto.nome }}</span>
                <span class="busca-resultado-sku">{{ produto.sku }}</span>
              </div>
              <div class="busca-resultado-acao">
                <span class="busca-resultado-preco">{{ formatarMoeda(produto.precoVenda) }}</span>
                <button type="button" class="pm-btn-primary pm-btn-small" @click="adicionarItem(produto)">Adicionar</button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="itens.length" class="tabela-itens">
          <div class="tabela-cabecalho">
            <span>Produto</span>
            <span>Qtd.</span>
            <span>Vlr. de Venda</span>
            <span>Total Item</span>
            <span></span>
          </div>
          <div v-for="(item, index) in itens" :key="item.produtoId" class="tabela-linha">
            <div>
              <div class="item-nome">{{ item.nome }}</div>
              <div class="item-sku">SKU: {{ item.sku }}</div>
            </div>
            <div class="qtd-stepper">
              <button type="button" @click="decrementarQuantidade(index)">−</button>
              <span>{{ item.quantidade }}</span>
              <button type="button" @click="incrementarQuantidade(index)">+</button>
            </div>
            <div class="item-preco">{{ formatarMoeda(item.precoVenda) }}</div>
            <div class="item-total">{{ formatarMoeda(item.quantidade * item.precoVenda) }}</div>
            <button type="button" class="item-remover" @click="removerItem(index)">×</button>
          </div>
          <div class="tabela-rodape">
            <span>Total do Kit ({{ totalItens }} itens)</span>
            <span class="tabela-rodape-valor">{{ formatarMoeda(totalKit) }}</span>
          </div>
        </div>
        <p v-else class="tabela-vazia">
          Nenhum produto adicionado ao kit ainda.<br />
          Clique em "+ Adicionar Produto" para começar.
        </p>
      </section>

      <p v-if="erroGeral" class="pm-error-geral">{{ erroGeral }}</p>

      <div class="pm-actions">
        <button type="button" class="pm-btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="pm-btn-primary" :disabled="salvando">Salvar Kit</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import AppShell from '@/components/AppShell.vue'
import TextField from '@/components/TextField.vue'
import SelectField from '@/components/SelectField.vue'
import StatusPillGroup from '@/components/StatusPillGroup.vue'
import TipoProdutoSelector from '@/components/produtos/TipoProdutoSelector.vue'
import { useProdutoKitController } from '@/composables/produtos/useProdutoKitController'
import type { UnidadeMedida } from '@/api/produtos'

const UNIDADES: UnidadeMedida[] = ['UN', 'KG', 'G', 'L', 'ML', 'MT', 'CM', 'CX', 'PC', 'PAR', 'DZ']
const OPCOES_UNIDADE = UNIDADES.map((u) => ({ value: u, label: u }))
const STATUS_OPCOES = [
  { value: 'ATIVO', label: 'Ativo', tone: 'ativo' as const },
  { value: 'INATIVO', label: 'Inativo', tone: 'inativo' as const },
]

function formatarMoeda(valor: number): string {
  return `R$ ${valor.toFixed(2).replace('.', ',')}`
}

const {
  form,
  itens,
  erros,
  erroGeral,
  salvando,
  buscaAberta,
  termoBusca,
  resultadosBusca,
  totalKit,
  totalItens,
  buscarProdutosParaAdicionar,
  adicionarItem,
  removerItem,
  incrementarQuantidade,
  decrementarQuantidade,
  salvar,
  cancelar,
} = useProdutoKitController()
</script>

<style scoped>
.secao-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.secao-header h2 {
  margin: 0;
}

.pm-btn-small {
  padding: 6px 12px;
  font-size: 12px;
}

.valor-kit {
  margin-bottom: 10px;
}

.badge-calculado {
  font-size: 10px;
  color: var(--pm-accent);
  background: var(--pm-accent-bg);
  border-radius: 4px;
  padding: 1px 6px;
  margin-left: 4px;
}

.valor-kit-box {
  height: 32px;
  border: 1.5px solid var(--pm-border-light);
  border-radius: 4px;
  background: var(--pm-bg);
  padding: 0 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.valor-kit-numero {
  color: var(--pm-text-dark);
  font-weight: 700;
}

.valor-kit-bloqueado {
  font-size: 10px;
  color: var(--pm-text-muted);
}

.busca-produto {
  margin-bottom: 12px;
  padding: 12px;
  border: 1.5px solid var(--pm-accent);
  border-radius: 6px;
  background: var(--pm-accent-bg);
}

.busca-linha {
  display: flex;
  gap: 8px;
}

.busca-input {
  flex: 1;
  height: 32px;
  border: 1.5px solid var(--pm-border-light);
  border-radius: 4px;
  background: var(--pm-white);
  padding: 0 8px;
  font-size: 13px;
  font-family: var(--pm-font);
}

.busca-resultados {
  margin-top: 8px;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 5px;
  overflow: hidden;
}

.busca-resultado-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-bottom: 1px solid var(--pm-border-light);
  font-size: 12px;
}

.busca-resultado-item:last-child {
  border-bottom: none;
}

.busca-resultado-nome {
  color: var(--pm-text-dark);
  font-weight: 600;
}

.busca-resultado-sku {
  color: var(--pm-text-muted);
  margin-left: 8px;
}

.busca-resultado-acao {
  display: flex;
  gap: 8px;
  align-items: center;
}

.busca-resultado-preco {
  color: var(--pm-accent);
  font-weight: 700;
}

.tabela-itens {
  border: 1px solid var(--pm-border-light);
  border-radius: 6px;
  overflow: hidden;
}

.tabela-cabecalho,
.tabela-linha,
.tabela-rodape {
  display: grid;
  grid-template-columns: 2fr 80px 110px 110px 36px;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
}

.tabela-cabecalho {
  background: var(--pm-bg);
  font-size: 11px;
  font-weight: 700;
  color: var(--pm-text-mid);
  border-bottom: 2px solid var(--pm-border-light);
}

.tabela-linha {
  background: var(--pm-white);
  border-bottom: 1px solid var(--pm-border-light);
}

.item-nome {
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
}

.item-sku {
  font-size: 11px;
  color: var(--pm-text-muted);
}

.qtd-stepper {
  display: flex;
  align-items: center;
  border: 1.5px solid var(--pm-border-light);
  border-radius: 4px;
  overflow: hidden;
  height: 28px;
}

.qtd-stepper button {
  width: 26px;
  height: 100%;
  border: none;
  background: var(--pm-bg);
  cursor: pointer;
  font-size: 14px;
  color: var(--pm-text-mid);
}

.qtd-stepper span {
  flex: 1;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.item-preco {
  font-size: 12px;
  color: var(--pm-text-dark);
}

.item-total {
  height: 28px;
  background: var(--pm-bg);
  border: 1px solid var(--pm-border-light);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.item-remover {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--pm-error-bg);
  cursor: pointer;
  font-size: 14px;
  color: var(--pm-error);
}

.tabela-rodape {
  background: var(--pm-bg);
  border-top: 2px solid var(--pm-border-light);
  grid-template-columns: 1fr 110px 36px;
}

.tabela-rodape span:first-child {
  font-size: 12px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.tabela-rodape-valor {
  font-size: 13px;
  font-weight: 700;
  color: var(--pm-accent);
}

.tabela-vazia {
  text-align: center;
  padding: 28px 0;
  color: var(--pm-text-muted);
  font-size: 13px;
}
</style>
