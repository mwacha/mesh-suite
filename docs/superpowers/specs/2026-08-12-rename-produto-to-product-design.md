# Rename Produto → Product (sub-project 4b) — Design

## Context

Part of the multi-sub-project initiative to rename mesh-suite's code identifiers (Java
packages/classes/methods, frontend code, DB tables/columns) from Portuguese to English,
keeping every end-customer-visible string (routes, UI labels, error messages) unchanged.

Completed so far: sub-project 1 Venda→Sale (`0e354fd`), sub-project 2 Empresa→Company
(`4b93bd1`), sub-project 3 Parceiro→Partner (`003a43b`), sub-project 4a Categoria→Category /
CorEstampa→Colorway (`9855c26`).

The original "Produto" module was too large for one sub-project (~90 backend files
referenced it) and was split into: 4a (Categoria/CorEstampa, done), 4b (Produto itself, this
spec), 4c (TabelaPreco, depends on Produto, done later). Sub-project 4a already extracted
`Categoria`→`Category` and `CorEstampa`→`Colorway` into their own top-level packages and
bridge-patched `Produto.java`/`ProdutoService.java` to consume the renamed types — but it
deliberately left `Produto`'s own field names (`categoria`, `corEstampa`) and FK columns
untouched, since renaming those was out of scope for 4a.

## Scope

**In scope:** everything remaining in `com.meshsuite.produto` that belongs to the `Produto`
entity itself — domain, repository, controller, service, DTOs, specifications, exceptions,
the enums `StatusProduto`/`UnidadeMedida`, the migrations that create/alter the `produto`
table, and the frontend (`api/produtos.ts`, `ProdutoFormView.vue`, `ProdutosListView.vue`).
Also in scope: finishing the `categoria`/`corEstampa` field-name rename on `Produto` itself
(type was already renamed in 4a; only the field/column names were deferred).

**Out of scope (deferred to 4c):** `TabelaPreco`, `TabelaPrecoItem`, their DTOs, service,
controller, repository, specifications, exceptions, and the pricing-only enums
(`Arredondamento`, `MetodoAjuste`, `ModoSelecaoProdutos`, `OperacaoAjuste`,
`TipoValorAjuste`). All of these stay in the `com.meshsuite.produto` package name for now,
receiving only a bridge-patch to consume the renamed `Product` type.

**Also out of scope:** `pedido` (Pedido) and its own field naming (`produtoId`,
`produtoNome`) — Pedido is a separate future sub-project. Only the *type* `Produto`→`Product`
is bridged there.

## Package topology

New top-level package `com.meshsuite.product`, matching the convention already used for
`sale`, `company`, `partner`, `category`, `colorway`.

## Name map

### Classes

| Portuguese | English |
|---|---|
| `Produto` | `Product` |
| `ProdutoController` | `ProductController` |
| `ProdutoService` | `ProductService` |
| `ProdutoRepository` | `ProductRepository` |
| `ProdutoSpecifications` | `ProductSpecifications` |
| `ProdutoRequest` | `ProductRequest` |
| `ProdutoResponse` | `ProductResponse` |
| `ProdutoStatusRequest` | `ProductStatusRequest` |
| `ProdutoResumoResponse` (dashboard counters) | `ProductSummaryResponse` |
| `ProdutoSummaryResponse` (list row) | `ProductListItemResponse` |
| `ProdutoExceptionHandler` | `ProductExceptionHandler` |
| `ProdutoNaoEncontradoException` | `ProductNotFoundException` |
| `SkuDuplicadoException` | `DuplicateSkuException` |
| `StatusProduto` (enum) | `ProductStatus` |
| `UnidadeMedida` (enum) | `MeasurementUnit` |
| `ProdutoRepository.CategoriaProdutoCount` (nested projection) | `ProductRepository.CategoryProductCount` |
| `ProdutoRepository.CorEstampaProdutoCount` (nested projection) | `ProductRepository.ColorwayProductCount` |

Naming-collision resolution (`ResumoResponse` vs `SummaryResponse`) follows the same pattern
already used for Parceiro→Partner: the dashboard-counters DTO becomes `*SummaryResponse`, the
list-row DTO becomes `*ListItemResponse`.

### Enum values

