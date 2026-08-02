# Cadastro de Usuário + Permissões — Spec de Design

**Data**: 2026-08-02
**PRD relacionado**: `prd/PRD-09-cadastro-seguranca.md` (subconjunto — cadastro-mestre de Usuário e mecanismo de permissão por módulo/ação; Menu, Empresa e Rede de Empresa ficam fora)
**Referência visual**: `layout/PediMais Prototipo.html` — componentes `UsuariosA` (listagem), `UsuariosCadastro` (formulário)

## 1. Contexto e decisão

O PRD-09 cobre um domínio maior do que só cadastro de usuário: Usuário, Menu (árvore hierárquica), Vínculo Usuário×Menu, Vínculo Usuário×Permissão, Empresa, Rede de Empresa. Esta fatia cobre só o cadastro-mestre de Usuário e o mecanismo de permissão por módulo/ação que o protótipo mostra embutido no formulário (seção "Permissões por Módulo"). Menu, Vínculo Usuário×Menu, Empresa e Rede de Empresa ficam para fatias futuras — nenhum deles tem base de dados ou tela própria hoje.

Diferente das fatias anteriores (Cliente/Produto/Pedido), esta introduz **enforcement real** de permissão: os services já existentes de Cliente (`Parceiro`), Produto e Pedido, além do novo de Usuário, passam a checar permissão antes de agir, respondendo 403 quando o usuário autenticado não tem a permissão necessária — não é um dado só decorativo.

**Decisão de idioma** (definida nesta rodada de brainstorm): código novo desta fatia é escrito em inglês — classes, métodos, variáveis, nomes de tabela/coluna. Texto visível ao usuário final (labels de formulário, mensagens de erro, rotas de URL do frontend) continua em português, mesmo padrão de todas as fatias anteriores. Como parte disso, a entidade `Usuario` e o enum `Papel` já existentes (criados durante a fatia de Pedido, usados por `PedidoService` para validar o vendedor) são renomeados agora para `User`/`Role` — única exceção ao "rename de código existente fica para depois", porque esta fatia precisa estender exatamente essas duas classes de qualquer forma, e deixá-las em português enquanto tudo ao redor é inglês criaria uma inconsistência pior. **Um rename completo de `Parceiro`/`Produto`/`Pedido` para inglês é um projeto futuro totalmente separado, fora desta fatia.**

## 2. Escopo desta rodada

### Incluído
- Cadastro-mestre de `User`: listar, criar, editar, ativar/inativar (sem exclusão definitiva).
- Modelo de permissão por módulo/ação (`Module` × `Action`), com um `Profile` (perfil de acesso) definindo um conjunto padrão de permissões na criação, editável por usuário depois.
- Enforcement real: os services de `Parceiro` (Cliente), `Produto`, `Pedido` e `User` passam a checar permissão antes de agir, via anotação `@RequiresPermission` + `PermissionAspect`, lançando 403 quando negado.
- Rename pontual: `Usuario`→`User`, `Papel`→`Role` (com o ajuste correspondente nos 3 arquivos do Pedido que dependem deles por nome, e no `api/usuarios.ts`→`users.ts` do frontend).

### Fora de escopo (documentado, fatias futuras)
- Menu (estrutura hierárquica de itens) e Vínculo Usuário×Menu.
- Empresa e Rede de Empresa.
- "Meus Dados" / autoatendimento de troca de senha pelo próprio usuário logado — login, esqueci-senha e redefinir-senha já existem e continuam exatamente como estão.
- Dados bancários do usuário — o PRD não confirma o uso real desse campo.
- Auto-proteção contra o usuário remover sua própria permissão de acesso a Usuários ou se auto-inativar — risco registrado na seção 9, não implementado.
- Rename de `Parceiro`/`Produto`/`Pedido` (e seus enums, tabelas, rotas de API, frontend) para inglês — projeto futuro separado.
- Exclusão definitiva de usuário — `Pedido.vendedor_id` referencia `User` sem cascade; só ativar/inativar, mesmo tratamento já usado em Produto.

## 3. Modelo de dados

### `User` (renomeado de `Usuario`; tabela `app_user`, renomeada de `usuario` — `user` é palavra reservada no Postgres)

| Campo | Tipo/domínio | Observação |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `name` | varchar | obrigatório (era `nome`) |
| `email` | varchar | obrigatório, único por tenant (já existente) |
| `passwordHash` | varchar | hash da senha (era `senhaHash`) |
| `role` | enum `Role` | obrigatório (era `papel`/`Papel`) |
| `active` | boolean | default true (era `ativo`) |
| `createdAt` | timestamp | automático (era `criadoEm`) |
| `lastAccessAt` | timestamp, nullable | já existente (era `ultimoAcesso`) |
| `phone` | varchar, nullable | **novo** |
| `profile` | enum `Profile` | **novo**, obrigatório |

