ALTER TABLE produto DROP COLUMN categoria;
ALTER TABLE produto ADD COLUMN categoria_id UUID REFERENCES categoria(id);