`StatusProduto.ATIVO/INATIVO` → `ProductStatus.ACTIVE/INACTIVE`. Confirmed the raw enum value
never reaches the UI directly — the frontend maps it to a separate Portuguese display label
(`{ ATIVO: 'Ativo', INATIVO: 'Inativo' }`) — so translating the wire value doesn't change any
visible text. Since this project has no production data yet (only dev-seed, replayed fresh
via Flyway), the `V6` migration is edited in place rather than adding a data-migration step.

`UnidadeMedida`'s values (`UN`, `KG`, `G`, `L`, `ML`, `MT`, `CM`, `CX`, `PC`, `PAR`, `DZ`) stay
unchanged — they're already language-neutral abbreviations, not Portuguese words. Only the
enum class name changes.

### Entity/DTO fields

| Portuguese | English |
|---|---|
| `nome` | `name` |
| `codigoBarras` | `barcode` |
| `marca` | `brand` |
| `categoria` / `categoriaId` / `categoriaNome` | `category` / `categoryId` / `categoryName` |
| `corEstampa` / `corEstampaId` / `corEstampaNome` | `colorway` / `colorwayId` / `colorwayName` |
| `precoVenda` | `salePrice` |
| `precoCusto` | `costPrice` |
| `descricao` | `description` |
| `quantidadeEstoque` | `stockQuantity` |
| `unidadeMedida` | `measurementUnit` |
| `estoqueMinimo` | `minStock` |
| `estoqueMaximo` | `maxStock` |
| `peso` | `weight` |
| `comprimento` | `length` |
| `largura` | `width` |
| `altura` | `height` |
| `criadoEm` | `createdAt` |

`sku` and `id` are unchanged (already language-neutral).

### Database

Table `produto` → `product`. Columns follow the field map above (`categoria_id`→
`category_id`, `cor_estampa_id`→`colorway_id`, `preco_venda`→`sale_price`, etc.). The `status`
column's `CHECK` constraint values become `'ACTIVE'`/`'INACTIVE'`.

`V6__create_produto.sql` is edited in place and renamed to `V6__create_product.sql` (same
pattern used for `V5`/`V21`/`V23` in prior sub-projects). `V18__add_fiscal_registration_to_produto.sql`,
`V22__replace_produto_categoria_with_fk.sql`, `V24__add_cor_estampa_to_produto.sql` only need
their FK target updated (`REFERENCES produto(id)`→`REFERENCES product(id)`) — their own
column names stay as previously renamed/added, no filename change since they don't create the
`produto` table themselves. `V7__create_pedido.sql`, `V11__create_purchase_order.sql`,
`V13__create_stock_movement.sql`, `V25__create_tabela_preco.sql`, `V26__create_sale.sql` — all
five reference `produto(id)` as a foreign key target only; each gets that one line updated,
nothing else in those files changes.

## Cross-module bridges (type swap only, no rename of the consumer's own symbols)

- **`CategoryService.java` / `ColorwayService.java`** (main, not test) — swap import/type
  `ProdutoRepository`→`ProductRepository`; `countByCategoriaIdIn`→`countByCategoryIdIn`,
  `CategoriaProdutoCount`→`CategoryProductCount` (and the Colorway equivalents
  `countByCorEstampaIdIn`→`countByColorwayIdIn`, `CorEstampaProdutoCount`→
  `ColorwayProductCount`). These two services are the only other-module consumers of these
  specific repository methods.
- **`pedido`** (`ItemPedido.java`, `PedidoService.java`, `ItemPedidoDto.java`,
  `ItemPedidoResponse.java`) — swap the type `Produto`→`Product`; the module's own field
  names (`produto`, `produtoId`, `produtoNome`) stay as-is, Pedido hasn't been rename-scoped
  yet.
- **`purchaseorder`, `sale`, `stock`** (`PurchaseOrderItem.java`, `SaleItem.java`,
  `StockMovement.java`, their services) — swap the type `Produto`→`Product`; these modules'
  own field names are already English (`product`, `productId`), no further change needed
  there.
- **`shared/handler/GlobalExceptionHandler.java`** — update exactly the 2 handlers for
  Produto's own exceptions (`handleProdutoNaoEncontrado`→`handleProductNotFound` catching
  `com.meshsuite.product.exception.ProductNotFoundException`; `handleSkuDuplicado`→
  `handleDuplicateSku` catching `com.meshsuite.product.exception.DuplicateSkuException`). The
  3 `TabelaPreco*` handlers immediately adjacent are left untouched (out of scope, deferred to
  4c) — this file was the source of a plan gap in 4a, so it's called out explicitly here to
  make sure the plan includes it as its own task.
