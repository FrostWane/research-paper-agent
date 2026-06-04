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

- Web: http://localhost:5173
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

第一版默认提供可运行的兜底 Agent。配置 OpenAI-compatible 参数后，后端会通过 Spring AI `ChatClient` 调用真实模型：

```env
OPENAI_API_KEY=your-key
OPENAI_BASE_URL=https://api.openai.com
OPENAI_CHAT_MODEL=gpt-4o-mini
```

向量数据库采用 PostgreSQL + pgvector。PDF 解析后会写入 `paper_chunks`，并为后续 embedding 检索和来源页码追踪预留字段。
