-- MVP-9 (A): read indexes for the Super Admin audit-log viewer.
-- audit_log already has ix_audit_tenant_created (tenant_id, created_at DESC) from V1; the
-- viewer also filters/sorts by created_at alone and by actor, and substring-matches action.

CREATE INDEX IF NOT EXISTS ix_audit_created       ON audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_actor_created ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_action        ON audit_log (action);
