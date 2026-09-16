-- Public signup needs to look up an existing Company by CNPJ *before* any tenant_id
-- is known (to detect "this CNPJ already signed up, resend the confirmation" vs.
-- "genuinely new"). company_tenant_isolation alone would hide every row in that
-- state (NULL app.tenant_id never matches). This adds a second PERMISSIVE policy
-- (Postgres ORs permissive policies for the same command), scoped to SELECT only,
-- gated by the same app.bypass_tenant_check flag AuthService.findAllByEmailForLogin
-- already uses for the equivalent app_user case (see app_user_login_lookup in
-- V3__create_usuario.sql).
CREATE POLICY company_signup_lookup ON company
    FOR SELECT
    USING (current_setting('app.bypass_tenant_check', true) = 'true');
