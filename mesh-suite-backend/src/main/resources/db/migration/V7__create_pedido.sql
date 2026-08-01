CREATE TABLE pedido_contador (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    proximo_numero INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE pedido_contador ENABLE ROW LEVEL SECURITY;
ALTER TABLE pedido_contador FORCE ROW LEVEL SECURITY;

CREATE POLICY pedido_contador_tenant_isolation ON pedido_contador
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE pedido (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    numero INTEGER NOT NULL,
    cliente_id UUID NOT NULL REFERENCES parceiro(id),
    vendedor_id UUID NOT NULL REFERENCES usuario(id),
    data_pedido DATE NOT NULL DEFAULT CURRENT_DATE,
    data_entrega DATE,
    status VARCHAR(10) NOT NULL DEFAULT 'DIGITADO' CHECK (status IN ('DIGITADO','EM_PREPARO','FATURADO')),
    desconto NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_pedido_tenant_numero ON pedido(tenant_id, numero);
CREATE INDEX idx_pedido_tenant_id ON pedido(tenant_id);
CREATE INDEX idx_pedido_cliente_id ON pedido(cliente_id);
CREATE INDEX idx_pedido_vendedor_id ON pedido(vendedor_id);

ALTER TABLE pedido ENABLE ROW LEVEL SECURITY;
ALTER TABLE pedido FORCE ROW LEVEL SECURITY;

CREATE POLICY pedido_tenant_isolation ON pedido
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE item_pedido (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pedido_id UUID NOT NULL REFERENCES pedido(id) ON DELETE CASCADE,
    produto_id UUID NOT NULL REFERENCES produto(id),
    quantidade NUMERIC(12,3) NOT NULL,
    valor_unitario NUMERIC(12,2) NOT NULL,
    valor_total NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_item_pedido_pedido_id ON item_pedido(pedido_id);

ALTER TABLE item_pedido ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_pedido FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent pedido
-- row's own RLS policy, matched by pedido_id. Same pattern as parceiro_contato.
CREATE POLICY item_pedido_tenant_isolation ON item_pedido
    USING (EXISTS (
        SELECT 1 FROM pedido p
        WHERE p.id = item_pedido.pedido_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
