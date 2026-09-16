-- Mirrors password_reset_token exactly (see V4__create_password_reset_token.sql):
-- no RLS, because this is read before any tenant context exists in the session.
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
