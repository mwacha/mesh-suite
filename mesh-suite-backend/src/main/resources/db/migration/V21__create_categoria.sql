CREATE TABLE categoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_categoria_tenant_nome ON categoria(tenant_id, nome);
CREATE INDEX idx_categoria_tenant_id ON categoria(tenant_id);

ALTER TABLE categoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE categoria FORCE ROW LEVEL SECURITY;

CREATE POLICY categoria_tenant_isolation ON categoria
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
