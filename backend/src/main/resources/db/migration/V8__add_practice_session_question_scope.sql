ALTER TABLE practice_session
    ADD COLUMN question_ids_json TEXT NULL COMMENT '专项练习题目ID数组，NULL表示整套练习' AFTER question_set_id;
