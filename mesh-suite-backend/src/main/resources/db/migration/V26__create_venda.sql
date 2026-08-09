CREATE TABLE venda_contador (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    proximo_numero INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE venda_contador ENABLE ROW LEVEL SECURITY;
ALTER TABLE venda_contador FORCE ROW LEVEL SECURITY;

CREATE POLICY venda_contador_tenant_isolation ON venda_contador
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE venda (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    numero INTEGER NOT NULL,
    pedido_id UUID NOT NULL UNIQUE REFERENCES pedido(id),
    cliente_id UUID NOT NULL REFERENCES parceiro(id),
    vendedor_id UUID NOT NULL REFERENCES app_user(id),
    data_emissao DATE NOT NULL DEFAULT CURRENT_DATE,
    desconto NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_icms NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_ipi NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_pis NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_cofins NUMERIC(12,2) NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_venda_tenant_numero ON venda(tenant_id, numero);
CREATE INDEX idx_venda_tenant_id ON venda(tenant_id);
CREATE INDEX idx_venda_cliente_id ON venda(cliente_id);
CREATE INDEX idx_venda_vendedor_id ON venda(vendedor_id);

ALTER TABLE venda ENABLE ROW LEVEL SECURITY;
ALTER TABLE venda FORCE ROW LEVEL SECURITY;

CREATE POLICY venda_tenant_isolation ON venda
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE item_venda (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venda_id UUID NOT NULL REFERENCES venda(id) ON DELETE CASCADE,
    produto_id UUID NOT NULL REFERENCES produto(id),
    quantidade NUMERIC(12,3) NOT NULL,
    valor_unitario NUMERIC(12,2) NOT NULL,
    valor_total NUMERIC(12,2) NOT NULL,
    valor_icms NUMERIC(12,2) NOT NULL,
    valor_ipi NUMERIC(12,2) NOT NULL,
    valor_pis NUMERIC(12,2) NOT NULL,
    valor_cofins NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_item_venda_venda_id ON item_venda(venda_id);

ALTER TABLE item_venda ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_venda FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent venda
-- row's own RLS policy, matched by venda_id. Same pattern as item_pedido.
CREATE POLICY item_venda_tenant_isolation ON item_venda
    USING (EXISTS (
        SELECT 1 FROM venda v
        WHERE v.id = item_venda.venda_id
          AND v.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
