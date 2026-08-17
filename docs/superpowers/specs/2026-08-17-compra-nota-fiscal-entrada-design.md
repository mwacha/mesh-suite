# Compra (Nota Fiscal de Entrada) — Spec de Design

> Sub-projeto 5 de 5 da iniciativa "Compras completa". Ver `docs/superpowers/specs/2026-08-04-ordem-compra-design.md` seção 1 para a decomposição inteira. Este documento amarra os 4 sub-projetos já prontos: Ordem de Compra (`PurchaseOrder`), Estoque mínimo (`StockService.adjustBalance`), Financeiro mínimo (`AccountsPayableService.createInstallments`) e Cálculo fiscal simplificado (`FiscalCalculationService`).

## 1. Contexto e decisão

`PRD-07-compras.md` documenta dois fluxos: **Ordem de Compra** (documento interno, já construído) e **Compra** (a nota fiscal de entrada em si — o documento fiscal que, lançado, atualiza estoque, gera parcelas financeiras e opcionalmente registra transporte). Este é o sub-projeto que fecha o item 5 do `ORDEM-EXECUCAO.md`.

O PRD deixa dois pontos explicitamente em aberto ("requer validação com o time"), resolvidos nesta fatia:

1. **Relação Ordem de Compra ↔ Compra** (PRD seção 8, risco 3): decidido **vínculo obrigatório 1:1** — mesmo padrão já usado em `Sale`↔`SalesOrder`. Uma `PurchaseInvoice` só nasce faturando uma `PurchaseOrder` existente em status `OPEN`, com FK única. Ao emitir, a `PurchaseOrder` avança para `RECEIVED` atomicamente na mesma transação.
2. **Regeneração destrutiva ao editar** (PRD regra de negócio 8, risco 1): decidido que `PurchaseInvoice` é **imutável** — só criar e ler, sem `PUT`/`DELETE`. Mesmo padrão já estabelecido em `Sale` (a contraparte de saída da mesma família "nota fiscal", segundo o próprio PRD seção 1). Evita por completo o risco de auditoria/rastreabilidade que o PRD sinaliza para a regeneração destrutiva de itens e movimentações de estoque a cada salvamento.

Nome da entidade: como `PurchaseOrder` já ocupa o prefixo "Purchase" para a Ordem de Compra, a nota fiscal de entrada se chama **`PurchaseInvoice`**, pacote `com.meshsuite.purchaseinvoice`, com `Module.PURCHASE_INVOICE` — mesmo par `Order`/`Sale` que já existe entre `SalesOrder` e `Sale`.

Investigação do código existente relevante:
- `StockMovementOrigin.PURCHASE` já existe, reservado desde o sub-projeto 2, sem chamador ainda — esta fatia é a primeira a usá-lo.
- `FiscalCalculationService.calculate(registration, quantity, unitPrice)` já é consumido por `SaleService`, mesmo padrão de uso aqui.
- `AccountsPayableService.createInstallments(tenantId, supplierId, referenceId, installments)` já existe, sem chamador ainda além de testes — esta fatia é a primeira a usá-lo de verdade.
- `SalesOrderService`/`SaleService` (o par `Order`/`Sale` já implementado) é o modelo mais próximo do fluxo desta fatia, incluindo o ajuste de trava em `updateStatus` para impedir que o status "faturado" seja alcançado fora do fluxo de emissão.

## 2. Escopo desta fatia

### Incluído
- Entidades `PurchaseInvoice` e `PurchaseInvoiceItem`.
- Fluxo de emissão: converte 1 `PurchaseOrder` (`OPEN`) em 1 `PurchaseInvoice`, copiando fornecedor/itens/totais e calculando tributos por item via `FiscalCalculationService`.
- Ao emitir: dispara `StockService.adjustBalance` (INBOUND, origem `PURCHASE`) para cada item, e `AccountsPayableService.createInstallments` para as parcelas informadas pelo usuário — tudo na mesma transação da emissão.
- Validações obrigatórias do PRD: número/série/modelo da nota (regra 1), bloqueio de nota duplicada por fornecedor (regra 2), soma das parcelas = total da nota (regra 5), data de entrada ≥ data de emissão (regra 7).
- Leitura de `PurchaseInvoice`: listagem paginada e busca por id. Sem edição/exclusão.
- Permissão dedicada (`Module.PURCHASE_INVOICE`), enforcement via `@RequiresPermission`, mesmo padrão das demais fatias.
- Frontend: ação "Lançar Compra" em `PurchaseOrdersListView` (substituindo a atual "Marcar como Recebida"), tela de formulário para os dados da nota + parcelas, tela de listagem de Notas de Entrada.

