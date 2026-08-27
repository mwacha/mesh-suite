-- Bulk test data for the Marcas list screen -- enough rows to exercise
-- pagination/search and a mix of active/inactive status. Dev/test profile
-- only (see application.yml's flyway.locations override).
SET LOCAL app.tenant_id = '11111111-1111-1111-1111-111111111111';

INSERT INTO brand (tenant_id, name, active) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Aurora Denim', true),
    ('11111111-1111-1111-1111-111111111111', 'Boreal Sport', true),
    ('11111111-1111-1111-1111-111111111111', 'Vento Norte', true),
    ('11111111-1111-1111-1111-111111111111', 'Malha Fina', true),
    ('11111111-1111-1111-1111-111111111111', 'Tramas Urbanas', true),
    ('11111111-1111-1111-1111-111111111111', 'Costura Nobre', true),
    ('11111111-1111-1111-1111-111111111111', 'Fio de Ouro', true),
    ('11111111-1111-1111-1111-111111111111', 'Rota 47', true),
    ('11111111-1111-1111-1111-111111111111', 'Estampa Livre', false),
    ('11111111-1111-1111-1111-111111111111', 'Alma Têxtil', true)
ON CONFLICT (tenant_id, name) DO NOTHING;
