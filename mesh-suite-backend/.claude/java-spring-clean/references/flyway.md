# Flyway — Convenções e Exemplos DDL (PostgreSQL)

## Convenção de Nomenclatura

```
db/migration/
├── V1__create_schema_initial.sql
├── V2__create_table_categories.sql
├── V3__create_table_products.sql
├── V4__add_index_products_name.sql
└── V5__alter_products_add_metadata.sql
```

**Regras:**
- Prefixo `V` + número sequencial + `__` (dois underscores) + descrição em snake_case
- Nunca editar uma migration já aplicada em produção — criar nova
- Uma migration por concern lógico (não misturar tabelas não relacionadas)
- Sempre incluir `NOT NULL` + `DEFAULT` onde aplicável

---

## Exemplos DDL

### Tabela base com UUID e auditoria

```sql
-- V2__create_table_categories.sql
CREATE TABLE categories (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE INDEX idx_categories_name ON categories (name);
```

### Tabela com FK

```sql
-- V3__create_table_products.sql
CREATE TABLE products (
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(200)   NOT NULL,
    description TEXT,
    price       NUMERIC(10, 2) NOT NULL,
    stock       INTEGER        NOT NULL DEFAULT 0,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    category_id UUID           NOT NULL,
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_products       PRIMARY KEY (id),
    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT chk_products_price  CHECK (price >= 0),
    CONSTRAINT chk_products_stock  CHECK (stock >= 0)
);

CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_name        ON products (name);
CREATE INDEX idx_products_active      ON products (active) WHERE active = TRUE;
```

### Coluna JSONB

```sql
-- V5__alter_products_add_metadata.sql
ALTER TABLE products
    ADD COLUMN metadata JSONB;

CREATE INDEX idx_products_metadata ON products USING GIN (metadata);
```

### Tabela de relacionamento N:N

```sql
-- V6__create_table_product_tags.sql
CREATE TABLE tags (
    id   UUID        NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_tags      PRIMARY KEY (id),
    CONSTRAINT uq_tags_name UNIQUE (name)
);

CREATE TABLE product_tags (
    product_id UUID NOT NULL,
    tag_id     UUID NOT NULL,
    CONSTRAINT pk_product_tags PRIMARY KEY (product_id, tag_id),
    CONSTRAINT fk_pt_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_pt_tag     FOREIGN KEY (tag_id)     REFERENCES tags(id)     ON DELETE CASCADE
);
```

### Trigger de updated_at (PostgreSQL)

```sql
-- V7__add_updated_at_trigger.sql
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_categories_updated_at
    BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
```

---

## Boas Práticas

| ✅ Fazer                                        | ❌ Evitar                              |
|-------------------------------------------------|----------------------------------------|
| `gen_random_uuid()` para UUIDs (nativo PG 13+)  | `uuid-ossp` extensão desnecessária     |
| Nomear constraints explicitamente               | Deixar o banco nomear automaticamente  |
| Index para FKs e campos filtrados               | Index em todo campo sem critério       |
| `NUMERIC(p, s)` para dinheiro                   | `FLOAT` ou `DOUBLE` para valores financeiros |
| `TIMESTAMP` sem timezone (UTC no app)           | `TIMESTAMP WITH TIME ZONE` sem controle |
| Migrations pequenas e atômicas                  | Migrations enormes com ALTER TABLE em produção |