### Fora de escopo (decisão já tomada ou dependência de domínio não construído)
- **Importação de NF-e via XML** — lançamento sempre manual; a leitura automática do arquivo de nota fiscal eletrônica é capacidade compartilhada do domínio Fiscal/Tributário completo, não construída.
- **Frete e Conhecimento de Transporte** — domínio Expedição/Logística não existe no sistema novo; nenhum registro de frete nesta fatia, mesmo quando por conta do destinatário.
- **Código de indicador de pagamento fixo** (PRD seção 8, risco 2) — sem significado documentado no legado e sem consumidor (não há SPED); descartado nesta fatia.
- **Chave de acesso, natureza da operação, dados de contingência, indicador de crédito ICMS/Simples Nacional, indicador de redução ICMS-ST, nome do produto conforme fornecedor, atualização do vínculo produto×fornecedor** — parte da base ampla de ~95 campos do PRD, sem consumidor construído (sem emissão de NF-e, sem SPED); mesmo recorte já aplicado por todo sub-projeto irmão desta iniciativa.
- **Edição e exclusão** — `PurchaseInvoice` é criar-e-ler apenas (ver decisão na seção 1). Um lançamento errado não tem correção nesta fatia.
- **Compra sem Ordem de Compra de origem** ("compra avulsa") — vínculo 1:1 obrigatório (ver decisão na seção 1).
- **Cálculo tributário granular** (ICMS-ST com MVA, IPI com redução, PIS/COFINS com tipo de redução) — reaproveita o `FiscalCalculationService` simplificado já existente, mesma redução de complexidade já aplicada a `Sale`.
- **Emissão de NF-e** — fica com o domínio Fiscal (`PRD-11`), quando esse item da ordem de execução for alcançado.

## 3. Modelo de dados

### `PurchaseInvoice` (tabela `purchase_invoice` — RLS por tenant direto, mesmo padrão de `purchase_order`/`sale`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `number` | Integer | sequencial interno por tenant, via `PurchaseInvoiceCounter` (mirror de `SaleCounter`/`PurchaseOrderCounter`) — não é o número da nota fiscal em si |
| `invoiceNumber` | String | número da nota fiscal (regra 1 do PRD) |
| `series` | String | série da nota (regra 1) |
| `model` | String | modelo da nota (regra 1) |
| `purchaseOrder` | FK → `PurchaseOrder` | `nullable = false`, **único** — vínculo estrutural 1:1, mesmo padrão de `Sale.order` |
| `supplier` | FK → `Partner` | copiado da `PurchaseOrder` no momento da emissão (snapshot, mesmo padrão de `Sale.customer`) |
| `issueDate` | LocalDate | data de emissão da nota (regra 1) — informada pelo usuário, não default-hoje |
| `entryDate` | LocalDate | data de entrada da mercadoria (regra 1); deve ser `>= issueDate` (regra 7) |
| `discount`, `subtotal`, `total` | BigDecimal(12,2) | copiados da `PurchaseOrder` |
| `icmsAmount`, `ipiAmount`, `pisAmount`, `cofinsAmount` | BigDecimal(12,2) | soma dos valores dos itens |
| `createdAt` | Instant | `updatable = false` |

Unique constraint `(supplier_id, invoice_number)` — bloqueio de nota duplicada por fornecedor (regra 2). Sem campo de status — a existência da linha já significa "lançada"; sem `updatedAt`.

### `PurchaseInvoiceItem` (tabela `purchase_invoice_item` — RLS via `EXISTS` no `purchase_invoice` pai, mesmo padrão de `sale_item`/`purchase_order_item`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `purchaseInvoice` | FK → `PurchaseInvoice` | |
| `product` | FK → `Product` | copiado do `PurchaseOrderItem` correspondente |
| `quantity`, `unitPrice`, `totalValue` | BigDecimal | copiados do `PurchaseOrderItem` |
| `icmsAmount`, `ipiAmount`, `pisAmount`, `cofinsAmount` | BigDecimal(12,2) | resultado de `FiscalCalculationService.calculate(produto.getFiscalRegistration(), quantity, unitPrice)` — mesmo padrão de `SaleItem` |

### `PurchaseInvoiceCounter` (tabela de contador, um registro por tenant — mirror de `SaleCounter`/`PurchaseOrderCounter`)

## 4. Fluxo de emissão

`PurchaseInvoiceService.issue(UUID purchaseOrderId, PurchaseInvoiceRequest request, UUID userId)` — módulo `purchaseinvoice` depende de `purchaseorder`, `stock`, `payable` e `fiscal` (não o contrário):

