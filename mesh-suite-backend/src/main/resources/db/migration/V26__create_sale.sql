-- mesh-suite-backend/src/main/resources/db/migration/V26__create_sale.sql
CREATE TABLE sale_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE sale_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY sale_counter_tenant_isolation ON sale_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE sale (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    order_id UUID NOT NULL UNIQUE REFERENCES pedido(id),
    customer_id UUID NOT NULL REFERENCES parceiro(id),
    salesperson_id UUID NOT NULL REFERENCES app_user(id),
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    icms_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ipi_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    pis_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    cofins_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_sale_tenant_number ON sale(tenant_id, number);
CREATE INDEX idx_sale_tenant_id ON sale(tenant_id);
CREATE INDEX idx_sale_customer_id ON sale(customer_id);
CREATE INDEX idx_sale_salesperson_id ON sale(salesperson_id);

ALTER TABLE sale ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale FORCE ROW LEVEL SECURITY;

CREATE POLICY sale_tenant_isolation ON sale
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE sale_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID NOT NULL REFERENCES sale(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES produto(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    icms_amount NUMERIC(12,2) NOT NULL,
    ipi_amount NUMERIC(12,2) NOT NULL,
    pis_amount NUMERIC(12,2) NOT NULL,
    cofins_amount NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_sale_item_sale_id ON sale_item(sale_id);

ALTER TABLE sale_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent sale
-- row's own RLS policy, matched by sale_id. Same pattern as item_pedido.
CREATE POLICY sale_item_tenant_isolation ON sale_item
    USING (EXISTS (
        SELECT 1 FROM sale s
        WHERE s.id = sale_item.sale_id
          AND s.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
