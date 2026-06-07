CREATE TABLE agent_pipeline_node_settings (
  node_name VARCHAR(120) PRIMARY KEY,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
