<template>
  <AppShell :title="editMode ? 'Editar Empresa' : 'Nova Empresa'">
    <PageHeader :title="editMode ? 'Edição de Empresa' : 'Cadastro de Empresa'" />

    <form class="form" @submit.prevent="save">
      <section class="card">
        <h2>Identificação</h2>
        <div class="grid grid-2">
          <TextField
            v-model="form.legalName"
            label="Razão Social"
            required
            placeholder="Ex: Mercado Silva Ltda"
            :error="errors.legalName"
            test-id="legal-name"
            @blur="validateLegalName"
          />
          <TextField
            v-model="form.tradeName"
            label="Nome Fantasia"
            placeholder="Ex: Mercado Silva"
            test-id="trade-name"
          />
        </div>
        <div class="grid grid-3-even">
          <TextField
            v-model="form.cnpj"
            label="CNPJ"
            required
            :mask="maskCnpj"
            :maxlength="18"
            placeholder="00.000.000/0000-00"
            :error="errors.cnpj"
            test-id="cnpj"
            @blur="validateCnpj"
          />
          <TextField v-model="form.stateRegistration" label="Inscrição Estadual" placeholder="000.000.000.000" />
          <TextField v-model="form.municipalRegistration" label="Inscrição Municipal" placeholder="000000" />
        </div>
      </section>

      <CollapsibleSection title="Contato">
        <div class="grid grid-3-even">
          <TextField v-model="form.phone" label="Telefone" placeholder="(11) 3000-0000" :mask="maskTelefone" :maxlength="15" />
          <TextField
            v-model="form.email"
            label="E-mail Comercial"
            placeholder="contato@empresa.com.br"
            :error="errors.email"
            test-id="email"
            @blur="validateEmail"
          />
          <TextField v-model="form.website" label="Site" placeholder="www.empresa.com.br" />
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Endereço">
        <div class="grid grid-cep">
          <div>
            <label class="field-label">CEP</label>
            <div class="input-action">
              <TextField
                v-model="form.zipCode"
                :mask="maskCep"
                :maxlength="9"
                :error="errors.zipCode"
                test-id="zip-code"
                @blur="validateZipCode"
              />
              <button type="button" data-test="search-cep" @click="searchCep">Buscar dados</button>
            </div>
            <p v-if="cepError" class="field-error">{{ cepError }}</p>
          </div>
          <TextField v-model="form.street" label="Logradouro" placeholder="Rua, Av., Alameda..." test-id="street" />
          <TextField v-model="form.number" label="Número" placeholder="123" />
        </div>
        <div class="grid grid-4">
          <TextField v-model="form.neighborhood" label="Bairro" placeholder="Ex: Centro" />
          <TextField v-model="form.city" label="Cidade" placeholder="Ex: São Paulo" test-id="city" />
          <div>
            <label class="field-label">UF</label>
            <select v-model="form.state" data-test="state">
              <option value="">UF</option>
              <option v-for="uf in UFS" :key="uf" :value="uf">{{ uf }}</option>
            </select>
          </div>
          <TextField v-model="form.complement" label="Complemento" placeholder="Sala, Andar, Bloco..." />
        </div>
      </CollapsibleSection>

      <p v-if="generalError" class="error-general">{{ generalError }}</p>

      <FormActions :saving="saving" save-label="Salvar Empresa" @cancel="cancel" />
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import TextField from '@/components/TextField.vue'
import CollapsibleSection from '@/components/CollapsibleSection.vue'
import FormActions from '@/components/FormActions.vue'
import {
  getCompany,
  createCompany,
  updateCompany,
  type CompanyRequest,
  type CompanyResponse,
} from '@/api/companies'
import { buscarEnderecoPorCep } from '@/api/cep'
import { maskCnpj, maskTelefone, maskCep } from '@/utils/masks'
import { emailValido, cepValido } from '@/utils/validacao'

const UFS = ['AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI',
  'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO']

const route = useRoute()
const router = useRouter()

const editMode = computed(() => typeof route.params.id === 'string')

function emptyForm(): CompanyRequest {
  return {
    legalName: '',
    cnpj: '',
    tradeName: '',
    stateRegistration: '',
    municipalRegistration: '',
    phone: '',
    email: '',
    website: '',
    zipCode: '',
    street: '',
    number: '',
    complement: '',
    neighborhood: '',
    city: '',
    state: '',
  }
}

// TextField's v-model needs a plain string -- optional fields come back as
// null from the backend (nullable columns, no empty-string default), so
// loading a company straight into `form` without this would hand null to
// the mask helpers (maskCnpj/maskTelefone/maskCep) and crash their render.
function toFormValues(company: CompanyResponse): CompanyRequest {
  return {
    legalName: company.legalName,
    cnpj: company.cnpj,
    tradeName: company.tradeName ?? '',
    stateRegistration: company.stateRegistration ?? '',
    municipalRegistration: company.municipalRegistration ?? '',
    phone: company.phone ?? '',
    email: company.email ?? '',
    website: company.website ?? '',
    zipCode: company.zipCode ?? '',
    street: company.street ?? '',
    number: company.number ?? '',
    complement: company.complement ?? '',
    neighborhood: company.neighborhood ?? '',
    city: company.city ?? '',
    state: company.state ?? '',
  }
}

const form = reactive<CompanyRequest>(emptyForm())
const errors = reactive<{ legalName?: string; cnpj?: string; email?: string; zipCode?: string }>({})
const cepError = ref('')
const generalError = ref('')
const saving = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const company = await getCompany(id)
      Object.assign(form, toFormValues(company))
    } catch {
      generalError.value = 'Não foi possível carregar os dados da empresa.'
    }
  }
})

async function searchCep() {
  cepError.value = ''
  const address = await buscarEnderecoPorCep(form.zipCode ?? '')
  if (!address) {
    cepError.value = 'CEP não encontrado — preencha o endereço manualmente'
    return
  }
  form.street = address.logradouro
  form.neighborhood = address.bairro
  form.city = address.localidade
  form.state = address.uf
}

function validateLegalName() {
  errors.legalName = form.legalName.trim() ? undefined : 'Campo obrigatório'
}

function validateCnpj() {
  const digits = form.cnpj.replace(/\D/g, '')
  if (!digits) {
    errors.cnpj = 'Campo obrigatório'
  } else if (digits.length !== 14) {
    errors.cnpj = 'Informe um CNPJ válido'
  } else {
    errors.cnpj = undefined
  }
}

function validateEmail() {
  errors.email = !form.email || emailValido(form.email) ? undefined : 'E-mail inválido'
}

function validateZipCode() {
  errors.zipCode = !form.zipCode || cepValido(form.zipCode) ? undefined : 'CEP inválido'
}

function validate(): boolean {
  validateLegalName()
  validateCnpj()
  validateEmail()
  validateZipCode()
  return !errors.legalName && !errors.cnpj && !errors.email && !errors.zipCode
}

function toPayload(): CompanyRequest {
  return {
    ...form,
    cnpj: form.cnpj.replace(/\D/g, ''),
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
      await updateCompany(id, payload)
    } else {
      await createCompany(payload)
    }
    router.push({ name: 'empresas' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      generalError.value = 'Já existe uma empresa cadastrada com este CNPJ.'
    } else if (err?.response?.status === 403) {
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
  router.push({ name: 'empresas' })
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
  font-size: 13px;
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

.grid-3-even {
  grid-template-columns: 1fr 1fr 1fr;
}

.grid-4 {
  grid-template-columns: repeat(4, 1fr);
}

.grid-cep {
  grid-template-columns: 160px 1fr 100px;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

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

.error-general {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0;
}
</style>
