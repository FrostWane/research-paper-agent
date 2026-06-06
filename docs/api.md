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
GET   /api/admin/users
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

`GET /api/admin/overview` 会返回系统聚合指标、最近文献、解析任务、模型调用聚合、模型健康和最近 RAG Trace。答案反馈指标包含 `totalFeedbacks`、`positiveFeedbacks`、`negativeFeedbacks`，用于观察回答质量趋势；查询术语指标包含 `totalQueryMappings`、`enabledQueryMappings`，用于观察领域术语运营规模；意图路由指标包含 `totalIntentRoutes`、`enabledIntentRoutes`，用于观察 QueryPlanning 运营规则规模；回答模板指标包含 `totalAnswerPromptTemplates`、`enabledAnswerPromptTemplates`，用于观察 AnswerAgent Prompt 运营规模；模型目标指标包含 `totalModelTargets`、`enabledModelTargets`，用于观察模型路由候选规模；示例问题指标包含 `totalSamplePrompts`、`enabledSamplePrompts`，用于观察推荐问法运营规模；`averageAnswerQualityScore` 表示成功 Trace 的平均质量分；模型健康字段包含 `taskType`、`provider`、`modelName`、`targetName`、`lastStatus`、`totalCalls`、`successCalls`、`failedCalls`、`fallbackCalls`、`averageLatencyMs`、`lastSeenAt`，用于按查询改写、回答生成、质量评估、会话摘要等任务观察模型路由是否健康；解析任务字段包含 `status`、`pageCount`、`chunkCount`、`durationMs`、`errorMessage`、`nodeSpans`，用于观察 PDF 入库质量和每个入库节点耗时；Trace 字段包含 `sessionId`、`sessionTitle`、`scope`、`status`、`pipelineName`、`queryIntent`、`searchQuery`、`rewrittenQuery`、`querySubQuestions`、`queryRewriteEnabled`、`queryRewriteModelName`、`queryExpansions`、`comparisonRequested`、`answerStrategy`、`answerContract`、`retrievalChannels`、`retrievalProcessors`、`nodeSpans`、`sourceCount`、`memoryTurnCount`、`memoryChars`、`memorySummaryUsed`、`memorySummaryTurnCount`、`memorySummaryChars`、`memorySummaryMethod`、`memorySummaryModelName`、`answerQualityScore`、`answerQualityLabel`、`answerQualityNotes`、`answerQualityMethod`、`answerQualityJudgeEnabled`、`answerQualityJudgeModelName`、`answerQualityConfidence`、`retrievalMs`、`generationMs`、`verificationMs`、`evaluationMs`、`formattingMs`、`totalMs`，用于观察全库/单篇问答的改写、规划、术语扩展、策略、会话记忆、长期摘要、检索通道、后处理器、质量评估、节点链路、检索和生成耗时。

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
  "userPromptTemplate": "回答范围：{{scope}}\n{{paper_metadata}}\n回答策略：{{answer_strategy}}\n输出契约：{{answer_contract}}\n历史对话：\n{{conversation_history}}\n用户问题：{{question}}\n检索片段：{{sources}}",
  "enabled": true,
  "defaultTemplate": true,
  "sortOrder": 10
}
```

`AnswerAgent` 会优先使用启用的默认模板；如果没有默认模板，则使用排序最靠前的启用模板；如果数据库无可用模板，则使用内置兜底模板。支持占位符：`{{scope}}`、`{{paper_metadata}}`、`{{answer_strategy}}`、`{{answer_contract}}`、`{{conversation_history}}`、`{{question}}`、`{{sources}}`。

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

`provider` 支持 `ENV` 和 `OPENAI_COMPATIBLE`。`ENV` 表示使用当前环境变量和 Spring AI 配置；`OPENAI_COMPATIBLE` 会按 `baseUrl + /v1/chat/completions` 调用兼容接口。`taskType` 支持 `GENERAL`、`ANSWER_GENERATION`、`QUERY_REWRITE`、`QUALITY_EVALUATION` 和 `CONVERSATION_SUMMARY`，用于让回答生成、查询改写、质量评估、会话摘要等节点选择不同模型；路由时会先按任务类型尝试启用目标，再尝试 `GENERAL` 通用目标。模型路由会按启用目标的 `priority` 升序逐个尝试，失败会记录到 `model_invocations` 并继续尝试下一个目标，全部失败后才走 fallback。更新时 `apiKey` 留空表示保留原密钥。

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
  "answerQualityJudgeEnabled": true
}
```

`candidateLimit` 控制每个检索通道的候选召回上限，`resultLimit` 控制最终返回来源数，`sourceExcerptChars` 控制来源卡片摘录长度，`vectorWeight` 和 `keywordWeight` 控制通道融合权重，`memoryHistoryTurns` 和 `memoryMaxChars` 控制近期历史问答注入 Prompt 的轮数和字符上限，`memorySummaryEnabled`、`memorySummaryStartTurns` 和 `memorySummaryMaxChars` 控制是否把超出近期窗口的旧会话压缩成长期摘要，`queryRewriteEnabled` 和 `queryRewriteMaxSubQuestions` 控制查询改写与子问题拆分，`answerQualityJudgeEnabled` 控制是否在启发式评估后调用 `QUALITY_EVALUATION` 模型做 LLM-as-judge 质量评审。

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
