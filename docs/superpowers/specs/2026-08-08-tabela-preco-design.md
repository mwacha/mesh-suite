# Tabela de Preço — Spec de Design

## 1. Contexto e decisão

`PRD-13-cadastro-comercial.md` documenta Tabela de Preço com um conjunto de campos (código, descrição, valor mínimo, índice de comissão, índice de comissão para pagamento parcelado, índice de reajuste sobre o preço-base, índice de desconto), adiado do recorte inicial de Cadastro Comercial junto com Categoria de Produto e Cor da Estampa (ambos já implementados). O wireframe de referência (`layout/wireframes/09 - Tabela de Precos-v1.html`) tem um modelo mais rico e mais concreto que o texto do PRD: uma regra de ajuste automático (somar/subtrair R$ ou %) com arredondamento configurável, vigência, e uma sub-lista de itens (produtos) com preço individual editável por tabela. Esta fatia segue o wireframe como fonte principal — o PRD vira referência de nomenclatura onde bate, não a estrutura de dados final.

Hoje nada consome Tabela de Preço — `Pedido` usa `Produto.precoVenda` diretamente, e a decisão de excluir Tabela de Preço do escopo de Pedido já foi tomada explicitamente numa fatia anterior desta sessão. Esta fatia constrói o cadastro completo (regra + itens), mas **sem integrar em Pedido/Vendas ainda** — fica pronta para quando esse domínio precisar consultar preço por tabela.

## 2. Escopo

### Incluído
- `TabelaPreco`: nome, modo de seleção de produtos (Todos os Produtos / Selecionar os Produtos), método de ajuste (Automático: operação Somar/Subtrair + tipo R$/% + valor — ou Manual), arredondamento (5 opções), início de vigência (obrigatório), término de vigência (opcional), valor mínimo de venda (opcional), % de comissão padrão (opcional), ativo/inativo.
- `TabelaPrecoItem`: vínculo tabela↔produto, preço nesta tabela (editável, nullable = "Pendente"), % de comissão por item (default herdado da tabela no momento da criação do item).
- Modo "Todos os Produtos": ao salvar, um item é criado para cada produto ativo do tenant.
- Modo "Selecionar os Produtos": lista começa vazia; itens são adicionados manualmente na tela antes de salvar.
- Botão de reset por item: recalcula o preço daquele item pela regra atual da tabela.
- Prévia de preço ao vivo na tela de criação/edição — a fórmula de cálculo (ajuste + arredondamento) é implementada **só em TypeScript**, no frontend. O backend não recalcula nada: ele persiste exatamente os valores de `precoNestaTabela`/`percentualComissao` que o frontend já resolveu e o usuário já viu na tela antes de salvar. Isso evita duplicar a fórmula em Java também — não há risco de divergência porque só existe uma implementação da fórmula.
- CRUD completo, exclusão sem bloqueio (hard delete com cascade nos itens — nada consome a tabela ainda, então não há "em uso" a proteger).
- Reaproveita `Module.PRODUCT` — sem módulo de permissão novo.

### Fora de escopo
- **Qualquer consumo por Pedido/Vendas** — fica para quando esse domínio existir e precisar consultar preço por tabela.
- **Sincronização automática de produtos novos** numa tabela "Todos os Produtos" já existente — a lista de itens fica congelada no que foi adicionado no momento da criação/edição; um produto criado depois não aparece automaticamente numa tabela já salva.
- **Campos do PRD sem equivalente no wireframe** (índice de comissão para pagamento parcelado) — o wireframe já define um modelo mais simples e mais concreto; não inventamos campos extras do PRD que o design aprovado não usa.
- **Validação/recálculo server-side da fórmula de preço** — deliberadamente fora de escopo (ver acima); o backend confia no valor que o frontend envia.

## 3. Modelo de dados

Pacote `com.meshsuite.produto`, em português — mesma convenção de `Categoria`/`CorEstampa`.

