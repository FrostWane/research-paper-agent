ALTER TABLE rag_traces
  ADD COLUMN tool_executions_json JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE answer_prompt_templates
SET
  system_prompt = replace(
    system_prompt,
    '必须基于给定范围、文献题录和检索片段回答。',
    E'必须基于给定范围、文献题录和检索片段回答。\n如果提供了业务工具结果，可以用它回答文献库统计、解析状态和系统运营类问题。'
  ),
  user_prompt_template = replace(
    user_prompt_template,
    '用户问题：{{question}}',
    E'业务工具结果：\n{{tool_context}}\n用户问题：{{question}}'
  )
WHERE code = 'ACADEMIC_RAG'
  AND user_prompt_template NOT LIKE '%{{tool_context}}%';
