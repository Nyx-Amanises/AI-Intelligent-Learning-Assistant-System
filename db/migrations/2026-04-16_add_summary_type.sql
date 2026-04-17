ALTER TABLE ai_generation_record
ADD COLUMN summary_type VARCHAR(32) NULL COMMENT '总结类型: STANDARD/EXAM/OUTLINE' AFTER task_type;

UPDATE ai_generation_record
SET summary_type = 'STANDARD'
WHERE task_type = 'SUMMARY'
  AND summary_type IS NULL;

CREATE INDEX idx_ai_generation_material_task_created
ON ai_generation_record (material_id, task_type, created_at);
