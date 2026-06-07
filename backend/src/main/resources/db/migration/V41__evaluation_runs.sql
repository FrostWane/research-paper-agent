CREATE TABLE evaluation_runs (
  id BIGSERIAL PRIMARY KEY,
  dataset_id BIGINT NOT NULL REFERENCES evaluation_datasets(id) ON DELETE CASCADE,
  triggered_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
  run_name VARCHAR(180),
  status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
  case_count INTEGER NOT NULL DEFAULT 0,
  completed_count INTEGER NOT NULL DEFAULT 0,
  passed_count INTEGER NOT NULL DEFAULT 0,
  average_score NUMERIC(5,2) NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  duration_ms INTEGER NOT NULL DEFAULT 0,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evaluation_case_results (
  id BIGSERIAL PRIMARY KEY,
  run_id BIGINT NOT NULL REFERENCES evaluation_runs(id) ON DELETE CASCADE,
  case_id BIGINT REFERENCES evaluation_cases(id) ON DELETE SET NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'REVIEW',
  score INTEGER NOT NULL DEFAULT 0,
  answer_similarity NUMERIC(5,2) NOT NULL DEFAULT 0,
  source_coverage NUMERIC(5,2) NOT NULL DEFAULT 0,
  matched_sources INTEGER NOT NULL DEFAULT 0,
  expected_sources INTEGER NOT NULL DEFAULT 0,
  expected_answer TEXT,
  actual_answer TEXT,
  expected_sources_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  actual_sources_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  latency_ms INTEGER NOT NULL DEFAULT 0,
  model_name VARCHAR(160),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_evaluation_runs_dataset_created ON evaluation_runs(dataset_id, created_at DESC);
CREATE INDEX idx_evaluation_runs_status_created ON evaluation_runs(status, created_at DESC);
CREATE INDEX idx_evaluation_case_results_run ON evaluation_case_results(run_id, id);
CREATE INDEX idx_evaluation_case_results_case ON evaluation_case_results(case_id);
