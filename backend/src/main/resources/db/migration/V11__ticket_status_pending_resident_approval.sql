-- ============================================================================
-- MVP-5 (A1): resident approval-of-allocation gate.
-- A community can require that a resident approves the assigned helper before
-- the provider is engaged. The ticket parks in a new PENDING_RESIDENT_APPROVAL
-- status while it waits. The value is 25 chars, so the status columns (today
-- varchar(16)) are widened first.
-- ============================================================================

ALTER TABLE ticket                ALTER COLUMN status      TYPE varchar(30);
ALTER TABLE ticket_status_history ALTER COLUMN from_status TYPE varchar(30);
ALTER TABLE ticket_status_history ALTER COLUMN to_status   TYPE varchar(30);

ALTER TABLE ticket DROP CONSTRAINT IF EXISTS ticket_status_check;
ALTER TABLE ticket ADD CONSTRAINT ticket_status_check CHECK (status IN (
    'NEW','ACKNOWLEDGED','PENDING_RESIDENT_APPROVAL','ASSIGNED','ACCEPTED','REJECTED',
    'IN_PROGRESS','ON_HOLD','RESOLVED','CLOSED','REOPENED'));
