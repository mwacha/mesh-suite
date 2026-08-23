# Cadastro de Fornecedores — Spec de Design

> Item MENU-01 do `tabela-execucao.md` — hoje um item inerte no menu (`AppSidebar.vue`, grupo CADASTROS, "Fornecedores", `route: null`).

## 1. Contexto e decisão

`Fornecedor` já existe como `PartnerRole.SUPPLIER` dentro da entidade genérica `Partner` (`com.meshsuite.partner`), que já tem CRUD completo e é usada hoje só pela tela de Cliente (`ClientesListView.vue`/`ClienteFormView.vue`/`ClienteDetailView.vue`, filtrando por `PartnerRole.CUSTOMER`). O backend (`PartnerController`, `PartnerService`, `PartnerRepository`, `PartnerSpecifications`) já é genérico por papel: `list()`/`summary()` aceitam um parâmetro `papel: PartnerRole` opcional, e `PartnerService.validate()` já aceita um `Partner` com qualquer combinação de papéis `CUSTOMER`/`SUPPLIER`. O formulário de Cliente já expõe os dois checkboxes de papel (Cliente e Fornecedor) no mesmo cadastro — um parceiro pode ser os dois ao mesmo tempo hoje.

Essa investigação leva à decisão central: **Fornecedores não precisa de nenhuma mudança de backend** — é uma feature inteiramente de frontend, reaproveitando 100% do `api/partners.ts`, `PartnerController` e `PartnerService` já existentes. As 3 telas novas espelham estruturalmente as 3 telas de Cliente já implementadas, com rótulos/defaults trocados.

**Permissão**: mantém `Module.CUSTOMER` compartilhado entre Cliente e Fornecedor, exatamente como já é hoje (toda operação de `Partner`, independente do papel, já é protegida por `CUSTOMER:VIEW/CREATE/EDIT/DELETE`). Não se cria um `Module.SUPPLIER` dedicado — decisão explícita para não introduzir uma segregação de permissão que o sistema não tem hoje nem foi pedida.

## 2. Escopo

### Incluído
- `FornecedoresListView.vue` — listagem paginada com busca e filtros (Status, Nr. Documento, UF, Cidade), espelhando `ClientesListView.vue`.
- `FornecedorFormView.vue` — criar/editar, espelhando `ClienteFormView.vue`, com `roles` default `['SUPPLIER']` (checkbox Cliente continua visível/editável — um Fornecedor pode virar Cliente também sem sair da tela, mesma lógica inversa de Cliente).
- `FornecedorDetailView.vue` — visualização read-only com layout mestre-detalhe (rail de busca + painel), espelhando `ClienteDetailView.vue`, com adaptação nas abas (ver seção 3).
- 4 rotas novas em `router/index.ts`: `/fornecedores`, `/fornecedores/novo`, `/fornecedores/:id/editar`, `/fornecedores/:id`.
- Item "Fornecedores" no `AppSidebar.vue` ganha `route: '/fornecedores'` (ícone 🏭 já existe).
- Um spec de teste por view, espelhando a cobertura das specs de Cliente equivalentes.

### Fora de escopo
- **Qualquer mudança de backend** — `Partner`, `PartnerController`, `PartnerService`, `api/partners.ts` não mudam nada; são só consumidos com `papel: 'SUPPLIER'` em vez de `'CUSTOMER'`.
- **`Module.SUPPLIER` dedicado** — decisão já tomada na seção 1; permissão continua compartilhada via `Module.CUSTOMER`.
- **Integração real da aba "Ordens de Compra" com `PurchaseOrder`** — mesmo que `PurchaseOrder` já exista e tenha `supplierId`, o endpoint de listagem (`GET /api/purchase-orders`) não tem hoje um filtro por fornecedor, e adicioná-lo seria mudança de backend fora do pedido original. A aba fica com o mesmo placeholder inerte que `ClienteDetailView.vue` já usa para "Pedidos" hoje, mesmo com `SalesOrder` também já existindo de verdade — mesma decisão de escopo já tomada naquela tela, replicada aqui por consistência.
- **Integração real da aba "Financeiro" com `AccountsPayable`** — mesma razão acima; fica como placeholder inerte, espelhando `ClienteDetailView.vue`.
- **Campos stub de venda na aba Dados** (Tabela de Preço, Limite de Crédito, Forma de Pagamento, Vendedor Responsável) — existem em `ClienteDetailView.vue` como campos desabilitados com `title` explicando a dependência futura (domínio Financeiro). Não têm equivalente natural do lado de compra hoje (não existe, por exemplo, um "Comprador Responsável" por fornecedor — `PurchaseOrder.buyer` é por ordem, não por fornecedor). Em vez de inventar um stub sem fundamento no domínio, esses campos são **omitidos** na aba Dados de Fornecedor.

## 3. Telas

### `FornecedoresListView.vue`
Cópia estrutural de `ClientesListView.vue` (mesmos componentes: `AppShell`, `PageHeader`, `FilterBar`, `StatPill`, `StatusBadge`, `ActionsMenu`, `Pagination`). Mudanças:
- Título "Fornecedores"; texto de busca "Buscar fornecedor por nome..."; contagem "N fornecedores cadastrados"; mensagens de erro "...lista de fornecedores"/"...resumo de fornecedores".
- `listPartners({ ..., papel: 'SUPPLIER' })` e `getPartnerSummary('SUPPLIER')` no lugar de `'CUSTOMER'`.
- Rotas de navegação: `fornecedores-novo`, `fornecedores-editar`.
- `data-test="novo-fornecedor"` no botão "+ Novo Fornecedor".

