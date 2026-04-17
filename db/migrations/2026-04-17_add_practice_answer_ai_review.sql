ALTER TABLE practice_answer
    ADD COLUMN review_mode VARCHAR(20) DEFAULT NULL COMMENT '评分方式: RULE/AI/AI_PENDING' AFTER obtained_score,
    ADD COLUMN review_comment VARCHAR(1000) DEFAULT NULL COMMENT '评分评语' AFTER review_mode;
