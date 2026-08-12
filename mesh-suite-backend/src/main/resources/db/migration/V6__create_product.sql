CREATE TABLE product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    barcode VARCHAR(50),
    brand VARCHAR(100),
    categoria VARCHAR(100),
    sale_price NUMERIC(12,2) NOT NULL,
    cost_price NUMERIC(12,2),
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    description TEXT,
    stock_quantity NUMERIC(12,3) NOT NULL DEFAULT 0,
    measurement_unit VARCHAR(5) NOT NULL DEFAULT 'UN'
        CHECK (measurement_unit IN ('UN','KG','G','L','ML','MT','CM','CX','PC','PAR','DZ')),
    min_stock NUMERIC(12,3),
    max_stock NUMERIC(12,3),
    weight NUMERIC(10,3),
    length NUMERIC(10,2),
    width NUMERIC(10,2),
    height NUMERIC(10,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_produto_tenant_sku ON product(tenant_id, sku);
CREATE INDEX idx_produto_tenant_id ON product(tenant_id);

ALTER TABLE product ENABLE ROW LEVEL SECURITY;
ALTER TABLE product FORCE ROW LEVEL SECURITY;

CREATE POLICY produto_tenant_isolation ON product
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
