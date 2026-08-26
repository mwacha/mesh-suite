# Perfis de Permissão + Tela de Permissões — Spec de Design

**Data**: 2026-08-26
**PRD relacionado**: `prd/PRD-09-cadastro-seguranca.md` (mesmo subconjunto da fatia anterior)
**Spec anterior**: `docs/superpowers/specs/2026-08-02-cadastro-usuario-design.md` — introduziu `Profile` (enum) e a matriz de permissões-padrão, mas deixou registrado na seção 9 ("riscos e notas abertas") que a matriz era "uma proposta de julgamento de negócio, não confirmada por PRD nem protótipo". Esta fatia resolve isso: transforma a matriz hardcoded no frontend num registro real e editável no backend.
**Referência visual**: `layout/wireframes/12 - Permissoes-v1.html` (bundle interativo — inspecionado via browser, não HTML estático)

## 1. Contexto e decisão

Hoje `Profile` é um enum fixo (`ADMIN`, `MANAGER`, `SALES`, `VIEWER`) e a matriz de permissões-padrão de cada perfil está hardcoded em `UserFormView.vue` (`DEFAULT_MATRIX`), usada só para pré-marcar checkboxes ao escolher o perfil — o backend nunca vê essa matriz, só recebe a lista final de `(module, action)` que o formulário envia. Não existe hoje nenhuma tela dedicada de "Permissões"; o item já existe na sidebar (`CONFIGURAÇÕES → Permissões`) mas aponta para `route: null`.

O wireframe 12 mostra uma tela `Config. / Permissões` com duas abas — **Perfis de Permissão** (CRUD de perfis, cada um com sua própria matriz módulo×ação, incluindo um botão "+ Novo Perfil") e **Usuários e Permissões** (lista de usuários com atalho de permissões por usuário).

**Decisão de escopo** (definida no brainstorm desta fatia):
- **Perfis passam a ser dinâmicos** — uma entidade nova no banco (`PermissionProfile`), não mais só um enum. Dá pra criar, editar e excluir perfis customizados além dos 4 padrão.
- **Sem duplicar o enum `Profile` do `User`**: investigação encontrou 24 arquivos de teste espalhados por módulos sem nenhuma relação com Permissões (Vendas, Compras, Produtos, Estoque etc.) que chamam `.setProfile(Profile.ADMIN)` só para montar um usuário autenticado válido no setup do teste — não têm relação com a lógica de autorização real, que já é feita hoje inteiramente via `UserPermissionGrant` explícito por usuário (`PermissionAspect` nunca lê `Profile`). Para não tocar esses 24 arquivos, o enum `profile` **continua existindo** no `User` (coluna com default, não aparece mais em nenhuma tela), e um campo novo `permissionProfileId` (FK nullable) é adicionado ao lado — esse é o único usado pela tela nova e pelo formulário de Usuário daqui pra frente.
- **Ação "Cancelar"** do wireframe (só no módulo Pedidos) fica fora de escopo — não existe endpoint de cancelamento de pedido gateado por permissão hoje; adicionar a ação seria especulativo.
- **Aba "Usuários e Permissões" reaproveita `UsersListView.vue`** existente (renderizado dentro da segunda aba), em vez de duplicar a listagem — o formulário de usuário já tem a seção "Permissões por Módulo" funcionando.
- **Formulário de perfil em página própria**, não modal/slide-over como o wireframe — segue o padrão já estabelecido em todo o resto do app (Fornecedor, Tabela de Preço, Forma de Pagamento etc.), consistência > fidelidade pixel-a-pixel ao protótipo.

## 2. Escopo desta rodada

### Incluído
- `PermissionProfile`: CRUD completo (listar, criar, editar, excluir), com seed automático dos 4 perfis padrão por tenant na primeira listagem (idempotente — não existe hoje um fluxo de registro de tenant para ganchar esse seed).
- `User.permissionProfileId` (novo, ao lado do enum `profile` legado) — `UserFormView.vue` passa a usar a API de perfis em vez do `DEFAULT_MATRIX` hardcoded.
- Tela `/permissoes` com as duas abas do wireframe.
- Correção pontual: `MODULES` do `UserFormView.vue` (e agora também da matriz de `PermissionProfile`) passa a incluir `STOCK` (Estoque), que faltava na fatia anterior — todos os 9 módulos reais do enum `Module` ficam cobertos.

