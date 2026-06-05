CREATE TABLE parse_jobs (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  paper_id BIGINT REFERENCES papers(id) ON DELETE SET NULL,
  file_id BIGINT REFERENCES paper_files(id) ON DELETE SET NULL,
  paper_title VARCHAR(512) NOT NULL,
  file_name VARCHAR(512) NOT NULL,
  file_size BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  page_count INTEGER NOT NULL DEFAULT 0,
  chunk_count INTEGER NOT NULL DEFAULT 0,
  duration_ms INTEGER NOT NULL DEFAULT 0,
  error_message TEXT,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at TIMESTAMPTZ
);

CREATE INDEX idx_parse_jobs_owner_started ON parse_jobs(owner_id, started_at DESC);
CREATE INDEX idx_parse_jobs_status_started ON parse_jobs(status, started_at DESC);
