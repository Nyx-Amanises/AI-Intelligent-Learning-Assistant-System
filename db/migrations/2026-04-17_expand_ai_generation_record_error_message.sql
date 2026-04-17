ALTER TABLE ai_generation_record
    MODIFY COLUMN error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息';
