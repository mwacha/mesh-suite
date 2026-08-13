# Rename TabelaPreco → PriceTable (sub-project 4c) — Design

## Context

Final piece of the multi-sub-project initiative renaming mesh-suite's code identifiers
(Java packages/classes/methods, frontend code, DB tables/columns) from Portuguese to
English, keeping every end-customer-visible string (routes, UI labels, error messages)
unchanged. Completed so far: sub-project 1 Venda→Sale (`0e354fd`), sub-project 2
Empresa→Company (`4b93bd1`), sub-project 3 Parceiro→Partner (`003a43b`), sub-project 4a
Categoria→Category / CorEstampa→Colorway (`9855c26`), sub-project 4b Produto→Product
(`ae29430`).

The original "Produto" mega-module (~90 backend files referencing it) was split into
three sub-projects: 4a (Categoria/CorEstampa), 4b (Produto itself), and this one, 4c
(TabelaPreco — pricing tables). TabelaPreco was deliberately deferred through both prior
sub-projects: 4a extracted Categoria/CorEstampa out of `com.meshsuite.produto`, leaving
`Produto` and `TabelaPreco` behind; 4b extracted `Produto`, leaving only `TabelaPreco`
behind, bridge-patched to consume the renamed `Product` type without renaming
`TabelaPreco`'s own identifiers. **After this sub-project, `com.meshsuite.produto` stops
existing entirely.**

## Scope

Everything remaining in `com.meshsuite.produto`: `TabelaPreco`, `TabelaPrecoItem`, their
repository/specifications/service/controller/DTOs/exceptions, the 5 pricing-only enums
(`Arredondamento`, `MetodoAjuste`, `ModoSelecaoProdutos`, `OperacaoAjuste`,
`TipoValorAjuste`), the `V25__create_tabela_preco.sql` migration, and the frontend
(`api/tabelasPreco.ts`, `TabelaPrecoFormView.vue`, `TabelasPrecoListView.vue`,
`utils/calculoTabelaPreco.ts` — a pure pricing-calculation module used only by these
screens).

This module is self-contained: no other module in the codebase depends on any
TabelaPreco type. The only external dependency is `TabelaPrecoItem`'s `@ManyToOne`
association to `Product` (already the renamed type as of 4b) — no other module's rename
is required here.

## Package topology

New top-level package `com.meshsuite.pricetable`, matching the convention used by every
prior sub-project (`sale`, `company`, `partner`, `category`, `colorway`, `product`).

## Name map

### Classes

| Portuguese | English |
|---|---|
| `TabelaPreco` | `PriceTable` |
| `TabelaPrecoItem` | `PriceTableItem` |
| `TabelaPrecoController` | `PriceTableController` |
| `TabelaPrecoService` | `PriceTableService` |
| `TabelaPrecoRepository` | `PriceTableRepository` |
| `TabelaPrecoSpecifications` | `PriceTableSpecifications` |
| `TabelaPrecoRequest` | `PriceTableRequest` |
| `TabelaPrecoResponse` | `PriceTableResponse` |
| `TabelaPrecoSummaryResponse` | `PriceTableSummaryResponse` |
| `TabelaPrecoItemInput` | `PriceTableItemInput` |
| `TabelaPrecoItemResponse` | `PriceTableItemResponse` |
| `TabelaPrecoExceptionHandler` | `PriceTableExceptionHandler` |
| `TabelaPrecoNaoEncontradaException` | `PriceTableNotFoundException` |
| `TabelaPrecoNomeDuplicadoException` | `DuplicatePriceTableNameException` |
| `TabelaPrecoValidationException` | `PriceTableValidationException` |

