<template>
  <AppShell :title="modoEdicao ? 'Editar Produto' : 'Novo Produto'">
    <form class="pm-form" @submit.prevent="salvar">
      <TipoProdutoSelector v-if="!modoEdicao" tipo-atual="VARIACAO" />

      <section class="pm-card">
        <h2>Informações Gerais</h2>
        <TextField v-model="form.nome" label="Nome do Produto" required :error="erros.nome" test-id="nome" />
        <div class="pm-grid pm-grid-2">
          <TextField v-model="form.sku" label="Código SKU" required :error="erros.sku" test-id="sku" />
          <TextField label="Código de Barra (EAN/GTIN)" model-value="" disabled placeholder="por variante" />
          <MarcaCategoriaFields v-model:marca="form.marca" v-model:categoria="form.categoria" />
          <NumberField
            v-model="form.precoVenda"
            label="Preço de Venda"
            step="0.01"
            :min="0"
            :error="erros.precoVenda"
            test-id="preco-venda"
          />
          <NumberField label="Preço de Custo" :model-value="null" disabled placeholder="por variante" />
        </div>
        <StatusPillGroup v-model="form.status" label="Status" :options="STATUS_OPCOES" />
        <TextField v-model="form.descricao" label="Descrição" placeholder="Descreva o produto..." />
      </section>

      <section class="pm-card">
        <h2>Estoque</h2>
        <div class="estoque-unidade">
          <SelectField v-model="form.unidadeMedida" label="Unidade de Medida" :options="OPCOES_UNIDADE" />
        </div>
        <p class="ajuda">Qtd., mínimo e máximo são definidos em cada variante.</p>
      </section>

      <section class="pm-card">
        <div class="secao-header">
          <h2>Tipos de Variação</h2>
          <span class="ajuda">Os valores definem as combinações da tabela abaixo</span>
        </div>
        <p v-if="erros.tiposVariacao" class="pm-field-error">{{ erros.tiposVariacao }}</p>

        <div v-for="tipo in tiposVariacao" :key="tipo.id" class="tipo-variacao">
          <div class="tipo-variacao-header">
            <span class="tipo-variacao-nome">{{ tipo.nome }}</span>
            <button type="button" class="remover-link" @click="confirmarRemocaoTipo(tipo)">Remover tipo</button>
          </div>
          <div class="tipo-variacao-valores">
            <span v-for="valor in tipo.valores" :key="valor" class="chip">
              {{ valor }}
              <button type="button" class="chip-remover" @click="confirmarRemocaoValor(tipo, valor)">✕</button>
            </span>
            <template v-if="valorAbertoPara === tipo.id">
              <input v-model="novoValor" class="chip-input" placeholder="Ex: XG" autofocus @keyup.enter="confirmarNovoValor(tipo)" />
              <button type="button" class="chip-acao" @click="confirmarNovoValor(tipo)">✓</button>
              <button type="button" class="chip-acao" @click="cancelarNovoValor">✕</button>
            </template>
            <button v-else type="button" class="chip-adicionar" @click="abrirNovoValor(tipo.id)">+ Valor</button>
          </div>
        </div>

        <div v-if="novoTipoAberto" class="novo-tipo">
          <div class="novo-tipo-titulo">Novo Tipo de Variação</div>
          <TextField v-model="nomeNovoTipo" label="Nome do tipo" placeholder="Ex: Material, Voltagem, Estilo..." />
          <div class="pm-actions">
            <button type="button" class="pm-btn-secondary pm-btn-small" @click="cancelarNovoTipo">Cancelar</button>
            <button type="button" class="pm-btn-primary pm-btn-small" @click="confirmarNovoTipo">Confirmar Tipo</button>
          </div>
        </div>
        <button v-else type="button" class="adicionar-tipo" @click="novoTipoAberto = true">+ Adicionar Tipo de Variação</button>
      </section>

      <section class="pm-card tabela-variantes">
        <div class="secao-header">
          <h2>Variantes Geradas</h2>
          <span class="badge-contagem">{{ variantesGeradas.length }} combinações</span>
        </div>
        <p v-if="erros.variantes" class="pm-field-error">{{ erros.variantes }}</p>
        <table v-if="variantesGeradas.length" class="tabela">
          <thead>
            <tr>
              <th>Variante</th>
              <th>SKU</th>
              <th>Código de Barra</th>
              <th>Valor de Venda</th>
              <th>Estoque Disponível</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="variante in variantesGeradas" :key="variante.combinacao.join('|')">
              <td>
                <span v-for="valor in variante.combinacao" :key="valor" class="chip chip--tabela">{{ valor }}</span>
              </td>
              <td>{{ variante.sku }}</td>
              <td>{{ variante.codigoBarras || '—' }}</td>
              <td>{{ formatarMoeda(variante.precoVenda) }}</td>
              <td>{{ variante.quantidadeEstoque }}</td>
              <td>
                <button type="button" class="editar-link" @click="abrirEdicaoVariante(variante)">✏️ Editar</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="tabela-vazia">Adicione tipos de variação e valores para gerar as combinações.</p>
      </section>

      <p v-if="erroGeral" class="pm-error-geral">{{ erroGeral }}</p>

      <div class="pm-actions">
        <button type="button" class="pm-btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="pm-btn-primary" :disabled="salvando">Salvar Produto</button>
      </div>
    </form>

    <div v-if="confirmacao" class="overlay" @click="cancelarConfirmacao">
      <div class="dialogo" @click.stop>
        <div class="dialogo-corpo">
          <div class="dialogo-titulo">Confirmar remoção</div>
          <div class="dialogo-mensagem">{{ confirmacao.mensagem }}</div>
        </div>
        <div class="dialogo-acoes">
          <button type="button" class="pm-btn-secondary pm-btn-small" @click="cancelarConfirmacao">Cancelar</button>
          <button type="button" class="pm-btn-danger pm-btn-small" @click="confirmar">Remover</button>
        </div>
      </div>
    </div>

    <div v-if="editingVariant" class="overlay" @click="fecharEdicaoVariante">
      <div class="painel-variante" @click.stop>
        <div class="painel-header">
          <div>
            <div class="painel-titulo">Editar Variante</div>
            <div class="painel-combo">
              <span v-for="valor in editingVariant.combinacao" :key="valor" class="chip chip--painel">{{ valor }}</span>
            </div>
          </div>
          <button type="button" class="painel-fechar" @click="fecharEdicaoVariante">✕</button>
        </div>
        <div class="painel-conteudo">
          <section class="pm-card">
            <h2>Identificação</h2>
            <TextField v-model="editingVariant.sku" label="Código SKU" />
            <TextField v-model="editingVariant.codigoBarras" label="Código de Barra (EAN/GTIN)" />
          </section>
          <section class="pm-card">
            <h2>Preços</h2>
            <div class="pm-grid pm-grid-2">
              <NumberField v-model="editingVariant.precoVenda" label="Preço de Venda" step="0.01" :min="0" />
              <NumberField v-model="editingVariant.precoCusto" label="Preço de Custo" step="0.01" :min="0" />
            </div>
          </section>
          <EstoqueCard
            :mostrar-unidade="false"
            v-model:quantidade-estoque="editingVariant.quantidadeEstoque"
            v-model:unidade-medida="unidadeInerte"
            v-model:estoque-minimo="editingVariant.estoqueMinimo"
            v-model:estoque-maximo="editingVariant.estoqueMaximo"
          />
          <PesosDimensoesCard
            v-model:peso="editingVariant.peso"
            v-model:comprimento="editingVariant.comprimento"
            v-model:largura="editingVariant.largura"
            v-model:altura="editingVariant.altura"
          />
        </div>
        <div class="painel-footer">
          <button type="button" class="pm-btn-secondary" @click="fecharEdicaoVariante">Cancelar</button>
          <button type="button" class="pm-btn-primary" @click="fecharEdicaoVariante">Salvar Variante</button>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AppShell from '@/components/AppShell.vue'
