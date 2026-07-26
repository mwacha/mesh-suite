# PRD — Login Multitenant (Fundação do Sistema Novo)

> **Natureza deste documento**: diferente dos demais PRDs (`PRD-01` a `PRD-13`), que documentam comportamento **existente** do sisconf legado por engenharia reversa, este é um PRD de **construção greenfield**. Ele é a primeira peça (tela + backend) de um sistema novo, em stack a definir pelo time de implementação — não pressupõe reaproveitamento do código Java/JSF/Hibernate do sisconf atual.
>
> O sisconf legado entra aqui de duas formas, e só dessas duas:
> 1. **Como especificação de regra de negócio a preservar** — os PRDs 01 a 13 continuam sendo a fonte da verdade de "o que o sistema precisa fazer" dentro de cada domínio, independentemente da stack em que forem reimplementados.
> 2. **Como fonte de dados a migrar** — a instalação atual do sisconf vira o primeiro tenant do sistema novo (ver seção 4, fluxo de migração).
>
> Nenhuma classe, arquivo ou padrão de código do sisconf legado deve ser assumido como reaproveitável pela implementação deste PRD.

## 0. Decisões já tomadas (não reabrir sem nova rodada de alinhamento)

1. **Estratégia de isolamento entre tenants: coluna `tenant_id` compartilhada** — um único banco de dados, uma tabela por entidade para todos os tenants, toda linha carrega `tenant_id`. **Foi sinalizado explicitamente, e a escolha foi mantida**, que esta é a opção de isolamento mais fraca entre as avaliadas (banco-por-tenant, schema-por-tenant, coluna compartilhada) — um único ponto de consulta sem filtro de tenant vaza dados entre empresas-cliente diferentes. Por isso, este PRD trata o **mecanismo de enforcement do isolamento como requisito de primeira classe**, não como detalhe de implementação (seção 5 e 7).
2. **Identificação do tenant no login: campo "código da empresa"** digitado pelo usuário junto com e-mail e senha na mesma tela — não subdomínio, não seletor de workspace pós-autenticação.
3. **Escopo deste PRD: login + fundação multitenant.** Ele entrega autenticação, o modelo de dados de tenant/usuário/empresa, e o mecanismo que torna o isolamento **automaticamente aplicado** a qualquer funcionalidade construída depois. Construir as telas/APIs de negócio dos 13 domínios (Financeiro, Estoque, Vendas etc.) sobre essa fundação é trabalho subsequente, fora deste documento — mas cada um deles, ao ser implementado, **deve** herdar o isolamento por tenant daqui, não reinventá-lo.
4. **Provisionamento de tenant: só por operação interna** (admin) — sem autoatendimento/signup público neste PRD.

## 1. Contexto e objetivo

O sisconf legado é uma aplicação single-tenant por instalação (um deploy = um banco = uma empresa operadora) — o comportamento de login/autenticação de hoje está descrito em `PRD-09-cadastro-seguranca.md`. Dele, dois fatos são relevantes como **ponto de partida de regra de negócio** (não como arquitetura a herdar):

- Login é feito por e-mail + senha.
- Existe o conceito de "empresa operadora" com dados fiscais completos (matriz + filiais), e um conjunto de perfis de usuário (`TipoUsuario`: ADMINISTRATIVO, REPRESENTANTE, PRODUCAO, TERCERIZADO, ADMINISTRADOR — ver `PRD-01` e `PRD-09`) e dois mecanismos de controle de acesso (por item de menu e por tipo de permissão, cuja relação nunca foi clara mesmo no sistema legado — ver `PRD-09`, débito técnico 5). **Este PRD não herda essa ambiguidade**: o sistema novo deve nascer com um único mecanismo de controle de acesso (ver seção 5, regra 6).

Objetivo deste PRD: ser a fundação de identidade e multi-inquilino (multitenancy) do sistema novo — autenticar usuários, resolver a qual empresa-cliente (tenant) cada um pertence, e garantir que **nenhuma funcionalidade construída depois desta fundação precise reimplementar isolamento entre tenants por conta própria**.

