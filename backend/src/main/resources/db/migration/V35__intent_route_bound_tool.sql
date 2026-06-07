ALTER TABLE intent_routes
  ADD COLUMN bound_tool_name VARCHAR(120);

INSERT INTO intent_routes(
  intent_code,
  label,
  description,
  keywords,
  search_hint,
  answer_strategy,
  answer_contract,
  comparison_enabled,
  sort_order,
  bound_tool_name
)
SELECT
  'LIBRARY_OPERATIONS',
  '文献库运营统计',
  '识别文献库统计、解析状态、文件数量、知识片段和问答历史等运营类问题，并显式绑定内部统计工具。',
  '统计,数量,多少,几篇,概览,状态,解析率,已解析,待解析,pdf,文件,知识片段,问答历史,入库,stats,status,count,overview',
  'library stats parse status file chunk chat history',
  'TOOL_GROUNDED_STATUS',
  '输出结构：直接回答工具能够证明的统计或状态；必要时列出关键计数；不要声称工具结果之外的论文内容结论。',
  false,
  5,
  'library-stats'
WHERE NOT EXISTS (
  SELECT 1 FROM intent_routes WHERE lower(intent_code) = lower('LIBRARY_OPERATIONS')
);
