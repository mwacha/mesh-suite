# Rename Municipio → Municipality — Design

## Context

Part of the multi-sub-project initiative renaming mesh-suite's code identifiers
(Java packages/classes/methods, frontend code, DB tables/columns) from Portuguese to
English, keeping every end-customer-visible string (routes, UI labels, error
messages) unchanged. Completed so far: Venda→Sale (`0e354fd`), Empresa→Company
(`4b93bd1`), Parceiro→Partner (`003a43b`), Categoria→Category/CorEstampa→Colorway
(`9855c26`), Produto→Product (`ae29430`), TabelaPreco→PriceTable (`499df4a`).

`Municipio` (Brazilian municipality/city reference data, sourced from IBGE) is the
next module in the initiative. It is by far the smallest and simplest sub-project so
far: no DTOs, no service layer, no exceptions — just an entity, a repository, a
controller, and one frontend consumer.

## Scope

`com.meshsuite.municipio` (already its own top-level package — this is a straight
in-place rename, not an extraction like the Produto split): `Municipio.java`,
`MunicipioRepository.java`, `MunicipioController.java`, `MunicipioControllerTest.java`,
migration `V19__create_municipio.sql`, and the frontend
(`api/municipios.ts` + its spec, and a bridge in the sole consumer,
`ClientesListView.vue`).

No other backend module references `Municipio` directly — confirmed via full-codebase
grep during design. `Company` has no address fields at all; `Partner`'s address
fields (`state`, `city`) are plain, unconstrained strings with no FK/relationship to
`Municipio`. The only coupling is frontend-level: `ClientesListView.vue` (Partner's
list screen) calls `GET /api/municipios` to populate a "Cidade" filter dropdown with
plain city-name strings, with no shared identifier or entity relationship.

## Package

`com.meshsuite.municipio` → `com.meshsuite.municipality`.

## Name map

| Portuguese | English |
|---|---|
| `Municipio` | `Municipality` |
| `MunicipioRepository` | `MunicipalityRepository` |
| `MunicipioController` | `MunicipalityController` |
| field `nome` | `name` |
| field `uf` | `state` |

`uf`→`state` aligns the field with `Partner.state`, which already represents the same
concept (Brazilian state abbreviation) in English. The REST query parameter name
(`?uf=SP`) and the repository's named JPQL parameter (`:uf`) both stay `uf` — this
matches the established convention (`busca`/`ativo` elsewhere in the codebase) of
keeping query-parameter names in Portuguese even when the underlying entity field is
translated.

`MunicipioController`'s method name (`listar`) stays Portuguese, matching the
established convention that an entity's own already-Portuguese method names aren't
retranslated (same treatment every prior sub-project's own controllers/services got).

`MunicipioRepository.findNomesByUfOptional` → `findNamesByStateOptional`; its JPQL
body's property paths (`m.nome`, `m.uf`) become `m.name`, `m.state` to match the
renamed entity fields — its `:uf` named parameter stays `uf`.

## Database

Table `municipio` → `municipality`. Columns `nome`→`name`, `uf`→`state`.

`V19__create_municipio.sql` is edited in place and renamed to
`V19__create_municipality.sql` (same pattern as every prior sub-project's migration
rename). The file's DDL header (`CREATE TABLE`, the two `CREATE INDEX` statements) and
the single `INSERT INTO municipio (id, nome, uf) VALUES` header line are the only
lines that change. The ~5,573 data rows themselves (`(1200013, 'Acrelândia', 'AC'),`
etc.) are positional — they don't reference column names — and stay byte-identical.
Index names (`idx_municipio_uf`, `idx_municipio_nome`) stay unrenamed, per the
established convention of leaving internal DB object names alone during table
renames.

## REST endpoint

`/api/municipios` → `/api/municipalities` — code, not user-visible text, confirmed
convention from every prior sub-project.

## Frontend

`api/municipios.ts` → `api/municipalities.ts`: `listarMunicipios`→
`listMunicipalities`, `ListarMunicipiosParams`→`ListMunicipalitiesParams`; the `uf`
param name stays `uf`.

`ClientesListView.vue` (bridge, not renamed — it's Partner's own screen, not
Municipality's): only the import path/names change
(`listarMunicipios`→`listMunicipalities` from `@/api/municipios`→
`@/api/municipalities`). Local variable/function names (`cidades`, `carregarCidades`)
stay unchanged — they belong to Partner's module, not Municipality's field map.

Municipality has no CRUD screens of its own (no form/list view) — it's purely a
lookup data source, so there's no "own view" rename task this time, unlike every
prior sub-project.

## Testing

`MunicipioControllerTest.java` → `MunicipalityControllerTest.java`: method names
`listsAllMunicipiosWhenNoUfIsGiven`→`listsAllMunicipalitiesWhenNoUfIsGiven`,
`filtersMunicipiosByUf`→`filtersMunicipalitiesByUf` (its query param stays
`uf` — only the method name's readability improves, per usual convention),
`unauthenticatedRequestIsRejected` unchanged. `api/__tests__/municipios.spec.ts` →
`api/__tests__/municipalities.spec.ts`, translated alongside `municipios.ts`.

## Known pre-existing flake (unrelated, confirm unchanged)

Full `mvn clean test` is expected to keep showing 0 failures / 15 errors (3
`CompanyRepositoryTest` + 3 `AccountsPayableControllerTest` + 1
`AccountsPayableRepositoryTest` + 8 `AccountsPayableServiceTest`) — pre-existing
test-isolation flake, unrelated to this rename, confirmed identical after every prior
sub-project's merge.
