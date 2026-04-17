ALTER TABLE material_segment
    ADD COLUMN embedding_model VARCHAR(100) DEFAULT NULL COMMENT 'latest planned embedding model' AFTER embedding_status,
    ADD COLUMN embedding_task_id BIGINT DEFAULT NULL COMMENT 'latest embedding task id' AFTER embedding_model,
    ADD COLUMN vector_id VARCHAR(128) DEFAULT NULL COMMENT 'vector store id' AFTER embedding_task_id,
    ADD COLUMN embedded_at DATETIME DEFAULT NULL COMMENT 'vector store write time' AFTER vector_id,
    ADD KEY idx_material_segment_embedding_task (embedding_task_id),
    ADD KEY idx_material_segment_material_embedding (material_id, embedding_status);
