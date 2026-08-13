CREATE TABLE price_table (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    product_selection_mode VARCHAR(20) NOT NULL
        CHECK (product_selection_mode IN ('ALL_PRODUCTS','SELECT_PRODUCTS')),
    adjustment_method VARCHAR(10) NOT NULL CHECK (adjustment_method IN ('AUTOMATIC','MANUAL')),
    adjustment_operation VARCHAR(10) CHECK (adjustment_operation IN ('ADD','SUBTRACT')),
    adjustment_value_type VARCHAR(12) CHECK (adjustment_value_type IN ('FIXED','PERCENTAGE')),
    adjustment_value NUMERIC(12,2),
    rounding VARCHAR(20) NOT NULL
        CHECK (rounding IN ('NO_ROUNDING','END_IN_0','END_IN_9','END_IN_90','END_IN_99')),
    effective_start_date DATE NOT NULL,
    effective_end_date DATE,
    min_sale_price NUMERIC(12,2),
    default_commission_percentage NUMERIC(5,2),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_tabela_preco_tenant_nome ON price_table(tenant_id, name);
CREATE INDEX idx_tabela_preco_tenant_id ON price_table(tenant_id);

ALTER TABLE price_table ENABLE ROW LEVEL SECURITY;
ALTER TABLE price_table FORCE ROW LEVEL SECURITY;

CREATE POLICY tabela_preco_tenant_isolation ON price_table
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE price_table_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    price_table_id UUID NOT NULL REFERENCES price_table(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    table_price NUMERIC(12,2),
    commission_percentage NUMERIC(5,2)
);

CREATE INDEX idx_tabela_preco_item_tabela_preco_id ON price_table_item(price_table_id);

ALTER TABLE price_table_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE price_table_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- price_table row's own RLS policy, matched by price_table_id. Same
-- pattern as purchase_order_item.
CREATE POLICY tabela_preco_item_tenant_isolation ON price_table_item
    USING (EXISTS (
        SELECT 1 FROM price_table pt
        WHERE pt.id = price_table_item.price_table_id
          AND pt.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
