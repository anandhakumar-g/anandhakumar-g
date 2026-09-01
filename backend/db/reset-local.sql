-- DEV ONLY: wipe the local schema so the next app start re-runs every Flyway migration
-- and BootstrapService re-seeds. Run against the singlepoint (or singlepoint_test) database.
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
ALTER SCHEMA public OWNER TO singlepoint_app;
GRANT ALL ON SCHEMA public TO singlepoint_app;
GRANT ALL ON SCHEMA public TO public;