### Fora de escopo (documentado, fatias futuras)
- Ação `Action.CANCEL` — sem caso de uso real hoje.
- Remoção do enum `Profile` legado do `User` — fica como campo morto até uma fatia futura decidir migrar os 24 arquivos de teste e os dados existentes.
- Slide-over/modal fiel ao wireframe para editar perfil — página própria, como o resto do app.
- Qualquer alteração em `PermissionAspect`/enforcement — a checagem continua exatamente como está, via `UserPermissionGrant` por usuário; perfis continuam sendo só uma fonte de "valores padrão" no momento de montar/editar um usuário.

## 3. Modelo de dados

### `PermissionProfile` (novo)

| Campo | Tipo/domínio | Observação |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `name` | varchar | obrigatório, único por tenant |
| `description` | varchar, nullable | |
| `isSystem` | boolean, default false | `true` só nos 4 perfis semeados automaticamente — trava **exclusão**, não trava edição da matriz nem do nome |
| `createdAt` | timestamp | automático |
| `grants` | coleção de `(module, action)` | reaproveita a classe `UserPermissionGrant` já existente (`@Embeddable`), só que numa tabela filha própria `permission_profile_grant` |

Seed automático (na primeira chamada de `list()` sem nenhum perfil para o tenant): 4 perfis com `isSystem = true` — `Admin`, `Gerente`, `Vendedor`, `Visualizador` — com a mesma matriz que hoje está em `DEFAULT_MATRIX` no `UserFormView.vue`, estendida para cobrir `STOCK` (Visualizar, para Gerente e Visualizador; nada para Vendedor — mesmo critério usado para os outros módulos operacionais).

### `User` (alteração)

| Campo | Tipo/domínio | Observação |
|---|---|---|
| `profile` | enum `Profile` | **legado** — continua no banco com default `ADMIN`, deixa de ser obrigatório no request, não aparece mais em nenhuma tela |
| `permissionProfileId` | UUID, FK nullable → `PermissionProfile` | **novo** — o que a tela de Usuário usa de fato daqui pra frente |

## 4. Regras de negócio

- `PermissionProfile.name` obrigatório, único por tenant (409 em duplicata, mesmo padrão de `PaymentMethod.description`/`PriceTable.name`).
- Exclusão bloqueada se `isSystem = true` (400) **ou** se algum `User` referencia o perfil via `permissionProfileId` (400 — mesmo padrão de `CategoryInUseException`/`ColorwayInUseException`).
- Edição (nome, descrição, matriz de permissões) permitida para **qualquer** perfil, incluindo os `isSystem` — o cadeado do wireframe é só contra exclusão.
- `UserService.applyRequest`: `profile` (enum) só é sobrescrito se vier preenchido no request; se vier nulo, mantém o valor atual/default — nenhum dos 24 arquivos de teste alheios a Permissões precisa mudar.
- Trocar o `permissionProfileId` de um usuário já existente **não** reescreve permissões já customizadas — mesmo comportamento que já existe hoje pra troca de `Profile` (só afeta o pré-preenchimento no momento da escolha, no frontend).

## 5. Telas

### `PermissionsView.vue` (rota `/permissoes`)
- Duas abas: "Perfis de Permissão" (default) e "Usuários e Permissões".
- Aba "Usuários e Permissões" renderiza o componente `UsersListView.vue` existente, sem alterações nele.

### `PermissionProfilesListView.vue` (dentro da aba "Perfis de Permissão")
- Listagem: Nome, Descrição, quantidade de usuários vinculados, resumo "X de 9 módulos" com acesso, botão Editar.
- "+ Novo Perfil" → `PermissionProfileFormView.vue`.
- Excluir (menu de ações) — bloqueado com mensagem de erro do backend se `isSystem` ou em uso.

