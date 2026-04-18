# AI 智能学习助手系统后端

后端基于 `Spring Boot 3 + MyBatis Plus + MySQL + Redis` 构建，负责承接资料解析、AI 总结、AI 出题、在线练习、AI 判分、任务中心、RAG 检索以及 AI 学习助手等核心能力。

如果你是第一次接手这个项目，建议先看根目录 [README.md](../README.md)，那里是完整的项目总览；本文件更偏向后端开发与接口运行说明。

## 1. 技术栈

- Spring Boot 3.3.4
- MyBatis Plus 3.5.7
- MySQL 8
- Redis
- JWT
- Lombok
- PDFBox
- Apache POI

## 2. 后端已实现模块

- 用户注册、登录、JWT 鉴权
- 资料上传、文本资料创建、资料分页、资料详情、资料删除
- PDF / DOCX / TXT 解析与资料分段
- AI 总结、总结历史、最新总结
- AI 出题、题集详情、题集列表、题集删除
- 练习开始、练习提交、练习详情、练习分页
- 简答题 AI 判分与评分结果查询
- AI 任务中心
- Embedding 任务与向量状态管理
- RAG 检索预览
- AI 助手会话、消息、SSE 流式回复、工具调用记录、记忆记录

## 3. 目录说明

```text
backend
├─ src/main/java/com/aiassistant/learning
│  ├─ controller      # 控制器
│  ├─ service         # 业务层
│  ├─ mapper          # MyBatis Plus Mapper
│  ├─ entity          # 实体类
│  ├─ dto             # 请求参数
│  ├─ vo              # 返回对象
│  ├─ common          # 公共响应、异常、工具
│  └─ config          # 配置类
├─ src/main/resources
│  ├─ application.yml
│  └─ application-dev.yml
└─ uploads            # 上传资料目录
```

## 4. 启动前准备

### 4.1 数据库

先执行：

```sql
../db/ai_learning_assistant.sql
```

再按顺序执行：

```text
../db/migrations/2026-04-16_add_summary_type.sql
../db/migrations/2026-04-16_expand_question_item_correct_answer.sql
../db/migrations/2026-04-17_add_material_segment_embedding_fields.sql
../db/migrations/2026-04-17_add_practice_answer_ai_review.sql
../db/migrations/2026-04-17_create_ai_task_table.sql
../db/migrations/2026-04-17_create_assistant_tables.sql
../db/migrations/2026-04-17_expand_ai_generation_record_error_message.sql
../db/migrations/2026-04-18_add_assistant_pending_action_fields.sql
```

### 4.2 中间件

- MySQL 8
- Redis
- Qdrant（如果要启用 Embedding / 检索）

## 5. 核心配置

配置文件位于：

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

默认后端端口：

- `8083`

重点配置项：

- `spring.datasource.*`：MySQL 连接
- `spring.data.redis.*`：Redis 连接
- `app.ai.*`：聊天模型与 Embedding 配置
- `app.qdrant.*`：Qdrant 配置
- `app.file.upload-dir`：资料上传目录
- `app.jwt.*`：JWT 密钥与过期时间

运行时 AI 配置会写入：

```text
../runtime/ai-config.json
```

## 6. 启动命令

开发环境：

```bash
mvn spring-boot:run
```

打包运行：

```bash
mvn clean package
java -jar target/ai-learning-assistant-backend-1.0.0.jar
```

## 7. 主要接口

### 7.1 认证

- `POST /api/auth/register`
- `POST /api/auth/login`

### 7.2 资料

- `POST /api/material/text`
- `POST /api/material/upload`
- `GET /api/material/page`
- `GET /api/material/{id}`
- `POST /api/material/{id}/parse`
- `DELETE /api/material/{id}`

### 7.3 AI 能力

- `GET /api/ai/config`
- `PUT /api/ai/config`
- `POST /api/ai/material/{id}/summary`
- `GET /api/ai/material/{id}/latest-summary`
- `GET /api/ai/material/{id}/summary-history`
- `GET /api/ai/summary-history`
- `POST /api/ai/material/{id}/question-set`

### 7.4 任务中心

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

### 7.5 练习与题集

- `GET /api/question-set/page`
- `GET /api/question-set/{id}`
- `DELETE /api/question-set/{id}`
- `POST /api/practice/start`
- `POST /api/practice/submit`
- `GET /api/practice/page`
- `GET /api/practice/{sessionId}`
- `GET /api/practice/{sessionId}/review-status`
- `DELETE /api/practice/{sessionId}`

### 7.6 RAG 与助手

- `GET /api/rag/material/{materialId}/retrieve-preview`
- `POST /api/assistant/sessions`
- `GET /api/assistant/sessions/page`
- `GET /api/assistant/sessions/{sessionId}`
- `POST /api/assistant/sessions/{sessionId}/messages`
- `POST /api/assistant/sessions/{sessionId}/messages/stream`
- `DELETE /api/assistant/sessions/{sessionId}`

## 8. AI 助手后端说明

助手模块不只是普通对话接口，当前已经具备：

- 会话上下文绑定
- 资料 / 任务 / 题集 / 练习上下文识别
- LLM 结构化意图提取
- 规则兜底解析
- 工具调用编排
- 待确认动作恢复
- SSE 流式返回

当前已实现工具：

- `material.list`
- `material.search`
- `material.detail`
- `material.chapter_outline`
- `question_set.list`
- `question_set.detail`
- `task.list`
- `task.get_status`
- `practice.detail`
- `rag.retrieve`
- `task.submit_summary`
- `task.submit_question_generate`

## 9. 开发建议

- 新增 AI 业务时，优先考虑是否应该接入任务中心，而不是直接同步执行。
- 新增助手能力时，优先考虑是否应做成工具，而不是把逻辑全部塞进聊天提示词。
- 新增数据库字段时，保持 `db/migrations` 增量脚本同步更新。
- 若涉及中文文本读取，统一使用 `UTF-8`。

## 10. 相关文档

- [项目总览 README](../README.md)
- [后端接口设计](../docs/后端接口设计.md)
- [数据库设计说明](../docs/数据库设计说明.md)
