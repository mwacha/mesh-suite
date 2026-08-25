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
    paymentMethodId: null,
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