## 2. Escopo

### Incluído neste PRD
- **Modelo de dados de identidade**: `Tenant`, `Empresa` (dados fiscais/cadastrais do operador dentro de um tenant — matriz + filiais), `Usuario` (com perfil/papel), vínculos entre eles.
- **Autenticação**: login com código do tenant + e-mail + senha; hash de senha moderno desde o primeiro dia (bcrypt/Argon2 — não há MD5 a migrar aqui, é sistema novo).
- **Sessão/token de autenticação**: mecanismo de continuidade de sessão após login (ver seção 4 — decisão de stateful vs. stateless é aberta e deve ser tomada pelo time de implementação com base na stack escolhida; este PRD define os *dados* que a sessão/token precisa carregar, não a tecnologia).
- **Recuperação de senha (esqueci minha senha)**: incluída por ser parte inseparável de um fluxo de autenticação novo em produção — **assunção**: fluxo por e-mail com link/token de expiração curta. Se não for necessário no primeiro lançamento, remover explicitamente do escopo antes de implementar.
- **Mecanismo de isolamento por tenant aplicado no nível de dados**, não apenas na tela de login — de forma que todo domínio implementado depois automaticamente respeite as fronteiras de tenant (ver seção 5, regra 5, e seção 7).
- **Provisionamento de tenant** por operação interna: criação de tenant + primeira empresa + primeiro usuário administrador.
- **Controle de acesso por papel** (`TipoUsuario`/role), como único mecanismo de autorização do sistema novo (substituindo os dois mecanismos coexistentes do legado).

### Fora de escopo (explicitamente)
- Implementação das telas/APIs de negócio dos 13 domínios já documentados — este PRD entrega a fundação sobre a qual eles serão construídos, não os domínios em si.
- Autoatendimento/signup público de novo tenant.
- Seleção de tenant pós-login (um usuário pertence a um único tenant neste desenho).
- Isolamento por schema ou banco de dados separado por tenant (rejeitado na decisão da seção 0).
- Cobrança/planos/limites de uso por tenant (billing), SSO/login social, autenticação multifator — não mencionados pelo usuário, não presumidos. Se algum for necessário, tratar como PRD à parte.
- Escolha de linguagem/framework/hospedagem do backend — decisão de arquitetura do time de implementação, não deste PRD de produto.

## 3. Modelo de dados proposto

Descrito em termos lógicos (entidade/campo/relacionamento), não como schema de uma tecnologia específica — a implementação escolhe o ORM/schema concreto.

### `Tenant`
| Campo | Tipo | Observação |
|---|---|---|
| id | identificador único | |
| codigo | texto curto, único globalmente, imutável após criação | o "código da empresa" digitado no login |
| nome | texto | nome de exibição do tenant |
| ativo | booleano, default verdadeiro | suspende acesso de todos os usuários do tenant sem apagar dados |
| criado_em | data/hora | |

### `Empresa` (dentro de um Tenant)
Representa a(s) pessoa(s) jurídica(s) operada(s) por um tenant — preserva o conceito já validado no legado de matriz + filiais (`PRD-09`). Campos mínimos: razão social, CNPJ, dados fiscais básicos (os detalhes fiscais completos — regime tributário, inscrições, etc. — pertencem ao domínio Fiscal/Cadastro & Segurança quando esse módulo for implementado; aqui só o necessário para a fundação: identificar univocamente a empresa dentro do tenant). Relacionamento: `Tenant` 1&nbsp;—&nbsp;N `Empresa`.

### `Usuario`
Campos mínimos para a fundação: nome, e-mail, senha (hash), papel/perfil (role), ativo, tenant ao qual pertence, data de criação, último acesso. Relacionamento: `Tenant` 1&nbsp;—&nbsp;N `Usuario`.

