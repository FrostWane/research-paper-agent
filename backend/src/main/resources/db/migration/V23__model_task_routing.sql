ALTER TABLE model_targets
  ADD COLUMN task_type VARCHAR(64) NOT NULL DEFAULT 'GENERAL';

ALTER TABLE model_invocations
  ADD COLUMN task_type VARCHAR(64) NOT NULL DEFAULT 'GENERAL';

CREATE INDEX idx_model_targets_task_enabled_priority ON model_targets(task_type, enabled, priority, id);
CREATE INDEX idx_model_invocations_task_target_created ON model_invocations(task_type, target_name, created_at DESC);
