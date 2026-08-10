# Rename Venda → Sale (Código em Inglês) — Spec de Design

> Sub-projeto 1 da iniciativa "Rename para inglês". Escopo completo da iniciativa: renomear todo o codebase mesh-suite (Java, Vue, banco de dados) de português para inglês, mantendo a exibição pro cliente final (rotas visíveis, textos de UI, labels, mensagens de erro) em português. Módulos ainda pendentes após este: Produto (+Categoria, CorEstampa, TabelaPreco), Parceiro, Empresa, Município, Pedido — cada um vira seu próprio sub-projeto com spec→plano→implementação independente, na ordem que for decidida quando chegar a vez.

## 1. Contexto e decisão

O módulo `venda` foi construído inteiro em português (`Venda`, `ItemVenda`, `VendaService`, `faturar`...), espelhando o padrão antigo do módulo `Pedido` — mas a convenção do projeto (já registrada em memória após um aviso anterior) é que código **novo** use inglês, mesmo quando o módulo vizinho mais próximo ainda está em português. Essa fatia corrige isso, servindo também de piloto pro processo de rename que será repetido nos demais módulos.

**Decisões já tomadas com o usuário:**
- Começar por `venda` (mais recente, menor, valida o processo antes dos módulos maiores).
- URL visível no navegador continua em português (`/vendas`); só o código por trás vira inglês.
- Tradução dos termos de domínio: `cliente→customer`, `vendedor→salesperson`, `faturar→issue`.
- Tabelas/colunas do banco também viram inglês já nesta fatia, mesmo apontando pra tabelas que ainda não foram renomeadas (`pedido`, `parceiro`, `app_user`) — evita renomear a mesma coluna duas vezes depois.
- A migration `V26__create_venda.sql` é editada diretamente (renomeada pra `V26__create_sale.sql`) em vez de criar uma migration nova só de rename — código greenfield, sem dado de produção. Exige resetar o banco local para reaplicar as migrations do zero.

## 2. Escopo

### Incluído
- Pacote Java `com.meshsuite.venda` → `com.meshsuite.sale`, todas as classes/campos/métodos renomeados (mapa completo na seção 3).
- Migration `V26` editada para criar `sale`/`sale_item`/`sale_counter` em vez de `venda`/`item_venda`/`venda_contador`.
- Frontend: `api/vendas.ts→api/sales.ts`, `VendasListView.vue→SalesListView.vue` (+ spec).
- Referências cruzadas fora do módulo: `GlobalExceptionHandler` (2 handlers), a mensagem de erro em `PedidoService.avancarStatus`, o import em `PedidosListView.vue`.
- Todos os testes já existentes, renomeados e ajustados para os novos nomes (mesma cobertura, não menos).

### Fora de escopo (fica para quando for a vez de cada módulo)
- Renomear `Pedido`, `Parceiro`, `Produto`/`Categoria`/`CorEstampa`/`TabelaPreco`, `Empresa`, `Município` — ficam em português por enquanto. Campos do `Sale` que apontam pra essas entidades (`order: Pedido`, `customer: Parceiro`, `salesperson: User`) têm nome em inglês mas tipo ainda em português — é esperado e temporário.
- Qualquer texto visível ao usuário final (títulos de tela, labels de coluna, mensagens de erro do frontend, o texto "Vendas"/"Faturar" no menu e nos botões) — tudo isso continua exatamente igual, em português. Só identificadores de código mudam.
- `Module.SALE` (enum de permissão) já está em inglês desde que foi criado — nenhuma mudança necessária ali.

## 3. Mapa de nomes completo

### Backend — classes

| Atual | Novo |
|---|---|
| `Venda` | `Sale` |
| `ItemVenda` | `SaleItem` |
| `VendaContador` | `SaleCounter` |
| `VendaController` | `SaleController` |
| `VendaService` | `SaleService` |
| `VendaRepository` | `SaleRepository` |
| `VendaContadorRepository` | `SaleCounterRepository` |
| `VendaSpecifications` | `SaleSpecifications` |
| `VendaResponse` | `SaleResponse` |
| `ItemVendaResponse` | `SaleItemResponse` |
| `VendaSummaryResponse` | `SaleSummaryResponse` |
| `VendaExceptionHandler` | `SaleExceptionHandler` |
| `VendaNaoEncontradaException` | `SaleNotFoundException` |
| `VendaValidacaoException` | `SaleValidationException` |

### Backend — campos (`Sale`)

`id`, `tenantId` (sem mudança) · `numero→number` · `pedido→order` (tipo continua `Pedido`) · `cliente→customer` (tipo continua `Parceiro`) · `vendedor→salesperson` (tipo continua `User`) · `dataEmissao→issueDate` · `desconto→discount` · `subtotal`, `total` (sem mudança) · `valorIcms→icmsAmount` · `valorIpi→ipiAmount` · `valorPis→pisAmount` · `valorCofins→cofinsAmount` · `criadoEm→createdAt` · `itens→items`

### Backend — campos (`SaleItem`)

`id` · `venda→sale` · `produto→product` (tipo continua `Produto`) · `quantidade→quantity` · `valorUnitario→unitPrice` · `valorTotal→totalAmount` · `valorIcms/Ipi/Pis/Cofins→icms/ipi/pis/cofinsAmount`

### Backend — `SaleCounter`

`tenantId` (sem mudança) · `proximoNumero→nextNumber`

### Backend — `SaleService` / `SaleController`

