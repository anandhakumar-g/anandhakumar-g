-- ============================================================================
-- MVP-4 follow-up: widen the FREE tier caps so the platform stays effectively
-- free through the adoption phase, and lift the paid tiers to unmetered where it
-- makes sense. Core ticketing (TICKETS_PER_MONTH) is never metered on any tier.
-- ============================================================================

UPDATE subscription_plan SET entitlements =
  '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":5,"OFFERS_PER_MONTH":10}'  WHERE code = 'TENANT_FREE';
UPDATE subscription_plan SET entitlements =
  '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":15,"OFFERS_PER_MONTH":40}' WHERE code = 'TENANT_STANDARD';
UPDATE subscription_plan SET entitlements =
  '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":-1,"OFFERS_PER_MONTH":-1}' WHERE code = 'TENANT_PLUS';
UPDATE subscription_plan SET entitlements =
  '{"DIRECTORY_LISTING":1,"OFFERS_PER_MONTH":8}'  WHERE code = 'PROVIDER_FREE';
UPDATE subscription_plan SET entitlements =
  '{"DIRECTORY_LISTING":1,"OFFERS_PER_MONTH":40}' WHERE code = 'PROVIDER_LISTING';
