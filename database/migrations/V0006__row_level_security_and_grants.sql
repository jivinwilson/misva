CREATE FUNCTION app_business_id() RETURNS uuid
    LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('app.business_id', true), '')::uuid $$;

CREATE FUNCTION app_user_id() RETURNS uuid
    LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('app.user_id', true), '')::uuid $$;

-- businesses
ALTER TABLE businesses ENABLE ROW LEVEL SECURITY;
CREATE POLICY businesses_select ON businesses FOR SELECT USING (
    id = app_business_id()
    OR id IN (SELECT business_id FROM business_memberships WHERE user_id = app_user_id()));
CREATE POLICY businesses_insert ON businesses FOR INSERT WITH CHECK (id = app_business_id());
CREATE POLICY businesses_update ON businesses FOR UPDATE
    USING (id = app_business_id()) WITH CHECK (id = app_business_id());

-- strictly tenant-scoped tables
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['branches', 'employee_profiles', 'membership_roles', 'branch_access',
                             'subscriptions', 'business_entitlement_overrides']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY %I ON %I USING (business_id = app_business_id()) WITH CHECK (business_id = app_business_id())',
                       t || '_tenant', t);
    END LOOP;
END $$;

-- memberships: visible to the business, and to the user they belong to
ALTER TABLE business_memberships ENABLE ROW LEVEL SECURITY;
CREATE POLICY memberships_select ON business_memberships FOR SELECT
    USING (business_id = app_business_id() OR user_id = app_user_id());
CREATE POLICY memberships_insert ON business_memberships FOR INSERT
    WITH CHECK (business_id = app_business_id());
CREATE POLICY memberships_update ON business_memberships FOR UPDATE
    USING (business_id = app_business_id()) WITH CHECK (business_id = app_business_id());

-- roles: system templates (business_id IS NULL) are readable by everyone, writable by no tenant
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
CREATE POLICY roles_select ON roles FOR SELECT
    USING (business_id IS NULL OR business_id = app_business_id());
CREATE POLICY roles_insert ON roles FOR INSERT WITH CHECK (business_id = app_business_id());
CREATE POLICY roles_update ON roles FOR UPDATE
    USING (business_id = app_business_id()) WITH CHECK (business_id = app_business_id());
CREATE POLICY roles_delete ON roles FOR DELETE USING (business_id = app_business_id());

ALTER TABLE role_permissions ENABLE ROW LEVEL SECURITY;
CREATE POLICY role_permissions_select ON role_permissions FOR SELECT
    USING (business_id IS NULL OR business_id = app_business_id());
CREATE POLICY role_permissions_insert ON role_permissions FOR INSERT
    WITH CHECK (business_id = app_business_id());
CREATE POLICY role_permissions_delete ON role_permissions FOR DELETE
    USING (business_id = app_business_id());

-- sessions belong to a user
ALTER TABLE auth_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY auth_sessions_user ON auth_sessions
    USING (user_id = app_user_id()) WITH CHECK (user_id = app_user_id());

-- audit: tenants read their own events and append; platform events (NULL business) are not tenant-readable
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_events_select ON audit_events FOR SELECT USING (business_id = app_business_id());
CREATE POLICY audit_events_insert ON audit_events FOR INSERT
    WITH CHECK (business_id IS NULL OR business_id = app_business_id());

-- grants: global reference data is read-only for the application
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON industry_types, permissions, plans, plan_entitlements FROM misva_app;
-- no hard deletes of core records, audit is append-only
REVOKE DELETE, TRUNCATE ON businesses, branches, users, business_memberships, employee_profiles,
    subscriptions FROM misva_app;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_events FROM misva_app;
REVOKE UPDATE ON role_permissions, membership_roles, branch_access FROM misva_app;
REVOKE EXECUTE ON FUNCTION touch_updated_at, forbid_industry_change, forbid_audit_mutation,
    sync_role_permission_business, check_membership_role_scope FROM PUBLIC;
