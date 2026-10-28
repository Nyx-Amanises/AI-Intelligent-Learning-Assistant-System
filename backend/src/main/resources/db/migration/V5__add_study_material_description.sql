-- 资料说明：记录来源、版本、用途等备注，便于整理资料，也用于记录评测语料的来源。
ALTER TABLE study_material
    ADD COLUMN description VARCHAR(500) DEFAULT NULL COMMENT '资料说明（来源、版本、备注）' AFTER tags;
