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

.success {
  color: var(--pm-success);
}
</style>
