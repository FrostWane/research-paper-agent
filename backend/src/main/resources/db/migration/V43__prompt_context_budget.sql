ALTER TABLE rag_settings
  ADD COLUMN context_token_budget INTEGER NOT NULL DEFAULT 2600;

ALTER TABLE rag_traces
  ADD COLUMN context_token_budget INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN context_estimated_tokens INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN context_truncated BOOLEAN NOT NULL DEFAULT false;
