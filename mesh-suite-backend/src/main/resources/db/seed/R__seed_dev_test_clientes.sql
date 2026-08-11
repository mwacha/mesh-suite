-- Bulk test data for the Clientes list screen -- enough rows/variety (UF,
-- cidade, status, tipo de documento) to exercise pagination (numbered pages,
-- page-size selector) and the "Mais filtros" multi-select panel. Dev/test
-- profile only (see application.yml's flyway.locations override).
--
-- Deterministic documentos in the 900... range (unlikely to collide with
-- other seed/test data) + ON CONFLICT DO NOTHING on the tenant+document
-- unique index make this safe to re-run when Flyway detects a checksum
-- change on this repeatable migration.
SET LOCAL app.tenant_id = '11111111-1111-1111-1111-111111111111';

-- Two separate statements (not one writable-CTE chaining both inserts):
-- a single statement shares one snapshot across all its parts, so the
-- partner_role RLS policy's cross-table EXISTS check can't see partner
-- rows inserted earlier in that same statement. Splitting them lets the
-- second statement start a fresh snapshot (read committed) that does.
WITH origem AS (
    SELECT
        n,
        (ARRAY['SP','SP','SP','RJ','RJ','MG','MG','PR','RS','BA','PE','CE','DF','SC','GO'])[1 + (n % 15)] AS state,
        (ARRAY['São Paulo','Campinas','Santos','Rio de Janeiro','Niterói','Belo Horizonte','Bicas',
               'Curitiba','Porto Alegre','Salvador','Recife','Fortaleza','Brasília','Florianópolis','Goiânia']
        )[1 + (n % 15)] AS city,
        CASE WHEN n % 2 = 0 THEN 'LEGAL_ENTITY' ELSE 'INDIVIDUAL' END AS person_type,
        CASE
            WHEN n % 5 = 0 THEN 'BLOCKED'
            WHEN n % 5 = 1 THEN 'AT_RISK'
            ELSE 'ACTIVE'
        END AS status
    FROM generate_series(1, 62) AS n
)
INSERT INTO partner (
    tenant_id, person_type, document, trade_name, legal_name, status,
    whatsapp, state, city
)
SELECT
    '11111111-1111-1111-1111-111111111111',
    o.person_type,
    CASE WHEN o.person_type = 'LEGAL_ENTITY'
        THEN lpad((90000000000000 + o.n)::text, 14, '0')
        ELSE lpad((90000000000 + o.n)::text, 11, '0')
    END,
    'Cliente Teste ' || lpad(o.n::text, 3, '0'),
    CASE WHEN o.person_type = 'LEGAL_ENTITY' THEN 'Cliente Teste ' || lpad(o.n::text, 3, '0') || ' Ltda' END,
    o.status,
    '(11) 99' || lpad(o.n::text, 3, '0') || '-' || lpad((o.n * 37 % 10000)::text, 4, '0'),
    o.state,
    o.city
FROM origem o
ON CONFLICT (tenant_id, document) DO NOTHING;

INSERT INTO partner_role (partner_id, role)
SELECT id, 'CUSTOMER'
FROM partner
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
  AND trade_name LIKE 'Cliente Teste %'
ON CONFLICT DO NOTHING;
