CREATE TABLE admin_audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  actor_username VARCHAR(64) NOT NULL,
  action VARCHAR(80) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id VARCHAR(160),
  summary VARCHAR(500) NOT NULL,
  detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_logs_created ON admin_audit_logs(created_at DESC);
CREATE INDEX idx_admin_audit_logs_action_created ON admin_audit_logs(action, created_at DESC);
CREATE INDEX idx_admin_audit_logs_resource_created ON admin_audit_logs(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_admin_audit_logs_actor_created ON admin_audit_logs(actor_id, created_at DESC);
