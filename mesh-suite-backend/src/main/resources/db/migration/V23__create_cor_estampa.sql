CREATE TABLE cor_estampa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(100) NOT NULL,
    data_vigencia DATE NOT NULL,
    descricao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_cor_estampa_tenant_nome ON cor_estampa(tenant_id, nome);
CREATE INDEX idx_cor_estampa_tenant_id ON cor_estampa(tenant_id);

ALTER TABLE cor_estampa ENABLE ROW LEVEL SECURITY;
ALTER TABLE cor_estampa FORCE ROW LEVEL SECURITY;

CREATE POLICY cor_estampa_tenant_isolation ON cor_estampa
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
