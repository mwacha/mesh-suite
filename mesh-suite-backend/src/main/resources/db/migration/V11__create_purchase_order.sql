CREATE TABLE purchase_order_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE purchase_order_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY purchase_order_counter_tenant_isolation ON purchase_order_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE purchase_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    supplier_id UUID NOT NULL REFERENCES partner(id),
    buyer_id UUID NOT NULL REFERENCES app_user(id),
    order_date DATE NOT NULL DEFAULT CURRENT_DATE,
    expected_delivery_date DATE,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RECEIVED','CANCELLED')),
    discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_purchase_order_tenant_number ON purchase_order(tenant_id, number);
CREATE INDEX idx_purchase_order_tenant_id ON purchase_order(tenant_id);
CREATE INDEX idx_purchase_order_supplier_id ON purchase_order(supplier_id);
CREATE INDEX idx_purchase_order_buyer_id ON purchase_order(buyer_id);

ALTER TABLE purchase_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order FORCE ROW LEVEL SECURITY;

CREATE POLICY purchase_order_tenant_isolation ON purchase_order
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE purchase_order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES produto(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_value NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_purchase_order_item_purchase_order_id ON purchase_order_item(purchase_order_id);

ALTER TABLE purchase_order_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- purchase_order row's own RLS policy, matched by purchase_order_id. Same
-- pattern as item_pedido/partner_contact.
CREATE POLICY purchase_order_item_tenant_isolation ON purchase_order_item
    USING (EXISTS (
        SELECT 1 FROM purchase_order po
        WHERE po.id = purchase_order_item.purchase_order_id
          AND po.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
