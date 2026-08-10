# Rename Parceiro → Partner (Código em Inglês) — Spec de Design

> Sub-projeto 3 da iniciativa "Rename para inglês". Sub-projeto 1 (Venda→Sale) e sub-projeto 2 (Empresa→Company) concluídos e mesclados em `main` (commits `0e354fd` e `4b93bd1`). Escopo completo da iniciativa: renomear todo o codebase mesh-suite de português para inglês, mantendo a exibição pro cliente final (rotas visíveis, textos de UI, labels, mensagens de erro) em português. Módulos ainda pendentes após este: Produto (+Categoria, CorEstampa, TabelaPreco), Município, Pedido.

## 1. Contexto e decisão

`Parceiro` é uma entidade genérica de parceiro de negócio — pode ter os papéis CLIENTE, FORNECEDOR e/ou TRANSPORTADORA simultaneamente (não é só "cliente"). Tem CRUD completo próprio: controller, service, repository, specifications, 4 exceções, 4 enums, 6 DTOs, entidade filha `ParceiroContato` (contatos da empresa parceira), migration própria com 3 tabelas. É referenciado por 4 outros módulos (Pedido, AccountsPayable, PurchaseOrder, Sale) — todos já usando nomes de campo em inglês (`cliente` continua em português no Pedido pois esse módulo ainda não foi renomeado; `supplier`/`customer` já em inglês nos módulos já renomeados), então só o **tipo** `Parceiro→Partner` precisa mudar nesses consumidores, não os nomes de campo.

**Decisões já tomadas com o usuário:**
- `Parceiro` → `Partner`.
- Campos de endereço brasileiro (`logradouro`, `numero`, `bairro`, `complemento`, `uf`, `cidade`, `cep`) traduzem integralmente — são vocabulário comum, não siglas fiscais/legais.
- Enums traduzem também os valores (persistidos como string no banco via `@Enumerated(EnumType.STRING)`) — mesmo padrão do sub-projeto Empresa (`ativo→active`).
- Colisão de nomes resolvida: `ParceiroResumoResponse` (contadores agregados do endpoint `/resumo`) → `PartnerSummaryResponse`; `ParceiroSummaryResponse` (linha da listagem paginada, já parcialmente em inglês) → `PartnerListItemResponse`. Alinha com o padrão já usado em outros módulos do projeto, onde "Summary" sempre significa contadores agregados.
- Migration `V5__create_parceiro.sql` editada diretamente (renomeada para `V5__create_partner.sql`) — mesmo padrão greenfield dos sub-projetos anteriores, exige resetar o banco local.
- Frontend: só `api/parceiros.ts` é renomeado (`partners.ts`). As views `Cliente*.vue` (que são uma UI específica de "cliente", filtro `papel=CLIENTE`, não o conceito Parceiro inteiro) recebem só ajuste mínimo de import/tipo/valores literais para compilar contra a nova API — o rename delas (`Cliente→Customer`) fica para um sub-projeto futuro e separado.
- A chave `"mensagem"` usada em `Map.of("mensagem", ...)` nas respostas de erro é uma convenção do projeto inteiro (usada por Sale, Pedido, PurchaseOrder, GlobalExceptionHandler, etc.), não específica do Parceiro — fica fora de escopo, como o `empresa_id` do JWT ficou fora do sub-projeto Empresa.

## 2. Escopo

