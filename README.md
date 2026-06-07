# Research Paper Agent

Research Paper Agent 是一个科研文献阅读与问答工作台。项目从 CloudBase Serverless 原型升级为 React + Spring Boot + PostgreSQL/pgvector + MinIO 的全栈系统，支持 PDF 上传、文献管理、在线预览、会话化流式问答历史、聊天入口限流、内部工具执行、意图引导澄清和 Spring AI 多 Agent Lite 架构。

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

阅读页和全库问答页的推荐问题来自 `sample_prompts`，默认内置单篇/全库两组提示词；管理员可以在后台调整标题、问题、排序和启用状态，让常用问法可运营而不是固定写死在前端。

阅读页和全库问答页支持会话化问答：用户可以新建、切换、重命名和归档单篇或全库会话；聊天请求可携带 `sessionId`，不携带时后端会复用当前范围最近未归档会话或自动创建。前端默认通过 `POST /api/agent/chat/stream` 读取 SSE 阶段事件，展示连接、检索生成、最终保存和停止等待状态；每个流式请求都会获得 `taskId`，停止按钮会调用服务端取消接口并中断本地 fetch，后端也提供当前用户活跃流式任务查询，管理员总览可观察并强制停止当前仍在排队或运行的流式任务。普通 `POST /api/agent/chat` 仍保留为非流式兼容接口。同步和流式问答共享 `AgentRateLimiterService`，管理员可在 RAG 设置中配置全局并发、单用户并发和单用户每分钟请求上限；被限流的请求返回 429，并写入失败 Trace 便于追踪。旧问答记录通过迁移回填默认会话，历史接口仍保持兼容。

Agent 编排参考 ragent 的节点化思路，后端使用轻量 `AgentPipeline` 串联 `ScopeResolutionNode -> ConversationMemoryNode -> QueryRewriteNode -> QueryPlanningNode -> ToolExecutionNode -> RetrievalNode -> IntentGuidanceNode -> AnswerPlanningNode -> AnswerGenerationNode -> CitationVerificationNode -> AnswerQualityEvaluationNode -> AnswerFormattingNode`。当前不引入 ragent 的 AOP Trace 和复杂异步流式上下文，先以 `AgentStreamService` 提供轻量 SSE 包装、任务注册、管理员活跃任务总览、用户自助取消和管理员强制取消能力，保留简单、可扩展、易调试的 Spring Bean 节点结构。`AgentPipeline` 运行时会读取 `agent_pipeline_node_settings`：范围解析、查询规划、检索、回答规划、回答生成和格式化等主链路节点由服务端锁定；会话记忆、查询改写、工具执行、意图引导、引用校验和质量评估等增强节点可在管理后台停用，停用后仍会在 Trace 中记录 `SKIPPED` 节点 span。`ConversationMemoryNode` 会优先按当前会话读取长期摘要和最近问答，生成受字符上限保护的“长期摘要 + 近期对话”压缩记忆，并通过 `{{conversation_history}}` 注入回答 Prompt；近期轮数、记忆字符数、摘要开关、摘要触发轮数和摘要最大字符数同样来自 `rag_settings`。成功问答保存后，`ConversationSummaryService` 会把超出近期窗口的旧轮次交给 `ConversationSummaryAgent`，通过 `CONVERSATION_SUMMARY` 模型路由生成长期摘要，模型不可用时退回启发式摘要。`QueryRewriteNode` 参考 ragent 的问题改写与拆分能力，使用模型路由把用户原问题改写成更适合检索的查询，并记录改写文本、子问题和改写模型；模型不可用或 JSON 解析失败时会回退原问题。`QueryPlanningNode` 会读取 `intent_routes`，按关键词规则识别问题意图、拼接检索提示、标记是否需要跨文献比较，并可把路由绑定的工具名写入当前 Pipeline 上下文；管理员配置的查询术语映射会在此阶段把领域缩写、别名和同义词注入检索式，并写入 Trace；`ToolExecutionNode` 会通过 `AgentToolRegistry` 按工具自身触发规则或意图路由绑定工具匹配内部业务工具，并继续执行启停状态和当前用户角色校验，启停状态和最小调用角色持久化在 `agent_tool_settings`，目前提供 `LibraryStatsTool` 读取当前用户文献库、PDF 文件、解析任务、知识片段和问答统计，工具结果通过 `{{tool_context}}` 注入回答 Prompt，并写入 Trace；`RetrievalNode` 内部使用多通道检索引擎混合向量检索和关键词检索，并通过后处理器链完成通道融合、检索式感知精排、可选模型重排、跨论文多样性重排和结果截断；RAG 候选数、最终来源数、来源摘录长度、向量权重、关键词权重、查询改写开关、子问题上限、答案模型评审开关、模型重排开关和重排候选窗口来自 `rag_settings`，管理员修改后会被检索链路运行时读取；`IntentGuidanceNode` 吸收 ragent 引导式问答思路，在问题过泛或检索无证据且无工具结果时生成澄清提示和建议追问，并写入 `guidance_json`；`AnswerPlanningNode` 会根据意图路由配置、工具结果、证据状态和引导需求选择回答策略与输出契约，让统计状态、比较、综述、实验、局限和引导澄清等问题走不同回答结构。`AnswerAgent` 会读取启用的默认 `answer_prompt_templates`，用 `{{scope}}`、`{{paper_metadata}}`、`{{answer_strategy}}`、`{{answer_contract}}`、`{{conversation_history}}`、`{{tool_context}}`、`{{guidance_context}}`、`{{question}}`、`{{sources}}` 渲染 System Prompt 和用户消息；`AnswerQualityEvaluationNode` 会先生成启发式质量信号，再可选调用 `QUALITY_EVALUATION` 模型做 LLM-as-judge 评审，并把质量分、标签、方法、置信度、评审模型和解释说明写入 Trace。模型调用通过轻量 `ModelRoutingService` 按任务类型读取 `model_targets`，优先尝试对应任务目标，再使用通用目标兜底，单个目标失败会继续尝试下一个，全部失败后才走 fallback；同一目标连续失败 3 次会进入 `OPEN` 冷却 1 分钟，冷却期间记录 `SKIPPED` 并尝试下一个目标，冷却结束后以 `HALF_OPEN` 单次探测恢复或重新打开；成功、失败、fallback 和跳过都会带任务类型写入模型调用日志，便于后台分别观察查询改写、回答生成、质量评估、会话摘要、检索重排等环节的模型健康；用户也可以对回答标记有用 / 无用，形成可被后台聚合的答案质量反馈。

