<!-- mesh-suite-frontend/src/views/SignupView.vue -->
<template>
  <div class="signup-page">
    <div class="signup-card" v-if="!submitted">
      <h1>Criar conta</h1>
      <p class="subtitle">Cadastre sua empresa e comece a usar o Mesh Suite</p>

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
            <TextField v-model="form.tradeName" label="Nome Fantasia" placeholder="Ex: Mercado Silva" test-id="trade-name" />
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
              test-id="company-email"
              @blur="validateCompanyEmail"
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

        <section class="card">
          <h2>Acesso</h2>
          <div class="grid grid-2">
            <TextField
              v-model="form.adminName"
              label="Seu nome"
              required
              :error="errors.adminName"
              test-id="admin-name"
              @blur="validateAdminName"
            />
            <TextField
              v-model="form.adminEmail"
              label="Seu e-mail"
              required
              :error="errors.adminEmail"
              test-id="admin-email"
              @blur="validateAdminEmail"
            />
          </div>
          <div class="grid grid-2">
            <div>
              <label class="field-label" for="senha">Senha</label>
              <input id="senha" data-test="senha" type="password" v-model="form.senha" required minlength="8" />
            </div>
            <div>
              <label class="field-label" for="confirmar-senha">Confirmar senha</label>
              <input
                id="confirmar-senha"
                data-test="confirmar-senha"
                type="password"
                v-model="confirmarSenha"
                required
                minlength="8"
              />
            </div>
          </div>
        </section>

        <p v-if="generalError" class="error-general">{{ generalError }}</p>

        <FormActions :saving="saving" save-label="Criar conta" @cancel="cancel" />
      </form>
    </div>

    <div class="signup-card" v-else>
      <h1>Verifique seu e-mail</h1>
      <p class="subtitle">
        Enviamos um link de confirmação para {{ form.adminEmail }}. Clique nele para ativar sua conta.
      </p>
      <RouterLink to="/login" class="link">Voltar para o login</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import TextField from '@/components/TextField.vue'
import CollapsibleSection from '@/components/CollapsibleSection.vue'
import FormActions from '@/components/FormActions.vue'
import { signup, type SignupPayload } from '@/api/auth'
import { buscarEnderecoPorCep } from '@/api/cep'
import { maskCnpj, maskTelefone, maskCep } from '@/utils/masks'
import { emailValido, cepValido } from '@/utils/validacao'

const UFS = ['AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI',
  'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO']

const router = useRouter()

function emptyForm(): SignupPayload {
  return {
    legalName: '', cnpj: '', tradeName: '', stateRegistration: '', municipalRegistration: '',
    phone: '', email: '', website: '', zipCode: '', street: '', number: '', complement: '',
    neighborhood: '', city: '', state: '', adminName: '', adminEmail: '', senha: '',
  }
}

const form = reactive<SignupPayload>(emptyForm())
const confirmarSenha = ref('')
const errors = reactive<{
  legalName?: string; cnpj?: string; email?: string; zipCode?: string
  adminName?: string; adminEmail?: string
}>({})
const cepError = ref('')
const generalError = ref('')
const saving = ref(false)
const submitted = ref(false)

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

function validateCompanyEmail() {
  errors.email = !form.email || emailValido(form.email) ? undefined : 'E-mail inválido'
}

function validateZipCode() {
  errors.zipCode = !form.zipCode || cepValido(form.zipCode) ? undefined : 'CEP inválido'
}

function validateAdminName() {
  errors.adminName = form.adminName.trim() ? undefined : 'Campo obrigatório'
}

function validateAdminEmail() {
  if (!form.adminEmail.trim()) {
    errors.adminEmail = 'Campo obrigatório'
  } else if (!emailValido(form.adminEmail)) {
    errors.adminEmail = 'E-mail inválido'
  } else {
    errors.adminEmail = undefined
  }
}

function validate(): boolean {
  validateLegalName()
  validateCnpj()
  validateCompanyEmail()
  validateZipCode()
  validateAdminName()
  validateAdminEmail()
  return !errors.legalName && !errors.cnpj && !errors.email && !errors.zipCode
    && !errors.adminName && !errors.adminEmail
}

async function save() {
  generalError.value = ''
  if (!validate()) {
    return
  }
  if (!form.senha || form.senha.length < 8) {
    generalError.value = 'A senha precisa ter no mínimo 8 caracteres'
    return
  }
  if (form.senha !== confirmarSenha.value) {
    generalError.value = 'As senhas não coincidem'
    return
  }

  saving.value = true
  try {
    await signup({ ...form, cnpj: form.cnpj.replace(/\D/g, '') })
    submitted.value = true
  } catch (err: any) {
    if (err?.response?.status === 409) {
      generalError.value = 'Já existe uma conta confirmada com este CNPJ. Faça login.'
    } else if (err?.response?.status === 429) {
      generalError.value = 'Muitas tentativas, tente novamente em instantes'
    } else if (err?.response?.status === 400) {
      generalError.value = err.response.data?.mensagem ?? 'Verifique os dados informados.'
    } else {
      generalError.value = 'Não foi possível concluir o cadastro. Tente novamente em instantes.'
    }
  } finally {
    saving.value = false
  }
}

function cancel() {
  router.push({ name: 'login' })
}
</script>

<style scoped>
.signup-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40px 24px;
  box-sizing: border-box;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.signup-card {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 12px;
  padding: 40px;
  width: 100%;
  max-width: 720px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
}

.signup-card h1 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
}

.subtitle {
  color: var(--pm-text-mid);
  font-size: 14px;
  margin: 0 0 24px;
}

.link {
  color: var(--pm-accent);
  text-decoration: none;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  grid-template-columns: 220px 1fr 100px;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

select,
input[type='password'] {
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
  min-width: 90px;
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