### Incluído
- Pacote Java `com.meshsuite.parceiro` → `com.meshsuite.partner`: todas as classes, DTOs, exceções, enums, specifications, controller, service, repository (mapa completo na seção 3).
- Migration `V5` editada para criar as tabelas `partner`/`partner_role`/`partner_contact`.
- **Migrations posteriores com FK literal para `parceiro(id)`** (achado na auditoria de arquivos não-Java, mesmo tipo de lacuna que a auditoria do sub-projeto Empresa deixou passar no `R__seed_dev_tenant.sql`): `V7__create_pedido.sql` (`cliente_id UUID NOT NULL REFERENCES parceiro(id)`), `V11__create_purchase_order.sql` (`supplier_id ... REFERENCES parceiro(id)`), `V15__create_accounts_payable.sql` (`supplier_id ... REFERENCES parceiro(id)`), `V26__create_sale.sql` (`customer_id ... REFERENCES parceiro(id)`) — todas precisam de `REFERENCES parceiro(id)` → `REFERENCES partner(id)`, senão a cadeia de migrations quebra num banco resetado do zero (a tabela `parceiro` não existiria mais). `V9__create_user_permission.sql` e `V11`/`V7` também têm comentários mencionando `parceiro_papel`/`parceiro_contato` por nome — atualizar para `partner_role`/`partner_contact` por consistência (mesmo padrão do fix de comentário em `V3__create_usuario.sql` no sub-projeto Empresa).
- **`R__seed_dev_test_clientes.sql`** (seed repetível de 62 linhas de dados de teste para a tela de Clientes, perfil dev/test): reescrever para inserir em `partner`/`partner_role` com as novas colunas (`person_type`, `document`, `trade_name`, `legal_name`, `status`) e novos valores de enum (`'JURIDICA'/'FISICA'→'LEGAL_ENTITY'/'INDIVIDUAL'`, `'ATIVO'/'EM_RISCO'/'BLOQUEADO'→'ACTIVE'/'AT_RISK'/'BLOCKED'`, `'CLIENTE'→'CUSTOMER'`). Texto de dado de negócio (`'Cliente Teste 001'` etc.) fica em português — é conteúdo de exemplo, não identificador de código.
- Rota da API: `/api/parceiros` → `/api/partners`.
- `api/parceiros.ts` → `partners.ts` no frontend, com todos os types e funções renomeados.
- Limpeza de referências fora do módulo: tipo `Parceiro→Partner` em `Pedido.java`/`PedidoService.java`, `AccountsPayable.java`/`AccountsPayableService.java`, `PurchaseOrder.java`/`PurchaseOrderService.java`, `Sale.java` (só o tipo — nomes de campo `cliente`/`supplier`/`customer` ficam); os 3 handlers de exceção do Parceiro em `shared/handler/GlobalExceptionHandler.java`; os valores literais de enum (`'CLIENTE'`, `'FORNECEDOR'`, `'ATIVO'`, `'EM_RISCO'`, `'BLOQUEADO'`) e imports nos arquivos de teste/view que os usam fora do próprio módulo.

### Fora de escopo
- Qualquer texto visível ao usuário final: mensagens de erro (`"Já existe um parceiro cadastrado..."`, `"Parceiro não encontrado"` etc. continuam em português, inalteradas), label "Clientes" no menu lateral, rotas do Vue Router (`/clientes`, `/clientes/novo` etc.).
- A chave `"mensagem"` do envelope de erro JSON (convenção do projeto inteiro).
- Rename das views `ClientesListView.vue`, `ClienteFormView.vue`, `ClienteDetailView.vue`, `DashboardView.vue` e seus arquivos de spec — só recebem ajuste mínimo de import/tipo/literais.
- Os demais módulos ainda em português (Pedido, Produto, Categoria, CorEstampa, TabelaPreco, Município) — ficam para os próximos sub-projetos.

## 3. Mapa de nomes completo

### Backend — classes

| Atual | Novo |
|---|---|
| `Parceiro` | `Partner` |
| `ParceiroContato` | `PartnerContact` |
| `ParceiroRepository` | `PartnerRepository` |
| `ParceiroController` | `PartnerController` |
| `ParceiroService` | `PartnerService` |
| `ParceiroSpecifications` | `PartnerSpecifications` |
| `ParceiroExceptionHandler` | `PartnerExceptionHandler` |
| `ParceiroRequest` | `PartnerRequest` |
| `ParceiroResponse` | `PartnerResponse` |
| `ParceiroContatoDto` | `PartnerContactDto` |
| `ParceiroResumoResponse` | `PartnerSummaryResponse` |
| `ParceiroSummaryResponse` | `PartnerListItemResponse` |
| `ParceiroStatusRequest` | `PartnerStatusRequest` |
| `DocumentoDuplicadoException` | `DuplicateDocumentException` |
| `ParceiroNaoEncontradoException` | `PartnerNotFoundException` |
| `ParceiroValidacaoException` | `PartnerValidationException` |

### Backend — enums (classe + valores)

