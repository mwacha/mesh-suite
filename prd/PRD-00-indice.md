# Índice Geral de PRDs — sisconf

Este documento é o índice dos PRDs gerados por engenharia reversa do sisconf (ERP para indústria de confecção/vestuário — JSF 1.2 + RichFaces + Hibernate 5 + PostgreSQL, monolito único). Cada PRD listado abaixo é **autocontido**: pode ser usado isoladamente como insumo de execução, sem depender da leitura dos demais.

> **Todo este conjunto de PRDs (00 a 14) é insumo para uma reescrita greenfield do sisconf, não para manutenção do código legado.** `PRD-01` a `PRD-13` documentam o comportamento do sistema **atual** por engenharia reversa — funcionam como **especificação de regras de negócio a preservar** (o quê o sistema precisa fazer, com evidência rastreável no código-fonte legado), não como instrução de onde/como alterar o código Java existente. `PRD-14` é a primeira peça do sistema **novo** (login + fundação multitenant) e não pressupõe nenhuma stack específica. Cada um dos 13 domínios de negócio, ao ser reimplementado, deve nascer **sobre** a fundação do `PRD-14`, não como patch do código legado. Onde os PRDs 01–13 dizem "preservar"/"continua funcionando"/"sem regressão", leia como "o sistema novo deve replicar este comportamento" — essa nota está registrada em cada um deles individualmente também.

## Stack e observações gerais

- **Sistema legado** (o que os PRD-01 a PRD-13 documentam): backend Java monolítico (WAR único), pacote raiz `br.com.sisconf`, sem módulos Maven; JSF 1.2 + Facelets + RichFaces 3.3 + PrimeFaces; Hibernate 5 + PostgreSQL, pool C3P0; camadas `facade` (JSF managed beans) → `business` (`*Bean`) → `interfaces` (contratos DAO) → `modelo/dao/Hibernate/imp`. **Nenhuma dessas escolhas de stack é requisito para o sistema novo** — são contexto de onde as regras de negócio foram extraídas.
- **Sistema novo** (o que o `PRD-14` funda): stack em aberto, a ser decidida pelo time de implementação; multitenant desde o primeiro dia (ver `PRD-14`).
- **Achado transversal de segurança** (sistema legado): credenciais de banco de dados e SMTP em texto puro, commitadas em `src/resources/hibernate.cfg.xml` e `src/resources/application.properties`. O sistema novo não deve reproduzir esse padrão — segredos via variável de ambiente/secret manager desde o primeiro deploy (já registrado como requisito em `PRD-14`).
- **Achado transversal de qualidade** (sistema legado): diversos bugs de "copy-paste" foram encontrados (mensagens de sucesso com severidade de erro, IDs/outcomes de outra tela reaproveitados, telas com nome de um domínio implementando lógica de outro) — cada ocorrência está documentada na seção de débitos técnicos do respectivo PRD como **comportamento a não replicar**, não a corrigir no código legado.

## Lista de PRDs gerados

| # | Arquivo | Domínio | Tamanho aprox. |
|---|---|---|---|
| 1 | `PRD-01-rh-departamento-pessoal.md` | RH / Departamento Pessoal | ~17 classes |
| 2 | `PRD-02-expedicao-logistica.md` | Expedição / Logística | ~35 classes |
| 3 | `PRD-03-cobranca-bancaria.md` | Cobrança Bancária | ~37 classes |
| 4 | `PRD-04-contabil-patrimonial.md` | Contábil / Patrimonial | ~48 classes |
| 5 | `PRD-05-estoque.md` | Estoque | ~49 classes |
| 6 | `PRD-06-administracao-sistema.md` | Administração do Sistema | ~25 classes |
| 7 | `PRD-07-compras.md` | Compras | ~72 classes |
| 8 | `PRD-08-financeiro.md` | Financeiro | ~93 classes |
| 9 | `PRD-09-cadastro-seguranca.md` | Cadastro & Segurança | ~97 classes |
| 10 | `PRD-10-producao-pcp.md` | Produção / PCP | ~150+ classes (inclui Mesada/Facção e Mescla) |
| 11 | `PRD-11-fiscal-tributario.md` | Fiscal / Tributário | ~132 classes |
| 12 | `PRD-12-vendas.md` | Vendas | ~133 classes |
| 13 | `PRD-13-cadastro-comercial.md` | Cadastro Comercial | ~196 classes (inclui Modelo/ficha técnica) |
| 14 | `PRD-14-login-multitenant.md` | Login Multitenant (Fundação do Sistema Novo) | **construção greenfield** — não é engenharia reversa |

**`PRD-14` é diferente dos demais**: não documenta comportamento existente nem propõe estender o código Java/JSF/Hibernate do sisconf legado — é a especificação da **primeira peça (tela + backend) de um sistema novo**, em stack a definir pelo time de implementação. Ele usa os PRDs 01–13 apenas como fonte de regras de negócio a preservar (o que cada domínio precisa fazer) e trata a instalação atual do sisconf como dado a migrar para dentro do primeiro tenant, não como base de código a herdar. Deve ser lido como pré-requisito de qualquer domínio do sistema novo, já que estabelece o modelo de identidade/tenant que os demais consomem.

## Correções de escopo feitas durante a investigação

