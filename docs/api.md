# REST API

所有 `/api/**` 接口默认需要 `Authorization: Bearer <token>`，注册和登录除外。

## Auth

```http
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```

## Papers

```http
GET    /api/papers?keyword=&status=&page=1&pageSize=20
POST   /api/papers
GET    /api/papers/{id}
PUT    /api/papers/{id}
DELETE /api/papers/{id}
PATCH  /api/papers/{id}/status
POST   /api/papers/{id}/parse
DELETE /api/papers/{id}/parse
GET    /api/papers/{id}/parse-status
```

## Files

```http
POST /api/files/papers
GET  /api/files/papers/{fileId}/preview
```

上传使用 `multipart/form-data`，字段名为 `file`。

## Agent

```http
POST /api/agent/chat
POST /api/agent/chat/stream
GET  /api/agent/sessions?paperId=1
POST /api/agent/sessions
PATCH /api/agent/sessions/{id}
GET  /api/agent/sessions/{id}/chats
GET  /api/agent/chats
GET  /api/papers/{paperId}/chats
GET  /api/agent/sample-prompts?scope=PAPER
PATCH /api/agent/chats/{id}/feedback
```

单篇问答请求：

```json
{
  "sessionId": 12,
  "paperId": 1,
  "question": "请总结这篇论文的实验设计",
  "useRag": true
}
```

全库问答请求：

```json
{
  "sessionId": 18,
  "paperId": null,
  "question": "请比较这些论文的主要方法路线",
  "useRag": true
}
```

`sessionId` 可为空；为空时后端会为当前单篇或全库范围复用最近的未归档会话，没有会话时自动创建。响应会返回 `recordId`、`sessionId`、`sessionTitle`、`modelName`、`latencyMs` 和 `sources`，其中 `sources` 包含命中的论文 ID、标题、来源页码和片段。`GET /api/agent/chats` 返回全库问答历史，`GET /api/papers/{paperId}/chats` 返回单篇问答历史，`GET /api/agent/sessions/{id}/chats` 返回某个会话内的消息。

`POST /api/agent/chat/stream` 使用同一份请求体和鉴权规则，返回 `text/event-stream`。前端使用 `fetch` 读取流式响应，并在请求头携带 `Authorization: Bearer <token>`；不直接用 `EventSource`，因为浏览器原生 `EventSource` 不方便附带 JWT。事件名包含 `started`、`running`、`final`、`done` 和 `error`：`started` / `running` 用于展示运行阶段，`final` 的 `response` 字段包含完整 `ChatResponse`，并且后端已经保存问答记录和来源片段；`done` 表示流结束；`error` 会返回 `errorMessage`。同步和流式问答都会经过聊天入口限流；超过全局并发、单用户并发或单用户每分钟次数时，非流式接口返回 HTTP 429，流式接口返回 `error` 事件。

会话创建请求：

```json
{
  "paperId": null,
  "title": "综述选题讨论"
}
```

`paperId` 为空表示全库会话；有值表示单篇会话，后端会校验论文归属。会话更新请求支持重命名和归档：

```json
{
  "title": "药物靶点预测综述",
  "archived": false
}
```

归档会话会从默认会话列表隐藏，但历史问答记录仍然保留。问答记忆优先读取当前 `sessionId` 下最近轮次；旧接口不带会话时仍可按当前范围自动落到最近未归档会话。

回答反馈请求：

```json
{
  "score": 1,
  "comment": ""
}
```

`score` 支持 `1`、`-1` 或 `null`，分别表示有用、无用和取消反馈。接口只允许反馈当前用户自己的问答记录，并会在返回的问答记录中带回 `feedbackScore`、`feedbackComment` 和 `feedbackAt`。

示例问题请求：

```http
GET /api/agent/sample-prompts?scope=LIBRARY
```

`scope` 支持 `PAPER` 和 `LIBRARY`，返回对应问答入口已启用的推荐问题，按 `sortOrder` 排序。

## Admin

`/api/admin/**` 需要 `ADMIN` 角色。

