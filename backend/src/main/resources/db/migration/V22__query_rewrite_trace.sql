ALTER TABLE rag_settings
  ADD COLUMN query_rewrite_enabled BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN query_rewrite_max_sub_questions INTEGER NOT NULL DEFAULT 3;

ALTER TABLE rag_traces
  ADD COLUMN query_rewrite_enabled BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN rewritten_query TEXT,
  ADD COLUMN query_sub_questions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN query_rewrite_model_name VARCHAR(120);
