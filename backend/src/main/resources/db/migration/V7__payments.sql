-- ============================================================================
-- MVP-3: payments. Loosely coupled to tickets (references ticket_id, never
-- touches the ticket state machine). Enum columns store Java enum name() (UPPERCASE).
-- All three tables carry tenant_id + RLS, same policy as the ticket child tables.
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS receipt_seq START 1;

CREATE TABLE IF NOT EXISTS ticket_payment (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             uuid NOT NULL REFERENCES tenant(id),
    ticket_id             uuid NOT NULL REFERENCES ticket(id),
    amount                numeric(12,2) NOT NULL CHECK (amount >= 0),
    currency              varchar(3) NOT NULL DEFAULT 'INR',
    mode                  varchar(8) CHECK (mode IN ('CASH','ONLINE')),
    status                varchar(20) NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING','CASH_PENDING_OTP','PAID_ONLINE',
                                                'PAID_CASH','WAIVED','FAILED')),
    charged_by_user_id    uuid NOT NULL REFERENCES app_user(id),
    charged_by_role       varchar(20) NOT NULL,
    note                  text,
    gateway               varchar(20),
    gateway_ref           varchar(120),
    gateway_payment_link  text,
    gateway_payment_id    varchar(120),
    otp_verified_at       timestamptz,
    paid_at               timestamptz,
    waived_by_user_id     uuid REFERENCES app_user(id),
    waived_reason         text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ticket_payment_active
    ON ticket_payment (ticket_id) WHERE status <> 'FAILED';
CREATE INDEX IF NOT EXISTS ix_ticket_payment_gateway_ref ON ticket_payment (gateway_ref);
CREATE INDEX IF NOT EXISTS ix_ticket_payment_tenant ON ticket_payment (tenant_id, status);

CREATE TABLE IF NOT EXISTS payment_receipt (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid NOT NULL REFERENCES tenant(id),
    ticket_payment_id  uuid NOT NULL REFERENCES ticket_payment(id),
    receipt_number     varchar(20) NOT NULL,
    amount             numeric(12,2) NOT NULL,
    currency           varchar(3) NOT NULL DEFAULT 'INR',
    mode               varchar(8) NOT NULL,
    ticket_reference   varchar(20) NOT NULL,
    payer_name         varchar(160),
    payee_name         varchar(160),
    issued_at          timestamptz NOT NULL DEFAULT now(),
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_receipt_payment ON payment_receipt (ticket_payment_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_receipt_number ON payment_receipt (receipt_number);
CREATE TRIGGER trg_payment_receipt_append_only
    BEFORE UPDATE OR DELETE ON payment_receipt
    FOR EACH ROW EXECUTE FUNCTION sp_block_mutation();

CREATE TABLE IF NOT EXISTS payment_event (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid NOT NULL REFERENCES tenant(id),
    ticket_payment_id  uuid NOT NULL REFERENCES ticket_payment(id),
    type               varchar(40) NOT NULL,
    detail             text,
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_payment_event_payment ON payment_event (ticket_payment_id, created_at);
CREATE TRIGGER trg_payment_event_append_only
    BEFORE UPDATE OR DELETE ON payment_event
    FOR EACH ROW EXECUTE FUNCTION sp_block_mutation();

-- ---- RLS (same fail-closed tenant policy as the ticket child tables) ----------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['ticket_payment', 'payment_receipt', 'payment_event']
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
