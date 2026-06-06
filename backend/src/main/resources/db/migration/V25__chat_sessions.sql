CREATE TABLE chat_sessions (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  paper_id BIGINT REFERENCES papers(id) ON DELETE CASCADE,
  scope VARCHAR(32) NOT NULL,
  title VARCHAR(160) NOT NULL,
  archived BOOLEAN NOT NULL DEFAULT false,
  message_count INTEGER NOT NULL DEFAULT 0,
  last_message_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_sessions_owner_updated
  ON chat_sessions(owner_id, archived, updated_at DESC);

CREATE INDEX idx_chat_sessions_owner_paper_updated
  ON chat_sessions(owner_id, paper_id, archived, updated_at DESC);

INSERT INTO chat_sessions (
  owner_id,
  paper_id,
  scope,
  title,
  message_count,
  last_message_at,
  created_at,
  updated_at
)
SELECT
  c.owner_id,
  c.paper_id,
  CASE WHEN c.paper_id IS NULL THEN 'LIBRARY' ELSE 'PAPER' END,
  CASE
    WHEN c.paper_id IS NULL THEN '全库问答'
    ELSE left(coalesce(p.title, '单篇问答'), 160)
  END,
  count(*)::integer,
  max(c.created_at),
  min(c.created_at),
  max(c.created_at)
FROM chat_records c
LEFT JOIN papers p ON p.id = c.paper_id
GROUP BY c.owner_id, c.paper_id, p.title;

ALTER TABLE chat_records
  ADD COLUMN session_id BIGINT;

UPDATE chat_records c
SET session_id = s.id
FROM chat_sessions s
WHERE s.owner_id = c.owner_id
  AND (
    (s.paper_id = c.paper_id)
    OR (s.paper_id IS NULL AND c.paper_id IS NULL)
  );

ALTER TABLE chat_records
  ALTER COLUMN session_id SET NOT NULL,
  ADD CONSTRAINT fk_chat_records_session
    FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE;

CREATE INDEX idx_chat_records_session_created
  ON chat_records(session_id, created_at ASC);

ALTER TABLE rag_traces
  ADD COLUMN session_id BIGINT REFERENCES chat_sessions(id) ON DELETE SET NULL;

UPDATE rag_traces t
SET session_id = c.session_id
FROM chat_records c
WHERE c.id = t.chat_record_id;

CREATE INDEX idx_rag_traces_session_created
  ON rag_traces(session_id, created_at DESC);
