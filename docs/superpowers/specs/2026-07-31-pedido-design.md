# Cadastro de Pedido — Spec de Design

**Data**: 2026-07-31
**PRD relacionado**: `prd/PRD-12-vendas.md` (subconjunto — só Pedido, não Venda), conforme `prd/ORDEM-EXECUCAO.md` item 3
**Referência visual**: `layout/PediMais Prototipo.html` — componentes `PedidosA` (listagem) e `PedidoA` (formulário único de captura)

## 1. Contexto e decisão

Este é o primeiro domínio operacional construído sobre os cadastros-mestre já prontos (Cliente/Fornecedor, Produto). PRD-12 descreve dois documentos distintos:

1. **Pedido** — documento comercial interno, não fiscal (o que o cliente pediu).
2. **Venda** — documento fiscal de saída, com cálculo detalhado de ICMS/ICMS-ST/IPI/PIS/COFINS por item, compartilhando estrutura com o domínio Compras.

`prd/ORDEM-EXECUCAO.md` já separa os dois em itens distintos (3 e 4). Esta spec cobre **só Pedido**. Venda fica para uma fatia futura separada.

**Correção de escopo vs. o PRD**: o PRD-12 descreve um fluxo de status do Pedido com 9 valores (Digitado, Exportado, Importado, Cancelado, Validado, Faturado, Aguardando Autorização, Rejeitado, Autorizado), mas o próprio PRD assinala (§8, risco 1) que a relação exata entre esses estados **não foi confirmada** na investigação e "requer investigação dedicada antes de qualquer decisão de reimplementação". A tela de referência (`PedidosA`/`PedidoA`) usa um fluxo bem mais simples: apenas 3 status (Digitado, Em Preparo, Faturado), em progressão linear. Esta fatia adota o fluxo simplificado da tela de referência — os 6 status adicionais do PRD ficam fora até uma investigação dedicada decidir o fluxo real.

**"Faturado" nesta fatia é só um marcador de status.** Não dispara geração de Venda, baixa de estoque, título financeiro ou NF-e — todos esses mecanismos pertencem a domínios ainda não construídos (Venda em si, Financeiro, Fiscal/Tributário, Estoque).

## 2. Escopo desta rodada

Duas telas — listagem e formulário único de criar/editar — seguindo a navegação real do protótipo (que, como Produto, não tem uma tela de perfil separada para Pedido).

Ativa o item "Pedidos" do menu lateral (hoje inerte).

### Pequenas extensões nos domínios já existentes, necessárias para esta fatia
- `GET /api/parceiros` ganha um filtro por papel (`papel=CLIENTE`), para o seletor de cliente do pedido mostrar só parceiros com esse papel.
- Novo endpoint, só leitura, `GET /api/usuarios/representantes` — lista usuários com papel `REPRESENTANTE`, para o seletor de vendedor. Não é um CRUD completo de Usuário (esse cadastro continua fora de escopo, item "Usuários" do menu continua inerte).

### Fora de escopo (campos do PRD/protótipo que dependem de domínio não construído, ou decisão já tomada)
- **Venda** (documento fiscal, cálculo de tributos) — item 4 da ordem, fatia futura separada.
- **Tabela de Preço** e **Condição de Pagamento** — campos opcionais no protótipo, mas nenhum dos dois domínios existe (Tabela de Preço é do Produto, adiada; Condição de Pagamento é do Financeiro, não construído). Ficam de fora por completo nesta fatia.
- **Desconto detalhado** (o PRD tem 4 campos: valor, % total, % sobre valor, % sobre condição de pagamento) — simplificado para um único valor de desconto em R$, igual a tela de referência mostra.
- Os 6 status adicionais do PRD (Exportado, Importado, Validado, Aguardando Autorização, Autorizado, Rejeitado) — não confirmados, ficam para investigação dedicada.
- Campos preenchidos pelo domínio Expedição (quantidade de volumes, peso bruto, indicador de volume) — Expedição não construído.
- Percentual de comissão do representante — não confirmado em detalhe no PRD, sem tela de referência mostrando esse campo.
- Fluxo "Gerenciar Venda" (atendimento consolidado) e "Agrupamento de Pedido" — ambos descritos no PRD como não confirmados em profundidade, fora desta fatia.
- Exportação de lista (botão "Exportar" no protótipo) e o filtro avançado por widget customizado — mesma simplificação já usada em Cliente/Produto (busca + selects nativos).

## 3. Modelo de dados

### `Pedido` (tabela principal — RLS por tenant, mesmo padrão de `parceiro`/`produto`)

| Campo | Tipo/domínio | Observação |
|---|---|---|
| `id` | UUID | PK |
| `tenant_id` | UUID | RLS |
| `numero` | integer | sequencial por tenant, gerado automaticamente (não editável), único por tenant |
| `cliente_id` | UUID | FK → `parceiro`; deve ter papel CLIENTE (validado no service) |
| `vendedor_id` | UUID | FK → `usuario`; deve ter papel REPRESENTANTE (validado no service) |
| `data_pedido` | date | obrigatório, default hoje |
| `data_entrega` | date, nullable | previsão de entrega |
| `status` | DIGITADO \| EM_PREPARO \| FATURADO | default DIGITADO; progressão linear, sem retrocesso |
| `desconto` | numeric | default 0 |
| `subtotal` | numeric | soma dos itens, recalculado a cada salvamento |
| `total` | numeric | subtotal − desconto |
| `criado_em` | timestamp | automático |

### `PedidoContador` (tabela de contador, um registro por tenant — mecanismo de numeração)

