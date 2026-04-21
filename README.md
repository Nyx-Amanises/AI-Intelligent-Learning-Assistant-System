# AI 智能学习助手系统

一个面向大学生学习场景的全栈项目，围绕“资料上传解析 -> AI 总结 -> AI 出题 -> 在线练习 -> 简答题 AI 判分 -> 错题本 -> 知识点掌握度 -> 学习分析可视化 -> RAG 检索问答”构建学习闭环。

这个项目不是单纯的聊天机器人，而是一个带业务流程、异步任务中心、RAG 检索评测和学习效果分析的 AI 学习平台。它适合作为软件工程实习简历项目展示，也适合作为后续扩展 Agent、RAG、学习计划和推荐系统的基础。

## 项目亮点

- 完整学习闭环：资料管理、AI 总结、AI 出题、在线练习、AI 判分、错题本、知识点掌握度、学习分析全部打通。
- 异步任务中心：`SUMMARY`、`QUESTION_GENERATE`、`PRACTICE_REVIEW`、`EMBEDDING` 统一任务化，支持状态追踪、失败重试、重新派发和删除记录。
- RAG 工程化能力：资料分段、Embedding、Qdrant 向量存储、检索预览、PDF 页码定位、RAG 评测集、CMRC2018 导入、Hit@K / Recall@K / MRR 指标。
- AI 学习助手：支持会话、SSE 流式回复、上下文绑定、工具调用轨迹、记忆记录、LLM 结构化意图抽取和规则兜底。
- 学习数据分析：基于练习记录自动生成错题本、知识点掌握度、题型表现、资料表现、薄弱知识点和趋势图表。
- 前后端分离：后端 Spring Boot 3 + MyBatis Plus，前端 Vue 3 + TypeScript + Element Plus。
- 支持 Docker Compose 一键启动 MySQL、Redis、Qdrant、后端和前端。

## 技术栈

### 前端

- Vue 3
- TypeScript
- Vite
- Element Plus
- Pinia
- Axios
- Vue Router
- CSS / SVG 轻量图表

### 后端

- Spring Boot 3.3.4
- MyBatis Plus 3.5.7
- MySQL 8
- Redis
- JWT
- Lombok
- PDFBox
- Apache POI

### AI / RAG

- OpenAI Compatible Chat API
- 豆包 / DeepSeek 等兼容模型配置
- 独立 Embedding Provider 配置
- Qdrant 向量数据库
- SSE 流式输出

## 功能模块

### 用户与认证

- 用户注册
- 用户登录
- JWT 鉴权
- 获取当前用户信息

### 资料管理

- 新增文本资料
- 上传 PDF / DOCX / TXT 文件资料
- 资料分页、筛选、详情、改名、删除
- PDF / DOCX / TXT 解析
- 自动切分 `material_segment`
- 分段记录页码、段号、章节标题、字符数
- 资料列表展示 Embedding 状态和已向量化段数

### AI 总结

- 支持标准总结、考试复习、提纲总结
- 支持最新总结和历史总结列表
- 走任务中心异步执行
- 可保存为学习笔记
- 总结列表支持筛选和详情查看

### AI 出题

- 基于资料生成题集
- 支持单选、判断、简答题数量配置
- 支持难度配置和题集名称自定义
- 长资料出题支持检索增强路线
- 走任务中心异步执行

### 在线练习与 AI 判分

- 开始练习
- 提交练习
- 客观题即时判分
- 简答题异步 AI 判分
- 简答题双展示：AI 判分 + 人工参考答案
- 支持 AI 评语、答案解析、资料依据
- 支持练习记录改名、再次练习、删除

### 错题本

- 自动收集错误题目和低分简答题
- 支持按资料、题型、关键词筛选
- 支持查看解析、查看原练习
- 支持移出错题本

### 知识点掌握度

