ALTER TABLE rag_traces
  ADD COLUMN guidance_json JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE answer_prompt_templates
SET system_prompt = replace(
  system_prompt,
  '必须遵守用户消息中的“回答策略”和“输出契约”。',
  E'必须遵守用户消息中的“回答策略”和“输出契约”。\n如果用户消息包含“意图引导”，优先按引导说明收窄问题或给出澄清选项。'
)
WHERE code = 'ACADEMIC_RAG'
  AND system_prompt NOT LIKE '%意图引导%';

UPDATE answer_prompt_templates
SET user_prompt_template = replace(
  user_prompt_template,
  '用户问题：{{question}}',
  E'意图引导：\n{{guidance_context}}\n用户问题：{{question}}'
)
WHERE code = 'ACADEMIC_RAG'
  AND user_prompt_template NOT LIKE '%{{guidance_context}}%';
