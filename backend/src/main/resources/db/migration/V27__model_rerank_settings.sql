ALTER TABLE rag_settings
  ADD COLUMN rerank_model_enabled BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN rerank_model_max_candidates INTEGER NOT NULL DEFAULT 8;
