CREATE TABLE rag_settings (
  id BIGINT PRIMARY KEY,
  candidate_limit INTEGER NOT NULL DEFAULT 10,
  result_limit INTEGER NOT NULL DEFAULT 5,
  source_excerpt_chars INTEGER NOT NULL DEFAULT 520,
  vector_weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
  keyword_weight DOUBLE PRECISION NOT NULL DEFAULT 0.78,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO rag_settings(id, candidate_limit, result_limit, source_excerpt_chars, vector_weight, keyword_weight)
VALUES (1, 10, 5, 520, 1.0, 0.78);
