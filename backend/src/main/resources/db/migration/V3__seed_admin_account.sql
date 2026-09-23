-- 初始管理员账号（README 中的示例账号 admin / 123456）。
-- 全局 AI 配置只有 ADMIN 角色能修改，因此全新数据库需要一个管理员。
-- 生产环境请在首次登录后立即修改密码。
INSERT IGNORE INTO sys_user (username, password_hash, nickname, role_code, status, deleted)
VALUES ('admin', '$2a$10$kkQN2HTakw8VlHKRJmx7rujZkYZbmMbLShHG6shrcLTsjew/8YkYu', '管理员', 'ADMIN', 1, 0);

-- 旧库中 admin 可能仍是 USER 角色，这里统一提升。
UPDATE sys_user SET role_code = 'ADMIN' WHERE username = 'admin';
