-- ============================================================================
-- MVP-8 (B2): an offer can be tagged to a set of named individuals (USER_LIST),
-- resolved from phone numbers at submit / approval time. Mirrors tenant_ids.
-- ============================================================================

ALTER TABLE offer_target DROP CONSTRAINT IF EXISTS offer_target_target_type_check;
ALTER TABLE offer_target ADD CONSTRAINT offer_target_target_type_check
    CHECK (target_type IN ('SINGLE_TENANT', 'TENANT_LIST', 'ALL_TENANTS',
                           'USER_SEGMENT', 'ENQUIRY_BASED', 'USER_LIST'));

ALTER TABLE offer_target ADD COLUMN user_ids text;   -- CSV of app_user UUIDs (USER_LIST)
