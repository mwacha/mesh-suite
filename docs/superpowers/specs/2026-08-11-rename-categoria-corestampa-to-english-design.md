# Rename Categoria/CorEstampa → Category/Colorway (Código em Inglês) — Spec de Design

> Sub-projeto 4a da iniciativa "Rename para inglês". Sub-projetos 1 (Venda→Sale), 2 (Empresa→Company) e 3 (Parceiro→Partner) concluídos e mesclados em `main` (commits `0e354fd`, `4b93bd1`, `003a43b`). Escopo completo da iniciativa: renomear todo o codebase mesh-suite de português para inglês, mantendo a exibição pro cliente final (rotas visíveis, textos de UI, labels, mensagens de erro) em português.
>
> O sub-projeto 4 original ("Produto") foi decomposto em três partes menores por causa do tamanho (90 arquivos no backend referenciam `Produto`): **4a** (este documento) — `Categoria`/`CorEstampa`, entidades de referência pequenas e independentes, que o `Produto` referencia via FK; **4b** — `Produto` em si (o módulo grande); **4c** — `TabelaPreco` (depende do Produto renomeado). Módulos ainda pendentes após todo o sub-projeto 4: Município, Pedido.

## 1. Contexto e decisão

`Categoria` e `CorEstampa` são duas entidades de referência simples (nome/descrição/ativo, sem relação uma com a outra) com CRUD completo próprio: controller, service, repository, specifications, 3 exceções cada, 2 DTOs cada, migration própria, e telas dedicadas no frontend (list + form). Ambas são referenciadas pelo `Produto` via FK opcional (`categoria_id`, `cor_estampa_id`). Diferente do caso Parceiro/Cliente, as telas de frontend aqui **são** as próprias telas da entidade (não uma view filtrada de um conceito mais amplo) — por isso, ao contrário do Parceiro, os arquivos de view também são renomeados.

**Decisões já tomadas com o usuário:**
- `Categoria` → `Category`.
- `CorEstampa` → `Colorway` (termo real da indústria de vestuário/moda: a combinação específica de cor/estampa em que uma peça é oferecida).
- Migrations `V21__create_categoria.sql` e `V23__create_cor_estampa.sql` editadas diretamente (renomeadas), mesmo padrão greenfield dos sub-projetos anteriores — exige resetar o banco local.
- Frontend: arquivos de view próprios (`CategoriaFormView.vue`, `CategoriasListView.vue`, `CorEstampaFormView.vue`, `CoresEstampasListView.vue`) são renomeados junto — não são uma view emprestada de outro conceito como foi o caso do Cliente no sub-projeto Parceiro.

## 2. Escopo

### Incluído
- Pacote Java `com.meshsuite.produto` (classes de Categoria e CorEstampa apenas — o resto do pacote `produto` continua intacto até o 4b/4c): controller, service, repository, specifications, exceções, DTOs, domínio, enums não se aplicam aqui (nenhum enum próprio).
- Migrations `V21`/`V23` editadas para criar as tabelas `category`/`colorway`.
- **Migrations posteriores com FK literal**: `V22__replace_produto_categoria_with_fk.sql` (`REFERENCES categoria(id)`) e `V24__add_cor_estampa_to_produto.sql` (`REFERENCES cor_estampa(id)`) — só o alvo da FK muda; a coluna em si (`categoria_id`, `cor_estampa_id`) fica com o nome atual, pois pertence ao `produto`, fora de escopo até o 4b.
- Rotas da API: `/api/categorias→/api/categories`, `/api/cores-estampas→/api/colorways`.
- Frontend: `api/categorias.ts→categories.ts`, `api/coresEstampas.ts→colorways.ts`, e os 4 arquivos de view (`CategoriaFormView.vue`, `CategoriasListView.vue`, `CorEstampaFormView.vue`, `CoresEstampasListView.vue`) + specs, todos renomeados.
- Limpeza de referências fora do módulo (bridge mínimo, sem renomear o Produto): tipo em `Produto.java` (`Categoria categoria→Category categoria`, `CorEstampa corEstampa→Colorway corEstampa`, nomes de campo inalterados); imports/tipos + os dois acessores `.getNome()→.getName()` em `ProdutoService.java`; imports/tipos em `ProdutoFormView.vue` (nomes de variável local e campos do form do Produto inalterados); imports de componente em `router/index.ts`.

