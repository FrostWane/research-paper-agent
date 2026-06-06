# Research Paper Agent

Research Paper Agent 是一个科研文献阅读与问答工作台。项目从 CloudBase Serverless 原型升级为 React + Spring Boot + PostgreSQL/pgvector + MinIO 的全栈系统，支持 PDF 上传、文献管理、在线预览、问答历史和 Spring AI 多 Agent Lite 架构。

## 项目结构

```text
research-paper-agent/
  backend/      Spring Boot REST API
  frontend/     React + Vite 科研工作台
  docs/         架构、API 和移动端路线
  PLAN.md       三阶段实施计划
```

## 快速启动

复制环境变量：

```bash
Copy-Item .env.example .env
```

启动基础服务和应用：

```bash
docker compose up --build
```

访问：

- Web: http://localhost:15173
- Backend: http://localhost:18080
- Swagger: http://localhost:18080/swagger-ui/index.html
- MinIO Console: http://localhost:9001

## 本地开发

前端：

```bash
cd frontend
npm install
npm run dev
```

后端建议使用 Java 21。仓库包含 Maven Wrapper：

```bash
cd backend
.\mvnw.cmd test
```

若本机没有 Java 21，可使用 Docker 构建：

```bash
docker run --rm -v ${PWD}/backend:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

## Agent 与 RAG

默认提供可运行的兜底 Agent。配置 OpenAI-compatible 参数后，后端会通过 Spring AI `ChatClient` 调用真实模型：

```env
AI_PROVIDER=openai
OPENAI_API_KEY=your-key
OPENAI_BASE_URL=https://api.openai.com
OPENAI_CHAT_MODEL=gpt-4o-mini
```

向量数据库采用 PostgreSQL + pgvector。PDF 解析后会写入 `paper_chunks.embedding`，并建立 HNSW 余弦索引。默认使用本地 1536 维 hashing embedding，避免依赖额外云服务；如果要切换到 Spring AI 远程 embedding，可设置：

```env
AI_EMBEDDING_PROVIDER=openai
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
```

Agent 支持两种问答范围：

- 单篇论文问答：请求携带 `paperId`。
- 全库问答：`paperId` 传 `null`，在当前用户所有已解析文献中检索来源片段。

Agent 编排参考 ragent 的节点化思路，后端使用轻量 `AgentPipeline` 串联 `ScopeResolutionNode -> QueryPlanningNode -> RetrievalNode -> AnswerPlanningNode -> AnswerGenerationNode -> CitationVerificationNode -> AnswerFormattingNode`。当前不引入 AOP Trace 和异步流式上下文，先保留简单、可扩展、易调试的 Spring Bean 节点结构。`QueryPlanningNode` 会识别问题意图，生成检索式，并标记是否需要跨文献比较；`RetrievalNode` 内部使用多通道检索引擎混合向量检索和关键词检索，并通过后处理器链完成通道融合、检索式感知精排、跨论文多样性重排和结果截断；`AnswerPlanningNode` 会把意图和证据状态转换成回答策略与输出契约，让比较、综述、实验、局限等问题走不同回答结构。模型调用通过轻量 `ModelRoutingService` 进入目标模型，成功、失败和 fallback 都会写入模型调用日志，便于后台观察模型健康；用户也可以对回答标记有用 / 无用，形成可被后台聚合的答案质量反馈。

回答下方会展示来源卡片，点击来源可回到对应论文并跳转 PDF 页码。已解析论文也可以取消解析，从知识库移除文本片段和向量索引，同时保留 PDF 文件与文献记录。

## 管理后台

`ADMIN` 用户可以进入管理后台查看系统级指标：用户数、文献数、PDF 存储、知识片段、问答次数、答案反馈、模型调用聚合、模型健康、解析任务、最近文献、RAG Trace 和用户资源使用情况。解析任务会记录 PDF 入库状态、页数、片段数、总耗时、失败信息，以及准备、读取 PDF、抽取文本、写入片段、向量索引、完成等入库节点耗时；模型健康会展示目标模型最近状态、成功/失败/fallback 次数和平均延迟；RAG Trace 会记录最近问答的范围、问题意图、检索式、回答策略、输出契约、检索通道、检索后处理器、来源数量、Pipeline 节点耗时、检索耗时、生成耗时和总耗时。后台也支持启用 / 禁用普通用户。
