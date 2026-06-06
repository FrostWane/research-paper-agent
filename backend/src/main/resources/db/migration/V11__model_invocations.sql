CREATE TABLE model_invocations (
  id BIGSERIAL PRIMARY KEY,
  provider VARCHAR(64) NOT NULL,
  model_name VARCHAR(160) NOT NULL,
  target_name VARCHAR(220) NOT NULL,
  status VARCHAR(32) NOT NULL,
  latency_ms INTEGER NOT NULL DEFAULT 0,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_model_invocations_target_created ON model_invocations(target_name, created_at DESC);
CREATE INDEX idx_model_invocations_status_created ON model_invocations(status, created_at DESC);
