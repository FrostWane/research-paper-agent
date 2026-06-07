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
  'PAPER_PARSE_STATUS',
  '单篇解析状态',
  '识别当前单篇论文的解析、入库、chunk、向量索引和可检索状态问题，并显式绑定单篇解析状态工具。',
  '这篇论文,当前论文,当前文献,本篇,单篇论文,本文解析,没解析,无法解析,解析失败,失败原因,可检索,无法检索,检索状态,pdf页数,入库详情,chunk,chunks,知识片段,向量化,embedding,paper parse status,parse failure,index status',
  'paper parse status chunk embedding index job failure',
  'TOOL_GROUNDED_STATUS',
  '输出结构：先说明当前论文是否可参与检索；再列出 PDF、解析任务、chunk、向量化覆盖和失败信息；不要扩展到工具结果之外的论文内容判断。',
  false,
  6,
  'paper-parse-status'
WHERE NOT EXISTS (
  SELECT 1 FROM intent_routes WHERE lower(intent_code) = lower('PAPER_PARSE_STATUS')
);
