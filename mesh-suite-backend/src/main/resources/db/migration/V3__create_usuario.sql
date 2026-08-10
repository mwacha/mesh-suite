CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    papel VARCHAR(20) NOT NULL CHECK (papel IN ('ADMINISTRATIVO','REPRESENTANTE','PRODUCAO','TERCEIRIZADO','ADMINISTRADOR')),
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_acesso TIMESTAMPTZ
);

CREATE INDEX idx_usuario_tenant_id ON usuario(tenant_id);
CREATE INDEX idx_usuario_email ON usuario(email);

ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;

-- NULLIF guard: see the matching comment on company_tenant_isolation in
-- V2__create_company.sql -- a RESET custom GUC comes back as '', not NULL.
CREATE POLICY usuario_tenant_isolation ON usuario
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Login authenticates by email alone, before any tenant is known, so the
-- lookup can't go through the tenant-scoped policy above. This second
-- PERMISSIVE policy (Postgres ORs multiple permissive policies together)
-- allows SELECT only when app.bypass_tenant_check is explicitly set to
-- 'true' for that one query. Set in exactly one place in the codebase:
-- AuthService.findByEmailForLogin. See plan §"Design decision beyond the spec".
CREATE POLICY usuario_login_lookup ON usuario
    FOR SELECT
    USING (current_setting('app.bypass_tenant_check', true) = 'true');
