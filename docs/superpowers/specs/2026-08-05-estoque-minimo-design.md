# Estoque Mínimo (StockMovement) — Spec de Design

> Sub-projeto 2 de 5 da iniciativa "Compras completa". Ver `docs/superpowers/specs/2026-08-04-ordem-compra-design.md` seção 1 para a decomposição inteira.

## 1. Contexto e decisão

`PRD-05-estoque.md` documenta o domínio Estoque como o "livro-razão central de quantidade de produto" do sisconf legado — bem mais amplo do que o necessário aqui: Movimentação de Estoque (com ~14 origens possíveis, transferência entre unidades, conversão de unidade de medida), Baixa de Estoque (matéria-prima), Inventário físico periódico (retrato fiscal, Bloco H do SPED), e Estoque em Terceiros.

Esta fatia cobre **apenas o mecanismo mínimo necessário para o sub-projeto 5 (Compra/nota fiscal de entrada) poder debitar/creditar estoque de forma atômica e rastreável** — não o domínio Estoque completo. Baixa de Estoque, Inventário físico e Estoque em Terceiros ficam de fora inteiramente; nenhum deles é dependência de Compra.

`Produto` já tem um campo `quantidadeEstoque` (saldo corrente, hoje só editado manualmente pelo formulário de cadastro) — esta fatia não duplica esse saldo em uma tabela separada, apenas passa a atualizá-lo de forma atômica e a registrar cada ajuste num livro-razão (`StockMovement`) para rastreabilidade, como o PRD exige ("saldo... com histórico de todas as movimentações").

Nada nesta fatia tem um consumidor real ainda — Compra (sub-projeto 5) é quem vai chamar o mecanismo. Por isso: sem tela, sem endpoint de escrita, só o serviço interno + um endpoint de leitura para inspecionar o histórico.

## 2. Escopo

### Incluído
- `StockMovement`: registro append-only de cada ajuste de saldo (produto, tipo, quantidade, origem, saldo após, usuário responsável, observação).
- `StockService.adjustBalance(...)`: ajuste atômico de `Produto.quantidadeEstoque` (mesmo padrão `UPDATE ... RETURNING` já usado no contador de Pedido/Ordem de Compra), gravando o `StockMovement` correspondente na mesma transação.
- `GET /api/stock-movements?productId=...`: único endpoint desta fatia, leitura paginada do histórico de um produto, permission-gated.
- Rejeição de saldo negativo em saídas (`OUTBOUND`).

### Fora de escopo
- **Movimentação de Estoque completa** (transferência entre unidades, conversão de unidade de medida, múltiplas origens do PRD) — só `INBOUND`/`OUTBOUND` nesta fatia; os demais tipos ficam para quando (se) forem necessários.
- **Baixa de Estoque (matéria-prima)** — tela/fluxo de ajuste manual de saída; não é dependência de Compra.
- **Inventário físico** — declaração periódica, retrato fiscal, possível alimentação do SPED Bloco H.
- **Estoque em Terceiros** — controle de produto em poder de terceiros.
- **Qualquer tela/frontend** — sem `StockMovementsView`, sem chamada de API do lado do Vue. Fica pronto para quando Compra (ou uma tela de histórico futura) precisar.
- **Endpoint de escrita** — `adjustBalance` é interno, chamado por código de outros domínios, não por request HTTP direto de usuário.

## 3. Modelo de dados

### `StockMovement` (tabela `stock_movement` — RLS por tenant direto, não via `EXISTS`, já que não é filha de outra entidade como `PurchaseOrderItem`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `product` | FK → `Produto` | |
| `type` | `StockMovementType` (`INBOUND`, `OUTBOUND`) | sem `TRANSFERENCIA`/`ESTOQUE_INICIAL` do PRD nesta fatia |
| `quantity` | BigDecimal(12,3) | sempre positivo — o sinal do ajuste vem de `type`, mesmo tipo/precisão de `PurchaseOrderItem.quantity` |
| `origin` | `StockMovementOrigin` (`MANUAL`, `PURCHASE`) | `MANUAL` usado por testes/futuros ajustes; `PURCHASE` reservado para o sub-projeto 5, sem chamador ainda |
| `referenceId` | UUID | nullable — id do registro de origem (ex. futura Compra) |
| `balanceAfter` | BigDecimal(12,3) | retrato do saldo após a movimentação, nunca recalculado depois (exigência explícita do PRD) |
| `user` | FK → `User` | responsável pela movimentação, obrigatório |
| `note` | String | opcional |
| `createdAt` | Instant | `updatable = false` |

