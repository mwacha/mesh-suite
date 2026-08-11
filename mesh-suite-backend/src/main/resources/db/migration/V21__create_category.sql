CREATE TABLE category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_category_tenant_name ON category(tenant_id, name);
CREATE INDEX idx_category_tenant_id ON category(tenant_id);

ALTER TABLE category ENABLE ROW LEVEL SECURITY;
ALTER TABLE category FORCE ROW LEVEL SECURITY;

CREATE POLICY category_tenant_isolation ON category
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
