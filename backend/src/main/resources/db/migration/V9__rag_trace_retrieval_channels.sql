ALTER TABLE rag_traces
  ADD COLUMN retrieval_channels_json JSONB NOT NULL DEFAULT '[]'::jsonb;
