ALTER TABLE parse_jobs
  ADD COLUMN node_spans_json JSONB NOT NULL DEFAULT '[]'::jsonb;
