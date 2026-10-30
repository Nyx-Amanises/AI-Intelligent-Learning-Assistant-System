-- 历史数据不伪造参数；只有新解析/新运行才记录真实来源和参数。
ALTER TABLE study_material
    ADD COLUMN chunk_strategy VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN chunk_size INT NULL,
    ADD COLUMN chunk_overlap INT NULL,
    ADD COLUMN content_hash VARCHAR(64) NULL,
    ADD COLUMN source_text LONGTEXT NULL;

ALTER TABLE material_segment
    ADD COLUMN embedding_mode VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE rag_eval_run
    ADD COLUMN name VARCHAR(80) NULL,
    ADD COLUMN strategy VARCHAR(30) NULL,
    ADD COLUMN parameter_snapshot_json LONGTEXT NULL,
    ADD COLUMN actual_strategies_json TEXT NULL,
    ADD COLUMN mock_embedding TINYINT NULL,
    ADD COLUMN degraded_samples INT NULL,
    ADD INDEX idx_rag_eval_history (user_id, dataset_id, id);

ALTER TABLE rag_eval_run_item
    ADD COLUMN actual_strategy VARCHAR(30) NULL,
    ADD COLUMN mock_embedding TINYINT NULL,
    ADD COLUMN query_embedding_model VARCHAR(200) NULL,
    ADD COLUMN degrade_reason VARCHAR(1000) NULL,
    ADD COLUMN retrieved_snapshot_json LONGTEXT NULL;
