-- ============================================================================
-- MVP-7 (C): a community spans multiple physical locations. Flats hang off a
-- location; address / geo defaults flow from the flat's location.
--
-- RLS is enabled on `location` AFTER the backfill so the non-superuser Flyway
-- role (no BYPASSRLS) is not blocked by the forced policy while seeding.
-- ============================================================================

CREATE TABLE location (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenant(id),
    label       varchar(120) NOT NULL,
    address_enc text,
    geo_lat     numeric(9,6),
    geo_lng     numeric(9,6),
    pincode     varchar(12),
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_location_tenant ON location (tenant_id);

ALTER TABLE flat ADD COLUMN location_id uuid REFERENCES location(id);

-- One default location per existing community, and point every flat at it.
INSERT INTO location (tenant_id, label, geo_lat, geo_lng, pincode)
SELECT t.id, COALESCE(NULLIF(t.locality, ''), NULLIF(t.city, ''), 'Main'),
       t.geo_lat, t.geo_lng, t.pincode
FROM tenant t;

UPDATE flat f
SET location_id = (SELECT l.id FROM location l WHERE l.tenant_id = f.tenant_id LIMIT 1);

-- Now lock the table down to the same tenant-isolation policy as flat/invite_code.
ALTER TABLE location ENABLE ROW LEVEL SECURITY;
ALTER TABLE location FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS location_tenant_isolation ON location;
CREATE POLICY location_tenant_isolation ON location
    USING (
        current_setting('app.current_tenant_id', true) = '*'
        OR tenant_id::text = current_setting('app.current_tenant_id', true)
    )
    WITH CHECK (
        current_setting('app.current_tenant_id', true) = '*'
        OR tenant_id::text = current_setting('app.current_tenant_id', true)
    );
