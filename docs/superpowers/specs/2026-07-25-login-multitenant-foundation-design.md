# Fundação Multitenant + Login — Spec de Design

**Data**: 2026-07-25
**PRD relacionado**: `prd/PRD-14-login-multitenant.md`
**Ordem de execução**: item 1 de `prd/ORDEM-EXECUCAO.md`

## 1. Escopo desta fatia

Esta é a primeira fatia implementável do sistema novo Mesh Suite. Ela cobre:

- Scaffolding dos dois repositórios de código (`mesh-suite-backend/`, `mesh-suite-frontend/`) como subpastas deste repositório, com `docker-compose.yml` na raiz.
- Modelo de dados de identidade: `Tenant`, `Empresa`, `Usuario`, `PasswordResetToken`.
- Autenticação: login por e-mail + senha, emissão de JWT em cookie `HttpOnly`.
- Isolamento por tenant via Row-Level Security (RLS) no PostgreSQL.
- Recuperação de senha (fluxo completo).
- Rate limiting no login e na recuperação de senha.
- Tela de login e telas de recuperação de senha (Vue 3), replicando `design_handoff/screenshot-login.png`.
- Seed de dados de desenvolvimento/teste (Flyway, perfil `dev`/`test`).
- Testes automatizados de isolamento entre tenants (obrigatório, Definition of Done do PRD-14).

### Fora de escopo desta fatia (specs futuras)

- Provisionamento de tenant via ferramenta administrativa (mecanismo interno real — aqui só existe via seed Flyway de dev/test).
- Migração dos dados legados do sisconf como primeiro tenant real.
- CI/CD.
- Qualquer domínio de negócio (Pedidos, Vendas, Compras, Financeiro, Estoque, Sped Fiscal — itens 2 a 7 de `ORDEM-EXECUCAO.md`).

## 2. Desvios registrados em relação ao PRD-14

O PRD-14 marca a seção 0 como "decisões já tomadas, não reabrir sem nova rodada de alinhamento". Esta rodada de brainstorming *é* essa nova rodada de alinhamento, e resultou nos seguintes desvios deliberados, decididos pelo responsável de produto:

1. **Identificação do tenant no login não usa mais campo "código da empresa".** O login usa apenas e-mail + senha, replicando `screenshot-login.png`. Consequência direta: `email` do `Usuario` passa a ser **único globalmente**, não único por `(tenant_id, email)` como o PRD-14 §3/§5.2 especificava. Trade-off aceito conscientemente: um mesmo e-mail não pode mais atender dois tenants diferentes (ex. um contador terceirizado que atende duas empresas-cliente precisaria de um e-mail distinto para cada uma). O campo `Tenant.codigo` continua existindo no modelo como identificador administrativo (usado em ferramentas internas e na futura tela de provisionamento), só deixou de aparecer no formulário de login.
2. **`Empresa.cnpj` é único globalmente**, não por tenant — o mesmo CNPJ não pode ser cadastrado em dois tenants diferentes.
3. **Recuperação de senha entra nesta fatia** (o PRD-14 §2 trata como assunção a confirmar antes de implementar — está confirmada e incluída).
4. **Sessão/token**: PRD-14 §4 deixa em aberto para o time de implementação. Decisão: JWT stateless.
5. **Mecanismo de isolamento**: PRD-14 §7 recomenda fortemente RLS sem tornar obrigatório. Decisão: adotar RLS.
6. **Rate limiting**: PRD-14 §7 pede decisão explícita se ficar de fora. Decisão: incluir nesta fatia.
7. **Armazenamento do token no frontend**: não coberto pelo PRD-14 (é detalhe de stack). Decisão: cookie `HttpOnly`+`Secure`+`SameSite=Strict`, não `localStorage`.

## 3. Estrutura de repositórios

```
mesh-suite/
├── docker-compose.yml          # postgres + backend + frontend
├── prd/                        # existente, não alterado
├── design_handoff/             # existente, não alterado
├── docs/superpowers/specs/     # este documento
├── tabela-execucao.md          # criado/atualizado por este trabalho
├── mesh-suite-backend/
│   ├── README.md
│   ├── Dockerfile
│   └── src/main/java/...       # Maven, Spring Boot, Java 21
└── mesh-suite-frontend/
    ├── README.md
    ├── Dockerfile
    └── src/                     # Vite, Vue 3, TypeScript
```

Ambos os subprojetos são autocontidos (README próprio, Dockerfile próprio), mas compartilham o histórico git deste repositório. `docker-compose.yml` na raiz sobe os três serviços (postgres, backend, frontend) para desenvolvimento local.

## 4. Modelo de dados

### `Tenant`
| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | |
| codigo | texto curto | único global, imutável após criação; não usado no login, só administrativo |
| nome | texto | |
| ativo | boolean | default `true` |
| criado_em | timestamp | |

### `Empresa`
| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | |
| tenant_id | UUID (FK) | |
| razao_social | texto | |
| cnpj | texto | **único globalmente** (desvio registrado, seção 2) |
| ativo | boolean | default `true` |

