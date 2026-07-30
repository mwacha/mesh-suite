<template>
  <div class="login-page">
    <aside class="login-brand">
      <div class="logo">
        <span class="logo-mark">P</span>
        <span class="logo-text">PediMais</span>
      </div>
      <p class="tagline">Gestão inteligente de pedidos para o seu negócio.</p>
    </aside>

    <main class="login-main">
      <div class="login-card">
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
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const email = ref('')
const senha = ref('')
const manterConectado = ref(false)
const showSenha = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const router = useRouter()
const authStore = useAuthStore()

async function onSubmit() {
  errorMessage.value = ''
  loading.value = true
  try {
    await login({ email: email.value, senha: senha.value, manterConectado: manterConectado.value })
    await authStore.checkSession()
    router.push({ name: 'dashboard' })
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
</script>

<style scoped>
.login-page {
  display: flex;
  width: 100vw;
  height: 100vh;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.login-brand {
  width: 40%;
  min-width: 320px;
  background: var(--pm-sidebar-bg);
  color: var(--pm-text-light);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 64px;
  gap: 16px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-mark {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  background: var(--pm-accent);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--pm-white);
  font-weight: 700;
  font-size: 13px;
}

.logo-text {
  font-family: var(--pm-font);
  font-weight: 700;
  font-size: 18px;
}

.tagline {
  color: var(--pm-text-muted);
  font-size: 16px;
  max-width: 220px;
}

.login-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  background: var(--pm-white);
  border-radius: 12px;
  padding: 40px;
  width: 380px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
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
input[type='text'] {
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
  color: #9ca3af;
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
</style>
