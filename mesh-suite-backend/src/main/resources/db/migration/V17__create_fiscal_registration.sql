CREATE TABLE fiscal_registration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    description VARCHAR(255) NOT NULL,
    cfop VARCHAR(10),
    icms_cst VARCHAR(10),
    icms_rate NUMERIC(5,2) NOT NULL,
    ipi_rate NUMERIC(5,2) NOT NULL,
    pis_rate NUMERIC(5,2) NOT NULL,
    cofins_rate NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fiscal_registration_tenant_id ON fiscal_registration(tenant_id);

ALTER TABLE fiscal_registration ENABLE ROW LEVEL SECURITY;
ALTER TABLE fiscal_registration FORCE ROW LEVEL SECURITY;

CREATE POLICY fiscal_registration_tenant_isolation ON fiscal_registration
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