Relacionamento `Tenant` 1—N `Empresa` (matriz + filiais). Nesta fatia, só a matriz é criada (via seed).

### `Usuario`
| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | |
| tenant_id | UUID (FK) | |
| nome | texto | |
| email | texto | **único globalmente** (desvio registrado, seção 2) |
| senha_hash | texto | bcrypt |
| papel | enum | `ADMINISTRATIVO`, `REPRESENTANTE`, `PRODUCAO`, `TERCEIRIZADO`, `ADMINISTRADOR` |
| ativo | boolean | default `true` |
| criado_em | timestamp | |
| ultimo_acesso | timestamp | nullable |

### `PasswordResetToken`
| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | |
| usuario_id | UUID (FK) | |
| token_hash | texto | SHA-256 do token bruto enviado por e-mail; nunca armazenado em texto puro |
| expira_em | timestamp | criado_em + 1h |
| usado_em | timestamp | nullable |
| criado_em | timestamp | |

### Migrations Flyway

- `V1__create_tenant.sql`
- `V2__create_empresa.sql` (inclui constraint única global em `cnpj`, índice em `tenant_id`, política RLS)
- `V3__create_usuario.sql` (inclui constraint única global em `email`, índice em `tenant_id`, política RLS)
- `V4__create_password_reset_token.sql` (índice em `usuario_id`)
- `V5__seed_dev_tenant.sql` — gated por perfil Spring `dev`/`test`: 2 tenants de exemplo + 1 `Empresa` + 1 `Usuario` administrador cada, com senha conhecida, para login manual local e fixture-base dos testes de isolamento.

Toda tabela de negócio nasce com `tenant_id` indexado e política RLS — não há retrofit (regra explícita do PRD-14 §3).

## 5. Autenticação

### Fluxo de login
1. `POST /api/auth/login` `{ email, senha, manterConectado }`.
2. Backend busca `Usuario` por `email` (único global). Falha em qualquer um destes pontos retorna a **mesma mensagem genérica** de erro: usuário não encontrado, senha incorreta (bcrypt, comparação em tempo constante), usuário inativo, ou tenant do usuário inativo.
3. Sucesso: gera JWT (claims: `sub`=usuario.id, `tenant_id`, `empresa_id` da matriz, `papel`). Expiração: 8h (padrão) ou 30 dias (`manterConectado=true`). Token é setado como cookie `HttpOnly`+`Secure`+`SameSite=Strict` na resposta — nunca exposto ao JavaScript do frontend.
4. Registra `ultimo_acesso` do usuário.
5. `GET /api/auth/me` — endpoint autenticado via cookie, usado pelo frontend para checar sessão ativa e obter dados do usuário logado (nome, papel), sem nunca expor o token em si.

### Isolamento por tenant (RLS)
- Toda tabela de negócio tem `tenant_id` + política RLS: `USING (tenant_id = current_setting('app.tenant_id')::uuid)`.
- Um `OncePerRequestFilter` valida o JWT (lido do cookie) e extrai `tenant_id`. No início da transação de cada request, uma `TransactionSynchronization` executa `SET LOCAL app.tenant_id = '<uuid>'` na conexão corrente. `SET LOCAL` expira automaticamente ao fim da transação — seguro com pool de conexões reutilizadas.
- Na mesma consulta que seta `app.tenant_id`, o backend também verifica `usuario.ativo` e `tenant.ativo` no banco (não confia apenas na claim do JWT) — fecha a brecha de um JWT ainda válido (não expirado) para um usuário/tenant desativado depois da emissão do token. Retorna 401 se qualquer um estiver inativo.
- Alternativa descartada: filtro só a nível de Hibernate (`@Filter`) — não protege contra SQL nativo/relatórios diretos no banco (risco que o PRD-14 §7 aponta explicitamente).

### Autorização (papéis)
- `papel` do usuário vai como claim no JWT.
- Spring Security com `@EnableMethodSecurity`; endpoints futuros de outros domínios anotam `@PreAuthorize("hasRole('ADMINISTRADOR')")` etc. — mecanismo único de autorização (regra 6 do PRD-14), sem reintroduzir os dois mecanismos coexistentes do legado.
- Nesta fatia, não há endpoint de negócio protegido por papel além dos próprios endpoints de auth — a fundação fica pronta para os domínios seguintes.

### Rate limiting
- Login e recuperação de senha: **5 tentativas falhas em 15 minutos** por IP e **5 tentativas falhas em 15 minutos** por e-mail (o que for atingido primeiro bloqueia), com backoff temporário de 15 minutos. Guardado em memória para esta fatia (upgrade para Redis fica em aberto se o backend escalar horizontalmente — nota de risco, seção 8).

### Recuperação de senha
1. `POST /api/auth/forgot-password` `{ email }` → sempre `200`, mensagem genérica ("se o e-mail existir, enviamos um link"), exista ou não a conta. Se existir usuário ativo, gera token aleatório seguro, guarda `SHA-256(token)` em `PasswordResetToken`, envia e-mail com link contendo o token bruto.
2. `POST /api/auth/reset-password` `{ token, novaSenha }` → valida hash + `expira_em` + `usado_em IS NULL`; se ok, atualiza `senha_hash`, marca `usado_em`. Mesma mensagem genérica de erro se o token for inválido/expirado/já usado.
3. Envio de e-mail via `JavaMailSender` (Spring), configurado 100% por variáveis de ambiente (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, remetente) — funciona com qualquer provedor SMTP-compatível sem prender o código a um SDK específico.

