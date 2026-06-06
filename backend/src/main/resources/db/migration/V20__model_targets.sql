CREATE TABLE model_targets (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  model_name VARCHAR(160) NOT NULL,
  description VARCHAR(500),
  base_url VARCHAR(500),
  api_key TEXT,
  enabled BOOLEAN NOT NULL DEFAULT true,
  priority INTEGER NOT NULL DEFAULT 100,
  timeout_seconds INTEGER NOT NULL DEFAULT 45,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_model_targets_code_lower ON model_targets (lower(code));
CREATE INDEX idx_model_targets_enabled_priority ON model_targets (enabled, priority, id);

INSERT INTO model_targets(code, provider, model_name, description, base_url, api_key, enabled, priority, timeout_seconds)
VALUES (
  'ENV_DEFAULT',
  'ENV',
  'ENV_CONFIGURED_CHAT_MODEL',
  '使用当前环境变量和 Spring AI 配置的默认模型目标。',
  null,
  null,
  true,
  10,
  45
);
