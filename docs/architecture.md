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
- `parse`：PDFBox 正文抽取、按页切块、写入 `paper_chunks` 并生成向量索引。
- `embedding`：Spring AI embedding 或本地 hashing embedding、pgvector 写入和向量召回。
- `agent`：多 Agent Lite 编排、问答、来源片段和历史记录。
- `common`：统一响应、异常处理、分页对象。

## Agent Design

MVP 采用 Spring Boot 内部多 Agent Lite，并参考 ragent 的节点化框架，把问答链路收敛到轻量 `AgentPipeline`：

```text
AgentOrchestratorService
  -> AgentPipeline
     -> ScopeResolutionNode
     -> QueryPlanningNode
     -> RetrievalNode
     -> AnswerPlanningNode
     -> AnswerGenerationNode
     -> CitationVerificationNode
     -> AnswerFormattingNode
```

`AgentOrchestratorService` 负责事务边界、聊天历史和 RAG Trace 收尾；具体业务步骤由 `AgentNode` 实现。`QueryPlanningNode` 会识别总结、贡献、实验、局限、比较、综述等意图，生成面向召回的 `searchQuery`。`RetrievalNode` 内部使用 `MultiChannelRetrievalEngine`，并行执行向量通道和关键词通道，再通过后处理器链完成通道融合、检索式感知精排、跨论文多样性重排和结果截断，通道与后处理器的候选数、输入输出和耗时都会写入 Trace。`AnswerPlanningNode` 吸收 ragent Prompt Plan 的思路，把意图、范围和证据状态转换成 `answerStrategy` 与 `answerContract`，让比较、综述、实验和局限类问题进入不同回答结构。`AgentPipeline` 自动记录节点 span，并写入 `rag_traces.node_spans_json`，同时保留检索、生成、校验和格式化的聚合耗时字段。后续可以继续插入 LLM 改写、向量意图树、模型 rerank、跨论文综合、答案评审等节点。

默认走兜底回答，保证无模型 API Key 时系统仍然可用。配置 OpenAI-compatible API 后，`AnswerGenerationNode` 会调用 `AnswerAgent`，再通过 `ModelRoutingService` 进入 Spring AI `ChatClient` 生成回答。模型路由会记录目标模型、成功 / 失败 / fallback、延迟和错误摘要，管理员后台据此展示模型健康，后续可以继续扩展为多模型优先级、限流、熔断和故障切换。

`paperId` 为空时进入全库问答，`RetrieverAgent` 会在当前用户所有已解析文献中召回片段；`paperId` 有值时只检索单篇论文。

LangGraph 暂不进入第一版主链路。当前 Pipeline 吸收 ragent 的框架感，但保持在 Spring Boot 内部，避免过早引入 AOP Trace、异步流式 Trace 上下文和独立工作流服务。后续如果要做多文献综述、人工确认、可恢复工作流，可以新增独立 `agent-service/`，由 Spring Boot 通过内部 REST 调用。

## Vector Store

项目使用 PostgreSQL + pgvector。`paper_chunks.embedding vector(1536)` 保存 chunk 向量，Flyway V2 创建 HNSW 余弦索引。默认 embedding provider 是 `local`，使用确定性 hashing embedding；设置 `AI_EMBEDDING_PROVIDER` 为非 `local` 后，会优先使用 Spring AI `EmbeddingModel`，失败时退回本地 embedding。关键词检索仍保留为兜底。