**Unicidade de e-mail é por tenant, não global** — dois tenants diferentes podem ter usuários com o mesmo e-mail (ex. um contador terceirizado que atende duas empresas-cliente diferentes, cada uma com sua própria conta). Constraint: único por (`tenant_id`, `email`).

### Papel/Perfil de usuário
Reaproveitar, como ponto de partida de negócio, os perfis já validados no legado (`TipoUsuario`, documentado em `PRD-01`/`PRD-09`): administrativo, representante, produção, terceirizado, administrador — ajustável pelo time de produto ao desenhar o controle de acesso do sistema novo, mas não presumir novos perfis sem necessidade demonstrada.

### `tenant_id` como campo universal
Toda entidade de negócio que vier a ser criada pelos PRDs 01–13 no sistema novo **nasce já com `tenant_id`** — diferente do legado, aqui não há "retrofit": é requisito de fundação que qualquer tabela nova, desde a primeira, inclua e indexe esse campo.

## 4. Fluxos funcionais propostos

### Fluxo principal — Login
1. Usuário acessa a tela de login e informa **código da empresa** (tenant), e-mail e senha.
2. Backend resolve o `Tenant` pelo código informado. Se não existir ou estiver inativo, segue para o mesmo tratamento de erro do passo 4 (não revelar se o tenant existe).
3. Dentro do tenant resolvido, busca o `Usuario` por (`tenant_id`, `email`) e valida a senha (hash moderno, comparação em tempo constante).
4. Falha em qualquer um dos três pontos (tenant não existe/inativo, e-mail não encontrado, senha incorreta) retorna **a mesma mensagem de erro genérica**, sem diferenciar o motivo — mitigação contra enumeração de tenants/usuários.
5. Sucesso: sistema estabelece o contexto de sessão/autenticação (ver decisão em aberto abaixo) contendo, no mínimo: `usuario.id`, `tenant.id`, `empresa.id` (matriz, como padrão), papel do usuário. Esse contexto é o que a fundação expõe para todo domínio construído depois usar como base de escopo de dados.
6. Login registra data/hora de último acesso.

**Decisão em aberto para o time de implementação**: sessão de servidor (cookie + estado no backend) vs. token stateless (JWT) — este PRD não decide por não ser uma escolha de produto, mas exige que, qualquer que seja a escolha, o `tenant_id` esteja **sempre** disponível e não seja auto-declarável pelo cliente (isto é, o token/sessão deve ser assinado/validado pelo servidor a partir do login, nunca um campo que o front-end envia livremente em cada requisição).

### Fluxo — Esqueci minha senha (assunção de escopo, ver seção 2)
1. Usuário informa código do tenant + e-mail.
2. Sistema gera token de reset de validade curta (ex. 1 hora) e envia por e-mail — resposta ao usuário é sempre "se o e-mail existir, um link foi enviado", **mesma mensagem** exista ou não a conta, para não vazar quais e-mails têm conta.
3. Link contém o token; ao acessá-lo, usuário define nova senha; token é invalidado após uso ou expiração.

### Fluxo — Provisionar novo Tenant (operação interna)
1. Operador interno (equipe do produto sisconf, não usuário de um tenant) cria o tenant: código único, nome.
2. Cria a primeira `Empresa` (matriz) e o primeiro `Usuario` com papel administrador daquele tenant.
3. Tenant fica ativo e o primeiro usuário pode logar imediatamente e convidar/criar os demais usuários do seu próprio tenant (fluxo de gestão de usuários dentro do tenant é do domínio Cadastro & Segurança, `PRD-09`, ao ser reimplementado — aqui só garantimos que o primeiro usuário existe).

