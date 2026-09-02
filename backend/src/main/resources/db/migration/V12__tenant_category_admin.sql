-- ============================================================================
-- MVP-5 (A5): who curates a community's ticket categories.
--   SUPER_ADMIN (default) — the platform team manages the community's category
--                           list on its behalf (used when a community has no
--                           capable admin yet).
--   COMMUNITY             — the community's own admin manages its category list.
-- The global category catalogue (category.tenant_id IS NULL) always exists and
-- is only ever managed by the Super Admin.
-- ============================================================================

ALTER TABLE tenant ADD COLUMN category_admin varchar(20) NOT NULL DEFAULT 'SUPER_ADMIN'
    CHECK (category_admin IN ('SUPER_ADMIN','COMMUNITY'));
