CREATE TABLE user_permission (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    module VARCHAR(20) NOT NULL CHECK (module IN ('CUSTOMER','PRODUCT','ORDER','USER')),
    action VARCHAR(10) NOT NULL CHECK (action IN ('VIEW','CREATE','EDIT','DELETE')),
    PRIMARY KEY (user_id, module, action)
);

ALTER TABLE user_permission ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_permission FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent app_user
-- row's own RLS policy, matched by user_id. Same pattern as partner_role.
CREATE POLICY user_permission_tenant_isolation ON user_permission
    USING (EXISTS (
        SELECT 1 FROM app_user u
        WHERE u.id = user_permission.user_id
          AND u.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