### Fora de escopo
- Qualquer texto visível ao usuário final: mensagens de erro (`"Categoria não encontrada"`, `"Já existe uma categoria cadastrada com este nome"`, `"Não é possível excluir: N produto(s) usam esta categoria"` etc. continuam em português, inalteradas), rotas do Vue Router (`/categorias`, `/cores-estampas`), labels da sidebar ("Categorias", "Cores / Estampas").
- `ProdutoRepository.java` — confirmado que não precisa de nenhuma mudança: o JPQL usa os caminhos de campo do próprio `Produto` (`p.categoria.id`, `p.corEstampa.id`), não referencia as classes `Categoria`/`CorEstampa` diretamente, e as interfaces de projeção (`CategoriaProdutoCount`, `CorEstampaProdutoCount`) não usam os tipos — só `UUID`/`Long`.
- `ProdutoSpecifications.java` — confirmado, via grep, que não tem nenhum join Criteria API string (`.get("categoria")`/`.get("corEstampa")`) que precisaria de correção — ao contrário do que aconteceu 5 vezes no sub-projeto Parceiro (ver `feedback_dangling_property_string_literals` em memória), aqui o risco dessa classe de bug é baixo, mas a verificação final do plano deve incluir a mesma varredura por segurança.
- `api/produtos.ts` — confirmado que não importa nada de `categorias.ts`/`coresEstampas.ts`; seus campos `categoriaId`/`corEstampaId`/`categoriaNome`/`corEstampaNome` são do próprio DTO do Produto, fora de escopo.
- `Produto` em si (módulo inteiro) e `TabelaPreco` — ficam para os sub-projetos 4b e 4c.
- Os demais módulos ainda em português (Município, Pedido) — ficam para depois do sub-projeto 4 completo.

## 3. Mapa de nomes completo

### Backend — classes (Categoria)

| Atual | Novo |
|---|---|
| `Categoria` | `Category` |
| `CategoriaController` | `CategoryController` |
| `CategoriaService` | `CategoryService` |
| `CategoriaRepository` | `CategoryRepository` |
| `CategoriaSpecifications` | `CategorySpecifications` |
| `CategoriaExceptionHandler` | `CategoryExceptionHandler` |
| `CategoriaRequest` | `CategoryRequest` |
| `CategoriaResponse` | `CategoryResponse` |
| `CategoriaEmUsoException` | `CategoryInUseException` |
| `CategoriaNaoEncontradaException` | `CategoryNotFoundException` |
| `CategoriaNomeDuplicadoException` | `DuplicateCategoryNameException` |

### Backend — classes (CorEstampa)

| Atual | Novo |
|---|---|
| `CorEstampa` | `Colorway` |
| `CorEstampaController` | `ColorwayController` |
| `CorEstampaService` | `ColorwayService` |
| `CorEstampaRepository` | `ColorwayRepository` |
| `CorEstampaSpecifications` | `ColorwaySpecifications` |
| `CorEstampaExceptionHandler` | `ColorwayExceptionHandler` |
| `CorEstampaRequest` | `ColorwayRequest` |
| `CorEstampaResponse` | `ColorwayResponse` |
| `CorEstampaEmUsoException` | `ColorwayInUseException` |
| `CorEstampaNaoEncontradaException` | `ColorwayNotFoundException` |
| `CorEstampaNomeDuplicadoException` | `DuplicateColorwayNameException` |

### Backend — campos

`id`, `tenantId` — sem mudança. `nome→name` · `descricao→description` · `ativo→active` · `criadoEm→createdAt` · `produtosVinculados→linkedProducts` (campo do DTO de resposta, contagem de produtos vinculados). CorEstampa/Colorway também tem `dataVigencia→effectiveDate`.

### Backend — métodos (`CategoryService`/`CategoryController`, `ColorwayService`/`ColorwayController`)

