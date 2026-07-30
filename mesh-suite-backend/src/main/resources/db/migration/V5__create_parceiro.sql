CREATE TABLE parceiro (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    tipo_pessoa VARCHAR(10) NOT NULL CHECK (tipo_pessoa IN ('FISICA','JURIDICA')),
    documento VARCHAR(14) NOT NULL,
    nome_fantasia VARCHAR(255) NOT NULL,
    razao_social VARCHAR(255),
    status VARCHAR(10) NOT NULL DEFAULT 'ATIVO' CHECK (status IN ('ATIVO','EM_RISCO','BLOQUEADO')),
    emails_cobranca VARCHAR(500),
    whatsapp VARCHAR(20),
    indicador_ie VARCHAR(20) CHECK (indicador_ie IN ('NAO_CONTRIBUINTE','CONTRIBUINTE','CONTRIBUINTE_ISENTO')),
    inscricao_estadual VARCHAR(20),
    inscricao_municipal VARCHAR(20),
    inscricao_suframa VARCHAR(20),
    cep VARCHAR(8),
    logradouro VARCHAR(255),
    numero VARCHAR(20),
    bairro VARCHAR(100),
    complemento VARCHAR(100),
    uf VARCHAR(2),
    cidade VARCHAR(100),
    observacao TEXT,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_parceiro_tenant_documento ON parceiro(tenant_id, documento);
CREATE INDEX idx_parceiro_tenant_id ON parceiro(tenant_id);

ALTER TABLE parceiro ENABLE ROW LEVEL SECURITY;
ALTER TABLE parceiro FORCE ROW LEVEL SECURITY;

CREATE POLICY parceiro_tenant_isolation ON parceiro
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE parceiro_papel (
    parceiro_id UUID NOT NULL REFERENCES parceiro(id) ON DELETE CASCADE,
    papel VARCHAR(20) NOT NULL CHECK (papel IN ('CLIENTE','FORNECEDOR','TRANSPORTADORA')),
    PRIMARY KEY (parceiro_id, papel)
);

ALTER TABLE parceiro_papel ENABLE ROW LEVEL SECURITY;
ALTER TABLE parceiro_papel FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- parceiro row's own RLS policy, matched by parceiro_id. This keeps the
-- Hibernate @ElementCollection mapping on Parceiro.papeis simple (just
-- parceiro_id + papel, nothing extra for the app to populate on insert).
CREATE POLICY parceiro_papel_tenant_isolation ON parceiro_papel
    USING (EXISTS (
        SELECT 1 FROM parceiro p
        WHERE p.id = parceiro_papel.parceiro_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));


CREATE TABLE parceiro_contato (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parceiro_id UUID NOT NULL REFERENCES parceiro(id) ON DELETE CASCADE,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    telefone_comercial VARCHAR(20),
    telefone_celular VARCHAR(20),
    cargo VARCHAR(100)
);

CREATE INDEX idx_parceiro_contato_parceiro_id ON parceiro_contato(parceiro_id);

ALTER TABLE parceiro_contato ENABLE ROW LEVEL SECURITY;
ALTER TABLE parceiro_contato FORCE ROW LEVEL SECURITY;

CREATE POLICY parceiro_contato_tenant_isolation ON parceiro_contato
    USING (EXISTS (
        SELECT 1 FROM parceiro p
        WHERE p.id = parceiro_contato.parceiro_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
