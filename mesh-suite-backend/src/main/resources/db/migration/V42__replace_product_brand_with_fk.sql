ALTER TABLE product DROP COLUMN brand;
ALTER TABLE product ADD COLUMN brand_id UUID REFERENCES brand(id);
