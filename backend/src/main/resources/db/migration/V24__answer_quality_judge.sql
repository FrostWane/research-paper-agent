ALTER TABLE rag_settings
  ADD COLUMN answer_quality_judge_enabled BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE rag_traces
  ADD COLUMN answer_quality_method VARCHAR(32) NOT NULL DEFAULT 'HEURISTIC',
  ADD COLUMN answer_quality_judge_enabled BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN answer_quality_judge_model_name VARCHAR(120),
  ADD COLUMN answer_quality_confidence INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_rag_traces_quality_method_created ON rag_traces(answer_quality_method, created_at DESC);
