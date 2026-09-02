-- ============================================================================
-- MVP-5 (C): a resident can mark themselves away until a date. Informational —
-- shown on the raiser card so a provider/admin knows not to expect a fast reply.
-- ============================================================================

ALTER TABLE app_user ADD COLUMN away_until timestamptz;
