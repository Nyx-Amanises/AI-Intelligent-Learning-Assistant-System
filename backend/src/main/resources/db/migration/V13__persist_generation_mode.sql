-- 只记录可证明的生成来源；不根据当前配置或文本内容猜测历史来源。
ALTER TABLE ai_generation_record
    ADD COLUMN generation_mode VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '生成来源: MOCK/LIVE/UNKNOWN';
ALTER TABLE question_set
    ADD COLUMN generation_mode VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '生成来源: MOCK/LIVE/UNKNOWN';
ALTER TABLE study_note
    ADD COLUMN generation_mode VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '继承总结生成来源: MOCK/LIVE/UNKNOWN';
ALTER TABLE study_material
    ADD COLUMN summary_generation_mode VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '最近成功总结来源: MOCK/LIVE/UNKNOWN';
ALTER TABLE practice_session
    ADD COLUMN generation_mode VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '开始练习时题集来源: MOCK/LIVE/UNKNOWN';