Filtros, colunas da tabela (Nome/Razão Social, Documento, Cidade, Telefone, Status), ordenação e paginação são idênticos a Cliente — mesmos campos existem em `Partner` independente do papel.

### `FornecedorFormView.vue`
Cópia estrutural de `ClienteFormView.vue`. Mudanças:
- Título "Novo Fornecedor" / "Editar Fornecedor"; botão "Salvar Fornecedor".
- Estado inicial do formulário: `roles: ['SUPPLIER']` (era `['CUSTOMER']`).
- Mensagem de erro do toast: "Fornecedor salvo com sucesso!".
- Navegação pós-salvar/cancelar: `router.push({ name: 'fornecedores' })`.

Todos os campos (Tipo de Pessoa, Documento, Nome Fantasia/Razão Social, Indicador de Inscrição Estadual, Inscrição Estadual/Municipal/Suframa, endereço completo, contatos, observação) são idênticos — nenhum é específico de Cliente. Os dois checkboxes de papel (Cliente/Fornecedor) continuam ambos presentes e editáveis, igual ao form de Cliente.

### `FornecedorDetailView.vue`
Cópia estrutural de `ClienteDetailView.vue` (layout rail + painel com abas). Mudanças:
- Título "Fornecedor"; busca do rail "Buscar fornecedor...".
- Botão inerte "+ Pedido" → "+ Ordem de Compra" (continua `btn-inert`, sem navegação real, mesmo `title` explicando escopo).
- Aba "Pedidos" → **"Ordens de Compra"**, mesmo placeholder estático "Nenhuma ordem de compra ainda" (sem chamada de API, mesmo padrão do stub atual).
- Aba "Financeiro" mantém o nome e o placeholder "Nenhum lançamento financeiro ainda".
- Aba "Dados": mesmos campos genéricos (Razão Social, CNPJ/CPF, Nome Fantasia, Inscrição Estadual) — **sem** os 4 campos stub de venda (ver seção 2, Fora de escopo).
- Aba "Endereços" e "Contatos": idênticas a Cliente.
- Navegação: `fornecedores-detalhe`, `fornecedores-editar`, `fornecedores` (voltar/cancelar).
- `listPartners`/`getPartner` chamados sem filtro de papel no rail (mesmo comportamento hoje em `ClienteDetailView.vue` — o rail de busca não filtra por papel, mostra qualquer parceiro que bater a busca; mantido para não divergir do padrão já existente, mesmo que o contexto seja "fornecedores").

## 4. Rotas e Sidebar

```ts
{ path: '/fornecedores', name: 'fornecedores', component: FornecedoresListView },
{ path: '/fornecedores/novo', name: 'fornecedores-novo', component: FornecedorFormView },
{ path: '/fornecedores/:id/editar', name: 'fornecedores-editar', component: FornecedorFormView },
{ path: '/fornecedores/:id', name: 'fornecedores-detalhe', component: FornecedorDetailView },
```

Em `AppSidebar.vue`, grupo CADASTROS: `{ icon: '🏭', label: 'Fornecedores', route: null }` → `{ icon: '🏭', label: 'Fornecedores', route: '/fornecedores' }`.

## 5. Testes

Um spec por view, espelhando a cobertura das specs de Cliente:
- `FornecedoresListView.spec.ts` — carregamento inicial, busca, filtros, ordenação, paginação, ações (ver/editar/bloquear-ativar/excluir).
- `FornecedorFormView.spec.ts` — criação com `roles` default `['SUPPLIER']`, edição, validação (papel obrigatório, documento, etc.), toggle dos dois checkboxes de papel.
- `FornecedorDetailView.spec.ts` — carregamento, navegação pelo rail, troca de abas, aba "Dados" sem os campos de venda.

Mais o ajuste em `AppSidebar.spec.ts`: teste de navegação para `/fornecedores` ao clicar no item (mesmo padrão já usado para Compras/Notas de Entrada).

## 6. Riscos e notas abertas

1. **Duplicação de código entre Cliente e Fornecedor**: as 3 telas de Fornecedor são, em grande parte, cópias quase literais das 3 telas de Cliente (mesmo padrão que `PurchaseOrder`/`Sale`/`SalesOrder` já seguem entre si neste projeto — arquivo próprio por conceito de domínio, sem abstração compartilhada). Extrair um componente genérico `PartnerListView`/`PartnerFormView`/`PartnerDetailView` parametrizado por papel é uma refatoração possível, mas fica fora de escopo aqui — YAGNI, e divergiria do padrão já estabelecido no resto do código.
2. **Rail de busca em `FornecedorDetailView.vue` não filtra por papel** (ver seção 3) — mesmo comportamento de `ClienteDetailView.vue` hoje; se um parceiro Cliente aparecer na navegação lateral da tela de Fornecedor, é um comportamento herdado, não uma regressão introduzida aqui.
3. **Sem integração real com Ordens de Compra/Financeiro** (ver seção 2) — quando essas integrações forem desenhadas de verdade (provavelmente junto com a mesma decisão para o lado de Cliente/Pedido/Financeiro), as duas telas de detalhe devem ser revisitadas juntas.
