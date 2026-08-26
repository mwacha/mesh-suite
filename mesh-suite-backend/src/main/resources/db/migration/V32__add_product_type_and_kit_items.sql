ALTER TABLE product ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'PRODUCT'
    CHECK (type IN ('PRODUCT','VARIATION_PARENT','VARIATION_CHILD','PRODUCT_KIT'));
ALTER TABLE product ADD COLUMN parent_product_id UUID REFERENCES product(id) ON DELETE CASCADE;
ALTER TABLE product ADD COLUMN size VARCHAR(50);

CREATE INDEX idx_product_parent_product_id ON product(parent_product_id);

CREATE TABLE product_kit_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kit_product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    component_product_id UUID NOT NULL REFERENCES product(id),
    quantity NUMERIC(12,3) NOT NULL,
    UNIQUE (kit_product_id, component_product_id)
);
CREATE INDEX idx_product_kit_item_kit_product_id ON product_kit_item(kit_product_id);

ALTER TABLE product_kit_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_kit_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation via the parent product row's tenant,
-- same pattern as price_table_item / purchase_order_item.
CREATE POLICY product_kit_item_tenant_isolation ON product_kit_item
    USING (EXISTS (
        SELECT 1 FROM product p
        WHERE p.id = product_kit_item.kit_product_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
