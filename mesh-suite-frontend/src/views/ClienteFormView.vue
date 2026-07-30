<template>
  <AppShell :title="modoEdicao ? 'Editar Cliente' : 'Novo Cliente'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Dados Gerais</h2>
        <div class="grid grid-3">
          <div>
            <label class="field-label">Tipo de Pessoa *</label>
            <select v-model="form.tipoPessoa">
              <option value="JURIDICA">Jurídica</option>
              <option value="FISICA">Física</option>
            </select>
          </div>
          <div>
            <label class="field-label">CNPJ / CPF *</label>
            <input v-model="form.documento" data-test="documento" />
          </div>
          <div>
            <label class="field-label">Nome Fantasia *</label>
            <input v-model="form.nomeFantasia" data-test="nomeFantasia" />
            <p v-if="erros.nomeFantasia" class="field-error">{{ erros.nomeFantasia }}</p>
          </div>
        </div>
        <div>
          <label class="field-label">Razão Social</label>
          <input v-model="form.razaoSocial" />
        </div>
        <div>
          <label class="field-label">
            Tipo de Papel * <span class="hint">(pode selecionar mais de uma opção)</span>
          </label>
          <div class="checkbox-row">
            <label class="checkbox-label">
              <input type="checkbox" :checked="form.papeis.includes('CLIENTE')" @change="togglePapel('CLIENTE')" />
              Cliente
            </label>
            <label class="checkbox-label">
              <input type="checkbox" :checked="form.papeis.includes('FORNECEDOR')" @change="togglePapel('FORNECEDOR')" />
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

      <section class="card">
        <h2>Contato para Cobrança e Faturamento</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">E-mail(s)</label>
            <input v-model="form.emailsCobranca" placeholder="email@exemplo.com.br" />
          </div>
          <div>
            <label class="field-label">Número do WhatsApp</label>
            <input v-model="form.whatsapp" placeholder="(11) 99999-9999" />
          </div>
        </div>
        <p class="hint">Para inserir mais de um e-mail, use a vírgula</p>
      </section>

      <section class="card">
        <h2>Informações Fiscais</h2>
        <div class="grid grid-4">
          <div>
            <label class="field-label">Indicador de Inscrição Estadual</label>
            <select v-model="form.indicadorIe">
              <option :value="null">Selecione...</option>
              <option value="NAO_CONTRIBUINTE">Não contribuinte</option>
              <option value="CONTRIBUINTE">Contribuinte</option>
              <option value="CONTRIBUINTE_ISENTO">Contribuinte isento</option>
            </select>
          </div>
          <div>
            <label class="field-label">Inscrição Estadual</label>
            <input v-model="form.inscricaoEstadual" />
          </div>
          <div>
            <label class="field-label">Inscrição Municipal</label>
            <input v-model="form.inscricaoMunicipal" />
          </div>
          <div>
            <label class="field-label">Inscrição Suframa</label>
            <input v-model="form.inscricaoSuframa" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Endereço</h2>
        <div class="grid grid-3">
          <div>
            <label class="field-label">CEP</label>
            <div class="input-action">
              <input v-model="form.cep" data-test="cep" />
              <button type="button" data-test="buscar-cep" @click="buscarCep">Buscar dados</button>
            </div>
            <p v-if="erroCep" class="field-error">{{ erroCep }}</p>
          </div>
          <div>
            <label class="field-label">Endereço</label>
            <input v-model="form.logradouro" data-test="logradouro" />
          </div>
          <div>
            <label class="field-label">Número</label>
            <input v-model="form.numero" />
          </div>
        </div>
        <div class="grid grid-4">
          <div>
            <label class="field-label">Estado</label>
            <select v-model="form.uf" data-test="uf">
              <option value="">UF</option>
              <option v-for="estado in UFS" :key="estado" :value="estado">{{ estado }}</option>
            </select>
          </div>
          <div>
            <label class="field-label">Cidade</label>
            <input v-model="form.cidade" data-test="cidade" />
          </div>
          <div>
            <label class="field-label">Bairro</label>
            <input v-model="form.bairro" />
          </div>
          <div>
            <label class="field-label">Complemento</label>
            <input v-model="form.complemento" />
          </div>
        </div>
      </section>

      <section class="card">
        <h2>Outros Contatos</h2>
        <div v-for="(contato, index) in form.contatos" :key="index" class="grid grid-contato">
          <input v-model="contato.nome" placeholder="Nome" />
          <input v-model="contato.email" placeholder="email@exemplo.com" />
          <input v-model="contato.telefoneComercial" placeholder="(11) 3333-3333" />
          <input v-model="contato.telefoneCelular" placeholder="(11) 99999-9999" />
          <input v-model="contato.cargo" placeholder="Ex: Financeiro" />
          <button type="button" class="btn-remove" @click="removerContato(index)">🗑</button>
        </div>
        <button type="button" class="btn-add-contato" @click="adicionarContato">+ Adicionar Contato</button>
      </section>

      <section class="card">
        <h2>Observação</h2>
        <textarea v-model="form.observacao" rows="4" placeholder="Informações adicionais sobre o cliente..."></textarea>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Cliente</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  buscarParceiro,
  criarParceiro,
  atualizarParceiro,
  type ParceiroRequest,
  type PapelParceiro,
} from '@/api/parceiros'
import { buscarEnderecoPorCep } from '@/api/cep'

