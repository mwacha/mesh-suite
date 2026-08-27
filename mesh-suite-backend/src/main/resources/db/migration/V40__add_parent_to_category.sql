ALTER TABLE category ADD COLUMN parent_id UUID REFERENCES category(id);

CREATE INDEX idx_category_parent_id ON category(parent_id);

-- Categorias filhas só podem ter uma categoria raiz como pai (aplicado em
-- CategoryService); este check só cobre o caso trivial de autorreferência.
ALTER TABLE category ADD CONSTRAINT category_parent_not_self CHECK (parent_id IS NULL OR parent_id <> id);
