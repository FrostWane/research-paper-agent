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
- `agent`：多 Agent Lite 编排、会话、问答、来源片段和历史记录。
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
     -> ToolExecutionNode
     -> RetrievalNode
     -> IntentGuidanceNode
     -> AnswerPlanningNode
     -> AnswerGenerationNode
     -> CitationVerificationNode
     -> AnswerQualityEvaluationNode
     -> AnswerFormattingNode
```

`AgentOrchestratorService` 负责事务边界、会话解析、聊天历史和 RAG Trace 收尾；具体业务步骤由 `AgentNode` 实现。问答进入链路前会先解析 `chat_sessions`：显式传入 `sessionId` 时校验归属和问答范围，不传时复用当前单篇/全库范围的最近未归档会话，没有会话时自动创建。`AgentPipeline` 在执行每个节点前会读取 `agent_pipeline_node_settings`，范围解析、查询规划、检索、回答规划、回答生成和格式化等主链路节点由服务端锁定，不能停用；会话记忆、查询改写、工具执行、意图引导、引用校验和质量评估等增强节点可由管理员停用，停用后仍写入 `rag_traces.node_spans_json` 的 `SKIPPED` span，引用校验被停用时会把生成答案原样传给后续格式化。`ConversationMemoryNode` 在范围解析后优先读取当前会话长期摘要和最近问答，按 `rag_settings.memory_history_turns`、`memory_max_chars`、`memory_summary_enabled`、`memory_summary_start_turns` 和 `memory_summary_max_chars` 渲染“长期会话摘要 + 近期对话”的压缩记忆，写入 Trace 的 `memoryTurnCount`、`memoryChars`、`memorySummaryUsed`、`memorySummaryTurnCount`、`memorySummaryChars`、`memorySummaryMethod` 和 `memorySummaryModelName`，并交给 `AnswerAgent` 填充 `{{conversation_history}}`；旧记录迁移后会回填默认会话，历史接口仍可按单篇/全库范围读取。成功问答保存后，`ConversationSummaryService` 会把超出近期窗口的旧轮次交给 `ConversationSummaryAgent`，通过 `CONVERSATION_SUMMARY` 模型路由生成长期摘要，模型不可用时退回启发式摘要并写入 `chat_session_summaries`。`QueryRewriteNode` 在查询规划前运行，读取 `query_rewrite_enabled` 和 `query_rewrite_max_sub_questions`，通过 `QueryRewriteAgent` 调用模型路由生成 JSON 格式的改写查询和子问题；改写失败不会中断链路，会回退原问题并继续检索。`QueryPlanningNode` 会读取 `IntentRouteService` 中启用的意图路由，按关键词规则识别总结、贡献、实验、局限、比较、综述等意图，生成面向召回的 `searchQuery`，并读取管理员维护的查询术语映射，把领域缩写、英文别名和中文同义词扩展进检索式。`ToolExecutionNode` 通过 `AgentToolRegistry` 执行匹配到且未被后台停用的内部业务工具，启停状态来自 `agent_tool_settings`；当前 `LibraryStatsTool` 可以读取当前用户的文献、PDF 文件、解析任务、知识片段和问答统计；成功工具输出会汇总为 `toolContext`，交给 `AnswerAgent` 填充 `{{tool_context}}`，并保存到 `rag_traces.tool_executions_json`。`RetrievalNode` 内部使用 `RetrieverAgent` 读取 `RagSettingsService` 快照，再交给 `MultiChannelRetrievalEngine` 并行执行向量通道和关键词通道；候选召回数、最终来源数、来源摘录长度、向量权重和关键词权重都来自运行时配置。后处理器链负责通道融合、检索式感知精排、可选模型重排、跨论文多样性重排和结果截断；`ModelRerankPostProcessor` 在 `rerank_model_enabled` 开启时调用 `RETRIEVAL_RERANK` 模型重排前 N 个候选，模型不可用或输出异常时保留原顺序，通道与后处理器的候选数、输入输出和耗时都会写入 Trace。`IntentGuidanceNode` 在检索后运行，识别“总结一下”“介绍一下”等过泛问题，以及 RAG 无来源且无成功工具结果的证据缺口，生成 `AMBIGUOUS_QUESTION`、`PAPER_EVIDENCE_GAP` 或 `LIBRARY_EVIDENCE_GAP` 引导诊断、澄清说明和建议追问，并保存到 `rag_traces.guidance_json`。`AnswerPlanningNode` 吸收 ragent Prompt Plan 的思路，优先使用意图路由上的 `answerStrategy` 与 `answerContract`，并叠加范围、工具、证据和引导规则，让统计状态、比较、综述、实验、局限和引导澄清类问题进入不同回答结构。`AnswerAgent` 通过 `AnswerPromptTemplateService` 渲染启用的默认回答模板，把范围、题录、策略、契约、历史对话、工具结果、意图引导、问题和来源片段填入 System/User Prompt，再交给模型路由。`AnswerQualityEvaluationNode` 在引用校验后运行，先生成启发式质量信号，再按 `answer_quality_judge_enabled` 可选调用 `QUALITY_EVALUATION` 模型评审答案是否忠于来源、是否回答问题、证据边界是否清晰，并把质量方法、置信度和评审模型写入 Trace；模型不可用或 JSON 解析失败时回退启发式结果。`AgentPipeline` 自动记录节点 span，并写入 `rag_traces.node_spans_json`，同时保留检索、生成、校验、评估和格式化的聚合耗时字段。问答记录支持用户反馈有用 / 无用，反馈会落到 `chat_records` 并进入管理员后台聚合，作为后续答案评审、Prompt 调优和模型路由策略的质量信号。后续可以继续插入向量意图树、外部 MCP 工具、流式任务和跨论文综合等节点。

默认走兜底回答，保证无模型 API Key 时系统仍然可用。`ModelRoutingService` 会读取 `model_targets` 中启用的候选目标，按任务类型和优先级依次尝试：`QUERY_REWRITE` 服务查询改写，`ANSWER_GENERATION` 服务回答生成，`QUALITY_EVALUATION` 服务答案质量评审，`CONVERSATION_SUMMARY` 服务长期会话摘要，`RETRIEVAL_RERANK` 服务检索候选模型重排，`GENERAL` 作为跨任务通用兜底；`ENV` 目标复用当前环境变量和 Spring AI `ChatClient`，`OPENAI_COMPATIBLE` 目标直接调用兼容 `/v1/chat/completions` 接口。单个目标失败会带 `task_type` 写入 `model_invocations` 并继续尝试下一个目标，全部失败后才走 fallback；同一目标连续失败 3 次后，`ModelCircuitBreaker` 会把它置为 `OPEN` 冷却 1 分钟，期间路由记录 `SKIPPED` 并跳过该目标；冷却结束后进入 `HALF_OPEN`，仅放行一次探测，成功则恢复 `CLOSED`，失败则重新打开。管理员后台据此按任务展示模型健康、候选目标配置、fallback、跳过次数、熔断状态、连续失败次数和冷却截止时间，后续可以继续扩展成本权重和流式首包探测。

`paperId` 为空时进入全库问答，`RetrieverAgent` 会在当前用户所有已解析文献中召回片段；`paperId` 有值时只检索单篇论文。

示例问题作为轻量运营配置保存在 `sample_prompts`，按 `PAPER` / `LIBRARY` 范围分发给阅读页和全库问答页。前端保留默认提示作为兜底，但登录后会优先使用后端启用的推荐问法，让管理员可以持续调整用户入口问题，而无需重新发布前端。

流式问答采用轻量 HTTP SSE 包装：`AgentStreamService` 在独立 `agentStreamExecutor` 中调用现有 `AgentOrchestratorService.chat`，向前端发送 `started`、`running`、`final`、`done` 和 `error` 事件。`final` 事件携带完整 `ChatResponse`，表示问答记录、来源片段、Trace 和会话摘要等现有事务链路已经完成；前端在读取流期间展示临时回答和停止等待按钮，完成后再刷新会话列表。同步和流式入口都会经过 `AgentRateLimiterService`，该服务从 `rag_settings` 读取限流开关、全局并发、单用户并发和单用户每分钟请求上限，使用进程内计数器保护模型调用入口；被拒绝的请求返回 HTTP 429，并通过 `AgentOrchestratorService` 写入失败 Trace。这个设计先提供 ragent 式流式交互体验和基础并发护栏，但不改变当前 Pipeline 的事务边界，也不引入独立工作流引擎。

## Runtime Observability

RAG Trace 采用“概览快照 + 分页检索 + 单条详情”的轻量可观测设计。`AgentPipeline` 和各检索后处理器在运行时把节点 span、检索通道、后处理器、查询改写、术语扩展、工具执行、意图引导、长期摘要、答案质量和耗时写入 `rag_traces`；限流拒绝也会落入失败 Trace，便于把 429 与具体用户、会话范围和问题关联起来。`AdminService` 统一解析 JSONB 字段，既向 `/api/admin/overview` 提供最近 Trace、最近解析任务和聊天限流状态，也向 `/api/admin/rag-traces` 提供按状态、范围、会话 ID 和关键词过滤的分页查询，通过 `/api/admin/parse-jobs` 提供按状态和关键词过滤的解析任务分页查询，并通过 `/api/admin/agent-pipeline/nodes` 把 `AgentPipeline` 中的节点定义、`agent_pipeline_node_settings` 启停状态与历史 `node_spans_json` 聚合成节点目录和运行画像，通过 `/api/admin/ingestion-pipeline/nodes` 把 `IngestionPipelineCatalog` 中的入库节点定义与历史 `parse_jobs.node_spans_json` 聚合成入库节点目录和运行画像，通过 `/api/admin/retrieval-channels` 把 `RetrievalChannel` 注册表与历史 `retrieval_channels_json` 聚合成检索通道目录和召回画像，通过 `/api/admin/retrieval-processors` 把 `RetrievalPostProcessor` 注册表与历史 `retrieval_processors_json` 聚合成后处理器目录和输入输出画像，通过 `/api/admin/agent-tools` 把 `AgentToolRegistry` 中的工具定义、`agent_tool_settings` 启停状态与历史 `tool_executions_json` 聚合成工具目录和调用画像，通过 `/api/admin/chunks` 从 `paper_chunks` 暴露可分页检索的知识片段治理视图。管理台的 Trace Explorer 可以展开单条链路，查看检索式、改写、工具、引导、记忆、质量评估、通道、后处理器和节点耗时；解析任务 Explorer 可以追查历史 PDF 入库任务、失败原因和每个入库节点耗时；Agent Pipeline 节点目录用于观察问答链路顺序、节点职责、启停状态、成功/失败/跳过次数、平均耗时和最近运行时间，入库 Pipeline 节点目录用于观察 PDF 解析链路的节点职责、长期失败风险和耗时热点，检索通道与后处理器目录用于观察向量/关键词召回、通道融合、规则精排、模型重排、多样性重排和结果截断的候选规模、输入输出规模、成功/失败次数和最近运行时间，Agent 工具目录用于观察工具触发规则、启停状态、成功/失败次数、平均耗时和最近调用时间，知识片段治理用于检查入库后的正文预览、页码、chunk 序号、向量化状态和启用状态；被禁用的 Agent 增强节点保留在目录中但后续链路记录为 `SKIPPED`，被禁用的工具保留在目录中但不参与后续问答链路，被禁用的片段保留在后台治理视图中但 `KeywordRetrievalChannel` 与 `PaperChunkVectorRepository` 都会排除它，便于排查召回不足、问题过泛、生成过慢、质量风险、工具异常、入库异常、限流拒绝和模型路由异常。

## Ingestion Observability

PDF 入库保留同步执行方式，但解析任务会记录 ragent 风格的轻量节点链路：`prepare -> fetch-pdf -> parse-pdf -> persist-chunks -> index-embeddings -> finalize`。每个节点记录名称、状态、耗时和错误摘要，并保存到 `parse_jobs.node_spans_json`。管理员后台的解析任务 Explorer 会显示这些节点标签，并支持按状态、文件名、文献标题、用户名、节点内容或错误信息检索，用于定位是文件读取、PDF 文本抽取、切块写库还是 embedding 索引慢或失败。

LangGraph 暂不进入第一版主链路。当前 Pipeline 吸收 ragent 的框架感，但保持在 Spring Boot 内部，避免过早引入 AOP Trace、复杂异步流式 Trace 上下文和独立工作流服务；流式体验先由 Spring MVC SSE 对完整问答调用做阶段事件包装。后续如果要做多文献综述、人工确认、可恢复工作流，可以新增独立 `agent-service/`，由 Spring Boot 通过内部 REST 调用。

## Vector Store

项目使用 PostgreSQL + pgvector。`paper_chunks.embedding vector(1536)` 保存 chunk 向量，Flyway V2 创建 HNSW 余弦索引。`paper_chunks.enabled` 控制片段是否参与检索，默认启用；管理员禁用脏片段后，关键词检索和向量检索都会跳过它。默认 embedding provider 是 `local`，使用确定性 hashing embedding；设置 `AI_EMBEDDING_PROVIDER` 为非 `local` 后，会优先使用 Spring AI `EmbeddingModel`，失败时退回本地 embedding。关键词检索仍保留为兜底。