O levantamento inicial de domínios (baseado em nomes de pacotes/telas) continha classificações que a leitura do código corrigiu:

- **`Mesada`/`Mescla`** — inicialmente cogitadas como RH/Administração — pertencem a **Produção/PCP** (facção/terceirização de costura e reagrupamento de lotes de corte).
- **`CotacaoFrete`** — inicialmente cogitada como Compras — pertence a **Expedição/Logística** (cotação de frete de saída, vinculada a Pedido de venda, não a Compra).
- **`Modelo`/ficha técnica** — inicialmente cogitada como Administração do Sistema — pertence a **Cadastro Comercial** (BOM/consumo de matéria-prima do produto).
- **`AdicionaExpedicaoFacade`, `EntradaExpedicaoFacade`, `MoviEstoqExpedFacade`** — nomeadas como Expedição, mas implementam, respectivamente, ajuste manual de Estoque, lógica de Mescla (Produção) e movimentação genérica de Estoque. Documentadas em `PRD-02` como débito técnico, não movidas, pois a decisão de renomear/realocar cabe ao time.

## Ordem sugerida de execução (por dependência, não por ordem de geração)

Os PRDs foram **gerados** em ordem crescente de tamanho (do menor para o maior, a pedido do usuário). Para fins de **execução/implementação**, a ordem abaixo segue as dependências reais entre domínios — comece pelos fundamentos antes dos domínios que os consomem:

1. **Cadastro & Segurança** — base de tudo (Usuario, Empresa, autenticação).
2. **Cadastro Comercial** — depende de (1); base de Produto e Cliente/Fornecedor usada por quase todos os demais.
3. **Contábil / Patrimonial** — depende de (1); Plano de Contas é pré-requisito de Financeiro.
4. **Fiscal / Tributário** — depende de (1) e (2); regra de tributação usada por Compras/Vendas.
5. **Financeiro** — depende de (1), (2), (3); contas a pagar/receber usadas por Compras/Vendas/Cobrança Bancária.
6. **Estoque** — depende de (2); ledger central de saldo/movimentação de produto.
7. **Compras** — depende de (2), (4), (5), (6).
8. **Vendas** — depende de (2), (4), (5), (6).
9. **Produção / PCP** — depende de (2), (6), (8) (parte do pedido de venda).
10. **Expedição / Logística** — depende de (2), (4), (6), (7), (8).
11. **Cobrança Bancária** — depende de (5).
12. **RH / Departamento Pessoal** — depende apenas de (1); pode ser feito a qualquer momento, praticamente isolado.
13. **Administração do Sistema** — transversal (Backup, Mensagens); depende apenas de (1); pode ser feito a qualquer momento.

**Nota**: (12) e (13) são de baixo acoplamento e podem ser executados em paralelo com qualquer outro item da lista, inclusive antes de (1)-(11), caso a prioridade de negócio assim exigir — a ordem acima reflete dependência técnica, não urgência de negócio.

**Sobre `PRD-14` (Login Multitenant)**: é a fundação de um **sistema novo**, não uma alteração no sisconf legado — os domínios (1)-(13) acima descrevem o sisconf **atual** (uso como especificação de regra de negócio a preservar, e como manutenção corretiva do legado enquanto ele estiver em produção). Se a decisão for reconstruir o sistema do zero, `PRD-14` é o ponto de partida (primeira tela + backend), e cada um dos domínios (1)-(13) passa a ser reimplementado **sobre** essa fundação, na mesma ordem de dependência já listada, mas como construção nova, não como migração de código.

## Diagrama textual de dependências

```
Cadastro & Segurança (Usuario, Empresa, Login)
  │
  ├──> Cadastro Comercial (Cliente_Fornecedor, Produto)
  │      │
  │      ├──> Contábil / Patrimonial (Plano de Contas, Ativo Imobilizado)
  │      │      │
  │      │      └──> Financeiro (Contas a Pagar/Receber, Caixa)
  │      │             │
  │      │             └──> Cobrança Bancária (CNAB, Boleto)
  │      │
  │      ├──> Fiscal / Tributário (CST/CFOP, NF-e, SPED)
  │      │
  │      ├──> Estoque (saldo e movimentação de Produto)
  │      │      │
  │      │      ├──> Compras (Ordem de Compra, NF de Entrada)
  │      │      │
  │      │      ├──> Vendas (Pedido, NF de Saída)
  │      │      │      │
  │      │      │      └──> Produção / PCP (Corte, Facção/Mesada, Mescla)
  │      │      │
  │      │      └──> Expedição / Logística (Volumes, CT-e, Cotação de Frete)
  │      │             (depende também de Vendas e Compras)
  │      │
  │      └──> (usado também por Produção/PCP para Facção)
  │
  ├──> RH / Departamento Pessoal (isolado, só usa Usuario)
  │
  └──> Administração do Sistema (Backup, Mensagens — transversal)
```

Legado de acoplamento cruzado a observar (documentado nos PRDs correspondentes):
- `Compra`/`Compra_Item` e `Venda`/`VendaItem` **compartilham a mesma superclasse** `Entrada_Saida`/`Entrada_Saida_Item` — mudança estrutural em uma afeta a outra.
- Telas do domínio Expedição (`EntradaExpedicaoFacade`) implementam lógica de Produção/PCP (Mescla) — acoplamento de código, não apenas de dados.