1. Carrega a `PurchaseOrder`; se `status != OPEN`, lança `PurchaseInvoiceValidationException`.
2. Valida duplicidade: se já existe `PurchaseInvoice` com o mesmo `(supplier, invoiceNumber)`, lança exceção nomeando a nota (regra 2).
3. Valida `entryDate >= issueDate` (regra 7).
4. Cria a `PurchaseInvoice`: copia fornecedor/desconto/subtotal/total da `PurchaseOrder`; grava `invoiceNumber`/`series`/`model`/`issueDate`/`entryDate` do request; gera `number` via `PurchaseInvoiceCounter` (mesmo mecanismo atômico `UPDATE ... RETURNING` dos demais contadores).
5. Para cada `PurchaseOrderItem`: cria `PurchaseInvoiceItem` copiando produto/quantidade/valor; valida `product.getFiscalRegistration() != null` (senão exceção nomeando o produto, mesma regra do `Sale`); chama `FiscalCalculationService.calculate(...)` e preenche os 4 tributos do item; acumula nos totais da nota.
6. Valida que a soma dos `installments` do request bate com o `total` da nota (regra 5) — senão lança exceção.
7. Salva a `PurchaseInvoice` (itens em cascade).
8. Para cada item, chama `StockService.adjustBalance(tenantId, productId, INBOUND, quantity, StockMovementOrigin.PURCHASE, referenceId=purchaseInvoice.id, userId, note=null)`.
9. Chama `AccountsPayableService.createInstallments(tenantId, supplier.id, referenceId=purchaseInvoice.id, installments)`.
10. Define `purchaseOrder.status = RECEIVED` e salva.

Todo o método roda em uma única `@Transactional` — as validações (passos 1–3) e as gravações/disparos (passos 4–10) compartilham a mesma transação, mesmo padrão de `SaleService.issue`.

### Ajuste em código existente

`PurchaseOrderService.updateStatus` passa a **rejeitar** `newStatus == RECEIVED`. Motivo idêntico ao já aplicado em `SalesOrderService` quando `Sale` foi introduzido: sem essa trava, `PATCH /api/purchase-orders/{id}/status` deixaria uma `PurchaseOrder` chegar a `RECEIVED` sem nunca gerar a `PurchaseInvoice` correspondente, quebrando a garantia da FK única em `PurchaseInvoice.purchaseOrder`. `RECEIVED` só é alcançável através de `issue`. `CANCELLED` continua liberado via `updateStatus` normalmente. Esta é a única alteração em código já funcionando nesta fatia.

### Concorrência

Requisito não-funcional do PRD (seção 7): proteção contra execução concorrente do salvamento. Coberta pelo mesmo padrão já usado em todo o sistema — contador atômico (`UPDATE ... RETURNING`) mais a transação única cobrindo os passos 1–10: se duas emissões concorrentes mirarem a mesma `PurchaseOrder`, a segunda falha na checagem de status (`OPEN`) porque a primeira já moveu para `RECEIVED` dentro de sua transação. Sem lock explícito adicional.

## 5. API (backend)

DTOs:
- `PurchaseInvoiceRequest(invoiceNumber, series, model, issueDate, entryDate, installments: List<InstallmentInput>)`, `InstallmentInput(amount, dueDate)` — mesmo shape de `AccountsPayableInstallmentInput`.
- `PurchaseInvoiceResponse` — cabeçalho completo + `purchaseOrderId`/`purchaseOrderNumber` + `items: List<PurchaseInvoiceItemResponse>`.
- `PurchaseInvoiceSummaryResponse` — número interno, número da nota, fornecedor, data de emissão, total.

Endpoints:
- `POST /api/purchase-invoices/issue/{purchaseOrderId}` → `PurchaseInvoiceService.issue`, retorna `201` com `PurchaseInvoiceResponse`.
- `GET /api/purchase-invoices` → paginado, filtro por busca (fornecedor/número da nota), mesmo padrão de `PurchaseOrderController.list`.
- `GET /api/purchase-invoices/{id}` → `PurchaseInvoiceResponse` com itens.

Sem `PUT`/`DELETE`. Todos os métodos protegidos por `@RequiresPermission`: `VIEW` para os dois `GET`, `CREATE` para `issue`. `Module.PURCHASE_INVOICE` é um novo valor no enum (hoje: `CUSTOMER, PRODUCT, ORDER, USER, PURCHASE, STOCK, PAYABLE, SALE`), com migration adicionando o valor ao `CHECK` constraint de `user_permission` — mesmo padrão da `V27` que adicionou `SALE`.

### Erros

- `PurchaseInvoiceValidationException` — Ordem de Compra fora de `OPEN`; produto sem `fiscalRegistration`; nota duplicada para o fornecedor; soma das parcelas ≠ total da nota; `entryDate < issueDate`.
- `PurchaseInvoiceNotFoundException` — id inexistente em `GET /api/purchase-invoices/{id}`.

Ambas registradas no `shared/handler/GlobalExceptionHandler` já existente.

## 6. Frontend

