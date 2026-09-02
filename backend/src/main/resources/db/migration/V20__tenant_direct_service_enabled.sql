-- ============================================================================
-- MVP-6 (B): Direct-to-Provider booking. When enabled, a resident may pick a
-- verified + enrolled provider on raise → request_mode = DIRECT_SERVICE,
-- status = ASSIGNED, skipping admin triage. Default off; set by the Super Admin
-- or the community's own admin.
-- ============================================================================

ALTER TABLE tenant ADD COLUMN direct_service_enabled boolean NOT NULL DEFAULT false;
