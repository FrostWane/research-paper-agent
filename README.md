# Research Paper Agent

Research Paper Agent 是一个科研文献阅读与问答工作台。项目从 CloudBase Serverless 原型升级为 React + Spring Boot + PostgreSQL/pgvector + MinIO 的全栈系统，支持 PDF 上传、文献管理、在线预览、问答历史和 Spring AI 多 Agent Lite 架构。

## 项目结构

```text
research-paper-agent/
  backend/      Spring Boot REST API
  frontend/     React + Vite 科研工作台
  docs/         架构、API 和移动端路线
  PLAN.md       三阶段实施计划
```

## 快速启动

复制环境变量：

```bash
Copy-Item .env.example .env
```

启动基础服务和应用：

```bash
docker compose up --build
```

访问：

- Web: http://localhost:15173
- Backend: http://localhost:18080
- Swagger: http://localhost:18080/swagger-ui/index.html
- MinIO Console: http://localhost:9001

## 本地开发

前端：

```bash
cd frontend
npm install
npm run dev
```

后端建议使用 Java 21。仓库包含 Maven Wrapper：

```bash
cd backend
.\mvnw.cmd test
```

若本机没有 Java 21，可使用 Docker 构建：

```bash
docker run --rm -v ${PWD}/backend:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

## Agent 与 RAG

默认提供可运行的兜底 Agent。配置 OpenAI-compatible 参数后，后端会通过 Spring AI `ChatClient` 调用真实模型：

```env
AI_PROVIDER=openai
OPENAI_API_KEY=your-key
OPENAI_BASE_URL=https://api.openai.com
OPENAI_CHAT_MODEL=gpt-4o-mini
```

向量数据库采用 PostgreSQL + pgvector。PDF 解析后会写入 `paper_chunks.embedding`，并建立 HNSW 余弦索引。默认使用本地 1536 维 hashing embedding，避免依赖额外云服务；如果要切换到 Spring AI 远程 embedding，可设置：

```env
AI_EMBEDDING_PROVIDER=openai
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
```

Agent 支持两种问答范围：

- 单篇论文问答：请求携带 `paperId`。
- 全库问答：`paperId` 传 `null`，在当前用户所有已解析文献中检索来源片段。

阅读页和全库问答页的推荐问题来自 `sample_prompts`，默认内置单篇/全库两组提示词；管理员可以在后台调整标题、问题、排序和启用状态，让常用问法可运营而不是固定写死在前端。

Agent 编排参考 ragent 的节点化思路，后端使用轻量 `AgentPipeline` 串联 `ScopeResolutionNode -> ConversationMemoryNode -> QueryRewriteNode -> QueryPlanningNode -> RetrievalNode -> AnswerPlanningNode -> AnswerGenerationNode -> CitationVerificationNode -> AnswerQualityEvaluationNode -> AnswerFormattingNode`。当前不引入 AOP Trace 和异步流式上下文，先保留简单、可扩展、易调试的 Spring Bean 节点结构。`ConversationMemoryNode` 会按当前单篇或全库范围读取最近问答，生成受字符上限保护的滑动窗口记忆，并通过 `{{conversation_history}}` 注入回答 Prompt；记忆轮数和最大字符数同样来自 `rag_settings`。`QueryRewriteNode` 参考 ragent 的问题改写与拆分能力，使用模型路由把用户原问题改写成更适合检索的查询，并记录改写文本、子问题和改写模型；模型不可用或 JSON 解析失败时会回退原问题。`QueryPlanningNode` 会读取 `intent_routes`，按关键词规则识别问题意图、拼接检索提示，并标记是否需要跨文献比较；管理员配置的查询术语映射会在此阶段把领域缩写、别名和同义词注入检索式，并写入 Trace；`RetrievalNode` 内部使用多通道检索引擎混合向量检索和关键词检索，并通过后处理器链完成通道融合、检索式感知精排、跨论文多样性重排和结果截断；RAG 候选数、最终来源数、来源摘录长度、向量权重、关键词权重、查询改写开关、子问题上限和答案模型评审开关来自 `rag_settings`，管理员修改后会被检索链路运行时读取；`AnswerPlanningNode` 会根据意图路由配置和证据状态选择回答策略与输出契约，让比较、综述、实验、局限等问题走不同回答结构。`AnswerAgent` 会读取启用的默认 `answer_prompt_templates`，用 `{{scope}}`、`{{paper_metadata}}`、`{{answer_strategy}}`、`{{answer_contract}}`、`{{conversation_history}}`、`{{question}}`、`{{sources}}` 渲染 System Prompt 和用户消息；`AnswerQualityEvaluationNode` 会先生成启发式质量信号，再可选调用 `QUALITY_EVALUATION` 模型做 LLM-as-judge 评审，并把质量分、标签、方法、置信度、评审模型和解释说明写入 Trace。模型调用通过轻量 `ModelRoutingService` 按任务类型读取 `model_targets`，优先尝试对应任务目标，再使用通用目标兜底，单个目标失败会继续尝试下一个，全部失败后才走 fallback；成功、失败和 fallback 都会带任务类型写入模型调用日志，便于后台分别观察查询改写、回答生成、质量评估等环节的模型健康；用户也可以对回答标记有用 / 无用，形成可被后台聚合的答案质量反馈。

回答下方会展示来源卡片，点击来源可回到对应论文并跳转 PDF 页码。已解析论文也可以取消解析，从知识库移除文本片段和向量索引，同时保留 PDF 文件与文献记录。

## 管理后台

`ADMIN` 用户可以进入管理后台查看系统级指标：用户数、文献数、PDF 存储、知识片段、问答次数、答案反馈、查询术语映射、意图路由、回答模板、模型目标、示例问题、模型调用聚合、模型健康、解析任务、最近文献、RAG Trace 和用户资源使用情况。后台支持配置 RAG 检索参数、查询改写、模型质量评审、会话记忆窗口、维护意图路由、回答 Prompt 模板、按任务类型维护模型路由目标、术语映射和示例问题，也支持启用 / 禁用普通用户。解析任务会记录 PDF 入库状态、页数、片段数、总耗时、失败信息，以及准备、读取 PDF、抽取文本、写入片段、向量索引、完成等入库节点耗时；模型健康会按任务类型展示目标模型最近状态、成功/失败/fallback 次数和平均延迟；RAG Trace 会记录最近问答的范围、问题意图、检索式、改写查询、子问题、术语扩展、回答策略、输出契约、检索通道、检索后处理器、记忆轮数、记忆字符数、来源数量、答案质量分、质量标签、质量方法、评审置信度、评审模型、质量解释、Pipeline 节点耗时、检索耗时、生成耗时、评估耗时和总耗时。
