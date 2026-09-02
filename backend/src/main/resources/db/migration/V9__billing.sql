-- ============================================================================
-- MVP-4: monetization. Subscription plans for TENANT and PROVIDER subjects.
-- No RLS — app-scoped (a tenant admin queries by tenant_id, a provider by its
-- provider id, Super Admin sees all), consistent with ADR-011.
-- entitlements is a JSON string: {"FEATURE": <limit>} where -1 = unlimited,
-- 0 = disabled, positive = hard cap per calendar month.
-- ============================================================================

CREATE TABLE IF NOT EXISTS subscription_plan (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    target         varchar(10) NOT NULL CHECK (target IN ('TENANT','PROVIDER')),
    code           varchar(40) NOT NULL,
    name           varchar(120) NOT NULL,
    description    text,
    billing_cycle  varchar(8) NOT NULL DEFAULT 'MONTHLY' CHECK (billing_cycle IN ('MONTHLY','ANNUAL')),
    price_amount   numeric(12,2) NOT NULL DEFAULT 0,
    currency       varchar(3) NOT NULL DEFAULT 'INR',
    entitlements   text NOT NULL DEFAULT '{}',
    active         boolean NOT NULL DEFAULT true,
    is_default     boolean NOT NULL DEFAULT false,
    sort_order     integer NOT NULL DEFAULT 100,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_subscription_plan_code ON subscription_plan (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_subscription_plan_default
    ON subscription_plan (target) WHERE is_default;

INSERT INTO subscription_plan (target, code, name, billing_cycle, price_amount, entitlements, is_default, sort_order) VALUES
 ('TENANT',  'TENANT_FREE',     'Community Free',     'MONTHLY',    0,
   '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":3,"OFFERS_PER_MONTH":2}',  true,  10),
 ('TENANT',  'TENANT_STANDARD', 'Community Standard', 'MONTHLY', 2999,
   '{"TICKETS_PER_MONTH":200,"ADMIN_SEATS":5,"OFFERS_PER_MONTH":10}', false, 20),
 ('TENANT',  'TENANT_PLUS',     'Community Plus',     'MONTHLY', 5999,
   '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":15,"OFFERS_PER_MONTH":-1}', false, 30),
 ('PROVIDER','PROVIDER_FREE',   'Vendor Free',       'MONTHLY',    0,
   '{"DIRECTORY_LISTING":1,"OFFERS_PER_MONTH":1}',   true,  10),
 ('PROVIDER','PROVIDER_LISTING','Vendor Listing',    'MONTHLY',  499,
   '{"DIRECTORY_LISTING":1,"OFFERS_PER_MONTH":5}',   false, 20)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS subscription (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type         varchar(10) NOT NULL CHECK (subject_type IN ('TENANT','PROVIDER')),
    subject_id           uuid NOT NULL,
    plan_id              uuid NOT NULL REFERENCES subscription_plan(id),
    status               varchar(12) NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('TRIAL','ACTIVE','PAST_DUE','EXPIRED','CANCELLED','COMPED')),
    current_period_start timestamptz,
    current_period_end   timestamptz,
    grace_until          timestamptz,
    auto_renew           boolean NOT NULL DEFAULT true,
    cancelled_at         timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_subscription_subject_active
    ON subscription (subject_type, subject_id) WHERE status <> 'CANCELLED';

CREATE TABLE IF NOT EXISTS subscription_invoice (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id      uuid NOT NULL REFERENCES subscription(id),
    subject_type         varchar(10) NOT NULL,
    subject_id           uuid NOT NULL,
    tenant_id            uuid REFERENCES tenant(id),
    amount               numeric(12,2) NOT NULL,
    currency             varchar(3) NOT NULL DEFAULT 'INR',
    period_start         timestamptz NOT NULL,
    period_end           timestamptz NOT NULL,
    status               varchar(8) NOT NULL DEFAULT 'DUE' CHECK (status IN ('DUE','PAID','VOID')),
    gateway              varchar(20),
    gateway_ref          varchar(120),
    gateway_payment_link text,
    paid_at              timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_subscription_invoice_sub ON subscription_invoice (subscription_id, status);
CREATE INDEX IF NOT EXISTS ix_subscription_invoice_subject ON subscription_invoice (subject_type, subject_id, status);
CREATE INDEX IF NOT EXISTS ix_subscription_invoice_gateway_ref ON subscription_invoice (gateway_ref);
