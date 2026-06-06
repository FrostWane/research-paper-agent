CREATE TABLE answer_prompt_templates (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  system_prompt TEXT NOT NULL,
  user_prompt_template TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true,
  default_template BOOLEAN NOT NULL DEFAULT false,
  sort_order INTEGER NOT NULL DEFAULT 100,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_answer_prompt_templates_code_lower ON answer_prompt_templates (lower(code));
CREATE INDEX idx_answer_prompt_templates_enabled_default ON answer_prompt_templates (enabled, default_template, sort_order, id);

INSERT INTO answer_prompt_templates(code, name, description, system_prompt, user_prompt_template, enabled, default_template, sort_order)
VALUES (
  'ACADEMIC_RAG',
  '科研 RAG 默认模板',
  '面向论文精读、全库综合和证据约束回答的默认模板。',
  '你是 Research Paper Agent 的论文精读 Agent。
必须基于给定范围、文献题录和检索片段回答。
必须遵守用户消息中的“回答策略”和“输出契约”。
不要在最终答案中复述“回答策略”“输出契约”等内部字段名。
如果材料不足，明确说明“材料不足”，不要编造实验结果。
用结构化中文 Markdown 输出，并尽量附上论文标题和来源页码。',
  '回答范围：{{scope}}
{{paper_metadata}}
回答策略：{{answer_strategy}}
输出契约：
{{answer_contract}}
用户问题：{{question}}
检索片段：
{{sources}}',
  true,
  true,
  10
);
