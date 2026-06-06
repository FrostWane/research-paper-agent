ALTER TABLE rag_settings
  ADD COLUMN chat_rate_limit_enabled BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN chat_rate_limit_global_concurrency INTEGER NOT NULL DEFAULT 12,
  ADD COLUMN chat_rate_limit_user_concurrency INTEGER NOT NULL DEFAULT 2,
  ADD COLUMN chat_rate_limit_user_per_minute INTEGER NOT NULL DEFAULT 20;
