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
  - `QueryPlanningNode`：识别问题意图，生成检索式，标记跨文献比较需求。
  - `RetrievalNode`：通过多通道检索引擎混合向量检索和关键词检索，检索论文相关片段。
  - `AnswerPlanningNode`：根据问题意图、问答范围和证据状态选择回答策略，并生成输出契约。
  - `AnswerGenerationNode`：基于片段生成结构化 Markdown 回答。
  - `CitationVerificationNode`：检查回答是否引用来源页码，材料不足时明确说明。
  - `AnswerFormattingNode`：统一输出格式。
- 新增 API：
  - `POST /api/papers/{id}/parse`
  - `DELETE /api/papers/{id}/parse`
  - `GET /api/papers/{id}/parse-status`
  - `GET /api/agent/chats`
- 模型接入：
  - 默认支持无 API Key 的兜底 Agent。
  - 配置 OpenAI-compatible API 后启用 Spring AI `ChatClient`、Embedding 和 RAG 检索。
  - 可接 OpenAI、DeepSeek、通义兼容接口或本地兼容网关。
- 工程化增强：
  - 参考 Ragent 的控制台信息架构，新增轻量管理员后台。
  - 管理员可查看用户、文献、存储、索引、问答和模型调用聚合。
  - 管理员可启用 / 禁用普通用户。
  - 新增简化 RAG Trace，记录问答范围、问题意图、检索式、回答策略、输出契约、检索通道、来源数量、Pipeline 节点 span、检索耗时、生成耗时、校验耗时、格式化耗时、总耗时和失败信息。
  - 新增解析任务日志，记录 PDF 入库状态、页数、片段数、耗时和失败信息。

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
- `paper_chunks`：文献 ID、页码、块序号、文本内容、embedding vector。
- `chat_records`：用户归属、可为空的文献 ID、问题、回答、来源 JSON、模型名、耗时。
- `rag_traces`：用户归属、可为空的文献 ID、可为空的聊天记录 ID、问答范围、状态、模型名、Pipeline 名称、问题意图、检索式、回答策略、输出契约、检索通道 JSON、节点 span JSON、来源数量、分段耗时、错误信息。
- `parse_jobs`：用户归属、可为空的文献/文件 ID、文献标题快照、文件名、状态、页数、片段数、耗时、失败信息。

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
  - Agent 问答、来源页码、历史保存。
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
