# Research Paper Agent 全栈 + Android 路线计划

## Summary

- 在 `D:\code\research-paper-agent` 新建项目，旧的 `research-paper-agent-serverless` 只作为业务流程和 UI 交互参考。
- 项目分三阶段：Web 全栈 MVP、RAG/多 Agent 增强、Android 简化端。
- 后端统一采用 Spring Boot REST API，Web 和 Android 共用同一套接口。
- Agent 框架使用 **Spring AI**，向量数据库使用 **PostgreSQL + pgvector**。
- LangGraph 不进第一版主链路，后续可作为独立 `agent-service/` 增强。
- GitHub remote：`https://github.com/FrostWane/research-paper-agent.git`。

## Phase 1: Web 全栈 MVP

- 前端：
  - `frontend/` 使用 React + Vite + TypeScript + Axios + React Router + Zustand。
  - 做高级科研工作台风格，不做营销首页。
  - 核心页面：登录注册、文献库、上传文献、PDF 阅读 + Agent 面板、问答历史。
  - 迁移旧项目可复用能力：PDF.js 预览、题录自动识别、Markdown 渲染、IndexedDB PDF 缓存。
- 后端：
  - `backend/` 使用 Spring Boot 3 + Java 21 + Spring Security JWT + Spring Data JPA + Flyway + Springdoc OpenAPI。
  - 实现用户注册登录、JWT 鉴权、文献 CRUD、PDF 上传、PDF 鉴权预览、问答历史保存。
  - MinIO 存 PDF 文件，PostgreSQL 存业务数据。
- API：
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `GET /api/auth/me`
  - `GET /api/papers`
  - `POST /api/papers`
  - `GET /api/papers/{id}`
  - `PUT /api/papers/{id}`
  - `DELETE /api/papers/{id}`
  - `PATCH /api/papers/{id}/status`
  - `POST /api/files/papers`
  - `GET /api/files/papers/{fileId}/preview`
  - `POST /api/agent/chat`
  - `POST /api/agent/chat/stream`
  - `GET /api/agent/sessions`
  - `POST /api/agent/sessions`
  - `PATCH /api/agent/sessions/{id}`
  - `GET /api/agent/sessions/{id}/chats`
  - `GET /api/papers/{paperId}/chats`

## Phase 2: RAG + 多 Agent Lite

- 数据能力：
  - PostgreSQL 启用 pgvector。
  - PDFBox 提取 PDF 文本，按页码和块索引写入 `paper_chunks`。
  - embedding 存入 pgvector，支持按当前文献或当前用户全库检索相关片段。
- Agent 设计：
  - 使用 Spring AI + `AgentPipeline` + `AgentOrchestratorService`。
  - 参考 ragent 的节点化框架，先采用轻量 Spring Bean Pipeline，而不是直接引入 AOP Trace 和异步流式上下文。
  - `ScopeResolutionNode`：解析单篇 / 全库问答范围。
  - `ConversationMemoryNode`：优先按当前会话读取长期摘要和最近历史，生成“摘要 + 近期对话”的压缩记忆并注入回答 Prompt。
  - `ConversationSummaryService`：按阈值把超出近期窗口的旧轮次压缩为长期摘要，模型不可用时启发式兜底。
  - `AgentRateLimiterService`：保护同步和流式聊天入口，支持全局并发、用户并发和用户每分钟请求限流。
  - `QueryRewriteNode`：参考 ragent 的问题改写与拆分，把用户问题转换为更适合检索的查询和子问题。
  - `QueryPlanningNode`：识别问题意图，生成检索式，标记跨文献比较需求。
  - `ToolExecutionNode`：通过内部 `AgentToolRegistry` 匹配业务工具，当前支持文献库统计和单篇解析状态工具结果注入。
  - `RetrievalNode`：通过多通道检索引擎混合向量检索和关键词检索，并用后处理器链完成融合、检索式感知精排、可选模型重排、多样性重排和截断。
  - `IntentGuidanceNode`：参考 ragent 引导式问答，在问题过泛或证据缺口时生成澄清说明和建议追问。
  - `AnswerPlanningNode`：根据问题意图、问答范围、工具结果、证据状态和引导需求选择回答策略，并生成输出契约。
  - `AnswerGenerationNode`：基于片段生成结构化 Markdown 回答。
  - `CitationVerificationNode`：检查回答是否引用来源页码，材料不足时明确说明。
  - `AnswerQualityEvaluationNode`：先生成启发式质量信号，再可选调用模型做 LLM-as-judge 质量评审。
  - `AnswerFormattingNode`：统一输出格式。