- 按 `question_item.knowledge_point` 聚合练习记录
- 统计作答次数、正确次数、错题次数、得分率
- 自动划分已掌握、基本掌握、待巩固、薄弱
- 支持按资料、题型、掌握状态、关键词筛选
- 支持跳转查看相关错题

### 学习分析可视化

- 平均正确率、平均得分率、错题次数、薄弱知识点数
- 掌握度分布环形图
- 最近练习趋势折线图
- 题型表现柱状图
- 资料表现排行
- 薄弱知识点卡片

### 任务中心

已接入任务类型：

- `SUMMARY`
- `QUESTION_GENERATE`
- `PRACTICE_REVIEW`
- `EMBEDDING`

支持能力：

- 分页查询任务
- 查看任务详情
- 等待任务完成
- 失败重试
- 重新派发
- 删除任务记录
- 任务结果跳转业务页面

### Embedding / RAG / Qdrant

- 资料分段向量化
- Embedding 模型独立配置
- 向量写入 Qdrant
- 检索预览
- 检索结果返回页码、段号、章节标题、相似度
- 支持 RAG 评测集和批量评测

### RAG 评测

- 创建评测集
- 手动维护评测样本
- 导入 CMRC2018 数据集
- 批量运行检索评测
- 统计 Hit@1 / Hit@3 / Hit@5
- 统计 Recall@1 / Recall@3 / Recall@5
- 统计 MRR 和平均耗时

### AI 学习助手

助手支持：

- 创建通用会话
- 创建带资料、题集、练习、任务上下文的会话
- 普通对话
- SSE 流式回复
- 会话分页、详情、删除
- 记忆记录
- 工具调用轨迹

当前工具体系：

- `material.list`：查看资料列表
- `material.search`：按标题关键词找资料
- `material.detail`：查看资料详情
- `material.chapter_outline`：查看章节 / 目录线索
- `question_set.list`：查看题集列表
- `question_set.detail`：查看题集详情
- `task.list`：查看任务列表
- `task.get_status`：查看任务状态
- `practice.detail`：查看练习详情
- `rag.retrieve`：基于资料检索问答
- `task.submit_summary`：自然语言触发 AI 总结任务
- `task.submit_question_generate`：自然语言触发 AI 出题任务

## 系统架构

```text
Vue 3 Frontend
        |
        v
Spring Boot Backend
        |
        +-- MySQL: 用户 / 资料 / 分段 / 题集 / 练习 / 任务 / 助手 / 评测
        +-- Redis: 缓存和扩展预留
        +-- Chat API: 总结 / 出题 / 判分 / 助手意图识别 / 对话
        +-- Embedding API: 资料分段向量化
        +-- Qdrant: 向量存储与语义检索
```

## 目录结构

```text
AI-Intelligent-Learning-Assistant-System
├─ backend                      # Spring Boot 后端
│  ├─ src/main/java             # 业务代码
│  ├─ src/main/resources        # 配置文件
│  ├─ Dockerfile                # 后端镜像构建
│  └─ README.md                 # 后端说明
├─ frontend                     # Vue 3 前端
│  ├─ src/api                   # 接口封装
│  ├─ src/views                 # 页面视图
│  ├─ src/components            # 通用组件
│  ├─ Dockerfile                # 前端镜像构建
│  └─ nginx.conf                # 前端 Nginx 配置
├─ db
│  ├─ ai_learning_assistant.sql # 基础建表脚本
│  └─ migrations                # 增量迁移脚本
├─ docker/mysql/init            # MySQL 容器初始化脚本
├─ docs                         # 接口设计和数据库设计文档
├─ runtime                      # AI 运行时配置，默认不提交
├─ output                       # 导出与调试产物
├─ docker-compose.yml           # 一键启动编排
├─ .env.example                 # Docker 环境变量示例
└─ README.md                    # 项目总览
```

## 环境要求

本地开发：

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8.0+
- Redis
- Qdrant

Docker 启动：

