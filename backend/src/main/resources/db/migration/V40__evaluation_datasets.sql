CREATE TABLE evaluation_datasets (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  description TEXT,
  scope VARCHAR(32) NOT NULL DEFAULT 'LIBRARY',
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evaluation_cases (
  id BIGSERIAL PRIMARY KEY,
  dataset_id BIGINT NOT NULL REFERENCES evaluation_datasets(id) ON DELETE CASCADE,
  source_owner_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  paper_id BIGINT REFERENCES papers(id) ON DELETE SET NULL,
  chat_record_id BIGINT REFERENCES chat_records(id) ON DELETE SET NULL,
  rag_trace_id BIGINT REFERENCES rag_traces(id) ON DELETE SET NULL,
  scope VARCHAR(32) NOT NULL DEFAULT 'LIBRARY',
  question TEXT NOT NULL,
  expected_answer TEXT NOT NULL,
  expected_sources_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  tags VARCHAR(500),
  difficulty VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_evaluation_datasets_updated ON evaluation_datasets(updated_at DESC);
CREATE INDEX idx_evaluation_cases_dataset_updated ON evaluation_cases(dataset_id, updated_at DESC);
CREATE INDEX idx_evaluation_cases_trace ON evaluation_cases(rag_trace_id);
CREATE INDEX idx_evaluation_cases_chat ON evaluation_cases(chat_record_id);
CREATE INDEX idx_evaluation_cases_paper ON evaluation_cases(paper_id);
