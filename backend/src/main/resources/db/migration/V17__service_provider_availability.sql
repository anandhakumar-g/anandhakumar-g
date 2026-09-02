-- ============================================================================
-- MVP-5 (C): provider availability. Surfaced in the assign picker / ticket view
-- but NOT enforced — an AWAY provider can still be assigned (a small community
-- may have no alternative).
-- ============================================================================

ALTER TABLE service_provider ADD COLUMN availability varchar(12) NOT NULL DEFAULT 'AVAILABLE'
    CHECK (availability IN ('AVAILABLE', 'BUSY', 'AWAY'));
ALTER TABLE service_provider ADD COLUMN availability_note varchar(200);
