ALTER TABLE rag_settings
  ADD COLUMN memory_history_turns INTEGER NOT NULL DEFAULT 4,
  ADD COLUMN memory_max_chars INTEGER NOT NULL DEFAULT 2400;

ALTER TABLE rag_traces
  ADD COLUMN memory_turn_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN memory_chars INTEGER NOT NULL DEFAULT 0;

UPDATE answer_prompt_templates
SET user_prompt_template = replace(
  user_prompt_template,
  '用户问题：{{question}}',
  E'历史对话：\n{{conversation_history}}\n用户问题：{{question}}'
)
WHERE code = 'ACADEMIC_RAG'
  AND user_prompt_template NOT LIKE '%{{conversation_history}}%';
