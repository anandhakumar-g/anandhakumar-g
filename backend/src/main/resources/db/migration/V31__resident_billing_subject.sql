-- ============================================================================
-- MVP-13 (A1): a third billing subject — RESIDENT.
-- Infrastructure only: widen the subject CHECKs and seed a FREE default resident
-- plan so EntitlementService / BillingService can resolve a resident subject.
-- No sellable resident plan ships in this MVP (adding one is a createPlan call).
-- ============================================================================

ALTER TABLE subscription_plan DROP CONSTRAINT IF EXISTS subscription_plan_target_check;
ALTER TABLE subscription_plan ADD CONSTRAINT subscription_plan_target_check
    CHECK (target IN ('TENANT', 'PROVIDER', 'RESIDENT'));

ALTER TABLE subscription DROP CONSTRAINT IF EXISTS subscription_subject_type_check;
ALTER TABLE subscription ADD CONSTRAINT subscription_subject_type_check
    CHECK (subject_type IN ('TENANT', 'PROVIDER', 'RESIDENT'));

INSERT INTO subscription_plan (target, code, name, billing_cycle, price_amount, entitlements, is_default, sort_order)
VALUES ('RESIDENT', 'RESIDENT_FREE', 'Resident Free', 'MONTHLY', 0, '{}', true, 10)
ON CONFLICT (code) DO NOTHING;
