CREATE TABLE brand (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_brand_tenant_name ON brand(tenant_id, name);
CREATE INDEX idx_brand_tenant_id ON brand(tenant_id);

ALTER TABLE brand ENABLE ROW LEVEL SECURITY;
ALTER TABLE brand FORCE ROW LEVEL SECURITY;

CREATE POLICY brand_tenant_isolation ON brand
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
