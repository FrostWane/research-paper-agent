ALTER TABLE agent_tool_settings
  ADD COLUMN minimum_role VARCHAR(32) NOT NULL DEFAULT 'USER';