```http
GET   /api/admin/overview
GET   /api/admin/rag-traces?status=&scope=&sessionId=&keyword=&page=1&pageSize=20
GET   /api/admin/rag-traces/{id}
GET   /api/admin/users
GET   /api/admin/agent-pipeline/nodes
GET   /api/admin/ingestion-pipeline/nodes
GET   /api/admin/agent-tools
GET   /api/admin/chunks?paperId=&keyword=&page=1&pageSize=12
PATCH /api/admin/chunks/{id}/enabled
PATCH /api/admin/users/{id}/status
GET   /api/admin/query-term-mappings
POST  /api/admin/query-term-mappings
PATCH /api/admin/query-term-mappings/{id}
DELETE /api/admin/query-term-mappings/{id}
GET   /api/admin/intent-routes
POST  /api/admin/intent-routes
PATCH /api/admin/intent-routes/{id}
DELETE /api/admin/intent-routes/{id}
GET   /api/admin/answer-prompt-templates
POST  /api/admin/answer-prompt-templates
PATCH /api/admin/answer-prompt-templates/{id}
DELETE /api/admin/answer-prompt-templates/{id}
GET   /api/admin/model-targets
POST  /api/admin/model-targets
PATCH /api/admin/model-targets/{id}
DELETE /api/admin/model-targets/{id}
GET   /api/admin/rag-settings
PATCH /api/admin/rag-settings
GET   /api/admin/sample-prompts
POST  /api/admin/sample-prompts
PATCH /api/admin/sample-prompts/{id}
DELETE /api/admin/sample-prompts/{id}
```

`GET /api/admin/overview` 会返回系统聚合指标、最近文献、解析任务、模型调用聚合、模型健康、聊天限流状态和最近 RAG Trace。答案反馈指标包含 `totalFeedbacks`、`positiveFeedbacks`、`negativeFeedbacks`，用于观察回答质量趋势；查询术语指标包含 `totalQueryMappings`、`enabledQueryMappings`，用于观察领域术语运营规模；意图路由指标包含 `totalIntentRoutes`、`enabledIntentRoutes`，用于观察 QueryPlanning 运营规则规模；回答模板指标包含 `totalAnswerPromptTemplates`、`enabledAnswerPromptTemplates`，用于观察 AnswerAgent Prompt 运营规模；模型目标指标包含 `totalModelTargets`、`enabledModelTargets`，用于观察模型路由候选规模；示例问题指标包含 `totalSamplePrompts`、`enabledSamplePrompts`，用于观察推荐问法运营规模；`chatRateLimit` 包含 `enabled`、`activeGlobal`、`activeUsers`、`recentRequests`、`globalConcurrencyLimit`、`userConcurrencyLimit` 和 `userPerMinuteLimit`，用于观察聊天入口限流状态；`averageAnswerQualityScore` 表示成功 Trace 的平均质量分；模型健康字段包含 `taskType`、`provider`、`modelName`、`targetName`、`lastStatus`、`totalCalls`、`successCalls`、`failedCalls`、`fallbackCalls`、`averageLatencyMs`、`lastSeenAt`，用于按查询改写、回答生成、质量评估、会话摘要、检索重排等任务观察模型路由是否健康；解析任务字段包含 `status`、`pageCount`、`chunkCount`、`durationMs`、`errorMessage`、`nodeSpans`，用于观察 PDF 入库质量和每个入库节点耗时；Trace 字段包含 `sessionId`、`sessionTitle`、`scope`、`status`、`pipelineName`、`queryIntent`、`searchQuery`、`rewrittenQuery`、`querySubQuestions`、`queryRewriteEnabled`、`queryRewriteModelName`、`queryExpansions`、`toolExecutions`、`guidance`、`comparisonRequested`、`answerStrategy`、`answerContract`、`retrievalChannels`、`retrievalProcessors`、`nodeSpans`、`sourceCount`、`memoryTurnCount`、`memoryChars`、`memorySummaryUsed`、`memorySummaryTurnCount`、`memorySummaryChars`、`memorySummaryMethod`、`memorySummaryModelName`、`answerQualityScore`、`answerQualityLabel`、`answerQualityNotes`、`answerQualityMethod`、`answerQualityJudgeEnabled`、`answerQualityJudgeModelName`、`answerQualityConfidence`、`retrievalMs`、`generationMs`、`verificationMs`、`evaluationMs`、`formattingMs`、`totalMs`，用于观察全库/单篇问答的改写、规划、术语扩展、工具执行、意图引导、策略、会话记忆、长期摘要、检索通道、后处理器、质量评估、节点链路、检索和生成耗时。`guidance` 包含 `required`、`type`、`message`、`reason` 和 `suggestions`，用于定位问题过泛或证据缺口。

`GET /api/admin/agent-pipeline/nodes` 返回当前问答 Pipeline 的节点目录和运行画像。字段包含 `pipelineName`、`type`、`name`、`label`、`description`、`sortOrder`、`enabled`、`totalRuns`、`successRuns`、`failedRuns`、`averageLatencyMs` 和 `lastSeenAt`。节点定义来自 `AgentPipeline` 注册表，运行统计从历史 `nodeSpans` Trace 聚合，用于观察节点顺序、职责、失败风险和耗时热点。

