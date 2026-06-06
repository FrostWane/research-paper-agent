# Architecture

## Overview

Research Paper Agent 使用前后端分离架构：

```text
React Web / Android
  -> Spring Boot REST API
  -> PostgreSQL + pgvector
  -> MinIO
  -> Spring AI Agent Lite
```

## Backend Modules

- `auth`：注册、登录、JWT、当前用户。
- `paper`：文献 CRUD、搜索、阅读状态、解析状态。
- `file`：PDF 上传、MinIO 存储、鉴权预览。
- `parse`：PDFBox 正文抽取、按页切块、写入 `paper_chunks` 并生成向量索引，同时记录入库节点 span。
- `embedding`：Spring AI embedding 或本地 hashing embedding、pgvector 写入和向量召回。
- `agent`：多 Agent Lite 编排、问答、来源片段和历史记录。
- `common`：统一响应、异常处理、分页对象。

## Agent Design

MVP 采用 Spring Boot 内部多 Agent Lite，并参考 ragent 的节点化框架，把问答链路收敛到轻量 `AgentPipeline`：

```text
AgentOrchestratorService
  -> AgentPipeline
     -> ScopeResolutionNode
     -> ConversationMemoryNode
     -> QueryRewriteNode
     -> QueryPlanningNode
     -> RetrievalNode
     -> AnswerPlanningNode
     -> AnswerGenerationNode
     -> CitationVerificationNode
     -> AnswerQualityEvaluationNode
     -> AnswerFormattingNode
```

`AgentOrchestratorService` 负责事务边界、聊天历史和 RAG Trace 收尾；具体业务步骤由 `AgentNode` 实现。`ConversationMemoryNode` 在范围解析后读取同范围最近问答，按 `rag_settings.memory_history_turns` 和 `memory_max_chars` 渲染滑动窗口记忆，写入 Trace 的 `memoryTurnCount` 和 `memoryChars`，并交给 `AnswerAgent` 填充 `{{conversation_history}}`。`QueryRewriteNode` 在查询规划前运行，读取 `query_rewrite_enabled` 和 `query_rewrite_max_sub_questions`，通过 `QueryRewriteAgent` 调用模型路由生成 JSON 格式的改写查询和子问题；改写失败不会中断链路，会回退原问题并继续检索。`QueryPlanningNode` 会读取 `IntentRouteService` 中启用的意图路由，按关键词规则识别总结、贡献、实验、局限、比较、综述等意图，生成面向召回的 `searchQuery`，并读取管理员维护的查询术语映射，把领域缩写、英文别名和中文同义词扩展进检索式。`RetrievalNode` 内部使用 `RetrieverAgent` 读取 `RagSettingsService` 快照，再交给 `MultiChannelRetrievalEngine` 并行执行向量通道和关键词通道；候选召回数、最终来源数、来源摘录长度、向量权重和关键词权重都来自运行时配置。后处理器链负责通道融合、检索式感知精排、跨论文多样性重排和结果截断，通道与后处理器的候选数、输入输出和耗时都会写入 Trace。`AnswerPlanningNode` 吸收 ragent Prompt Plan 的思路，优先使用意图路由上的 `answerStrategy` 与 `answerContract`，并叠加范围和证据规则，让比较、综述、实验和局限类问题进入不同回答结构。`AnswerAgent` 通过 `AnswerPromptTemplateService` 渲染启用的默认回答模板，把范围、题录、策略、契约、历史对话、问题和来源片段填入 System/User Prompt，再交给模型路由。`AnswerQualityEvaluationNode` 在引用校验后运行，基于来源数量、引用覆盖、材料不足提示和结构化程度生成可解释质量信号，并写入 Trace。`AgentPipeline` 自动记录节点 span，并写入 `rag_traces.node_spans_json`，同时保留检索、生成、校验、评估和格式化的聚合耗时字段。问答记录支持用户反馈有用 / 无用，反馈会落到 `chat_records` 并进入管理员后台聚合，作为后续答案评审、Prompt 调优和模型路由策略的质量信号。后续可以继续插入向量意图树、模型 rerank、跨论文综合、LLM-as-judge 等节点。

默认走兜底回答，保证无模型 API Key 时系统仍然可用。`ModelRoutingService` 会读取 `model_targets` 中启用的候选目标，按任务类型和优先级依次尝试：`QUERY_REWRITE` 服务查询改写，`ANSWER_GENERATION` 服务回答生成，`GENERAL` 作为跨任务通用兜底；`ENV` 目标复用当前环境变量和 Spring AI `ChatClient`，`OPENAI_COMPATIBLE` 目标直接调用兼容 `/v1/chat/completions` 接口。单个目标失败会带 `task_type` 写入 `model_invocations` 并继续尝试下一个目标，全部失败后才走 fallback。管理员后台据此按任务展示模型健康、候选目标配置和 fallback 情况，后续可以继续扩展为熔断半开探测、成本权重和流式首包探测。

`paperId` 为空时进入全库问答，`RetrieverAgent` 会在当前用户所有已解析文献中召回片段；`paperId` 有值时只检索单篇论文。

示例问题作为轻量运营配置保存在 `sample_prompts`，按 `PAPER` / `LIBRARY` 范围分发给阅读页和全库问答页。前端保留默认提示作为兜底，但登录后会优先使用后端启用的推荐问法，让管理员可以持续调整用户入口问题，而无需重新发布前端。

## Ingestion Observability

PDF 入库保留同步执行方式，但解析任务会记录 ragent 风格的轻量节点链路：`prepare -> fetch-pdf -> parse-pdf -> persist-chunks -> index-embeddings -> finalize`。每个节点记录名称、状态、耗时和错误摘要，并保存到 `parse_jobs.node_spans_json`。管理员后台的解析任务面板会显示这些节点标签，用于定位是文件读取、PDF 文本抽取、切块写库还是 embedding 索引慢或失败。

LangGraph 暂不进入第一版主链路。当前 Pipeline 吸收 ragent 的框架感，但保持在 Spring Boot 内部，避免过早引入 AOP Trace、异步流式 Trace 上下文和独立工作流服务。后续如果要做多文献综述、人工确认、可恢复工作流，可以新增独立 `agent-service/`，由 Spring Boot 通过内部 REST 调用。

## Vector Store

项目使用 PostgreSQL + pgvector。`paper_chunks.embedding vector(1536)` 保存 chunk 向量，Flyway V2 创建 HNSW 余弦索引。默认 embedding provider 是 `local`，使用确定性 hashing embedding；设置 `AI_EMBEDDING_PROVIDER` 为非 `local` 后，会优先使用 Spring AI `EmbeddingModel`，失败时退回本地 embedding。关键词检索仍保留为兜底。
