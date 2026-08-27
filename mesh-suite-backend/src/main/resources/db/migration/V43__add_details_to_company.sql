-- mesh-suite-backend/src/main/resources/db/migration/V43__add_details_to_company.sql
-- Column lengths mirror partner's equivalents (V5__create_partner.sql) for consistency.
ALTER TABLE company
    ADD COLUMN trade_name             VARCHAR(255),
    ADD COLUMN state_registration     VARCHAR(20),
    ADD COLUMN municipal_registration VARCHAR(20),
    ADD COLUMN phone                  VARCHAR(20),
    ADD COLUMN email                  VARCHAR(255),
    ADD COLUMN website                VARCHAR(255),
    ADD COLUMN zip_code               VARCHAR(8),
    ADD COLUMN street                 VARCHAR(255),
    ADD COLUMN number                 VARCHAR(20),
    ADD COLUMN complement             VARCHAR(100),
    ADD COLUMN neighborhood           VARCHAR(100),
    ADD COLUMN city                   VARCHAR(100),
    ADD COLUMN state                  VARCHAR(2);
