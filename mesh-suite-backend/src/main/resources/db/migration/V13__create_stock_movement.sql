CREATE TABLE stock_movement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    product_id UUID NOT NULL REFERENCES product(id),
    type VARCHAR(10) NOT NULL CHECK (type IN ('INBOUND','OUTBOUND')),
    quantity NUMERIC(12,3) NOT NULL,
    origin VARCHAR(10) NOT NULL CHECK (origin IN ('MANUAL','PURCHASE')),
    reference_id UUID,
    balance_after NUMERIC(12,3) NOT NULL,
    user_id UUID NOT NULL REFERENCES app_user(id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movement_tenant_id ON stock_movement(tenant_id);
CREATE INDEX idx_stock_movement_product_id ON stock_movement(product_id);

ALTER TABLE stock_movement ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_movement FORCE ROW LEVEL SECURITY;

-- Own tenant_id column and own policy -- unlike sales_order_item/purchase_order_item,
-- this isn't a line item of a single parent header; it's a standalone ledger
-- row in its own right, same pattern as sales_order/purchase_order themselves.
CREATE POLICY stock_movement_tenant_isolation ON stock_movement
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
