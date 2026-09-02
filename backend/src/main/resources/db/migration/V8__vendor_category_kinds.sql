-- ============================================================================
-- MVP-3: make vendor-category "kinds" data so a Super Admin can open new verticals
-- without a migration. The CHECK constraint on vendor_category.kind is dropped;
-- validation moves to SuperAdminTaxonomyController against this lookup table.
-- ============================================================================

CREATE TABLE IF NOT EXISTS vendor_category_kind (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code       varchar(40) NOT NULL,
    label      varchar(80) NOT NULL,
    sort_order integer NOT NULL DEFAULT 100,
    active     boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_vendor_category_kind_code ON vendor_category_kind (code);

INSERT INTO vendor_category_kind (code, label, sort_order) VALUES
 ('MAINTENANCE',          'Home & Maintenance',      10),
 ('FOOD_DINING',          'Food & Dining',           20),
 ('RETAIL',               'Shops & Retail',          30),
 ('TRAVEL',               'Travel',                  40),
 ('ACCOMMODATION',        'Stays',                   50),
 ('EVENTS_ENTERTAINMENT', 'Events & Entertainment',  60),
 ('OTHER',                'Other',                   99)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE vendor_category DROP CONSTRAINT IF EXISTS vendor_category_kind_check;
ALTER TABLE vendor_category ALTER COLUMN kind TYPE varchar(40);
