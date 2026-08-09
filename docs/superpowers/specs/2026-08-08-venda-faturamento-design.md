# Venda (Faturamento de Pedido) — Spec de Design

## 1. Contexto e decisão

`ORDEM-EXECUCAO.md` marca o item 2 (Cadastro Comercial) como concluído e o item 3 (Pedidos, `PRD-12-vendas.md`) já está implementado no código (`mesh-suite-backend/.../pedido`). O próximo item da ordem é o 4 — Vendas, mesmo PRD-12, seção "Venda"/"Item de Venda": o documento fiscal de saída emitido a partir de um Pedido faturado.

O PRD-12 marca explicitamente como "requer investigação dedicada" três pontos: o vínculo Pedido↔Venda (no legado é referência solta, o PRD recomenda FK estrutural), o mecanismo de povoamento dos itens de Venda a partir dos itens de Pedido, e o fluxo de status completo do Pedido (Exportado/Importado/Autorização). Este documento resolve os dois primeiros para esta fatia; o terceiro permanece fora de escopo — o `StatusPedido` atual (`DIGITADO/EM_PREPARO/FATURADO`) já é uma redução deliberada do legado, preservada como está.

Investigação do código existente relevante para as decisões abaixo:
- `PurchaseOrder` (Ordem de Compra) já existe e **não** dispara `AccountsPayable` nem `StockMovement` automaticamente — nenhum módulo do sistema ainda tem esse tipo de disparo entre documentos.
- `AccountsPayable` só cobre contas a **pagar**; não existe módulo de contas a receber.
- `Produto.fiscalRegistration` já existe e é opcional; `FiscalCalculationService.calculate(registration, quantidade, valorUnitario)` já calcula ICMS/IPI/PIS/COFINS por taxa percentual fixa, mas não está exposto por nenhum controller ainda.
- `StockMovementOrigin` só tem `MANUAL, PURCHASE` (sem `SALE`).

## 2. Escopo desta fatia

### Incluído
- Entidades `Venda` e `ItemVenda`, mesma estrutura de `Pedido`/`ItemPedido`.
- Fluxo de faturamento: converte 1 Pedido (`EM_PREPARO`) em 1 Venda, copiando os itens e calculando tributos por item via `FiscalCalculationService` já existente.
- Leitura de Venda: listagem paginada e busca por id. Sem edição/exclusão — documento imutável uma vez emitido.
- Permissão dedicada (`Module.SALE`), enforcement via `@RequiresPermission`, mesmo padrão das demais fatias.
- Frontend: ação "Faturar" no `PedidosListView`, tela de listagem de Vendas.

### Fora de escopo (decisão já tomada ou dependência de domínio não construído)
- **Baixa de estoque e título a receber automáticos** ao salvar a Venda — nenhum módulo do sistema dispara esse tipo de efeito colateral entre documentos ainda (mesma situação de `PurchaseOrder`); não existe módulo de contas a receber. Fica para quando Estoque/Financeiro tiverem esse gancho desenhado, como já ocorreu em outras fatias.
- **Agrupamento de Pedido** (N pedidos → 1 Venda) — o PRD marca esse fluxo como não mapeado; vínculo aqui é 1:1.
- **Cancelamento de Venda** — não confirmado no PRD; Venda é criar-e-ler apenas nesta fatia.
- **Cálculo tributário granular** (ICMS-ST com MVA, IPI com redução, PIS/COFINS com tipo de redução) — reaproveita o `FiscalCalculationService` simplificado já existente (taxas percentuais fixas por `FiscalRegistration`), mesma redução de complexidade já aplicada ao restante do sistema. Pertence ao domínio Fiscal/Tributário completo (item 8 da ordem de execução) quando esse item for alcançado.
- **Emissão de NF-e** — fica com o domínio Fiscal (`PRD-11`).
- **Fluxo de status completo do Pedido** (Exportado/Importado/Autorização) — fora de escopo, como em todas as fatias anteriores de Pedido.

## 3. Modelo de dados

### `Venda` (tabela `venda` — RLS por tenant, mesmo padrão de `pedido`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `numero` | Integer | sequencial por tenant, via `VendaContador` (mirror de `PedidoContador`, mesmo mecanismo atômico `UPDATE ... RETURNING`) |
| `pedido` | FK → `Pedido` | `nullable = false`, **único** (constraint `UNIQUE`) — vínculo formal 1:1, diferente da referência solta do legado |
| `cliente` | FK → `Parceiro` | copiado do Pedido no momento do faturamento (snapshot) |
| `vendedor` | FK → `User` | copiado do Pedido |
| `dataEmissao` | LocalDate | default hoje |
| `desconto`, `subtotal`, `total` | BigDecimal(12,2) | copiados/recalculados do Pedido |
| `valorIcms`, `valorIpi`, `valorPis`, `valorCofins` | BigDecimal(12,2) | soma dos valores dos itens |
| `criadoEm` | Instant | `updatable = false` |
| `itens` | `List<ItemVenda>` | `@OneToMany`, cascade all + orphanRemoval |

Sem campo de status — a existência da linha já significa "emitida"; sem endpoint de edição/exclusão.

### `ItemVenda` (tabela `item_venda` — RLS via `EXISTS` no `venda` pai)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `venda` | FK → `Venda` | |
| `produto` | FK → `Produto` | copiado do `ItemPedido` correspondente |
| `quantidade`, `valorUnitario`, `valorTotal` | BigDecimal | copiados do `ItemPedido` |
| `valorIcms`, `valorIpi`, `valorPis`, `valorCofins` | BigDecimal(12,2) | resultado de `FiscalCalculationService.calculate(produto.getFiscalRegistration(), quantidade, valorUnitario)` |

### `VendaContador` (tabela de contador, um registro por tenant — mirror de `PedidoContador`/`PurchaseOrderCounter`)

