CREATE TABLE permission_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    code VARCHAR(20),
    is_system BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_permission_profile_tenant_name ON permission_profile(tenant_id, name);
CREATE INDEX idx_permission_profile_tenant_id ON permission_profile(tenant_id);
CREATE UNIQUE INDEX idx_permission_profile_tenant_code ON permission_profile(tenant_id, code) WHERE code IS NOT NULL;

ALTER TABLE permission_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE permission_profile FORCE ROW LEVEL SECURITY;

CREATE POLICY permission_profile_tenant_isolation ON permission_profile
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE permission_profile_grant (
    permission_profile_id UUID NOT NULL REFERENCES permission_profile(id) ON DELETE CASCADE,
    module VARCHAR(20) NOT NULL
        CHECK (module IN ('CUSTOMER','PRODUCT','ORDER','USER','PURCHASE','STOCK','PAYABLE','SALE','PURCHASE_INVOICE')),
    action VARCHAR(10) NOT NULL CHECK (action IN ('VIEW','CREATE','EDIT','DELETE')),
    PRIMARY KEY (permission_profile_id, module, action)
);

ALTER TABLE permission_profile_grant ENABLE ROW LEVEL SECURITY;
ALTER TABLE permission_profile_grant FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- permission_profile row's own RLS policy, matched by permission_profile_id.
-- Same pattern as user_permission/partner_role.
CREATE POLICY permission_profile_grant_tenant_isolation ON permission_profile_grant
    USING (EXISTS (
        SELECT 1 FROM permission_profile pp
        WHERE pp.id = permission_profile_grant.permission_profile_id
          AND pp.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
