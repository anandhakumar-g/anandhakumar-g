-- ============================================================================
-- Row-Level Security for tenant-scoped tables.
--
-- The application connects as a non-superuser role (singlepoint_app) and sets
--   SET app.current_tenant_id = '<tenant-uuid>'   -- normal tenant request
--   SET app.current_tenant_id = '*'               -- Super Admin (cross-tenant)
-- per pooled connection (see TenantConnectionDataSource). When the GUC is unset,
-- current_setting(...,true) returns NULL and every policy denies -> fail closed.
-- FORCE is used so the table owner is subject to the policy too.
-- ============================================================================

DO $$
DECLARE t text;
BEGIN
    -- user_tenant_membership and tenant_service_provider are deliberately NOT here: they are
    -- read across tenant boundaries during login / onboarding / portability, and are scoped
    -- at the app layer (every query filters by user_id / provider_id / the caller's tenant_id).
    -- See V4 + docs/decisions.md ADR-011.
    FOREACH t IN ARRAY ARRAY[
        'flat',
        'invite_code',
        'ticket',
        'ticket_attachment',
        'ticket_status_history'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS %I_tenant_isolation ON %I', t, t);
        EXECUTE format($f$
            CREATE POLICY %I_tenant_isolation ON %I
            USING (
                current_setting('app.current_tenant_id', true) = '*'
                OR tenant_id::text = current_setting('app.current_tenant_id', true)
            )
            WITH CHECK (
                current_setting('app.current_tenant_id', true) = '*'
                OR tenant_id::text = current_setting('app.current_tenant_id', true)
            )
        $f$, t, t);
    END LOOP;
END $$;
