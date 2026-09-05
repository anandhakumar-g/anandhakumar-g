-- ============================================================================
-- MVP-11 (A): community self-onboarding. Anyone can request a community from
-- inside the app; it lands in PENDING_REVIEW until a Super Admin approves it.
-- `tenant` is the root table (never RLS'd), so a pending row is simply excluded
-- by every ACTIVE-only query (TenantRepository.search, tenantHealth).
-- ============================================================================

ALTER TABLE tenant DROP CONSTRAINT IF EXISTS tenant_status_check;
ALTER TABLE tenant ADD CONSTRAINT tenant_status_check
    CHECK (status IN ('PENDING_REVIEW','ACTIVE','SUSPENDED','ARCHIVED'));

ALTER TABLE tenant ADD COLUMN requested_by_user_id uuid REFERENCES app_user(id);

CREATE INDEX ix_tenant_status_created ON tenant (status, created_at DESC);
