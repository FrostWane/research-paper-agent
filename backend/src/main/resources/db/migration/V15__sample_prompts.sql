CREATE TABLE sample_prompts (
  id BIGSERIAL PRIMARY KEY,
  scope VARCHAR(32) NOT NULL,
  title VARCHAR(120) NOT NULL,
  prompt TEXT NOT NULL,
  description VARCHAR(255),
  sort_order INTEGER NOT NULL DEFAULT 100,
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sample_prompts_scope_enabled_order ON sample_prompts(scope, enabled, sort_order ASC, updated_at DESC);

INSERT INTO sample_prompts(scope, title, prompt, description, sort_order, enabled) VALUES
('PAPER', '核心方法', '请总结这篇论文的核心方法和贡献。', '快速建立单篇论文理解。', 10, true),
('PAPER', '实验设计', '请提炼这篇论文的实验设置、数据集和评价指标。', '聚焦实验和评价依据。', 20, true),
('PAPER', '精读提纲', '请给出适合精读这篇论文的章节阅读顺序。', '辅助安排精读路径。', 30, true),
('PAPER', '局限方向', '请指出这篇论文可能的局限性和后续研究方向。', '用于发现后续研究机会。', 40, true),
('LIBRARY', '方法比较', '请比较这些论文的核心方法差异。', '适合横向比较全库论文。', 10, true),
('LIBRARY', '研究问题', '请梳理当前文献库围绕的主要研究问题。', '帮助提炼文献库主题。', 20, true),
('LIBRARY', '实验综述', '请总结这些论文中常见的数据集、指标和实验结论。', '汇总实验材料和结论。', 30, true),
('LIBRARY', '综述大纲', '请给出一份适合写综述的结构化大纲。', '生成综述写作骨架。', 40, true);
