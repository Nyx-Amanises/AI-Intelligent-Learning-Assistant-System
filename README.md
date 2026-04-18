# AI 智能学习助手系统

一个面向大学生学习场景的全栈项目，围绕“资料整理 -> AI 总结 -> AI 出题 -> 在线练习 -> 简答题 AI 判分 -> RAG 检索 -> AI 学习助手”这条主线构建学习闭环。

这个项目的定位不是单纯的聊天机器人，而是一个带明确业务流的学习平台：

- 用户可以上传 PDF / DOCX / TXT 学习资料，系统会自动解析并分段。
- 用户可以基于资料生成 AI 总结、AI 题集，并进入在线练习。
- 简答题支持 AI 判分，同时展示人工参考答案、AI 评语和资料依据。
- AI 能力统一接入任务中心，便于查看异步执行状态、失败重试和结果追踪。
- 资料支持生成 Embedding，接入 Qdrant 后可做检索预览与后续 RAG。
- 内置 AI 学习助手支持基于上下文发起“总结 / 出题 / 查资料 / 查任务 / 查题集 / 看章节 / 检索问答”等操作。

## 1. 项目亮点

- 业务闭环完整：资料管理、AI 总结、AI 出题、练习记录、AI 判分、任务中心、智能助手全部打通。
- 任务中心化：`SUMMARY`、`QUESTION_GENERATE`、`PRACTICE_REVIEW`、`EMBEDDING` 已统一走任务调度。
- RAG 预埋完整：资料分段、向量化状态、Qdrant 存储、检索预览接口都已接好。
- 助手不只是聊天：支持带上下文进入，也支持自然语言找资料、查任务、查题集、看目录。
- 参数提取升级：从纯正则升级为“LLM 结构化抽取 + 规则兜底”，更接近真实业务助手。
- 前后端分离：前端 Vue 3 + Element Plus，后端 Spring Boot 3 + MyBatis Plus。

## 2. 当前已实现能力

### 2.1 用户与认证

- 用户注册
- 用户登录
- JWT 鉴权
- 获取当前用户信息

### 2.2 资料管理

- 新增文本资料
- 上传 PDF / DOCX / TXT 文件资料
- 资料分页查询
- 资料详情查看
- 删除资料
- PDF / DOCX / TXT 解析
- 自动切分 `material_segment`
- 分段附带页码、段号、章节标题
- 资料列表展示 Embedding 状态与已向量化段数

### 2.3 AI 总结

- 支持 `STANDARD / EXAM / OUTLINE` 三种总结类型
- 可查看最新总结与历史总结
- 可保存为学习笔记
- 前端已改为走任务中心

### 2.4 AI 出题

- 基于资料生成题集
- 支持单选、判断、简答组合出题
- 支持题量、题型数量、难度控制
- 前端已改为走任务中心
- 对长资料已接入“任务化 + 后续可走检索增强”的架构

### 2.5 在线练习与 AI 判分

- 开始练习
- 提交练习
- 查看练习详情
- 查看练习分页记录
- 简答题支持 AI 判分
- 同时展示人工参考答案
- 支持查看 AI 评语、答案解析、资料依据
- 支持等待 AI 评分结果接口

### 2.6 任务中心

- 创建任务
- 提交总结任务
- 提交出题任务
- 提交简答题评分任务
- 提交 Embedding 任务
- 分页查询任务
- 查看任务详情
- 等待任务完成
- 失败重试
- 重新派发
- 删除任务记录

目前已接入的任务类型：

- `SUMMARY`
- `QUESTION_GENERATE`
- `PRACTICE_REVIEW`
- `EMBEDDING`

### 2.7 Embedding / RAG / Qdrant

- 资料分段支持 Embedding 字段和状态管理
- Embedding 模型支持单独配置
- 向量写入 Qdrant
- 提供检索预览接口
- 检索结果返回页码、段号、章节标题、相似度
- 为后续 AI 助手 RAG、多轮问答、知识定位打好基础

### 2.8 AI 学习助手

已支持：

- 创建通用会话
- 创建带上下文会话
- 普通消息回复
- SSE 流式回复
- 会话分页
- 会话详情
- 删除会话
- 会话记忆记录
- 工具调用轨迹记录

当前助手工具体系：

- `material.list`：查看资料列表
- `material.search`：按标题关键词找资料
- `material.detail`：查看当前资料详情
- `material.chapter_outline`：查看章节 / 目录 / 某一章线索
- `question_set.list`：查看题集列表
- `question_set.detail`：查看当前题集详情
- `task.list`：查看任务列表
- `task.get_status`：查看任务状态
- `practice.detail`：查看练习详情
- `rag.retrieve`：基于当前资料做检索问答
- `task.submit_summary`：自然语言触发 AI 总结任务
- `task.submit_question_generate`：自然语言触发 AI 出题任务

助手参数提取方式：

- 第一层：LLM 结构化抽取
- 第二层：规则兜底解析
- 第三层：上下文绑定与待确认动作恢复

## 3. 技术栈

### 3.1 前端

