-- Unifies Produto Simples, Kit and Variação (parent + generated children) into
-- the single `produto` table, discriminated by `tipo`, instead of separate
-- tables per type. Every sellable/stockable unit -- including each generated
-- variant SKU -- is a `produto` row, so SKU uniqueness (idx_produto_tenant_sku,
-- already in V6) and any FK from other modules (purchase_order_item,
-- pedido_item, stock_movement, ...) keep working unchanged for every type.

ALTER TABLE produto ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'PRODUCT'
    CHECK (tipo IN ('PRODUCT','PRODUCT_KIT','VARIATION_PARENT','VARIATION_CHILD'));

ALTER TABLE produto ADD COLUMN parent_id UUID REFERENCES produto(id);

-- Only VARIATION_CHILD rows point back at the VARIATION_PARENT they were
-- generated from; combinacao_valores (see below) is populated in lockstep.
ALTER TABLE produto ADD CONSTRAINT produto_parent_iff_variation_child
    CHECK ((tipo = 'VARIATION_CHILD') = (parent_id IS NOT NULL));

-- Values joined by '|', in the same order as the parent's tipo_variacao.ordem
-- (e.g. "P|Branco") -- mirrors the key the frontend already uses internally
-- (combinacao.join('|')), instead of a fully normalized join table, since
-- nothing needs to query variants by an individual variation value yet.
ALTER TABLE produto ADD COLUMN combinacao_valores VARCHAR(500);

ALTER TABLE produto ADD CONSTRAINT produto_combinacao_iff_variation_child
    CHECK ((tipo = 'VARIATION_CHILD') = (combinacao_valores IS NOT NULL));

CREATE INDEX idx_produto_parent_id ON produto(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_produto_tenant_tipo ON produto(tenant_id, tipo);


CREATE TABLE produto_kit_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    produto_kit_id UUID NOT NULL REFERENCES produto(id) ON DELETE CASCADE,
    produto_id UUID NOT NULL REFERENCES produto(id),
    quantidade NUMERIC(12,3) NOT NULL CHECK (quantidade > 0)
);

CREATE UNIQUE INDEX idx_produto_kit_item_kit_produto ON produto_kit_item(produto_kit_id, produto_id);
CREATE INDEX idx_produto_kit_item_produto_kit_id ON produto_kit_item(produto_kit_id);

ALTER TABLE produto_kit_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE produto_kit_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- produto row's own RLS policy, matched by produto_kit_id. Same pattern as
-- purchase_order_item.
CREATE POLICY produto_kit_item_tenant_isolation ON produto_kit_item
    USING (EXISTS (
        SELECT 1 FROM produto p
        WHERE p.id = produto_kit_item.produto_kit_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));


CREATE TABLE tipo_variacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    produto_id UUID NOT NULL REFERENCES produto(id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL,
    ordem INTEGER NOT NULL,
    UNIQUE (produto_id, nome)
);

CREATE INDEX idx_tipo_variacao_produto_id ON tipo_variacao(produto_id);

ALTER TABLE tipo_variacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE tipo_variacao FORCE ROW LEVEL SECURITY;

CREATE POLICY tipo_variacao_tenant_isolation ON tipo_variacao
    USING (EXISTS (
        SELECT 1 FROM produto p
        WHERE p.id = tipo_variacao.produto_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));


CREATE TABLE tipo_variacao_valor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_variacao_id UUID NOT NULL REFERENCES tipo_variacao(id) ON DELETE CASCADE,
    valor VARCHAR(100) NOT NULL,
    ordem INTEGER NOT NULL,
    UNIQUE (tipo_variacao_id, valor)
);

CREATE INDEX idx_tipo_variacao_valor_tipo_variacao_id ON tipo_variacao_valor(tipo_variacao_id);

ALTER TABLE tipo_variacao_valor ENABLE ROW LEVEL SECURITY;
ALTER TABLE tipo_variacao_valor FORCE ROW LEVEL SECURITY;

CREATE POLICY tipo_variacao_valor_tenant_isolation ON tipo_variacao_valor
    USING (EXISTS (
        SELECT 1 FROM tipo_variacao tv
        JOIN produto p ON p.id = tv.produto_id
        WHERE tv.id = tipo_variacao_valor.tipo_variacao_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
