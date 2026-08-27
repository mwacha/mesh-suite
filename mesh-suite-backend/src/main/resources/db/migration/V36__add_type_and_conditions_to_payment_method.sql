ALTER TABLE payment_method
    ADD COLUMN type VARCHAR(20),
    ADD COLUMN notes VARCHAR(255),
    ADD COLUMN max_installments INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN interest_rate NUMERIC(5,2),
    ADD COLUMN settlement_days INTEGER;

-- Migrations run with no app.tenant_id set, so the tenant-isolation RLS policy
-- hides every row from the backfill below -- a plain UPDATE would silently
-- affect zero rows. meshsuite_app owns the table, so it can toggle RLS off for
-- just these statements; FORCE is restored immediately after. Same pattern as
-- V8__rename_usuario_to_user.sql.
ALTER TABLE payment_method DISABLE ROW LEVEL SECURITY;

-- Linhas já cadastradas não tinham tipo. Deduz pelo nome quando dá para
-- reconhecer; o que não casar fica NULL e aparece como "—" na listagem até
-- alguém editar a forma de pagamento (o cadastro passa a exigir o tipo).
UPDATE payment_method SET type = CASE
    WHEN description ILIKE '%pix%' THEN 'PIX'
    WHEN description ILIKE '%boleto%' THEN 'BOLETO'
    WHEN description ILIKE '%cart%' THEN 'CARD'
    WHEN description ILIKE '%duplicata%' THEN 'DUPLICATA'
    WHEN description ILIKE '%transfer%' THEN 'TRANSFER'
    WHEN description ILIKE '%dinheiro%' OR description ILIKE '%vista%' THEN 'CASH'
END;

-- Quem já tem parcelas cadastradas passa a ter max_installments coerente com
-- elas; o default 1 cobre o resto.
UPDATE payment_method pm SET max_installments = GREATEST(1, (
    SELECT COUNT(*) FROM payment_method_installment i WHERE i.payment_method_id = pm.id
));

ALTER TABLE payment_method ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_method FORCE ROW LEVEL SECURITY;

ALTER TABLE payment_method ADD CONSTRAINT payment_method_type_check
    CHECK (type IS NULL OR type IN ('CASH', 'CARD', 'BOLETO', 'PIX', 'DUPLICATA', 'TRANSFER'));

ALTER TABLE payment_method ADD CONSTRAINT payment_method_max_installments_check
    CHECK (max_installments >= 1);

ALTER TABLE payment_method ADD CONSTRAINT payment_method_interest_rate_check
    CHECK (interest_rate IS NULL OR interest_rate >= 0);

ALTER TABLE payment_method ADD CONSTRAINT payment_method_settlement_days_check
    CHECK (settlement_days IS NULL OR settlement_days >= 0);
