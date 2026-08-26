-- Each VARIATION_CHILD's own coordinate in the parent's Tipos de Variação matrix,
-- e.g. ["40","VERMELHA"], stored as a JSON array of strings in the same order as
-- the parent's variation_axes. Without it a matrix value that isn't backed by a
-- real cadastro (only "Tamanho" maps to product.size; "COR" and any custom axis
-- have nowhere to live) is unrecoverable on reload, so the child can't be matched
-- back to the combination that generated it.
ALTER TABLE product ADD COLUMN variation_values TEXT;