### `TabelaPreco` (tabela `tabela_preco` — RLS por tenant direto, mesmo padrão de `categoria`/`cor_estampa`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `nome` | String | obrigatório, único por tenant |
| `modoSelecaoProdutos` | enum (`TODOS_PRODUTOS`, `SELECIONAR_PRODUTOS`) | obrigatório |
| `metodoAjuste` | enum (`AUTOMATICO`, `MANUAL`) | obrigatório |
| `operacaoAjuste` | enum (`SOMAR`, `SUBTRAIR`) | nullable — só relevante quando `AUTOMATICO` |
| `tipoValorAjuste` | enum (`REAL`, `PERCENTUAL`) | nullable — só relevante quando `AUTOMATICO` |
| `valorAjuste` | BigDecimal | nullable — só relevante quando `AUTOMATICO` |
| `arredondamento` | enum (`NAO_ARREDONDAR`, `TERMINAR_EM_0`, `TERMINAR_EM_9`, `TERMINAR_EM_90`, `TERMINAR_EM_99`) | obrigatório |
| `inicioVigencia` | LocalDate | obrigatório |
| `terminoVigencia` | LocalDate | opcional |
| `valorMinimoVenda` | BigDecimal | opcional |
| `percentualComissaoPadrao` | BigDecimal | opcional |
| `ativo` | Boolean | default `true` |
| `criadoEm` | Instant | `updatable = false` |

### `TabelaPrecoItem` (tabela `tabela_preco_item` — item de linha, **RLS via `EXISTS` contra `tabela_preco`**, mesmo padrão de `purchase_order_item`; não tem `tenant_id` próprio)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tabelaPreco` | FK → `TabelaPreco` | obrigatório, `ON DELETE CASCADE` |
| `produto` | FK → `Produto` | obrigatório |
| `precoNestaTabela` | BigDecimal | nullable — "Pendente" (só ocorre em modo `MANUAL`) |
| `percentualComissao` | BigDecimal | nullable, copiado de `percentualComissaoPadrao` da tabela no momento em que o item é resolvido pelo frontend |

## 4. Regras de negócio

1. `nome` da Tabela de Preço é único por tenant.
2. Modo `TODOS_PRODUTOS`: ao salvar (criar ou atualizar), o backend recebe do frontend a lista de itens já resolvida — um por produto ativo do tenant no momento do salvamento. Não há sincronização automática depois.
3. Modo `SELECIONAR_PRODUTOS`: a lista de itens enviada no salvamento é exatamente o que o usuário montou na tela.
4. `PUT /api/tabelas-preco/{id}` substitui a lista de itens inteira (mesmo padrão "regenerar tudo" já usado em `PurchaseOrderService`) — não faz merge incremental.
5. Fórmula de cálculo (implementada só em TypeScript, no frontend, usada pra prévia ao vivo e pro botão de reset por item):
   - Base: `precoBase = produto.precoVenda`.
   - Ajuste: `SOMAR + REAL → precoBase + valorAjuste`; `SOMAR + PERCENTUAL → precoBase × (1 + valorAjuste/100)`; `SUBTRAIR + REAL → precoBase − valorAjuste`; `SUBTRAIR + PERCENTUAL → precoBase × (1 − valorAjuste/100)`.
   - Arredondamento — **sempre para cima** (nunca abaixo do valor ajustado, pra não vender por menos que o calculado):
     - `NAO_ARREDONDAR`: sem alteração.
     - `TERMINAR_EM_0`: próximo valor inteiro de reais cujo último dígito é 0 (ex.: 117,32 → 120,00).
     - `TERMINAR_EM_9`: próximo valor inteiro de reais cujo último dígito é 9 (ex.: 117,32 → 119,00).
     - `TERMINAR_EM_90`: próximo valor cujos centavos são ,90 (ex.: 117,32 → 117,90).
     - `TERMINAR_EM_99`: próximo valor cujos centavos são ,99 (ex.: 117,32 → 117,99).
   - Método `MANUAL`: nenhum cálculo automático — todo item nasce sem `precoNestaTabela` (Pendente) até o usuário preencher manualmente.
6. Botão de reset por item: reaplica a fórmula acima usando o `produto.precoVenda` atual e a regra atual da tabela, sobrescrevendo o valor em tela (não persiste sozinho — só ao salvar a tabela).
7. "Margem" exibida por item é `(precoNestaTabela − produto.precoVenda) / produto.precoVenda`, calculada no frontend, não persistida.
8. Sem bloqueio de exclusão — hard delete direto (cascade nos itens).

