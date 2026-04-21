# AI 智能学习助手系统后端

后端基于 `Spring Boot 3 + MyBatis Plus + MySQL + Redis` 构建，负责资料解析、AI 总结、AI 出题、在线练习、AI 判分、任务中心、RAG 检索、RAG 评测、错题本、掌握度统计、学习分析和 AI 学习助手等核心能力。

如果是第一次接手项目，建议先阅读根目录 [README.md](../README.md)，那里包含完整项目说明和 Docker Compose 一键启动方式。本文件更偏向后端开发与接口说明。

## 技术栈

- Spring Boot 3.3.4
- MyBatis Plus 3.5.7
- MySQL 8
- Redis
- JWT
- Lombok
- PDFBox
- Apache POI
- Qdrant
- OpenAI Compatible API

## 模块说明

```text
src/main/java/com/aiassistant/learning
├─ controller      # REST 控制器
├─ service         # 业务接口与实现
├─ mapper          # MyBatis Plus Mapper
├─ entity          # 数据库实体
├─ dto             # 请求参数
├─ vo              # 返回对象
├─ config          # 配置类
├─ context         # 用户上下文
├─ common          # 公共响应、异常、基础实体
└─ service/assistant
   ├─ tool         # AI 助手工具
   └─ ...          # 意图识别、编排、记忆、轨迹
```

主要业务模块：

- 认证：注册、登录、JWT 鉴权
- 资料：上传、解析、分段、改名、删除
- AI：配置、总结、出题、简答题评分
- 任务中心：总结、出题、评分、Embedding 任务
- 练习：开始练习、提交、详情、记录、重命名、删除
- 错题本：错题分页、详情、移出错题本
- 掌握度：按知识点聚合正确率和得分率
- 学习分析：趋势图、题型表现、资料表现、薄弱知识点
- RAG：Embedding、Qdrant、检索预览
- RAG 评测：评测集、样本、批量评测、CMRC2018 导入
- AI 助手：会话、消息、SSE、工具调用、记忆、待确认动作

## 配置文件

配置文件位于：

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

核心配置均支持环境变量覆盖：

```yaml
server.port: ${SERVER_PORT:8083}
spring.datasource.url: ${SPRING_DATASOURCE_URL:...}
spring.datasource.username: ${SPRING_DATASOURCE_USERNAME:root}
spring.datasource.password: ${SPRING_DATASOURCE_PASSWORD:123456789}
spring.data.redis.host: ${SPRING_REDIS_HOST:127.0.0.1}
spring.data.redis.port: ${SPRING_REDIS_PORT:6379}
app.ai.base-url: ${APP_AI_BASE_URL:https://api.openai.com}
app.ai.api-key: ${APP_AI_API_KEY:replace-with-your-api-key}
app.qdrant.base-url: ${APP_QDRANT_BASE_URL:http://127.0.0.1:6333}
app.file.upload-dir: ${APP_FILE_UPLOAD_DIR:...}
```

AI 配置页面保存的运行时配置默认写入：

```text
../runtime/ai-config.json
```

Docker 容器中写入：

```text
/app/runtime/ai-config.json
```

## 数据库初始化

先执行基础脚本：

```sql
../db/ai_learning_assistant.sql
```

再按顺序执行迁移：

```text
../db/migrations/2026-04-16_add_summary_type.sql
../db/migrations/2026-04-16_expand_question_item_correct_answer.sql
../db/migrations/2026-04-17_add_material_segment_embedding_fields.sql
../db/migrations/2026-04-17_add_practice_answer_ai_review.sql
../db/migrations/2026-04-17_create_ai_task_table.sql
../db/migrations/2026-04-17_create_assistant_tables.sql
../db/migrations/2026-04-17_expand_ai_generation_record_error_message.sql
../db/migrations/2026-04-18_add_assistant_pending_action_fields.sql
../db/migrations/2026-04-20_create_rag_eval_tables.sql
```

如果用 Docker Compose，首次启动 MySQL 容器会自动执行基础脚本和所有迁移脚本。

## 本地启动

```powershell
mvn spring-boot:run
```

打包运行：

```powershell
mvn clean package
java -jar target/ai-learning-assistant-backend-1.0.0.jar
```

默认端口：

```text
8083
```

## Docker 构建

项目根目录已提供 `docker-compose.yml`。后端镜像也可以单独构建：

```powershell
docker build -t ai-learning-backend ./backend
docker run --rm -p 8083:8083 ai-learning-backend
```

推荐使用根目录一键启动：

```powershell
docker compose up -d --build
```

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

### AI 配置与任务

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

### RAG 与评测

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

## AI 助手后端设计

助手不是简单调用大模型回复，而是分层处理：

1. 读取会话上下文、页面上下文和记忆。
2. 调用 LLM 做结构化意图抽取。
3. 使用规则解析兜底。
4. 规划需要调用的业务工具。
5. 对缺少参数的任务进入 pending 状态。
6. 用户补充参数后恢复原动作。
7. 将工具结果和引用轨迹写入消息。

当前已实现工具包括：

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

## 开发建议

- 新增 AI 业务优先考虑接入任务中心。
- 新增助手能力优先做成工具，不要只堆提示词。
- 新增数据库字段必须同步 `db/migrations`。
- 中文资料读取统一使用 UTF-8。
- RAG 改动建议同步补评测样本，用指标观察效果变化。

## 相关文档

- [项目总览 README](../README.md)
- [后端接口设计](../docs/后端接口设计.md)
- [数据库设计说明](../docs/数据库设计说明.md)
