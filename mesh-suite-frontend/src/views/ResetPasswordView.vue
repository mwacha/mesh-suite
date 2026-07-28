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
  background: #fafaf9;
  font-family: 'Manrope', sans-serif;
}

.auth-card {
  background: #0e2530;
  color: #eaf2f4;
  border-radius: 20px;
  padding: 40px;
  width: 380px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #8fb0ba;
  margin: 16px 0 6px;
}

input {
  width: 100%;
  box-sizing: border-box;
  background: #14313d;
  border: 1px solid #1e4552;
  border-radius: 10px;
  padding: 10px 14px;
  color: #eaf2f4;
}

.submit-button {
  width: 100%;
  margin-top: 24px;
  background: #c9a15a;
  border: none;
  border-radius: 10px;
  padding: 12px;
  font-weight: 700;
  cursor: pointer;
}

.error {
  color: #d0453a;
  margin-top: 16px;
}

.success {
  color: #1f9d66;
  margin-top: 16px;
}
</style>
