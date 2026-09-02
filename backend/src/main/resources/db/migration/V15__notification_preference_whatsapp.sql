-- ============================================================================
-- MVP-5 (B): per-user opt-in for WhatsApp ticket notifications. Opt-in like the
-- promo channel — off until the resident turns it on. Only effective when the
-- community's plan carries the WHATSAPP_NOTIFICATIONS entitlement.
-- ============================================================================

ALTER TABLE notification_preference ADD COLUMN whatsapp_enabled boolean NOT NULL DEFAULT false;
