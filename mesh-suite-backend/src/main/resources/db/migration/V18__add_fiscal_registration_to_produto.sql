ALTER TABLE product ADD COLUMN fiscal_registration_id UUID REFERENCES fiscal_registration(id);
