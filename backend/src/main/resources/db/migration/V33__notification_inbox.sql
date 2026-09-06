-- ============================================================================
-- MVP-13 (B1/B2): an in-app notification inbox, and a promo-WhatsApp opt-in.
--  * notification.read_at — null until the user opens it in the app.
--  * notification_preference.promo_whatsapp_enabled — opt-IN (unlike the push
--    promo pref, which is opt-out), gates WhatsApp delivery of offer promos.
-- ============================================================================

ALTER TABLE notification ADD COLUMN IF NOT EXISTS read_at timestamptz;

ALTER TABLE notification_preference
    ADD COLUMN IF NOT EXISTS promo_whatsapp_enabled boolean NOT NULL DEFAULT false;
