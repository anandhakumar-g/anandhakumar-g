-- ============================================================================
-- MVP-5 (D): encrypt free-text PII at rest. New writes go straight to the *_enc
-- columns (the JPA @Convert handles it); pre-existing plaintext rows are moved
-- by PiiBackfillRunner (guarded by sp.pii.backfill.enabled, run once per env).
-- The old plaintext columns are kept this release and dropped in a later
-- migration once every environment has been backfilled.
--
-- Scope is deliberately narrow — ticket free-text + addresses. Names stay
-- plaintext (too many ORDER BY / LIKE sites); see ADR-025.
-- ============================================================================

ALTER TABLE ticket ADD COLUMN description_enc      text;
ALTER TABLE ticket ADD COLUMN resolution_notes_enc text;
ALTER TABLE ticket ADD COLUMN rating_comment_enc   text;
ALTER TABLE ticket ADD COLUMN service_landmark_enc text;
ALTER TABLE ticket ALTER COLUMN description DROP NOT NULL;

ALTER TABLE flat   ADD COLUMN address_text_enc text;
ALTER TABLE tenant ADD COLUMN address_enc      text;
