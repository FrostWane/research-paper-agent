ALTER TABLE chat_records ALTER COLUMN paper_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_paper_chunks_embedding_cosine
  ON paper_chunks USING hnsw (embedding vector_cosine_ops)
  WHERE embedding IS NOT NULL;
