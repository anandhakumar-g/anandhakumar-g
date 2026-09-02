-- ============================================================================
-- MVP-5 (A4): provider ratings aggregation. Denormalised onto the global
-- service_provider row (like `tier`) — recomputed from ticket.rating whenever a
-- resident closes a ticket with a rating. NOT RLS-scoped (ADR-011).
-- ============================================================================

ALTER TABLE service_provider ADD COLUMN rating_avg   numeric(3,2);
ALTER TABLE service_provider ADD COLUMN rating_count integer NOT NULL DEFAULT 0;
