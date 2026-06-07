CREATE TABLE agent_tool_settings (
  tool_name VARCHAR(120) PRIMARY KEY,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