- Vue 3
- TypeScript
- Vite
- Element Plus
- Pinia
- Axios
- Vue Router

### 3.2 后端

- Spring Boot 3.3.4
- MyBatis Plus 3.5.7
- MySQL 8
- Redis
- JWT
- Lombok
- PDFBox
- Apache POI

### 3.3 AI / 向量检索

- OpenAI Compatible Chat API
- 独立 Embedding Provider 配置
- Qdrant
- SSE 流式输出

## 4. 系统架构

```text
Frontend (Vue 3 + Element Plus)
        |
        v
Backend API (Spring Boot)
        |
        +-- MySQL：用户 / 资料 / 题集 / 练习 / 任务 / 助手会话
        +-- Redis：缓存与扩展预留
        +-- AI Chat API：总结 / 出题 / 判分 / 助手推理
        +-- Embedding API：分段向量化
        +-- Qdrant：向量存储与检索
```

## 5. 目录结构

```text
AI-Intelligent-Learning-Assistant-System
├─ backend                      # Spring Boot 后端
│  ├─ src/main/java             # 业务代码
│  ├─ src/main/resources        # 配置文件
│  └─ README.md                 # 后端单独说明
├─ frontend                     # Vue 3 前端
│  ├─ src/api                   # 接口封装
│  ├─ src/views                 # 页面视图
│  ├─ src/components            # 组件
│  └─ src/stores                # 状态管理
├─ db
│  ├─ ai_learning_assistant.sql # 初始建表脚本
│  └─ migrations                # 增量迁移脚本
├─ docs                         # 接口设计与数据库设计文档
├─ runtime                      # 运行时配置
├─ output                       # 导出与调试产物
└─ README.md                    # 项目总览
```

## 6. 环境要求

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8.0+
- Redis 6+
- Qdrant 1.8+（如果要启用 Embedding / 检索 / RAG）

## 7. 数据库初始化

### 7.1 第一步：执行基础脚本

先执行：

```sql
db/ai_learning_assistant.sql
```

### 7.2 第二步：按时间顺序执行迁移脚本

依次执行：

```text
db/migrations/2026-04-16_add_summary_type.sql
db/migrations/2026-04-16_expand_question_item_correct_answer.sql
db/migrations/2026-04-17_add_material_segment_embedding_fields.sql
db/migrations/2026-04-17_add_practice_answer_ai_review.sql
db/migrations/2026-04-17_create_ai_task_table.sql
db/migrations/2026-04-17_create_assistant_tables.sql
db/migrations/2026-04-17_expand_ai_generation_record_error_message.sql
db/migrations/2026-04-18_add_assistant_pending_action_fields.sql
```

说明：

- 基础脚本负责创建主业务表。
- `migrations` 目录负责补增量字段、任务中心、助手会话、Embedding 字段等后续能力。
- 如果数据库是旧版本，务必把迁移脚本补齐，否则容易出现字段缺失报错。

## 8. 后端启动

### 8.1 默认配置位置

配置文件：

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`

当前默认端口：

- 后端：`8083`

### 8.2 关键配置项

`application.yml` 中重点关注：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3307/ai_learning_assistant...
    username: root
    password: 123456789

  data:
    redis:
      host: 127.0.0.1
      port: 6379

app:
  ai:
    enabled: true
    mock-mode: true
    base-url: https://api.openai.com
    chat-path: /v1/chat/completions
    embedding-provider-type: OPENAI_COMPATIBLE
    embedding-base-url:
    embedding-path: /v1/embeddings
    api-key: replace-with-your-api-key
    embedding-api-key:
    default-model: gpt-4o-mini
    default-embedding-model: text-embedding-3-small
    config-file: ./runtime/ai-config.json

  qdrant:
    enabled: true
    base-url: http://127.0.0.1:6333
    collection-name: material_segment_vectors
```

### 8.3 启动命令

在项目根目录执行：

```bash
cd backend
mvn spring-boot:run
```

或者：

```bash
cd backend
mvn clean package
java -jar target/ai-learning-assistant-backend-1.0.0.jar
```

## 9. 前端启动

当前默认端口：

- 前端：`5173`

代理配置：

- `/api -> http://127.0.0.1:8083`

启动命令：

```bash
cd frontend
npm install
npm run dev
```

打包命令：

```bash
cd frontend
npm run build
```

## 10. AI 配置说明

项目支持两套 AI 配置思路：

### 10.1 聊天模型

用于：

- AI 总结
- AI 出题
- 简答题 AI 判分
- AI 学习助手对话与结构化抽取

### 10.2 Embedding 模型

用于：

- 资料分段向量化
- 检索预览
- 后续 RAG

当前架构支持聊天模型和 Embedding 模型分开配置，因此可以：

- 聊天走 OpenAI Compatible / 中转站
- Embedding 单独走别的供应商

运行时配置会写入：

```text
runtime/ai-config.json
```

前端也提供了 AI 配置页面用于调整这些参数。

## 11. Qdrant 使用说明

如果你只想先体验基础资料管理、总结、出题、练习，可以暂时不开 Qdrant。

