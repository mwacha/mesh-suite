ALTER TABLE product DROP COLUMN categoria;
ALTER TABLE product ADD COLUMN category_id UUID REFERENCES category(id);
