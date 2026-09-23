#!/bin/bash
set -e

# 只负责保证数据库存在且使用 utf8mb4。
# 表结构与后续变更由后端启动时的 Flyway 自动执行（backend/src/main/resources/db/migration）。
DB_NAME="${MYSQL_DATABASE:-ai_learning_assistant}"

echo "Ensuring database ${DB_NAME} exists..."
mysql --protocol=socket --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

echo "Database initialization finished. Schema will be created by Flyway on backend startup."
