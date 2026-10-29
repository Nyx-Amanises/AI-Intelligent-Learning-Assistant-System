ALTER TABLE assistant_tool_call
    ADD COLUMN summary_text TEXT NULL COMMENT 'Readable result summary captured when the tool ran' AFTER tool_result_json;
