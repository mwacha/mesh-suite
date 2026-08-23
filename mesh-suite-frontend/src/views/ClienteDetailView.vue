<template>
  <AppShell title="Cliente">
    <p v-if="erro" class="error-geral">{{ erro }}</p>

    <div class="detalhe">
      <aside class="rail">
        <input v-model="buscaRail" class="busca-rail" placeholder="Buscar cliente..." @input="carregarRail" />
        <div
          v-for="item in listaRail"
          :key="item.id"
          class="item-rail"
          :class="{ 'item-rail-ativo': item.id === parceiroId }"
          @click="selecionar(item.id)"
        >
          <div class="item-rail-nome">{{ item.tradeName }}</div>
          <div class="item-rail-info">{{ item.city }}</div>
        </div>
      </aside>

      <div v-if="parceiro" class="painel">
        <div class="painel-header">
          <h1>{{ parceiro.tradeName }}</h1>
          <div class="painel-acoes">
            <button type="button" class="btn-secondary" data-test="cancelar" @click="cancelar">Cancelar</button>
            <button type="button" class="btn-secondary" data-test="editar" @click="editar">✏️ Editar</button>
            <button
              type="button"
              class="btn-primary btn-inert"
              title="Cadastro de pedidos fora de escopo desta fatia"
            >
              + Pedido
            </button>
          </div>
        </div>

        <div class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab"
            type="button"
            class="tab"
            :class="{ 'tab-ativa': abaAtiva === tab }"
            @click="abaAtiva = tab"
          >
            {{ tab }}
          </button>
        </div>

        <div v-if="abaAtiva === 'Dados'" class="grid grid-2">
          <div><label class="field-label">Razão Social</label><input :value="parceiro.legalName" readonly /></div>
          <div><label class="field-label">CNPJ / CPF</label><input :value="parceiro.document" readonly /></div>
          <div><label class="field-label">Nome Fantasia</label><input :value="parceiro.tradeName" readonly /></div>
          <div><label class="field-label">Inscrição Estadual</label><input :value="parceiro.stateRegistration" readonly /></div>
          <div>
            <label class="field-label">Tabela de Preço</label>
            <select disabled title="Depende do domínio Financeiro, ainda não implementado"><option>—</option></select>
          </div>
          <div>
            <label class="field-label">Limite de Crédito</label>
            <input disabled placeholder="—" title="Depende do domínio Financeiro, ainda não implementado" />
          </div>
          <div>
            <label class="field-label">Forma de Pagamento</label>
            <select disabled title="Depende do domínio Financeiro, ainda não implementado"><option>—</option></select>
          </div>
          <div>
            <label class="field-label">Vendedor Responsável</label>
            <select disabled title="Depende de atribuição de vendedor, ainda não implementada"><option>—</option></select>
          </div>
        </div>

        <div v-else-if="abaAtiva === 'Endereços'" class="endereco">
          <p>{{ parceiro.street }}, {{ parceiro.number }} — {{ parceiro.neighborhood }}</p>
          <p>{{ parceiro.city }} / {{ parceiro.state }} — CEP {{ parceiro.zipCode }}</p>
        </div>

        <div v-else-if="abaAtiva === 'Contatos'">
          <div v-if="parceiro.contacts.length === 0" class="estado-vazio">Nenhum contato cadastrado</div>
          <div v-for="(contato, index) in parceiro.contacts" :key="index" class="contato-item">
            <strong>{{ contato.name }}</strong> — {{ contato.jobTitle }}
            <div>{{ contato.email }} · {{ contato.businessPhone }}</div>
          </div>
        </div>

        <div v-else-if="abaAtiva === 'Pedidos'" class="estado-vazio">Nenhum pedido ainda</div>

        <div v-else-if="abaAtiva === 'Financeiro'" class="estado-vazio">Nenhum lançamento financeiro ainda</div>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { getPartner, listPartners, type PartnerResponse, type PartnerListItem } from '@/api/partners'

