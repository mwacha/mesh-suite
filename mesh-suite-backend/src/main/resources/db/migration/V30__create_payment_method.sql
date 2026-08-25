CREATE TABLE payment_method (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    description VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_payment_method_tenant_description ON payment_method(tenant_id, description);
CREATE INDEX idx_payment_method_tenant_id ON payment_method(tenant_id);

ALTER TABLE payment_method ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_method FORCE ROW LEVEL SECURITY;

CREATE POLICY payment_method_tenant_isolation ON payment_method
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE payment_method_installment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_method_id UUID NOT NULL REFERENCES payment_method(id) ON DELETE CASCADE,
    installment_number INTEGER NOT NULL,
    days_due INTEGER NOT NULL,
    percentage NUMERIC(5,2) NOT NULL
);

CREATE INDEX idx_payment_method_installment_payment_method_id ON payment_method_installment(payment_method_id);

ALTER TABLE payment_method_installment ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_method_installment FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- payment_method row's own RLS policy, matched by payment_method_id. Same
-- pattern as price_table_item/partner_contact.
CREATE POLICY payment_method_installment_tenant_isolation ON payment_method_installment
    USING (EXISTS (
        SELECT 1 FROM payment_method pm
        WHERE pm.id = payment_method_installment.payment_method_id
          AND pm.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