import TextField from '@/components/TextField.vue'
import NumberField from '@/components/NumberField.vue'
import SelectField from '@/components/SelectField.vue'
import StatusPillGroup from '@/components/StatusPillGroup.vue'
import TipoProdutoSelector from '@/components/produtos/TipoProdutoSelector.vue'
import MarcaCategoriaFields from '@/components/produtos/MarcaCategoriaFields.vue'
import EstoqueCard from '@/components/produtos/EstoqueCard.vue'
import PesosDimensoesCard from '@/components/produtos/PesosDimensoesCard.vue'
import { useProdutoVariacaoController, type TipoVariacaoLocal } from '@/composables/produtos/useProdutoVariacaoController'
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
  modoEdicao,
  form,
  erros,
  erroGeral,
  salvando,
  tiposVariacao,
  variantesGeradas,
  adicionarTipo,
  confirmarRemocaoTipo,
  adicionarValor,
  confirmarRemocaoValor,
  confirmacao,
  confirmar,
  cancelarConfirmacao,
  editingVariant,
  abrirEdicaoVariante,
  fecharEdicaoVariante,
  salvar,
  cancelar,
} = useProdutoVariacaoController()

// EstoqueCard always exposes a unidade v-model; the variant panel hides that
// field (mostrarUnidade=false) since unidade is set once at the product
// level, so this binding target is never rendered or read.
const unidadeInerte = ref<UnidadeMedida>('UN')

