-- 保存可复用的出题设置，独立于可删除的异步任务记录，不包含 API 密钥。
ALTER TABLE question_set
    ADD COLUMN generation_request_json TEXT NULL COMMENT '原始出题参数（模型、题型、难度、知识点）';
