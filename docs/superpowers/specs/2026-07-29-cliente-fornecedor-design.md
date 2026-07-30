# Cadastro de Cliente/Fornecedor — Spec de Design

**Data**: 2026-07-29
**PRD relacionado**: `prd/PRD-13-cadastro-comercial.md` (subconjunto — só Cliente/Fornecedor, conforme `prd/ORDEM-EXECUCAO.md` item 2)
**Referência visual**: `layout/PediMais Prototipo.html` — componentes `ClientesA` (listagem), `ClientesCadastro` (formulário) e `ClientesB` (perfil com abas)

## 1. Contexto e decisão

Este é o primeiro domínio de negócio real construído sobre a fundação de login/multitenant (PRD-14) e o novo layout PediMais. Pedidos (próximo item da ordem de execução) não funciona sem cliente para selecionar — por isso este slice entra antes.

O PRD-13 descreve um cadastro unificado de parceiro servindo 5 papéis (Cliente, Fornecedor, Transportadora, Contador, Prestador de Serviço). Este slice cobre apenas os papéis **Cliente** e **Fornecedor** — os outros três pertencem a domínios ainda não construídos (Expedição, Contábil/Patrimonial, Produção/PCP).

**Correção de comportamento em relação ao legado**: o PRD-13 (§8, risco 1) sinaliza que o sistema legado permite só um papel por registro — a mesma empresa cliente-e-fornecedora precisa de dois cadastros duplicados com o mesmo documento. O sistema novo corrige isso: um parceiro tem uma **lista de papéis** no mesmo registro, e o documento (CPF/CNPJ) é único **globalmente por tenant**, não por papel.

## 2. Escopo desta rodada

Três telas, seguindo rigorosamente os campos das três telas de referência do protótipo (`ClientesA`, `ClientesCadastro`, `ClientesB`):

1. **Listagem** (`ClientesA`) — busca, filtros, resumo, tabela paginada, ações.
2. **Formulário de cadastro** (`ClientesCadastro`) — criar e editar, com todas as seções do protótipo.
3. **Perfil com abas** (`ClientesB`) — visão detalhada de um cliente, com edição rápida.

Ativa o item "Clientes" do menu lateral (deixa de ser inerte).

### Fora de escopo (campos/telas do protótipo que dependem de domínio não construído)

- Tabela de Preço, Limite de Crédito, Forma de Pagamento, Vendedor Responsável (aparecem na aba "Dados" do perfil, mas **não são persistidos** — campos desabilitados, sem dado real).
- Aba "Pedidos" e aba "Financeiro" do perfil — estado vazio ("nenhum pedido/lançamento ainda"), sem chamada de API.
- Múltiplos endereços por cliente (a aba "Endereços" do perfil mostra o único endereço já cadastrado no formulário, não uma lista).
- Papel Transportadora funcional (checkbox aparece na tela, desabilitado — ver seção 4).
- Status "Em Risco" com cálculo automático (existe no modelo/badge, mas sem caminho de escrita nesta fatia — ver seção 5).
- Vínculo com rede comercial, classificação contábil, condições comerciais (tabela de preço/desconto), vínculo usuário-cliente — nenhum desses aparece nas telas de referência usadas aqui; ficam fora até seus domínios existirem.

## 3. Modelo de dados

### `Parceiro` (tabela principal — RLS por tenant, mesmo padrão de `empresa`/`usuario`)

| Campo | Tipo/domínio | Observação |
|---|---|---|
| `id` | UUID | PK |
| `tenant_id` | UUID | RLS |
| `tipo_pessoa` | FISICA \| JURIDICA | |
| `documento` | varchar | CPF ou CNPJ, único por tenant (global entre papéis) |
| `nome_fantasia` | varchar | obrigatório |
| `razao_social` | varchar, nullable | opcional mesmo para PJ — o protótipo não marca como obrigatório |
| `papeis` | conjunto de CLIENTE \| FORNECEDOR \| TRANSPORTADORA | só CLIENTE/FORNECEDOR alcançáveis pela UI; validação exige pelo menos um dos dois |
| `status` | ATIVO \| EM_RISCO \| BLOQUEADO | default ATIVO; só ATIVO/BLOQUEADO setáveis pela UI |
| `emails_cobranca` | texto | múltiplos e-mails separados por vírgula (mesma UX do campo no protótipo) |
| `whatsapp` | varchar | |
| `indicador_ie` | NAO_CONTRIBUINTE \| CONTRIBUINTE \| CONTRIBUINTE_ISENTO | |
| `inscricao_estadual` | varchar, nullable | |
| `inscricao_municipal` | varchar, nullable | |
| `inscricao_suframa` | varchar, nullable | |
| `cep`, `logradouro`, `numero`, `bairro`, `complemento`, `uf`, `cidade` | varchar | endereço único |
| `observacao` | texto, nullable | |
| `criado_em` | timestamp | automático |

### `ParceiroContato` (tabela filha — "Outros Contatos", lista repetível)

| Campo | Tipo | Observação |
|---|---|---|
| `id` | UUID | PK |
| `parceiro_id` | UUID | FK → `Parceiro` |
| `nome` | varchar | |
| `email` | varchar, nullable | |
| `telefone_comercial` | varchar, nullable | |
| `telefone_celular` | varchar, nullable | |
| `cargo` | varchar, nullable | |

## 4. Papel Transportadora — tratamento visual

