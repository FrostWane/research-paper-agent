ALTER TABLE rag_traces
  ADD COLUMN answer_quality_score INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN answer_quality_label VARCHAR(32) NOT NULL DEFAULT 'UNASSESSED',
  ADD COLUMN answer_quality_notes TEXT,
  ADD COLUMN evaluation_ms INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_rag_traces_quality_created ON rag_traces(answer_quality_label, answer_quality_score DESC, created_at DESC);
