ALTER TABLE rag_traces
  ADD COLUMN query_intent VARCHAR(64) NOT NULL DEFAULT 'GENERAL_QA',
  ADD COLUMN search_query TEXT,
  ADD COLUMN comparison_requested BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_rag_traces_intent_created ON rag_traces(query_intent, created_at DESC);
