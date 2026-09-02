-- ============================================================================
-- MVP-5 (B): WHATSAPP_NOTIFICATIONS entitlement on the tenant plans.
-- SubscriptionPlan.limitFor treats an ABSENT key as -1 (entitled), so a plan
-- that excludes the feature must carry an explicit 0. Full-literal rewrite of
-- the entitlements map per plan (mirrors V10). PROVIDER plans are untouched —
-- the subject is TENANT.
-- ============================================================================

UPDATE subscription_plan SET entitlements =
  '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":5,"OFFERS_PER_MONTH":10,"WHATSAPP_NOTIFICATIONS":0}'
  WHERE code = 'TENANT_FREE';
UPDATE subscription_plan SET entitlements =
  '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":15,"OFFERS_PER_MONTH":40,"WHATSAPP_NOTIFICATIONS":-1}'
  WHERE code = 'TENANT_STANDARD';
UPDATE subscription_plan SET entitlements =
  '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":-1,"OFFERS_PER_MONTH":-1,"WHATSAPP_NOTIFICATIONS":-1}'
  WHERE code = 'TENANT_PLUS';
