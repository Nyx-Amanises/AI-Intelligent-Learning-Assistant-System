ALTER TABLE assistant_message
    ADD COLUMN reply_to_message_id BIGINT NULL COMMENT 'assistant reply parent user message id' AFTER user_id,
    ADD INDEX idx_assistant_message_history (user_id, session_id, id),
    ADD INDEX idx_assistant_message_reply (reply_to_message_id);