### Fluxo — Migração do sisconf legado como primeiro tenant
Diferente de um "script de ALTER TABLE" (que faria sentido numa extensão in-place do sistema atual), aqui é um projeto de **migração/ETL** de dados, já que o sistema novo tem schema próprio:
1. Criar o tenant que representa a operação atual do sisconf legado.
2. Migrar os dados relevantes de `Usuario`/`Empresa` do banco legado para as novas entidades, associando tudo ao tenant criado no passo 1.
3. A migração dos dados de negócio dos 13 domínios (Financeiro, Estoque, Vendas etc.) acontece **domínio a domínio, à medida que cada um for reimplementado no sistema novo** — não é um evento único; este PRD só cobre a migração da parte de identidade (usuários/empresas) necessária para login funcionar.

## 5. Regras de negócio

1. **Um usuário pertence a exatamente um tenant.**
2. **E-mail é único por tenant, não globalmente.**
3. **Código do tenant é único globalmente e imutável após criação.**
4. **Mensagens de erro de autenticação não diferenciam a causa** (tenant inexistente/inativo, usuário inexistente, senha incorreta) — sempre a mesma mensagem genérica.
5. **O isolamento por tenant deve ser garantido no nível de acesso a dados, de forma automática, não por convenção de código** — ou seja, a fundação deve fornecer um mecanismo que torna **impossível ou muito difícil** um desenvolvedor esquecer de filtrar por tenant ao construir um novo domínio, em vez de depender de disciplina manual em cada consulta (ver seção 7 para a recomendação técnica concreta).
6. **Controle de acesso por papel único** (role-based, um usuário tem um papel dentro do seu tenant) — decisão de simplificação em relação ao legado, que tinha dois mecanismos coexistentes sem relação clara documentada (`PRD-09`). Se o negócio precisar de permissões mais finas que um papel fixo permite (o que o legado tentava resolver com `Usuario_Permissao`), isso deve ser desenhado como extensão explícita do modelo de papéis, não reintroduzindo dois sistemas paralelos.
7. **Hash de senha nunca em algoritmo fraco** (nada de MD5/SHA1 sem salt) — bcrypt, Argon2 ou PBKDF2 desde o primeiro usuário criado.
8. **Tenant inativo bloqueia login de todos os seus usuários**, mesmo com credenciais corretas.

## 6. Integrações e dependências

- **É o alicerce de todo o resto do sistema novo** — qualquer domínio (`PRD-01` a `PRD-13`) implementado depois consome o contexto de tenant/usuário estabelecido aqui; nenhum deve criar seu próprio mecanismo paralelo de identificar "de quem são estes dados".
- **Depende de `PRD-09-cadastro-seguranca.md`** apenas como fonte de regras de negócio de usuário/empresa/papéis a preservar — não como código a estender.
- **Envio de e-mail** (fluxo de recuperação de senha) — dependência de infraestrutura a definir pelo time de implementação (serviço transacional de e-mail); o sisconf legado tinha SMTP configurado com credenciais em texto puro (`PRD-06`, `PRD-01`) — **não reaproveitar essa configuração**, o sistema novo deve nascer com segredo gerenciado corretamente (variável de ambiente/secret manager), não commitado em repositório.

## 7. Requisitos não-funcionais relevantes

