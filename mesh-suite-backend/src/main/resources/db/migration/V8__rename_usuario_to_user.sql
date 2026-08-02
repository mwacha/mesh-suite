ALTER TABLE usuario RENAME TO app_user;
ALTER TABLE app_user RENAME COLUMN nome TO name;
ALTER TABLE app_user RENAME COLUMN senha_hash TO password_hash;
ALTER TABLE app_user RENAME COLUMN papel TO role;
ALTER TABLE app_user RENAME COLUMN ativo TO active;
ALTER TABLE app_user RENAME COLUMN criado_em TO created_at;
ALTER TABLE app_user RENAME COLUMN ultimo_acesso TO last_access_at;

ALTER TABLE app_user DROP CONSTRAINT usuario_papel_check;

UPDATE app_user SET role = CASE role
    WHEN 'ADMINISTRATIVO' THEN 'ADMINISTRATIVE'
    WHEN 'REPRESENTANTE' THEN 'SALES_REP'
    WHEN 'PRODUCAO' THEN 'PRODUCTION'
    WHEN 'TERCEIRIZADO' THEN 'OUTSOURCED'
    WHEN 'ADMINISTRADOR' THEN 'ADMIN'
END;

ALTER TABLE app_user ADD CONSTRAINT app_user_role_check
    CHECK (role IN ('ADMINISTRATIVE','SALES_REP','PRODUCTION','OUTSOURCED','ADMIN'));

ALTER INDEX idx_usuario_tenant_id RENAME TO idx_app_user_tenant_id;
ALTER INDEX idx_usuario_email RENAME TO idx_app_user_email;

ALTER POLICY usuario_tenant_isolation ON app_user RENAME TO app_user_tenant_isolation;
ALTER POLICY usuario_login_lookup ON app_user RENAME TO app_user_login_lookup;

-- password_reset_token.usuario_id -> user_id: this column's own FK target table
-- was renamed above, and PasswordResetService (Task 2) is being edited in this
-- same rename pass to call token.setUserId(...)/getUserId() instead of
-- setUsuarioId(...)/getUsuarioId() -- renaming the column now keeps the DB and
-- the Java field name in sync from the same commit.
ALTER TABLE password_reset_token RENAME COLUMN usuario_id TO user_id;
ALTER INDEX idx_password_reset_token_usuario_id RENAME TO idx_password_reset_token_user_id;