No naming collision this time (unlike Parceiro/Produto's Resumo-vs-Summary split) — there
is only one "summary" DTO here (`TabelaPrecoSummaryResponse`, the list row), so it maps
directly to `PriceTableSummaryResponse`.

### Enums (type + values)

| Portuguese | English |
|---|---|
| `ModoSelecaoProdutos` | `ProductSelectionMode` — `TODOS_PRODUTOS`/`SELECIONAR_PRODUTOS` → `ALL_PRODUCTS`/`SELECT_PRODUCTS` |
| `MetodoAjuste` | `AdjustmentMethod` — `AUTOMATICO`/`MANUAL` → `AUTOMATIC`/`MANUAL` |
| `OperacaoAjuste` | `AdjustmentOperation` — `SOMAR`/`SUBTRAIR` → `ADD`/`SUBTRACT` |
| `TipoValorAjuste` | `AdjustmentValueType` — `REAL`/`PERCENTUAL` → `FIXED`/`PERCENTAGE` (`REAL` here means "fixed monetary amount," not the currency — `FIXED` avoids the ambiguity) |
| `Arredondamento` | `Rounding` — `NAO_ARREDONDAR`/`TERMINAR_EM_0`/`TERMINAR_EM_9`/`TERMINAR_EM_90`/`TERMINAR_EM_99` → `NO_ROUNDING`/`END_IN_0`/`END_IN_9`/`END_IN_90`/`END_IN_99` |

Since this project has no production data yet (dev-seed only, replayed fresh via
Flyway), the `V25` migration's `CHECK` constraints are edited in place with the new
English values — no separate data migration needed, same reasoning as every prior
sub-project's enum-value translations.

### Entity/DTO fields

| Portuguese | English |
|---|---|
| `nome` | `name` |
| `ativo` | `active` |
| `criadoEm` | `createdAt` |
| `modoSelecaoProdutos` | `productSelectionMode` |
| `metodoAjuste` | `adjustmentMethod` |
| `operacaoAjuste` | `adjustmentOperation` |
| `tipoValorAjuste` | `adjustmentValueType` |
| `valorAjuste` | `adjustmentValue` |
| `arredondamento` | `rounding` |
| `inicioVigencia` | `effectiveStartDate` |
| `terminoVigencia` | `effectiveEndDate` |
| `valorMinimoVenda` | `minSalePrice` |
| `percentualComissaoPadrao` | `defaultCommissionPercentage` |
| `itens` | `items` |

`inicioVigencia`/`terminoVigencia` → `effectiveStartDate`/`effectiveEndDate` reuses the
"vigência → effective" convention already established by `Colorway.effectiveDate` in 4a.

### `PriceTableItem`'s own fields (the debt 4b deliberately left for this sub-project)

| Portuguese | English |
|---|---|
| `tabelaPreco` (association back to `PriceTable`) | `priceTable` |
| `produto` (association to `Product`) | `product` |
| `precoNestaTabela` | `tablePrice` |
| `percentualComissao` | `commissionPercentage` |

### `PriceTableItemInput`/`PriceTableItemResponse` fields

| Portuguese | English |
|---|---|
| `produtoId` | `productId` |
| `produtoNome` | `productName` |
| `produtoSku` | `productSku` |
| `precoCadastrado` (the product's own registered sale price) | `registeredPrice` |
| `precoNestaTabela`, `percentualComissao` | same as above |

### Database

Table `tabela_preco` → `price_table`, `tabela_preco_item` → `price_table_item`. Columns
follow the field map above (`modo_selecao_produtos`→`product_selection_mode`,
`metodo_ajuste`→`adjustment_method`, `operacao_ajuste`→`adjustment_operation`,
`tipo_valor_ajuste`→`adjustment_value_type`, `valor_ajuste`→`adjustment_value`,
`inicio_vigencia`→`effective_start_date`, `termino_vigencia`→`effective_end_date`,
`valor_minimo_venda`→`min_sale_price`, `percentual_comissao_padrao`→
`default_commission_percentage`, `produto_id`→`product_id`, `preco_nesta_tabela`→
`table_price`, `percentual_comissao`→`commission_percentage`).

`V25__create_tabela_preco.sql` is edited in place and renamed to
`V25__create_price_table.sql` (same pattern used for `V5`/`V6`/`V21`/`V23` in prior
sub-projects). No other migration references `tabela_preco`/`tabela_preco_item` as a
foreign-key target, so no other migration file needs changes.

## REST endpoint

`/api/tabelas-preco` → `/api/price-tables` — this is code, not user-visible text
(confirmed convention: every prior sub-project translated its backend REST path).

## Frontend

- `api/tabelasPreco.ts` → `api/priceTables.ts`, all types/functions translated per the
  name/field maps above (`listarTabelasPreco`→`listPriceTables`, `buscarTabelaPreco`→
  `getPriceTable`, `criarTabelaPreco`→`createPriceTable`, `atualizarTabelaPreco`→
  `updatePriceTable`, `excluirTabelaPreco`→`deletePriceTable`).
- `TabelaPrecoFormView.vue` → `PriceTableFormView.vue`, `TabelasPrecoListView.vue` →
  `PriceTablesListView.vue` — these are the entity's own screens (not borrowed views), so
  they're renamed like every prior sub-project's own screens. Routes stay
  `/tabelas-preco` (user-visible URL, unchanged); route `name` values
  (`'tabelas-preco'`, `'tabelas-preco-novo'`, `'tabelas-preco-editar'`) also stay
  unchanged, per the established convention (Category/Colorway/Product all kept their
  Portuguese route names even after their components were renamed).
- `utils/calculoTabelaPreco.ts` → `utils/priceCalculation.ts` (a pure calculation module,
  not tied to the entity name — translated for consistency since it's pricing-table-only
  logic used by no other module): `RegraAjuste`→`AdjustmentRule`,
  `calcularPrecoAjustado`→`calculateAdjustedPrice`,
  `arredondarParaCima`→`roundUp`, `REGRAS_ARREDONDAMENTO`→`ROUNDING_RULES`. Its own enum
  re-exports (`OperacaoAjuste`, `TipoValorAjuste`, `Arredondamento`) follow the same
  type/value translations as the backend enums.
- `AppSidebar.vue` needs only its route link's `path` checked (stays `/tabelas-preco`,
  unchanged) — no code identifier to rename there.

## Dangling property string literals (audited upfront, per the standing lesson from
Parceiro/Produto)