`listar→list` · `buscarPorId→findById` · `criar→create` · `atualizar→update` · `excluir→delete` · `buscarEntidadePorId→findEntityById` · `validarNome→validateName` · `aplicar→apply` · `toResponse` (sem mudança).

### Backend — `CategoryRepository`/`ColorwayRepository`

`existsByNome→existsByName` · `existsByNomeAndIdNot→existsByNameAndIdNot`.

### Backend — `CategorySpecifications`/`ColorwaySpecifications`

`comBusca→withSearch` · `comAtivo→withActive`.

### Backend — bridge em `Produto` (tipo/acessor apenas, resto do módulo Produto fora de escopo)

- `produto/domain/Produto.java`: `private Categoria categoria;→private Category categoria;`; `private CorEstampa corEstampa;→private Colorway corEstampa;` (nomes de campo inalterados).
- `produto/service/ProdutoService.java`: imports `CategoriaRepository→CategoryRepository`, `CorEstampaRepository→ColorwayRepository`, `CategoriaNaoEncontradaException→CategoryNotFoundException`, `CorEstampaNaoEncontradaException→ColorwayNotFoundException` (nomes de campo `categoriaRepository`/`corEstampaRepository` inalterados); os dois acessores `p.getCategoria().getNome()→p.getCategoria().getName()` e `p.getCorEstampa().getNome()→p.getCorEstampa().getName()` (mudança obrigatória — mesmo padrão do fix `AuthController.result.empresa().getId()→.company().getId()` do sub-projeto Empresa).
- `produto/repository/ProdutoRepository.java`: **sem mudanças** (ver seção 2, Fora de escopo).

### Banco de dados

Tabela `categoria→category`. Colunas: `nome→name` · `descricao→description` · `ativo→active` · `criado_em→created_at` (`id`, `tenant_id` sem mudança). Índices: `idx_categoria_tenant_nome→idx_category_tenant_name` · `idx_categoria_tenant_id→idx_category_tenant_id`. Política RLS: `categoria_tenant_isolation→category_tenant_isolation`.

Tabela `cor_estampa→colorway`. Colunas: `nome→name` · `data_vigencia→effective_date` · `descricao→description` · `ativo→active` · `criado_em→created_at`. Índices: `idx_cor_estampa_tenant_nome→idx_colorway_tenant_name` · `idx_cor_estampa_tenant_id→idx_colorway_tenant_id`. Política RLS: `cor_estampa_tenant_isolation→colorway_tenant_isolation`.

Migrations dependentes (só o alvo da FK muda, coluna em `produto` fica como está):
- `V22__replace_produto_categoria_with_fk.sql`: `ALTER TABLE produto ADD COLUMN categoria_id UUID REFERENCES categoria(id);` → `REFERENCES category(id)`.
- `V24__add_cor_estampa_to_produto.sql`: `ALTER TABLE produto ADD COLUMN cor_estampa_id UUID REFERENCES cor_estampa(id);` → `REFERENCES colorway(id)`.
- `V6__create_produto.sql`: não precisa de mudança — a coluna `categoria VARCHAR(100)` que ele cria é descartada pela própria `V22` antes de chegar ao schema final (histórico transitório, não uma referência viva à tabela `categoria`).

### API

Rotas: `/api/categorias→/api/categories`, `/api/cores-estampas→/api/colorways` (rota do backend é código, vira inglês; rota do frontend/Vue Router é visível ao cliente, fica em português).

### Frontend — arquivos próprios (renomeados)

- `api/categorias.ts→categories.ts` (+ spec): types e funções seguindo o mesmo mapa de campos/métodos do backend (ex: `listarCategorias→listCategories`, `CategoriaResponse→CategoryResponse`).
- `api/coresEstampas.ts→colorways.ts` (+ spec): mesmo padrão (`listarCoresEstampas→listColorways`, `CorEstampaResponse→ColorwayResponse`).
- `views/CategoriaFormView.vue→CategoryFormView.vue`, `views/CategoriasListView.vue→CategoriesListView.vue` (+ specs).
- `views/CorEstampaFormView.vue→ColorwayFormView.vue`, `views/CoresEstampasListView.vue→ColorwaysListView.vue` (+ specs).
- Rotas visíveis (`/categorias`, `/cores-estampas`) e labels ("Categorias", "Cores / Estampas") inalterados — só nome de arquivo/componente/import.