### `PermissionProfileFormView.vue` (rotas `/permissoes/perfis/novo` e `/permissoes/perfis/:id/editar`)
- Nome*, Descrição.
- Matriz módulo × ação: 9 módulos (`CUSTOMER, PRODUCT, ORDER, USER, PURCHASE, STOCK, PAYABLE, SALE, PURCHASE_INVOICE`) × 4 ações (`VIEW, CREATE, EDIT, DELETE`), checkboxes livres (sem mínimo obrigatório).

### `UserFormView.vue` (alteração)
- Select "Perfil de Acesso" passa a buscar `GET /api/permission-profiles` em vez do array `PROFILES` hardcoded.
- Ao selecionar um perfil, `applyDefaultPermissions()` usa a matriz vinda do backend daquele perfil (`GET /api/permission-profiles/{id}`) em vez do `DEFAULT_MATRIX` local.
- Envia `permissionProfileId` no payload; **não envia mais `profile`** (enum) — o backend mantém o default.

### Sidebar
- Item "Permissões" (`CONFIGURAÇÕES`, hoje `route: null`) passa a apontar para `/permissoes`.

## 6. API

- `GET /api/permission-profiles` — lista paginada (`busca` por nome; sem filtro de ativo/inativo — perfil não tem esse conceito, só existe ou foi excluído); semeia os 4 padrão na primeira chamada do tenant.
- `GET /api/permission-profiles/{id}` — detalhe com `grants`.
- `POST /api/permission-profiles` — criar (`isSystem` nunca vem do cliente, sempre `false`).
- `PUT /api/permission-profiles/{id}` — atualizar (nome, descrição, `grants` — substitui a lista inteira, mesmo padrão de contatos/parcelas).
- `DELETE /api/permission-profiles/{id}` — excluir (400 se `isSystem` ou em uso).
- `UserRequest` ganha `permissionProfileId` (nullable); `profile` deixa de ter `@NotNull`.
- `UserResponse`/`UserListItemResponse` ganham `permissionProfileId`/`permissionProfileName`.

Gate de permissão (`@RequiresPermission`) do novo service reaproveita `Module.USER` — mesmo padrão de Category/Colorway reaproveitando `Module.PRODUCT`.

## 7. Testes

- Backend: repository (RLS de `permission_profile`/`permission_profile_grant`), service (seed automático idempotente, nome único, bloqueio de exclusão `isSystem`/em uso, edição de perfil `isSystem` permitida), controller (CRUD completo, 403/401, RLS cross-tenant), `UserService`/`UserController` (novo campo `permissionProfileId` opcional, `profile` continua opcional sem quebrar os 24 arquivos existentes que só chamam `.setProfile(...)` na entidade diretamente).
- Regressão: suíte completa do backend (os 24 arquivos alheios a Permissões não devem precisar de nenhuma mudança).
- Frontend: `PermissionProfilesListView`/`PermissionProfileFormView` (CRUD, validação de nome obrigatório), `UserFormView` (perfil dinâmico vindo da API, pré-preenchimento correto, envio de `permissionProfileId`), `PermissionsView` (troca de aba, aba de usuários renderiza `UsersListView` sem alteração de comportamento).

## 8. Riscos e notas abertas

1. Seed automático no `list()` tem uma janela de corrida teórica (duas requisições simultâneas do mesmo tenant sem nenhum perfil ainda) — mitigada pela constraint `UNIQUE(tenant_id, name)`: a segunda tentativa de insert falha com `DataIntegrityViolationException`, tratada como 409 pelo mesmo `ExceptionHandler` de nome duplicado; não trava a listagem em si (só logaria/ignoraria a falha do seed duplicado). Aceitável nesta fatia.
2. O enum `Profile` do `User` fica como campo morto (não exposto em nenhuma tela, mas ainda gravado com default) — próxima fatia natural é decidir removê-lo de vez, migrando os 24 arquivos de teste e os dados existentes (documentado aqui, não faz parte desta entrega).
3. Igual à fatia anterior: a matriz de permissões-padrão semeada pros 4 perfis do sistema é julgamento de negócio, não confirmada por PRD — mas agora é editável por qualquer administrador diretamente na tela, sem precisar de deploy.
4. `GET /api/users/sales-reps` continua sem gate de permissão (decisão da fatia anterior, não revisitada aqui).