## 6. Frontend

Vue 3 + TypeScript + Vite + Vue Router + Pinia + Axios (Composition API).

### Tela de login (`/login`)
Replica `screenshot-login.png` pixel a pixel: painel escuro à esquerda (logo + tagline), card à direita com:
- Campos `E-mail`, `Senha` (toggle mostrar/ocultar), checkbox `Manter conectado`.
- Link `Esqueci minha senha` → navega para `/esqueci-senha` (funcional).
- Link `Fale com o time comercial` → inerte (sem navegação, cursor `not-allowed`; provisionamento de tenant é fora de escopo desta fatia).
- Submit → `POST /api/auth/login` via Axios (`withCredentials: true`, cookie setado pela resposta).
- Erros exibidos na mesma área de mensagem: 401 genérico ("E-mail ou senha inválidos") e 429 de rate limit ("Muitas tentativas, tente novamente em instantes").

### Telas de recuperação de senha
- `/esqueci-senha`: campo de e-mail, submit → sempre mostra a mesma mensagem de sucesso genérica.
- `/redefinir-senha?token=...`: lê `token` da query string, campos nova senha + confirmação, submit → `POST /api/auth/reset-password`.

### Estado e roteamento
- Pinia store `auth`: guarda apenas estado derivado (usuário logado, papel) para reatividade da UI — nunca o token (fica só no cookie `HttpOnly`).
- Guarda de rota do Vue Router chama `GET /api/auth/me` para decidir se o usuário está autenticado; redireciona para `/login` em caso de 401.

## 7. Testes

- **Isolamento entre tenants (obrigatório)**: Testcontainers (PostgreSQL), cria 2 tenants + usuários, prova via repository que operações de um tenant não leem/alteram dados do outro (RLS em ação).
- **Autenticação**: login válido; senha errada; e-mail inexistente; usuário inativo; tenant inativo — todos com mensagem genérica; claims corretas no JWT; expiração 8h/30 dias conforme `manterConectado`.
- **Revogação por desativação**: usuário com JWT válido é desativado no meio da sessão → próxima request retorna 401.
- **Rate limiting**: N+1 tentativas falhas de login/recuperação de senha bloqueiam temporariamente.
- **Recuperação de senha**: token válido reseta senha; token expirado/usado/inválido retorna erro genérico; e-mail inexistente não revela isso na resposta.
- **Frontend**: testes de componente (Vitest) para as telas de login, esqueci-senha e redefinir-senha — render, submit, exibição de erro.

## 8. Riscos e notas abertas

1. **Desvio de unicidade de e-mail** (seção 2, item 1) é uma decisão de produto que reduz flexibilidade multi-tenant para usuários que atendem múltiplos tenants — aceito conscientemente nesta rodada de alinhamento.
2. **Rate limiting em memória** não sobrevive a reinício do processo nem escala entre múltiplas instâncias do backend — suficiente para esta fatia; migrar para Redis se/quando o backend escalar horizontalmente.
3. **Revogação de JWT** é mitigada (checagem de `ativo` a cada request), mas não há blacklist de tokens individuais — se necessário revogar um token específico antes da expiração por outro motivo (ex. logout remoto forçado), precisa de spec futuro.
4. Provisionamento de tenant real e migração dos dados legados do sisconf permanecem como dependências para o tenant real entrar em produção — o seed de dev/test desta fatia não substitui isso.

## 9. Definition of Done desta fatia

- [ ] Repositórios `mesh-suite-backend/` e `mesh-suite-frontend/` criados com README, Dockerfile, e `docker-compose.yml` funcional na raiz.
- [ ] `Tenant`, `Empresa`, `Usuario`, `PasswordResetToken` implementados com as constraints desta spec (incluindo unicidade global de `email` e `cnpj`).
- [ ] Login funcional (e-mail + senha), JWT em cookie `HttpOnly`, mensagens de erro genéricas.
- [ ] RLS ativo em toda tabela de negócio, com teste automatizado de isolamento entre 2 tenants passando.
- [ ] Checagem de `ativo` (usuário/tenant) a cada request autenticado.
- [ ] Rate limiting funcional em login e recuperação de senha.
- [ ] Recuperação de senha completa (solicitação + reset), e-mail via SMTP configurado por variável de ambiente.
- [ ] Tela de login replicando `screenshot-login.png`; telas de esqueci-senha/redefinir-senha.
- [ ] Seed de dev/test (Flyway, perfil `dev`/`test`) com 2 tenants de exemplo.
- [ ] Nenhum segredo (senha de banco, chave de assinatura JWT, credenciais SMTP) commitado — tudo via variável de ambiente.
- [ ] `tabela-execucao.md` atualizado com as tarefas desta fatia (IDs de backend e frontend separados).
