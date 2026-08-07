# Categoria de Produto — Spec de Design

## 1. Contexto e decisão

`PRD-13-cadastro-comercial.md` documenta um conjunto de "cadastros auxiliares de característica de produto" — Cor, Tamanho, Estampa, Tecido/Aviamento, **Grupo de Produto**, Unidade, Origem do Produto. `ORDEM-EXECUCAO.md` registra explicitamente que esses cadastros ficaram fora do recorte inicial de Cadastro Comercial (item 2, já concluído): *"ficam para quando forem necessários, não fazem parte deste recorte inicial"*.

Esta fatia constrói o primeiro deles: **Categoria** (nome usado no menu já organizado em `layout/wireframes/Menu-v1.html`, correspondente ao "Grupo de Produto" do PRD). Hoje `Produto.categoria` é um campo de texto livre, sem cadastro nenhum por trás — confirmado que nenhum dado de teste/seed usa esse campo, então não há dado real de produção a preservar na migração.

Não há wireframe de tela dedicado para Categoria em `layout/`, mas o wireframe de **Cores/Estampas** (`13 - Cores e Estampas-v1.html`, mesmo grupo "Catálogo" no menu) tem tela de lista e de cadastro completas — usado aqui como referência de padrão visual (lista com busca/filtro, formulário de página cheia com toggle Ativo/Inativo), adaptado removendo o campo "Data de Vigência" (específico do caráter sazonal de Cor/Estampa, não se aplica a uma categoria estrutural).

Cor/Estampa em si fica para uma próxima rodada, com seu próprio ciclo spec → plano → implementação, seguindo os mesmos passos definidos aqui.

## 2. Escopo

### Incluído
- `Categoria`: cadastro com nome (obrigatório, único por tenant), descrição (opcional), status ativo/inativo.
- CRUD completo: listagem (busca por nome, filtro por status), criação, edição.
- Exclusão bloqueada se houver `Produto` vinculado, com mensagem informando a quantidade de produtos usando a categoria.
- `Produto.categoria` deixa de ser texto livre e passa a referenciar `Categoria` (opcional) — completa o cadastro de Produto, elimina duplicidade/inconsistência de digitação (ex. "Camisas" vs "camisa").
- Permissão: reaproveita `Module.PRODUCT` já existente — Categoria é auxiliar de Produto, não um domínio de negócio próprio como Compras/Estoque/Financeiro/Fiscal foram.

### Fora de escopo
- **Categoria hierárquica** (sub-categorias) — o PRD não documenta essa estrutura para Grupo de Produto; YAGNI.
- **Reordenação manual** (drag-and-drop) na listagem.
- **Cor/Estampa, Tamanho, Tecido/Aviamento, Unidade, Origem do Produto** — os demais cadastros auxiliares do PRD-13 ficam para rodadas futuras, uma de cada vez. Cor/Estampa é a próxima, logo em seguida a esta.
- **Migração de dados existentes** de `Produto.categoria` (texto) — não há dado real nos ambientes de teste/dev a migrar; a coluna antiga é descartada diretamente.

## 3. Modelo de dados

Novo código vive no pacote `com.meshsuite.produto` (mesmo pacote de `Produto`), em **português** — segue a convenção já usada em Produto/Cliente/Parceiro, diferente do inglês adotado nos domínios novos da iniciativa Compras (Categoria é complemento de um cadastro já existente, não um domínio novo).

### `Categoria` (tabela `categoria` — RLS por tenant direto, mesmo padrão de `purchase_order`/`fiscal_registration`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `nome` | String | obrigatório, único por tenant |
| `descricao` | String | opcional |
| `ativo` | Boolean | default `true` |
| `criadoEm` | Instant | `updatable = false` |

### `Produto` (modificação)

Campo `categoria` (String) substituído por `categoria` (`@ManyToOne` opcional para `Categoria`, coluna `categoria_id`). No DTO de resposta (`ProdutoResponse`), expõe `categoriaId` + `categoriaNome` — mesmo padrão já usado em `AccountsPayableResponse.supplierId`/`supplierName`. No `ProdutoRequest`, recebe `categoriaId` (nullable).

