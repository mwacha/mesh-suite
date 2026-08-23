# Tabela de Execução

## Backlog — itens do menu sem tela

Itens que já aparecem no menu lateral (`AppSidebar.vue`, `route: null`) mas ainda não têm tela implementada.

| ID | Item | Grupo do menu | Observação | Status |
|----|------|----------------|------------|--------|
| MENU-01 | Fornecedores | CADASTROS | **Concluído** — `FornecedoresListView.vue`/`FornecedorFormView.vue`/`FornecedorDetailView.vue`, reaproveitando 100% do backend Partner/`PartnerRole.SUPPLIER` já existente, sem mudança de backend. | Concluído |
| MENU-02 | Transportadoras | CADASTROS | Idem — hoje `PartnerRole.CARRIER` existe no backend, sem tela própria | Pendente |
| MENU-03 | Marcas | CATÁLOGO | Sem entidade/backend ainda | Pendente |
| MENU-04 | Empresa | CONFIGURAÇÕES | Tela de edição dos dados da(s) `Company`(s) do tenant — hoje só existe via seed/migration | Pendente |
| MENU-05 | Permissões | CONFIGURAÇÕES | Tela dedicada de matriz de permissões — hoje só é editável embutida no formulário de Usuário | Pendente |

## PRD-14 slice 1 (Login/Multitenant)

Plano: `docs/superpowers/specs/2026-07-25-login-multitenant-foundation-design.md`
Plano de implementação: `docs/superpowers/plans/2026-07-25-login-multitenant-foundation-plan.md`

| ID | Tarefa | Área | Status |
|----|--------|------|--------|
| BE-01 | Scaffolding backend + frontend + docker-compose | Infra | Concluído |
| BE-02 | Entidade Tenant + migration | Backend | Concluído |
| BE-03 | Entidade Empresa + RLS + migration | Backend | Concluído |
| BE-04 | Entidade Usuario + RLS + política de login + migration | Backend | Concluído |
| BE-05 | Entidade PasswordResetToken + migration | Backend | Concluído |
| BE-06 | JwtService | Backend | Concluído |
| BE-07 | TenantContext + aspecto RLS + teste obrigatório de isolamento | Backend | Concluído |
| BE-08 | JwtAuthenticationFilter + SecurityConfig | Backend | Concluído |
| BE-09 | RateLimiter | Backend | Concluído |
| BE-10 | AuthService.login + AuthController (/login, /me) | Backend | Concluído |
| BE-11 | Recuperação de senha (MailService, PasswordResetService, endpoints) | Backend | Concluído |
| BE-12 | Seed dev/test (2 tenants) | Backend | Concluído |
| FE-13 | Axios client + Pinia auth store + router guard | Frontend | Concluído |
| FE-14 | LoginView.vue | Frontend | Concluído |
| FE-15 | ForgotPasswordView.vue + ResetPasswordView.vue | Frontend | Concluído |

## Definition of Done (spec §9) — status

- [x] Repositórios `mesh-suite-backend/` e `mesh-suite-frontend/` criados com README, Dockerfile, e `docker-compose.yml` funcional na raiz. (BE-01)
- [x] `Tenant`, `Empresa`, `Usuario`, `PasswordResetToken` implementados com unicidade global de `email` e `cnpj`. (BE-02 a BE-05)
- [x] Login funcional (e-mail + senha), JWT em cookie `HttpOnly`, mensagens de erro genéricas. (BE-10)
- [x] RLS ativo em toda tabela de negócio, com teste automatizado de isolamento entre 2 tenants passando. (BE-07)
- [x] Checagem de `ativo` (usuário/tenant) a cada request autenticado. (BE-08)
- [x] Rate limiting funcional em login e recuperação de senha. (BE-09, BE-11)
- [x] Recuperação de senha completa, e-mail via SMTP configurado por variável de ambiente. (BE-11)
- [x] Tela de login replicando `screenshot-login.png`; telas de esqueci-senha/redefinir-senha. (FE-14, FE-15)
- [x] Seed de dev/test (Flyway, perfil `dev`/`test`) com 2 tenants de exemplo. (BE-12)
- [x] Nenhum segredo commitado — tudo via variável de ambiente. (BE-01, verified across all tasks)
- [x] `tabela-execucao.md` atualizado. (this file)
