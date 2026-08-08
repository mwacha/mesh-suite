# Cor da Estampa — Spec de Design

## 1. Contexto e decisão

`PRD-13-cadastro-comercial.md` lista "Cor" e "Estampa" como dois cadastros auxiliares de característica de produto separados, entre os adiados do recorte inicial de Cadastro Comercial (junto com Grupo de Produto, já implementado como Categoria de Produto). O wireframe de referência (`layout/wireframes/13 - Cores e Estampas-v1.html`) já define um cadastro **combinado**: uma única lista "Cor/Estampa" com nome, data de vigência, descrição e status — tratando cores (ex. "Azul Marinho", "Preto") e estampas (ex. "Floral Primavera", "Listrado Náutico") como o mesmo tipo de registro. Esta fatia segue o wireframe, não o PRD nesse ponto específico.

Hoje `Produto` não tem nenhum campo de cor/estampa — diferente da Categoria (que já existia como texto livre a ser convertido), aqui é um vínculo inteiramente novo.

## 2. Escopo

### Incluído
- `CorEstampa`: cadastro com nome (obrigatório, único por tenant), data de vigência (obrigatória), descrição (opcional), status ativo/inativo.
- CRUD completo: listagem (busca por nome, filtro por status), criação, edição.
- Exclusão bloqueada se houver `Produto` vinculado, com mensagem informando a quantidade.
- `Produto` ganha campo opcional `corEstampa` (referência a `CorEstampa`) — dropdown no formulário, mesmo padrão já usado para `categoriaId`/`categoriaNome`, incluindo preservar uma cor/estampa inativa já vinculada ao editar um produto existente.
- Reaproveita `Module.PRODUCT` já existente — sem módulo de permissão novo.
- Contagem de "produtos vinculados" na listagem é feita em lote (uma query agregada por página, não uma por linha) desde a primeira versão — lição já aprendida e corrigida na fatia de Categoria de Produto, incorporada aqui diretamente.

### Fora de escopo
- **Cor e Estampa como cadastros separados** — decisão consciente de seguir o wireframe combinado, não o PRD.
- **Relação com Modelo/Ficha Técnica** (consumo de matéria-prima por combinação de estampa/cor/tecido) — não documentado em profundidade pelo PRD ("estrutura detalhada... não foi confirmada"), fatia própria e maior, fora daqui.
- **Demais cadastros auxiliares** (Tamanho, Tecido/Aviamento, Unidade, Origem do Produto) — ficam para rodadas futuras, uma de cada vez.
- **Regra de negócio computada a partir da data de vigência** — o campo é informativo nesta fatia (o PRD não detalha nenhum comportamento associado); nenhuma filtragem ou validação depende dela além de ser um campo obrigatório do cadastro.

## 3. Modelo de dados

Novo código no pacote `com.meshsuite.produto`, em português, seguindo exatamente a convenção já usada por `Categoria`.

### `CorEstampa` (tabela `cor_estampa` — RLS por tenant direto, mesmo padrão de `categoria`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `nome` | String | obrigatório, único por tenant |
| `dataVigencia` | LocalDate | obrigatória |
| `descricao` | String | opcional |
| `ativo` | Boolean | default `true` |
| `criadoEm` | Instant | `updatable = false` |

### `Produto` (modificação)

Novo campo `corEstampa` (`@ManyToOne` opcional para `CorEstampa`, coluna `cor_estampa_id`). `ProdutoResponse` ganha `corEstampaId`/`corEstampaNome`; `ProdutoRequest` ganha `corEstampaId` (nullable) — mesmo padrão de `categoriaId`/`categoriaNome`.

## 4. Regras de negócio

1. `nome` da CorEstampa é único por tenant.
2. Exclusão bloqueada se `ProdutoRepository.existsByCorEstampaId(id)` for verdadeiro — mensagem informa a quantidade de produtos vinculados.
3. `Produto.corEstampa` é opcional.
4. CorEstampa inativa continua aparecendo no dropdown do Produto se já estiver vinculada a esse produto (não esconde vínculo já feito), mas não aparece como opção nova.
5. Contagem de produtos vinculados na listagem (`GET /api/cores-estampas`) é feita via uma única query agregada por página (`countByCorEstampaIdIn`), não uma consulta por linha.

## 5. Telas

- `CoresEstampasListView.vue` (rota `/cores-estampas`): busca por nome, filtro por status, tabela (nome, vigência, produtos vinculados, status), ações (editar, excluir). Botão "+ Nova Cor/Estampa". Mesmo padrão visual das listagens já existentes.
- `CorEstampaFormView.vue` (`/cores-estampas/novo`, `/cores-estampas/:id/editar`): card "Informações Gerais" — nome, data de vigência, descrição, toggle Ativo/Inativo — réplica do wireframe "Cadastro de Cor/Estampa".
- `ProdutoFormView.vue`: novo dropdown "Cor/Estampa", carregado das cores/estampas ativas (mais a atual do produto, se inativa).
- `AppSidebar.vue`: item "Cores / Estampas" (grupo Catálogo, hoje `route: null`) passa a apontar para `/cores-estampas`.

## 6. API (backend)

- `GET /api/cores-estampas` — lista paginada, filtro `nome`/`ativo`, `@RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)`.
- `GET /api/cores-estampas/{id}` — busca por id.
- `POST /api/cores-estampas` — cria, `@RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)`.
- `PUT /api/cores-estampas/{id}` — edita, `@RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)`.
- `DELETE /api/cores-estampas/{id}` — exclui (bloqueado se em uso), `@RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)`.

## 7. Testes

- Backend: `CorEstampaRepositoryTest` (RLS), `CorEstampaServiceTest` (nome único, bloqueio de exclusão em uso, contagem em lote, CRUD, permissões), `CorEstampaControllerTest` (endpoints via HTTP, RLS cross-tenant, 401/403).
- Frontend: `CoresEstampasListView.spec.ts`, `CorEstampaFormView.spec.ts`, mais um caso em `ProdutoFormView.spec.ts` cobrindo o novo dropdown (incluindo o caso de cor/estampa inativa já vinculada).

## 8. Riscos e notas abertas

1. **Data de vigência sem comportamento definido**: o campo existe porque o wireframe o exige, mas nenhuma regra de negócio depende dele nesta fatia. Se um uso futuro precisar (ex. esconder cores fora de vigência), exige revisão desta decisão.
2. **Divergência do PRD**: Cor e Estampa tratados como um único cadastro aqui — se uma necessidade futura exigir separá-los (ex. um produto com cor E estampa simultâneas, não um OU outro), este modelo precisa ser revisitado.