| Atual | Novo |
|---|---|
| `PapelParceiro` {CLIENTE, FORNECEDOR, TRANSPORTADORA} | `PartnerRole` {CUSTOMER, SUPPLIER, CARRIER} |
| `StatusParceiro` {ATIVO, EM_RISCO, BLOQUEADO} | `PartnerStatus` {ACTIVE, AT_RISK, BLOCKED} |
| `TipoPessoa` {FISICA, JURIDICA} | `PersonType` {INDIVIDUAL, LEGAL_ENTITY} |
| `IndicadorIe` {NAO_CONTRIBUINTE, CONTRIBUINTE, CONTRIBUINTE_ISENTO} | `TaxIndicator` {NON_TAXPAYER, TAXPAYER, EXEMPT_TAXPAYER} |

### Backend — campos de `Partner`

`id`, `tenantId`, `whatsapp`, `status` — sem mudança de nome.
`tipoPessoa→personType` · `documento→document` · `nomeFantasia→tradeName` · `razaoSocial→legalName` · `papeis→roles` · `emailsCobranca→billingEmails` · `indicadorIe→taxIndicator` · `inscricaoEstadual→stateRegistration` · `inscricaoMunicipal→municipalRegistration` · `inscricaoSuframa` (sem mudança — sigla legal, mesmo padrão do `cnpj`) · `cep→zipCode` · `logradouro→street` · `numero→number` · `bairro→neighborhood` · `complemento→complement` · `uf→state` · `cidade→city` · `observacao→notes` · `criadoEm→createdAt` · `contatos→contacts`.

### Backend — campos de `PartnerContact`

`id`, `email` — sem mudança. `parceiro→partner` · `nome→name` · `telefoneComercial→businessPhone` · `telefoneCelular→mobilePhone` · `cargo→jobTitle`.

### Backend — campos de `PartnerSummaryResponse` (contadores agregados)

`total` — sem mudança. `ativos→active` · `emRisco→atRisk` · `bloqueados→blocked`. Mesmo mapa se aplica à interface `PartnerSummary` no frontend (`api/partners.ts`), consumida por `DashboardView.vue` (`parceiroResumo.value.ativos` → `.active`).

### Backend — métodos (`PartnerService`/`PartnerController`)

`listar→list` · `resumo→summary` · `buscarPorId→findById` · `criar→create` · `atualizar→update` · `atualizarStatus→updateStatus` · `excluir→delete` · `buscarEntidadePorId→findEntityById` · `validar→validate` · `normalizarDocumento→normalizeDocument` · `aplicar→apply` · `contarPorStatus→countByStatus` · `toSummary→toListItem` · `toResponse` (sem mudança).

### Backend — `PartnerRepository`

`existsByDocumento→existsByDocument` · `existsByDocumentoAndIdNot→existsByDocumentAndIdNot` · `countByStatus` (sem mudança) · `countByStatusAndPapeisContaining→countByStatusAndRolesContaining`.

### Backend — `PartnerSpecifications`

`comBusca→withSearch` · `comStatus→withStatus` · `comTipoPessoa→withPersonType` · `comUf→withState` · `comCidade→withCity` · `comDocumento→withDocument` · `comPapel→withRole`.

### Backend — dentro de `shared/handler/GlobalExceptionHandler.java` (módulo não renomeado, só limpeza de referências)

`handleParceiroNaoEncontrado→handlePartnerNotFound` (captura `PartnerNotFoundException`) · `handleDocumentoDuplicado→handleDuplicateDocument` (captura `DuplicateDocumentException`) · `handleParceiroValidacao→handlePartnerValidation` (captura `PartnerValidationException`). Mensagens de erro (`e.getMessage()`, texto literal em português) inalteradas.

### Backend — bridge (tipo apenas, campos ficam como estão)

- `pedido/domain/Pedido.java`, `pedido/service/PedidoService.java`: `private Parceiro cliente;` → `private Partner cliente;` (nome do campo `cliente` inalterado).
- `payable/domain/AccountsPayable.java`, `payable/service/AccountsPayableService.java`: `private Parceiro supplier;` → `private Partner supplier;`.
- `purchaseorder/domain/PurchaseOrder.java`, `purchaseorder/service/PurchaseOrderService.java`: `private Parceiro supplier;` → `private Partner supplier;`.
- `sale/domain/Sale.java`: `private Parceiro customer;` → `private Partner customer;`.

