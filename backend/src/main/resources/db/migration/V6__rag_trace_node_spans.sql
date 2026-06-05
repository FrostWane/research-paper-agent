ALTER TABLE rag_traces
  ADD COLUMN pipeline_name VARCHAR(120) NOT NULL DEFAULT 'agent-pipeline-v1',
  ADD COLUMN node_spans_json JSONB NOT NULL DEFAULT '[]'::jsonb;
