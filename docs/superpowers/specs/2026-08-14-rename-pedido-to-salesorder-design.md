# Rename Pedido → SalesOrder — Design

## Context

Part of the multi-sub-project initiative renaming mesh-suite's code identifiers
(Java packages/classes/methods, frontend code, DB tables/columns) from Portuguese to
English, keeping every end-customer-visible string (routes, UI labels, error
messages) unchanged. Completed so far: Venda→Sale (`0e354fd`), Empresa→Company
(`4b93bd1`), Parceiro→Partner (`003a43b`), Categoria→Category/CorEstampa→Colorway
(`9855c26`), Produto→Product (`ae29430`), TabelaPreco→PriceTable (`499df4a`),
Município→Municipality (`0362424`).

`Pedido` (sales order) is the last remaining sub-project of the initiative. It is
the most cross-module-coupled module renamed since Parceiro/Produto: the `Sale`
module (already renamed, merged) directly depends on `Pedido` — `SaleService.issue()`
loads a `Pedido`, validates its status, and copies its data into a new `Sale` —
and the already-merged `sale` migration has a foreign key pointing at the `pedido`
table.

## Scope

`com.meshsuite.pedido` (its own top-level package, straight in-place rename):
`Pedido.java`, `ItemPedido.java`, `PedidoContador.java`, `StatusPedido.java`,
`PedidoRepository.java`, `PedidoContadorRepository.java`, `PedidoSpecifications.java`,
`PedidoController.java`, `PedidoService.java`, 7 DTOs, 3 exception classes, migration
`V7__create_pedido.sql`, and the 3 backend test classes. Frontend:
`api/pedidos.ts` + spec, `PedidoFormView.vue` + spec, `PedidosListView.vue` + spec.

Confirmed via full-codebase grep: no purchaseorder, stock, or other module
references `Pedido`/`pedido` beyond what's listed in the Cross-module bridge
section below.

## Package

`com.meshsuite.pedido` → `com.meshsuite.salesorder`.

## Name map

| Portuguese | English |
|---|---|
| `Pedido` | `SalesOrder` |
| `ItemPedido` | `SalesOrderItem` |
| `PedidoContador` | `SalesOrderCounter` |
| `StatusPedido` | `SalesOrderStatus` |
| `PedidoRepository` | `SalesOrderRepository` |
| `PedidoContadorRepository` | `SalesOrderCounterRepository` |
| `PedidoSpecifications` | `SalesOrderSpecifications` |
| `PedidoController` | `SalesOrderController` |
| `PedidoService` | `SalesOrderService` |

`SalesOrder` (not bare `Order`) was chosen deliberately: `order` is a reserved
keyword in PostgreSQL (used in `ORDER BY`), so a table literally named `order`
would require double-quoting in every native query and migration touching it —
a real risk in a codebase that has already had raw-SQL dangling-string-literal
bugs during this initiative. `com.meshsuite.purchaseorder`/`PurchaseOrder` also
already exists in the codebase; naming this module bare `Order` would create
ambiguity between "purchase order" and "sales order". `SalesOrder` is also the
standard ERP term for this exact concept.

## Enum

`StatusPedido` → `SalesOrderStatus`. Values: `DIGITADO→DRAFT`,
`EM_PREPARO→IN_PREPARATION`, `FATURADO→INVOICED`. Persisted as a string
(`@Enumerated(STRING)`), so the persisted values change — consistent with every
enum already translated in this initiative (pre-production/greenfield database,
no real data to preserve). The CHECK constraint in the migration
(`CHECK (status IN ('DIGITADO','EM_PREPARO','FATURADO'))`) is updated to match.

## Fields

**`Pedido`→`SalesOrder`**: `numero→number`, `cliente→customer`,
`vendedor→salesperson`, `dataPedido→orderDate`, `dataEntrega→deliveryDate`,
`desconto→discount`, `criadoEm→createdAt`, `itens→items`. `subtotal`, `total`,
`status`, `tenantId`, `id` are already English and stay. These names deliberately
mirror `Sale.java`'s own fields (`customer`, `salesperson`, `discount`,
`createdAt`) — `Sale` was written anticipating this rename.

**`ItemPedido`→`SalesOrderItem`**: `pedido→salesOrder`, `produto→product`,
`quantidade→quantity`, `valorUnitario→unitPrice`, `valorTotal→totalAmount` —
identical to `SaleItem`'s own field names.

**`PedidoContador`→`SalesOrderCounter`**: `proximoNumero→nextNumber` — identical
to `SaleCounter`'s own field name.