### Banco de dados

Tabela `parceiro→partner`. Colunas: `tipo_pessoa→person_type` · `documento→document` · `nome_fantasia→trade_name` · `razao_social→legal_name` · `status` (sem mudança de nome, valores mudam) · `emails_cobranca→billing_emails` · `whatsapp` (sem mudança) · `indicador_ie→tax_indicator` · `inscricao_estadual→state_registration` · `inscricao_municipal→municipal_registration` · `inscricao_suframa→suframa_registration` · `cep→zip_code` · `logradouro→street` · `numero→number` · `bairro→neighborhood` · `complemento→complement` · `uf→state` · `cidade→city` · `observacao→notes` · `criado_em→created_at`.

Tabela `parceiro_papel→partner_role` (coluna `parceiro_id→partner_id`, `papel→role`). Tabela `parceiro_contato→partner_contact` (coluna `parceiro_id→partner_id`, `nome→name`, `telefone_comercial→business_phone`, `telefone_celular→mobile_phone`, `cargo→job_title`).

Índices: `idx_parceiro_tenant_documento→idx_partner_tenant_document` · `idx_parceiro_tenant_id→idx_partner_tenant_id` · `idx_parceiro_contato_parceiro_id→idx_partner_contact_partner_id`.

Políticas RLS: `parceiro_tenant_isolation→partner_tenant_isolation` · `parceiro_papel_tenant_isolation→partner_role_tenant_isolation` · `parceiro_contato_tenant_isolation→partner_contact_tenant_isolation`.

CHECK constraints atualizados com os novos valores de enum: `person_type IN ('INDIVIDUAL','LEGAL_ENTITY')` · `status IN ('ACTIVE','AT_RISK','BLOCKED')` · `role IN ('CUSTOMER','SUPPLIER','CARRIER')` · `tax_indicator IN ('NON_TAXPAYER','TAXPAYER','EXEMPT_TAXPAYER')`.

### API

Rota: `/api/parceiros` → `/api/partners` (mesmo padrão do Venda→Sale — rota do backend é código, vira inglês; rota do frontend/Vue Router é visível ao cliente, fica em português).

### Frontend — `api/parceiros.ts` → `partners.ts`

Types: `TipoPessoa→PersonType` · `PapelParceiro→PartnerRole` · `StatusParceiro→PartnerStatus` · `IndicadorIe→TaxIndicator` · `ParceiroContato→PartnerContact` · `ParceiroRequest→PartnerRequest` · `ParceiroResponse→PartnerResponse` · `ParceiroSummary→PartnerListItem` · `ListarParceirosParams→ListPartnersParams` · `ParceiroResumo→PartnerSummary`.

Funções: `listarParceiros→listPartners` · `buscarParceiro→getPartner` · `criarParceiro→createPartner` · `atualizarParceiro→updatePartner` · `atualizarStatusParceiro→updatePartnerStatus` · `excluirParceiro→deletePartner` · `buscarResumoParceiros→getPartnerSummary`.

### Frontend — bridge (import/tipo/literais apenas, arquivo e labels visíveis ficam)

`views/ClientesListView.vue`, `ClienteFormView.vue`, `ClienteDetailView.vue`, `DashboardView.vue`, `PedidoFormView.vue`, `PurchaseOrderFormView.vue` (+ seus arquivos `__tests__/*.spec.ts`): atualizar imports para `partners.ts`, atualizar valores literais usados como parâmetro de API ou comparação de tipo (`'CLIENTE'→'CUSTOMER'`, `'FORNECEDOR'→'SUPPLIER'`, `'ATIVO'→'ACTIVE'`, `'EM_RISCO'→'AT_RISK'`, `'BLOQUEADO'→'BLOCKED'`). Labels visíveis ao usuário (`'Ativo'`, `'Em Risco'`, `'Bloqueado'`, `'Ativar'`, `'Bloquear'`, título "Clientes" etc.) ficam inalterados — só as chaves internas de comparação/API mudam. `DashboardView.vue` também usa o campo `ParceiroResumo.ativos` (vira `PartnerSummary.active`, ver mapa de campos abaixo).

