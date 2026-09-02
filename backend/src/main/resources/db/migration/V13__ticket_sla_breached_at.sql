-- ============================================================================
-- MVP-5 (A2): SLA breach tracking. sla_due_at is already set at raise from
-- category.sla_hours; this records the first time a still-open ticket is found
-- past that due time. Alert + flag only — no status/priority/routing change.
-- ============================================================================

ALTER TABLE ticket ADD COLUMN sla_breached_at timestamptz;

-- Scan index for the breach job: open tickets that are due and not yet flagged.
CREATE INDEX ix_ticket_sla_breach_scan ON ticket (sla_due_at)
    WHERE sla_breached_at IS NULL AND status NOT IN ('RESOLVED', 'CLOSED');
