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
- `parse`：PDFBox 正文抽取、按页切块、写入 `paper_chunks`。
- `agent`：多 Agent Lite 编排、问答、来源片段和历史记录。
- `common`：统一响应、异常处理、分页对象。

## Agent Design

MVP 采用 Spring Boot 内部多 Agent Lite：

```text
RetrieverAgent
  -> AnswerAgent
  -> CitationVerifierAgent
  -> FormatterAgent
```

默认走兜底回答，保证无模型 API Key 时系统仍然可用。配置 OpenAI-compatible API 后，`AnswerAgent` 会通过 Spring AI `ChatClient` 生成回答。

LangGraph 暂不进入第一版主链路。后续如果要做多文献综述、人工确认、可恢复工作流，可以新增独立 `agent-service/`，由 Spring Boot 通过内部 REST 调用。

## Vector Store

项目使用 PostgreSQL + pgvector。第一版已经创建 `paper_chunks.embedding vector(1536)` 字段，PDF 解析先写入分块文本和页码。后续接 embedding 后，可以在同一张表上加入 HNSW/IVFFlat 索引并替换当前的关键词检索。
