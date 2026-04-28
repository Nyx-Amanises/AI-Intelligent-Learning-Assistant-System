#!/bin/bash
set -e

DB_NAME="${MYSQL_DATABASE:-ai_learning_assistant}"

echo "Initializing ${DB_NAME} schema..."
mysql --protocol=socket --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

mysql --protocol=socket --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" "${DB_NAME}" < /schema/ai_learning_assistant.sql

if [ "${APPLY_SCHEMA_MIGRATIONS:-false}" = "true" ]; then
  for migration in /schema/migrations/*.sql; do
    [ -e "${migration}" ] || continue
    echo "Applying migration: ${migration}"
    mysql --protocol=socket --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" "${DB_NAME}" < "${migration}"
  done
else
  echo "Skipping schema migrations because the base SQL is a full database dump."
fi

echo "Database initialization finished."