- Docker Desktop
- Docker Compose V2

你的 Windows 环境里：

- Redis 本地目录：`D:\Redis-x64-3.0.504`
- Docker Desktop 目录：`D:\Docker\DockerDesktop`

说明：Docker Compose 不需要直接引用 Docker Desktop 的安装目录；只要 Docker Desktop 已启动，命令行能执行 `docker version` 和 `docker compose version` 即可。

## Docker Compose 一键启动

### 1. 准备环境变量

在项目根目录执行：

```powershell
Copy-Item .env.example .env
```

如果只是本地演示，可以先保持 `APP_AI_MOCK_MODE=true`。如果要真实调用模型，把 `.env` 中的模型地址、模型名和 API Key 改成你的配置。

### 2. 启动全部服务

先确认 Docker Desktop 已启动。你的安装目录是 `D:\Docker\DockerDesktop`，如果没有启动，可以先手动打开 Docker Desktop，或在 PowerShell 中执行：

```powershell
Start-Process "D:\Docker\DockerDesktop\Docker Desktop.exe"
```

等 Docker Desktop 显示运行中后，再执行：

```powershell
docker compose up -d --build
```

启动后访问：

- 前端：http://localhost:5173
- 后端：http://localhost:8083
- MySQL：`localhost:3307`
- Redis：`localhost:6379`
- Qdrant：http://localhost:6333

### 3. 查看日志

```powershell
docker compose logs -f backend
docker compose logs -f frontend
```

### 4. 停止服务

```powershell
docker compose down
```

如果想清空 MySQL、Redis、Qdrant 数据并重新执行初始化脚本：

```powershell
docker compose down -v
docker compose up -d --build
```

### 5. 数据库初始化说明

MySQL 容器首次启动时会自动执行：

1. `db/ai_learning_assistant.sql`
2. `db/migrations/*.sql`

初始化脚本位置：

```text
docker/mysql/init/01-init-database.sh
```

注意：MySQL 官方镜像只会在数据卷为空时执行初始化脚本。如果你改了 SQL 后想重新初始化，需要先执行 `docker compose down -v`。

## 本地手动启动

### 1. 启动 MySQL

创建数据库并执行：

```sql
db/ai_learning_assistant.sql
```

然后按时间顺序执行：

```text
db/migrations/2026-04-16_add_summary_type.sql
db/migrations/2026-04-16_expand_question_item_correct_answer.sql
db/migrations/2026-04-17_add_material_segment_embedding_fields.sql
db/migrations/2026-04-17_add_practice_answer_ai_review.sql
db/migrations/2026-04-17_create_ai_task_table.sql
db/migrations/2026-04-17_create_assistant_tables.sql
db/migrations/2026-04-17_expand_ai_generation_record_error_message.sql
db/migrations/2026-04-18_add_assistant_pending_action_fields.sql
db/migrations/2026-04-20_create_rag_eval_tables.sql
```

### 2. 启动本地 Redis

如果使用你本机的 Redis：

```powershell
Set-Location D:\Redis-x64-3.0.504
.\redis-server.exe .\redis.windows.conf
```

如果没有配置文件，也可以直接：

```powershell
Set-Location D:\Redis-x64-3.0.504
.\redis-server.exe
```

### 3. 启动 Qdrant

```powershell
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant
```

### 4. 启动后端

```powershell
Set-Location backend
mvn spring-boot:run
```

默认后端端口：`8083`。

### 5. 启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

默认前端端口：`5173`。

## 关键配置

后端配置文件：

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`

Docker 和本地均支持通过环境变量覆盖配置，例如：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`
- `APP_AI_BASE_URL`
- `APP_AI_API_KEY`
- `APP_AI_DEFAULT_MODEL`
- `APP_AI_EMBEDDING_BASE_URL`
- `APP_AI_EMBEDDING_API_KEY`
- `APP_AI_DEFAULT_EMBEDDING_MODEL`
- `APP_QDRANT_BASE_URL`
- `APP_FILE_UPLOAD_DIR`

