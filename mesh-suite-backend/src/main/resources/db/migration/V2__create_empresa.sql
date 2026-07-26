CREATE TABLE empresa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    razao_social VARCHAR(255) NOT NULL,
    cnpj VARCHAR(14) NOT NULL UNIQUE,
    ativo BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_empresa_tenant_id ON empresa(tenant_id);

ALTER TABLE empresa ENABLE ROW LEVEL SECURITY;
-- FORCE so the policy also applies to the table owner (the role the app
-- connects as); without FORCE, RLS is bypassed for the owning role.
ALTER TABLE empresa FORCE ROW LEVEL SECURITY;

-- current_setting(..., true) returns NULL instead of raising when the
-- session var isn't set, so an unset app.tenant_id safely denies all rows
-- (NULL = tenant_id is never true) rather than erroring out.
CREATE POLICY empresa_tenant_isolation ON empresa
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