| Campo | Tipo | Observação |
|---|---|---|
| `tenant_id` | UUID | PK, FK → `tenant` |
| `proximo_numero` | integer | incrementado atomicamente (`UPDATE ... RETURNING`) dentro da transação de criação do pedido |

### `ItemPedido` (tabela filha — RLS via EXISTS no `pedido` pai, mesmo padrão do `parceiro_contato`)

| Campo | Tipo | Observação |
|---|---|---|
| `id` | UUID | PK |
| `pedido_id` | UUID | FK → `pedido` |
| `produto_id` | UUID | FK → `produto` |
| `quantidade` | numeric | |
| `valor_unitario` | numeric | capturado no momento da adição (snapshot do `preco_venda` do produto) — não vinculado ao vivo, para não alterar retroativamente pedidos já feitos se o preço do produto mudar depois |
| `valor_total` | numeric | quantidade × valor_unitario |

## 4. Regras de negócio

- `numero` gerado automaticamente via `PedidoContador`, único por tenant, não editável pelo usuário.
- Cliente deve ter papel CLIENTE; Vendedor deve ter papel REPRESENTANTE — ambos validados no backend, erro 400 se o parceiro/usuário referenciado não tiver o papel exigido.
- Pelo menos 1 item é obrigatório para salvar um pedido.
- `valor_unitario` de cada item é copiado do `preco_venda` do produto no momento em que o item é adicionado ao pedido — mudanças futuras no preço do produto não afetam pedidos já criados.
- Status só avança na ordem DIGITADO → EM_PREPARO → FATURADO — tentar pular etapas ou retroceder é rejeitado com 400.

## 5. Telas

### `PedidosListView.vue` (rota `/pedidos`)
- Busca por número/cliente/vendedor; filtro por Status.
- Cards de resumo: Total, Digitados, Em Preparo, Faturados.
- Colunas: Nº, Cliente, Vendedor, Data, Total, Status, Ações.
- Menu Ações (`Teleport`, mesmo padrão de Cliente/Produto): Editar, "Avançar para Em Preparo"/"Avançar para Faturado" (rótulo muda conforme o status atual; item não aparece quando já Faturado), Excluir.
- "+ Novo Pedido" → `PedidoFormView.vue`.

### `PedidoFormView.vue` (rotas `/pedidos/novo` e `/pedidos/:id/editar`)
- Seção Dados do Pedido: Cliente\* (busca entre parceiros com papel CLIENTE), Data do Pedido (default hoje), Previsão de Entrega, Vendedor\* (busca entre usuários com papel REPRESENTANTE).
- Seção Itens: buscador de produto + campo de quantidade + "+ Adicionar" — ao adicionar, preenche o valor unitário automaticamente a partir do preço de venda do produto (editável depois); tabela de itens já adicionados com botão de remover; bloco de totais (Subtotal, Desconto, Total) recalculado em tempo real.
- Validação: Cliente, Vendedor e ao menos 1 item são obrigatórios.
- Um único botão "Salvar Pedido" (sem distinção rascunho/final — simplificação em relação ao protótipo, que tem dois botões sem comportamento diferenciado) + "Cancelar".

Sem tela de perfil/detalhe separada, mesmo padrão do Produto — o formulário único serve tanto para criar quanto editar.

## 6. API (backend)

Mesmo padrão de `parceiro`/`produto`: entity + repository + service + controller + DTO, RLS via `tenant_id`.

- `GET /api/pedidos` — lista paginada; query params: `busca` (nº/cliente/vendedor), `status`.
- `GET /api/pedidos/resumo` — contagens Total/Digitados/Em Preparo/Faturados.
- `GET /api/pedidos/{id}` — detalhe, incluindo itens.
- `POST /api/pedidos` — criar (gera `numero` via `PedidoContador`).
- `PUT /api/pedidos/{id}` — atualizar.
- `PATCH /api/pedidos/{id}/status` — avança um estágio; rejeita pular/retroceder.
- `DELETE /api/pedidos/{id}` — excluir.
- `GET /api/parceiros?papel=CLIENTE` — filtro novo no endpoint já existente.
- `GET /api/usuarios/representantes` — endpoint novo, só leitura.

## 7. Testes

- Backend: repository (RLS, numeração por tenant via contador), service (validação de papel de cliente/vendedor, mínimo de 1 item, progressão de status, cálculo de totais), controller (CRUD completo, RLS cross-tenant, 401/400).
- Frontend: formulário (campos obrigatórios, adicionar/remover item, cálculo de totais em tempo real), listagem (busca, filtro, avançar status, error-handling já estabelecido em Cliente/Produto).

## 8. Riscos e notas abertas

1. Editar um pedido já Faturado continua permitido nesta fatia (sem trava de negócio) — quando Venda existir e um pedido faturado estiver de fato vinculado a um documento fiscal, essa trava provavelmente precisa existir. Decisão consciente de não implementar agora, já que "Faturado" aqui é só um marcador.
2. Os 6 status adicionais do PRD (Exportado, Importado, Validado, Aguardando Autorização, Autorizado, Rejeitado) exigem investigação dedicada antes de qualquer decisão de modelagem — não estão no schema desta fatia, e adicioná-los depois pode exigir migração.
3. `PedidoContador` com incremento atômico por tenant garante numeração contígua, mas usa uma linha por tenant como ponto de contenção — sob alta concorrência de criação de pedidos do mesmo tenant, isso serializa os `POST /api/pedidos`. Aceitável no volume esperado desta fase.
