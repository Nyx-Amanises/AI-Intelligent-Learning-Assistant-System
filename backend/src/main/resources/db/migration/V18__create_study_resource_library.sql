CREATE TABLE study_resource (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    subject VARCHAR(100) NOT NULL,
    knowledge_points VARCHAR(500) NOT NULL,
    difficulty_level INT NOT NULL,
    estimated_minutes INT NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    source_name VARCHAR(200) NOT NULL,
    source_url VARCHAR(1000) NULL,
    author VARCHAR(200) NOT NULL,
    license_code VARCHAR(64) NOT NULL,
    license_url VARCHAR(1000) NULL,
    license_note VARCHAR(1000) NOT NULL,
    content_text MEDIUMTEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    review_note VARCHAR(1000) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_study_resource_status_subject (status, subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经过审核的公共学习资源目录';

CREATE TABLE study_resource_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    material_id BIGINT NULL,
    bookmarked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_study_resource_user (user_id, resource_id),
    KEY idx_study_resource_bookmarks (user_id, bookmarked, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏与私有资料导入记录';

ALTER TABLE study_material
    ADD COLUMN resource_attribution MEDIUMTEXT NULL COMMENT '导入时保存的完整来源与授权快照，不参与教学分块';
