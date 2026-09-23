# db 目录说明

从 2026-09 起，表结构由后端 Flyway 管理，唯一权威来源是：

```
backend/src/main/resources/db/migration/V*__*.sql
```

后端启动时会自动建表 / 补齐缺失的变更，Docker Compose 一键启动不再需要手工导入 SQL。

本目录中的文件仅作历史参考：

- `ai_learning_assistant.sql`：旧的完整 mysqldump（含示例数据）。用它建过的库在后端首次启动时会被 Flyway 标记为版本 1，然后只补执行 V2 及之后的脚本。
- `migrations/`：Flyway 引入前手工执行的增量脚本，内容已全部包含在 V1 基线中。

新增表结构变更时，请在 `db/migration` 下追加新的 `V{n}__描述.sql`，不要修改已经发布过的脚本。
