CREATE TABLE partner (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    person_type VARCHAR(10) NOT NULL CHECK (person_type IN ('INDIVIDUAL','LEGAL_ENTITY')),
    document VARCHAR(14) NOT NULL,
    trade_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255),
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','AT_RISK','BLOCKED')),
    billing_emails VARCHAR(500),
    whatsapp VARCHAR(20),
    tax_indicator VARCHAR(20) CHECK (tax_indicator IN ('NON_TAXPAYER','TAXPAYER','EXEMPT_TAXPAYER')),
    state_registration VARCHAR(20),
    municipal_registration VARCHAR(20),
    suframa_registration VARCHAR(20),
    zip_code VARCHAR(8),
    street VARCHAR(255),
    number VARCHAR(20),
    neighborhood VARCHAR(100),
    complement VARCHAR(100),
    state VARCHAR(2),
    city VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_partner_tenant_document ON partner(tenant_id, document);
CREATE INDEX idx_partner_tenant_id ON partner(tenant_id);

ALTER TABLE partner ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner FORCE ROW LEVEL SECURITY;

CREATE POLICY partner_tenant_isolation ON partner
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);


CREATE TABLE partner_role (
    partner_id UUID NOT NULL REFERENCES partner(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','SUPPLIER','CARRIER')),
    PRIMARY KEY (partner_id, role)
);

ALTER TABLE partner_role ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner_role FORCE ROW LEVEL SECURITY;

-- No tenant_id column here -- isolation is enforced through the parent
-- partner row's own RLS policy, matched by partner_id. This keeps the
-- Hibernate @ElementCollection mapping on Partner.roles simple (just
-- partner_id + role, nothing extra for the app to populate on insert).
CREATE POLICY partner_role_tenant_isolation ON partner_role
    USING (EXISTS (
        SELECT 1 FROM partner p
        WHERE p.id = partner_role.partner_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));


CREATE TABLE partner_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id UUID NOT NULL REFERENCES partner(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    business_phone VARCHAR(20),
    mobile_phone VARCHAR(20),
    job_title VARCHAR(100)
);

CREATE INDEX idx_partner_contact_partner_id ON partner_contact(partner_id);

ALTER TABLE partner_contact ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner_contact FORCE ROW LEVEL SECURITY;

CREATE POLICY partner_contact_tenant_isolation ON partner_contact
    USING (EXISTS (
        SELECT 1 FROM partner p
        WHERE p.id = partner_contact.partner_id
          AND p.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
