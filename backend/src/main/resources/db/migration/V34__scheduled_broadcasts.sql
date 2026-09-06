-- ============================================================================
-- MVP-13 (B3): scheduled ("send later") broadcasts.
--   scheduled_for null + status SENT  = sent immediately (every pre-MVP-13 row)
--   scheduled_for set  + status PENDING = queued; BroadcastDispatchJob fans it
--                                         out once scheduled_for passes
--   status CANCELLED   = a pending one the sender withdrew
-- ============================================================================

ALTER TABLE broadcast ADD COLUMN IF NOT EXISTS scheduled_for timestamptz;
ALTER TABLE broadcast ADD COLUMN IF NOT EXISTS status varchar(12) NOT NULL DEFAULT 'SENT'
    CHECK (status IN ('PENDING', 'SENT', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS ix_broadcast_pending ON broadcast (scheduled_for)
    WHERE status = 'PENDING';
