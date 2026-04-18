ALTER TABLE assistant_session
    ADD COLUMN pending_action_type VARCHAR(32) DEFAULT NULL COMMENT 'pending action type: MATERIAL_SELECTION/QUESTION_CONFIG' AFTER current_practice_session_id,
    ADD COLUMN pending_action_payload_json LONGTEXT DEFAULT NULL COMMENT 'pending action payload json' AFTER pending_action_type;
