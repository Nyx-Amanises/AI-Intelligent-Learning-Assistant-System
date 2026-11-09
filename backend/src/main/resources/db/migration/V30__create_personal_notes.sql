-- Personal writing is independent of AI-generated study_note summaries.
CREATE TABLE note_notebook (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(60) NOT NULL,
    color VARCHAR(20) NOT NULL DEFAULT 'blue',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_notebook_owner (user_id, deleted, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE personal_note (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_key CHAR(36) NOT NULL,
    notebook_id BIGINT NULL,
    title VARCHAR(200) NOT NULL DEFAULT '',
    content_json MEDIUMTEXT NOT NULL,
    content_text MEDIUMTEXT NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    source_id BIGINT NULL,
    practice_session_id BIGINT NULL,
    page_number INT NULL,
    source_title VARCHAR(200) NOT NULL DEFAULT '',
    source_excerpt TEXT NOT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_note_client (user_id, client_key),
    INDEX idx_note_owner (user_id, deleted, updated_at, id),
    INDEX idx_note_notebook (user_id, notebook_id, deleted),
    INDEX idx_note_source (user_id, source_type, source_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