### Frontend — bridge (import/tipo apenas, fora de escopo)

- `router/index.ts`: só os imports de componente (`CategoriaFormView→CategoryFormView` etc.) — `path`/`name` das rotas inalterados.
- `views/ProdutoFormView.vue` (+ spec): import de `categorias.ts`/`coresEstampas.ts` atualizado para os novos nomes de arquivo/tipo/função; `categoria.nome`/`corEstampa.nome` (acesso a campo do objeto `CategoryResponse`/`ColorwayResponse` importado) viram `.name`. Nomes de variável local (`categorias`, `coresEstampas`, `categoria`, `corEstampa` como var de loop) e campos do form do Produto (`form.categoriaId`, `form.corEstampaId`) ficam como estão.
- `api/produtos.ts`: sem mudanças (ver seção 2).

## 4. Testes

Mesma cobertura de hoje, renomeada: `CategoryControllerTest`, `CategoryRepositoryTest`, `CategoryServiceTest`, `ColorwayControllerTest`, `ColorwayRepositoryTest`, `ColorwayServiceTest` (mirror dos atuais `Categoria*Test`/`CorEstampa*Test`, mesmos casos). `ProdutoServiceTest`/`ProdutoControllerTest` (fora de escopo) recebem só o bridge de tipo necessário para compilar, sem renomear seus próprios casos de teste. Os specs do frontend (`categories.spec.ts`, `colorways.spec.ts`, `CategoryFormView.spec.ts`, `CategoriesListView.spec.ts`, `ColorwayFormView.spec.ts`, `ColorwaysListView.spec.ts`, `ProdutoFormView.spec.ts`) mantêm os mesmos casos, só com os literais/imports atualizados.

**Nomes de método de teste — mapa completo** (verificado diretamente nos arquivos atuais, não presumido — lição do sub-projeto Parceiro, onde uma auditoria por amostragem via `grep | head` deixou passar 2 métodos):

`CategoriaControllerTest` (6 testes, já majoritariamente em inglês) → `CategoryControllerTest`: `createsListsUpdatesAndDeletesCategoria→createsListsUpdatesAndDeletesCategory` · `rejectsDuplicateNomeWithConflict→rejectsDuplicateNameWithConflict` · `rejectsDeletingACategoriaInUseWithBadRequest→rejectsDeletingACategoryInUseWithBadRequest` · `tenantACannotAccessTenantBsCategoria→tenantACannotAccessTenantBsCategory` · `unauthenticatedRequestIsRejected` (sem mudança) · `listingWithoutProductViewPermissionIsForbidden` (sem mudança).

`CorEstampaControllerTest` (7 testes) → `ColorwayControllerTest`: `createsListsUpdatesAndDeletesCorEstampa→createsListsUpdatesAndDeletesColorway` · `rejectsDuplicateNomeWithConflict→rejectsDuplicateNameWithConflict` · `rejectsMissingDataVigenciaWithBadRequest→rejectsMissingEffectiveDateWithBadRequest` · `rejectsDeletingACorEstampaInUseWithBadRequest→rejectsDeletingAColorwayInUseWithBadRequest` · `tenantACannotAccessTenantBsCorEstampa→tenantACannotAccessTenantBsColorway` · `unauthenticatedRequestIsRejected` (sem mudança) · `listingWithoutProductViewPermissionIsForbidden` (sem mudança).

`CategoriaRepositoryTest` (4 testes, já majoritariamente em inglês) → `CategoryRepositoryTest`: `savesCategoriaWithDefaults→savesCategoryWithDefaults` · `nomeMustBeUniquePerTenant→nameMustBeUniquePerTenant` · `sameNomeAllowedAcrossDifferentTenants→sameNameAllowedAcrossDifferentTenants` · `rlsHidesRowsWhenTenantContextUnset` (sem mudança).

