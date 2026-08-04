# Ordem de Compra (PurchaseOrder) — Spec de Design

> Sub-projeto 1 de 5 da iniciativa "Compras completa". Ver seção 1 para a decomposição inteira.

## 1. Contexto e decisão

`ORDEM-EXECUCAO.md` prioriza Compras (`PRD-07-compras.md`) como o item 5, logo após Pedidos/Vendas. O PRD documenta dois fluxos distintos, no mesmo padrão já visto em Vendas (Pedido vs. Venda):

1. **Ordem de Compra** — documento interno, sem efeito fiscal, sem dependência de outro domínio de negócio.
2. **Compra (Nota Fiscal de Entrada)** — documento fiscal pesado: gera contas a pagar (Financeiro), atualiza saldo/movimentação (Estoque), usa cálculo tributário por item (Fiscal/Tributário), pode gerar Conhecimento de Transporte (Expedição). Nenhuma dessas dependências existe ainda no sistema novo.

Decisão tomada com o usuário: implementar a iniciativa completa "Compras" (Ordem de Compra + Compra), o que exige decompor em 5 sub-projetos sequenciais, cada um com seu próprio ciclo spec → plano → implementação:

1. **Ordem de Compra** (este documento) — documento interno, zero dependências novas.
2. **Estoque mínimo** — saldo e movimentação de produto (necessário pra Compra debitar/creditar estoque).
3. **Financeiro mínimo** — contas a pagar (necessário pra Compra gerar parcelas).
4. **Cálculo fiscal simplificado** — só o essencial de ICMS/IPI/PIS/COFINS por item necessário pra Compra funcionar, não o `PRD-11` inteiro.
5. **Compra (Nota Fiscal de Entrada)** — amarra os 4 anteriores.

Este documento cobre **apenas o sub-projeto 1**. Os demais serão brainstormados individualmente, em sequência, cada um partindo do estado real do sistema no momento em que começar (não assumido antecipadamente aqui).

## 2. Escopo desta rodada

### Incluído
- Cadastro de Ordem de Compra: fornecedor, comprador, data da ordem, data de entrega esperada, itens (produto/quantidade/valor unitário), desconto, subtotal, total.
- Ciclo de vida simples: `OPEN → RECEIVED` ou `OPEN → CANCELLED` (ambos terminais).
- Listagem com busca/filtro por status, mesmo padrão visual de `PedidosListView`.
- Permissão dedicada (`Module.PURCHASE`), com enforcement real via `@RequiresPermission`, mesmo padrão de Pedido/Parceiro/Produto.

### Fora de escopo (decisão já tomada ou dependência de domínio não construído)
- **Compra (nota fiscal de entrada)** — sub-projeto 5 desta mesma iniciativa.
- **Cronograma de entregas parciais** (múltiplas entregas por ordem, cada uma com seu próprio subconjunto de itens) — o PRD documenta essa estrutura, mas foi simplificada para uma lista plana de itens + uma única data de entrega esperada, mesma redução de complexidade já aplicada a outras fatias.
- **Condição de pagamento** e **tabela de preço** — o PRD lista esses campos para Ordem de Compra, mas Pedido já tinha os mesmos campos em seu PRD de origem e a fatia implementada os removeu; mantendo consistência, ficam fora aqui também.
- **Telefone** (campo do PRD) — era uma cópia do telefone do fornecedor no momento do lançamento no legado; redundante, já que o cadastro de Parceiro tem esse dado.
- **Vínculo estrutural com a futura Compra** — o PRD documenta essa relação como não confirmada no sistema legado ("requer validação com o time"); a referência (se necessária) será desenhada no sub-projeto 5, quando Compra existir.
- **"Tipos" de Ordem de Compra** que alteram o comportamento da tela — o PRD sinaliza a existência disso mas não confirma o detalhe; fora de escopo até haver necessidade concreta.
- Card de Ordens de Compra no Dashboard — pode ser adicionado depois, sem misturar escopo com esta fatia.

## 3. Modelo de dados

### `PurchaseOrder` (tabela `purchase_order` — RLS por tenant, mesmo padrão de `pedido`/`parceiro`/`produto`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `numero` | Integer | sequencial por tenant, via `PurchaseOrderCounter` (mirror de `PedidoContador`) |
| `supplier` | FK → `Parceiro` | deve ter `PapelParceiro.FORNECEDOR` (reaproveita `comPapel`, já existente) |
| `buyer` | FK → `User` | deve ter `Role.ADMINISTRATIVE` |
| `orderDate` | LocalDate | default hoje |
| `expectedDeliveryDate` | LocalDate | nullable |
| `status` | `PurchaseOrderStatus` | default `OPEN` |
| `discount` | BigDecimal(12,2) | default 0 |
| `subtotal` | BigDecimal(12,2) | soma dos itens, calculado no service |
| `total` | BigDecimal(12,2) | `subtotal - discount`, calculado no service |
| `createdAt` | Instant | `updatable = false` |
| `items` | `List<PurchaseOrderItem>` | `@OneToMany`, cascade all + orphanRemoval, mesmo padrão de `ItemPedido` |

### `PurchaseOrderItem` (tabela `purchase_order_item` — RLS via `EXISTS` no `purchase_order` pai, mesmo padrão de `item_pedido`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `purchaseOrder` | FK → `PurchaseOrder` | |
| `product` | FK → `Produto` | |
| `quantity` | BigDecimal(12,3) | > 0, mesmo tipo de `ItemPedido.quantidade` |
| `unitPrice` | BigDecimal(12,2) | client-submitted/editável, mesmo padrão já aprovado em `ItemPedido.valorUnitario` |
| `totalValue` | BigDecimal(12,2) | `quantity * unitPrice`, calculado no service |

