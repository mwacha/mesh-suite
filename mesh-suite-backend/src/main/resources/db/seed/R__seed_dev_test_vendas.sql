-- Bulk test data to feed the Dashboard/Home screen: sales reps, payment terms
-- (condições de recebimento), products, and sales orders spread across the
-- current month + the previous 11 months with varied statuses -- enough for
-- the KPI cards, the "Pedidos por Período" chart (both toggle positions), and
-- the "Últimos Pedidos" table to show real data. Dev/test profile only (see
-- application.yml's flyway.locations override).
--
-- Everything below depends on R__seed_dev_test_clientes.sql's 62 "Cliente
-- Teste NNN" customers, so it's bundled into ONE file rather than split across
-- several: Flyway orders repeatable migrations alphabetically by description,
-- and "pedidos" would otherwise sort before "produtos"/"vendedores".
SET LOCAL app.tenant_id = '11111111-1111-1111-1111-111111111111';

-- ── Vendedores (sales reps) ─────────────────────────────────────────────────
-- Password for all three, same as the seeded admins: MeshSuite@123

INSERT INTO app_user (tenant_id, name, email, password_hash, role, profile, active) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Carla Vendedora', 'carla.vendedora@aurora.com.br',
        '$2a$10$gc21cu8nxmoffokwJXbpaeCEhZLVpe1IX/zX0wyFFkx.XnMzBy.IS', 'SALES_REP', 'SALES', true),
    ('11111111-1111-1111-1111-111111111111', 'Roberto Vendas', 'roberto.vendas@aurora.com.br',
        '$2a$10$gc21cu8nxmoffokwJXbpaeCEhZLVpe1IX/zX0wyFFkx.XnMzBy.IS', 'SALES_REP', 'SALES', true),
    ('11111111-1111-1111-1111-111111111111', 'Juliana Comercial', 'juliana.comercial@aurora.com.br',
        '$2a$10$gc21cu8nxmoffokwJXbpaeCEhZLVpe1IX/zX0wyFFkx.XnMzBy.IS', 'SALES_REP', 'SALES', true)
ON CONFLICT (tenant_id, email) DO NOTHING;

INSERT INTO user_permission (user_id, module, action)
SELECT u.id, m, a
FROM app_user u
CROSS JOIN unnest(ARRAY['ORDER','CUSTOMER']) AS m
CROSS JOIN unnest(ARRAY['VIEW','CREATE','EDIT']) AS a
WHERE u.tenant_id = '11111111-1111-1111-1111-111111111111' AND u.role = 'SALES_REP'
ON CONFLICT DO NOTHING;

INSERT INTO user_permission (user_id, module, action)
SELECT u.id, 'PRODUCT', 'VIEW'
FROM app_user u
WHERE u.tenant_id = '11111111-1111-1111-1111-111111111111' AND u.role = 'SALES_REP'
ON CONFLICT DO NOTHING;

-- ── Condições de recebimento (payment methods) ──────────────────────────────

INSERT INTO payment_method (tenant_id, description, active, type, max_installments, settlement_days) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Pix', true, 'PIX', 1, 0),
    ('11111111-1111-1111-1111-111111111111', 'Dinheiro', true, 'CASH', 1, 0),
    ('11111111-1111-1111-1111-111111111111', 'Boleto 30/60/90', true, 'BOLETO', 3, 30),
    ('11111111-1111-1111-1111-111111111111', 'Cartão de Crédito 3x', true, 'CARD', 3, 30)
ON CONFLICT (tenant_id, description) DO NOTHING;

INSERT INTO payment_method_installment (payment_method_id, installment_number, days_due, percentage)
SELECT pm.id, i.n, i.n * 30, CASE WHEN i.n = 1 THEN 33.34 ELSE 33.33 END
FROM payment_method pm
CROSS JOIN generate_series(1, 3) AS i(n)
WHERE pm.tenant_id = '11111111-1111-1111-1111-111111111111' AND pm.description = 'Boleto 30/60/90'
  AND NOT EXISTS (SELECT 1 FROM payment_method_installment WHERE payment_method_id = pm.id);

