ALTER TABLE produto ADD COLUMN fiscal_registration_id UUID REFERENCES fiscal_registration(id);
