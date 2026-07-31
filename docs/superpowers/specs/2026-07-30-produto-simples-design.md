# Cadastro de Produto (Simples) — Spec de Design

**Data**: 2026-07-30
**PRD relacionado**: `prd/PRD-13-cadastro-comercial.md` (subconjunto — só o cadastro-mestre de Produto, tipo Simples), conforme `prd/ORDEM-EXECUCAO.md` item 2
**Referência visual**: `layout/PediMais Prototipo.html` — componentes `ProdutosA` (listagem) e `ProdutosB` (formulário de cadastro, variante "Simples")

## 1. Contexto e decisão

Este é o segundo cadastro-mestre do item 2 da ordem de execução (Cadastro Comercial), depois de Cliente/Fornecedor. O PRD-13 descreve Produto como suportando três modelagens: **Simples** (produto único), **Kit** (composição de outros produtos) e **Com Variação** (combinações geradas automaticamente a partir de tipos/valores, ex. Tamanho × Cor).

**Decisão de decomposição**: dado o tamanho comparável — ou maior — que o slice de Cliente inteiro, os três tipos são tratados como **três specs e planos sequenciais e independentes**, cada um mesclado na `main` antes do próximo começar:
1. **Produto Simples** (este documento)
2. Kit (spec futura)
3. Com Variação (spec futura)

Esta spec cobre apenas o tipo **Simples**. Não há campo `tipo` no banco nesta fatia — todo registro de `produto` criado aqui é implicitamente "Simples". A fatia de Kit decide, quando chegar, como estender o schema (nova coluna/migração), sem antecipação aqui.

**Correção de escopo vs. o protótipo**: o formulário de referência (`ProdutosB`) inclui um seletor "Tipo de Produto" (Simples/Kit/Com Variação) que direciona pra três telas diferentes. Como só Simples existe nesta fatia, o formulário desta fatia **não mostra esse seletor** — ele será reintroduzido quando Kit/Variação existirem.

## 2. Escopo desta rodada

Duas telas — a listagem e o formulário — seguindo a navegação real do protótipo (que, diferente de Cliente, não tem uma tela de perfil/detalhe separada para Produto).

Ativa o item "Produtos" do menu lateral (hoje inerte).

### Fora de escopo (campos do protótipo que dependem de infraestrutura/domínio não construído, ou de decisão já tomada)

- **Imagem do Produto** (upload, galeria com reordenação, máx. 8 imagens) — nenhuma infraestrutura de armazenamento de arquivo existe no sistema ainda; fica de fora por completo, não como um campo de URL substituto.
- **Marca** e **Categoria** como cadastros próprios — campos de texto livre por agora, sem validação de lista. Viram seleção real quando os domínios Marcas/Categorias existirem (mesmo tratamento que Rede Comercial recebeu no Cliente).
- **Unidade de Medida** como cadastro próprio — lista fixa curta no formulário (não um domínio à parte), mesmo espírito do UF do endereço de Cliente.
- Tabela de Preço, Modelo/Ficha Técnica, cadastros auxiliares de característica (Cor, Tamanho, Estampa, Tecido/Aviamento, Grupo de Produto, Origem do Produto) — já adiados desde a decisão do Cliente/PRD-13.
- Seleção em massa, importação via CSV, linhas hierárquicas de variação na listagem — pertencem à fatia de Variação.
- Tipos Kit e Com Variação — planos futuros separados.

## 3. Modelo de dados

### `Produto` (tabela principal — RLS por tenant, mesmo padrão de `parceiro`/`empresa`/`usuario`)

| Campo | Tipo/domínio | Observação |
|---|---|---|
| `id` | UUID | PK |
| `tenant_id` | UUID | RLS |
| `nome` | varchar | obrigatório |
| `sku` | varchar | obrigatório, **único por tenant** |
| `codigo_barras` | varchar, nullable | EAN/GTIN, sem validação de dígito verificador nesta fatia |
| `marca` | varchar, nullable | texto livre |
| `categoria` | varchar, nullable | texto livre |
| `preco_venda` | numeric, obrigatório | maior que zero |
| `preco_custo` | numeric, nullable | |
| `status` | ATIVO \| INATIVO | default ATIVO |
| `descricao` | texto, nullable | |
| `quantidade_estoque` | numeric, default 0 | valor inicial editável, sem log de movimentação — pronto pro domínio Estoque assumir a lógica real no futuro |
| `unidade_medida` | UN \| KG \| G \| L \| ML \| MT \| CM \| CX \| PC \| PAR \| DZ | default UN |
| `estoque_minimo` | numeric, nullable | configuração, não saldo |
| `estoque_maximo` | numeric, nullable | configuração, não saldo |
| `peso` | numeric, nullable | kg |
| `comprimento`, `largura`, `altura` | numeric, nullable | cm |
| `criado_em` | timestamp | automático |