回答下方会展示来源卡片，点击来源可回到对应论文并跳转 PDF 页码。已解析论文也可以取消解析，从知识库移除文本片段和向量索引，同时保留 PDF 文件与文献记录。

## 管理后台

`ADMIN` 用户可以进入管理后台查看系统级指标：用户数、文献数、PDF 存储、知识片段、问答次数、答案反馈、查询术语映射、意图路由、回答模板、模型目标、Agent Pipeline 节点、入库 Pipeline 节点、检索通道与后处理器目录、Agent 工具目录、Agent 工具调用审计、示例问题、模型调用聚合、模型健康、聊天限流状态、解析任务、最近文献、RAG Trace 和用户资源使用情况。后台支持配置 RAG 检索参数、查询改写、模型重排、模型质量评审、会话记忆窗口、长期摘要压缩、聊天入口限流、维护意图路由、回答 Prompt 模板、按任务类型维护模型路由目标、术语映射和示例问题，也支持启用 / 禁用普通用户。意图路由可选绑定已注册的 Agent 工具，让特定运营意图显式请求工具执行；绑定工具仍会受 Agent 工具目录中的启停状态和最小调用角色约束。Agent Pipeline 节点目录会展示当前问答链路的节点顺序、职责、启停状态、成功/失败/跳过运行次数、平均耗时和最近运行时间，管理员可停用非主链路增强节点，主链路节点会在后台锁定；入库 Pipeline 节点目录会展示 PDF 解析入库链路中准备、读取 PDF、抽取文本、写入片段、向量索引和完成节点的长期运行画像；检索通道与后处理器目录会展示向量/关键词通道以及通道融合、规则精排、模型重排、多样性重排、结果截断等组件的注册顺序、候选规模、输入输出规模、成功/失败运行次数、平均耗时和最近运行时间；Agent 工具目录会展示每个内部工具的触发规则、来源、启停状态、最小调用角色、成功/失败调用次数、平均耗时和最近调用时间，调用画像从历史 Trace 聚合，管理员可以停用单个工具让后续问答链路跳过它，也可以把敏感工具限制为仅管理员调用；Agent 工具调用审计会按工具、状态和关键词分页展开历史 Trace 中的工具执行明细，显示触发问题、Trace 编号、用户、范围、工具摘要、失败原因和耗时，用于追查工具异常或敏感工具调用；知识片段治理支持按文献 ID 或关键词检查入库后的 chunk 正文、页码、序号、向量化状态和是否参与检索，管理员可以禁用脏片段让关键词检索与向量检索跳过它，用于排查和修正召回材料质量；解析任务 Explorer 支持按状态或关键词分页检索历史 PDF 入库任务，查看页数、片段数、总耗时、失败信息和各入库节点耗时；模型健康会按任务类型展示目标模型最近状态、成功/失败/fallback/跳过次数、熔断状态、连续失败次数、冷却时间和平均延迟，管理员可手动复位异常熔断目标；RAG Trace 会记录最近问答的会话、范围、问题意图、检索式、改写查询、子问题、术语扩展、业务工具执行、意图引导、回答策略、输出契约、检索通道、检索后处理器、近期记忆轮数、记忆字符数、长期摘要轮数、摘要字符数、摘要方法、摘要模型、来源数量、答案质量分、质量标签、质量方法、评审置信度、评审模型、质量解释、Pipeline 节点耗时、检索耗时、生成耗时、评估耗时和总耗时。Trace Explorer 额外支持按状态、范围、会话 ID 和关键词分页检索历史链路，并可展开查看单条 Trace 的检索、改写、工具、引导、记忆、质量、通道、后处理器和节点 span 诊断。
