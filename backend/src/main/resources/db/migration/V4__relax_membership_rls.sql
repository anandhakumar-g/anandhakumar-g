-- Drop RLS from tables that were enabled in an earlier V2 revision but are read across
-- tenant boundaries during login / onboarding / portability. Access is scoped at the
-- application layer instead (every query filters by user_id / provider_id / the caller's
-- own tenant_id). The sensitive ticket-domain tables (flat, invite_code, ticket,
-- ticket_attachment, ticket_status_history) keep RLS. See docs/decisions.md ADR-011.

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['user_tenant_membership', 'tenant_service_provider']
    LOOP
        EXECUTE format('ALTER TABLE %I NO FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I DISABLE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS %I_tenant_isolation ON %I', t, t);
    END LOOP;
END $$;
