-- Single Point — one-time database bootstrap.
-- Run as a PostgreSQL superuser BEFORE the first application/Flyway start, once per database:
--
--   psql -h localhost -p 5433 -U postgres -d singlepoint      -f backend/db/bootstrap.sql
--   psql -h localhost -p 5433 -U postgres -d singlepoint_test -f backend/db/bootstrap.sql
--
-- Creates the non-superuser application role that Flyway and the app both connect as.
-- A non-superuser role is required for PostgreSQL Row-Level Security to take effect
-- (superusers and BYPASSRLS roles ignore RLS policies).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'singlepoint_app') THEN
        CREATE ROLE singlepoint_app LOGIN PASSWORD 'singlepoint';
    END IF;
END $$;

-- The app role owns the public schema so it owns every table Flyway creates.
-- RLS is then enforced against it via ALTER TABLE ... FORCE ROW LEVEL SECURITY (in V2).
ALTER SCHEMA public OWNER TO singlepoint_app;
GRANT ALL ON SCHEMA public TO singlepoint_app;
GRANT ALL ON ALL TABLES IN SCHEMA public TO singlepoint_app;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO singlepoint_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO singlepoint_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO singlepoint_app;