AI 配置页面保存的运行时配置默认写入：

```text
runtime/ai-config.json
```

Docker 环境中映射到：

```text
/app/runtime/ai-config.json
```

## 主要页面

- 首页：学习概览
- 资料管理：上传、解析、Embedding、检索预览
- 任务中心：AI 总结、出题、评分、Embedding 任务管理
- RAG 评测：评测集、样本、运行结果
- AI 总结：总结列表和生成
- AI 出题：题集列表和生成
- 练习记录：答题记录、AI 判分、再次练习
- 错题本：自动收集错题和低分简答题
- 掌握度：知识点掌握度统计
- 学习分析：可视化图表看板
- AI 配置：聊天模型和 Embedding 模型配置
- AI 学习助手：全局悬浮入口和对话抽屉

## 主要接口

### 认证

- `POST /api/auth/register`
- `POST /api/auth/login`

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

### 错题与学习分析

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

### AI 学习助手

- `POST /api/assistant/sessions`
- `GET /api/assistant/sessions/page`
- `GET /api/assistant/sessions/{sessionId}`
- `POST /api/assistant/sessions/{sessionId}/messages`
- `POST /api/assistant/sessions/{sessionId}/messages/stream`
- `DELETE /api/assistant/sessions/{sessionId}`

## 典型业务流程

### 资料学习流程

1. 注册并登录系统。
2. 上传 PDF / DOCX / TXT 或创建文本资料。
3. 解析资料并生成分段。
4. 生成 AI 总结。
5. 生成 AI 题集。
6. 在线练习并提交。
7. 客观题立即判分，简答题异步 AI 判分。
8. 查看练习解析、错题本、知识点掌握度和学习分析图表。

### RAG 流程

1. 对资料执行 Embedding 任务。
2. 分段向量写入 Qdrant。
3. 使用检索预览检查召回效果。
4. 建立 RAG 评测集，批量评估检索质量。
5. 在 AI 助手中基于检索片段回答资料问题。

### AI 助手流程

1. 从资料、题集、练习、任务等页面进入助手。
2. 助手自动携带当前页面上下文。
3. 用户用自然语言提出请求。
4. 后端先做 LLM 结构化意图抽取，再调用业务工具。
5. 若参数不足，进入待确认状态，用户补充后继续原任务。

## 适合写进简历的描述

可以写成：

```text
AI 智能学习助手系统 | Spring Boot + Vue3 + Qdrant + 大模型 API
```

简历要点示例：

- 设计并实现资料解析、AI 总结、AI 出题、简答题 AI 判分等学习辅助功能，统一接入异步任务中心，支持任务状态追踪、失败重试和结果回溯。
- 接入 Qdrant 向量数据库与 Embedding 服务，实现资料分段向量化、语义检索、检索预览和 PDF 页码定位。
- 建设 RAG 检索评测模块，支持人工标注样本和 CMRC2018 数据导入，统计 Hit@K、Recall@K、MRR、平均耗时等指标。
- 实现错题本、知识点掌握度和学习分析可视化，基于练习记录自动识别薄弱知识点并辅助复习决策。
- 实现 AI 学习助手的会话、SSE 流式回复、工具调用轨迹、上下文绑定和 LLM 结构化意图抽取。

## 后续扩展方向

- 更完整的 Agent 工具规划层
- LangChain4j 或自研 Tool Calling 编排升级
- 混合检索、重排、检索效果 AB 对比
- 学习计划和间隔复习
- 错题推荐和薄弱点专项训练
- 多模型成本统计和调用日志
- 第三方登录
- CI/CD 自动部署

## 相关文档

- [后端 README](./backend/README.md)
- [后端接口设计](./docs/后端接口设计.md)
- [数据库设计说明](./docs/数据库设计说明.md)