- 新增 API：
  - `POST /api/papers/{id}/parse`
  - `DELETE /api/papers/{id}/parse`
  - `GET /api/papers/{id}/parse-status`
  - `GET /api/agent/chats`
  - `PATCH /api/agent/chats/{id}/feedback`
  - `GET /api/agent/sample-prompts`
  - `GET/POST/PATCH/DELETE /api/admin/query-term-mappings`
  - `GET/POST/PATCH/DELETE /api/admin/sample-prompts`
- 模型接入：
  - 默认支持无 API Key 的兜底 Agent。
  - 配置 OpenAI-compatible API 后启用 Spring AI `ChatClient`、Embedding 和 RAG 检索。
  - 管理员可维护模型路由目标，按查询改写、回答生成、质量评估、会话摘要、检索重排、通用兜底等任务类型尝试环境默认模型或 OpenAI-compatible 模型，失败后自动切换下一个目标。
  - 可接 OpenAI、DeepSeek、通义兼容接口或本地兼容网关。
- 工程化增强：
  - 参考 Ragent 的控制台信息架构，新增轻量管理员后台。
  - 管理员可查看用户、文献、存储、索引、问答、答案反馈、查询术语映射、模型目标、示例问题和模型调用聚合。
  - 管理员可维护意图路由规则，用关键词、检索提示、回答策略和输出契约驱动 QueryPlanning / AnswerPlanning。
  - 管理员可维护回答 Prompt 模板，用占位符渲染 AnswerAgent 的 System Prompt 和 User Prompt，其中 `{{tool_context}}` 可接收业务工具执行结果，`{{guidance_context}}` 可接收意图引导说明。
  - 新增 Agent Pipeline 节点目录，后台可查看问答链路节点顺序、职责、成功 / 失败运行次数、平均耗时和最近运行时间，后续可扩展为可配置节点启停和条件编排。
  - 新增入库 Pipeline 节点目录，后台可查看 PDF 入库链路节点顺序、职责、成功 / 失败运行次数、平均耗时和最近运行时间，后续可扩展为可配置入库编排。
  - 新增 Agent 工具目录，后台可查看内部工具触发规则、来源、成功 / 失败调用次数、平均耗时和最近调用时间，后续可扩展为工具启停、权限和外部 MCP。
  - 新增知识片段治理，后台可按文献 ID 或关键词分页检查 chunk 正文、页码、序号、向量化状态和启用状态，并可禁用脏片段让关键词/向量检索跳过它，辅助排查入库质量和召回材料问题。
  - 管理员可配置 RAG 候选召回数、最终来源数、来源摘录长度、向量通道权重、关键词通道权重、记忆轮数、记忆字符上限、查询改写开关、子问题上限、模型质量评审开关、模型重排开关和重排候选窗口。
  - 新增轻量任务感知模型路由和模型调用日志，管理员可查看并维护目标模型，按任务观察最近状态、成功 / 失败 / fallback 次数和平均延迟。
  - 新增聊天入口限流设置和运行态概览，管理员可配置全局并发、单用户并发和单用户每分钟请求上限，限流拒绝返回 429 并写入失败 Trace。
  - 新增轻量 SSE 问答流，前端可展示 Agent 连接、运行、最终保存和停止等待状态，同时保留非流式问答接口兼容调用方。
  - 管理员可启用 / 禁用普通用户。
  - 新增简化 RAG Trace，记录会话、问答范围、问题意图、检索式、改写查询、子问题、业务工具执行、意图引导、回答策略、输出契约、检索通道、检索后处理器、会话记忆轮数、会话记忆字符数、长期摘要使用情况、摘要轮数、摘要字符数、摘要方法、摘要模型、来源数量、答案质量分、质量标签、质量方法、评审模型、评审置信度、质量解释、Pipeline 节点 span、检索耗时、生成耗时、校验耗时、评估耗时、格式化耗时、总耗时和失败信息，并提供后台 Trace Explorer 支持状态、范围、会话 ID 和关键词分页检索历史链路。
  - 新增解析任务日志，记录 PDF 入库状态、页数、片段数、耗时、失败信息和入库节点 span。

## Phase 3: Android 简化端

- 技术路线：
  - 新增 `android/`，使用 Android 原生 Kotlin。
  - Android 只消费后端 REST API，不单独实现业务后端。
  - 使用 JWT 登录态，token 存本地安全存储。
- 移动端范围：
  - 登录 / 退出登录。
  - 文献列表、搜索、状态筛选。
  - 文献详情。
  - PDF 简化预览。
  - 当前文献提问。
  - 问答历史查看。
