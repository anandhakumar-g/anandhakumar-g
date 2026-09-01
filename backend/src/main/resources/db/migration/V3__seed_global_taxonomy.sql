-- ============================================================================
-- Global seed data: ticket categories + vendor-category taxonomy.
-- Deterministic UUIDs so dev-seed / tests can reference them.
-- Ticket categories for MVP-1 are global (tenant_id = NULL); admins get CRUD in MVP-5.
-- Enum-valued columns use Java enum name() (UPPERCASE).
-- ============================================================================

INSERT INTO category (id, tenant_id, name, request_type, default_provider_kind, sla_hours, sort_order, active) VALUES
 ('11111111-0000-0000-0000-000000000001', NULL, 'Electrical',            'ISSUE',    'electrical',   24, 10,  true),
 ('11111111-0000-0000-0000-000000000002', NULL, 'Plumbing',              'ISSUE',    'plumbing',     24, 20,  true),
 ('11111111-0000-0000-0000-000000000003', NULL, 'Internet / Cable TV',   'ISSUE',    'internet',     48, 30,  true),
 ('11111111-0000-0000-0000-000000000004', NULL, 'Housekeeping',          'ISSUE',    'housekeeping', 24, 40,  true),
 ('11111111-0000-0000-0000-000000000005', NULL, 'Security',              'ISSUE',    'security',     8,  50,  true),
 ('11111111-0000-0000-0000-000000000006', NULL, 'Lift / Elevator',       'ISSUE',    'lift',         12, 60,  true),
 ('11111111-0000-0000-0000-000000000007', NULL, 'Water Supply',          'ISSUE',    'plumbing',     12, 70,  true),
 ('11111111-0000-0000-0000-000000000008', NULL, 'Pest Control',          'ISSUE',    'pest_control', 72, 80,  true),
 ('11111111-0000-0000-0000-000000000009', NULL, 'Carpentry',             'ISSUE',    'carpentry',    72, 90,  true),
 ('11111111-0000-0000-0000-00000000000a', NULL, 'Common Area / Amenity', 'ISSUE',    NULL,           48, 100, true),
 ('11111111-0000-0000-0000-00000000000b', NULL, 'Amenity Booking Issue', 'ISSUE',    NULL,           24, 110, true),
 ('11111111-0000-0000-0000-00000000000c', NULL, 'General Feedback',      'FEEDBACK', NULL,           NULL,120, true),
 ('11111111-0000-0000-0000-00000000000d', NULL, 'Enquiry',               'ENQUIRY',  NULL,           NULL,130, true),
 ('11111111-0000-0000-0000-00000000000e', NULL, 'Other',                 'ISSUE',    NULL,           72, 140, true);

INSERT INTO vendor_category (id, name, kind, sort_order, active) VALUES
 ('22222222-0000-0000-0000-000000000001', 'Electrical',         'MAINTENANCE', 10,  true),
 ('22222222-0000-0000-0000-000000000002', 'Plumbing',           'MAINTENANCE', 20,  true),
 ('22222222-0000-0000-0000-000000000003', 'Internet / Cable',   'MAINTENANCE', 30,  true),
 ('22222222-0000-0000-0000-000000000004', 'Housekeeping',       'MAINTENANCE', 40,  true),
 ('22222222-0000-0000-0000-000000000005', 'Security Services',  'MAINTENANCE', 50,  true),
 ('22222222-0000-0000-0000-000000000006', 'Lift Maintenance',   'MAINTENANCE', 60,  true),
 ('22222222-0000-0000-0000-000000000007', 'Pest Control',       'MAINTENANCE', 70,  true),
 ('22222222-0000-0000-0000-000000000008', 'Carpentry',          'MAINTENANCE', 80,  true),
 ('22222222-0000-0000-0000-000000000009', 'Painting',           'MAINTENANCE', 90,  true),
 ('22222222-0000-0000-0000-00000000000a', 'Appliance Repair',   'MAINTENANCE', 100, true),
 ('22222222-0000-0000-0000-00000000000b', 'General Maintenance','MAINTENANCE', 110, true);