## 4. Testes

Mesma cobertura de hoje, renomeada: `PartnerControllerTest`, `PartnerRepositoryTest`, `PartnerServiceTest` (mirror dos atuais `Parceiro*Test`, mesmos casos). Os arquivos de teste de outros módulos que criam um `Parceiro` como fixture (`AccountsPayableControllerTest`, `PedidoControllerTest`, `PurchaseOrderControllerTest`, `SaleControllerTest`, e seus respectivos repository/service tests) mantêm os mesmos casos, só com os identificadores renomeados. Os specs do frontend (`parceiros.spec.ts→partners.spec.ts`, `ClienteFormView.spec.ts`, `ClientesListView.spec.ts`, `ClienteDetailView.spec.ts`, `DashboardView.spec.ts`, `PedidoFormView.spec.ts`, `PurchaseOrderFormView.spec.ts`) mantêm os mesmos casos, só com os literais/imports atualizados.

**Nomes de método de teste:** `ParceiroRepositoryTest`/`ParceiroControllerTest` já usam nomes majoritariamente em inglês (só o substantivo `Parceiro`/`Documento`/`EmRisco` precisa virar `Partner`/`Document`/`AtRisk`). `ParceiroServiceTest` é a exceção — todos os 16 nomes de método estão em português (`criaERecuperaParceiro`, `aceitaCnpjComMascaraEArmazenaSomenteDigitos`, `rejeitaParceiroSemPapelClienteOuFornecedor`, `rejeitaDocumentoDuplicadoNoMesmoTenant`, `rejeitaAtualizacaoDeStatusParaEmRisco`, `atualizaStatusParaBloqueado`, `resumoContaPorStatus`, `resumoContaSomenteOPapelInformado`, `listaComFiltroDeBusca`, `listaComFiltroDeDocumentoParcialIgnorandoMascara`, `listaComFiltroDePapel`, `listaComFiltroDeStatusMultiplo`, `listaComFiltroDeUfMultiplo`, `excluiParceiro`, `atualizaParceiroComSucesso`, `atualizaParceiroMantendoOProprioDocumento`). Estes traduzem para inglês também — mesmo padrão observado em `CompanyRepositoryTest` no sub-projeto Empresa, onde os nomes de teste ficaram em inglês (`savesCompanyForTenant` etc.), e nomes de método de teste não são texto visível ao cliente final.

## 5. Riscos e notas abertas

1. **Reset do banco local necessário** — mesma situação dos sub-projetos anteriores: como a migration `V5` é editada em vez de gerar uma migration de rename, qualquer banco local que já tenha aplicado a versão antiga precisa ser recriado.
2. **Blast radius maior que o Empresa, comparável ao Venda→Sale**: módulo próprio com 20 arquivos (controller/service/repository/specifications/exceptions/enums/DTOs/entidade filha) + migration, mais ~19 arquivos de teste/consumidores em 4 módulos backend, mais 5 migrations posteriores com FK literal (`V7`/`V9`/`V11`/`V15`/`V26`), mais o seed `R__seed_dev_test_clientes.sql`, mais 8 arquivos de frontend (1 renomeado + 7 com bridge-patch de literais). O `Parceiro` também é a entidade mais "rica" em campos vista até agora nesta iniciativa (endereço completo + inscrições fiscais).
3. **Lição do Task 6 do sub-projeto Empresa já aplicada nesta spec**: lá, um bug real no script de seed dev (`R__seed_dev_tenant.sql`) só foi descoberto na verificação final porque a auditoria original só varreu arquivos `.java`. Desta vez a varredura de arquivos não-Java (`.sql`) já foi feita durante o design — encontrou as 5 migrations com FK literal e o seed de 62 linhas, ambos já incluídos no escopo da seção 2. Ainda assim, o plano deve incluir uma verificação final equivalente (grep amplo, não só `.java`) como rede de segurança.
4. **Ordem dos próximos sub-projetos** ainda não decidida — será definida quando este for concluído (candidatos restantes: Produto+Categoria+CorEstampa+TabelaPreco, Município, Pedido).
