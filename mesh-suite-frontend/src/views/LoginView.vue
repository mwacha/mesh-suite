<template>
  <div class="login-page">
    <div class="login-box">
      <aside class="login-brand">
        <div class="logo">
          <span class="logo-mark">P</span>
          <span class="logo-text">PediMais</span>
        </div>
        <p class="tagline">Gestão inteligente de pedidos para o seu negócio.</p>
      </aside>

      <main class="login-main">
        <div class="login-card" v-if="!accountOptions">
          <h1>Entrar</h1>
          <p class="subtitle">Acesse o painel do seu PediMais</p>

          <form @submit.prevent="onSubmit">
            <label class="field-label" for="email">E-mail</label>
            <input
              id="email"
              type="email"
              v-model="email"
              required
              autocomplete="username"
              placeholder="marina@confeccaoaurora.com.br"
            />

            <label class="field-label" for="senha">Senha</label>
            <div class="password-field">
              <input
                id="senha"
                :type="showSenha ? 'text' : 'password'"
                v-model="senha"
                required
                autocomplete="current-password"
              />
              <button
                type="button"
                class="toggle-senha"
                @click="showSenha = !showSenha"
                :aria-label="showSenha ? 'Ocultar senha' : 'Mostrar senha'"
              >
                {{ showSenha ? 'Ocultar' : 'Mostrar' }}
              </button>
            </div>

            <div class="row">
              <label class="checkbox-label">
                <input type="checkbox" v-model="manterConectado" />
                Manter conectado
              </label>
              <RouterLink to="/esqueci-senha" class="link">Esqueci minha senha</RouterLink>
            </div>

            <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

            <button type="submit" class="submit-button" :disabled="loading">Entrar</button>
          </form>

          <p class="footer-text">
            Não tem conta?
            <span class="link-inert" title="Provisionamento de tenant fora de escopo desta fatia">
              Fale com o time comercial
            </span>
          </p>
        </div>

        <div class="login-card" v-else>
          <h1>Escolha a empresa</h1>
          <p class="subtitle">Seu e-mail e senha dão acesso a mais de uma empresa</p>

          <form @submit.prevent="onConfirmAccount">
            <label class="field-label" for="empresa">Empresa</label>
            <select id="empresa" v-model="selectedTenantId" data-test="account-select" required>
              <option value="" disabled>Selecione...</option>
              <option v-for="conta in accountOptions" :key="conta.tenantId" :value="conta.tenantId">
                {{ conta.nomeEmpresa }}
              </option>
            </select>

            <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

            <button type="submit" class="submit-button" :disabled="loading || !selectedTenantId">Entrar</button>
          </form>

          <button type="button" class="link back-link" data-test="back-to-login" @click="accountOptions = null">
            Voltar
          </button>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, selectAccount, type AccountOption } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const email = ref('')
const senha = ref('')
const manterConectado = ref(false)
const showSenha = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const accountOptions = ref<AccountOption[] | null>(null)
const selectedTenantId = ref('')

const router = useRouter()
const authStore = useAuthStore()

async function finishLogin() {
  await authStore.checkSession()
  router.push({ name: 'dashboard' })
}

async function onSubmit() {
  errorMessage.value = ''
  loading.value = true
  try {
    const result = await login({ email: email.value, senha: senha.value, manterConectado: manterConectado.value })
    if (result.status === 'select-account') {
      accountOptions.value = result.contas
      selectedTenantId.value = ''
      return
    }
    await finishLogin()
  } catch (err: any) {
    if (err?.response?.status === 429) {
      errorMessage.value = 'Muitas tentativas, tente novamente em instantes'
    } else if (err?.response?.status === 401) {
      errorMessage.value = 'E-mail ou senha inválidos'
    } else {
      errorMessage.value = 'Não foi possível conectar. Tente novamente em instantes.'
    }
  } finally {
    loading.value = false
  }
}

async function onConfirmAccount() {
  if (!selectedTenantId.value) {
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    await selectAccount(selectedTenantId.value, manterConectado.value)
    await finishLogin()
  } catch {
    errorMessage.value = 'Não foi possível entrar nessa empresa. Faça login novamente.'
    accountOptions.value = null
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100vw;
  height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.login-box {
  display: flex;
  width: 100%;
  max-width: 800px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 32px rgba(0, 0, 0, 0.12);
}

.login-brand {
  width: 320px;
  flex-shrink: 0;
  background: var(--pm-sidebar-bg);
  color: var(--pm-text-light);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 56px 40px;
  gap: 16px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-mark {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  background: var(--pm-accent);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--pm-white);
  font-weight: 700;
  font-size: 15px;
}

.logo-text {
  font-family: var(--pm-font);
  font-weight: 700;
  font-size: 20px;
}

.tagline {
  color: var(--pm-text-muted);
  font-size: 14px;
  max-width: 220px;
  margin: 0;
}

.login-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--pm-white);
  padding: 48px 40px;
  box-sizing: border-box;
}

.login-card {
  width: 100%;
  max-width: 340px;
  color: var(--pm-text-dark);
  font-family: var(--pm-font);
}

.login-card h1 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
}

.subtitle {
  color: var(--pm-text-mid);
  font-size: 14px;
  margin: 0 0 24px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--pm-text-mid);
  margin: 16px 0 6px;
}

input[type='email'],
input[type='password'],
input[type='text'],
select {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 10px 14px;
  color: var(--pm-text-dark);
  font-size: 14px;
}

input[type='email']::placeholder,
input[type='password']::placeholder,
input[type='text']::placeholder {
  color: var(--pm-placeholder);
}

.password-field {
  position: relative;
}

.password-field input {
  padding-right: 64px;
}

.toggle-senha {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--pm-accent);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  font-size: 14px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--pm-text-dark);
}

.checkbox-label input[type='checkbox'] {
  accent-color: var(--pm-accent);
  width: 16px;
  height: 16px;
}

.link {
  color: var(--pm-accent);
  text-decoration: none;
}

.link-inert {
  color: var(--pm-accent);
  cursor: not-allowed;
}

.error {
  color: var(--pm-error);
  font-size: 14px;
  margin-top: 16px;
}

.submit-button {
  width: 100%;
  margin-top: 24px;
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
  border-radius: 8px;
  padding: 12px;
  font-weight: 700;
  font-size: 15px;
  cursor: pointer;
}

.submit-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.footer-text {
  text-align: center;
  margin-top: 24px;
  font-size: 13px;
  color: var(--pm-text-mid);
}

.back-link {
  display: block;
  margin: 20px auto 0;
  background: none;
  border: none;
  padding: 0;
  font-size: 13px;
  cursor: pointer;
}
</style>
