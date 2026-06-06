ALTER TABLE rag_settings
  ADD COLUMN memory_summary_enabled BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN memory_summary_start_turns INTEGER NOT NULL DEFAULT 6,
  ADD COLUMN memory_summary_max_chars INTEGER NOT NULL DEFAULT 1800;

CREATE TABLE chat_session_summaries (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
  owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  last_chat_record_id BIGINT REFERENCES chat_records(id) ON DELETE SET NULL,
  turn_count INTEGER NOT NULL DEFAULT 0,
  content TEXT NOT NULL,
  method VARCHAR(32) NOT NULL DEFAULT 'HEURISTIC',
  model_name VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_session_summaries_session_latest
  ON chat_session_summaries(session_id, id DESC);

CREATE INDEX idx_chat_session_summaries_owner_updated
  ON chat_session_summaries(owner_id, updated_at DESC);

ALTER TABLE rag_traces
  ADD COLUMN memory_summary_used BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN memory_summary_turn_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN memory_summary_chars INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN memory_summary_method VARCHAR(32) NOT NULL DEFAULT 'NONE',
  ADD COLUMN memory_summary_model_name VARCHAR(120);
