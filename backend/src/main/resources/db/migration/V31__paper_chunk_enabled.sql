ALTER TABLE paper_chunks
  ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_paper_chunks_enabled
  ON paper_chunks(paper_id, enabled, page_number, chunk_index);
