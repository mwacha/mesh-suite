# Financeiro Mínimo (AccountsPayable) — Spec de Design

> Sub-projeto 3 de 5 da iniciativa "Compras completa". Ver `docs/superpowers/specs/2026-08-04-ordem-compra-design.md` seção 1 para a decomposição inteira.

## 1. Contexto e decisão

`PRD-08-financeiro.md` documenta o domínio Financeiro do sisconf legado como um núcleo amplo: Título Financeiro (conceito único compartilhado entre contas a pagar e a receber), Movimentação de Caixa (livro-caixa), Controle de Cheque, Cartão de Crédito, Desconto de Duplicata, Programação de Pagamento e Forma de Recebimento.

Um achado importante limita o que dá pra construir agora: a baixa completa de um título (quitação) depende de **saldo de Conta Bancária**, que o próprio PRD documenta como pertencente ao domínio **Cobrança Bancária** — que ainda não existe no sistema novo. Baixa com reflexo em caixa/conta bancária não é construível nesta fatia.

Esta fatia cobre apenas o necessário para o sub-projeto 5 (Compra/nota fiscal de entrada) poder gerar suas parcelas de contas a pagar, mais uma baixa simplificada (sem caixa/conta bancária) para dar utilidade real à funcionalidade desde já — diferente do Estoque mínimo, que ficou sem tela nenhuma, aqui a baixa é uma ação que alguém precisa disparar no dia a dia, então ganha uma tela mínima.

**Escopo deliberadamente reduzido em relação ao "título financeiro" do PRD**: construímos só **contas a pagar**, não o conceito compartilhado pagar/receber — contas a receber (consumida por uma futura Venda) fica para quando for um sub-projeto próprio. Sem Movimentação de Caixa, sem Conta Bancária, sem Cheque, Cartão de Crédito, Desconto de Duplicata, Programação de Pagamento ou Forma de Recebimento.

## 2. Escopo

### Incluído
- `AccountsPayable`: título a pagar (número, parcela, fornecedor, valor, vencimento, status).
- `AccountsPayableService.createInstallments(...)`: criação em lote de parcelas — método interno, chamado pela futura Compra, sem endpoint de escrita direta.
- Baixa simplificada (`markAsPaid`) e reversão (`reverse`) — sem movimentação de caixa, sem toque em saldo de conta bancária, sem valor pago divergente do valor original (sempre quita o valor cheio).
- `AccountsPayableListView.vue`: lista com filtro por status e ações de baixa/reversão.

### Fora de escopo
- **Conceito compartilhado pagar/receber** — só "a pagar" nesta fatia; contas a receber fica para um sub-projeto futuro (quando Vendas/nota fiscal de saída existir).
- **Movimentação de Caixa / livro-caixa** — depende de Conta Bancária, que não existe.
- **Conta Bancária** — domínio Cobrança Bancária, não construído.
- **Estorno com efeito em caixa** — a "reversão" desta fatia é só a volta de status (`PAID → OPEN`), sem o mecanismo de estorno contábil do PRD (contas fixas de estorno, lançamento de caixa invertido, ocultação de lançamento).
- **Cheque, Cartão de Crédito, Desconto de Duplicata, Programação de Pagamento, Forma de Recebimento** — nenhum é dependência de Compra.
- **Lançamento manual de conta a pagar avulsa** — a tela só lista e dá baixa; a criação é exclusivamente via `createInstallments`, chamado pela futura Compra. Sem "Nova Conta a Pagar" na UI.
- **Valor pago divergente do valor original** (juros/desconto/multa na baixa) — a baixa sempre quita o valor cheio do título.

## 3. Modelo de dados

### `AccountsPayable` (tabela `accounts_payable` — RLS por tenant direto, mesmo padrão de `stock_movement`/`purchase_order`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `number` | Integer | sequencial por tenant, via `AccountsPayableCounter` (mirror do contador de Pedido/Ordem de Compra) |
| `installmentNumber` | Integer | ex. 2 (de 3) |
| `totalInstallments` | Integer | ex. 3 |
| `supplier` | FK → `Parceiro` | deve ter `PapelParceiro.FORNECEDOR` |
| `amount` | BigDecimal(12,2) | valor original da parcela |
| `issueDate` | LocalDate | data de emissão |
| `dueDate` | LocalDate | vencimento |
| `paymentDate` | LocalDate | nullable — vazio = em aberto |
| `status` | `AccountsPayableStatus` (`OPEN`, `PAID`) | default `OPEN` |
| `referenceId` | UUID | nullable — id da futura Compra de origem |
| `createdAt` | Instant | `updatable = false` |

### `AccountsPayableCounter` (tabela de contador, um registro por tenant — mirror de `PedidoContador`/`PurchaseOrderCounter`)

Mesmo mecanismo de numeração sequencial já usado em Pedido e Ordem de Compra.

