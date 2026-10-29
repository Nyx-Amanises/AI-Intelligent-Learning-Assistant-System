-- 出题时使用的资料正文快照；历史题集保持 NULL，不能以事后检索冒充原始依据。
ALTER TABLE question_set
    ADD COLUMN source_segments_json LONGTEXT NULL COMMENT '出题时的资料片段快照';
