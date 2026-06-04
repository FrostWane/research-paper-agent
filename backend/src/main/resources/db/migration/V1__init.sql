CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  email VARCHAR(160) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  avatar_url VARCHAR(512),
  role VARCHAR(32) NOT NULL DEFAULT 'USER',
  status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE paper_files (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  original_name VARCHAR(512) NOT NULL,
  object_key VARCHAR(768) NOT NULL UNIQUE,
  content_type VARCHAR(160) NOT NULL,
  size BIGINT NOT NULL,
  sha256 VARCHAR(128) NOT NULL,
  page_count INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE papers (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id BIGINT,
  title VARCHAR(512) NOT NULL,
  authors TEXT,
  venue VARCHAR(255),
  year INTEGER,
  keywords VARCHAR(512),
  abstract_text TEXT,
  note TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'TO_READ',
  process_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  file_id BIGINT REFERENCES paper_files(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_papers_owner_updated ON papers(owner_id, updated_at DESC);
CREATE INDEX idx_papers_status ON papers(owner_id, status);

CREATE TABLE paper_chunks (
  id BIGSERIAL PRIMARY KEY,
  paper_id BIGINT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
  page_number INTEGER NOT NULL,
  chunk_index INTEGER NOT NULL,
  content TEXT NOT NULL,
  embedding vector(1536),
  embedding_id VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (paper_id, page_number, chunk_index)
);

CREATE INDEX idx_paper_chunks_paper ON paper_chunks(paper_id, page_number, chunk_index);

CREATE TABLE chat_records (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  paper_id BIGINT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
  question TEXT NOT NULL,
  answer TEXT NOT NULL,
  sources_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  model_name VARCHAR(120),
  latency_ms INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_records_owner_paper_created ON chat_records(owner_id, paper_id, created_at ASC);