## 4. Regras de negócio

1. `createInstallments(tenantId, supplierId, referenceId, installments: List<{amount, dueDate}>)` cria N registros de `AccountsPayable`, numerados sequencialmente (`number`, via contador atômico) e com `installmentNumber`/`totalInstallments` corretos — método interno, sem endpoint HTTP de escrita direta, mesmo papel que `StockService.adjustBalance` tem para Estoque.
2. Fornecedor deve ter o papel `FORNECEDOR` — mesma validação já usada em Ordem de Compra (`PapelParceiro`, sem checagem de status ativo/inativo, mesmo precedente).
3. `markAsPaid(id, paymentDate)`: só permitido a partir de `status=OPEN`; grava `paymentDate` e muda para `PAID`. Sem valor pago separado — sempre quita `amount` integralmente.
4. `reverse(id)`: só permitido a partir de `status=PAID`; limpa `paymentDate` e volta para `OPEN`. Diferente das transições terminais de Ordem de Compra (`RECEIVED`/`CANCELLED`) — aqui a reversão é permitida indefinidamente porque não há efeito colateral externo (sem caixa, sem conta bancária) para desfazer; é apenas a correção de um lançamento.
5. Sem exclusão física nesta fatia — só os dois status e a transição entre eles.

## 5. Telas

### `AccountsPayableListView.vue` (rota `/contas-a-pagar`)
Mesmo padrão visual/estrutural de `PedidosListView.vue`/`PurchaseOrdersListView.vue`: filtro por status, tabela paginada (número/parcela, fornecedor, vencimento, valor, status), dropdown de Ações ("Dar baixa" quando `OPEN`, "Reverter baixa" quando `PAID`). **Sem botão de "Novo"** — não há criação manual nesta fatia.

### `AppSidebar.vue`
Novo item "Contas a Pagar", roteando para `/contas-a-pagar`.

## 6. API (backend)

- `GET /api/accounts-payable` — lista paginada, filtro `status`, `@RequiresPermission(module = Module.PAYABLE, action = Action.VIEW)`.
- `PATCH /api/accounts-payable/{id}/status` — recebe o status-alvo explicitamente (`PAID` ou `OPEN`, mesmo padrão de Ordem de Compra), `@RequiresPermission(module = Module.PAYABLE, action = Action.EDIT)`.

Sem `POST`/`PUT`/`DELETE` — `createInstallments` é um método de serviço Java, não uma rota HTTP. Novo valor `PAYABLE` no enum `Module` — nomeado estreito (não `FINANCE`) porque só contas a pagar está sendo construído; uma futura fatia de contas a receber ganharia seu próprio módulo.

## 7. Frontend

Código novo em inglês, mesma convenção já usada em Ordem de Compra: `src/api/accountsPayable.ts`, `src/views/AccountsPayableListView.vue` — nomes de arquivo/tipo em inglês, variáveis locais do `<script setup>` em português (mesmo padrão de `PurchaseOrderFormView.vue`/`PurchaseOrdersListView.vue`). Rota e texto visível continuam em português: `/contas-a-pagar`, nome de rota `contas-a-pagar`.

O módulo `PAYABLE` entra na matriz de permissões do `UserFormView` (diferente de `STOCK`, que ficou de fora por não ter tela nenhuma) — como esta fatia tem uma tela real e ações reais (baixa/reversão), um admin precisa conseguir conceder esse acesso.

## 8. Testes

- Backend: `AccountsPayableServiceTest` (criação em lote com numeração/parcela corretos, validação de papel do fornecedor, baixa, reversão, transições inválidas rejeitadas, RLS isolation, negação de permissão), `AccountsPayableControllerTest` (lista, baixa/reversão via API, RLS cross-tenant, 403 sem permissão).
- Frontend: `AccountsPayableListView.spec.ts` — carregamento da lista, filtro por status, baixa, reversão, estado vazio, mensagem de erro.

## 9. Riscos e notas abertas

1. **A tela fica vazia até a Compra existir**: como a criação é exclusivamente via `createInstallments` (chamado pela futura Compra, sub-projeto 5), não há nenhum jeito de popular dados reais nesta fatia — mesmo trade-off já aceito no Estoque mínimo, mas aqui com uma tela de verdade esperando dados, não só um endpoint de inspeção via API.
2. **Reversão sem limite de tempo**: como não há efeito de caixa a desfazer, a reversão de uma baixa é permitida a qualquer momento, sem trava — se isso se mostrar um problema operacional (ex. reverter uma baixa de meses atrás), pode precisar de uma janela de tempo ou permissão mais restrita no futuro.
3. **Contas a receber**: fica documentada aqui como sub-projeto futuro, não como extensão silenciosa desta fatia — quando Vendas (documento fiscal de saída) for construída, provavelmente vai precisar do mesmo tipo de decisão de escopo tomada aqui.
