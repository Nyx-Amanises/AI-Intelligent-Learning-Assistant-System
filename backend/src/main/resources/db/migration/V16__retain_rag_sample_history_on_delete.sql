-- 运行明细是历史快照，样本删除后仍保留非空 sample_id 及其索引，用于识别同一历史样本。
-- 不级联删除或置空：问题、标注、检索结果和运行指标均保持评测时的内容。
ALTER TABLE rag_eval_run_item
    DROP FOREIGN KEY fk_rag_eval_run_item_sample;
