# AI 智能学习助手系统

一个面向学习场景的全栈 AI 应用，围绕“资料管理 -> AI 总结 -> AI 出题 -> 在线练习 -> AI 判分 -> 错题本 -> 知识点掌握度 -> 学习分析 -> RAG 检索问答”构建完整学习闭环。

项目不仅是一个聊天助手，还包含异步任务中心、资料解析、向量检索、RAG 评测、学习数据分析和可配置 AI Provider，适合作为 AI + 教育、RAG 工程化、Agent 工具调用和全栈项目实践展示。

## 在线体验

- 前端地址：[https://ai-intelligent-learning-assistant-s.vercel.app](https://ai-intelligent-learning-assistant-s.vercel.app)
- 后端健康检查：[https://backend-production-d1f3.up.railway.app/api/health](https://backend-production-d1f3.up.railway.app/api/health)
- GitHub 仓库：[https://github.com/Nyx-Amanises/AI-Intelligent-Learning-Assistant-System](https://github.com/Nyx-Amanises/AI-Intelligent-Learning-Assistant-System)

> 线上环境依赖 Railway / Vercel 免费或个人资源，首次访问可能需要等待后端冷启动。

## 项目亮点

- **学习闭环完整**：资料上传、AI 总结、AI 出题、练习作答、AI 判分、错题复盘、知识点掌握度和学习分析全部打通。
- **Agent 学习助手**：支持 SSE 流式回复、会话管理、工具调用轨迹、上下文选择、记忆记录和任务联动。
- **RAG 工程化能力**：支持资料分段、Embedding、Qdrant 向量库、检索预览、PDF 页码定位和 RAG 评测。
- **异步任务中心**：总结、出题、简答题判分、Embedding 等 AI 操作统一进入任务队列，支持状态追踪、重试、重新派发和删除记录。
- **可配置 AI Provider**：支持 OpenAI-compatible、DeepSeek 等兼容接口，聊天模型和 Embedding 模型可独立配置，也支持 Mock 模式便于演示。
- **移动端适配**：登录页、首页、Agent 聊天界面和 RAG 评测界面已做手机端 UI 优化。
- **生产部署可用**：前端部署到 Vercel，后端部署到 Railway，支持跨域、健康检查和前端路由重写。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3, TypeScript, Vite, Pinia, Vue Router, Element Plus, Axios |
| 后端 | Spring Boot 3.3, Java 17, MyBatis Plus, JWT, Bean Validation |
| 数据库 | MySQL 8, Redis |
| RAG | Qdrant, Embedding API, 资料分段, Hit@K / Recall@K / MRR 评测 |
| AI | OpenAI-compatible Chat API, SSE 流式输出, Mock 模式 |
| 部署 | Docker Compose, Vercel, Railway |

## 功能模块

### 用户与首页

- 用户注册、登录、JWT 鉴权。
- 登录失效自动回到登录页。
- 首页展示学习概览、最近任务和学习入口。
- 登录页和首页支持移动端适配。

### 资料管理

- 支持文本资料创建。
- 支持 PDF / DOCX / TXT 文件上传。
- 支持资料解析、分段、页码记录、章节标题和字符统计。
- 支持资料重命名、删除、分页、筛选和详情查看。
- 支持资料 Embedding 状态展示和检索预览。

### AI 总结

- 基于资料生成标准总结、复习总结和提纲总结。
- 支持保存为学习笔记。
- 支持历史总结列表和详情查看。
- 总结任务走异步任务中心，避免长请求阻塞。

### AI 出题与在线练习

- 基于资料生成题集。
- 支持单选题、判断题、简答题数量配置。
- 支持题集难度和标题配置。
- 支持在线作答、提交、客观题即时判分。
- 简答题支持异步 AI 判分、评语、答案解析和资料依据。
- 支持练习记录改名、再次练习和删除。

### 错题本与知识点掌握度

- 自动收集错题和低分简答题。
- 支持按资料、题型、关键词筛选。
- 支持查看错题解析和跳转原练习。
- 按知识点聚合练习记录，统计正确率、错误次数和得分率。
- 自动划分已掌握、基本掌握、待巩固、薄弱知识点。

### 学习分析

- 汇总平均正确率、平均得分率、错题次数和薄弱知识点数量。
- 展示掌握度分布、最近练习趋势、题型表现和资料表现排行。
- 用于快速判断复习优先级。

### 任务中心

已接入任务类型：

- `SUMMARY`
- `QUESTION_GENERATE`
- `PRACTICE_REVIEW`
- `EMBEDDING`

支持能力：

- 分页查询任务。
- 查看任务详情。
- 等待任务完成。
- 失败重试。
- 重新派发等待中的任务。
- 删除任务记录。
- 显示业务对象名称，例如 `资料标题 #8`、`练习名称 #12`。
- 跳转到对应业务页面。

### RAG 与评测

- 支持资料分段向量化。
- 支持写入 Qdrant 向量库。
- 支持检索预览，展示页码、段号、章节标题和相似度。
- 支持 RAG 评测集管理。
- 支持手动维护评测样本。
- 支持导入 CMRC2018 数据集。
- 支持批量评测并统计 Hit@K、Recall@K、MRR 和平均耗时。
- 样本较多时支持渐进展开，避免页面过长。

### AI 学习助手 Agent

- 全局悬浮入口。
- 支持新建通用会话。
- 支持会话列表、详情和删除。
- 支持 SSE 流式回复。
- 支持工具调用轨迹和执行摘要。
- 支持资料检索问答、任务查询、资料查询、题集查询、练习详情等工具。
- 打开时默认进入新对话，不再自动恢复上次退出的会话。
- 不再自动绑定最近资料，用户可在对话中自行指定资料标题或 ID。
- 打开时会检查 AI 配置，如果处于 Mock 模式，会在 Agent 界面显示提示。

## 系统架构

```text
Vue 3 Frontend
  |
  | HTTP / SSE
  v
Spring Boot Backend
  |
  +-- MySQL: 用户、资料、题集、练习、任务、Agent 会话、RAG 评测
  +-- Redis: 缓存与扩展预留
  +-- Qdrant: 资料分段向量存储与语义检索
  +-- Chat API: 总结、出题、判分、Agent 对话与意图识别
  +-- Embedding API: 资料分段向量化
```

## 目录结构

```text
AI-Intelligent-Learning-Assistant-System
├─ backend/                    # Spring Boot 后端
│  ├─ src/main/java/            # 业务代码
│  ├─ src/main/resources/       # 配置文件
│  └─ Dockerfile
├─ frontend/                   # Vue 3 前端
│  ├─ src/api/                  # 接口封装
│  ├─ src/components/           # 通用组件
│  ├─ src/views/                # 页面视图
│  └─ Dockerfile
├─ db/                          # 建表与迁移脚本
├─ docker/mysql/init/           # MySQL 容器初始化脚本
├─ runtime/                     # AI 运行时配置，默认不提交敏感内容
├─ docker-compose.yml           # 本地一键启动
├─ vercel.json                  # Vercel 前端部署配置
└─ README.md
```

## 环境要求

本地开发：

- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8+
- Redis
- Qdrant

Docker 启动：

- Docker Desktop
- Docker Compose V2

## 快速启动

### 方式一：Docker Compose

1. 准备环境变量：

```powershell
Copy-Item .env.example .env
```

2. 启动全部服务：

```powershell
docker compose up -d --build
```

3. 访问服务：

- 前端：http://localhost:5173
- 后端：http://localhost:8083
- MySQL：localhost:3307
- Redis：localhost:6379
- Qdrant：http://localhost:6333

4. 查看日志：

```powershell
docker compose logs -f backend
docker compose logs -f frontend
```

5. 停止服务：

```powershell
docker compose down
```

如果需要清空数据并重新初始化：

```powershell
docker compose down -v
docker compose up -d --build
```

### 方式二：本地手动启动

1. 初始化 MySQL：

```text
db/ai_learning_assistant.sql
db/migrations/*.sql
```

2. 启动 Redis。

3. 启动 Qdrant：

```powershell
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant
```

4. 启动后端：

```powershell
Set-Location backend
mvn spring-boot:run
```

5. 启动前端：

```powershell
Set-Location frontend
npm install
npm run dev
```

## 核心配置

后端支持通过环境变量覆盖配置：

| 变量 | 说明 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | MySQL 连接地址 |
| `SPRING_DATASOURCE_USERNAME` | MySQL 用户名 |
| `SPRING_DATASOURCE_PASSWORD` | MySQL 密码 |
| `SPRING_REDIS_HOST` | Redis 地址 |
| `APP_AI_ENABLED` | 是否启用 AI 能力 |
| `APP_AI_MOCK_MODE` | 是否启用 Mock 模式 |
| `APP_AI_CHAT_PROVIDER_TYPE` | 聊天模型 Provider |
| `APP_AI_BASE_URL` | Chat API Base URL |
| `APP_AI_CHAT_PATH` | Chat API Path |
| `APP_AI_API_KEY` | Chat API Key |
| `APP_AI_DEFAULT_MODEL` | 默认聊天模型 |
| `APP_AI_EMBEDDING_BASE_URL` | Embedding API Base URL |
| `APP_AI_EMBEDDING_API_KEY` | Embedding API Key |
| `APP_AI_DEFAULT_EMBEDDING_MODEL` | 默认 Embedding 模型 |
| `APP_QDRANT_BASE_URL` | Qdrant 地址 |
| `APP_QDRANT_COLLECTION_NAME` | Qdrant Collection 名称 |
| `APP_JWT_SECRET` | JWT 密钥 |

AI 配置页面保存的运行时配置默认写入：

```text
runtime/ai-config.json
```

Docker 环境中映射到：

```text
/app/runtime/ai-config.json
```

## 常用接口

### 认证

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/user/profile`

### 资料

- `POST /api/material/text`
- `POST /api/material/upload`
- `GET /api/material/page`
- `GET /api/material/{id}`
- `PUT /api/material/{id}/title`
- `POST /api/material/{id}/parse`
- `DELETE /api/material/{id}`

### AI 与任务

- `GET /api/ai/config`
- `PUT /api/ai/config`
- `POST /api/ai/tasks/material/{materialId}/summary`
- `POST /api/ai/tasks/material/{materialId}/question-set`
- `POST /api/ai/tasks/practice/{sessionId}/review`
- `POST /api/ai/tasks/material/{materialId}/embedding`
- `GET /api/ai/tasks/page`
- `GET /api/ai/tasks/{taskId}`
- `GET /api/ai/tasks/{taskId}/wait`
- `POST /api/ai/tasks/{taskId}/dispatch`
- `POST /api/ai/tasks/{taskId}/retry`
- `DELETE /api/ai/tasks/{taskId}`

### 题集与练习

- `GET /api/question-set/page`
- `GET /api/question-set/{id}`
- `DELETE /api/question-set/{id}`
- `POST /api/practice/start`
- `POST /api/practice/submit`
- `GET /api/practice/page`
- `GET /api/practice/{sessionId}`
- `PUT /api/practice/{sessionId}/name`
- `GET /api/practice/{sessionId}/review-status`
- `DELETE /api/practice/{sessionId}`

### 学习分析

- `GET /api/wrong-questions/page`
- `GET /api/wrong-questions/{answerId}`
- `DELETE /api/wrong-questions/{answerId}`
- `GET /api/knowledge-mastery/overview`
- `GET /api/learning-analytics/overview`

### RAG

- `GET /api/rag/material/{materialId}/retrieve-preview`
- `GET /api/rag-eval/datasets/page`
- `POST /api/rag-eval/datasets`
- `POST /api/rag-eval/datasets/{datasetId}/samples`
- `POST /api/rag-eval/datasets/{datasetId}/run`
- `GET /api/rag-eval/runs/{runId}`
- `POST /api/rag-eval/import/cmrc2018`

### Agent

- `POST /api/assistant/sessions`
- `GET /api/assistant/sessions/page`
- `GET /api/assistant/sessions/{sessionId}`
- `POST /api/assistant/sessions/{sessionId}/messages`
- `POST /api/assistant/sessions/{sessionId}/messages/stream`
- `DELETE /api/assistant/sessions/{sessionId}`

## 部署说明

当前线上部署方式：

- 前端：Vercel
- 后端：Railway
- 数据库与依赖：Railway / 外部托管服务

前端生产构建：

```powershell
Set-Location frontend
npm run build
```

后端生产构建：

```powershell
Set-Location backend
mvn -DskipTests package
```

Vercel 使用根目录 `vercel.json`：

```json
{
  "installCommand": "cd frontend && npm ci",
  "buildCommand": "cd frontend && npm run build",
  "outputDirectory": "frontend/dist"
}
```

Railway 后端部署时需要确保服务根目录指向 `backend`，并配置好数据库、Redis、Qdrant、AI Provider 和 JWT 相关环境变量。

## 适合写进简历的描述

```text
AI 智能学习助手系统 | Spring Boot + Vue 3 + Qdrant + 大模型 API
```

- 设计并实现资料解析、AI 总结、AI 出题、在线练习、简答题 AI 判分等学习辅助功能，统一接入异步任务中心，支持任务状态追踪、失败重试和结果回流。
- 接入 Qdrant 向量数据库与 Embedding 服务，实现资料分段向量化、语义检索、检索预览和 PDF 页码定位。
- 建设 RAG 检索评测模块，支持人工样本和 CMRC2018 数据导入，统计 Hit@K、Recall@K、MRR 和平均耗时。
- 实现错题本、知识点掌握度和学习分析可视化，基于练习记录自动识别薄弱知识点并辅助复习决策。
- 实现 AI 学习助手的会话、SSE 流式回复、工具调用轨迹、任务联动和 Mock 模式提示。

## 后续规划

- 引入更完整的 Agent 工具编排和权限控制。
- 增加混合检索、重排模型和 RAG A/B 对比。
- 增加学习计划、间隔复习和个性化推荐。
- 增加模型调用成本统计和调用日志。
- 增加 GitHub Actions 自动化构建与部署。

## License

本项目用于学习、课程设计和作品集展示。若用于商业用途，请先确认依赖组件与模型服务的许可要求。
