<template>
  <div class="confirm-page">
    <div class="confirm-card">
      <h1>Confirmação de cadastro</h1>
      <p v-if="loading" class="subtitle">Confirmando...</p>
      <template v-else>
        <p v-if="successMessage" class="success">{{ successMessage }}</p>
        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        <RouterLink v-if="successMessage" to="/login" class="link">Ir para o login</RouterLink>
        <RouterLink v-else to="/cadastro" class="link">Cadastrar novamente</RouterLink>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { confirmSignup } from '@/api/auth'

const route = useRoute()
const loading = ref(true)
const successMessage = ref('')
const errorMessage = ref('')

onMounted(async () => {
  const token = String(route.query.token ?? '')
  try {
    await confirmSignup(token)
    successMessage.value = 'Sua conta foi confirmada com sucesso. Já pode fazer login.'
  } catch (err: any) {
    if (err?.response?.status === 401) {
      // The backend maps an unknown, already-used, or expired token to 401
      // (see TenantSignupService.confirmSignup) -- that's the only case where
      // "invalid or expired link" is an accurate message.
      errorMessage.value = 'Link inválido ou expirado. Preencha o cadastro novamente com o mesmo CNPJ para receber um novo link.'
    } else {
      errorMessage.value = 'Não foi possível conectar. Tente novamente em instantes.'
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.confirm-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--pm-bg);
  font-family: var(--pm-font);
}

.confirm-card {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 12px;
  padding: 40px;
  width: 380px;
  text-align: center;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
}

.subtitle {
  color: var(--pm-text-mid);
  font-size: 14px;
}

.success {
  color: var(--pm-success);
  margin-bottom: 16px;
}

.error {
  color: var(--pm-error);
  margin-bottom: 16px;
}

.link {
  color: var(--pm-accent);
  text-decoration: none;
}
</style>