`faturar(UUID pedidoId)→issue(UUID orderId)` · `listar(...)→list(...)` · `buscarPorId(...)→findById(...)` · `proximoNumero()→nextNumber()` (privado). Endpoint: `POST /api/vendas/faturar/{pedidoId}` → `POST /api/sales/issue/{orderId}`; `GET /api/vendas→GET /api/sales`; `GET /api/vendas/{id}→GET /api/sales/{id}`.

### Backend — DTOs

`VendaResponse`: `numero→number`, `pedidoId→orderId`, `pedidoNumero→orderNumber`, `clienteId→customerId`, `clienteNome→customerName`, `vendedorId→salespersonId`, `vendedorNome→salespersonName`, `dataEmissao→issueDate`, `desconto→discount`, `valorIcms/Ipi/Pis/Cofins→icms/ipi/pis/cofinsAmount`, `itens→items`.
`ItemVendaResponse`: `produtoId→productId`, `produtoNome→productName`, `quantidade→quantity`, `valorUnitario→unitPrice`, `valorTotal→totalAmount`, `valorIcms/Ipi/Pis/Cofins→icms/ipi/pis/cofinsAmount`.
`VendaSummaryResponse`: `numero→number`, `clienteNome→customerName`, `dataEmissao→issueDate`.

### Banco de dados

Tabelas: `venda→sale`, `item_venda→sale_item`, `venda_contador→sale_counter`.
Colunas: `numero→number`, `pedido_id→order_id`, `cliente_id→customer_id`, `vendedor_id→salesperson_id`, `data_emissao→issue_date`, `desconto→discount`, `valor_icms/ipi/pis/cofins→icms/ipi/pis/cofins_amount`, `criado_em→created_at`.
Índices/políticas RLS: `idx_venda_*→idx_sale_*`, `venda_tenant_isolation→sale_tenant_isolation`, `item_venda_tenant_isolation→sale_item_tenant_isolation`, `venda_contador_tenant_isolation→sale_counter_tenant_isolation`.

### Frontend

- `mesh-suite-frontend/src/api/vendas.ts` → `src/api/sales.ts`: `VendaResponse→SaleResponse`, `ItemVendaResponse→SaleItemResponse`, `VendaSummary→SaleSummary`, `listarVendas→listSales`, `buscarVenda→getSale`, `faturarPedido→issueSale`. Campos dos tipos seguem o mesmo mapa dos DTOs do backend (`customerId`, `customerName`, `salespersonId`, `orderId`, `orderNumber`, `issueDate`, `icmsAmount`, etc.).
- `mesh-suite-frontend/src/views/VendasListView.vue` → `src/views/SalesListView.vue` (+ `__tests__/SalesListView.spec.ts`). Template/labels/textos visíveis não mudam — só os bindings (`sale.customerName` em vez de `venda.clienteNome`) e o nome do arquivo/componente.
- `mesh-suite-frontend/src/views/PedidosListView.vue` (não renomeado nesta fatia): import muda de `faturarPedido` (de `@/api/vendas`) para `issueSale` (de `@/api/sales`). A função local que chama isso continua se chamando `faturar()`, mesmo padrão dos vizinhos `avancar()`/`excluir()` nesse arquivo ainda em português.
- `mesh-suite-frontend/src/router/index.ts`: import muda de `VendasListView` para `SalesListView`; a entrada de rota continua `{ path: '/vendas', name: 'vendas', component: SalesListView }` — path e name inalterados.

### Referências cruzadas fora do módulo

- `com.meshsuite.shared.handler.GlobalExceptionHandler`: os dois `@ExceptionHandler` que hoje apontam para `com.meshsuite.venda.exception.VendaNaoEncontradaException`/`VendaValidacaoException` passam a apontar para `com.meshsuite.sale.exception.SaleNotFoundException`/`SaleValidationException`.
- `com.meshsuite.pedido.service.PedidoService.avancarStatus`: a mensagem de erro que hoje cita `POST /api/vendas/faturar/{pedidoId}` passa a citar `POST /api/sales/issue/{orderId}`.

## 4. Testes

Mesma cobertura de hoje, renomeada: `SaleRepositoryTest` (5 casos), `SaleServiceTest` (7 casos, incluindo o de ordenação por `customerName` corrigido na revisão final anterior), `SaleControllerTest` (3 casos), `SalesListView.spec.ts` (4 casos). Ajuste em `PedidosListView.spec.ts`: o teste da ação "Faturar" passa a mockar `issueSale` (de `@/api/sales`) em vez de `faturarPedido`.

## 5. Riscos e notas abertas

1. **Reset do banco local necessário**: como a migration `V26` é editada em vez de gerar uma migration de rename, qualquer banco local que já tenha aplicado a versão antiga precisa ser recriado (`docker-compose down -v` + subir de novo) para reaplicar as migrations do zero. Isso é aceitável agora (sem dado de produção) mas não seria mais uma vez que o sistema estiver em produção — a partir daí, futuras correções de nomenclatura precisarão ser migrations de `ALTER TABLE RENAME`, não edição do arquivo original.
2. **Inconsistência temporária de tipo/nome**: campos como `Sale.order` (tipo `Pedido`) ou `Sale.customer` (tipo `Parceiro`) têm nome em inglês mas tipo em português até a vez desses módulos. Isso é esperado e documentado, não um erro a corrigir agora.
3. **Ordem dos próximos sub-projetos** ainda não decidida — será definida quando este sub-projeto for concluído.
