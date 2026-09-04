-- MVP-9 (B): broadcast announcements.
--   admin  -> every active resident of the community they're acting in
--   super  -> all admins | all users | a named community
-- One notification_outbox row per broadcast, fanned out by the existing OutboxDispatcher.

CREATE TABLE broadcast (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope            varchar(16)  NOT NULL CHECK (scope IN ('COMMUNITY','ALL_ADMINS','ALL_USERS')),
    tenant_id        uuid REFERENCES tenant(id),          -- set for COMMUNITY, null for platform scopes
    sender_user_id   uuid NOT NULL REFERENCES app_user(id),
    sender_role      varchar(20)  NOT NULL,
    title            varchar(160) NOT NULL,
    body_enc         text NOT NULL,                       -- @Convert(EncryptedStringConverter), like ticket free-text
    recipient_count  integer NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_broadcast_sender_created ON broadcast (sender_user_id, created_at DESC);
CREATE INDEX ix_broadcast_tenant_created ON broadcast (tenant_id, created_at DESC);

-- Announcements are opt-out (default on), unlike promos (opt-in by category).
ALTER TABLE notification_preference ADD COLUMN broadcast_enabled boolean NOT NULL DEFAULT true;
