CREATE TABLE rag_traces (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  paper_id BIGINT REFERENCES papers(id) ON DELETE SET NULL,
  chat_record_id BIGINT REFERENCES chat_records(id) ON DELETE SET NULL,
  scope VARCHAR(32) NOT NULL,
  question TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  model_name VARCHAR(120),
  source_count INTEGER NOT NULL DEFAULT 0,
  retrieval_ms INTEGER NOT NULL DEFAULT 0,
  generation_ms INTEGER NOT NULL DEFAULT 0,
  verification_ms INTEGER NOT NULL DEFAULT 0,
  formatting_ms INTEGER NOT NULL DEFAULT 0,
  total_ms INTEGER NOT NULL DEFAULT 0,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rag_traces_owner_created ON rag_traces(owner_id, created_at DESC);
CREATE INDEX idx_rag_traces_status_created ON rag_traces(status, created_at DESC);