## 4. Regras de negócio

- `sku` único por tenant, validado no backend (constraint UNIQUE + 409 tratado no formulário — mesmo padrão do `documento` de Parceiro).
- `nome`, `sku` e `preco_venda` obrigatórios; `preco_venda` deve ser maior que zero.
- Status tem só dois valores (`ATIVO`/`INATIVO`), ambos setáveis livremente pela UI — sem terceiro estado como o "Em Risco" do Cliente, já que não há equivalente de negócio aqui.

## 5. Telas

### `ProdutosListView.vue` (rota `/produtos`)
- Busca por nome/SKU; filtro por Status.
- Cards de resumo: Total, Ativos, Inativos (mesmo padrão do Cliente, só sem "Em Risco" já que Produto tem apenas dois status).
- Colunas: Código (SKU), Produto (nome), Marca, Preço de Venda, Estoque, Status, Ações.
- Menu Ações (Ver/Editar/Ativar-Inativar/Excluir) — usa `Teleport` desde o início, evitando o bug de clipping por `overflow: hidden` já corrigido no Cliente.
- Paginação real via backend, simplificada (Prev/Next + "página X de Y"), mesma simplificação usada no Cliente.
- "+ Novo Produto" → `ProdutoFormView.vue` (modo criar).

Diferente do Cliente, não há tela de perfil/detalhe separada — o protótipo não define uma para Produto, e "Ver" no menu Ações abre o mesmo formulário em modo somente-visualização... **decisão**: como o protótipo não distingue "ver" de "editar" para Produto (só tem uma tela de cadastro, reaberta tanto para criar quanto editar), o menu Ações desta fatia tem apenas **Editar** (não "Ver"), diferente do Cliente.

### `ProdutoFormView.vue` (rotas `/produtos/novo` e `/produtos/:id/editar`)
Seções, na ordem do protótipo (sem o seletor "Tipo de Produto" e sem a seção "Imagem do Produto", ambos fora de escopo):
1. Informações Gerais: Nome, SKU, Código de Barra, Marca, Categoria, Preço de Venda, Preço de Custo, Status, Descrição.
2. Estoque: Qtd. em Estoque, Unidade de Medida, Estoque Mínimo, Estoque Máximo.
3. Pesos & Dimensões: Peso, Comprimento, Largura, Altura.

Um único componente serve criar e editar. Erros: 409 (SKU duplicado) com mensagem específica; demais erros, mensagem genérica — mesmo padrão do `ClienteFormView.vue`.

## 6. API (backend)

Mesmo padrão de `parceiro`: entity + repository + service + controller + DTO, RLS via `tenant_id`.

- `GET /api/produtos` — lista paginada; query params: `busca` (nome/SKU), `status`.
- `GET /api/produtos/resumo` — contagens Total/Ativos/Inativos, independente dos filtros da lista (mesma simplificação do Cliente).
- `GET /api/produtos/{id}` — detalhe.
- `POST /api/produtos` — criar.
- `PUT /api/produtos/{id}` — atualizar.
- `PATCH /api/produtos/{id}/status` — ativar/inativar.
- `DELETE /api/produtos/{id}` — excluir.

Validações: `nome`, `sku` e `preco_venda` obrigatórios; `preco_venda > 0`; `sku` único por tenant (pré-checagem + fallback de `DataIntegrityViolationException` escopado ao controller de Produto, mesmo padrão usado para Parceiro).

## 7. Testes

- Backend: service (validação de SKU único, preço obrigatório/positivo, RLS por tenant) e controller (CRUD completo, 409, RLS cross-tenant, 401 sem autenticação) — mesmo padrão de `ParceiroServiceTest`/`ParceiroControllerTest`.
- Frontend: formulário (campos obrigatórios, validação de preço, 409 tratado), listagem (busca, filtro, paginação, ações, error-handling) — mesmo padrão das telas de Cliente, incluindo o error-handling estabelecido como convenção desde a revisão final daquele slice.

## 8. Riscos e notas abertas

1. `quantidade_estoque`/`estoque_minimo`/`estoque_maximo` ficam totalmente editáveis pela UI sem qualquer trilha de auditoria — aceitável nesta fatia (mesma decisão do Cliente para campos que antecipam um domínio futuro), mas o domínio Estoque, quando construído, precisa decidir como herdar/migrar esses valores iniciais.
2. Ausência do seletor "Tipo de Produto" nesta fatia é deliberada — quando a fatia de Kit for implementada, a listagem e o formulário desta fatia precisarão ser revisitados para acomodar o seletor e, possivelmente, uma coluna "Tipo" na listagem.
3. `codigo_barras` não tem validação de formato/dígito verificador nesta fatia — puramente informativo.
