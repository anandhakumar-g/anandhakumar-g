-- ============================================================================
-- MVP-2: provider KYC documents, notification preferences, and the Offers module.
-- None of these are tenant-scoped (provider directory is global; offers are
-- cross-tenant by design; preferences are per-user) — access is enforced at the
-- application layer, consistent with ADR-011. Enum columns store UPPERCASE names.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Provider KYC documents
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_kyc_document (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_provider_id uuid NOT NULL REFERENCES service_provider(id),
    doc_type            varchar(20) NOT NULL
                            CHECK (doc_type IN ('GOV_ID','ADDRESS_PROOF','COMPANY_REG','OTHER')),
    storage_key         text NOT NULL,
    content_type        varchar(100) NOT NULL,
    size_bytes          bigint NOT NULL,
    original_filename   varchar(255),
    status              varchar(12) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    uploaded_by_user_id uuid REFERENCES app_user(id),
    reviewed_by_user_id uuid REFERENCES app_user(id),
    review_note         text,
    reviewed_at         timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_kyc_provider ON provider_kyc_document (service_provider_id, status);

-- ---------------------------------------------------------------------------
-- Notification preferences (one row per user; created lazily with defaults)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_preference (
    id                            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                       uuid NOT NULL REFERENCES app_user(id),
    subscribed_vendor_category_ids text NOT NULL DEFAULT '',
    promo_frequency_cap_per_week   integer NOT NULL DEFAULT 5,
    digest_mode                   varchar(8) NOT NULL DEFAULT 'OFF'
                                      CHECK (digest_mode IN ('OFF','DAILY','WEEKLY')),
    ticket_notifications_enabled  boolean NOT NULL DEFAULT true,
    promo_notifications_enabled   boolean NOT NULL DEFAULT true,
    created_at                    timestamptz NOT NULL DEFAULT now(),
    updated_at                    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_notif_pref_user ON notification_preference (user_id);

-- ---------------------------------------------------------------------------
-- Offers / coupons
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offer (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by_user_id       uuid NOT NULL REFERENCES app_user(id),
    created_by_role          varchar(20) NOT NULL,
    service_provider_id      uuid REFERENCES service_provider(id),
    tenant_id                uuid REFERENCES tenant(id),          -- set for admin community offers
    vendor_category_id       uuid NOT NULL REFERENCES vendor_category(id),
    title                    varchar(160) NOT NULL,
    description              text,
    image_key                text,
    discount_type            varchar(12) NOT NULL
                                 CHECK (discount_type IN ('FLAT','PERCENTAGE')),
    discount_value           numeric(10,2) NOT NULL,
    coupon_code              varchar(40),
    valid_from               timestamptz NOT NULL,
    valid_to                 timestamptz NOT NULL,
    redemption_limit_per_user integer NOT NULL DEFAULT 1,
    redemption_limit_total    integer,
    terms                    text,
    status                   varchar(20) NOT NULL DEFAULT 'DRAFT'
                                 CHECK (status IN ('DRAFT','PENDING_APPROVAL','ACTIVE',
                                                   'EXPIRED','CANCELLED','REJECTED')),
    submitted_at             timestamptz,
    validated_by_user_id     uuid REFERENCES app_user(id),
    validated_at             timestamptz,
    reject_reason            text,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_offer_status ON offer (status, valid_to);
CREATE INDEX IF NOT EXISTS ix_offer_creator ON offer (created_by_user_id);
CREATE INDEX IF NOT EXISTS ix_offer_provider ON offer (service_provider_id);

CREATE TABLE IF NOT EXISTS offer_target (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id           uuid NOT NULL REFERENCES offer(id),
    target_type        varchar(16) NOT NULL
                           CHECK (target_type IN ('SINGLE_TENANT','TENANT_LIST','ALL_TENANTS',
                                                  'USER_SEGMENT','ENQUIRY_BASED')),
    tenant_ids         text,                 -- CSV of tenant UUIDs (SINGLE_TENANT / TENANT_LIST)
    segment_filter     text,                 -- JSON: {"block":"A"} or {"usedVendorCategoryId":"..."}
    enquiry_category_id uuid REFERENCES category(id),
    set_by_user_id     uuid REFERENCES app_user(id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_offer_target_offer ON offer_target (offer_id);

CREATE TABLE IF NOT EXISTS offer_redemption (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id     uuid NOT NULL REFERENCES offer(id),
    user_id      uuid NOT NULL REFERENCES app_user(id),
    tenant_id    uuid REFERENCES tenant(id),
    code_entered varchar(40),
    verified_by  varchar(12) NOT NULL DEFAULT 'RESIDENT'
                     CHECK (verified_by IN ('RESIDENT','PROVIDER','ADMIN')),
    status       varchar(10) NOT NULL DEFAULT 'REDEEMED'
                     CHECK (status IN ('REDEEMED','VOID')),
    redeemed_at  timestamptz NOT NULL DEFAULT now(),
    confirmed_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_offer_redemption_offer ON offer_redemption (offer_id, status);
CREATE INDEX IF NOT EXISTS ix_offer_redemption_user ON offer_redemption (user_id, offer_id);
