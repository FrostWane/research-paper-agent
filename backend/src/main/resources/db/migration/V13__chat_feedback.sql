ALTER TABLE chat_records
  ADD COLUMN feedback_score INTEGER,
  ADD COLUMN feedback_comment TEXT,
  ADD COLUMN feedback_at TIMESTAMPTZ;

CREATE INDEX idx_chat_records_feedback ON chat_records(owner_id, feedback_score, feedback_at DESC);