### `Role` (enum, renomeado de `Papel`)
`ADMINISTRATIVE` (era `ADMINISTRATIVO`), `SALES_REP` (era `REPRESENTANTE`), `PRODUCTION` (era `PRODUCAO`), `OUTSOURCED` (era `TERCEIRIZADO`), `ADMIN` (era `ADMINISTRADOR`) — mesmos 5 valores, sem mudança de significado, só de nome. `SALES_REP` continua sendo o valor exigido pelo `Pedido.vendedor`. A migration de rename precisa, além de renomear a coluna e o `CHECK` constraint, fazer um `UPDATE` mapeando cada valor antigo já gravado (ex. `'REPRESENTANTE'`) para o novo literal em inglês (`'SALES_REP'`) — não é só um rename de coluna, os dados existentes também mudam de valor.

### `Profile` (enum, novo)
`ADMIN`, `MANAGER`, `SALES`, `VIEWER` — controla a matriz de permissões-padrão (seção 5). Conceito separado de `Role`: `Role` é a função do usuário no negócio (usada por regras de domínio, ex. vendedor do Pedido); `Profile` é o nível de acesso ao sistema.

### `user_permission` (tabela filha — RLS via `EXISTS` contra `app_user`, mesmo padrão de `parceiro_contato`/`item_pedido`)

| Campo | Tipo | Observação |
|---|---|---|
| `user_id` | UUID | FK → `app_user` |
| `module` | enum `Module` | |
| `action` | enum `Action` | |

Chave composta `(user_id, module, action)` — cada linha é uma permissão concedida.

### `Module` (enum)
`CUSTOMER`, `PRODUCT`, `ORDER`, `USER` — um por domínio já existente com endpoints reais. Não inclui "Preços" (Tabela de Preço não tem domínio construído ainda).

### `Action` (enum)
`VIEW`, `CREATE`, `EDIT`, `DELETE` — conjunto uniforme em todos os módulos (simplificação deliberada; o protótipo varia a lista de ações por módulo, ex. "Cancelar" em vez de "Excluir" para Pedidos, mas não existe operação de cancelamento distinta de exclusão em nenhum backend hoje).

## 4. Regras de negócio

- `name`, `email`, `role`, `profile` obrigatórios; `email` único por tenant (409, mesmo padrão do `documento`/`sku`).
- Senha: mínimo 8 caracteres com letras e números; obrigatória e deve bater com a confirmação na criação; na edição, campos vazios mantêm a senha atual (não altera).
- Sem exclusão definitiva de usuário — só ativar/inativar, evitando violação de integridade referencial com `Pedido.vendedor_id`.
- Permissões: o backend **não recalcula** a matriz padrão — só persiste a lista de `(module, action)` que vier na requisição. O frontend pré-marca os checkboxes a partir da matriz da seção 5 quando o `Profile` é escolhido (só para UX), mas o que é de fato salvo é o estado dos checkboxes no momento do envio, customizável livremente antes de salvar. Trocar o `Profile` de um usuário já existente **não** reescreve permissões já customizadas.
- Enforcement: cada método relevante dos services de `Parceiro`, `Produto`, `Pedido` e `User` ganha `@RequiresPermission(module = ..., action = ...)`. Um `PermissionAspect` (`@Order(2)`) roda **depois** do `TenantContextAspect` (`@Order(1)`) — precisa rodar depois porque a checagem de permissão consulta `user_permission`, que tem RLS via `EXISTS` contra `app_user.tenant_id`; se rodasse antes do `SET LOCAL app.tenant_id`, a consulta não encontraria nenhuma linha e negaria tudo incondicionalmente. Nega com `PermissionDeniedException` → 403.
- `GET /api/users/sales-reps` (renomeado de `/representantes`) **não é gateado** por `USER.VIEW` — é uma consulta de apoio ao seletor de vendedor do formulário de Pedido, não "ver a tela de Usuários"; continua acessível a qualquer usuário autenticado, independente de permissão.

## 5. Matriz de permissões-padrão por Profile (referência, calculada no frontend)

| Profile | Customer | Product | Order | User |
|---|---|---|---|---|
| **ADMIN** | View/Create/Edit/Delete | View/Create/Edit/Delete | View/Create/Edit/Delete | View/Create/Edit |
| **MANAGER** | View/Create/Edit | View/Create/Edit | View/Create/Edit | View |
| **SALES** | View/Create/Edit | View | View/Create/Edit | — |
| **VIEWER** | View | View | View | — |

`USER.DELETE` nunca é concedido a ninguém — não existe endpoint de exclusão de usuário, então essa permissão nunca é checada por nada; existe no modelo só por uniformidade do enum `Action`, sem efeito prático. Esta matriz é uma proposta de julgamento de negócio (não confirmada por PRD nem protótipo, que o próprio PRD-09 marca como mecanismo não confirmado) — ajustável.

## 6. Telas