- **Recomendação forte para o mecanismo de isolamento (regra de negócio 5)**: dado que a decisão de arquitetura (seção 0) foi por `tenant_id` compartilhado — a opção mais fraca — recomenda-se fortemente usar **Row-Level Security (RLS) do PostgreSQL** como camada de enforcement no próprio banco, independente de qual linguagem/framework/ORM for escolhido para o backend. Com RLS, uma política do banco garante que nenhuma conexão consegue ler/escrever linhas de um `tenant_id` diferente do configurado na sessão de banco corrente — isso protege inclusive contra bugs de aplicação (uma query sem `WHERE tenant_id = ...` simplesmente não retorna nada de outro tenant, em vez de vazar dados). Isso é preferível a depender apenas de um filtro no nível do ORM (equivalente ao "Hibernate Filter" do mundo Java), porque RLS funciona mesmo para SQL manual/relatórios/ferramentas administrativas que acessem o banco diretamente — um ponto onde o sisconf legado já demonstrou ter esse tipo de acesso direto via SQL nativo (`PRD-05-estoque.md`, `ProdutoDAO.atualizaEstoqueProduto`).
- **Testes automatizados de isolamento são requisito, não nice-to-have**: antes de qualquer domínio de negócio ser construído sobre esta fundação, deve existir suíte de teste que cria dois tenants e comprova que nenhuma operação de um retorna ou altera dados do outro.
- **Índice em `tenant_id`** obrigatório em toda tabela, desde a criação (evita degradação de performance à medida que a base cresce com múltiplos tenants).
- **Segredo de assinatura de sessão/token e credenciais de banco/e-mail nunca em texto puro no repositório** — lição direta do sisconf legado (`PRD-06`, `PRD-09`), que teve exatamente esse problema em múltiplos arquivos.
- Autenticação e emissão de sessão devem ter proteção básica contra força bruta (rate limiting por IP/usuário) — não presumido como aceite obrigatório do MVP, mas deve ser decisão explícita se ficar de fora do primeiro lançamento.

## 8. Riscos conhecidos

1. **Isolamento fraco por decisão explícita de produto** (seção 0, item 1): a escolha de `tenant_id` compartilhado significa que qualquer consulta futura sem filtro de tenant é um vazamento de dados entre empresas-cliente. A recomendação de RLS (seção 7) é a principal mitigação técnica; sem ela, o risco fica inteiramente dependente de disciplina de code review em cada um dos 13 domínios que serão construídos depois.
2. **"Decisão em aberto" de sessão vs. token** (seção 4) pode virar retrabalho se não for decidida cedo — recomenda-se resolver antes de qualquer outro domínio começar a ser implementado sobre esta fundação, já que muda a forma como cada API subsequente valida o usuário autenticado.
3. **Escopo de "esqueci minha senha" foi assumido, não confirmado** (seção 2) — validar com o responsável de produto antes de implementar; é barato remover agora, caro remover depois de já integrado a um provedor de e-mail.
4. **Consolidação dos dois mecanismos de controle de acesso do legado em um só** (regra de negócio 6) é uma simplificação deliberada — se alguma regra de permissão fina hoje resolvida por `Usuario_Permissao` no legado for, na prática, crítica para algum cliente, isso só será descoberto ao migrar o domínio correspondente; não foi possível antecipar isso nesta fundação.

## 9. Critérios de aceite / Definition of Done

- [ ] `Tenant`, `Empresa` e `Usuario` implementados com os relacionamentos e constraints de unicidade descritos na seção 3.
- [ ] Login funcional com código do tenant + e-mail + senha; mensagens de erro não diferenciam a causa da falha.
- [ ] Hash de senha em algoritmo moderno (bcrypt/Argon2/PBKDF2) desde o primeiro usuário criado — sem qualquer caminho de código que grave senha em texto puro ou hash fraco.
- [ ] Mecanismo de isolamento por tenant (recomendado: RLS no PostgreSQL) implementado e coberto por teste automatizado com dois tenants de teste, provando que nenhuma operação de um vaza para o outro.
- [ ] Decisão de sessão vs. token tomada e documentada antes de iniciar a implementação do primeiro domínio de negócio sobre esta fundação.
- [ ] Fluxo de recuperação de senha implementado (ou removido do escopo por decisão explícita registrada).
- [ ] Provisionamento de novo tenant (tenant + primeira empresa + primeiro usuário administrador) funcional via mecanismo interno.
- [ ] Tenant inativo bloqueia login de todos os seus usuários.
- [ ] Tenant representando a operação atual do sisconf legado criado, com usuários/empresas migrados, e login funcionando para eles no sistema novo.
- [ ] Nenhum segredo (senha de banco, chave de assinatura de sessão/token, credencial de e-mail) commitado em repositório — configurado via variável de ambiente/secret manager desde o primeiro deploy.