-- Spread payment terms across the seeded customers for a touch of realism
-- (not required by any order below, which settles instantly on INVOICED).
UPDATE partner p SET payment_method_id = pm.id
FROM (
    SELECT id, row_number() OVER (ORDER BY trade_name) AS rn
    FROM partner
    WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND trade_name LIKE 'Cliente Teste %'
) c
JOIN LATERAL (
    SELECT id FROM payment_method
    WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
    ORDER BY description OFFSET (c.rn % 4) LIMIT 1
) pm ON true
WHERE p.id = c.id AND p.payment_method_id IS NULL;

-- ── Produtos ─────────────────────────────────────────────────────────────
-- product.brand was replaced by product.brand_id (FK to brand) in
-- V42__replace_product_brand_with_fk.sql -- these three names need to exist
-- as real brand rows before the products below can reference them.
INSERT INTO brand (tenant_id, name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Aurora Basics'),
    ('11111111-1111-1111-1111-111111111111', 'Aurora Denim'),
    ('11111111-1111-1111-1111-111111111111', 'Aurora Feminina')
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO product (tenant_id, name, sku, brand_id, sale_price, status, stock_quantity, measurement_unit)
SELECT '11111111-1111-1111-1111-111111111111', v.name, v.sku,
       (SELECT id FROM brand WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND name = v.brand_name),
       v.sale_price, 'ACTIVE', v.stock_quantity, 'UN'
FROM (VALUES
    ('Camiseta Polo', 'SEED-CAMPOLO', 'Aurora Basics', 59.90, 180),
    ('Camiseta Regata', 'SEED-CAMREGATA', 'Aurora Basics', 39.90, 220),
    ('Calça Jeans', 'SEED-CALJEANS', 'Aurora Denim', 119.90, 95),
    ('Bermuda Sarja', 'SEED-BERSARJA', 'Aurora Denim', 79.90, 140),
    ('Vestido Floral', 'SEED-VESFLORAL', 'Aurora Feminina', 149.90, 60),
    ('Jaqueta Jeans', 'SEED-JAQJEANS', 'Aurora Denim', 199.90, 40)
) AS v(name, sku, brand_name, sale_price, stock_quantity)
ON CONFLICT (tenant_id, sku) DO NOTHING;

-- ── Pedidos ──────────────────────────────────────────────────────────────
-- Monthly volume is deliberately uneven (metas_mensais.qtd), not a flat
-- count -- a real business has slow and busy months, and a flat count made
-- the "Últimos 12 Meses" chart look like a straight line.
--
-- This migration is edited/re-run repeatedly during development, so it's
-- self-replacing rather than purely additive: it first deletes whatever
-- orders IT previously seeded (identified by referencing a SEED-% product,
-- which only these generated orders ever use -- see items insert below) and
-- regenerates from scratch. Cascades to their items; anything a human
-- created by hand through the app (a different product) is left untouched.
DELETE FROM sales_order so
WHERE so.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND EXISTS (
      SELECT 1 FROM sales_order_item soi
      JOIN product p ON p.id = soi.product_id
      WHERE soi.sales_order_id = so.id AND p.sku LIKE 'SEED-%'
  );

WITH metas_mensais(m, qtd) AS (
    -- m=1..11 count back from the current month. qtd varies deliberately
    -- (3..13) instead of a flat per-month count -- this only drives the
    -- MONTHLY total (the "Últimos 12 Meses" chart aggregates by month), so
    -- how its orders land on individual days doesn't need to vary too.
    VALUES (1, 9), (2, 5), (3, 11), (4, 7), (5, 3),
           (6, 8), (7, 13), (8, 6), (9, 10), (10, 4), (11, 7)
),
-- The current month's chart ("Mês Corrente") plots one point PER DAY, so
-- unlike the months above, evenly spreading N orders one-per-day here would
-- make every non-zero day land on the exact same count (1) -- every point
-- but the zeros would render at identical height, reading as "no scale".
-- Explicit varied per-day quantities (1..6) instead. dia <= today keeps this
-- valid no matter which day of the month the migration runs on.
metas_diarias(dia, qtd) AS (
    VALUES (1, 2), (3, 1), (4, 4), (6, 1), (8, 3), (10, 1), (11, 5), (13, 2), (15, 1),
           (17, 3), (19, 1), (20, 4), (22, 1), (24, 2), (25, 6), (27, 3)
),
pedidos_mes_corrente AS (
    SELECT (date_trunc('month', CURRENT_DATE)::date + (interval '1 day' * (md.dia - 1)))::date AS order_date
    FROM metas_diarias md
    CROSS JOIN LATERAL generate_series(1, md.qtd) AS g
    WHERE md.dia <= EXTRACT(DAY FROM CURRENT_DATE)::int
),
pedidos_historicos AS (
    -- Spread across day 2..27 (safe for every month length).
    SELECT (date_trunc('month', CURRENT_DATE)::date - (interval '1 month' * mm.m) + (interval '1 day' *
            (1 + round(k * 25.0 / GREATEST(mm.qtd - 1, 1))::int)))::date AS order_date
    FROM metas_mensais mm
    CROSS JOIN LATERAL generate_series(0, mm.qtd - 1) AS k
),
pedidos_datas AS (
    SELECT order_date FROM pedidos_mes_corrente
    UNION ALL
    SELECT order_date FROM pedidos_historicos
),
pedidos_numerados AS (
    SELECT
        row_number() OVER (ORDER BY order_date) AS numero,
        row_number() OVER (ORDER BY order_date) - 1 AS idx,
        order_date
    FROM pedidos_datas
)
INSERT INTO sales_order (tenant_id, number, customer_id, salesperson_id, order_date, status, discount, subtotal, total)
SELECT
    '11111111-1111-1111-1111-111111111111',
    pn.numero,
    (SELECT id FROM partner
     WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND trade_name LIKE 'Cliente Teste %'
     ORDER BY trade_name OFFSET (pn.idx % 62) LIMIT 1),
    (SELECT id FROM app_user
     WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND role = 'SALES_REP'
     ORDER BY email OFFSET (pn.idx % 3) LIMIT 1),
    pn.order_date,
    CASE pn.idx % 3 WHEN 0 THEN 'DRAFT' WHEN 1 THEN 'IN_PREPARATION' ELSE 'INVOICED' END,
    0,
    (1 + (pn.idx % 5)) * (SELECT sale_price FROM product
        WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND sku LIKE 'SEED-%'
        ORDER BY sku OFFSET (pn.idx % 6) LIMIT 1),
    (1 + (pn.idx % 5)) * (SELECT sale_price FROM product
        WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND sku LIKE 'SEED-%'
        ORDER BY sku OFFSET (pn.idx % 6) LIMIT 1)
FROM pedidos_numerados pn
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO sales_order_item (sales_order_id, product_id, quantity, unit_price, total_amount)
SELECT
    so.id,
    prod.id,
    1 + ((so.number - 1) % 5),
    prod.sale_price,
    (1 + ((so.number - 1) % 5)) * prod.sale_price
FROM sales_order so
CROSS JOIN LATERAL (
    SELECT id, sale_price FROM product
    WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND sku LIKE 'SEED-%'
    ORDER BY sku OFFSET ((so.number - 1) % 6) LIMIT 1
) prod
WHERE so.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND NOT EXISTS (SELECT 1 FROM sales_order_item WHERE sales_order_id = so.id);

-- Keep the tenant's number sequence past every seeded order, so the next
-- real order created through the app doesn't collide with one seeded here.
-- GREATEST guards against moving the counter backwards on a re-run after
-- real orders have already advanced it further.
INSERT INTO sales_order_counter (tenant_id, next_number)
SELECT '11111111-1111-1111-1111-111111111111', COALESCE(MAX(number), 0) + 1
FROM sales_order
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
ON CONFLICT (tenant_id) DO UPDATE SET next_number = GREATEST(sales_order_counter.next_number, EXCLUDED.next_number);
