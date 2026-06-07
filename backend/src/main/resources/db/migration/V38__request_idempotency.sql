CREATE TABLE request_idempotency (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  endpoint VARCHAR(160) NOT NULL,
  request_key VARCHAR(160) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  response_type VARCHAR(240) NOT NULL,
  status VARCHAR(32) NOT NULL,
  response_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_request_idempotency_owner_endpoint_key
  ON request_idempotency(owner_id, endpoint, request_key);
CREATE INDEX idx_request_idempotency_expires ON request_idempotency(expires_at);