## 4. Regras de negócio

1. `nome` da Categoria é único por tenant — tentativa de duplicar é rejeitada com mensagem clara.
2. Exclusão de Categoria bloqueada se `ProdutoRepository.existsByCategoriaId(id)` for verdadeiro — mensagem informa quantos produtos usam a categoria.
3. `Produto.categoria` é opcional — produto pode não ter categoria.
4. Categoria inativa continua aparecendo no dropdown do Produto se já estiver vinculada a esse produto (não esconde vínculo já feito), mas não aparece como opção nova para produtos sem categoria ainda.

## 5. Telas

- `CategoriasListView.vue` (rota `/categorias`): busca por nome, filtro por status, tabela (nome, descrição, produtos vinculados, status), ações (editar, ativar/inativar, excluir). Botão "+ Nova Categoria". Mesmo padrão visual/estrutural das listagens já existentes (`ProdutosListView.vue`).
- `CategoriaFormView.vue` (`/categorias/novo`, `/categorias/:id/editar`): card único "Informações Gerais" — nome, descrição, toggle Ativo/Inativo — mesmo padrão visual do wireframe "Cadastro de Cor/Estampa" (sem o campo de vigência).
- `ProdutoFormView.vue`: campo Categoria (hoje um `<input>` de texto) passa a ser um `<select>` carregado da API de Categorias ativas (mais a categoria atual do produto, mesmo se inativa).
- `AppSidebar.vue`: item "Categorias" (já presente no menu, `route: null`) passa a apontar para `/categorias`.

## 6. API (backend)

- `GET /api/categorias` — lista paginada, filtro `nome`/`ativo`, `@RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)`.
- `POST /api/categorias` — cria, `@RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)`.
- `PUT /api/categorias/{id}` — edita, `@RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)`.
- `DELETE /api/categorias/{id}` — exclui (bloqueado se em uso), `@RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)`.
- `GET /api/produtos` continua igual na assinatura HTTP, mas `ProdutoResponse` ganha `categoriaId`/`categoriaNome`; `ProdutoRequest` troca `categoria: String` por `categoriaId: UUID | null`.

## 7. Testes

- Backend: `CategoriaRepositoryTest` (isolamento RLS), `CategoriaServiceTest` (nome único, bloqueio de exclusão em uso, CRUD, permissões), `CategoriaControllerTest` (endpoints via HTTP, RLS cross-tenant, 403 sem permissão). `ProdutoServiceTest`/`ProdutoControllerTest` existentes precisam de ajuste pontual para o novo formato de campo categoria.
- Frontend: `CategoriasListView.spec.ts` (listagem, busca, filtro, exclusão bloqueada), `CategoriaFormView.spec.ts` (criar, editar, validação de nome único), mais um caso em `ProdutoFormView.spec.ts` cobrindo o dropdown de categoria.

## 8. Riscos e notas abertas

1. **Sem wireframe dedicado**: a tela de Categoria foi desenhada por analogia à de Cores/Estampas (mesmo grupo "Catálogo" no menu), removendo o campo de vigência. Se surgir um wireframe específico de Categoria depois, pode exigir ajuste visual — não muda o modelo de dados nem as regras de negócio.
2. **Reaproveitar `Module.PRODUCT`**: decisão consciente de não criar um módulo de permissão novo — se no futuro fizer sentido conceder acesso a Categoria sem conceder acesso a Produto (ou vice-versa), essa decisão precisa ser revisitada.
3. **Cadastros auxiliares futuros**: Cor/Estampa (próxima rodada) tem um wireframe completo e um campo a mais (vigência) — o padrão de tela criado aqui (list + form de página cheia, toggle Ativo/Inativo) deve se repetir lá, mas o modelo de dados não, dado que Cor/Estampa tem semântica diferente (sazonal, com vigência).
