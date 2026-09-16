# Cadastro Público de Tenant (Signup) — Spec de Design

**Data**: 2026-09-16
**PRD relacionado**: `prd/PRD-14-login-multitenant.md`
**Depende de**: fundação multitenant + login já implementada (`2026-07-25-login-multitenant-foundation-design.md`)

## 1. Escopo desta fatia

O PRD-14, seção "Fora de escopo (explicitamente)", lista hoje: *"Autoatendimento/signup público de novo tenant"*. Esta fatia reverte essa decisão a pedido explícito do responsável de produto — o PRD-14 será atualizado como parte desta implementação para refletir a nova decisão.

Cobre:

- Endpoint público (sem autenticação) que cria `tenant` + `company` + primeiro `app_user` (papel ADMIN, todas as permissões) numa única transação.
- Confirmação de e-mail obrigatória antes do tenant poder ser usado — reaproveita a regra já existente "tenant inativo bloqueia login" (PRD-14 §5 regra 8).
- Rate limiting por IP+e-mail no endpoint de cadastro (reaproveita `RateLimiter` já existente).
- Tela pública de cadastro (`/cadastro`) e de confirmação (`/confirmar-cadastro`).
- Atualização do PRD-14 (mover o item de "fora de escopo" pra dentro do escopo, nova seção de fluxo, nova regra de negócio).

### Fora de escopo desta fatia

- CAPTCHA (decisão explícita: rate limit + confirmação de e-mail bastam para a v1; CAPTCHA fica como extensão futura se abuso real for observado).
- Limpeza automática de tenants nunca confirmados (`ativo=false` órfãos). Se algum dia virar problema de volume, é um job de limpeza trivial de adicionar depois — não faz parte desta fatia.
- Login automático após confirmar o e-mail (decisão explícita: só confirma e redireciona pro login).
- Qualquer mudança em como o login em si funciona (continua e-mail + senha, com seleção de conta quando o e-mail existe em mais de um tenant).

## 2. Decisões / desvios registrados

1. **Tenant nasce com `ativo = false`**, criado já com `company` e `app_user` (ambos `active = true` desde já — o bloqueio de acesso acontece inteiramente via `tenant.ativo`, não duplicado nos registros filhos).
2. **Código do tenant é gerado automaticamente** (slug do `legalName`/`tradeName`, minúsculo, sem acento, `-` no lugar de espaço, sufixo numérico em caso de colisão) — nunca é um campo do formulário público. Mantém a regra do PRD-14 §5 regra 3 (único globalmente, imutável).
3. **CNPJ duplicado é rejeitado com 409**, reaproveitando o `UNIQUE` já existente em `company.cnpj` e o mesmo tratamento de erro que `CompanyFormView.vue` já faz hoje para esse status.
4. **Mensagens de erro do cadastro não vazam qual CNPJ/e-mail já existe** além do 409 de CNPJ duplicado (que é inevitavelmente observável, já que é a mesma UX que o cadastro de empresa autenticado já expõe hoje) — mantém o espírito da regra PRD-14 §5 regra 4 (não diferenciar causas de erro sensíveis).
5. **CNPJ duplicado com tenant ainda não confirmado reenvia a confirmação, em vez de bloquear.** Sem isso, alguém que abandona o formulário antes de clicar no link (ou perde o e-mail) fica travado num 409 permanente, sem confirmar nem tentar de novo — não existe fluxo de reenvio manual (ver escopo). Regra: se o CNPJ já existe **e** o `tenant` dono dele está `ativo=false`, o signup não cria nada novo — gera um novo `tenant_signup_token` pra esse mesmo tenant/usuário (invalidando o anterior) e reenvia o e-mail, respondendo 202 igual a um cadastro novo. Se o CNPJ existe e o tenant já está `ativo=true`, aí sim é 409 de verdade.

## 3. Modelo de dados

Nova migration `V45__create_tenant_signup_token.sql`, espelhando exatamente `password_reset_token`:

```sql
CREATE TABLE tenant_signup_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    token_hash VARCHAR(64) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_signup_token_tenant_id ON tenant_signup_token(tenant_id);
CREATE UNIQUE INDEX idx_tenant_signup_token_hash ON tenant_signup_token(token_hash);
```

Sem RLS — mesmo raciocínio do `password_reset_token`: é consultado antes de haver qualquer contexto de tenant ativo na sessão. Expiração: 24h (mais folgada que o reset de senha, que é 1h — confirmar e-mail não tem a mesma urgência de segurança que redefinir senha).

Nenhuma coluna nova em `tenant`, `company` ou `app_user`.

## 4. Backend

### `POST /api/auth/signup` (público)

`SignupRequest`:
```java
record SignupRequest(
    // Empresa — mesmos campos de CompanyRequest
    @NotBlank String legalName,
    @NotBlank String cnpj,
    String tradeName, String stateRegistration, String municipalRegistration,
    String phone, String email, String website,
    String zipCode, String street, String number, String complement,
    String neighborhood, String city, String state,
    // Admin
    @NotBlank String adminName,
    @NotBlank @Email String adminEmail,
    @NotBlank @Size(min = 8) String senha
)
```

