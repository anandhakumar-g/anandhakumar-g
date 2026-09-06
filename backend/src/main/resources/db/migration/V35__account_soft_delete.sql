-- ============================================================================
-- MVP-13 (C2): account soft-delete + anonymize.
--   deleted_at set  = the account is closed; JwtAuthFilter rejects its tokens,
--                     PII on the row is scrubbed, tickets/payments are kept.
-- The phone_hash unique index becomes partial so a scrubbed (random) hash can't
-- collide and the real number can complete a fresh signup later.
-- ============================================================================

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP INDEX IF EXISTS ux_app_user_phone_hash;
CREATE UNIQUE INDEX ux_app_user_phone_hash ON app_user (phone_hash) WHERE deleted_at IS NULL;