// Transient "which inline editor is open" UI state -- kept in the view
// rather than the controller since it has no bearing on the saved payload.
const novoTipoAberto = ref(false)
const nomeNovoTipo = ref('')
const valorAbertoPara = ref<string | null>(null)
const novoValor = ref('')

function confirmarNovoTipo() {
  adicionarTipo(nomeNovoTipo.value)
  nomeNovoTipo.value = ''
  novoTipoAberto.value = false
}

function cancelarNovoTipo() {
  nomeNovoTipo.value = ''
  novoTipoAberto.value = false
}

function abrirNovoValor(tipoId: string) {
  valorAbertoPara.value = tipoId
  novoValor.value = ''
}

function confirmarNovoValor(tipo: TipoVariacaoLocal) {
  adicionarValor(tipo.id, novoValor.value)
  novoValor.value = ''
  valorAbertoPara.value = null
}

function cancelarNovoValor() {
  novoValor.value = ''
  valorAbertoPara.value = null
}
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

.ajuda {
  font-size: 11px;
  color: var(--pm-text-muted);
}

.estoque-unidade {
  max-width: 220px;
}

.pm-btn-small {
  padding: 6px 12px;
  font-size: 12px;
}

.pm-btn-danger {
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  font-family: var(--pm-font);
  cursor: pointer;
  border: none;
  background: var(--pm-error);
  color: var(--pm-white);
}

.tipo-variacao {
  margin-bottom: 10px;
  padding: 10px 12px;
  background: var(--pm-bg);
  border-radius: 6px;
  border: 1.5px solid var(--pm-border-light);
}

.tipo-variacao-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.tipo-variacao-nome {
  font-size: 13px;
  font-weight: 600;
  color: var(--pm-text-dark);
}

.remover-link {
  border: none;
  background: none;
  font-size: 11px;
  color: var(--pm-error);
  cursor: pointer;
  padding: 0;
}

.tipo-variacao-valores {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  background: var(--pm-white);
  border: 1.5px solid var(--pm-border);
  border-radius: 16px;
  font-size: 12px;
  color: var(--pm-text-dark);
}

.chip--tabela {
  margin-right: 4px;
  background: var(--pm-bg);
}

.chip--painel {
  background: var(--pm-accent-bg);
  border-color: var(--pm-accent);
  color: var(--pm-accent);
}