- **`produto/service/TabelaPrecoService.java`, `produto/domain/TabelaPrecoItem.java`** — swap
  the type `Produto`→`Product` and `ProdutoRepository`→`ProductRepository`; TabelaPreco's own
  field name (`produto` on `TabelaPrecoItem`, `produtoId` on its DTOs) stays as-is — its
  rename is 4c's decision to make.

## Dangling property string literals (audited upfront, per the standing lesson from Parceiro)

Checked every `Specification`, `@PageableDefault`, `Sort.by`, and frontend sort mechanism in
the codebase for hardcoded Produto field names. Found (all inside Produto's own files, fixed
as part of this same rename, not "6th occurrences" of the bug since nothing outside this
task's scope depends on them):

- `ProdutoController.java`: `@PageableDefault(size = 10, sort = "nome")` → `sort = "name"`.
- `ProdutoSpecifications.java`: `root.get("nome")`, `root.get("sku")` (query stays
  `root.get("name")`, `root.get("sku")` — `sku` unchanged).
- `ProdutosListView.vue`: `sortField` type and `toggleSort`/`carregar` build a raw
  `sort: '<field>,<dir>'` string sent straight to the API — `'nome'`→`'name'`,
  `'precoVenda'`→`'salePrice'` (`'status'` unchanged).

No other Specification, JPQL `@Query`, or frontend sort mechanism in the rest of the codebase
references a Produto field by bare string — confirmed via full-codebase grep during design.

## Frontend

- `api/produtos.ts` → `api/products.ts` (all types/functions translated: `ProdutoRequest`→
  `ProductRequest`, `ProdutoResponse`→`ProductResponse`, `ProdutoSummary`→
  `ProductListItem` (matching the backend `ProductListItemResponse` rename),
  `ProdutoResumo`→`ProductSummary`, `listarProdutos`→`listProducts`, `buscarProduto`→
  `getProduct`, `criarProduto`→`createProduct`, `atualizarProduto`→`updateProduct`,
  `atualizarStatusProduto`→`updateProductStatus`, `excluirProduto`→`deleteProduct`,
  `buscarResumoProdutos`→`getProductSummary`).
- `ProdutoFormView.vue` → `ProductFormView.vue`, `ProdutosListView.vue` → `ProductsListView.vue`
  — these are Product's own screens (not borrowed views), so they're renamed like Category/
  Colorway's own views were in 4a, not left alone like Parceiro's borrowed `Cliente*.vue`.
  Routes stay `/produtos` (user-visible URL, unchanged).
- Bridge-only frontend consumers (import the type/function but keep their own local field
  names): `router/index.ts`, `AppSidebar.vue`, `DashboardView.vue`, `PedidoFormView.vue`,
  `PurchaseOrderFormView.vue`, `TabelaPrecoFormView.vue`.

All user-visible text (route paths, form labels, button text, error messages, status pill
labels "Ativo"/"Inativo") stays exactly as it is today.

## Testing

- `ProdutoControllerTest.java`, `ProdutoRepositoryTest.java`, `ProdutoServiceTest.java` move
  and translate with their corresponding layer. The exact test-method-name translation map is
  produced during plan-writing (not this spec) — the Parceiro sub-project's plan undercounted
  methods due to a truncated `grep | head`; the plan for this sub-project must use an
  unabridged listing.
- The 14 cross-module test files that seed `Produto` fixtures (`CategoryServiceTest`,
  `ColorwayServiceTest`, `Pedido*Test` ×3, `PurchaseOrder*Test` ×3, `Sale*Test` ×3,
  `StockMovement*Test` ×2, `StockServiceTest`) only swap the type in their fixtures — no
  method-name translation, since they belong to other modules.
- Existing techniques remain available as needed: relocate-test-restore, compile-bridge,
  patch-in-place-then-revert.

## Known pre-existing flake (unrelated, confirm unchanged)

Full `mvn clean test` is expected to keep showing 0 failures / 15 errors (12
`com.meshsuite.payable.*` + 3 `CompanyRepositoryTest`) — pre-existing test-isolation flake,
unrelated to this rename, confirmed identical after every prior sub-project's merge.
