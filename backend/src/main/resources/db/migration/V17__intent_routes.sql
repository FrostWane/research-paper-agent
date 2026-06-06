CREATE TABLE intent_routes (
  id BIGSERIAL PRIMARY KEY,
  intent_code VARCHAR(64) NOT NULL,
  label VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  keywords TEXT NOT NULL,
  search_hint VARCHAR(500),
  answer_strategy VARCHAR(64) NOT NULL,
  answer_contract TEXT,
  comparison_enabled BOOLEAN NOT NULL DEFAULT false,
  enabled BOOLEAN NOT NULL DEFAULT true,
  sort_order INTEGER NOT NULL DEFAULT 100,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_intent_routes_code_lower ON intent_routes (lower(intent_code));
CREATE INDEX idx_intent_routes_enabled_order ON intent_routes (enabled, sort_order, id);

INSERT INTO intent_routes(intent_code, label, description, keywords, search_hint, answer_strategy, answer_contract, comparison_enabled, sort_order)
VALUES
  ('COMPARISON', '对比比较', '识别跨论文或方法差异比较类问题。', '比较,差异,对比,不同,compare,comparison,difference', 'method dataset result difference', 'CROSS_PAPER_COMPARISON', '输出结构：先列出参与比较的论文/方法；再用 Markdown 表格比较“研究对象、方法结构、数据/实验、优势、局限”；最后给出适合综述写作的综合判断。', true, 10),
  ('REVIEW_SYNTHESIS', '综述综合', '识别文献综述、大纲、研究脉络和主题综合类问题。', '综述,大纲,研究主题,研究脉络,review,survey,outline', 'method task dataset conclusion', 'REVIEW_SYNTHESIS', '输出结构：按“研究主题、方法家族、证据地图、争议/空白、综述大纲”组织，不要只做逐篇摘要。', true, 20),
  ('CONTRIBUTION', '贡献创新', '识别创新点、贡献点和 novelty 分析问题。', '创新,贡献,contribution,novelty', 'contribution method innovation', 'CONTRIBUTION_ANALYSIS', '输出结构：提炼 2-4 个贡献点；每个贡献点说明解决的问题、技术抓手、证据片段和可能边界。', false, 30),
  ('EXPERIMENT', '实验解读', '识别实验、数据集、指标、baseline 和消融分析问题。', '实验,数据集,指标,结果,消融,evaluation,experiment,dataset,benchmark', 'experiment dataset metric result baseline', 'EXPERIMENT_READING', '输出结构：优先整理数据集、baseline、指标、主要结果和消融/鲁棒性信息；适合时使用表格。', false, 40),
  ('LIMITATION', '局限展望', '识别局限、不足、未来工作和风险边界问题。', '局限,不足,限制,未来,limitation,future work', 'limitation future work', 'LIMITATION_REVIEW', '输出结构：从数据、方法假设、实验验证、泛化与应用风险四个角度分析局限，并给出后续研究建议。', false, 50),
  ('SUMMARY', '结构摘要', '识别总结、概括、摘要和快速理解类问题。', '总结,概括,摘要,summary,summarize', 'abstract method result conclusion', 'STRUCTURED_SUMMARY', '输出结构：用“研究问题、核心方法、主要发现、证据页码、可追问点”做紧凑摘要。', false, 60);
