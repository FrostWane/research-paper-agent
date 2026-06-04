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
GET  /api/papers/{paperId}/chats
```

`POST /api/agent/chat` 请求：

```json
{
  "paperId": 1,
  "question": "请总结这篇论文的实验设计",
  "useRag": true
}
```

响应中的 `sources` 会返回命中的来源页码和片段。
