<template>
  <AppShell :title="modoEdicao ? 'Editar Cor / Estampa' : 'Nova Cor / Estampa'">
    <form class="form" @submit.prevent="salvar">
      <section class="card">
        <h2>Informações Gerais</h2>
        <div class="grid grid-2">
          <div>
            <label class="field-label">Cor / Estampa *</label>
            <input v-model="form.nome" data-test="nome" placeholder="Ex: Azul Marinho, Floral Primavera" />
            <p v-if="erros.nome" class="field-error">{{ erros.nome }}</p>
          </div>
          <div>
            <label class="field-label">Data de Vigência *</label>
            <input v-model="form.dataVigencia" type="date" data-test="data-vigencia" />
            <p v-if="erros.dataVigencia" class="field-error">{{ erros.dataVigencia }}</p>
          </div>
        </div>
        <div>
          <label class="field-label">Descrição</label>
          <textarea v-model="form.descricao" data-test="descricao" rows="3" placeholder="Descrição opcional..."></textarea>
        </div>
        <div>
          <label class="field-label">Status</label>
          <div class="status-toggle">
            <button
              type="button"
              class="status-btn"
              :class="{ 'status-btn-active-ativo': form.ativo }"
              data-test="status-ativo"
              @click="form.ativo = true"
            >
              Ativo
            </button>
            <button
              type="button"
              class="status-btn"
              :class="{ 'status-btn-active-inativo': !form.ativo }"
              data-test="status-inativo"
              @click="form.ativo = false"
            >
              Inativo
            </button>
          </div>
        </div>
      </section>

      <p v-if="erroGeral" class="error-geral">{{ erroGeral }}</p>

      <div class="actions">
        <button type="button" class="btn-secondary" @click="cancelar">Cancelar</button>
        <button type="submit" class="btn-primary" :disabled="salvando">Salvar Cor / Estampa</button>
      </div>
    </form>
  </AppShell>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import {
  buscarCorEstampa,
  criarCorEstampa,
  atualizarCorEstampa,
  type CorEstampaRequest,
} from '@/api/coresEstampas'

const route = useRoute()
const router = useRouter()

const modoEdicao = computed(() => typeof route.params.id === 'string')

function novoFormulario(): CorEstampaRequest {
  return { nome: '', dataVigencia: '', descricao: '', ativo: true }
}

const form = reactive<CorEstampaRequest>(novoFormulario())
const erros = reactive<{ nome?: string; dataVigencia?: string }>({})
const erroGeral = ref('')
const salvando = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (typeof id === 'string') {
    try {
      const corEstampa = await buscarCorEstampa(id)
      form.nome = corEstampa.nome
      form.dataVigencia = corEstampa.dataVigencia
      form.descricao = corEstampa.descricao
      form.ativo = corEstampa.ativo
    } catch {
      erroGeral.value = 'Não foi possível carregar os dados da cor/estampa.'
    }
  }
})

function validar(): boolean {
  erros.nome = form.nome.trim() ? undefined : 'Campo obrigatório'
  erros.dataVigencia = form.dataVigencia ? undefined : 'Campo obrigatório'
  return !erros.nome && !erros.dataVigencia
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
      await atualizarCorEstampa(id, form)
    } else {
      await criarCorEstampa(form)
    }
    router.push({ name: 'cores-estampas' })
  } catch (err: any) {
    if (err?.response?.status === 409) {
      erroGeral.value = 'Já existe uma cor/estampa cadastrada com este nome.'
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
  router.push({ name: 'cores-estampas' })
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

.field-label {
  display: block;
  font-size: 12px;
  color: var(--pm-text-mid);
  margin-bottom: 4px;
}

input,
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
  margin-bottom: 10px;
}

.field-error {
  color: var(--pm-error);
  font-size: 12px;
  margin: -6px 0 10px;
}

.status-toggle {
  display: flex;
  gap: 8px;
}

.status-btn {
  border: 1px solid var(--pm-border-light);
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border-radius: 999px;
  padding: 6px 16px;
  font-size: 13px;
  font-family: var(--pm-font);
  cursor: pointer;
}

.status-btn-active-ativo {
  border-color: var(--pm-success);
  background: var(--pm-success-bg);
  color: var(--pm-success);
}

.status-btn-active-inativo {
  border-color: var(--pm-error);
  background: var(--pm-error-bg);
  color: var(--pm-error);
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
