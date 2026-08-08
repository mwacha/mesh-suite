ALTER TABLE produto ADD COLUMN cor_estampa_id UUID REFERENCES cor_estampa(id);