### `UsersListView.vue` (rota `/usuarios`)
- Busca por nome/e-mail; filtro por Perfil (`Profile`) e Status.
- Cards de resumo: Total, Ativos, Inativos.
- Colunas: Nome, E-mail, Perfil (badge), Status, Ações.
- Menu Ações (`Teleport`, mesmo padrão de Cliente/Produto/Pedido): Editar, Ativar/Inativar — sem Excluir.
- "+ Novo Usuário" → `UserFormView.vue`.

### `UserFormView.vue` (rotas `/usuarios/novo` e `/usuarios/:id/editar`)
1. **Dados do Usuário**: Nome completo*, E-mail*, Telefone, Papel* (`Role` — rótulos em português mapeando os valores em inglês: Administrativo/Representante/Produção/Terceirizado/Administrador), Perfil de Acesso* (`Profile` — Admin/Gerente/Vendedor/Visualizador), Status (toggle Ativo/Inativo).
2. **Acesso ao Sistema**: Senha, Confirmar Senha — obrigatórios e iguais na criação; vazios na edição mantêm a senha atual.
3. **Permissões por Módulo** (seção colapsável, fechada por padrão): grade Cliente/Produto/Pedido/Usuário × Visualizar/Criar/Editar/Excluir, pré-marcada pela matriz da seção 5 ao escolher o Perfil, editável livremente.
- Validação: nome/email/papel/perfil obrigatórios; senha (na criação) obrigatória e igual à confirmação.
- Botão único "Salvar Usuário" + "Cancelar". Sem tela de detalhe separada (mesmo padrão de Produto/Pedido).
- Item "Usuários" da sidebar (hoje inerte) é ativado.

## 7. API

- `GET /api/users` — lista paginada; `busca` (nome/email), `profile`, `status`.
- `GET /api/users/counts` — contagens Total/Ativos/Inativos.
- `GET /api/users/{id}` — detalhe, incluindo `permissions: [{module, action}]`.
- `POST /api/users` — criar.
- `PUT /api/users/{id}` — atualizar (`password` opcional; `permissions` substitui a lista inteira — clear + re-add, mesmo padrão dos contatos do Cliente/itens do Pedido).
- `PATCH /api/users/{id}/status` — ativar/inativar.
- `GET /api/users/sales-reps` (renomeado de `/representantes`) — sem gate de permissão.

`ParceiroController`/`ProdutoController`/`PedidoController` não ganham rotas novas — só os métodos de service correspondentes ganham `@RequiresPermission`.

## 8. Testes

- Backend: repository (RLS de `user_permission` via `EXISTS`), service (email único, validação de senha, CRUD de permissões, `hasPermission`), controller (CRUD completo, 403 sem permissão, 401 sem autenticação, RLS cross-tenant com `entityManager.clear()`), aspecto de permissão (ordem correta com `TenantContextAspect` — checagem só funciona depois do tenant aplicado).
- Regressão: `PedidoServiceTest`/`PedidoControllerTest` continuam passando depois do rename de `Usuario`/`Papel` para `User`/`Role`.
- Frontend: formulário (campos obrigatórios, senha e confirmação iguais, permissões pré-marcadas por perfil e editáveis, edição com senha em branco mantém a atual), listagem (busca, filtro, ativar/inativar, sem excluir), 403 tratado com mensagem amigável em qualquer tela que chame um endpoint sem permissão.

## 9. Riscos e notas abertas

1. O rename de `Usuario`→`User`/`Papel`→`Role` toca 3 arquivos já mergeados do Pedido (`PedidoService.java`, `PedidoServiceTest.java`, `PedidoControllerTest.java`) e 2 do frontend (`PedidoFormView.vue` + seu spec) — mudança mecânica (import + nome de tipo), mas a suíte completa de Pedido precisa rodar depois para confirmar que nada quebrou.
2. A matriz de permissões-padrão (seção 5) é uma proposta de julgamento de negócio, não confirmada por PRD nem protótipo — ajustável a qualquer momento sem migração (é só um mapeamento no frontend).
3. `user` é palavra reservada no Postgres/SQL — a tabela se chama `app_user` para evitar a necessidade de aspas em toda consulta.
4. O enforcement cobre só os 4 domínios já existentes (Cliente/Produto/Pedido/Usuário). Qualquer domínio futuro (Compras, Financeiro, Estoque) precisa lembrar de adicionar `@RequiresPermission` também — não é automático, não há um mecanismo que force isso.
5. `GET /api/users/sales-reps` sem gate de permissão é uma exceção deliberada — se no futuro isso for considerado vazamento de informação (nome de vendedores visível a qualquer usuário autenticado, independente de permissão em Usuários), precisa revisão.
6. Rename completo de `Parceiro`/`Produto`/`Pedido` para inglês (entidades, tabelas, rotas de API, frontend) fica documentado aqui como próximo projeto natural, mas não faz parte desta fatia.
7. Nenhum mecanismo de auto-proteção existe para impedir um usuário de remover sua própria permissão em `USER` ou se auto-inativar, ficando sem acesso administrativo ao próprio cadastro de usuários — aceitável nesta fatia (mesmo espírito de outras simplificações já registradas), mas vale revisão futura se isso acontecer na prática.
