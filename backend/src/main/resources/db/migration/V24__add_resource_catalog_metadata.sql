-- Existing resources keep their original access semantics; catalogue entries are added separately.
ALTER TABLE study_resource
    ADD COLUMN access_mode VARCHAR(32) NOT NULL DEFAULT 'OPEN_RESOURCE' COMMENT 'OPEN_RESOURCE/CATALOG_ONLY';
ALTER TABLE study_resource
    ADD COLUMN isbn VARCHAR(13) NULL COMMENT 'ISBN-13；无可靠来源时留空';
ALTER TABLE study_resource
    ADD COLUMN publisher VARCHAR(200) NULL COMMENT '出版社';
ALTER TABLE study_resource
    ADD COLUMN education_level VARCHAR(40) NULL COMMENT '教材适用教育层次';
ALTER TABLE study_resource
    ADD COLUMN publication_year INT NULL COMMENT '来源可核实的出版年；未知时留空';

CREATE INDEX idx_study_resource_status_access ON study_resource (status, access_mode);
