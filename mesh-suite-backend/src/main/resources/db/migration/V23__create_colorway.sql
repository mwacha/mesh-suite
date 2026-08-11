CREATE TABLE colorway (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    effective_date DATE NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_colorway_tenant_name ON colorway(tenant_id, name);
CREATE INDEX idx_colorway_tenant_id ON colorway(tenant_id);

ALTER TABLE colorway ENABLE ROW LEVEL SECURITY;
ALTER TABLE colorway FORCE ROW LEVEL SECURITY;

CREATE POLICY colorway_tenant_isolation ON colorway
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
