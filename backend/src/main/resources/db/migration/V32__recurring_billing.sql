-- ============================================================================
-- MVP-13 (A2): recurring auto-charge via a gateway subscription / mandate.
-- payment_method: a saved gateway token for one user (no RLS — personal,
-- app-scoped, like device_token). subscription gains the gateway's own ids so a
-- subscription.charged webhook can find it.
-- ============================================================================

CREATE TABLE IF NOT EXISTS payment_method (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              uuid NOT NULL REFERENCES app_user(id),
    gateway              varchar(20) NOT NULL,
    gateway_customer_id  varchar(80),
    gateway_token        varchar(120) NOT NULL,
    brand                varchar(20),
    last4                varchar(4),
    status               varchar(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
    created_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_payment_method_user ON payment_method (user_id) WHERE status = 'ACTIVE';

ALTER TABLE subscription ADD COLUMN IF NOT EXISTS gateway_subscription_id varchar(80);
ALTER TABLE subscription ADD COLUMN IF NOT EXISTS gateway_customer_id     varchar(80);
CREATE INDEX IF NOT EXISTS ix_subscription_gateway_sub ON subscription (gateway_subscription_id)
    WHERE gateway_subscription_id IS NOT NULL;
