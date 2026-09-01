-- ============================================================================
-- Single Point — MVP-1 core schema
-- PostgreSQL 14+.  UUID PKs (gen_random_uuid), TIMESTAMPTZ everywhere.
-- Enum-valued columns store the Java enum name() (UPPERCASE) to match
-- @Enumerated(EnumType.STRING). Tenant-scoped tables carry tenant_id NOT NULL
-- and get an RLS policy in V2.
-- ============================================================================

CREATE OR REPLACE FUNCTION sp_block_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only (% attempted)', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE SEQUENCE IF NOT EXISTS ticket_ref_seq START 1000;

-- ============================================================================
-- Tenants
-- ============================================================================
CREATE TABLE IF NOT EXISTS tenant (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 varchar(160) NOT NULL,
    city                 varchar(120),
    locality             varchar(160),
    address              text,
    pincode              varchar(12),
    geo_lat              numeric(9,6),
    geo_lng              numeric(9,6),
    logo_url             text,
    status               varchar(20) NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED')),
    reopen_window_hours  integer NOT NULL DEFAULT 72,
    require_allocation_approval boolean NOT NULL DEFAULT false,
    default_theme        varchar(40),
    brand_logo_url       text,
    brand_primary_color  varchar(9),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_tenant_city ON tenant (lower(city));
CREATE INDEX IF NOT EXISTS ix_tenant_name ON tenant (lower(name));

-- ============================================================================
-- Users  (global — not tenant-scoped; identity anchored to phone)
-- ============================================================================
CREATE TABLE IF NOT EXISTS app_user (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    role               varchar(20) NOT NULL
                           CHECK (role IN ('RESIDENT','ADMIN','PROVIDER','SUPER_ADMIN')),
    user_type          varchar(20) NOT NULL DEFAULT 'INDIVIDUAL'
                           CHECK (user_type IN ('INDIVIDUAL','COMPANY')),
    name               varchar(160),
    phone_enc          text NOT NULL,
    phone_hash         varchar(64) NOT NULL,
    email_enc          text,
    email_hash         varchar(64),
    preferred_theme    varchar(40),
    kyc_status         varchar(20) NOT NULL DEFAULT 'NOT_REQUIRED'
                           CHECK (kyc_status IN ('NOT_REQUIRED','PENDING','VERIFIED','REJECTED')),
    status             varchar(20) NOT NULL DEFAULT 'ACTIVE'
                           CHECK (status IN ('ACTIVE','DISABLED')),
    current_tenant_id  uuid REFERENCES tenant(id),
    profile_completed  boolean NOT NULL DEFAULT false,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_phone_hash ON app_user (phone_hash);
CREATE INDEX IF NOT EXISTS ix_app_user_current_tenant ON app_user (current_tenant_id);

-- ============================================================================
-- Flats  (tenant-scoped)
-- ============================================================================
CREATE TABLE IF NOT EXISTS flat (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              uuid NOT NULL REFERENCES tenant(id),
    block                  varchar(40),
    flat_number            varchar(40) NOT NULL,
    owner_user_id          uuid REFERENCES app_user(id),
    current_occupant_user_id uuid REFERENCES app_user(id),
    occupancy_type         varchar(20) NOT NULL DEFAULT 'VACANT'
                               CHECK (occupancy_type IN ('OWNER_OCCUPIED','RENTED','VACANT')),
    geo_lat                numeric(9,6),
    geo_lng                numeric(9,6),
    address_text           text,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_flat_tenant_block_number
    ON flat (tenant_id, coalesce(block,''), flat_number);
CREATE INDEX IF NOT EXISTS ix_flat_occupant ON flat (current_occupant_user_id);

-- ============================================================================
-- User <-> Tenant membership  (tenant-scoped)
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_tenant_membership (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES tenant(id),
    user_id       uuid NOT NULL REFERENCES app_user(id),
    flat_id       uuid REFERENCES flat(id),
    relation      varchar(20) NOT NULL DEFAULT 'OCCUPANT'
                      CHECK (relation IN ('OCCUPANT','OWNER','WATCHER')),
    status        varchar(20) NOT NULL DEFAULT 'PENDING_APPROVAL'
                      CHECK (status IN ('PENDING_APPROVAL','ACTIVE','REJECTED','EXITED')),
    requested_flat_label varchar(120),
    approved_by_user_id  uuid REFERENCES app_user(id),
    joined_at     timestamptz,
    exited_at     timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_membership_user_tenant_active
    ON user_tenant_membership (user_id, tenant_id)
    WHERE status IN ('PENDING_APPROVAL','ACTIVE');
CREATE INDEX IF NOT EXISTS ix_membership_tenant_status ON user_tenant_membership (tenant_id, status);

-- ============================================================================
-- Invite codes  (tenant-scoped)
-- ============================================================================
CREATE TABLE IF NOT EXISTS invite_code (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid NOT NULL REFERENCES tenant(id),
    flat_id            uuid REFERENCES flat(id),
    code               varchar(16) NOT NULL,
    relation           varchar(20) NOT NULL DEFAULT 'OCCUPANT'
                           CHECK (relation IN ('OCCUPANT','OWNER','WATCHER')),
    created_by_user_id  uuid NOT NULL REFERENCES app_user(id),
    expires_at         timestamptz,
    max_uses           integer NOT NULL DEFAULT 1,
    use_count          integer NOT NULL DEFAULT 0,
    status             varchar(20) NOT NULL DEFAULT 'ACTIVE'
                           CHECK (status IN ('ACTIVE','REVOKED','EXHAUSTED','EXPIRED')),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_invite_code_code ON invite_code (code);
CREATE INDEX IF NOT EXISTS ix_invite_code_tenant ON invite_code (tenant_id, status);

-- ============================================================================
-- Ticket categories  (global set for MVP-1; tenant_id kept null-able for MVP-5)
-- ============================================================================
CREATE TABLE IF NOT EXISTS category (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             uuid REFERENCES tenant(id),
    name                  varchar(120) NOT NULL,
    parent_category_id    uuid REFERENCES category(id),
    request_type          varchar(20) NOT NULL DEFAULT 'ISSUE'
                              CHECK (request_type IN ('ISSUE','FEEDBACK','ENQUIRY')),
    default_provider_kind varchar(40),
    sla_hours             integer,
    sort_order            integer NOT NULL DEFAULT 100,
    active                boolean NOT NULL DEFAULT true,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_category_scope_name
    ON category (coalesce(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid),
                 coalesce(parent_category_id, '00000000-0000-0000-0000-000000000000'::uuid),
                 lower(name));

-- ============================================================================
-- Vendor category taxonomy  (global, extensible — expansion is MVP-2)
-- ============================================================================
CREATE TABLE IF NOT EXISTS vendor_category (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name               varchar(120) NOT NULL,
    parent_category_id uuid REFERENCES vendor_category(id),
    kind               varchar(30) NOT NULL DEFAULT 'MAINTENANCE'
                           CHECK (kind IN ('MAINTENANCE','FOOD_DINING','RETAIL','TRAVEL',
                                           'ACCOMMODATION','EVENTS_ENTERTAINMENT','OTHER')),
    sort_order         integer NOT NULL DEFAULT 100,
    active              boolean NOT NULL DEFAULT true,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_vendor_category_name ON vendor_category (lower(name));

-- ============================================================================
-- Service providers  (global directory; may serve many tenants)
-- ============================================================================
CREATE TABLE IF NOT EXISTS service_provider (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid REFERENCES app_user(id),
    name                varchar(160) NOT NULL,
    vendor_category_id  uuid NOT NULL REFERENCES vendor_category(id),
    is_company          boolean NOT NULL DEFAULT false,
    contact_phone_enc   text NOT NULL,
    contact_phone_hash  varchar(64) NOT NULL,
    contact_email_enc   text,
    service_area        varchar(200),
    verification_status varchar(24) NOT NULL DEFAULT 'PENDING_VERIFICATION'
                            CHECK (verification_status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','SUSPENDED')),
    verified_by_user_id uuid REFERENCES app_user(id),
    verified_at         timestamptz,
    tier                varchar(20) NOT NULL DEFAULT 'STANDARD'
                            CHECK (tier IN ('STANDARD','FEATURED')),
    active              boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_service_provider_phone_hash ON service_provider (contact_phone_hash);
CREATE INDEX IF NOT EXISTS ix_service_provider_category ON service_provider (vendor_category_id);

CREATE TABLE IF NOT EXISTS tenant_service_provider (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenant(id),
    service_provider_id uuid NOT NULL REFERENCES service_provider(id),
    category_ids        text,
    service_area        varchar(200),
    active              boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_tsp_tenant_provider
    ON tenant_service_provider (tenant_id, service_provider_id);

-- ============================================================================
-- Tickets  (tenant-scoped)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ticket (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenant(id),
    reference_code      varchar(20) NOT NULL,
    request_mode        varchar(20) NOT NULL DEFAULT 'COMMUNITY_TICKET'
                            CHECK (request_mode IN ('COMMUNITY_TICKET','DIRECT_SERVICE')),
    raised_by_user_id   uuid NOT NULL REFERENCES app_user(id),
    flat_id             uuid REFERENCES flat(id),
    category_id         uuid NOT NULL REFERENCES category(id),
    subcategory_id      uuid REFERENCES category(id),
    description         text NOT NULL,
    priority            varchar(12) CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    status              varchar(16) NOT NULL DEFAULT 'NEW'
                            CHECK (status IN ('NEW','ACKNOWLEDGED','ASSIGNED','ACCEPTED','REJECTED',
                                              'IN_PROGRESS','ON_HOLD','RESOLVED','CLOSED','REOPENED')),
    assigned_provider_id uuid REFERENCES service_provider(id),
    allocation_approved_by_resident boolean NOT NULL DEFAULT false,
    preferred_time_window varchar(120),
    sla_due_at          timestamptz,
    service_address_text text NOT NULL,
    service_geo_lat     numeric(9,6),
    service_geo_lng     numeric(9,6),
    service_landmark    varchar(200),
    hold_reason         text,
    resolution_notes    text,
    rating              integer CHECK (rating BETWEEN 1 AND 5),
    rating_comment      text,
    reopened_count      integer NOT NULL DEFAULT 0,
    acknowledged_at     timestamptz,
    assigned_at         timestamptz,
    resolved_at         timestamptz,
    closed_at           timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ticket_reference_code ON ticket (reference_code);
CREATE INDEX IF NOT EXISTS ix_ticket_tenant_status ON ticket (tenant_id, status);
CREATE INDEX IF NOT EXISTS ix_ticket_raised_by ON ticket (raised_by_user_id);
CREATE INDEX IF NOT EXISTS ix_ticket_assigned_provider ON ticket (assigned_provider_id);
CREATE INDEX IF NOT EXISTS ix_ticket_sla_due ON ticket (sla_due_at) WHERE status NOT IN ('RESOLVED','CLOSED');

CREATE TABLE IF NOT EXISTS ticket_attachment (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid NOT NULL REFERENCES tenant(id),
    ticket_id          uuid NOT NULL REFERENCES ticket(id),
    storage_key        text NOT NULL,
    content_type       varchar(100) NOT NULL,
    size_bytes         bigint NOT NULL,
    original_filename  varchar(255),
    uploaded_by_user_id uuid NOT NULL REFERENCES app_user(id),
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_ticket_attachment_ticket ON ticket_attachment (ticket_id);

CREATE TABLE IF NOT EXISTS ticket_status_history (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid NOT NULL REFERENCES tenant(id),
    ticket_id          uuid NOT NULL REFERENCES ticket(id),
    from_status        varchar(16),
    to_status          varchar(16) NOT NULL,
    changed_by_user_id uuid REFERENCES app_user(id),
    actor_role         varchar(20),
    remarks            text,
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_tsh_ticket ON ticket_status_history (ticket_id, created_at);
CREATE TRIGGER trg_tsh_append_only
    BEFORE UPDATE OR DELETE ON ticket_status_history
    FOR EACH ROW EXECUTE FUNCTION sp_block_mutation();

-- ============================================================================
-- Push device tokens  (global — user-scoped)
-- ============================================================================
CREATE TABLE IF NOT EXISTS device_token (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES app_user(id),
    platform     varchar(12) NOT NULL CHECK (platform IN ('IOS','ANDROID','WEB')),
    token        text NOT NULL,
    provider     varchar(12) NOT NULL DEFAULT 'EXPO' CHECK (provider IN ('EXPO','FCM','APNS')),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_device_token_token ON device_token (token);
CREATE INDEX IF NOT EXISTS ix_device_token_user ON device_token (user_id);

-- ============================================================================
-- OTP challenges  (global)
-- ============================================================================
CREATE TABLE IF NOT EXISTS otp_challenge (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_hash   varchar(64) NOT NULL,
    purpose      varchar(24) NOT NULL DEFAULT 'LOGIN'
                     CHECK (purpose IN ('LOGIN','CASH_PAYMENT')),
    code_hash    varchar(128) NOT NULL,
    expires_at   timestamptz NOT NULL,
    attempts     integer NOT NULL DEFAULT 0,
    consumed_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_otp_phone_purpose ON otp_challenge (phone_hash, purpose, created_at DESC);

-- ============================================================================
-- Notification outbox + log
-- ============================================================================
CREATE TABLE IF NOT EXISTS notification_outbox (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type     varchar(60) NOT NULL,
    aggregate_type varchar(40) NOT NULL,
    aggregate_id   uuid NOT NULL,
    tenant_id      uuid REFERENCES tenant(id),
    payload        text NOT NULL,
    status         varchar(16) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','DEAD')),
    attempts       integer NOT NULL DEFAULT 0,
    last_error     text,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    processed_at   timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_outbox_due ON notification_outbox (status, next_attempt_at);

CREATE TABLE IF NOT EXISTS notification (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid REFERENCES tenant(id),
    user_id      uuid NOT NULL REFERENCES app_user(id),
    channel      varchar(12) NOT NULL DEFAULT 'PUSH' CHECK (channel IN ('PUSH','WHATSAPP','SMS')),
    template     varchar(60) NOT NULL,
    title        varchar(160),
    body         text,
    data         text,
    status       varchar(16) NOT NULL DEFAULT 'QUEUED'
                     CHECK (status IN ('QUEUED','SENT','FAILED','SKIPPED')),
    error        text,
    sent_at      timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_notification_user ON notification (user_id, created_at DESC);

-- ============================================================================
-- Audit log  (APPEND-ONLY)
-- ============================================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid REFERENCES tenant(id),
    actor_user_id  uuid REFERENCES app_user(id),
    actor_role     varchar(20),
    action         varchar(80) NOT NULL,
    entity_type    varchar(40),
    entity_id      uuid,
    http_method    varchar(10),
    endpoint       varchar(200),
    request_id     varchar(64),
    success        boolean NOT NULL DEFAULT true,
    error_code     varchar(40),
    duration_ms    integer,
    detail         text,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_audit_tenant_created ON audit_log (tenant_id, created_at DESC);
CREATE TRIGGER trg_audit_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION sp_block_mutation();