**`PedidoRepository`**: `countByStatus(StatusPedido)` stays as a method name
(already English), parameter type becomes `SalesOrderStatus`.

**`PedidoSpecifications`**: `comBusca(String)`→`withSearch(String)`,
`comStatus(StatusPedido)`→`withStatus(SalesOrderStatus)` — translating these
method names now since, unlike Município's `listar`, there's no established
precedent in this module for keeping them Portuguese.

**`PedidoService`**: constructor-injected fields `parceiroRepository` and
`produtoRepository` were never renamed during the Partner/Product sub-projects
since they're local field names inside `PedidoService`, not class names —
renaming them to `partnerRepository`/`productRepository` is in scope for this
task (they're this service's own fields, referencing already-renamed types,
same treatment Município gave its own controller's injected field).
`proximoNumero(tenantId)` method name stays (already English).

## Database

Tables: `pedido→sales_order`, `item_pedido→sales_order_item`,
`pedido_contador→sales_order_counter`. Columns: `cliente_id→customer_id`,
`vendedor_id→salesperson_id`, `data_pedido→order_date`,
`data_entrega→delivery_date`, `criado_em→created_at`, `numero→number` (in
`sales_order`); `pedido_id→sales_order_id`, `produto_id→product_id`,
`quantidade→quantity`, `valor_unitario→unit_price`,
`valor_total→total_amount` (in `sales_order_item`); `proximo_numero→next_number`
(in `sales_order_counter`).

`V7__create_pedido.sql` is edited in place and renamed to
`V7__create_salesorder.sql`, following the same table/column/index/RLS-policy
naming style already established by `sale`/`sale_item` (e.g.
`idx_pedido_tenant_id→idx_salesorder_tenant_id`,
`pedido_tenant_isolation→salesorder_tenant_isolation`) — unlike Município, this
migration is small (61 lines, no data seed) so every internal name gets
translated, matching the `sale` migration's own style rather than leaving
anything Portuguese behind.

**Cross-migration fix required:** `V26__create_sale.sql` (already merged) has
`order_id UUID NOT NULL UNIQUE REFERENCES pedido(id)`. Since Flyway migrations
run in sequence and this FK targets the `pedido` table by name, renaming that
table in `V7` breaks `V26` unless it's updated in the same task — changed to
`REFERENCES sales_order(id)`. This is a required edit to an already-merged
migration file, not optional cleanup.

**Comment-only touch-ups:** `V11__create_purchase_order.sql:56` and
`V13__create_stock_movement.sql:21,23` have prose comments referencing
`item_pedido`/`pedido` as an analogous naming pattern. These are non-functional
(comments only) but get updated in the same task to avoid leaving a stale old
name in prose, per the lesson from prior sub-projects about dangling references
surviving in comments.

## REST endpoint

`/api/pedidos` → `/api/sales-orders` — kebab-case, matching the existing
`/api/purchase-orders` convention.

## Cross-module bridge: Sale

`SaleService.issue(orderId)` directly injects `PedidoRepository`, loads a
`Pedido`, checks `Pedido.getStatus() == StatusPedido.EM_PREPARO`, and reads
`Pedido.getItens()` (each `ItemPedido`) to build `Sale`/`SaleItem` rows — this
is real logic dependency, not just an import. Updated to use
`SalesOrderRepository`, `SalesOrder`, `SalesOrderStatus.IN_PREPARATION`, and
`SalesOrder.getItems()` (each `SalesOrderItem`), without renaming any of
`Sale`'s own identifiers (`Sale`, `SaleService`, `Sale.order`, etc. stay as they
are — `Sale.order`'s field name already anticipated this). `SaleServiceTest`,
`SaleControllerTest`, and `SaleRepositoryTest` all construct `Pedido` fixtures
and get the same type-reference update.

`GlobalExceptionHandler.java` registers 2 handlers for `Pedido`'s own
exceptions (`handlePedidoNaoEncontrado`/`PedidoNaoEncontradoException`,
`handlePedidoValidacao`/`PedidoValidacaoException`) — treated as in-scope for
this sub-project (they're this entity's own exceptions), same criterion used
for `TabelaPreco`/`PriceTable`'s `GlobalExceptionHandler` handlers.

`AccountsPayableService.java` and `UserController.java`'s `GET /sales-reps`
endpoint reference `Pedido` only in prose comments (no code coupling) — updated
as trivial comment touch-ups, not a functional bridge.

## DTOs

`PedidoResumoResponse` (aggregate counts: `total`/`digitados`/`emPreparo`/
`faturados`, backs `GET /api/pedidos/resumo`, dashboard KPI tiles) and
`PedidoSummaryResponse` (per-row list projection, backs `GET /api/pedidos`) are
confirmed to be two genuinely different, simultaneously-used DTOs — not a
transitional naming collision like Parceiro/Produto had. Following the naming
convention the codebase's own `PurchaseOrder` module already established
(`PurchaseOrderCountsResponse` for its aggregate-counts DTO):
`PedidoResumoResponse→SalesOrderCountsResponse`,
`PedidoSummaryResponse→SalesOrderSummaryResponse`.

Rest of the map: `PedidoRequest→SalesOrderRequest`,
`PedidoResponse→SalesOrderResponse`,
`PedidoStatusRequest→SalesOrderStatusRequest`,
`ItemPedidoDto→SalesOrderItemRequest` (aligning to the Request/Response
pairing convention used elsewhere in the codebase, replacing the older `Dto`
suffix), `ItemPedidoResponse→SalesOrderItemResponse`.

## Exceptions

`PedidoExceptionHandler→SalesOrderExceptionHandler`,
`PedidoNaoEncontradoException→SalesOrderNotFoundException` (message stays
"Pedido não encontrado" — Portuguese, user-facing),
`PedidoValidacaoException→SalesOrderValidationException`.

## Frontend

`api/pedidos.ts→api/salesOrders.ts`: `listarPedidos→listSalesOrders`,
`buscarPedido→getSalesOrder`, `criarPedido→createSalesOrder`,
`atualizarPedido→updateSalesOrder`, `avancarStatusPedido→advanceSalesOrderStatus`,
`excluirPedido→deleteSalesOrder`, `buscarResumoPedidos→getSalesOrderCounts`.
Types: `StatusPedido→SalesOrderStatus`, `ItemPedidoRequest→SalesOrderItemRequest`,
`ItemPedidoResponse→SalesOrderItemResponse`, `PedidoRequest→SalesOrderRequest`,
`PedidoResponse→SalesOrderResponse`, `PedidoSummary→SalesOrderSummary`,
`ListarPedidosParams→ListSalesOrdersParams`, `PedidoResumo→SalesOrderCounts`.

Own views: `PedidoFormView.vue→SalesOrderFormView.vue`,
`PedidosListView.vue→SalesOrdersListView.vue` — matching the existing
`PurchaseOrderFormView.vue`/`PurchaseOrdersListView.vue` naming convention.

`router/index.ts` (bridge): only the component imports change
(`PedidosListView`/`PedidoFormView`→`SalesOrdersListView`/`SalesOrderFormView`).
Routes stay in Portuguese — paths `/pedidos`, `/pedidos/novo`,
`/pedidos/:id/editar` and route names `pedidos`/`pedidos-novo`/`pedidos-editar`
are unchanged, matching the treatment `/vendas` got when `Sale` was renamed
(its route path/name stayed Portuguese even though the component became
`SalesListView`).

`DashboardView.vue` (bridge, real consumer): imports
`buscarResumoPedidos`/`listarPedidos` and types `PedidoResumo`/`PedidoSummary`/
`StatusPedido` from `@/api/pedidos` — updated to the renamed imports, without
touching any of the dashboard's own local variable/function names.

`AppSidebar.vue`, `UserFormView.vue`'s `MODULE_LABELS` map,
`ClienteDetailView.vue`'s "Pedidos" tab placeholder, and `LoginView.vue`'s
tagline contain only Portuguese UI text (labels, tooltips, placeholder copy) —
no code identifiers to change, out of scope.

## Testing

`PedidoControllerTest→SalesOrderControllerTest`,
`PedidoRepositoryTest→SalesOrderRepositoryTest`,
`PedidoServiceTest→SalesOrderServiceTest` — translated alongside their
production classes. `PedidoFormView.spec.ts→SalesOrderFormView.spec.ts`,
`PedidosListView.spec.ts→SalesOrdersListView.spec.ts`. `SaleServiceTest`,
`SaleControllerTest`, `SaleRepositoryTest` get their `Pedido` fixture
references updated as part of the Sale bridge (see above), without renaming
the test classes themselves.

## Known pre-existing flake (unrelated, confirm unchanged)

Full `mvn clean test` is expected to keep showing 0 failures / 15 errors (3
`CompanyRepositoryTest` + 3 `AccountsPayableControllerTest` + 1
`AccountsPayableRepositoryTest` + 8 `AccountsPayableServiceTest`) — pre-existing
test-isolation flake, unrelated to this rename, confirmed identical after every
prior sub-project's merge.
