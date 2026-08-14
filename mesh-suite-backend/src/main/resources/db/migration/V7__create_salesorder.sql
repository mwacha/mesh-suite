CREATE TABLE sales_order_counter (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    next_number INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE sales_order_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_order_counter FORCE ROW LEVEL SECURITY;

CREATE POLICY sales_order_counter_tenant_isolation ON sales_order_counter
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE sales_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    number INTEGER NOT NULL,
    customer_id UUID NOT NULL REFERENCES partner(id),
    salesperson_id UUID NOT NULL REFERENCES usuario(id),
    order_date DATE NOT NULL DEFAULT CURRENT_DATE,
    delivery_date DATE,
    status VARCHAR(15) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','IN_PREPARATION','INVOICED')),
    discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_sales_order_tenant_number ON sales_order(tenant_id, number);
CREATE INDEX idx_sales_order_tenant_id ON sales_order(tenant_id);
CREATE INDEX idx_sales_order_customer_id ON sales_order(customer_id);
CREATE INDEX idx_sales_order_salesperson_id ON sales_order(salesperson_id);

ALTER TABLE sales_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_order FORCE ROW LEVEL SECURITY;

CREATE POLICY sales_order_tenant_isolation ON sales_order
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE sales_order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id UUID NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_sales_order_item_sales_order_id ON sales_order_item(sales_order_id);

ALTER TABLE sales_order_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_order_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent sales_order
-- row's own RLS policy, matched by sales_order_id. Same pattern as partner_contact.
CREATE POLICY sales_order_item_tenant_isolation ON sales_order_item
    USING (EXISTS (
        SELECT 1 FROM sales_order so
        WHERE so.id = sales_order_item.sales_order_id
          AND so.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
