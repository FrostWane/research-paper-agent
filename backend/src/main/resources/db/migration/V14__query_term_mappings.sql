CREATE TABLE query_term_mappings (
  id BIGSERIAL PRIMARY KEY,
  term VARCHAR(120) NOT NULL,
  expansions TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_query_term_mappings_term_lower ON query_term_mappings(lower(term));
CREATE INDEX idx_query_term_mappings_enabled ON query_term_mappings(enabled, updated_at DESC);

ALTER TABLE rag_traces
  ADD COLUMN query_expansions_json JSONB NOT NULL DEFAULT '[]'::jsonb;