## 5. Telas

- `TabelasPrecoListView.vue` (rota `/tabelas-preco`): busca por nome, filtro por status, tabela (nome, resumo do método de ajuste — ex. "Automático · Somar R$ 10,00" ou "Manual" —, início/término de vigência, status), ações (editar, excluir). Botão "+ Nova Tabela". Mesmo padrão visual das listagens já existentes.
- `TabelaPrecoFormView.vue` (`/tabelas-preco/novo`, `/tabelas-preco/:id/editar`):
  - Seção **Regras da Tabela**: todos os campos de cabeçalho, layout replicando o wireframe (toggle Automático/Manual, radio Somar/Subtrair, toggle R$/%, dropdown de arredondamento, datas de vigência, valor mínimo, % comissão padrão).
  - Seção **Itens na Tabela**:
    - Modo `TODOS_PRODUTOS`: lista automaticamente todo produto ativo, com preço calculado ao vivo (recalcula sempre que a regra muda).
    - Modo `SELECIONAR_PRODUTOS`: lista começa vazia; botão "+ Adicionar mais itens à tabela" abre um painel de busca simples (nome/SKU) listando produtos ativos ainda não adicionados, com ação de adicionar por linha.
    - Cada linha: nome do item, código, preço cadastrado (produto), preço nesta tabela (editável), botão reset, margem (calculada), % comissão (editável), remover.
    - Filtro Preenchido/Pendente/Todos sobre a lista de itens (Pendente só é possível em modo `MANUAL`).
  - Salvar envia cabeçalho + lista de itens já resolvida num único POST/PUT.
- `AppSidebar.vue`: item "Tab. Preços" (grupo Vendas, hoje `route: null`) passa a apontar para `/tabelas-preco`.

## 6. API (backend)

- `GET /api/tabelas-preco` — lista paginada, filtro `busca`/`ativo`, `@RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)`.
- `GET /api/tabelas-preco/{id}` — cabeçalho + itens.
- `POST /api/tabelas-preco` — cria cabeçalho + itens, `@RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)`.
- `PUT /api/tabelas-preco/{id}` — atualiza cabeçalho, substitui a lista de itens inteira, `@RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)`.
- `DELETE /api/tabelas-preco/{id}` — hard delete com cascade, `@RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)`.

Sem endpoint de item separado — itens só existem como parte do payload de criação/atualização da tabela (mesmo padrão de `PurchaseOrder`/`PurchaseOrderItem`).

## 7. Testes

- Backend: RLS (`tabela_preco` por `tenant_id`; `tabela_preco_item` via `EXISTS` contra a tabela pai, sem `tenant_id` próprio — mesmo padrão de `purchase_order_item`), nome único, criação/edição com itens (modo Todos/Selecionar), isolamento cross-tenant, permissões, exclusão em cascade.
- Frontend: lista/formulário (`TabelasPrecoListView.spec.ts`, `TabelaPrecoFormView.spec.ts`); testes unitários da fórmula de cálculo em TypeScript (cada operação × tipo de valor × regra de arredondamento, incluindo os casos de borda do arredondamento "sempre pra cima").

## 8. Riscos e notas abertas

1. **Fórmula de preço só no frontend**: decisão deliberada pra evitar duplicação — mas significa que o backend não valida nem garante que `precoNestaTabela` corresponda à regra da tabela. Um cliente de API diferente do frontend (ex. integração futura) poderia enviar qualquer preço por item, sem checagem. Aceitável nesta fatia porque nada consome a tabela ainda; precisa ser revisitado se/quando Vendas passar a confiar nesses preços para faturar.
2. **Sem sincronização de produtos novos**: uma tabela "Todos os Produtos" criada hoje não ganha automaticamente os produtos cadastrados depois — usuário precisa reabrir e resalvar a tabela pra atualizar a lista. Comportamento aceito nesta fatia, mas é uma limitação real de UX a médio prazo.
3. **Painel de "Adicionar mais itens"**: o wireframe não mostra o estado desse painel em detalhe (só o botão) — implementado aqui como uma busca simples inline (nome/SKU) com ação de adicionar por linha, não um modal completo. Ajustável se um wireframe mais detalhado desse fluxo aparecer depois.