### `PurchaseOrderCounter` (tabela de contador, um registro por tenant — mirror de `PedidoContador`)

Mesmo mecanismo de numeração sequencial já usado em Pedido.

## 4. Regras de negócio

1. Fornecedor deve ter o papel `FORNECEDOR` e estar ativo (validado no service, mesmo padrão de `buscarVendedorValido` em `PedidoService`, adaptado pra `buscarFornecedorValido`).
2. Comprador deve ter `Role.ADMINISTRATIVE` e estar ativo.
3. Ordem precisa de ao menos um item.
4. Desconto não pode exceder o subtotal (regra 6 do PRD-07, preservada).
5. Transição de status só é permitida a partir de `OPEN`; uma vez `RECEIVED` ou `CANCELLED`, a ordem é terminal — sem edição de fornecedor/comprador/itens a partir daí (mesma trava que Pedido aplica após `FATURADO`).
6. Exclusão física suportada (`DELETE /api/purchase-orders/{id}`) — correção em relação à primeira versão desta spec: Pedido/Parceiro/Produto **têm**, sim, exclusão física real (não só mudança de status); só User não tem, por causa de uma FK sem cascade que não se aplica aqui. Mantém consistência com o padrão real das fatias irmãs.
7. `subtotal`/`total` sempre recalculados no service a partir dos itens recebidos, nunca confiados ao client como valor final (mesmo padrão de `PedidoService`).

## 5. Telas

### `PurchaseOrdersListView.vue` (rota `/compras`)
Mesmo padrão visual/estrutural de `PedidosListView.vue`: busca, filtro por status, tabela paginada, dropdown de Ações (Editar, "Marcar como Recebida"/"Cancelar" quando `OPEN`, Excluir).

### `PurchaseOrderFormView.vue` (rotas `/compras/novo` e `/compras/:id/editar`)
Mesmo padrão de `PedidoFormView.vue`: seletor de fornecedor (busca/dropdown, reaproveitando o padrão do seletor de cliente), seletor de comprador (reaproveitando o padrão do seletor de vendedor), lista de itens com busca de produto, cálculo de totais ao vivo, tratamento de 403 na submissão.

### `AppSidebar.vue`
Novo item "Compras" (ícone a definir — não há mockup de referência no `layout/` para esta tela), roteando para `/compras`.

## 6. API (backend)

- `GET /api/purchase-orders` — lista paginada, filtros `busca`/`status`.
- `GET /api/purchase-orders/resumo` — contagens por status, mesmo padrão de `PedidoResumoResponse`.
- `GET /api/purchase-orders/{id}`
- `POST /api/purchase-orders`
- `PUT /api/purchase-orders/{id}`
- `PATCH /api/purchase-orders/{id}/status` — recebe o status-alvo explicitamente (`RECEIVED` ou `CANCELLED`), diferente do "avançar" de Pedido, já que aqui `OPEN` tem dois destinos possíveis em vez de uma cadeia linear única.
- `DELETE /api/purchase-orders/{id}` — exclusão física, mesmo padrão de Pedido/Parceiro/Produto.

Todos os métodos de `PurchaseOrderService` protegidos por `@RequiresPermission(module = Module.PURCHASE, action = ...)`. `Module.PURCHASE` é um novo valor no enum `com.meshsuite.auth.Module`, cobrindo esta fatia e a futura Compra.

## 7. Frontend — arquivos e nomenclatura

Código novo, nomeado em inglês (diretiva já em vigor para geração nova, distinta do rename retroativo ainda pendente de Cliente/Produto/Pedido):

- `src/api/purchaseOrders.ts`
- `src/views/PurchaseOrderFormView.vue`
- `src/views/PurchaseOrdersListView.vue`

Rotas e texto visível continuam em português, consistentes com as fatias irmãs: `/compras`, `/compras/novo`, `/compras/:id/editar` (nomes de rota `compras`, `compras-novo`, `compras-editar`).

O módulo de permissão `PURCHASE` precisa entrar em `ModuleName`/`ActionName` no frontend e na matriz `DEFAULT_MATRIX` de `UserFormView.vue`.

## 8. Testes

Mesmo padrão das fatias anteriores:
- Backend: testes unitários de `PurchaseOrderService` (validações, cálculo de totais, transições de status), teste de integração RLS (`purchase_order`/`purchase_order_item` isolados por tenant, mesmo padrão de `PedidoRepositoryTest`), testes de `PurchaseOrderController` (CRUD, 403 sem permissão, 404 cross-tenant).
- Frontend: vitest com mocks de API para `PurchaseOrdersListView`/`PurchaseOrderFormView`, seguindo os mesmos casos já cobertos em `PedidosListView`/`PedidoFormView` (busca, paginação, transição de status, 403).

## 9. Riscos e notas abertas

1. **Sub-projetos 2-5 ainda não especificados**: este documento cobre só o sub-projeto 1. A forma exata de Estoque mínimo, Financeiro mínimo e cálculo fiscal simplificado será decidida em brainstorming próprio de cada um, no momento em que começarem — não deve ser assumida aqui.
2. **Vínculo Ordem de Compra ↔ Compra**: deliberadamente não modelado nesta fatia (ver seção 2). Quando o sub-projeto 5 for desenhado, precisará decidir se adiciona uma FK em `Compra` apontando para `PurchaseOrder` (unidirecional) ou se os dois documentos continuam desconectados, como no legado.
3. **Ícone do item de sidebar "Compras"**: sem mockup de referência; será escolhido durante a implementação, sem impacto funcional.
