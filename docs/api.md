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
GET  /api/agent/chats
GET  /api/papers/{paperId}/chats
PATCH /api/agent/chats/{id}/feedback
```

单篇问答请求：

```json
{
  "paperId": 1,
  "question": "请总结这篇论文的实验设计",
  "useRag": true
}
```

全库问答请求：

```json
{
  "paperId": null,
  "question": "请比较这些论文的主要方法路线",
  "useRag": true
}
```

响应中的 `sources` 会返回命中的论文 ID、标题、来源页码和片段。`GET /api/agent/chats` 返回全库问答历史，`GET /api/papers/{paperId}/chats` 返回单篇问答历史。

回答反馈请求：

```json
{
  "score": 1,
  "comment": ""
}
```

`score` 支持 `1`、`-1` 或 `null`，分别表示有用、无用和取消反馈。接口只允许反馈当前用户自己的问答记录，并会在返回的问答记录中带回 `feedbackScore`、`feedbackComment` 和 `feedbackAt`。

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
```

`GET /api/admin/overview` 会返回系统聚合指标、最近文献、解析任务、模型调用聚合、模型健康和最近 RAG Trace。答案反馈指标包含 `totalFeedbacks`、`positiveFeedbacks`、`negativeFeedbacks`，用于观察回答质量趋势；查询术语指标包含 `totalQueryMappings`、`enabledQueryMappings`，用于观察领域术语运营规模；模型健康字段包含 `provider`、`modelName`、`targetName`、`lastStatus`、`totalCalls`、`successCalls`、`failedCalls`、`fallbackCalls`、`averageLatencyMs`、`lastSeenAt`，用于观察模型路由是否健康；解析任务字段包含 `status`、`pageCount`、`chunkCount`、`durationMs`、`errorMessage`、`nodeSpans`，用于观察 PDF 入库质量和每个入库节点耗时；Trace 字段包含 `scope`、`status`、`pipelineName`、`queryIntent`、`searchQuery`、`queryExpansions`、`comparisonRequested`、`answerStrategy`、`answerContract`、`retrievalChannels`、`retrievalProcessors`、`nodeSpans`、`sourceCount`、`retrievalMs`、`generationMs`、`verificationMs`、`formattingMs`、`totalMs`，用于观察全库/单篇问答的规划、术语扩展、策略、检索通道、后处理器、节点链路、检索和生成耗时。

查询术语映射请求：

```json
{
  "term": "GNN",
  "expansions": "Graph Neural Network，图神经网络",
  "enabled": true
}
```

启用后的映射会在 `QueryPlanningNode` 命中问题或初始检索式时自动扩展 `searchQuery`，并写入 Trace 的 `queryExpansions`。

用户状态更新请求：

```json
{
  "status": "DISABLED"
}
```
