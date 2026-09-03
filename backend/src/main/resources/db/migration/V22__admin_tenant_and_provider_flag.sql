-- ============================================================================
-- MVP-7 (A + B): an admin can belong to several communities, and a community
-- carries a per-community permission flag for provider enrolment.
--
-- admin_tenant is admin infrastructure, scoped at the app layer like
-- user_tenant_membership (read across tenant boundaries at login) — NOT RLS.
-- ============================================================================

CREATE TABLE admin_tenant (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id     uuid NOT NULL REFERENCES app_user(id),
    tenant_id         uuid NOT NULL REFERENCES tenant(id),
    added_by_user_id  uuid REFERENCES app_user(id),
    active            boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (admin_user_id, tenant_id)
);

CREATE INDEX ix_admin_tenant_tenant ON admin_tenant (tenant_id);
CREATE INDEX ix_admin_tenant_admin  ON admin_tenant (admin_user_id);

-- Backfill from the single-tenant model: every admin's current_tenant_id becomes
-- their one admin_tenant assignment.
INSERT INTO admin_tenant (admin_user_id, tenant_id, active)
SELECT id, current_tenant_id, true
FROM app_user
WHERE role = 'ADMIN' AND current_tenant_id IS NOT NULL;

-- Per-community switch: a community admin may enrol a verified provider only when
-- the Super Admin has granted this. Default off (Super-Admin-set).
ALTER TABLE tenant
    ADD COLUMN provider_onboarding_allowed boolean NOT NULL DEFAULT false;