### `Produto`
Sem alteração de schema — `quantidadeEstoque` já existe. Passa a ser atualizado exclusivamente via `StockService.adjustBalance`, nunca por `saveAndFlush` direto de fora do serviço.

## 4. Regras de negócio

1. `adjustBalance` executa um `UPDATE produto SET quantidade_estoque = quantidade_estoque ± quantity WHERE id = :productId RETURNING quantidade_estoque` atômico — nunca ler-então-escrever, mesmo padrão já usado no contador de numeração de Pedido/Ordem de Compra.
2. Uma saída (`OUTBOUND`) que levaria o saldo abaixo de zero é rejeitada com `StockValidationException` — trava aplicada no mecanismo central, não deixada para quem chama.
3. `quantity` deve ser maior que zero (o sinal vem de `type`, nunca de um valor negativo direto).
4. `StockMovement` é append-only — sem edição ou exclusão nesta fatia (o PRD já restringe exclusão a movimentações de origem manual, e nem essa tela existe aqui).
5. `balanceAfter` é sempre o resultado do `UPDATE` atômico, gravado na mesma transação — nunca recalculado depois.

## 5. API (backend)

- `GET /api/stock-movements?productId=...` — lista paginada, `@RequiresPermission(module = Module.STOCK, action = Action.VIEW)`. Rota própria no nível raiz (não aninhada em `/api/produtos`, que é o path português do Produto existente e não deveria ganhar um sub-recurso em inglês) — mesmo padrão top-level já usado por `/api/purchase-orders`. Novo valor `STOCK` no enum `Module`, cobrindo esta fatia (e o que vier depois no domínio Estoque).

Sem `POST`/`PUT`/`DELETE` — `adjustBalance` é um método de serviço Java, não uma rota HTTP.

## 6. Frontend

Nenhum nesta fatia. Sem tela, sem entrada em `api/*.ts`, sem item de sidebar. O módulo `STOCK` também não entra na matriz de permissões do `UserFormView` ainda — não há nada visível para um usuário fazer com essa permissão até existir alguma tela que a consuma; adicionar a linha agora seria um checkbox morto na UI. Revisitar quando a primeira tela do domínio Estoque for construída.

## 7. Testes

- `StockServiceTest`: ajuste atômico (increment/decrement corretos), `balanceAfter` bate com o saldo real, rejeição de saldo negativo, rejeição de `quantity <= 0`, histórico paginado, RLS isolation de `stock_movement`, negação de permissão sem `Module.STOCK` `VIEW`.
- Teste de concorrência real (duas conexões simultâneas ajustando o mesmo produto) fica fora de escopo — mesma decisão já tomada para o contador de Ordem de Compra (o padrão SQL atômico é a garantia, não um teste de corrida real).
- Sem testes de frontend (não há frontend nesta fatia).

## 8. Riscos e notas abertas

1. **`Module.STOCK` sem uso visível na UI ainda**: cria uma permissão que hoje nenhuma tela oferece para conceder de forma útil (um admin pode marcar o checkbox no banco/API diretamente, mas não há UI para isso, já que a matriz do `UserFormView` não inclui `STOCK` — ver seção 6). Aceito como custo temporário; resolvido quando a primeira tela de Estoque for construída.
2. **Vínculo com a futura Compra**: `origin = PURCHASE` e `referenceId` já preparam o campo para quando o sub-projeto 5 existir, mas nada valida ainda que um `referenceId` de `PURCHASE` realmente aponte para uma Compra existente (não há Compra para apontar). Validação estrutural fica para quando Compra for desenhada.
3. **Baixa de Estoque / Inventário / Estoque em Terceiros**: se algum desses vier a ser necessário antes de Compra (ex. um usuário pedir ajuste manual de estoque), será um novo sub-projeto próprio, não uma extensão silenciosa desta fatia.