Fluxo em `TenantSignupService` (novo, mesmo molde de `PasswordResetService`):
1. `RateLimiter.isBlocked(ip, adminEmail)` → 429 se bloqueado.
2. Normaliza CNPJ (remove máscara), valida 14 dígitos.
3. Busca `Company` por CNPJ:
   - Existe e o `tenant` dono está `ativo=true` → 409 de verdade (CNPJ já em uso por uma conta confirmada). Encerra aqui.
   - Existe e o `tenant` dono está `ativo=false` (cadastro anterior nunca confirmado) → **não cria nada novo**; pula direto pro passo 6 usando o `tenant`/`app_user` já existentes (reenvio de confirmação). Os dados do formulário atual são descartados nesse caso — não há "atualizar" um cadastro pendente, só reenviar o link para o que já existe.
   - Não existe → segue os passos 4-5.
4. Gera `codigo` do tenant (slug), resolve colisão consultando `TenantRepository`.
5. Transação: cria `Tenant(ativo=false)`, `Company(active=true)`, `AppUser(role=ADMIN, active=true)` + grant completo de `user_permission` (mesma matriz que o seed de dev usa — todos os módulos × ações, exceto `USER`+`DELETE`).
6. Gera token (32 bytes aleatórios, SHA-256 armazenado, raw enviado), grava `TenantSignupToken`, `expira_em = now + 24h`.
7. `RateLimiter.recordSuccess(ip, adminEmail)`.
8. `MailService.sendSignupConfirmationEmail(adminEmail, link)` — novo método, mesmo padrão de `sendPasswordResetEmail`.
9. Retorna 202, corpo `{}` (ou mensagem genérica) — nunca ecoa dados sensíveis.

### `POST /api/auth/confirm-signup` (público)

`ConfirmSignupRequest(@NotBlank String token)`.

1. Busca por `sha256(token)`; token inexistente/expirado/já usado → mesma exceção genérica de auth (`AuthException`, 401 — igual ao reset de senha).
2. `UPDATE tenant SET ativo = true` (via `TenantRepository`, sem RLS necessária — `Tenant` não tem RLS, só `company`/`app_user`/etc. têm).
3. Marca token `usado_em = now()`.
4. Retorna 200 vazio.

## 5. Frontend

- **Rota pública `/cadastro`** → `SignupView.vue`. Estrutura copiada de `CompanyFormView.vue` (seções Identificação/Contato/Endereço, mesmas máscaras `maskCnpj`/`maskTelefone`/`maskCep`, mesma busca de CEP) + nova seção "Acesso" (nome do admin, e-mail, senha, confirmar senha — validação de senhas iguais no client). Ao submeter com sucesso, troca o formulário por uma mensagem de "verifique seu e-mail" (sem redirecionar sozinho). Erros: 409 → "já existe uma empresa cadastrada com este CNPJ" (mesma mensagem de `CompanyFormView.vue`); 429 → mensagem de rate limit; genérico → mensagem padrão de erro.
- **Rota pública `/confirmar-cadastro`** → `ConfirmSignupView.vue`. Lê `?token=` da URL no `onMounted`, chama `confirm-signup`, mostra sucesso ("conta confirmada — faça login") com link pra `/login`, ou erro (token inválido/expirado) com um link pra `/cadastro` e o texto explicando que basta preencher o formulário de novo com o mesmo CNPJ — o backend reenvia a confirmação automaticamente (regra da seção 2, item 5), sem precisar de um botão de "reenviar" dedicado nesta tela.
- `LoginView.vue` ganha um link "Criar conta" → `/cadastro`.

## 6. Atualização do PRD-14

- Seção "Fora de escopo (explicitamente)": remover a linha *"Autoatendimento/signup público de novo tenant"*.
- Nova seção de fluxo, espelhando "Fluxo — Provisionar novo Tenant (operação interna)", documentando a variante pública: dados coletados, confirmação de e-mail obrigatória como único mecanismo de ativação, rate limiting.
- Nova regra de negócio (seção 5): *"Cadastro público de tenant exige confirmação de e-mail antes de qualquer login ser permitido; o tenant nasce inativo e só é ativado pela confirmação."*

## 7. Testes

- `TenantSignupServiceTest`: cria com sucesso (tenant/company/user/permissões corretos), CNPJ duplicado com tenant ativo → 409 de verdade, CNPJ duplicado com tenant inativo → reenvia sem duplicar nenhum registro (e invalida o token anterior), geração de código com colisão, token expira, token usado não reaplica, rate limit bloqueia após 5 tentativas.
- Teste de integração ponta a ponta: `POST /signup` → 202 → confirma token → `tenant.ativo=true` → login funciona.
- `SignupView.spec.ts` / `ConfirmSignupView.spec.ts`: seguindo o padrão de `CompanyFormView.spec.ts` (mount, preenche, submete, valida chamadas de API e mensagens de erro/sucesso).

## 8. Pontos em aberto para o plano de implementação

- Confirmar se `GlobalExceptionHandler` já trata `DataIntegrityViolationException` de forma genérica o bastante para o 409 de CNPJ duplicado no signup, ou se precisa de ajuste pontual.
- Confirmar template/copy do e-mail de confirmação (reaproveitar layout do e-mail de reset de senha).
