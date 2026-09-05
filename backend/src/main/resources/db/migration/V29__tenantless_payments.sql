-- ============================================================================
-- MVP-10 (C): a community-less direct booking (MVP-8, V24) can now be charged.
-- Relax ticket_payment / payment_receipt / payment_event's tenant_id the same
-- way ticket's was relaxed in V24, adding a null-tenant RLS branch. Existing
-- tenant rows are unaffected (tenant_id IS NULL is false for them).
-- ============================================================================

ALTER TABLE ticket_payment  ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE payment_receipt ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE payment_event   ALTER COLUMN tenant_id DROP NOT NULL;

-- ---- ticket_payment: one hop to ticket, plus the charging provider directly --
DROP POLICY IF EXISTS ticket_payment_tenant_isolation ON ticket_payment;
CREATE POLICY ticket_payment_tenant_isolation ON ticket_payment
    USING (
        current_setting('app.current_tenant_id', true) = '*'
        OR tenant_id::text = current_setting('app.current_tenant_id', true)
        OR (tenant_id IS NULL AND (
                current_setting('app.current_tenant_id', true) = '*'
             OR charged_by_user_id::text = current_setting('app.current_user_id', true)
             OR EXISTS (SELECT 1 FROM ticket t WHERE t.id = ticket_payment.ticket_id AND (
                     t.raised_by_user_id::text = current_setting('app.current_user_id', true)
                  OR EXISTS (SELECT 1 FROM service_provider sp WHERE sp.id = t.assigned_provider_id
                             AND sp.user_id::text = current_setting('app.current_user_id', true))))))
    )
    WITH CHECK (
        current_setting('app.current_tenant_id', true) = '*'
        OR tenant_id::text = current_setting('app.current_tenant_id', true)
        OR (tenant_id IS NULL AND (
                current_setting('app.current_tenant_id', true) = '*'
             OR charged_by_user_id::text = current_setting('app.current_user_id', true)
             OR EXISTS (SELECT 1 FROM ticket t WHERE t.id = ticket_payment.ticket_id AND (
                     t.raised_by_user_id::text = current_setting('app.current_user_id', true)
                  OR EXISTS (SELECT 1 FROM service_provider sp WHERE sp.id = t.assigned_provider_id
                             AND sp.user_id::text = current_setting('app.current_user_id', true))))))
    );

-- ---- payment_receipt + payment_event: two hops (via ticket_payment -> ticket) --
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['payment_receipt', 'payment_event']
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I_tenant_isolation ON %I', t, t);
        EXECUTE format($f$
            CREATE POLICY %1$I_tenant_isolation ON %1$I
            USING (
                current_setting('app.current_tenant_id', true) = '*'
                OR tenant_id::text = current_setting('app.current_tenant_id', true)
                OR (tenant_id IS NULL AND EXISTS (
                    SELECT 1 FROM ticket_payment tp JOIN ticket t ON t.id = tp.ticket_id
                    WHERE tp.id = %1$I.ticket_payment_id AND (
                         current_setting('app.current_tenant_id', true) = '*'
                      OR tp.charged_by_user_id::text = current_setting('app.current_user_id', true)
                      OR t.raised_by_user_id::text = current_setting('app.current_user_id', true)
                      OR EXISTS (SELECT 1 FROM service_provider sp WHERE sp.id = t.assigned_provider_id
                                 AND sp.user_id::text = current_setting('app.current_user_id', true)))))
            )
            WITH CHECK (
                current_setting('app.current_tenant_id', true) = '*'
                OR tenant_id::text = current_setting('app.current_tenant_id', true)
                OR (tenant_id IS NULL AND EXISTS (
                    SELECT 1 FROM ticket_payment tp JOIN ticket t ON t.id = tp.ticket_id
                    WHERE tp.id = %1$I.ticket_payment_id AND (
                         current_setting('app.current_tenant_id', true) = '*'
                      OR tp.charged_by_user_id::text = current_setting('app.current_user_id', true)
                      OR t.raised_by_user_id::text = current_setting('app.current_user_id', true)
                      OR EXISTS (SELECT 1 FROM service_provider sp WHERE sp.id = t.assigned_provider_id
                                 AND sp.user_id::text = current_setting('app.current_user_id', true)))))
            )
        $f$, t);
    END LOOP;
END $$;
