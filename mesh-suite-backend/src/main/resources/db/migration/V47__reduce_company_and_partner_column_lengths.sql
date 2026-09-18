-- mesh-suite-backend/src/main/resources/db/migration/V47__reduce_company_and_partner_column_lengths.sql
-- Bring identification/address columns down from the generic 255/100 defaults
-- used when these tables were created to sizes based on the format each field
-- actually holds: RFB's own layout for razao_social/nome_fantasia, and the
-- Correios DNE layout for street/neighborhood/city. Column lengths for
-- partner mirror company's, same as the original V43 comment intended.
ALTER TABLE company
    ALTER COLUMN legal_name TYPE VARCHAR(150),
    ALTER COLUMN trade_name TYPE VARCHAR(100),
    ALTER COLUMN street TYPE VARCHAR(100),
    ALTER COLUMN number TYPE VARCHAR(10),
    ALTER COLUMN neighborhood TYPE VARCHAR(60),
    ALTER COLUMN city TYPE VARCHAR(60);

ALTER TABLE partner
    ALTER COLUMN trade_name TYPE VARCHAR(100),
    ALTER COLUMN legal_name TYPE VARCHAR(150),
    ALTER COLUMN street TYPE VARCHAR(100),
    ALTER COLUMN number TYPE VARCHAR(10),
    ALTER COLUMN neighborhood TYPE VARCHAR(60),
    ALTER COLUMN city TYPE VARCHAR(60);

ALTER TABLE partner_contact
    ALTER COLUMN name TYPE VARCHAR(100),
    ALTER COLUMN job_title TYPE VARCHAR(60);