O formulário de cadastro mostra três checkboxes de papel (Cliente, Fornecedor, Transportadora), mas Transportadora não tem nenhum fluxo de negócio nesta fatia (pertence a Expedição/Logística). Tratamento: os três checkboxes aparecem, mas "Transportadora" fica com `cursor: not-allowed` e não pode ser marcado — mesmo padrão já usado nos itens inertes do menu lateral (`AppSidebar`) e nos botões da Dashboard. O enum do backend inclui `TRANSPORTADORA` para não exigir migração futura, mas nenhum caminho da API o persiste hoje.

## 5. Status "Em Risco" — tratamento

A listagem mostra três badges de status (Ativo/Em Risco/Bloqueado), mas o menu de ações do protótipo só oferece Ativar/Bloquear — "Em Risco" não tem gatilho manual, é presumivelmente calculado a partir de pendência financeira (domínio Financeiro, não construído). O enum do backend modela os três valores para bater com o visual da listagem, mas nenhum endpoint desta fatia escreve `EM_RISCO` — fica pronto para o domínio Financeiro popular no futuro.

## 6. Telas

### `ClientesListView.vue` (rota `/clientes`)
- Busca por nome; filtros: Status, Tipo de Documento (CPF/CNPJ), UF, Cidade.
- Cards de resumo: Total, Ativos, Em Risco, Bloqueados.
- Tabela: Nome/Razão Social, Cidade, Telefone, Status (badge), Ações (menu: Editar, Ativar/Bloquear, Excluir).
- Paginação real via backend (Spring Data `Pageable`).
- "+ Novo Cliente" → `ClienteFormView.vue` (modo criar).
- Clique no nome → `ClienteDetailView.vue`.

### `ClienteFormView.vue` (rotas `/clientes/novo` e `/clientes/:id/editar`)
Seções, na ordem do protótipo:
1. Dados Gerais: Tipo de Pessoa, CNPJ/CPF, Nome Fantasia, Razão Social, Tipo de Papel (checkboxes).
2. Contato para Cobrança e Faturamento: E-mail(s), WhatsApp.
3. Informações Fiscais: Indicador de IE, Inscrição Estadual, Inscrição Municipal, Inscrição SUFRAMA.
4. Endereço: CEP (com botão "Buscar dados" → ViaCEP), Logradouro, Número, UF, Cidade, Bairro, Complemento.
5. Outros Contatos: lista repetível (Nome, E-mail, Tel. Comercial, Tel. Celular, Cargo), adicionar/remover linha.
6. Observação: texto livre.

Um único componente serve criar e editar (carrega dados existentes quando `:id` está presente).

**Busca de CEP**: chamada direto do frontend para a API pública ViaCEP (`https://viacep.com.br/ws/{cep}/json/`) — sem chave, CORS liberado, sem necessidade de proxy no backend. Falha na consulta (CEP inválido ou API fora do ar) não bloqueia o cadastro: mostra uma mensagem discreta e o usuário preenche manualmente.

### `ClienteDetailView.vue` (rota `/clientes/:id`)
- Rail esquerdo: busca + lista compacta de clientes (clicar troca o cliente selecionado).
- Painel direito com abas:
  - **Dados**: Razão Social, CNPJ, Nome Fantasia, Inscrição Estadual (editáveis, salvam via `PUT /api/parceiros/{id}`) + Tabela de Preço, Limite de Crédito, Forma de Pagamento, Vendedor Responsável (campos desabilitados, sem persistência — ver seção 2).
  - **Endereços**: mostra o endereço único já cadastrado (somente leitura nesta aba; edição completa via `ClienteFormView.vue`).
  - **Contatos**: lista de "Outros Contatos".
  - **Pedidos**: estado vazio.
  - **Financeiro**: estado vazio.
- Botão "✏️ Editar" abre `ClienteFormView.vue` (modo editar). Botão "+ Pedido" inerte.

## 7. API (backend)

Mesmo padrão de `empresa`/`usuario`: entity + repository + service + controller + DTO, RLS via `tenant_id`.

- `GET /api/parceiros` — lista paginada; query params: `busca` (nome), `status`, `tipoDocumento`, `uf`, `cidade`.
- `GET /api/parceiros/{id}` — detalhe.
- `POST /api/parceiros` — criar.
- `PUT /api/parceiros/{id}` — atualizar.
- `PATCH /api/parceiros/{id}/status` — ativar/bloquear.
- `DELETE /api/parceiros/{id}` — excluir.

Validações: `nome_fantasia` obrigatório; `documento` obrigatório e único por tenant (constraint UNIQUE + erro 409 tratado no formulário); `papeis` deve conter ao menos CLIENTE ou FORNECEDOR.

## 8. Testes

- Backend: service (validação de unicidade de documento, papéis mínimo, RLS por tenant), seguindo o padrão de testes já existente de `usuario`/`empresa`.
- Frontend: formulário (campos obrigatórios, papéis mínimo, contatos repetíveis, integração ViaCEP com falha tratada), listagem (filtros, paginação, ações), perfil (troca de cliente selecionado, abas vazias renderizam estado vazio sem erro).

## 9. Riscos e notas abertas

1. `emails_cobranca` como texto único (comma-separated) em vez de tabela normalizada — decisão deliberada de simplicidade (YAGNI); se um dia precisar de metadados por e-mail, migra para tabela própria.
2. Papel Transportadora e status "Em Risco" ficam modelados no schema mas sem caminho de escrita — avaliar se isso é aceitável a longo prazo ou se deveria ser adicionado só quando os domínios dependentes existirem (decisão consciente desta rodada: manter no schema agora evita migração futura).
3. A aba "Endereços" do perfil mostra hoje o mesmo endereço único do formulário — se o negócio precisar de múltiplos endereços (entrega vs. cobrança, por exemplo) é uma extensão futura, não coberta aqui.