- **`src/api/purchaseInvoices.ts`** (novo, mesmo padrão de `purchaseOrders.ts`): tipos `PurchaseInvoiceResponse`, `PurchaseInvoiceSummaryResponse`, `PurchaseInvoiceItemResponse`, `InstallmentInput`; `issuePurchaseInvoice(purchaseOrderId, request)` → `POST /purchase-invoices/issue/{id}`, `listPurchaseInvoices(params)`, `getPurchaseInvoice(id)`.
- **`src/views/PurchaseInvoicesListView.vue`** (novo, rota `/notas-fiscais-entrada`) — somente leitura: busca, paginação, tabela (número interno, número da nota, fornecedor, data emissão, total). Sem ações de editar/excluir.
- **`src/views/PurchaseInvoiceFormView.vue`** (novo, rota `/compras/:id/nota-fiscal`) — diferente do fluxo de `Sale` (botão direto, sem tela): aqui precisa de tela própria porque o usuário informa dados reais da nota física recebida. Carrega a `PurchaseOrder` (fornecedor/itens/total, somente leitura) e apresenta campos editáveis: número da nota, série, modelo, data de emissão, data de entrada, e uma lista dinâmica de parcelas (valor + vencimento, adicionar/remover linha) com validação em tempo real de que a soma bate com o total antes de habilitar o botão salvar (espelha a regra 5 do backend, também client-side). Ao salvar, chama `issuePurchaseInvoice` e navega para o detalhe/lista.
- **`PurchaseOrdersListView.vue`** (ajuste em código existente): a ação atual "Marcar como Recebida" (hoje chama `updatePurchaseOrderStatus(ordem.id, 'RECEIVED')` diretamente) é **substituída** por "Lançar Compra", que navega para `/compras/${ordem.id}/nota-fiscal` — mesma troca já feita em `PedidosListView` quando `Venda` foi introduzida. Continua visível só quando `status === 'OPEN'`.
- **Router**: `{ path: '/compras/:id/nota-fiscal', name: 'compras-nota-fiscal', component: PurchaseInvoiceFormView }` e `{ path: '/notas-fiscais-entrada', name: 'notas-fiscais-entrada', component: PurchaseInvoicesListView }`.
- **`AppSidebar.vue`**: novo item `{ icon: '🧾', label: 'Notas de Entrada', route: '/notas-fiscais-entrada' }` no mesmo grupo `COMPRAS` que já tem "Compras" (Ordem de Compra).
- **Permissão**: `PURCHASE_INVOICE` entra em `ModuleName`/`MODULES`/`MODULE_LABELS` e na matriz `DEFAULT_MATRIX` de `UserFormView.vue`, mesmo padrão já feito para `SALE`.

## 7. Testes

- Backend: `PurchaseInvoiceServiceTest` (emissão feliz — verifica `PurchaseOrder` vira `RECEIVED`, `StockMovement` INBOUND por item com `origin=PURCHASE`/`referenceId` correto, parcelas de `AccountsPayable` criadas com `referenceId` correto, soma dos tributos; Ordem de Compra fora de `OPEN`; produto sem `fiscalRegistration`; nota duplicada; soma de parcelas ≠ total; `entryDate < issueDate`); `PurchaseInvoiceControllerTest` (emitir, listar, buscar, `403` sem permissão, via MockMvc + Testcontainers); `PurchaseInvoiceRepositoryTest` (isolamento RLS entre tenants); ajuste em `PurchaseOrderServiceTest` cobrindo que `updateStatus` agora rejeita `RECEIVED`.
- Frontend: vitest para `PurchaseInvoicesListView` (busca, paginação) e `PurchaseInvoiceFormView` (validação de soma de parcelas, submissão); ajuste no spec de `PurchaseOrdersListView` cobrindo a nova ação "Lançar Compra" em vez de "Marcar como Recebida".

## 8. Riscos e notas abertas

1. **Sem importação de NF-e (XML)**: lançamento sempre manual; a leitura automática de nota eletrônica é capacidade do domínio Fiscal/Tributário completo, não construída.
2. **Sem frete/Conhecimento de Transporte**: domínio Expedição/Logística não existe; nenhum registro de frete nesta fatia, mesmo quando por conta do destinatário.
3. **Código de indicador de pagamento fixo** (PRD seção 8, risco 2, sem significado documentado no legado): descartado nesta fatia por falta de consumidor (SPED não existe); precisa de investigação dedicada se reintroduzido depois.
4. **Sem estorno/edição**: um lançamento errado não tem correção nesta fatia — mesma limitação já aceita em `Sale`.
5. **Vínculo 1:1 obrigatório com Ordem de Compra**: não cobre eventual necessidade futura de "compra avulsa" sem OC prévia; se precisar depois, exige nova investigação e provavelmente um segundo fluxo de criação.
6. **Cálculo fiscal simplificado herdado** do sub-projeto 4: mesma simplificação (percentual fixo sobre a mesma base, sem ICMS-ST, sem Simples Nacional) já documentada lá; não válido para uso fiscal real.