## 4. Fluxo de faturamento

`VendaService.faturar(UUID pedidoId)` — módulo `venda` depende de `pedido` (não o contrário):

1. Carrega o `Pedido`; se `status != EM_PREPARO`, lança `VendaValidacaoException`.
2. Copia cliente, vendedor, desconto, subtotal, total do Pedido para uma nova `Venda`.
3. Para cada `ItemPedido`: cria um `ItemVenda` copiando produto/quantidade/valor; valida que `produto.getFiscalRegistration() != null` (senão `VendaValidacaoException` nomeando o produto); chama `FiscalCalculationService.calculate(...)` e preenche os 4 campos de tributo do item.
4. Soma os tributos dos itens nos totais da Venda.
5. Gera `numero` via `VendaContador`.
6. Salva a Venda; define `pedido.status = FATURADO` e salva o Pedido — tudo na mesma `@Transactional`.

### Ajuste em código existente

`PedidoService.avancarStatus` passa a **rejeitar** `novoStatus == FATURADO`. Motivo: sem essa trava, `PATCH /api/pedidos/{id}/status` deixaria um Pedido chegar a `FATURADO` sem nunca criar a Venda correspondente, quebrando a garantia que a FK única em `Venda.pedido` deveria dar. `FATURADO` só é alcançado através do fluxo de faturamento, que cria a Venda e avança o status atomicamente na mesma transação. Esta é a única alteração em código já funcionando nesta fatia.

## 5. API (backend)

- `POST /api/vendas/faturar/{pedidoId}` → `VendaService.faturar`, retorna `201` com `VendaResponse`.
- `GET /api/vendas` → paginado, filtro por busca (mesmo padrão de `PedidoController.listar`).
- `GET /api/vendas/{id}` → `VendaResponse` com itens.

Sem `PUT`/`DELETE`. Todos os métodos protegidos por `@RequiresPermission(module = Module.SALE, action = ...)`. `Module.SALE` é um novo valor no enum `com.meshsuite.auth.domain.enums.Module` (hoje: `CUSTOMER, PRODUCT, ORDER, USER, PURCHASE, STOCK, PAYABLE`).

### Erros

- `VendaValidacaoException` — Pedido fora de `EM_PREPARO`; produto sem `fiscalRegistration`.
- `VendaNaoEncontradaException` — id inexistente em `GET /api/vendas/{id}`.

Ambas registradas no `shared/handler/GlobalExceptionHandler` já existente, junto com as demais exceções de domínio mapeadas ali.

## 6. Frontend

- **`src/api/vendas.ts`** (novo, mesmo padrão de `pedidos.ts`): tipos `VendaResponse`, `VendaSummary`, `ItemVendaResponse`; `listarVendas(params)`, `buscarVenda(id)`, `faturarPedido(pedidoId): Promise<VendaResponse>` → `POST /vendas/faturar/{pedidoId}`.
- **`src/views/VendasListView.vue`** (novo, rota `/vendas`) — somente leitura: busca, paginação, tabela (número, cliente, data emissão, total). Sem ações de editar/excluir. Sem form view — Venda não é criada por formulário, só pelo fluxo de faturar.
- **`src/views/PedidosListView.vue`** (ajuste em código existente): a ação "Avançar para Faturado" (`EM_PREPARO → FATURADO`), hoje via `avancarStatusPedido`, será bloqueada nesse alvo pelo backend. Troca específica: quando `PROXIMO_STATUS[status] === 'FATURADO'`, o item do menu de ações vira `{ label: 'Faturar', action: () => faturar(pedido) }`, chamando `faturarPedido` (novo `api/vendas.ts`). O caminho `DIGITADO → EM_PREPARO` continua inalterado.
- **Router** (`src/router/index.ts`): `{ path: '/vendas', name: 'vendas', component: VendasListView }`.
- **`AppSidebar.vue`**: novo item `{ icon: '💰', label: 'Vendas', route: '/vendas' }`, mesmo grupo de "Pedidos".
- **Permissão**: `SALE` entra em `ModuleName`/`ActionName` e na matriz `DEFAULT_MATRIX` de `UserFormView.vue`, mesmo padrão já feito para `PURCHASE` em ordem-compra.

## 7. Testes

- Backend: `VendaServiceTest` (faturamento feliz; pedido fora de `EM_PREPARO`; produto sem cadastro fiscal); `VendaControllerTest` (faturar, listar, buscar, 403 sem permissão) via MockMvc + Testcontainers; `VendaRepositoryTest` se houver query customizada de listagem; ajuste em `PedidoServiceTest` cobrindo que `avancarStatus` agora rejeita `FATURADO`.
- Frontend: vitest para `VendasListView` (busca, paginação); ajuste no spec de `PedidosListView` cobrindo a nova ação "Faturar" em vez de "Avançar para Faturado".

## 8. Riscos e notas abertas

1. **Sem baixa de estoque/título a receber**: ao contrário do fluxo completo descrito no PRD ("ao salvar, a Venda dispara baixa de estoque e título financeiro"), esta fatia não implementa esses disparos — consistente com o estado atual do sistema (nem `PurchaseOrder` os dispara). Quando Estoque/Financeiro tiverem esse gancho desenhado, a Venda precisará ser revisitada para acionar os dois.
2. **Cancelamento de Venda não modelado**: o PRD não confirma esse fluxo no legado; se precisar depois, exigirá campo de status e nova investigação.
3. **`StatusPedido` continua reduzido**: esta fatia não expande para o fluxo completo do legado (Exportado/Importado/Autorização). Se esse mapeamento for feito no futuro, o fluxo de faturamento aqui desenhado (gatilho em `EM_PREPARO`) pode precisar mudar de estado de origem.
