#!/bin/bash
set -e

echo "Initializing ai_learning_assistant schema..."
mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" < /schema/ai_learning_assistant.sql

for migration in /schema/migrations/*.sql; do
  echo "Applying migration: ${migration}"
  mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" ai_learning_assistant < "${migration}"
done

echo "Database initialization finished."
