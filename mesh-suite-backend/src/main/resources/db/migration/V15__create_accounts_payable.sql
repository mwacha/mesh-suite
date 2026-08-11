CREATE TABLE accounts_payable_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE accounts_payable_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts_payable_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY accounts_payable_counter_tenant_isolation ON accounts_payable_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE accounts_payable (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    installment_number INTEGER NOT NULL,
    total_installments INTEGER NOT NULL,
    supplier_id UUID NOT NULL REFERENCES partner(id),
    amount NUMERIC(12,2) NOT NULL,
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date DATE NOT NULL,
    payment_date DATE,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PAID')),
    reference_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_accounts_payable_tenant_number ON accounts_payable(tenant_id, number);
CREATE INDEX idx_accounts_payable_tenant_id ON accounts_payable(tenant_id);
CREATE INDEX idx_accounts_payable_supplier_id ON accounts_payable(supplier_id);

ALTER TABLE accounts_payable ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts_payable FORCE ROW LEVEL SECURITY;

CREATE POLICY accounts_payable_tenant_isolation ON accounts_payable
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
