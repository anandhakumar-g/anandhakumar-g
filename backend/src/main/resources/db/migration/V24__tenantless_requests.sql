-- ============================================================================
-- MVP-8 (A): tenant-less service requests. A community-less individual books a
-- verified provider directly; the resulting ticket has tenant_id = NULL.
--
-- The app now also sets a second per-connection GUC, app.current_user_id, on
-- every borrow (TenantAwareDataSource) — '' for system / unauthenticated
-- connections. The RLS policies below gain a null-tenant branch that scopes such
-- rows to their raiser and their assigned provider (via service_provider, which
-- carries no RLS). Existing tenant rows are unaffected: tenant_id IS NULL is
-- false for them, so the pre-existing branch still decides everything.
-- ============================================================================

ALTER TABLE ticket                ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE ticket_status_history ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE ticket_attachment     ALTER COLUMN tenant_id DROP NOT NULL;

-- ---- ticket -----------------------------------------------------------------
DROP POLICY IF EXISTS ticket_tenant_isolation ON ticket;
CREATE POLICY ticket_tenant_isolation ON ticket
    USING (
        current_setting('app.current_tenant_id', true) = '*'
        OR tenant_id::text = current_setting('app.current_tenant_id', true)
        OR (tenant_id IS NULL AND (
                current_setting('app.current_tenant_id', true) = '*'
             OR raised_by_user_id::text = current_setting('app.current_user_id', true)
             OR EXISTS (SELECT 1 FROM service_provider sp
                        WHERE sp.id = ticket.assigned_provider_id
                          AND sp.user_id::text = current_setting('app.current_user_id', true))))
    )
    WITH CHECK (
        current_setting('app.current_tenant_id', true) = '*'
        OR tenant_id::text = current_setting('app.current_tenant_id', true)
        OR (tenant_id IS NULL AND (
                current_setting('app.current_tenant_id', true) = '*'
             OR raised_by_user_id::text = current_setting('app.current_user_id', true)
             OR EXISTS (SELECT 1 FROM service_provider sp
                        WHERE sp.id = ticket.assigned_provider_id
                          AND sp.user_id::text = current_setting('app.current_user_id', true))))
    );

-- ---- ticket_status_history + ticket_attachment -----------------------------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['ticket_status_history', 'ticket_attachment']
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I_tenant_isolation ON %I', t, t);
        EXECUTE format($f$
            CREATE POLICY %1$I_tenant_isolation ON %1$I
            USING (
                current_setting('app.current_tenant_id', true) = '*'
                OR tenant_id::text = current_setting('app.current_tenant_id', true)
                OR (tenant_id IS NULL AND EXISTS (
                    SELECT 1 FROM ticket tk WHERE tk.id = %1$I.ticket_id AND (
                         current_setting('app.current_tenant_id', true) = '*'
                      OR tk.raised_by_user_id::text = current_setting('app.current_user_id', true)
                      OR EXISTS (SELECT 1 FROM service_provider sp WHERE sp.id = tk.assigned_provider_id
                                 AND sp.user_id::text = current_setting('app.current_user_id', true)))))
            )
            WITH CHECK (
                current_setting('app.current_tenant_id', true) = '*'
                OR tenant_id::text = current_setting('app.current_tenant_id', true)
                OR (tenant_id IS NULL AND EXISTS (
                    SELECT 1 FROM ticket tk WHERE tk.id = %1$I.ticket_id AND (
                         current_setting('app.current_tenant_id', true) = '*'
                      OR tk.raised_by_user_id::text = current_setting('app.current_user_id', true)
                      OR EXISTS (SELECT 1 FROM service_provider sp WHERE sp.id = tk.assigned_provider_id
                                 AND sp.user_id::text = current_setting('app.current_user_id', true)))))
            )
        $f$, t);
    END LOOP;
END $$;