Found during design (full audit to be finalized in the plan): `TabelasPrecoListView.vue`'s
`resumoMetodoAjuste()` compares raw enum wire values directly —
`tabela.operacaoAjuste === 'SUBTRAIR'` → `'SUBTRACT'`, `tabela.tipoValorAjuste ===
'PERCENTUAL'` → `'PERCENTAGE'` (`'MANUAL'` is unchanged, coincidentally identical in both
languages). `utils/calculoTabelaPreco.ts`'s `RegraAjuste`-typed logic also branches
directly on these enum values (`regra.operacaoAjuste === 'SOMAR'`,
`regra.tipoValorAjuste === 'REAL'`, and the `REGRAS_ARREDONDAMENTO` lookup keyed by every
`Arredondamento` value except `NAO_ARREDONDAR`) — every one of these must track the new
English values. `TabelaPrecoController`'s `@PageableDefault(sort = "nome")` must become
`sort = "name"`. `TabelaPrecoSpecifications`'s `root.get("nome")`/`root.get("ativo")` must
become `root.get("name")`/`root.get("active")`.

## Testing

- `TabelaPrecoControllerTest.java`, `TabelaPrecoRepositoryTest.java`,
  `TabelaPrecoServiceTest.java` move and translate with their corresponding layer. The
  exact test-method-name translation map is produced during plan-writing, not this spec —
  per the standing lesson from Parceiro's plan (which undercounted methods via a
  truncated `grep | head`), the plan must use an unabridged listing.
- `calculoTabelaPreco.spec.ts` → `priceCalculation.spec.ts`, translated alongside its
  source module.
- `TabelaPrecoFormView.spec.ts`/`TabelasPrecoListView.spec.ts` → `PriceTableFormView.spec.ts`/
  `PriceTablesListView.spec.ts`, translated alongside their views.
- No cross-module bridge tasks are needed this time (unlike every prior sub-project) —
  TabelaPreco has no external consumers. The only "bridge-shaped" work is confirming
  `shared/handler/GlobalExceptionHandler.java`'s 3 existing TabelaPreco handlers (already
  present, added in 4a) get retargeted to the new exception package/names — this is
  in-scope, not a deferred bridge, since it's TabelaPreco's own exception classes moving.

## Known pre-existing flake (unrelated, confirm unchanged)

Full `mvn clean test` is expected to keep showing 0 failures / 15 errors (3
`CompanyRepositoryTest` + 3 `AccountsPayableControllerTest` + 1
`AccountsPayableRepositoryTest` + 8 `AccountsPayableServiceTest`) — pre-existing
test-isolation flake, unrelated to this rename, confirmed identical after every prior
sub-project's merge.
