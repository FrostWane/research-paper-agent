ALTER TABLE rag_traces
  ADD COLUMN retrieval_processors_json JSONB NOT NULL DEFAULT '[]'::jsonb;
