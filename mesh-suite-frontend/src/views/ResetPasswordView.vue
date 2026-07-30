<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>Redefinir senha</h1>
      <form @submit.prevent="onSubmit">
        <label class="field-label" for="nova">Nova senha</label>
        <input id="nova" name="novaSenha" type="password" v-model="novaSenha" required minlength="8" />

        <label class="field-label" for="confirma">Confirmar nova senha</label>
        <input id="confirma" name="confirmacao" type="password" v-model="confirmacao" required minlength="8" />

        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        <p v-if="successMessage" class="success">{{ successMessage }}</p>

        <button type="submit" class="submit-button">Redefinir senha</button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { resetPassword } from '@/api/auth'

const route = useRoute()
const novaSenha = ref('')
const confirmacao = ref('')
const errorMessage = ref('')
const successMessage = ref('')

async function onSubmit() {
  errorMessage.value = ''
  successMessage.value = ''

  if (novaSenha.value !== confirmacao.value) {
    errorMessage.value = 'As senhas não coincidem'
    return
  }

  const token = String(route.query.token ?? '')
  try {
    await resetPassword(token, novaSenha.value)
    successMessage.value = 'Senha redefinida com sucesso.'
  } catch (err: any) {
    if (err?.response?.status === 401) {
      // The backend maps an unknown, already-used, or expired token to 401
      // (see PasswordResetService.confirmReset) -- that's the only case where
      // "invalid or expired link" is an accurate message.
      errorMessage.value = 'Link inválido ou expirado'
    } else {
      // Network failure, 5xx, or any other unexpected status: don't tell the
      // user their link is bad when we don't actually know that.
      errorMessage.value = 'Não foi possível conectar. Tente novamente em instantes.'
    }
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.auth-card {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 12px;
  padding: 40px;
  width: 380px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--pm-text-mid);
  margin: 16px 0 6px;
}

input {
  width: 100%;
  box-sizing: border-box;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 10px 14px;
  color: var(--pm-text-dark);
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
  cursor: pointer;
}

.error {
  color: var(--pm-error);
  margin-top: 16px;
}

.success {
  color: var(--pm-success);
  margin-top: 16px;
}
</style>
