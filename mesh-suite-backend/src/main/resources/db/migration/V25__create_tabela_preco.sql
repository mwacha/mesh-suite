CREATE TABLE tabela_preco (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    modo_selecao_produtos VARCHAR(20) NOT NULL
        CHECK (modo_selecao_produtos IN ('TODOS_PRODUTOS','SELECIONAR_PRODUTOS')),
    metodo_ajuste VARCHAR(10) NOT NULL CHECK (metodo_ajuste IN ('AUTOMATICO','MANUAL')),
    operacao_ajuste VARCHAR(10) CHECK (operacao_ajuste IN ('SOMAR','SUBTRAIR')),
    tipo_valor_ajuste VARCHAR(12) CHECK (tipo_valor_ajuste IN ('REAL','PERCENTUAL')),
    valor_ajuste NUMERIC(12,2),
    arredondamento VARCHAR(20) NOT NULL
        CHECK (arredondamento IN ('NAO_ARREDONDAR','TERMINAR_EM_0','TERMINAR_EM_9','TERMINAR_EM_90','TERMINAR_EM_99')),
    inicio_vigencia DATE NOT NULL,
    termino_vigencia DATE,
    valor_minimo_venda NUMERIC(12,2),
    percentual_comissao_padrao NUMERIC(5,2),
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_tabela_preco_tenant_nome ON tabela_preco(tenant_id, nome);
CREATE INDEX idx_tabela_preco_tenant_id ON tabela_preco(tenant_id);

ALTER TABLE tabela_preco ENABLE ROW LEVEL SECURITY;
ALTER TABLE tabela_preco FORCE ROW LEVEL SECURITY;

CREATE POLICY tabela_preco_tenant_isolation ON tabela_preco
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE tabela_preco_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tabela_preco_id UUID NOT NULL REFERENCES tabela_preco(id) ON DELETE CASCADE,
    produto_id UUID NOT NULL REFERENCES produto(id),
    preco_nesta_tabela NUMERIC(12,2),
    percentual_comissao NUMERIC(5,2)
);

CREATE INDEX idx_tabela_preco_item_tabela_preco_id ON tabela_preco_item(tabela_preco_id);

ALTER TABLE tabela_preco_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE tabela_preco_item FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- tabela_preco row's own RLS policy, matched by tabela_preco_id. Same
-- pattern as purchase_order_item.
CREATE POLICY tabela_preco_item_tenant_isolation ON tabela_preco_item
    USING (EXISTS (
        SELECT 1 FROM tabela_preco tp
        WHERE tp.id = tabela_preco_item.tabela_preco_id
          AND tp.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