.chip-remover {
  border: none;
  background: none;
  font-size: 10px;
  color: var(--pm-text-muted);
  cursor: pointer;
  line-height: 1;
  padding: 0;
}

.chip-input {
  width: 80px;
  height: 28px;
  border: 1.5px solid var(--pm-accent);
  border-radius: 4px;
  padding: 0 8px;
  font-size: 12px;
  font-family: var(--pm-font);
}

.chip-acao {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 4px;
  background: var(--pm-bg);
  color: var(--pm-text-muted);
  cursor: pointer;
}

.chip-adicionar {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1.5px dashed var(--pm-text-muted);
  border-radius: 16px;
  font-size: 12px;
  color: var(--pm-text-muted);
  background: none;
  cursor: pointer;
}

.novo-tipo {
  padding: 14px;
  background: var(--pm-accent-bg);
  border: 2px solid var(--pm-accent);
  border-radius: 6px;
}

.novo-tipo-titulo {
  font-size: 13px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin-bottom: 12px;
}

.adicionar-tipo {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1.5px dashed var(--pm-accent);
  border-radius: 6px;
  font-size: 12px;
  color: var(--pm-accent);
  background: none;
  cursor: pointer;
}

.badge-contagem {
  font-size: 11px;
  color: var(--pm-text-mid);
  background: var(--pm-bg);
  border-radius: 10px;
  padding: 2px 8px;
}

.tabela-variantes {
  padding: 0;
  overflow: hidden;
}

.tabela-variantes .secao-header {
  padding: 14px 14px 0;
}

.tabela-variantes .tabela-vazia {
  padding: 14px;
}

.tabela {
  width: 100%;
  border-collapse: collapse;
  margin-top: 12px;
}

.tabela th {
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--pm-text-muted);
  text-align: left;
  border-bottom: 2px solid var(--pm-border-light);
  background: var(--pm-bg);
  white-space: nowrap;
}

.tabela td {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--pm-text-dark);
  border-bottom: 1px solid var(--pm-border-light);
}

.editar-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--pm-accent);
  cursor: pointer;
  padding: 4px 10px;
  border: 1.5px solid var(--pm-border-light);
  border-radius: 4px;
  background: var(--pm-white);
}

.tabela-vazia {
  text-align: center;
  padding: 28px 0;
  color: var(--pm-text-muted);
  font-size: 13px;
}

/* Overlay + confirm dialog + variant panel */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.38);
  z-index: 100;
  display: flex;
  justify-content: flex-end;
}

.dialogo {
  margin: auto;
  width: 380px;
  background: var(--pm-white);
  border-radius: 10px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.18);
  overflow: hidden;
}

.dialogo-corpo {
  padding: 18px 20px 14px;
  border-bottom: 1.5px solid var(--pm-border-light);
}

.dialogo-titulo {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin-bottom: 6px;
}

.dialogo-mensagem {
  font-size: 13px;
  color: var(--pm-text-mid);
  line-height: 1.5;
}

.dialogo-acoes {
  padding: 12px 20px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  background: var(--pm-bg);
}

.painel-variante {
  width: 488px;
  max-width: 100%;
  height: 100%;
  background: var(--pm-white);
  border-left: 2px solid var(--pm-border-light);
  box-shadow: -8px 0 36px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.painel-header {
  padding: 14px 18px;
  border-bottom: 2px solid var(--pm-border-light);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-shrink: 0;
}

.painel-titulo {
  font-size: 14px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin-bottom: 6px;
}

.painel-combo {
  display: flex;
  gap: 4px;
}

.painel-fechar {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--pm-text-muted);
  font-size: 15px;
  border-radius: 4px;
  border: 1.5px solid var(--pm-border-light);
  background: var(--pm-white);
  flex-shrink: 0;
  margin-left: 8px;
}

.painel-conteudo {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.painel-footer {
  padding: 12px 18px;
  border-top: 2px solid var(--pm-border-light);
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  background: var(--pm-white);
  flex-shrink: 0;
}
</style>
