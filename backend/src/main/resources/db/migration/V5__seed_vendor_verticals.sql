-- ============================================================================
-- MVP-2: widen the vendor taxonomy beyond maintenance trades.
-- Every row is directly selectable; UI groups by `kind`. parent_category_id
-- stays available for deeper nesting later. Deterministic UUIDs.
-- ============================================================================

INSERT INTO vendor_category (id, name, kind, sort_order, active) VALUES
 -- Food & dining
 ('22222222-0000-0000-0001-000000000001', 'Restaurant',            'FOOD_DINING', 200, true),
 ('22222222-0000-0000-0001-000000000002', 'Caterer',               'FOOD_DINING', 210, true),
 ('22222222-0000-0000-0001-000000000003', 'Bakery & Sweets',       'FOOD_DINING', 220, true),
 ('22222222-0000-0000-0001-000000000004', 'Cloud Kitchen',         'FOOD_DINING', 230, true),
 -- Retail
 ('22222222-0000-0000-0002-000000000001', 'Garments & Apparel',    'RETAIL', 300, true),
 ('22222222-0000-0000-0002-000000000002', 'Groceries & Essentials','RETAIL', 310, true),
 ('22222222-0000-0000-0002-000000000003', 'Electronics',           'RETAIL', 320, true),
 ('22222222-0000-0000-0002-000000000004', 'Gifts & Florist',       'RETAIL', 330, true),
 -- Travel
 ('22222222-0000-0000-0003-000000000001', 'Travel Agent',          'TRAVEL', 400, true),
 ('22222222-0000-0000-0003-000000000002', 'Cab & Car Rental',      'TRAVEL', 410, true),
 -- Accommodation
 ('22222222-0000-0000-0004-000000000001', 'Guest House',           'ACCOMMODATION', 500, true),
 ('22222222-0000-0000-0004-000000000002', 'Serviced Apartment',    'ACCOMMODATION', 510, true),
 -- Events & entertainment
 ('22222222-0000-0000-0005-000000000001', 'Birthday & Party Planning', 'EVENTS_ENTERTAINMENT', 600, true),
 ('22222222-0000-0000-0005-000000000002', 'Wedding Arrangements',      'EVENTS_ENTERTAINMENT', 610, true),
 ('22222222-0000-0000-0005-000000000003', 'Decor & Balloons',          'EVENTS_ENTERTAINMENT', 620, true),
 ('22222222-0000-0000-0005-000000000004', 'Photography & Video',       'EVENTS_ENTERTAINMENT', 630, true),
 ('22222222-0000-0000-0005-000000000005', 'Show & Movie Tie-ups',      'EVENTS_ENTERTAINMENT', 640, true);
