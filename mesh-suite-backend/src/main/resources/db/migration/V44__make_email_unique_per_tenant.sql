-- mesh-suite-backend/src/main/resources/db/migration/V44__make_email_unique_per_tenant.sql
-- Matches PRD-14 seção 5 regra 2: e-mail é único por tenant, não globalmente --
-- permite a mesma pessoa ter contas em tenants diferentes com o mesmo e-mail.
ALTER TABLE app_user DROP CONSTRAINT usuario_email_key;
ALTER TABLE app_user ADD CONSTRAINT app_user_tenant_id_email_key UNIQUE (tenant_id, email);

-- idx_app_user_email (não-único) já existe e continua servindo a busca por e-mail
-- entre tenants usada no login (AuthService.findAllByEmailForLogin).
