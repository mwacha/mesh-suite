<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>Esqueci minha senha</h1>
      <form v-if="!submitted" @submit.prevent="onSubmit">
        <label class="field-label" for="email">E-mail</label>
        <input id="email" type="email" v-model="email" required />
        <button type="submit" class="submit-button">Enviar link</button>
      </form>
      <p v-else class="success">Enviamos um link de redefinição se o e-mail existir.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { forgotPassword } from '@/api/auth'

const email = ref('')
const submitted = ref(false)

async function onSubmit() {
  try {
    await forgotPassword(email.value)
  } catch {
    // Swallow it: the backend always returns 200 regardless of whether the
    // account exists, so any failure here is a network/infra problem, not
    // something the user can act on. Falling through to `finally` keeps the
    // message identical either way (no account-enumeration signal) and, just
    // as importantly, catching here avoids an unhandled promise rejection.
  } finally {
    submitted.value = true
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

.success {
  color: #8fb0ba;
}
</style>
