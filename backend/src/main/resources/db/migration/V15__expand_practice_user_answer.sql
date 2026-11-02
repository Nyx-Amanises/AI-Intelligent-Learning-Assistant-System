-- 保留已发布基线；TEXT 可完整存储应用允许的最多 5000 个 UTF-16 代码单元。
ALTER TABLE practice_answer
    MODIFY COLUMN user_answer TEXT NULL COMMENT '用户答案（最多5000个UTF-16代码单元）';
