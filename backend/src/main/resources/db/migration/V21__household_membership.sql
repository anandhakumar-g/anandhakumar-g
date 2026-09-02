-- ============================================================================
-- MVP-6 (C): household model. A user can be a member of several flats (in one
-- community or across communities); each flat has one PRIMARY member (head) and
-- any number of SECONDARY members (family, different phone numbers). A PRIMARY
-- adds family via a HOUSEHOLD invite code — no admin step.
-- ============================================================================

ALTER TABLE user_tenant_membership
    ADD COLUMN household_role varchar(12) NOT NULL DEFAULT 'PRIMARY'
        CHECK (household_role IN ('PRIMARY', 'SECONDARY'));
ALTER TABLE user_tenant_membership
    ADD COLUMN invited_by_user_id uuid REFERENCES app_user(id);

-- One membership per (user, tenant) becomes one per (user, flat).
DROP INDEX IF EXISTS ux_membership_user_tenant_active;

CREATE UNIQUE INDEX ux_membership_user_flat_active
    ON user_tenant_membership (user_id, flat_id)
    WHERE flat_id IS NOT NULL AND status IN ('PENDING_APPROVAL', 'ACTIVE');

CREATE UNIQUE INDEX ux_membership_user_tenant_noflat
    ON user_tenant_membership (user_id, tenant_id)
    WHERE flat_id IS NULL AND status IN ('PENDING_APPROVAL', 'ACTIVE');

CREATE UNIQUE INDEX ux_flat_primary_active
    ON user_tenant_membership (flat_id)
    WHERE household_role = 'PRIMARY' AND flat_id IS NOT NULL
      AND status IN ('PENDING_APPROVAL', 'ACTIVE');

-- Distinguish admin-issued invite codes from resident PRIMARY-issued household codes.
ALTER TABLE invite_code
    ADD COLUMN kind varchar(12) NOT NULL DEFAULT 'ADMIN'
        CHECK (kind IN ('ADMIN', 'HOUSEHOLD'));