`GET /api/admin/ingestion-pipeline/nodes` 返回 PDF 入库 Pipeline 的节点目录和运行画像。字段包含 `pipelineName`、`type`、`name`、`label`、`description`、`sortOrder`、`enabled`、`totalRuns`、`successRuns`、`failedRuns`、`averageLatencyMs` 和 `lastSeenAt`。节点定义来自 `IngestionPipelineCatalog`，运行统计从历史解析任务的 `nodeSpans` 聚合，用于观察准备、读取 PDF、抽取文本、写入片段、向量索引和完成节点的长期健康状况。

`GET /api/admin/agent-tools` 返回当前 Pipeline 注册的内部工具目录和调用画像。字段包含 `name`、`label`、`description`、`triggerDescription`、`source`、`enabled`、`totalCalls`、`successCalls`、`failedCalls`、`averageLatencyMs` 和 `lastSeenAt`。工具定义来自 Spring Bean 注册表，调用统计从历史 `toolExecutions` Trace 聚合，用于运营侧观察哪些工具可用、如何触发、近期是否失败以及平均耗时。

`GET /api/admin/chunks` 返回知识片段分页数据，支持按 `paperId` 和 `keyword` 过滤；`keyword` 会匹配片段正文、论文标题、作者、关键词和用户名。字段包含 `id`、`username`、`paperId`、`paperTitle`、`pageNumber`、`chunkIndex`、`contentPreview`、`contentLength`、`embedded`、`enabled` 和 `createdAt`，用于管理员排查 PDF 入库后的 chunk 内容、页码定位、向量化覆盖和是否参与检索。`PATCH /api/admin/chunks/{id}/enabled` 请求体为 `{"enabled": false}` 或 `{"enabled": true}`；禁用后的片段仍保留在库中和后台列表里，但关键词检索与向量检索都会跳过它。

`GET /api/admin/rag-traces` 返回分页 Trace Explorer 数据，支持按 `status`、`scope`、`sessionId` 和 `keyword` 过滤；`keyword` 会匹配问题、检索式、改写问题、意图、模型名、工具执行结果、意图引导、错误信息、会话标题、论文标题和用户名。`GET /api/admin/rag-traces/{id}` 返回单条 Trace 的完整诊断字段，便于从后台打开历史链路详情。

查询术语映射请求：

```json
{
  "term": "GNN",
  "expansions": "Graph Neural Network，图神经网络",
  "enabled": true
}
```

启用后的映射会在 `QueryPlanningNode` 命中问题或初始检索式时自动扩展 `searchQuery`，并写入 Trace 的 `queryExpansions`。

意图路由请求：

```json
{
  "intentCode": "METHOD_ANALYSIS",
  "label": "方法分析",
  "description": "识别论文方法结构和架构细节类问题。",
  "keywords": "方法,架构,模块,method,architecture",
  "searchHint": "method architecture module",
  "answerStrategy": "EVIDENCE_GROUNDED_QA",
  "answerContract": "输出结构：按方法目标、模块组成、关键步骤和证据页码组织。",
  "comparisonEnabled": false,
  "enabled": true,
  "sortOrder": 80
}
```

启用后的意图路由会被 `QueryPlanningNode` 用于识别 `queryIntent`、扩展检索提示和标记比较类问题；`AnswerPlanningNode` 会读取对应的 `answerStrategy` 和 `answerContract`。

回答 Prompt 模板请求：

```json
{
  "code": "ACADEMIC_RAG",
  "name": "科研 RAG 默认模板",
  "description": "面向论文精读和全库综合。",
  "systemPrompt": "你是 Research Paper Agent 的论文精读 Agent。",
  "userPromptTemplate": "回答范围：{{scope}}\n{{paper_metadata}}\n回答策略：{{answer_strategy}}\n输出契约：{{answer_contract}}\n历史对话：\n{{conversation_history}}\n业务工具结果：\n{{tool_context}}\n意图引导：\n{{guidance_context}}\n用户问题：{{question}}\n检索片段：{{sources}}",
  "enabled": true,
  "defaultTemplate": true,
  "sortOrder": 10
}
```

