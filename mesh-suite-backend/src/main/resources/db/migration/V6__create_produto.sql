CREATE TABLE produto (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    codigo_barras VARCHAR(50),
    marca VARCHAR(100),
    categoria VARCHAR(100),
    preco_venda NUMERIC(12,2) NOT NULL,
    preco_custo NUMERIC(12,2),
    status VARCHAR(10) NOT NULL DEFAULT 'ATIVO' CHECK (status IN ('ATIVO','INATIVO')),
    descricao TEXT,
    quantidade_estoque NUMERIC(12,3) NOT NULL DEFAULT 0,
    unidade_medida VARCHAR(5) NOT NULL DEFAULT 'UN'
        CHECK (unidade_medida IN ('UN','KG','G','L','ML','MT','CM','CX','PC','PAR','DZ')),
    estoque_minimo NUMERIC(12,3),
    estoque_maximo NUMERIC(12,3),
    peso NUMERIC(10,3),
    comprimento NUMERIC(10,2),
    largura NUMERIC(10,2),
    altura NUMERIC(10,2),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_produto_tenant_sku ON produto(tenant_id, sku);
CREATE INDEX idx_produto_tenant_id ON produto(tenant_id);

ALTER TABLE produto ENABLE ROW LEVEL SECURITY;
ALTER TABLE produto FORCE ROW LEVEL SECURITY;

CREATE POLICY produto_tenant_isolation ON produto
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
