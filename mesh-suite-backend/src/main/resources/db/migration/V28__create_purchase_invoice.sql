CREATE TABLE purchase_invoice_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE purchase_invoice_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_invoice_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY purchase_invoice_counter_tenant_isolation ON purchase_invoice_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE purchase_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    invoice_number VARCHAR(20) NOT NULL,
    series VARCHAR(10) NOT NULL,
    model VARCHAR(10) NOT NULL,
    purchase_order_id UUID NOT NULL UNIQUE REFERENCES purchase_order(id),
    supplier_id UUID NOT NULL REFERENCES partner(id),
    issue_date DATE NOT NULL,
    entry_date DATE NOT NULL,
    discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    icms_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ipi_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    pis_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    cofins_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_purchase_invoice_tenant_number ON purchase_invoice(tenant_id, number);
-- Regra 2 do PRD: bloqueio de nota duplicada por fornecedor.
CREATE UNIQUE INDEX idx_purchase_invoice_supplier_invoice_number ON purchase_invoice(supplier_id, invoice_number);
CREATE INDEX idx_purchase_invoice_tenant_id ON purchase_invoice(tenant_id);
CREATE INDEX idx_purchase_invoice_supplier_id ON purchase_invoice(supplier_id);

ALTER TABLE purchase_invoice ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_invoice FORCE ROW LEVEL SECURITY;

CREATE POLICY purchase_invoice_tenant_isolation ON purchase_invoice
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE purchase_invoice_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_invoice_id UUID NOT NULL REFERENCES purchase_invoice(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_value NUMERIC(12,2) NOT NULL,
    icms_amount NUMERIC(12,2) NOT NULL,
    ipi_amount NUMERIC(12,2) NOT NULL,
    pis_amount NUMERIC(12,2) NOT NULL,
    cofins_amount NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_purchase_invoice_item_purchase_invoice_id ON purchase_invoice_item(purchase_invoice_id);

ALTER TABLE purchase_invoice_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_invoice_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- purchase_invoice row's own RLS policy, matched by purchase_invoice_id.
-- Same pattern as sale_item/purchase_order_item.
CREATE POLICY purchase_invoice_item_tenant_isolation ON purchase_invoice_item
    USING (EXISTS (
        SELECT 1 FROM purchase_invoice pi
        WHERE pi.id = purchase_invoice_item.purchase_invoice_id
          AND pi.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
