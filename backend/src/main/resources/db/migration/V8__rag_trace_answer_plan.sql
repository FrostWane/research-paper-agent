ALTER TABLE rag_traces
  ADD COLUMN answer_strategy VARCHAR(64) NOT NULL DEFAULT 'EVIDENCE_GROUNDED_QA',
  ADD COLUMN answer_contract TEXT;

CREATE INDEX idx_rag_traces_strategy_created ON rag_traces(answer_strategy, created_at DESC);
