INSERT INTO tenant (id, codigo, nome, ativo) VALUES
    ('11111111-1111-1111-1111-111111111111', 'aurora', 'Confecção Aurora', true),
    ('22222222-2222-2222-2222-222222222222', 'boreal', 'Confecção Boreal', true);

-- Flyway shares Spring's datasource, which connects as the non-superuser app role
-- (see AbstractIntegrationTest / Task 1's design note), so these INSERTs are subject
-- to RLS like any other write: each row needs app.tenant_id set to its own tenant_id
-- first. Flyway runs a whole migration script in one transaction, so SET LOCAL here
-- stays in effect until superseded by the next one, statement by statement.
SET LOCAL app.tenant_id = '11111111-1111-1111-1111-111111111111';

INSERT INTO empresa (id, tenant_id, razao_social, cnpj, ativo) VALUES
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Confecção Aurora Ltda', '11222333000144', true);

-- Password for both seeded users: MeshSuite@123
INSERT INTO usuario (id, tenant_id, nome, email, senha_hash, papel, ativo) VALUES
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'Marina Aurora', 'marina@aurora.com.br', '$2a$10$gc21cu8nxmoffokwJXbpaeCEhZLVpe1IX/zX0wyFFkx.XnMzBy.IS', 'ADMINISTRADOR', true);

SET LOCAL app.tenant_id = '22222222-2222-2222-2222-222222222222';

INSERT INTO empresa (id, tenant_id, razao_social, cnpj, ativo) VALUES
    ('44444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222', 'Confecção Boreal Ltda', '55666777000188', true);

INSERT INTO usuario (id, tenant_id, nome, email, senha_hash, papel, ativo) VALUES
    ('66666666-6666-6666-6666-666666666666', '22222222-2222-2222-2222-222222222222', 'Carlos Boreal', 'carlos@boreal.com.br', '$2a$10$gc21cu8nxmoffokwJXbpaeCEhZLVpe1IX/zX0wyFFkx.XnMzBy.IS', 'ADMINISTRADOR', true);