`CorEstampaRepositoryTest` (4 testes) → `ColorwayRepositoryTest`: `savesCorEstampaWithDefaults→savesColorwayWithDefaults` · `nomeMustBeUniquePerTenant→nameMustBeUniquePerTenant` · `sameNomeAllowedAcrossDifferentTenants→sameNameAllowedAcrossDifferentTenants` · `rlsHidesRowsWhenTenantContextUnset` (sem mudança).

`CategoriaServiceTest` (8 testes, mistura português/inglês) → `CategoryServiceTest`: `criaERecuperaCategoria→createsAndRetrievesCategory` · `rejectsDuplicateNomeOnCreate→rejectsDuplicateNameOnCreate` · `rejectsDuplicateNomeOnUpdateAgainstAnotherCategoria→rejectsDuplicateNameOnUpdateAgainstAnotherCategory` · `allowsUpdatingACategoriaWithoutChangingItsOwnNome→allowsUpdatingACategoryWithoutChangingItsOwnName` · `deletesUnusedCategoria→deletesUnusedCategory` · `rejectsDeletingACategoriaInUseByAProduto→rejectsDeletingACategoryInUseByAProduct` · `listFiltersByAtivo→listFiltersByActive` · `listAggregatesProdutosVinculadosPerCategoriaInASingleBatch→listAggregatesLinkedProductsPerCategoryInASingleBatch`.

`CorEstampaServiceTest` (8 testes) → `ColorwayServiceTest`: `criaERecuperaCorEstampa→createsAndRetrievesColorway` · `rejectsDuplicateNomeOnCreate→rejectsDuplicateNameOnCreate` · `rejectsDuplicateNomeOnUpdateAgainstAnotherCorEstampa→rejectsDuplicateNameOnUpdateAgainstAnotherColorway` · `allowsUpdatingACorEstampaWithoutChangingItsOwnNome→allowsUpdatingAColorwayWithoutChangingItsOwnName` · `deletesUnusedCorEstampa→deletesUnusedColorway` · `rejectsDeletingACorEstampaInUseByAProduto→rejectsDeletingAColorwayInUseByAProduct` · `listFiltersByAtivo→listFiltersByActive` · `listAggregatesProdutosVinculadosPerCorEstampaInASingleBatch→listAggregatesLinkedProductsPerColorwayInASingleBatch`.

Note: `Produto`/`Produtos` dentro de nomes de método de teste (ex: `rejectsDeletingACategoriaInUseByAProduto`) traduz para `Product`/`Products` mesmo com o módulo Produto em si fora de escopo — é só o nome do método de teste, não uma referência funcional ao módulo.

## 5. Riscos e notas abertas

1. **Reset do banco local necessário** — mesma situação dos sub-projetos anteriores: migrations `V21`/`V23` editadas em vez de geradas como rename, exige recriar o banco local.
2. **Menor que o Parceiro, mas ainda com bridge sensível**: dois módulos pequenos e paralelos (~30 arquivos no total incluindo specs de teste), mas o bridge em `ProdutoService.java` não é só de tipo — exige a correção dos dois acessores `.getNome()→.getName()`, que só quebram em runtime (não em compile-time, já que `getNome()` deixaria de existir e o compilador PEGARIA isso — na verdade esse é um erro de COMPILAÇÃO, não runtime, diferente da classe de bug de string solta encontrada no Parceiro). Isso é uma boa notícia: o compilador força a correção, reduzindo o risco de passar despercebido.
3. **Lição do Parceiro já aplicada**: a auditoria de arquivos não-Java (migrations, JPQL, especificações Criteria API) já foi feita durante este design — nenhuma referência solta tipo string encontrada em `ProdutoRepository`/`ProdutoSpecifications`. Ainda assim, a task final de verificação do plano deve repetir a varredura ampla como rede de segurança (mesmo padrão dos sub-projetos anteriores).
4. **Ordem dos sub-projetos 4b/4c** já decidida: 4b (Produto) em seguida, depois 4c (TabelaPreco). Após o sub-projeto 4 completo, restam Município e Pedido.
