-- Bulk test data for the Clientes list screen -- enough rows/variety (UF,
-- cidade, status, tipo de documento) to exercise pagination (numbered pages,
-- page-size selector) and the "Mais filtros" multi-select panel. Dev/test
-- profile only (see application.yml's flyway.locations override).
--
-- Deterministic documentos in the 900... range (unlikely to collide with
-- other seed/test data) + ON CONFLICT DO NOTHING on the tenant+documento
-- unique index make this safe to re-run when Flyway detects a checksum
-- change on this repeatable migration.
SET LOCAL app.tenant_id = '11111111-1111-1111-1111-111111111111';

-- Two separate statements (not one writable-CTE chaining both inserts):
-- a single statement shares one snapshot across all its parts, so the
-- parceiro_papel RLS policy's cross-table EXISTS check can't see parceiro
-- rows inserted earlier in that same statement. Splitting them lets the
-- second statement start a fresh snapshot (read committed) that does.
WITH origem AS (
    SELECT
        n,
        (ARRAY['SP','SP','SP','RJ','RJ','MG','MG','PR','RS','BA','PE','CE','DF','SC','GO'])[1 + (n % 15)] AS uf,
        (ARRAY['São Paulo','Campinas','Santos','Rio de Janeiro','Niterói','Belo Horizonte','Bicas',
               'Curitiba','Porto Alegre','Salvador','Recife','Fortaleza','Brasília','Florianópolis','Goiânia']
        )[1 + (n % 15)] AS cidade,
        CASE WHEN n % 2 = 0 THEN 'JURIDICA' ELSE 'FISICA' END AS tipo_pessoa,
        CASE
            WHEN n % 5 = 0 THEN 'BLOQUEADO'
            WHEN n % 5 = 1 THEN 'EM_RISCO'
            ELSE 'ATIVO'
        END AS status
    FROM generate_series(1, 62) AS n
)
INSERT INTO parceiro (
    tenant_id, tipo_pessoa, documento, nome_fantasia, razao_social, status,
    whatsapp, uf, cidade
)
SELECT
    '11111111-1111-1111-1111-111111111111',
    o.tipo_pessoa,
    CASE WHEN o.tipo_pessoa = 'JURIDICA'
        THEN lpad((90000000000000 + o.n)::text, 14, '0')
        ELSE lpad((90000000000 + o.n)::text, 11, '0')
    END,
    'Cliente Teste ' || lpad(o.n::text, 3, '0'),
    CASE WHEN o.tipo_pessoa = 'JURIDICA' THEN 'Cliente Teste ' || lpad(o.n::text, 3, '0') || ' Ltda' END,
    o.status,
    '(11) 99' || lpad(o.n::text, 3, '0') || '-' || lpad((o.n * 37 % 10000)::text, 4, '0'),
    o.uf,
    o.cidade
FROM origem o
ON CONFLICT (tenant_id, documento) DO NOTHING;

INSERT INTO parceiro_papel (parceiro_id, papel)
SELECT id, 'CLIENTE'
FROM parceiro
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
  AND nome_fantasia LIKE 'Cliente Teste %'
ON CONFLICT DO NOTHING;
