-- ============================================================================
-- MVP-13 (C3): device / session management.
--   device_id  — the MVP-10 JWT deviceId claim; recorded per sign-in.
--   revoked_at — the user signed this device out; JwtAuthFilter then 401s any
--                device-bound token for (user_id, device_id).
-- A device_token row can now be session-only (device_id set, no push token yet),
-- so token / platform become nullable.
-- ============================================================================

ALTER TABLE device_token ADD COLUMN IF NOT EXISTS device_id  varchar(64);
ALTER TABLE device_token ADD COLUMN IF NOT EXISTS revoked_at timestamptz;
ALTER TABLE device_token ADD COLUMN IF NOT EXISTS label      varchar(80);

ALTER TABLE device_token ALTER COLUMN token    DROP NOT NULL;
ALTER TABLE device_token ALTER COLUMN platform DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_device_token_user_device
    ON device_token (user_id, device_id) WHERE device_id IS NOT NULL;