- 暂不进入 Android 第一版：
  - 手机端 PDF 上传。
  - 文献编辑复杂表单。
  - 多文献综述。
  - 管理员能力。
  - 离线全文缓存。
- Android UI：
  - 简化为底部导航或单 Activity 多页面。
  - 保留科研工具气质，但减少桌面端复杂并列布局。
  - PDF 阅读页采用“上方 PDF / 下方提问入口”或页面切换，不强行复刻桌面双栏。

## Data Model

- `users`：账号、邮箱、BCrypt 密码、角色、状态、时间戳。
- `papers`：用户归属、标题、作者、年份、关键词、摘要、备注、阅读状态、处理状态、文件引用。
- `paper_files`：用户归属、原始文件名、MinIO object key、MIME、大小、SHA-256、页数。
- `paper_chunks`：文献 ID、页码、块序号、文本内容、embedding vector、是否参与检索。
- `chat_sessions`：用户归属、可为空的文献 ID、问答范围、标题、归档状态、消息数、最后问答时间，用于组织单篇/全库多轮会话。
- `chat_session_summaries`：会话长期摘要、已摘要到的问答记录、摘要轮数、摘要方法和摘要模型，用于长对话记忆压缩。
- `chat_records`：用户归属、会话 ID、可为空的文献 ID、问题、回答、来源 JSON、模型名、耗时。
- `rag_traces`：用户归属、可为空的会话 ID、可为空的文献 ID、可为空的聊天记录 ID、问答范围、状态、模型名、Pipeline 名称、问题意图、检索式、改写查询、子问题 JSON、业务工具执行 JSON、意图引导 JSON、回答策略、输出契约、检索通道 JSON、检索后处理器 JSON、节点 span JSON、会话记忆轮数、会话记忆字符数、长期摘要使用情况、摘要轮数、摘要字符数、摘要方法、摘要模型、来源数量、答案质量分、质量标签、质量方法、评审模型、评审置信度、质量解释、分段耗时、错误信息。
- `parse_jobs`：用户归属、可为空的文献/文件 ID、文献标题快照、文件名、状态、页数、片段数、耗时、失败信息。
- `rag_settings`：全局 RAG 运行时参数，包含候选召回数、最终来源数、来源摘录长度、检索通道融合权重、会话记忆窗口、长期摘要压缩、查询改写配置、模型质量评审开关、模型重排配置和聊天入口限流配置。
- `intent_routes`：轻量意图路由规则，包含意图标识、关键词、检索提示、回答策略、输出契约、比较标记和启用状态。
- `answer_prompt_templates`：回答生成模板，包含 System Prompt、User Prompt 模板、默认模板标记和启用状态，支持 `{{tool_context}}` 注入业务工具结果，支持 `{{guidance_context}}` 注入意图引导说明。
- `model_targets`：模型路由候选目标，包含任务类型、供应商、模型名、Base URL、密钥、优先级、超时和启用状态。
- `model_invocations`：模型调用日志，包含任务类型、供应商、模型名、目标名、状态、延迟、错误和调用时间。

## Test Plan

- Web：
  - `npm run build` 通过。
  - 登录、上传 PDF、创建文献、PDF 预览、提问、历史记录完整闭环。
  - 桌面和移动 Web 视口无重叠、按钮文字不溢出。
- Backend：
  - 注册登录、JWT、401、越权访问。
  - 文献 CRUD、搜索分页、状态更新。
  - PDF 上传校验：非 PDF、超大文件、正常 PDF。
  - PDF 解析、分块入库、pgvector 检索。
  - Agent 问答、来源页码、会话化历史保存。
- Android：
  - 登录态持久化。
  - 文献列表和详情加载。
  - PDF 预览可打开。
  - 提问和历史记录可用。
  - token 失效后回到登录页。
- 集成：
  - `docker compose up` 后可访问前端、后端 Swagger、PostgreSQL/pgvector、MinIO。
  - Web 和 Android 共用同一套 API。
  - 完成首个 commit 后推送到 GitHub remote。

## Assumptions

- 第一阶段先完成 Web 全栈 MVP。
- 第二阶段再做 RAG、多 Agent Lite 和引用页码。
- 第三阶段做 Android 原生简化端，范围锁定为“阅读闭环”，不包含上传。
- 本机默认 Java 8 不作为后端运行依据，使用 Maven Wrapper + Docker Java 21 环境验证。
- 如果 GitHub push 时本机未登录凭据，则保留本地 commit，并提示登录后重试 push。