`AnswerAgent` 会优先使用启用的默认模板；如果没有默认模板，则使用排序最靠前的启用模板；如果数据库无可用模板，则使用内置兜底模板。支持占位符：`{{scope}}`、`{{paper_metadata}}`、`{{answer_strategy}}`、`{{answer_contract}}`、`{{conversation_history}}`、`{{tool_context}}`、`{{guidance_context}}`、`{{question}}`、`{{sources}}`。其中 `{{tool_context}}` 来自 `ToolExecutionNode` 的成功工具结果，可用于回答文献库统计、解析状态和系统运营类问题；`{{guidance_context}}` 来自 `IntentGuidanceNode`，用于在问题过泛或证据缺口时输出澄清说明和建议追问。

模型目标请求：

```json
{
  "code": "DEEPSEEK_PRIMARY",
  "provider": "OPENAI_COMPATIBLE",
  "taskType": "ANSWER_GENERATION",
  "modelName": "deepseek-v4-flash",
  "description": "主力对话模型",
  "baseUrl": "https://api.example.com",
  "apiKey": "sk-***",
  "enabled": true,
  "priority": 20,
  "timeoutSeconds": 45
}
```

`provider` 支持 `ENV` 和 `OPENAI_COMPATIBLE`。`ENV` 表示使用当前环境变量和 Spring AI 配置；`OPENAI_COMPATIBLE` 会按 `baseUrl + /v1/chat/completions` 调用兼容接口。`taskType` 支持 `GENERAL`、`ANSWER_GENERATION`、`QUERY_REWRITE`、`QUALITY_EVALUATION`、`CONVERSATION_SUMMARY` 和 `RETRIEVAL_RERANK`，用于让回答生成、查询改写、质量评估、会话摘要、检索重排等节点选择不同模型；路由时会先按任务类型尝试启用目标，再尝试 `GENERAL` 通用目标。模型路由会按启用目标的 `priority` 升序逐个尝试，失败会记录到 `model_invocations` 并继续尝试下一个目标，全部失败后才走 fallback。更新时 `apiKey` 留空表示保留原密钥。

RAG 检索参数请求：

```json
{
  "candidateLimit": 10,
  "resultLimit": 5,
  "sourceExcerptChars": 520,
  "vectorWeight": 1.0,
  "keywordWeight": 0.78,
  "memoryHistoryTurns": 4,
  "memoryMaxChars": 2400,
  "memorySummaryEnabled": true,
  "memorySummaryStartTurns": 6,
  "memorySummaryMaxChars": 1800,
  "queryRewriteEnabled": true,
  "queryRewriteMaxSubQuestions": 3,
  "answerQualityJudgeEnabled": true,
  "rerankModelEnabled": false,
  "rerankModelMaxCandidates": 8,
  "chatRateLimitEnabled": true,
  "chatRateLimitGlobalConcurrency": 12,
  "chatRateLimitUserConcurrency": 2,
  "chatRateLimitUserPerMinute": 20
}
```

`candidateLimit` 控制每个检索通道的候选召回上限，`resultLimit` 控制最终返回来源数，`sourceExcerptChars` 控制来源卡片摘录长度，`vectorWeight` 和 `keywordWeight` 控制通道融合权重，`memoryHistoryTurns` 和 `memoryMaxChars` 控制近期历史问答注入 Prompt 的轮数和字符上限，`memorySummaryEnabled`、`memorySummaryStartTurns` 和 `memorySummaryMaxChars` 控制是否把超出近期窗口的旧会话压缩成长期摘要，`queryRewriteEnabled` 和 `queryRewriteMaxSubQuestions` 控制查询改写与子问题拆分，`answerQualityJudgeEnabled` 控制是否在启发式评估后调用 `QUALITY_EVALUATION` 模型做 LLM-as-judge 质量评审，`rerankModelEnabled` 和 `rerankModelMaxCandidates` 控制是否在检索式感知精排后调用 `RETRIEVAL_RERANK` 模型重排前 N 个候选；该开关默认关闭，模型不可用或返回格式异常时会回退原顺序并继续回答。`chatRateLimitEnabled`、`chatRateLimitGlobalConcurrency`、`chatRateLimitUserConcurrency` 和 `chatRateLimitUserPerMinute` 控制聊天入口限流，默认保护全局 12 路并发、单用户 2 路并发和每分钟 20 次请求。

示例问题管理请求：

```json
{
  "scope": "PAPER",
  "title": "核心方法",
  "prompt": "请总结这篇论文的核心方法和贡献。",
  "description": "快速建立单篇论文理解。",
  "sortOrder": 10,
  "enabled": true
}
```

用户状态更新请求：

```json
{
  "status": "DISABLED"
}
```
