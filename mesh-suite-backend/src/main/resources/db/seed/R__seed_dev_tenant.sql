INSERT INTO tenant (id, codigo, nome, ativo) VALUES
    ('11111111-1111-1111-1111-111111111111', 'aurora', 'Confecção Aurora', true),
    ('22222222-2222-2222-2222-222222222222', 'boreal', 'Confecção Boreal', true)
ON CONFLICT (id) DO NOTHING;

-- Flyway shares Spring's datasource, which connects as the non-superuser app role
-- (see AbstractIntegrationTest / Task 1's design note), so these INSERTs are subject
-- to RLS like any other write: each row needs app.tenant_id set to its own tenant_id
-- first. Flyway runs a whole migration script in one transaction, so SET LOCAL here
-- stays in effect until superseded by the next one, statement by statement.
SET LOCAL app.tenant_id = '11111111-1111-1111-1111-111111111111';

INSERT INTO company (id, tenant_id, legal_name, cnpj, active) VALUES
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Confecção Aurora Ltda', '11222333000144', true)
ON CONFLICT (id) DO NOTHING;

-- Password for both seeded users: MeshSuite@123
INSERT INTO app_user (id, tenant_id, name, email, password_hash, role, active) VALUES
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'Marina Aurora', 'marina@aurora.com.br', '$2a$10$gc21cu8nxmoffokwJXbpaeCEhZLVpe1IX/zX0wyFFkx.XnMzBy.IS', 'ADMIN', true)
ON CONFLICT (id) DO NOTHING;

-- Full ADMIN permission matrix (every Module x Action except USER+DELETE,
-- which doesn't exist as an operation -- there is no hard delete for User).
-- Matches what a user created through the real UI with profile=ADMIN would
-- receive. Without this, a seeded user predates the permission system (or a
-- Module added after this file was first written) and is silently denied
-- every @RequiresPermission-gated endpoint, including read-only ones like
-- the dashboard's /resumo calls.
INSERT INTO user_permission (user_id, module, action)
SELECT '55555555-5555-5555-5555-555555555555', m, a
FROM unnest(ARRAY['CUSTOMER','PRODUCT','ORDER','USER','PURCHASE','STOCK','PAYABLE','SALE','PURCHASE_INVOICE']) AS m
CROSS JOIN unnest(ARRAY['VIEW','CREATE','EDIT','DELETE']) AS a
WHERE NOT (m = 'USER' AND a = 'DELETE')
ON CONFLICT DO NOTHING;

SET LOCAL app.tenant_id = '22222222-2222-2222-2222-222222222222';

INSERT INTO company (id, tenant_id, legal_name, cnpj, active) VALUES
    ('44444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222', 'Confecção Boreal Ltda', '55666777000188', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (id, tenant_id, name, email, password_hash, role, active) VALUES
    ('66666666-6666-6666-6666-666666666666', '22222222-2222-2222-2222-222222222222', 'Carlos Boreal', 'carlos@boreal.com.br', '$2a$10$gc21cu8nxmoffokwJXbpaeCEhZLVpe1IX/zX0wyFFkx.XnMzBy.IS', 'ADMIN', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_permission (user_id, module, action)
SELECT '66666666-6666-6666-6666-666666666666', m, a
FROM unnest(ARRAY['CUSTOMER','PRODUCT','ORDER','USER','PURCHASE','STOCK','PAYABLE','SALE','PURCHASE_INVOICE']) AS m
CROSS JOIN unnest(ARRAY['VIEW','CREATE','EDIT','DELETE']) AS a
WHERE NOT (m = 'USER' AND a = 'DELETE')
ON CONFLICT DO NOTHING;