如果你要启用下面这些功能，建议启动 Qdrant：

- Embedding 任务
- 检索预览
- RAG 检索问答
- 后续知识库助手

### 11.1 Docker 启动示例

```bash
docker run -d ^
  --name qdrant ^
  -p 6333:6333 ^
  -v qdrant_storage:/qdrant/storage ^
  qdrant/qdrant
```

### 11.2 启用条件

- `app.qdrant.enabled=true`
- Qdrant 服务已启动
- Embedding API 可用

## 12. 关键页面

当前前端主要页面：

- 首页：`DashboardView.vue`
- 登录页：`LoginView.vue`
- 资料管理：`MaterialView.vue`
- AI 总结：`SummaryView.vue`
- AI 出题 / 题集：`QuizView.vue`
- 练习记录：`PracticeView.vue`
- 任务中心：`AiTaskCenterView.vue`
- AI 配置：`AiConfigView.vue`
- 全局 AI 助手抽屉：`AssistantDrawer.vue`

## 13. 主要接口模块

### 13.1 认证

- `POST /api/auth/register`
- `POST /api/auth/login`

### 13.2 资料

- `POST /api/material/text`
- `POST /api/material/upload`
- `GET /api/material/page`
- `GET /api/material/{id}`
- `POST /api/material/{id}/parse`
- `DELETE /api/material/{id}`

### 13.3 AI 总结 / 出题

- `GET /api/ai/config`
- `PUT /api/ai/config`
- `POST /api/ai/material/{id}/summary`
- `GET /api/ai/material/{id}/latest-summary`
- `GET /api/ai/material/{id}/summary-history`
- `GET /api/ai/summary-history`
- `POST /api/ai/material/{id}/question-set`

### 13.4 任务中心

- `POST /api/ai/tasks`
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

### 13.5 题集与练习

- `GET /api/question-set/page`
- `GET /api/question-set/{id}`
- `DELETE /api/question-set/{id}`
- `POST /api/practice/start`
- `POST /api/practice/submit`
- `GET /api/practice/page`
- `GET /api/practice/{sessionId}`
- `GET /api/practice/{sessionId}/review-status`
- `DELETE /api/practice/{sessionId}`

### 13.6 RAG

- `GET /api/rag/material/{materialId}/retrieve-preview`

### 13.7 AI 学习助手

- `POST /api/assistant/sessions`
- `GET /api/assistant/sessions/page`
- `GET /api/assistant/sessions/{sessionId}`
- `POST /api/assistant/sessions/{sessionId}/messages`
- `POST /api/assistant/sessions/{sessionId}/messages/stream`
- `DELETE /api/assistant/sessions/{sessionId}`

## 14. 典型业务流程

### 14.1 资料学习流程

1. 注册并登录系统。
2. 上传 PDF / DOCX / TXT，或直接新建文本资料。
3. 执行资料解析，生成资料分段。
4. 生成 AI 总结，提炼知识点。
5. 生成 AI 题集，进入在线练习。
6. 提交练习，客观题立即判分，简答题异步走 AI 判分。
7. 在练习记录中查看 AI 评语、参考答案和资料依据。

### 14.2 RAG / 检索流程

1. 对资料执行 Embedding 任务。
2. 分段向量写入 Qdrant。
3. 调用检索预览接口检查命中质量。
4. 后续在 AI 助手中将检索片段作为上下文回答问题。

### 14.3 AI 助手流程

1. 从资料、题集、练习记录等页面进入助手，会自动带入上下文。
2. 用户可直接说“帮我总结这份资料”“出 20 道单选”“查一下已生成 Embedding 的资料”。
3. 助手先做结构化意图提取，再决定调用哪个业务工具。
4. 对需要补充信息的场景，助手会进入待确认状态，并在用户下一句话中继续完成动作。

## 15. 文档位置

项目内已有补充文档：

- [后端接口设计](./docs/后端接口设计.md)
- [数据库设计说明](./docs/数据库设计说明.md)
- [后端 README](./backend/README.md)

## 16. 适合写进简历的点

如果你打算把这个项目写进简历，可以突出这些关键词：

- AI 学习助手
- Spring Boot + Vue 全栈项目
- 任务中心化异步架构
- Qdrant 向量检索 / RAG
- 大模型结构化抽取
- SSE 流式对话
- AI 自动出题与简答题评分
- PDF / DOCX 资料解析

## 17. 后续可扩展方向

- 更完整的 Agent 工具编排与多轮澄清
- 基于检索结果的高价值知识点筛选
- 学习计划 / 错题本 / 复习曲线
- 资料标签、课程、章节树模型
- 多模型切换与成本统计
- 第三方登录
- 更完整的部署方案与 CI/CD

## 18. 说明

当前仓库更适合作为“可持续扩展的简历项目”：

- 业务链路完整
- 代码结构清晰
- 已从普通 CRUD 项目升级到 AI + 任务中心 + RAG + 助手协作架构

后续继续补 Agent、RAG 问答、学习计划、多轮工具调用后，项目含金量会更高。
