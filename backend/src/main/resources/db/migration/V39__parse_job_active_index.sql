CREATE INDEX idx_parse_jobs_active_owner_paper
  ON parse_jobs(owner_id, paper_id, started_at DESC)
  WHERE paper_id IS NOT NULL
    AND status IN ('QUEUED', 'RUNNING');
