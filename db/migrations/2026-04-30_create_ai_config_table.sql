CREATE TABLE IF NOT EXISTS ai_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    scope VARCHAR(20) NOT NULL COMMENT 'Config scope: GLOBAL or USER',
    user_id BIGINT NOT NULL DEFAULT 0 COMMENT 'GLOBAL uses 0; USER uses sys_user.id',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether AI is enabled',
    mock_mode TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether mock mode is enabled',
    chat_provider_type VARCHAR(50) NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT 'Chat provider',
    base_url VARCHAR(500) DEFAULT NULL COMMENT 'Chat base URL',
    chat_path VARCHAR(255) DEFAULT NULL COMMENT 'Chat API path',
    api_key VARCHAR(1000) DEFAULT NULL COMMENT 'Chat API key',
    default_model VARCHAR(255) DEFAULT NULL COMMENT 'Default chat model',
    embedding_provider_type VARCHAR(50) DEFAULT 'OPENAI_COMPATIBLE' COMMENT 'Embedding provider',
    embedding_base_url VARCHAR(500) DEFAULT NULL COMMENT 'Embedding base URL',
    embedding_path VARCHAR(255) DEFAULT NULL COMMENT 'Embedding API path',
    embedding_api_key VARCHAR(1000) DEFAULT NULL COMMENT 'Embedding API key',
    default_embedding_model VARCHAR(255) DEFAULT NULL COMMENT 'Default embedding model',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_config_scope_user (scope, user_id),
    KEY idx_ai_config_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI config table';

UPDATE sys_user
SET role_code = 'ADMIN'
WHERE username = 'admin';