const route = useRoute()
const router = useRouter()

const tabs = ['Dados', 'Endereços', 'Contatos', 'Pedidos', 'Financeiro'] as const
const abaAtiva = ref<(typeof tabs)[number]>('Dados')

const parceiroId = ref('')
const parceiro = ref<PartnerResponse | null>(null)
const listaRail = ref<PartnerListItem[]>([])
const buscaRail = ref('')
const erro = ref('')

async function carregarParceiro(id: string) {
  parceiroId.value = id
  erro.value = ''
  try {
    parceiro.value = await getPartner(id)
    abaAtiva.value = 'Dados'
  } catch {
    erro.value = 'Não foi possível carregar os dados do cliente.'
  }
}

async function carregarRail() {
  erro.value = ''
  try {
    const pagina = await listPartners({ busca: buscaRail.value || undefined, papel: 'CUSTOMER', page: 0, size: 10 })
    listaRail.value = pagina.content
  } catch {
    erro.value = 'Não foi possível carregar a lista de clientes.'
  }
}

function selecionar(id: string) {
  router.push({ name: 'clientes-detalhe', params: { id } })
}

function editar() {
  router.push({ name: 'clientes-editar', params: { id: parceiroId.value } })
}

function cancelar() {
  router.push({ name: 'clientes' })
}

watch(
  () => route.params.id,
  (id) => {
    if (typeof id === 'string') {
      carregarParceiro(id)
    }
  },
)

onMounted(() => {
  carregarRail()
  const id = route.params.id
  if (typeof id === 'string') {
    carregarParceiro(id)
  }
})
</script>

<style scoped>
.error-geral {
  color: var(--pm-error);
  font-size: 14px;
  margin: 0 0 12px;
}

.detalhe {
  display: flex;
  gap: 16px;
  font-family: var(--pm-font);
}

.rail {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.busca-rail {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  margin-bottom: 6px;
  box-sizing: border-box;
  width: 100%;
}

.item-rail {
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  background: var(--pm-white);
}

.item-rail-ativo {
  border-color: var(--pm-accent);
  background: var(--pm-accent-bg);
}

.item-rail-nome {
  font-size: 12px;
  font-weight: 700;
  color: var(--pm-text-dark);
}

.item-rail-info {
  font-size: 11px;
  color: var(--pm-text-muted);
}

.painel {
  flex: 1;
  background: var(--pm-white);
  border: 1px solid var(--pm-border-light);
  border-radius: 12px;
  padding: 16px;
}

.painel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.painel-header h1 {
  font-size: 18px;
  font-weight: 700;
  color: var(--pm-text-dark);
  margin: 0;
}

.painel-acoes {
  display: flex;
  gap: 8px;
}

.btn-primary,
.btn-secondary {
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
}

.btn-inert {
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
}

.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--pm-border-light);
  margin-bottom: 14px;
}

.tab {
  background: none;
  border: none;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--pm-text-mid);
  cursor: pointer;
  border-bottom: 2px solid transparent;
}

.tab-ativa {
  color: var(--pm-accent);
  border-bottom-color: var(--pm-accent);
  font-weight: 600;
}

.grid {
  display: grid;
  gap: 12px 16px;
}

.grid-2 {
  grid-template-columns: 1fr 1fr;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pm-text-dark);
  margin-bottom: 4px;
}

input,
select {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--pm-border-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  color: var(--pm-text-dark);
  font-family: var(--pm-font);
}

input:disabled,
select:disabled,
input[readonly] {
  background: var(--pm-bg);
  color: var(--pm-text-mid);
}

.endereco p {
  font-size: 13px;
  color: var(--pm-text-dark);
  margin: 0 0 4px;
}

.contato-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--pm-border-light);
  font-size: 13px;
  color: var(--pm-text-dark);
}

.estado-vazio {
  color: var(--pm-text-muted);
  font-size: 13px;
  padding: 24px 0;
  text-align: center;
}
</style>