const UFS = [
  'AC', 'AL', 'AM', 'AP', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MG', 'MS', 'MT', 'PA', 'PB',
  'PE', 'PI', 'PR', 'RJ', 'RN', 'RO', 'RR', 'RS', 'SC', 'SE', 'SP', 'TO',
]

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): ParceiroRequest {
  return {
    tipoPessoa: 'JURIDICA',
    documento: '',
    nomeFantasia: '',
    razaoSocial: '',
    papeis: ['CLIENTE'],
    emailsCobranca: '',
    whatsapp: '',
    indicadorIe: null,
    inscricaoEstadual: '',
    inscricaoMunicipal: '',
    inscricaoSuframa: '',
    cep: '',
    logradouro: '',
    numero: '',
    bairro: '',
    complemento: '',
    uf: '',
    cidade: '',
    observacao: '',
    contatos: [],
  }
}

const form = reactive<ParceiroRequest>(novoFormulario())
const erros = reactive<{ nomeFantasia?: string; papeis?: string }>({})
const erroGeral = ref('')
const erroCep = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const parceiro = await buscarParceiro(id)
      Object.assign(form, parceiro)
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados do cliente. Tente novamente em instantes.'
    }
  }
})

function togglePapel(papel: PapelParceiro) {
  const index = form.papeis.indexOf(papel)
  if (index === -1) {
    form.papeis.push(papel)
  } else {
    form.papeis.splice(index, 1)
  }
}

function adicionarContato() {
  form.contatos.push({ nome: '', email: '', telefoneComercial: '', telefoneCelular: '', cargo: '' })
}

function removerContato(index: number) {
  form.contatos.splice(index, 1)
}

async function buscarCep() {
  erroCep.value = ''
  const endereco = await buscarEnderecoPorCep(form.cep)
  if (!endereco) {
    erroCep.value = 'CEP não encontrado — preencha o endereço manualmente'
    return
  }
  form.logradouro = endereco.logradouro
  form.bairro = endereco.bairro
  form.cidade = endereco.localidade
  form.uf = endereco.uf
}

function validar(): boolean {
  erros.nomeFantasia = form.nomeFantasia.trim() ? undefined : 'Campo obrigatório'
  erros.papeis = form.papeis.some((p) => p === 'CLIENTE' || p === 'FORNECEDOR')
    ? undefined
    : 'Selecione ao menos Cliente ou Fornecedor'
  return !erros.nomeFantasia && !erros.papeis
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
      await atualizarParceiro(id, form)
    } else {
      await criarParceiro(form)
    }
    router.push({ name: 'clientes' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe um parceiro cadastrado com este documento.'
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
  router.push({ name: 'clientes' })
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
  align-items: end;
}

.field-label {
  display: block;
  font-size: 12px;
  color: var(--pm-text-mid);
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
}

.input-action button {
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
