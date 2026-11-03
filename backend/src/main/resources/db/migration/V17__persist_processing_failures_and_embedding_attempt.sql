ALTER TABLE material_segment
    ADD COLUMN embedding_attempt_id VARCHAR(36) NULL COMMENT '本次向量化执行标识，用于失败补偿隔离';

ALTER TABLE study_material
    ADD COLUMN parse_error_message VARCHAR(1000) NULL COMMENT '最近一次解析失败原因',
    ADD COLUMN parse_attempt_id VARCHAR(36) NULL COMMENT '本次解析执行标识，用于失败补偿隔离';
